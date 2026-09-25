package net.unchartedsmp.bloodbath.weapon.behavior;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.unchartedsmp.bloodbath.ability.Cooldowns;
import net.unchartedsmp.bloodbath.ability.ServerClock;
import net.unchartedsmp.bloodbath.ability.TickScheduler;
import net.unchartedsmp.bloodbath.fx.BloodFx;
import net.unchartedsmp.bloodbath.fx.Particles;
import net.unchartedsmp.bloodbath.fx.Shapes;
import net.unchartedsmp.bloodbath.hud.Hud;
import net.unchartedsmp.bloodbath.util.Damage;
import net.unchartedsmp.bloodbath.util.Targeting;
import net.unchartedsmp.bloodbath.weapon.WeaponBehavior;
import net.unchartedsmp.bloodbath.weapon.WeaponType;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * Bloodrift Blade: tears a bleeding rift up to 14 blocks ahead that drags nearby enemies in.
 * Use again within 6s to step through it. The cooldown starts when the rift opens.
 */
public final class Riftblade implements WeaponBehavior {
	private static final double PULL_STRENGTH = 0.34;
	private static final int PULL_DURATION_TICKS = 14;

	private record Rift(Location origin, Location pos, long expiresAt) {
	}

	private final Map<UUID, Rift> rifts = new HashMap<>();

	@Override
	public WeaponType type() {
		return WeaponType.RIFTBLADE;
	}

	@Override
	public void use(Player player) {
		Rift rift = rifts.remove(player.getUniqueId());
		if (rift != null && ServerClock.now() <= rift.expiresAt() && Targeting.sameWorld(rift.pos(), player)) {
			stepThrough(player, rift);
			return;
		}
		if (!Cooldowns.checkReady(player, ability())) {
			return;
		}
		openRift(player);
		Cooldowns.start(player, ability());
	}

	private void openRift(Player player) {
		double pullRadius = setting("pull-radius", 3.5);
		int lifetime = ticksSetting("window", 120);
		Location origin = player.getEyeLocation();
		Location pos = Targeting.lookTarget(player, setting("range", 14.0));
		Rift rift = new Rift(origin, pos, ServerClock.now() + lifetime);
		rifts.put(player.getUniqueId(), rift);

		BloodFx.play(pos, BloodFx.RIFT_OPEN, 1.0F, 1.3F);
		BloodFx.play(pos, BloodFx.HEARTBEAT, 1.0F, 1.0F);
		BloodFx.burst(pos, BloodFx.BLOOD_LARGE, 40, 0.4);
		BloodFx.burst(pos, BloodFx.SPORE, 10, 0.2);
		BloodFx.flow(Hud.handPos(player, false), pos, 8, 0.1, BloodFx.BRIGHT_RED, 6);
		Shapes.vortex(pos.clone().add(0.0, -0.8, 0.0), pullRadius, 3, PULL_DURATION_TICKS + 6);
		double facing = Math.toRadians(player.getLocation().getYaw());

		TickScheduler.repeat(0, 1, PULL_DURATION_TICKS, tick -> {
			for (LivingEntity target : Targeting.livingInRadius(pos, pullRadius, player)) {
				Targeting.pullTowards(target, pos, PULL_STRENGTH);
				if (tick % 3 == 0) {
					BloodFx.flow(BloodFx.chest(target), pos, 2, 0.2, BloodFx.BRIGHT_RED, 6);
				}
			}
			if (tick % 3 == 0) {
				BloodFx.burst(pos, BloodFx.CLOT, 6, 0.5);
			}
			return true;
		});

		// The rift keeps weeping until it's used or closes.
		TickScheduler.repeat(10, 10, lifetime / 10, tick -> {
			if (rifts.get(player.getUniqueId()) != rift) {
				return false;
			}
			tear(pos, facing, tick);
			BloodFx.burst(pos, BloodFx.DRIP, 2, 0.3, 0.0);
			BloodFx.gather(pos, 2.2, 3, 10);
			return true;
		});
	}

