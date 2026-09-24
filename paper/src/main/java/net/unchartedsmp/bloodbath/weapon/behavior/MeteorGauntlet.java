package net.unchartedsmp.bloodbath.weapon.behavior;

import java.util.Optional;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.unchartedsmp.bloodbath.ability.Cooldowns;
import net.unchartedsmp.bloodbath.ability.TickScheduler;
import net.unchartedsmp.bloodbath.fx.BloodFx;
import net.unchartedsmp.bloodbath.hud.Hud;
import net.unchartedsmp.bloodbath.util.Damage;
import net.unchartedsmp.bloodbath.util.Targeting;
import net.unchartedsmp.bloodbath.weapon.WeaponBehavior;
import net.unchartedsmp.bloodbath.weapon.WeaponType;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

/**
 * Blood Meteor Gauntlet: punch a block, or right-click the ground up to 24 blocks away, to call
 * down a blood meteor. After a 2s telegraph it deals 5 damage and launches everything within
 * 4.5 blocks, except the caster.
 */
public final class MeteorGauntlet implements WeaponBehavior {
	private static final double LAUNCH_STRENGTH = 1.6;
	private static final double LAUNCH_VERTICAL = 1.1;

	@Override
	public WeaponType type() {
		return WeaponType.METEOR_GAUNTLET;
	}

	@Override
	public void use(Player player) {
		Optional<Location> ground = Targeting.lookBlock(player, setting("range", 24.0));
		if (ground.isEmpty()) {
			Hud.flash(player, Component.text("No ground within reach for the meteor.", NamedTextColor.GRAY));
			return;
		}
		if (!Cooldowns.checkReady(player, ability())) {
			return;
		}
		Cooldowns.start(player, ability());
		telegraphAndStrike(player, ground.get().add(0.0, 0.1, 0.0));
	}

	@Override
	public void punchBlock(Player player, Block block) {
		if (!Cooldowns.checkReady(player, ability())) {
			return;
		}
		Cooldowns.start(player, ability());
		telegraphAndStrike(player, block.getLocation().add(0.5, 1.05, 0.5));
	}

	private void telegraphAndStrike(Player caster, Location center) {
		double radius = setting("radius", 4.5);
		double damage = setting("damage", 5.0);
		int delay = Math.max(4, ticksSetting("delay", 40));
		int steps = delay / 4;
		BloodFx.play(center, BloodFx.HEARTBEAT, 1.0F, 0.6F);
		BloodFx.play(center, BloodFx.ROAR, 0.4F, 1.6F);
		TickScheduler.repeat(0, 4, steps, tick -> {
			double progress = tick / (double) steps;
			// Danger ring at full radius plus an inner ring closing in: impact when they meet.
			BloodFx.ring(center, BloodFx.CLOT, radius, 28);
			BloodFx.ring(center, BloodFx.BLOOD_FADE, radius * (1.0 - progress), 20);
			// The meteor: a falling clot that closes in on the impact point.
			Location meteor = center.clone().add(0.0, 14.0 * (1.0 - progress), 0.0);
			BloodFx.burst(meteor, BloodFx.BLOOD_LARGE, 8, 0.3);
			BloodFx.burst(meteor.clone().add(0.0, 0.8, 0.0), BloodFx.SMOKE, 2, 0.2, 0.0);
			BloodFx.burst(meteor, BloodFx.DRIP, 3, 0.2, 0.0);
			return true;
		});
		TickScheduler.schedule(delay, () -> strike(caster, center, radius, damage));
	}

	private void strike(Player caster, Location center, double radius, double damage) {
		BloodFx.play(center, BloodFx.IMPACT, 1.4F, 0.8F);
		BloodFx.play(center, BloodFx.SQUELCH, 1.2F, 0.5F);
		BloodFx.burst(center, BloodFx.BURST_HUGE, 1, 0.0);
		BloodFx.burst(center, BloodFx.SPLATTER, 50, 1.5, 0.25);
		BloodFx.burst(center, BloodFx.BLOOD_LARGE, 25, 1.2);
		BloodFx.spray(center, radius, 20, 10);
		BloodFx.ring(center, BloodFx.CLOT, radius * 0.7, 24);
		for (LivingEntity target : Targeting.livingInRadius(center, radius, caster)) {
			Damage.deal(target, damage, caster, type(), center);
			Targeting.launchOutward(target, center, LAUNCH_STRENGTH, LAUNCH_VERTICAL);
		}
	}

	@Override
	public BloodFx.Fx auraAccent() {
		return BloodFx.BLOOD_LARGE;
	}
}
