package net.unchartedsmp.bloodbath.boss;

import java.util.concurrent.ThreadLocalRandom;
import net.unchartedsmp.bloodbath.ability.TickScheduler;
import net.unchartedsmp.bloodbath.fx.BloodFx;
import net.unchartedsmp.bloodbath.fx.Shapes;
import org.bukkit.Location;
import org.bukkit.World;

/**
 * The Blood Knight's visual and sound language. Every sequence is short, built from a few shapes
 * rather than a heap of particles, and escalates: small and quiet at first, one big moment, then
 * a tail that settles. Everything goes through {@link BloodFx}, so view distance, level of detail,
 * the particle multiplier and the sound settings all apply.
 */
final class BossFx {
	static final String BELL = "block.bell.use";
	static final String RUMBLE = "entity.warden.emerge";
	static final String DIG = "entity.warden.dig";
	static final String ROAR = "entity.ravager.roar";
	static final String STEP = "entity.ravager.step";
	static final String SWING = "entity.player.attack.sweep";
	static final String HEAVY_HIT = "entity.ravager.attack";
	static final String SLAM = "entity.generic.explode";
	static final String SPIKE = "block.pointed_dripstone.land";
	static final String CHARGE = "item.trident.riptide_3";
	static final String GROWL = "entity.warden.angry";
	static final String DEATH = "entity.warden.death";
	static final String TRIUMPH = "ui.toast.challenge_complete";
	static final String CLAIMED = "entity.wither.ambient";

	private BossFx() {
	}

	// ---- summoning ----------------------------------------------------------------------------

	/**
	 * One beat of the warning before the Blood Knight rises: {@code t} runs 0 to 1 over the whole
	 * warning, and everything tightens and speeds up as it does.
	 */
	static void warningBeat(Location center, int tick, int total) {
		double t = tick / (double) total;
		Location floor = center.clone().add(0, 0.1, 0);
		if (tick % 4 == 0) {
			// The ring closes in on the altar.
			double radius = 7.0 - 5.5 * t;
			BloodFx.ring(floor, t > 0.66 ? BloodFx.BLOOD_FADE : BloodFx.CLOT, radius, (int) (18 + radius * 4));
		}
		int convergeEvery = t < 0.4 ? 10 : t < 0.8 ? 6 : 3;
		if (tick % convergeEvery == 0) {
			Shapes.converge(center.clone().add(0, 1.2, 0), 6.0 - 3.0 * t, (int) (3 + t * 9), 14);
		}
		int bellEvery = t < 0.5 ? 30 : 20;
		if (tick % bellEvery == 0) {
			BloodFx.play(center, BELL, 1.2F, (float) (0.5 + 0.3 * t));
		}
		int heartEvery = t < 0.33 ? 20 : t < 0.66 ? 12 : 6;
		if (tick % heartEvery == 0) {
			BloodFx.play(center, BloodFx.HEARTBEAT, 1.0F, (float) (0.6 + 0.4 * t));
		}
		if (t > 0.7 && tick % 2 == 0) {
			// A column of blood rises at the altar.
			double height = (t - 0.7) / 0.3 * 3.5;
			Shapes.column(center, BloodFx.BLOOD, height, (int) (3 + height * 2), 0.12);
			BloodFx.burst(center.clone().add(0, 0.2, 0), BloodFx.MIST, 2, 0.6, 0.0);
		}
		if (tick == (int) (total * 0.6)) {
			BloodFx.play(center, RUMBLE, 1.4F, 0.8F);
		}
	}

	/** The moment it breaks the surface. */
	static void emerge(Location center) {
		Location floor = center.clone().add(0, 0.1, 0);
		BloodFx.burst(center.clone().add(0, 1.0, 0), BloodFx.NOVA, 16, 0.4, 0.02);
		Shapes.shockwave(floor, BloodFx.BLOOD_FADE, 10.0, 14);
		Shapes.rising(center, 2.5, 3.5, 24, 16);
		BloodFx.burst(center, BloodFx.SPLATTER, 50, 1.2, 0.35);
		BloodFx.burst(center, BloodFx.GORE, 25, 1.0, 0.3);
		BloodFx.play(center, SLAM, 1.3F, 0.55F);
		BloodFx.play(center, DIG, 1.4F, 0.7F);
	}

	static void roar(Location center) {
		BloodFx.play(center, ROAR, 1.6F, 0.6F);
		BloodFx.play(center, GROWL, 1.2F, 0.5F);
		Shapes.shockwave(center.clone().add(0, 0.15, 0), BloodFx.CLOT, 7.0, 10);
		BloodFx.burst(center.clone().add(0, 0.3, 0), BloodFx.RING, 3, 1.2);
	}

	// ---- attacks ------------------------------------------------------------------------------

	/** Telegraph for the cleave: the arc it's about to cut, drawn on the ground. */
	static void cleaveTelegraph(Location feet, double yawRadians, double radius, double halfAngle, float progress) {
		Location floor = feet.clone().add(0, 0.12, 0);
		Shapes.arc(floor, progress > 0.7F ? BloodFx.BLOOD_FADE : BloodFx.CLOT, radius, yawRadians, halfAngle, 16);
		Shapes.arc(floor, BloodFx.CLOT, radius * 0.55, yawRadians, halfAngle, 9);
	}

