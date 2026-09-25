package net.unchartedsmp.bloodbath.fx;

import java.util.concurrent.ThreadLocalRandom;
import net.unchartedsmp.bloodbath.ability.TickScheduler;
import net.unchartedsmp.bloodbath.config.Settings;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.util.Vector;

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
		Particles.Batch batch = BloodFx.shape(base.clone().add(0.0, height / 2, 0.0), Math.max(radius, height / 2));
		if (!batch.visible()) {
			return;
		}
		for (int i = 0; i < points; i++) {
			double t = i / (double) Math.max(1, points - 1);
			double angle = phase + t * turns * Math.PI * 2.0;
			batch.point(i, fx, base.getX() + Math.cos(angle) * radius, base.getY() + t * height, base.getZ() + Math.sin(angle) * radius,
				1, 0, 0, 0, 0);
		}
	}

	/** A vertical column of particles, slightly jittered so it reads as liquid rather than a line. */
	public static void column(Location base, BloodFx.Fx fx, double height, int points, double spread) {
		Particles.Batch batch = BloodFx.shape(base.clone().add(0.0, height / 2, 0.0), height / 2 + spread);
		if (!batch.visible()) {
			return;
		}
		for (int i = 0; i < points; i++) {
			double y = base.getY() + height * i / (double) Math.max(1, points - 1);
			batch.point(i, fx, base.getX(), y, base.getZ(), 1, spread, 0.05, spread, 0);
		}
	}

	/** An arc of a horizontal circle, centred on {@code yawRadians}, spanning {@code halfAngle} each side. */
	public static void arc(Location center, BloodFx.Fx fx, double radius, double yawRadians, double halfAngle, int points) {
		Particles.Batch batch = BloodFx.shape(center, radius);
		if (!batch.visible()) {
			return;
		}
		for (int i = 0; i < points; i++) {
			double angle = yawRadians - halfAngle + 2.0 * halfAngle * i / (double) Math.max(1, points - 1);
			batch.point(i, fx, center.getX() + Math.cos(angle) * radius, center.getY(), center.getZ() + Math.sin(angle) * radius,
				1, 0, 0, 0, 0);
		}
	}

	/**
	 * A filled crescent: a slash's whole swept band, bright along its leading edge and fading to
	 * clotted blood behind, thickest in the middle and tapering to points at both ends.
	 * {@code yawRadians} is the direction it faces (Minecraft yaw + 90°, in radians).
	 */
	public static void crescent(Location center, double radius, double yawRadians, double halfAngle, double thickness) {
		Particles.Batch batch = BloodFx.shape(center, radius);
		if (!batch.visible()) {
			return;
		}
		int along = (int) Math.max(10, radius * halfAngle * 7);
		int index = 0;
		for (int i = 0; i < along; i++) {
			double t = i / (double) (along - 1);
			double angle = yawRadians - halfAngle + 2.0 * halfAngle * t;
			double width = thickness * Math.sin(Math.PI * t); // tapered ends
			double lift = Math.sin(Math.PI * t) * 0.15;
			for (int k = 0; k < 3; k++) {
				double r = radius - width * k / 2.0;
				BloodFx.Fx fx = k == 0 ? BloodFx.SLASH : k == 1 ? BloodFx.BLOOD_FADE : BloodFx.CLOT;
				batch.point(index++, fx, center.getX() + Math.cos(angle) * r, center.getY() + lift - k * 0.05,
					center.getZ() + Math.sin(angle) * r, 1, 0, 0, 0, 0);
			}
		}
	}

	/**
	 * Two strands of blood twisting round each other from {@code from} to {@code to}: a chain, a
	 * drain, a beam. {@code phase} turns it (advance it each tick and the twist flows along).
	 */
	public static void helix(Location from, Location to, BloodFx.Fx fx, double radius, double turnsPerBlock, double perBlock, double phase) {
		Vector axis = to.toVector().subtract(from.toVector());
		double length = axis.length();
		if (length < 1.0E-3) {
			return;
		}
		Location mid = from.clone().add(axis.clone().multiply(0.5));
		Particles.Batch batch = BloodFx.shape(mid, length / 2 + radius);
		if (!batch.visible()) {
			return;
		}
		axis.multiply(1.0 / length);
		// Two directions perpendicular to the axis.
		Vector side = Math.abs(axis.getY()) > 0.9 ? new Vector(1, 0, 0) : new Vector(0, 1, 0);
		Vector u = axis.getCrossProduct(side).normalize();
		Vector v = axis.getCrossProduct(u).normalize();
		int steps = (int) Math.max(4, length * perBlock * Math.min(1.0, Settings.get().particleMultiplier));
		int index = 0;
		for (int i = 0; i <= steps; i++) {
			double t = i / (double) steps;
			double angle = phase + t * length * turnsPerBlock * Math.PI * 2.0;
			for (int strand = 0; strand < 2; strand++) {
				double a = angle + strand * Math.PI;
				double ox = (u.getX() * Math.cos(a) + v.getX() * Math.sin(a)) * radius;
				double oy = (u.getY() * Math.cos(a) + v.getY() * Math.sin(a)) * radius;
				double oz = (u.getZ() * Math.cos(a) + v.getZ() * Math.sin(a)) * radius;
				batch.point(index++, fx, from.getX() + axis.getX() * t * length + ox, from.getY() + axis.getY() * t * length + oy,
					from.getZ() + axis.getZ() * t * length + oz, 1, 0, 0, 0, 0);
			}
		}
	}

	/** The upper half of a sphere: a field's wall and roof, seen from anywhere around it. */
	public static void dome(Location center, BloodFx.Fx fx, double radius, int rings, int perRing, double phase) {
		Particles.Batch batch = BloodFx.shape(center.clone().add(0.0, radius / 2, 0.0), radius);
		if (!batch.visible()) {
			return;
		}
		int index = 0;
		for (int r = 0; r < rings; r++) {
			double elevation = (Math.PI / 2) * r / rings;
			double ringRadius = radius * Math.cos(elevation);
			double y = center.getY() + radius * Math.sin(elevation);
			int n = Math.max(4, (int) Math.round(perRing * Math.cos(elevation)));
			for (int i = 0; i < n; i++) {
				double angle = phase + (Math.PI * 2.0 * i) / n + r * 0.4;
				batch.point(index++, fx, center.getX() + Math.cos(angle) * ringRadius, y, center.getZ() + Math.sin(angle) * ringRadius,
					1, 0, 0, 0, 0);
			}
		}
	}

	/**
	 * Blood swirling inward and down toward {@code center} over {@code ticks}: the read for "this
	 * pulls". A few arms of a spiral wind in and tighten each tick.
	 */
	public static void vortex(Location center, double radius, int arms, int ticks) {
		Location c = center.clone();
		TickScheduler.repeat(0, 2, Math.max(1, ticks / 2), tick -> {
			double t = tick * 2.0 / Math.max(1, ticks);
			Particles.Batch batch = BloodFx.shape(c, radius);
			if (!batch.visible()) {
				return true;
			}
			int index = 0;
			for (int arm = 0; arm < arms; arm++) {
				for (int k = 0; k < 6; k++) {
					double s = k / 5.0;
					double r = radius * (1.0 - s) * (1.0 - t * 0.3) + 0.2;
					double angle = arm * Math.PI * 2.0 / arms + s * 2.4 + tick * 0.45;
					BloodFx.Fx fx = k < 2 ? BloodFx.BLOOD_FADE : k < 4 ? BloodFx.BLOOD : BloodFx.EMBER;
					batch.point(index++, fx, c.getX() + Math.cos(angle) * r, c.getY() + 0.1 + s * 0.6, c.getZ() + Math.sin(angle) * r, 1, 0, 0, 0, 0);
				}
			}
			return true;
		});
	}

	/**
	 * A clock face on the ground: twelve marks round the rim and a hand pointing at how much of the
	 * window is left ({@code fraction}, 1 = all of it).
	 */
	public static void clock(Location center, double radius, double fraction) {
		Particles.Batch batch = BloodFx.shape(center, radius);
		if (!batch.visible()) {
			return;
		}
		int index = 0;
		for (int i = 0; i < 12; i++) {
			double angle = Math.PI * 2.0 * i / 12 - Math.PI / 2;
			BloodFx.Fx fx = i / 12.0 < fraction ? BloodFx.BLOOD_FADE : BloodFx.CLOT;
			batch.point(index++, fx, center.getX() + Math.cos(angle) * radius, center.getY(), center.getZ() + Math.sin(angle) * radius, 1, 0, 0, 0, 0);
		}
		double hand = Math.PI * 2.0 * fraction - Math.PI / 2;
		for (int k = 1; k <= 4; k++) {
			double r = radius * k / 5.0;
			batch.point(index++, BloodFx.EMBER, center.getX() + Math.cos(hand) * r, center.getY() + 0.02, center.getZ() + Math.sin(hand) * r, 1, 0, 0, 0, 0);
		}
	}

	/** Motes circling an entity at chest height, one per {@code count}: stacks you can count. */
	public static void orbit(Location chest, int count, double radius, double phase) {
		if (count <= 0) {
			return;
		}
		Particles.Batch batch = BloodFx.shape(chest, radius);
		if (!batch.visible()) {
			return;
		}
		for (int i = 0; i < count; i++) {
			double angle = phase + Math.PI * 2.0 * i / count;
			batch.point(i, BloodFx.EMBER, chest.getX() + Math.cos(angle) * radius, chest.getY() + Math.sin(phase * 2 + i) * 0.15,
				chest.getZ() + Math.sin(angle) * radius, 1, 0, 0, 0, 0);
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