	private void stepThrough(Player player, Rift rift) {
		Optional<Location> landing = Targeting.safeLanding(player, rift.origin(), rift.pos());
		if (landing.isEmpty()) {
			BloodFx.splash(rift.pos(), 6);
			Hud.flash(player, Component.text("The rift clotted shut. Nowhere to land.", NamedTextColor.DARK_RED));
			return;
		}
		Location target = landing.get();
		Location departure = BloodFx.chest(player);
		BloodFx.burst(departure, BloodFx.BLOOD_LARGE, 25, 0.4);
		Shapes.helix(departure, target.clone().add(0.0, 1.0, 0.0), BloodFx.BLOOD_FADE, 0.45, 0.35, 3.0, 0.0);
		BloodFx.flow(departure, target.clone().add(0.0, 1.0, 0.0), 8, 0.3, BloodFx.BRIGHT_RED, 8);
		if (Targeting.teleport(player, target, player.getLocation().getYaw(), player.getLocation().getPitch())) {
			BloodFx.play(target, BloodFx.RIFT_STEP, 1.0F, 0.8F);
			BloodFx.splash(target.clone().add(0.0, 1.0, 0.0), 8);
			tearShut(player, target);
		}
	}

	/**
	 * The rift itself: an upright tear in the air, a bright edge round a dark wound, turned to face
	 * whoever opened it and breathing as it weeps.
	 */
	private static void tear(Location pos, double facingRadians, int tick) {
		Particles.Batch batch = BloodFx.shape(pos, 1.4);
		if (!batch.visible()) {
			return;
		}
		double breathe = 1.0 + 0.08 * Math.sin(tick * 1.3);
		double sideX = Math.cos(facingRadians);
		double sideZ = Math.sin(facingRadians);
		int points = 22;
		for (int i = 0; i < points; i++) {
			double a = Math.PI * 2.0 * i / points;
			double across = Math.cos(a) * 0.38 * breathe;
			double up = Math.sin(a) * 1.15 * breathe;
			batch.point(i, i % 3 == 0 ? BloodFx.SLASH : BloodFx.BLOOD_FADE, pos.getX() + sideX * across, pos.getY() + up,
				pos.getZ() + sideZ * across, 1, 0, 0, 0, 0);
		}
		for (int k = -3; k <= 3; k++) {
			batch.point(points + k + 3, BloodFx.CLOT, pos.getX(), pos.getY() + k * 0.28, pos.getZ(), 1, 0.03, 0.05, 0.03, 0);
		}
	}

	/** The rift snaps shut behind you, cutting everything around where you came out and slowing it. */
	private void tearShut(Player player, Location at) {
		double radius = setting("exit-radius", 2.5);
		double damage = setting("exit-damage", 4.0);
		int slow = ticksSetting("exit-slow", 30);
		Location center = at.clone().add(0.0, 1.0, 0.0);
		Shapes.arc(center, BloodFx.SLASH, radius * 0.8, Math.toRadians(at.getYaw() + 90.0), Math.PI, 18);
		BloodFx.ring(at.clone().add(0.0, 0.15, 0.0), BloodFx.BLOOD_FADE, radius, 24);
		BloodFx.burst(center, BloodFx.NOVA, 1, 0.0);
		for (LivingEntity target : Targeting.livingInRadius(center, radius, player)) {
			Damage.deal(target, damage, player, type(), at);
			if (slow > 0) {
				target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, slow, 1, false, true, true));
			}
			BloodFx.splash(BloodFx.chest(target), 4);
		}
	}

	@Override
	public Component hud(Player player) {
		Rift rift = rifts.get(player.getUniqueId());
		long left = rift == null ? 0 : rift.expiresAt() - ServerClock.now();
		if (left > 0 && Targeting.sameWorld(rift.pos(), player)) {
			return Hud.timer("◉ Rift open", left).append(Component.text("  use again to step through", NamedTextColor.GRAY));
		}
		return Hud.cooldownBar(player, ability());
	}

	@Override
	public BloodFx.Fx auraAccent() {
		return BloodFx.SPORE;
	}

	@Override
	public void forget(UUID playerId) {
		rifts.remove(playerId);
	}

	@Override
	public void prune() {
		long now = ServerClock.now();
		rifts.values().removeIf(rift -> rift.expiresAt() < now);
	}

	@Override
	public void shutdown() {
		rifts.clear();
	}
}