	static void cleaveStrike(Location feet, double yawRadians, double radius, double halfAngle) {
		Location mid = feet.clone().add(0, 1.2, 0);
		for (int i = 0; i < 3; i++) {
			Shapes.arc(mid.clone().add(0, i * 0.25 - 0.25, 0), BloodFx.BLOOD_FADE, radius * (0.75 + i * 0.12), yawRadians, halfAngle, 18);
		}
		Shapes.arc(feet.clone().add(0, 0.15, 0), BloodFx.SPLATTER, radius, yawRadians, halfAngle, 10);
		BloodFx.play(feet, SWING, 1.4F, 0.55F);
		BloodFx.play(feet, HEAVY_HIT, 1.2F, 0.7F);
	}

	/** Slam wind-up: blood pulled into its raised fists, and the outline of the wave to come. */
	static void slamWindup(Location feet, double radius, int tick) {
		if (tick % 3 == 0) {
			Shapes.converge(feet.clone().add(0, 3.0, 0), 3.0, 4, 10);
		}
		if (tick % 5 == 0) {
			BloodFx.ring(feet.clone().add(0, 0.1, 0), BloodFx.CLOT, radius, 40);
		}
	}

	static void slamImpact(Location feet) {
		Location floor = feet.clone().add(0, 0.1, 0);
		BloodFx.burst(feet.clone().add(0, 0.6, 0), BloodFx.NOVA, 14, 0.3, 0.02);
		BloodFx.burst(feet, BloodFx.SPLATTER, 40, 1.0, 0.4);
		BloodFx.burst(feet, BloodFx.BURST, 1, 0.0);
		Shapes.rising(feet, 1.5, 2.0, 10, 10);
		BloodFx.ring(floor, BloodFx.BLOOD_FADE, 1.2, 16);
		BloodFx.play(feet, SLAM, 1.4F, 0.6F);
		BloodFx.play(feet, HEAVY_HIT, 1.4F, 0.5F);
	}

	/** One tick of the slam's travelling wave. */
	static void slamWave(Location feet, double radius) {
		Location floor = feet.clone().add(0, 0.15, 0);
		int points = (int) Math.min(70, Math.max(12, radius * 8));
		BloodFx.ring(floor, BloodFx.BLOOD_FADE, radius, points);
		BloodFx.ring(floor.clone().add(0, 0.35, 0), BloodFx.BLOOD, radius - 0.2, points / 2);
	}

	/** A spike about to erupt under someone: a shrinking ring and blood welling up. */
	static void spikeTelegraph(Location spot, float progress) {
		Location floor = spot.clone().add(0, 0.1, 0);
		BloodFx.ring(floor, progress > 0.65F ? BloodFx.BLOOD_FADE : BloodFx.CLOT, 2.4 - 1.2 * progress, 14);
		BloodFx.burst(floor, BloodFx.DRIP, 2, 0.4, 0.0);
		if (progress > 0.5F) {
			BloodFx.burst(floor.clone().add(0, 0.3, 0), BloodFx.MOTE, 3, 0.3, 0.02);
		}
	}

	static void spikeErupt(Location spot) {
		Shapes.column(spot, BloodFx.BLOOD_FADE, 3.2, 12, 0.18);
		Shapes.column(spot, BloodFx.BLOOD, 2.2, 8, 0.1);
		BloodFx.burst(spot.clone().add(0, 0.8, 0), BloodFx.NOVA, 8, 0.2, 0.02);
		BloodFx.burst(spot, BloodFx.SPLATTER, 18, 0.5, 0.35);
		BloodFx.play(spot, SPIKE, 1.2F, 0.6F);
		BloodFx.play(spot, BloodFx.SQUELCH, 1.0F, 0.6F);
	}

	static void chargeTelegraph(Location from, double yawRadians, double length) {
		World world = from.getWorld();
		double dx = -Math.sin(yawRadians);
		double dz = Math.cos(yawRadians);
		for (double d = 1.0; d <= length; d += 0.8) {
			BloodFx.emit(world, BloodFx.CLOT, from.getX() + dx * d, from.getY() + 0.15, from.getZ() + dz * d, 1, 0.15, 0, 0.15, 0);
		}
	}

	static void chargeTrail(Location at) {
		BloodFx.burst(at.clone().add(0, 0.6, 0), BloodFx.BLOOD_FADE, 6, 0.4);
		BloodFx.burst(at, BloodFx.SPLATTER, 3, 0.3, 0.1);
	}

	static void hit(Location at) {
		BloodFx.burst(at, BloodFx.SPLATTER, 10, 0.3, 0.2);
		BloodFx.burst(at, BloodFx.BLOOD_FADE, 8, 0.3);
	}

	// ---- the fight's mood -----------------------------------------------------------------------

