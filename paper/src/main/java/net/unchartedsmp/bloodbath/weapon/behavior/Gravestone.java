package net.unchartedsmp.bloodbath.weapon.behavior;

import net.unchartedsmp.bloodbath.ability.Cooldowns;
import net.unchartedsmp.bloodbath.ability.TickScheduler;
import net.unchartedsmp.bloodbath.blood.Bleeding;
import net.unchartedsmp.bloodbath.fx.BloodFx;
import net.unchartedsmp.bloodbath.fx.Shapes;
import net.unchartedsmp.bloodbath.util.Damage;
import net.unchartedsmp.bloodbath.util.Targeting;
import net.unchartedsmp.bloodbath.weapon.WeaponBehavior;
import net.unchartedsmp.bloodbath.weapon.WeaponType;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

/**
 * Crimson Gravestone: opens a blood pool at your feet that drags everything within 6 blocks
 * inward for 2s, then erupts under them: 6 damage, a bleeding wound, and a throw straight up so
 * they come down beside you rather than out of reach.
 */
public final class Gravestone implements WeaponBehavior {
	private static final double PULL_STRENGTH = 0.2;
	private static final double LAUNCH_STRENGTH = 0.35;
	private static final double LAUNCH_VERTICAL = 1.15;

	@Override
	public WeaponType type() {
		return WeaponType.GRAVESTONE;
	}

	@Override
	public void use(Player player) {
		if (!Cooldowns.checkReady(player, ability())) {
			return;
		}
		Cooldowns.start(player, ability());
		slam(player);
	}

	private void slam(Player caster) {
		double radius = setting("radius", 6.0);
		int duration = Math.max(1, ticksSetting("duration", 40));
		Location center = caster.getLocation();
		Location floor = center.clone().add(0.0, 0.1, 0.0);
		BloodFx.play(center, BloodFx.HEARTBEAT, 1.2F, 0.5F);
		BloodFx.play(center, BloodFx.SQUELCH, 1.0F, 0.6F);
		BloodFx.burst(center, BloodFx.BURST, 1, 0.0);
		BloodFx.burst(floor, BloodFx.GORE, 30, 1.5, 0.1);
		Shapes.vortex(floor, radius, 4, duration);
		Shapes.column(center, BloodFx.CLOT, 1.6, 10, 0.12);

		// One repeating task instead of one scheduled lambda per tick.
		TickScheduler.repeat(0, 1, duration, tick -> {
			for (LivingEntity target : Targeting.livingInRadius(center, radius, caster)) {
				Targeting.pullTowards(target, center, PULL_STRENGTH);
			}
			if (tick % 5 == 0) {
				double shrinking = radius * (1.0 - tick / (double) duration) + 0.5;
				BloodFx.ring(floor, BloodFx.BLOOD_FADE, shrinking, 24);
				BloodFx.burst(center, BloodFx.SPORE, 8, radius * 0.4);
				BloodFx.gather(center.clone().add(0.0, 0.4, 0.0), radius, 6, 12);
			}
			return true;
		});

		TickScheduler.schedule(duration, () -> {
			BloodFx.play(center, BloodFx.IMPACT, 1.0F, 1.4F);
			BloodFx.burst(center, BloodFx.SPLATTER, 50, 3.0, 0.25);
			Shapes.shockwave(floor, BloodFx.BLOOD_FADE, radius, 8);
			BloodFx.burst(center, BloodFx.NOVA, 1, 0.0);
			BloodFx.burst(center, BloodFx.BLOOD_LARGE, 30, 2.0);
			BloodFx.spray(center.clone().add(0.0, 0.3, 0.0), radius, 16, 10);
			double damage = setting("damage", 6.0);
			for (LivingEntity target : Targeting.livingInRadius(center, radius, caster)) {
				if (damage > 0.0) {
					Damage.deal(target, damage, caster, type(), center);
				}
				Bleeding.apply(target, 1, caster, type());
				Targeting.launchOutward(target, center, LAUNCH_STRENGTH, LAUNCH_VERTICAL);
				Shapes.column(target.getLocation(), BloodFx.BLOOD_FADE, 2.2, 10, 0.25);
			}
		});
	}

	@Override
	public BloodFx.Fx auraAccent() {
		return BloodFx.GORE;
	}
}
