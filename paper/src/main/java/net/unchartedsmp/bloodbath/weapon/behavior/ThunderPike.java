package net.unchartedsmp.bloodbath.weapon.behavior;

import net.unchartedsmp.bloodbath.ability.Cooldowns;
import net.unchartedsmp.bloodbath.ability.TickScheduler;
import net.unchartedsmp.bloodbath.fx.BloodFx;
import net.unchartedsmp.bloodbath.fx.Shapes;
import net.unchartedsmp.bloodbath.util.Damage;
import net.unchartedsmp.bloodbath.util.Targeting;
import net.unchartedsmp.bloodbath.weapon.WeaponBehavior;
import net.unchartedsmp.bloodbath.weapon.WeaponType;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * Crimson Thunder Pike: throw a bolt of charged blood up to 20 blocks, call crimson lightning
 * where it lands and ride it there. Whatever it strikes is stunned (heavily slowed) for a second.
 *
 * <p>The lightning is cosmetic and the pike deals its own damage: real lightning would strike the
 * wielder after the teleport, start fires, and could be farmed to turn villagers into witches,
 * pigs into piglins and creepers into charged creepers on demand.
 */
public final class ThunderPike implements WeaponBehavior {
	private static final int TRAVEL_TICKS = 8;

	@Override
	public WeaponType type() {
		return WeaponType.THUNDER_PIKE;
	}

	@Override
	public void use(Player player) {
		if (!Cooldowns.checkReady(player, ability())) {
			return;
		}
		Cooldowns.start(player, ability());
		double radius = setting("radius", 3.0);
		Location start = player.getEyeLocation();
		Location impact = Targeting.lookTarget(player, setting("range", 20.0));
		Location floor = impact.clone().add(0.0, 0.1, 0.0);
		// Telegraph the landing spot so the target (and you) can see where the bolt will fall.
		BloodFx.ring(floor, BloodFx.BLOOD_FADE, radius, 24);
		TickScheduler.repeat(0, 1, TRAVEL_TICKS, tick -> {
			double t = (tick + 1) / (double) TRAVEL_TICKS;
			Location p = start.clone().add(impact.clone().subtract(start).toVector().multiply(t));
			BloodFx.burst(p, BloodFx.SPARK, 6, 0.12);
			BloodFx.burst(p, BloodFx.BLOOD_FADE, 3, 0.12);
			if (tick % 2 == 0) {
				BloodFx.ring(floor, BloodFx.SPARK, radius * (1.0 - tick / (double) TRAVEL_TICKS), 16);
			}
			return true;
		});
		TickScheduler.schedule(TRAVEL_TICKS, () -> strike(player, start, impact, radius));
	}

	private void strike(Player player, Location start, Location impact, double radius) {
		World world = impact.getWorld();
		world.strikeLightningEffect(impact);
		BloodFx.burst(impact, BloodFx.SPARK, 45, 0.6);
		BloodFx.splash(impact, 8);
		BloodFx.spray(impact, radius, 12, 8);
		BloodFx.line(impact, impact.clone().add(0.0, 12.0, 0.0), BloodFx.BLOOD_FADE, 2.0);
		BloodFx.play(impact, BloodFx.THUNDER, 1.0F, 1.2F);

		double damage = setting("damage", 7.0);
		int stun = ticksSetting("stun", 20);
		for (LivingEntity target : Targeting.livingInRadius(impact, radius, player)) {
			Damage.deal(target, damage, player, type(), impact);
			if (stun > 0 && target.isValid() && !target.isDead()) {
				// Stunned by the bolt: slowed hard for a moment, so riding in on them is a real engage.
				target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, stun, 2, false, true, true));
				Shapes.spiral(target.getLocation(), BloodFx.SPARK, 0.6, 2.0, 1.5, 12, 0.0);
			}
		}

		// Only ride the bolt if we're still in the same dimension (no portal-hopping mid-cast)
		// and there's room to stand where it landed.
		if (Targeting.stillIn(player, world)) {
			Targeting.safeLanding(player, start, impact).ifPresent(landing ->
				Targeting.teleport(player, landing, player.getLocation().getYaw(), player.getLocation().getPitch()));
		}
	}

	@Override
	public BloodFx.Fx auraAccent() {
		return BloodFx.SPARK;
	}
}