	/** Very low haze around someone in the arena, and the odd mote drifting up. */
	static void atmosphere(Location feet, long now) {
		ThreadLocalRandom random = ThreadLocalRandom.current();
		double angle = random.nextDouble() * Math.PI * 2;
		double r = 1.5 + random.nextDouble() * 3.0;
		Location haze = feet.clone().add(Math.cos(angle) * r, 0.15, Math.sin(angle) * r);
		BloodFx.ambient(haze, BloodFx.MIST, 1, 0.5);
		if (now % 40 == 0) {
			BloodFx.ambient(feet.clone().add(random.nextGaussian() * 3, 0.5 + random.nextDouble() * 2, random.nextGaussian() * 3),
				BloodFx.EMBER, 1, 0.3);
		}
	}

	/** The Knight's own aura: mist pooling at its feet, heavier once it's bloodied. */
	static void aura(Location feet, boolean bloodied) {
		BloodFx.ring(feet.clone().add(0, 0.1, 0), BloodFx.MIST, bloodied ? 2.2 : 1.6, bloodied ? 8 : 5);
		if (bloodied) {
			BloodFx.burst(feet.clone().add(0, 1.4, 0), BloodFx.DRIP, 3, 0.6, 0.0);
		}
	}

	// ---- endings ------------------------------------------------------------------------------

	/** A player killed in the arena: a pillar of their blood and a toll. */
	static void elimination(Location at) {
		Location floor = at.clone().add(0, 0.1, 0);
		BloodFx.burst(at.clone().add(0, 1.0, 0), BloodFx.NOVA, 6, 0.2, 0.02);
		BloodFx.ring(floor, BloodFx.BLOOD_FADE, 1.0, 16);
		Shapes.spiral(floor, BloodFx.EMBER, 0.6, 2.8, 2.0, 20, 0.0);
		TickScheduler.repeat(2, 2, 8, tick -> {
			Shapes.column(at, BloodFx.BLOOD_FADE, 1.2 + tick * 0.35, 4 + tick, 0.12);
			return true;
		});
		BloodFx.burst(at, BloodFx.SOUL, 6, 0.4, 0.05);
		BloodFx.play(at, BELL, 1.0F, 0.45F);
		BloodFx.play(at, CLAIMED, 0.8F, 0.6F);
	}

	/**
	 * The Blood Knight falls. {@code tick} runs through the death clip (64 ticks) and beyond; the
	 * big moment is when it hits the ground.
	 */
	static void victoryBeat(Location feet, int tick) {
		if (tick < 34 && tick % 3 == 0) {
			Shapes.spiral(feet.clone().add(0, 0.1, 0), BloodFx.EMBER, 1.4 - tick * 0.02, 3.0, 1.0, 8, tick * 0.6);
		}
		if (tick == 0) {
			BloodFx.play(feet, DEATH, 1.3F, 0.7F);
			BloodFx.play(feet, ROAR, 1.2F, 0.4F);
		}
		if (tick == 34) {
			BloodFx.play(feet, SLAM, 0.9F, 0.8F); // the knee hits the ground
			BloodFx.burst(feet, BloodFx.SPLATTER, 20, 0.8, 0.25);
		}
		if (tick == 54) {
			// It hits the ground: the finale.
			Location floor = feet.clone().add(0, 0.1, 0);
			BloodFx.burst(feet.clone().add(0, 1.0, 0), BloodFx.NOVA, 20, 0.6, 0.02);
			Shapes.shockwave(floor, BloodFx.BLOOD_FADE, 12.0, 16);
			Shapes.rising(feet, 3.0, 4.5, 30, 20);
			BloodFx.burst(feet, BloodFx.SPLATTER, 70, 1.5, 0.4);
			BloodFx.burst(feet, BloodFx.GORE, 30, 1.2, 0.3);
			BloodFx.play(feet, SLAM, 1.5F, 0.5F);
		}
		if (tick == 60 || tick == 72 || tick == 84) {
			BloodFx.play(feet, BELL, 1.4F, tick == 60 ? 0.6F : tick == 72 ? 0.5F : 0.4F);
		}
		if (tick > 54 && tick < 110 && tick % 4 == 0) {
			// Blood rain over the arena, briefly.
			ThreadLocalRandom random = ThreadLocalRandom.current();
			for (int i = 0; i < 4; i++) {
				BloodFx.burst(feet.clone().add(random.nextGaussian() * 5, 5 + random.nextDouble() * 2, random.nextGaussian() * 5),
					BloodFx.DRIP, 2, 0.4, 0.0);
			}
			BloodFx.ring(feet.clone().add(0, 0.05, 0), BloodFx.MIST, 2.5 + (tick - 54) * 0.03, 10);
		}
	}

	/** It gave up and sank back down (nobody left to fight, or it was stopped). */
	static void retreat(Location feet, int tick) {
		if (tick == 0) {
			BloodFx.play(feet, GROWL, 1.2F, 0.6F);
			BloodFx.play(feet, DIG, 1.2F, 0.8F);
		}
		if (tick % 3 == 0) {
			BloodFx.ring(feet.clone().add(0, 0.1, 0), BloodFx.CLOT, 1.8, 14);
			BloodFx.burst(feet, BloodFx.MIST, 3, 0.8, 0.0);
		}
	}
}
