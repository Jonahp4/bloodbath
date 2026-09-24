package net.unchartedsmp.bloodbath.fx;

import java.util.concurrent.ThreadLocalRandom;
import net.unchartedsmp.bloodbath.ability.TickScheduler;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.World;

/**
 * Particle shapes the bigger effects are composed from: rings, spirals, columns, arcs, rising
 * streams and animated shockwaves. Each is deterministic (same inputs, same picture) apart from
 * where noted, sized by the caller, and goes through {@link BloodFx#emit} so it gets the same
 * view-distance filtering and level of detail as everything else.
 *
 * <p>Point counts are per shape, not per viewer: a 24-point ring is 24 spawn calls whoever is
 * watching. Callers keep counts modest and let the multiplier scale them down further.
 */
public final class Shapes {
	private Shapes() {
	}

	/** A helix around a vertical axis, {@code turns} times round over {@code height}. */
	public static void spiral(Location base, BloodFx.Fx fx, double radius, double height, double turns, int points, double phase) {
		World world = base.getWorld();
		for (int i = 0; i < points; i++) {
			double t = i / (double) Math.max(1, points - 1);
			double angle = phase + t * turns * Math.PI * 2.0;
			BloodFx.emit(world, fx, base.getX() + Math.cos(angle) * radius, base.getY() + t * height, base.getZ() + Math.sin(angle) * radius,
				1, 0, 0, 0, 0);
		}
	}

	/** A vertical column of particles, slightly jittered so it reads as liquid rather than a line. */
	public static void column(Location base, BloodFx.Fx fx, double height, int points, double spread) {
		World world = base.getWorld();
		for (int i = 0; i < points; i++) {
			double y = base.getY() + height * i / (double) Math.max(1, points - 1);
			BloodFx.emit(world, fx, base.getX(), y, base.getZ(), 1, spread, 0.05, spread, 0);
		}
	}

	/** An arc of a horizontal circle, centred on {@code yawRadians}, spanning {@code halfAngle} each side. */
	public static void arc(Location center, BloodFx.Fx fx, double radius, double yawRadians, double halfAngle, int points) {
		World world = center.getWorld();
		for (int i = 0; i < points; i++) {
			double angle = yawRadians - halfAngle + 2.0 * halfAngle * i / (double) Math.max(1, points - 1);
			BloodFx.emit(world, fx, center.getX() + Math.cos(angle) * radius, center.getY(), center.getZ() + Math.sin(angle) * radius,
				1, 0, 0, 0, 0);
		}
	}

	/**
	 * A ring that expands from nothing to {@code maxRadius} over {@code ticks}, with a thin lagging
	 * inner ring: the classic shockwave read. Point count grows with the circumference so the
	 * ring's density stays even.
	 */
	public static void shockwave(Location center, BloodFx.Fx fx, double maxRadius, int ticks) {
		Location c = center.clone();
		TickScheduler.repeat(0, 1, Math.max(1, ticks), tick -> {
			double t = (tick + 1) / (double) ticks;
			double eased = 1.0 - (1.0 - t) * (1.0 - t);
			double radius = maxRadius * eased;
			int points = (int) Math.min(64, Math.max(10, radius * 7));
			BloodFx.ring(c, fx, radius, points);
			if (radius > 1.0) {
				BloodFx.ring(c.clone().add(0, 0.15, 0), BloodFx.CLOT, radius * 0.82, Math.max(8, points / 2));
			}
			return true;
		});
	}

	/** Blood streaming upwards from a disc of {@code radius} (vanilla trail particles). */
	public static void rising(Location center, double radius, double height, int streams, int durationTicks) {
		ThreadLocalRandom random = ThreadLocalRandom.current();
		for (int i = 0; i < streams; i++) {
			double angle = random.nextDouble() * Math.PI * 2.0;
			double r = Math.sqrt(random.nextDouble()) * radius;
			Location from = center.clone().add(Math.cos(angle) * r, 0.0, Math.sin(angle) * r);
			Location to = from.clone().add(0.0, height * (0.6 + random.nextDouble() * 0.4), 0.0);
			BloodFx.flow(from, to, 1, 0.02, i % 3 == 0 ? BloodFx.BRIGHT_RED : BloodFx.BLOOD_RED, durationTicks);
		}
	}

	/**
	 * Blood drawn in from all around to a point, over {@code durationTicks}: the "something is
	 * gathering here" read, used for every wind-up.
	 */
	public static void converge(Location center, double radius, int streams, int durationTicks) {
		ThreadLocalRandom random = ThreadLocalRandom.current();
		for (int i = 0; i < streams; i++) {
			double yaw = random.nextDouble() * Math.PI * 2.0;
			double pitch = (random.nextDouble() - 0.35) * Math.PI * 0.6;
			Location from = center.clone().add(Math.cos(yaw) * Math.cos(pitch) * radius, Math.sin(pitch) * radius,
				Math.sin(yaw) * Math.cos(pitch) * radius);
			BloodFx.flow(from, center, 1, 0.05, i % 2 == 0 ? BloodFx.BRIGHT_RED : BloodFx.BLOOD_RED, durationTicks);
		}
	}

	/** An impact: a nova flash, a flat ring and a few rising drops. */
	public static void impact(Location at, double radius) {
		BloodFx.burst(at, BloodFx.NOVA, 12, 0.3, 0.02);
		BloodFx.ring(at.clone().add(0, 0.1, 0), BloodFx.BLOOD_FADE, radius, (int) Math.max(12, radius * 8));
		rising(at, radius * 0.5, 1.2, 5, 12);
	}

	/** Colour helper for callers building their own trail colours. */
	public static Color mix(Color a, Color b, double t) {
		return Color.fromRGB((int) (a.getRed() + (b.getRed() - a.getRed()) * t), (int) (a.getGreen() + (b.getGreen() - a.getGreen()) * t),
			(int) (a.getBlue() + (b.getBlue() - a.getBlue()) * t));
	}
}
