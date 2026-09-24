package net.unchartedsmp.bloodbath.fx;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.unchartedsmp.bloodbath.ability.ServerClock;
import net.unchartedsmp.bloodbath.config.Settings;
import net.unchartedsmp.bloodbath.pack.PackState;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

/**
 * Where every Bloodbath particle actually gets sent.
 *
 * <p>Instead of broadcasting to the whole world, each spawn goes only to players within
 * {@code effects.view-distance}, with level of detail: full count up close, half at mid range, a
 * quarter far away. Players are grouped into at most three distance bands, so one effect is at
 * most three packets' worth of work per band rather than one per particle.
 *
 * <p>Player positions are snapshotted once per tick per world (no {@code Location} allocation or
 * {@code getPlayers()} copy per spawn), which matters when a boss fight fires a few hundred spawn
 * calls a tick. Everything here runs on the main thread only.
 */
public final class Particles {
	/** Who an emission is for. */
	public enum Audience {
		/** Everyone in range. */
		ALL,
		/** Everyone in range except the subject (cosmetics that would sit in front of their camera). */
		OTHERS,
		/** Only the subject. */
		SELF
	}

	private static final class Snapshot {
		final Player[] players;
		final double[] xyz;
		final boolean[] pack;

		Snapshot(World world) {
			List<Player> list = world.getPlayers();
			players = list.toArray(new Player[0]);
			xyz = new double[players.length * 3];
			pack = new boolean[players.length];
			Location scratch = new Location(world, 0, 0, 0);
			for (int i = 0; i < players.length; i++) {
				players[i].getLocation(scratch);
				xyz[i * 3] = scratch.getX();
				xyz[i * 3 + 1] = scratch.getY() + 1.6;
				xyz[i * 3 + 2] = scratch.getZ();
				pack[i] = PackState.hasPack(players[i]);
			}
		}
	}

	private static final Map<World, Snapshot> SNAPSHOTS = new IdentityHashMap<>();
	private static long snapshotTick = Long.MIN_VALUE;
	/** Reused receiver lists: near, mid, far, each split into [no pack, pack]. */
	private static final List<List<Player>> BANDS = List.of(new ArrayList<>(), new ArrayList<>(), new ArrayList<>(),
		new ArrayList<>(), new ArrayList<>(), new ArrayList<>());

	private Particles() {
	}

	private static Snapshot snapshot(World world) {
		long now = ServerClock.now();
		if (now != snapshotTick) {
			SNAPSHOTS.clear(); // also forgets unloaded worlds
			snapshotTick = now;
		}
		return SNAPSHOTS.computeIfAbsent(world, Snapshot::new);
	}

	/** Drops the cached positions (players moved worlds, the plugin is stopping, tests). */
	public static void invalidate() {
		SNAPSHOTS.clear();
		snapshotTick = Long.MIN_VALUE;
	}

	/**
	 * Sends {@code fx} at a point. {@code count} is the full-detail count before the global
	 * multiplier; players further away get fewer. With a pack variant, players who loaded the
	 * resource pack get that instead.
	 */
	public static void spawn(World world, BloodFx.Fx fx, double x, double y, double z, int count, double dx, double dy, double dz,
		double speed, Audience audience, Player subject, double range) {
		Settings settings = Settings.get();
		double multiplier = settings.particleMultiplier;
		if (count <= 0 || multiplier <= 0.0 || world == null) {
			return;
		}
		Snapshot snapshot = snapshot(world);
		if (snapshot.players.length == 0) {
			return;
		}
		double rangeSq = range * range;
		double near = settings.fullDetailDistance;
		double nearSq = near * near;
		double midSq = 4.0 * nearSq;
		boolean split = fx.packVariant() != null;
		for (List<Player> band : BANDS) {
			band.clear();
		}
		boolean any = false;
		double[] xyz = snapshot.xyz;
		for (int i = 0; i < snapshot.players.length; i++) {
			Player player = snapshot.players[i];
			if (audience == Audience.OTHERS && player == subject || audience == Audience.SELF && player != subject) {
				continue;
			}
			double ddx = xyz[i * 3] - x;
			double ddy = xyz[i * 3 + 1] - y;
			double ddz = xyz[i * 3 + 2] - z;
			double distSq = ddx * ddx + ddy * ddy + ddz * ddz;
			if (distSq > rangeSq) {
				continue;
			}
			int band = distSq <= nearSq ? 0 : distSq <= midSq ? 1 : 2;
			BANDS.get(band * 2 + (split && snapshot.pack[i] ? 1 : 0)).add(player);
			any = true;
		}
		if (!any) {
			return;
		}
		int full = Math.max(1, (int) Math.round(count * multiplier));
		for (int band = 0; band < 3; band++) {
			int n = band == 0 ? full : Math.max(1, (int) Math.round(full / (band == 1 ? 2.0 : 4.0)));
			boolean force = band > 0; // beyond the client's own 32-block particle cut-off
			List<Player> plain = BANDS.get(band * 2);
			if (!plain.isEmpty()) {
				world.spawnParticle(fx.particle(), plain, subject, x, y, z, n, dx, dy, dz, speed, fx.data(), force);
			}
			List<Player> packed = BANDS.get(band * 2 + 1);
			if (!packed.isEmpty()) {
				BloodFx.Fx variant = fx.packVariant();
				int packCount = Math.max(1, (int) Math.round(n * variant.countScale()));
				world.spawnParticle(variant.particle(), packed, subject, x, y, z, packCount, dx, dy, dz, variant.speed() < 0 ? speed : variant.speed(),
					variant.data(), force);
			}
		}
		for (List<Player> band : BANDS) {
			band.clear(); // don't hold on to players between ticks
		}
	}

	/** Whether any player is close enough to see something at this point (skip whole effects early). */
	public static boolean anyoneNear(World world, double x, double y, double z, double range) {
		Snapshot snapshot = snapshot(world);
		double rangeSq = range * range;
		double[] xyz = snapshot.xyz;
		for (int i = 0; i < snapshot.players.length; i++) {
			double ddx = xyz[i * 3] - x;
			double ddy = xyz[i * 3 + 1] - y;
			double ddz = xyz[i * 3 + 2] - z;
			if (ddx * ddx + ddy * ddy + ddz * ddz <= rangeSq) {
				return true;
			}
		}
		return false;
	}
}
