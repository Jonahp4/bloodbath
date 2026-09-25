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
				xyz[i * 3 + 1] = scratch.getY() + players[i].getEyeHeight(); // the camera
				xyz[i * 3 + 2] = scratch.getZ();
				pack[i] = PackState.hasPack(players[i]);
			}
		}
	}

	/**
	 * No particle is sent to a player whose camera it would spawn this close to: there it would
	 * fill the screen (your own rift flow leaving your hand, the burst when you step through).
	 * Everyone else still sees it.
	 */
	private static final double CAMERA_GUARD_SQ = 1.0;

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
			if (distSq > rangeSq || distSq < CAMERA_GUARD_SQ) {
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
			if (!plain.isEmpty() && fx.particle() != null) { // null: an effect only players with the pack get
				world.spawnParticle(fx.particle(), plain, subject, x, y, z, n, dx, dy, dz, speed, fx.data(), force);
			}
			List<Player> packed = BANDS.get(band * 2 + 1);
			if (!packed.isEmpty()) {
				BloodFx.Fx variant = fx.packVariant();
				int packCount = Math.max(1, (int) Math.round(n * variant.countScale()));
				world.spawnParticle(variant.particle(), packed, subject, x, y, z, packCount, dx, dy, dz, variant.speed() < 0 ? speed * -variant.speed() : variant.speed(),
					variant.data(), force);
			}
		}
		for (List<Player> band : BANDS) {
			band.clear(); // don't hold on to players between ticks
		}
	}

	/**
	 * A shape's worth of particles around one place (a ring, a line, a spiral): who can see it,
	 * and how close they are, is worked out once for the whole shape instead of once per point,
	 * and viewers further away get every 2nd (mid) or 4th (far) point instead of all of them, so
	 * a big effect costs distant players a fraction of the packets. Near viewers get every point.
	 *
	 * @param extent how far the shape reaches from (x, y, z)
	 */
	public static Batch batch(World world, double x, double y, double z, double extent, Audience audience, Player subject, double range) {
		return new Batch(world, x, y, z, extent, audience, subject, range);
	}

	public static final class Batch {
		private final World world;
		private final Player subject;
		/** near, mid, far; each split into [no pack, pack]. */
		private final List<List<Player>> bands = new ArrayList<>(6);
		private final boolean any;
		/** Some near viewer's camera could be inside the shape: points check the camera guard. */
		private final boolean guard;
		private final double[] guardXyz;
		private final Player[] guardPlayers;

		private Batch(World world, double x, double y, double z, double extent, Audience audience, Player subject, double range) {
			this.world = world;
			this.subject = subject;
			for (int i = 0; i < 6; i++) {
				bands.add(new ArrayList<>(2));
			}
			Settings settings = Settings.get();
			if (world == null || settings.particleMultiplier <= 0.0) {
				any = false;
				guard = false;
				guardXyz = null;
				guardPlayers = null;
				return;
			}
			Snapshot snapshot = snapshot(world);
			double reach = range + extent;
			double reachSq = reach * reach;
			double near = settings.fullDetailDistance;
			double nearSq = near * near;
			double midSq = 4.0 * nearSq;
			double guardSq = (extent + 1.0) * (extent + 1.0);
			boolean found = false;
			List<Player> close = null;
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
				if (distSq > reachSq) {
					continue;
				}
				int band = distSq <= nearSq ? 0 : distSq <= midSq ? 1 : 2;
				bands.get(band * 2 + (snapshot.pack[i] ? 1 : 0)).add(player);
				found = true;
				if (distSq <= guardSq) {
					if (close == null) {
						close = new ArrayList<>(2);
					}
					close.add(player);
				}
			}
			any = found;
			guard = close != null;
			if (guard) {
				guardPlayers = close.toArray(new Player[0]);
				guardXyz = new double[guardPlayers.length * 3];
				Location scratch = new Location(world, 0, 0, 0);
				for (int i = 0; i < guardPlayers.length; i++) {
					guardPlayers[i].getLocation(scratch);
					guardXyz[i * 3] = scratch.getX();
					guardXyz[i * 3 + 1] = scratch.getY() + guardPlayers[i].getEyeHeight();
					guardXyz[i * 3 + 2] = scratch.getZ();
				}
			} else {
				guardPlayers = null;
				guardXyz = null;
			}
		}

		/** Whether anyone will see this shape at all (skip building it otherwise). */
		public boolean visible() {
			return any;
		}

		/** One point of the shape; {@code index} decides which points distant viewers still get. */
		public void point(int index, BloodFx.Fx fx, double x, double y, double z, int count, double dx, double dy, double dz, double speed) {
			if (!any) {
				return;
			}
			int full = Math.max(1, (int) Math.round(count * Settings.get().particleMultiplier));
			for (int band = 0; band < 3; band++) {
				if (band == 1 && (index & 1) != 0 || band == 2 && (index & 3) != 0) {
					continue;
				}
				int n = band == 0 ? full : Math.max(1, full >> band);
				boolean force = band > 0;
				send(bands.get(band * 2), fx, x, y, z, n, dx, dy, dz, speed, force, false);
				send(bands.get(band * 2 + 1), fx, x, y, z, n, dx, dy, dz, speed, force, fx.packVariant() != null);
			}
		}

		private void send(List<Player> to, BloodFx.Fx fx, double x, double y, double z, int n, double dx, double dy, double dz, double speed,
			boolean force, boolean pack) {
			if (to.isEmpty()) {
				return;
			}
			List<Player> receivers = to;
			if (guard) {
				// Never a particle right in front of someone's camera.
				receivers = null;
				for (Player player : to) {
					if (!tooClose(player, x, y, z)) {
						if (receivers == null) {
							receivers = new ArrayList<>(to.size());
						}
						receivers.add(player);
					}
				}
				if (receivers == null) {
					return;
				}
			}
			if (pack) {
				BloodFx.Fx variant = fx.packVariant();
				int packCount = Math.max(1, (int) Math.round(n * variant.countScale()));
				world.spawnParticle(variant.particle(), receivers, subject, x, y, z, packCount, dx, dy, dz,
					variant.speed() < 0 ? speed * -variant.speed() : variant.speed(), variant.data(), force);
			} else if (fx.particle() != null) {
				world.spawnParticle(fx.particle(), receivers, subject, x, y, z, n, dx, dy, dz, speed, fx.data(), force);
			}
		}

		private boolean tooClose(Player player, double x, double y, double z) {
			for (int i = 0; i < guardPlayers.length; i++) {
				if (guardPlayers[i] == player) {
					double ddx = guardXyz[i * 3] - x;
					double ddy = guardXyz[i * 3 + 1] - y;
					double ddz = guardXyz[i * 3 + 2] - z;
					return ddx * ddx + ddy * ddy + ddz * ddz < CAMERA_GUARD_SQ;
				}
			}
			return false;
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
