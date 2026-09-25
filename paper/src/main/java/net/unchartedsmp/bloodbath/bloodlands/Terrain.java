package net.unchartedsmp.bloodbath.bloodlands;

import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.util.noise.SimplexOctaveGenerator;

/**
 * The shape of the Bloodlands, as pure functions of (x, z) and the seed: no state is kept between
 * calls, so chunks can be generated on any number of threads and always come out the same.
 *
 * <p>Plains at heart: a gently rolling ground around y 70, rising into rounded hills in some
 * regions, cut by broad dry valleys and, here and there, stepped into low cliffs. Blood lakes are
 * scattered rarely enough that finding a big one is an event: small ponds, medium and large lakes,
 * and the odd giant, each an irregular blob (a warped circle) whose shores slope down to the water.
 * Little blood pools dot the plains between them, and rare clusters of old dark-rock spires stand
 * out of the grass.
 */
public final class Terrain {
	public static final int BASE = 70;

	/** What one column of the Bloodlands is. */
	public enum Kind {
		LAND, SHORE, LAKE, POOL, SPIRE
	}

	/**
	 * @param height y of the topmost solid block
	 * @param water y of the water surface (the highest water block), or {@link Integer#MIN_VALUE}; on a
	 *     shore, the level of the water it holds
	 * @param spire how many blocks of spire rock stand above the ground (0 = none)
	 * @param lakeDepth 0..1: how far into a lake or pool this is (0 on dry land)
	 */
	public record Column(int height, int water, Kind kind, int spire, double lakeDepth) {
		/** Water stands on this column (only ever in a lake or a pool, never on a shore). */
		public boolean wet() {
			return (kind == Kind.LAKE || kind == Kind.POOL) && water > height;
		}
	}

	private record Basin(double cx, double cz, double radius, int level, double depth, double shore, boolean pool) {
	}

	private static final int LAKE_CELL = 320;
	private static final int POOL_CELL = 64;
	private static final int SPIRE_CELL = 112;

	private final long seed;
	private final double lakeFrequency;
	private final SimplexOctaveGenerator continental;
	private final SimplexOctaveGenerator hills;
	private final SimplexOctaveGenerator hillMask;
	private final SimplexOctaveGenerator detail;
	private final SimplexOctaveGenerator valleys;
	private final SimplexOctaveGenerator cliffMask;
	private final SimplexOctaveGenerator warpX;
	private final SimplexOctaveGenerator warpZ;
	private final SimplexOctaveGenerator patches;
	private final SimplexOctaveGenerator fine;

	public Terrain(long seed, double lakeFrequency) {
		this.seed = seed;
		this.lakeFrequency = lakeFrequency;
		continental = octaves(1, 3, 1.0 / 700);
		hills = octaves(2, 4, 1.0 / 160);
		hillMask = octaves(3, 2, 1.0 / 520);
		detail = octaves(4, 3, 1.0 / 42);
		valleys = octaves(5, 3, 1.0 / 460);
		cliffMask = octaves(6, 2, 1.0 / 330);
		warpX = octaves(7, 2, 1.0 / 70);
		warpZ = octaves(8, 2, 1.0 / 70);
		patches = octaves(9, 3, 1.0 / 28);
		fine = octaves(10, 2, 1.0 / 6);
	}

	private SimplexOctaveGenerator octaves(int salt, int count, double scale) {
		SimplexOctaveGenerator generator = new SimplexOctaveGenerator(seed * 31 + salt * 0x9E3779B97F4A7C15L, count);
		generator.setScale(scale);
		return generator;
	}

	private static double n(SimplexOctaveGenerator g, double x, double z) {
		return g.noise(x, z, 0.5, 0.5, true);
	}

	private static double smooth(double a, double b, double v) {
		double t = Math.max(0.0, Math.min(1.0, (v - a) / (b - a)));
		return t * t * (3 - 2 * t);
	}

	/** The land before lakes and spires: plains, hills, valleys and cliffs. */
	public double ground(double x, double z) {
		double c = n(continental, x, z);
		// Where the hills are: about half the land rolls, the rest is open plain.
		double mask = smooth(-0.3, 0.4, n(hillMask, x, z));
		double hill = n(hills, x, z);
		hill = hill > 0 ? Math.pow(hill, 1.15) : -Math.pow(-hill, 1.5) * 0.6;
		double roll = n(detail, x * 0.3, z * 0.3);
		double h = BASE + c * 10.0 + hill * (5.0 + mask * 34.0) + roll * (2.5 + mask * 3.0) + n(detail, x, z) * 1.3;
		// Broad dry valleys where the valley noise crosses zero.
		double v = Math.abs(n(valleys, x, z));
		if (v < 0.16) {
			double t = 1.0 - v / 0.16;
			h -= t * t * (8.0 + mask * 10.0);
		}
		// Terraces: stepped cliffs in some regions.
		double cm = n(cliffMask, x, z);
		if (cm > 0.12) {
			double blend = smooth(0.12, 0.35, cm);
			double step = 6.0;
			double t = h / step;
			double f = t - Math.floor(t);
			double stepped = (Math.floor(t) + smooth(0.72, 0.86, f)) * step;
			h = h + (stepped - h) * blend;
		}
		return h;
	}

	public Column column(int x, int z) {
		double ground = ground(x, z);
		Basin lake = nearestBasin(x, z, false);
		Basin pool = lake == null ? nearestBasin(x, z, true) : null;
		Basin basin = lake != null ? lake : pool;
		if (basin != null) {
			double d = basinDistance(basin, x, z);
			double rim = 1.0 + Math.max((basin.shore() - 1.0) * 0.25, 1.5 / basin.radius());
			if (d < 1.0 && !contained(basin, x, z, rim)) {
				// Water here could reach land lower than itself: it stays a dry rim at the water line.
				return new Column(basin.level(), basin.level(), Kind.SHORE, 0, 0.0);
			}
			if (d < 1.0) {
				double bowl = (1.0 - d * d) * basin.depth() + n(fine, x, z) * (basin.pool() ? 0.3 : 0.8);
				int bottom = (int) Math.min(Math.floor(ground), Math.floor(basin.level() - 1 - bowl));
				return new Column(bottom, basin.level(), basin.pool() ? Kind.POOL : Kind.LAKE, 0, 1.0 - d);
			}
			if (d < basin.shore()) {
				double t = smooth(1.0, basin.shore(), d);
				double h = basin.level() + (ground - basin.level()) * t;
				if (d < rim) {
					h = Math.max(h, basin.level()); // the rim always holds the water in
				}
				return new Column((int) Math.floor(h), basin.level(), Kind.SHORE, 0, 0.0);
			}
		}
		int height = (int) Math.floor(ground);
		int spire = spire(x, z);
		return new Column(height, Integer.MIN_VALUE, spire > 0 ? Kind.SPIRE : Kind.LAND, spire, 0.0);
	}

	/** Surface patch noise (-1..1) for picking grass, coarse dirt, podzol... */
	public double patch(int x, int z) {
		return n(patches, x, z);
	}

	/** High-frequency noise (-1..1) for scattering. */
	public double grain(int x, int z) {
		return n(fine, x, z);
	}

	// ---- lakes and pools ------------------------------------------------------------------

	private Basin nearestBasin(int x, int z, boolean pools) {
		int cell = pools ? POOL_CELL : LAKE_CELL;
		int cx = Math.floorDiv(x, cell);
		int cz = Math.floorDiv(z, cell);
		Basin best = null;
		double bestD = Double.MAX_VALUE;
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				Basin basin = basin(cx + dx, cz + dz, pools);
				if (basin == null) {
					continue;
				}
				double d = basinDistance(basin, x, z);
				if (d < basin.shore() && d < bestD) {
					best = basin;
					bestD = d;
				}
			}
		}
		return best;
	}

	/** Basins are costly to work out (they sample the ground) and asked for by every column near them. */
	private final ConcurrentHashMap<Long, Basin> basins = new ConcurrentHashMap<>();
	private static final Basin NONE = new Basin(0, 0, 1, 0, 0, 0, false);

	private Basin basin(int cx, int cz, boolean pool) {
		long key = ((long) cx << 33) ^ ((cz & 0xFFFFFFFFL) << 1) ^ (pool ? 1 : 0);
		Basin cached = basins.get(key);
		if (cached == null) {
			if (basins.size() > 8192) {
				basins.clear();
			}
			Basin computed = computeBasin(cx, cz, pool);
			cached = computed == null ? NONE : computed;
			basins.put(key, cached);
		}
		return cached == NONE ? null : cached;
	}

	/** The lake (or pool) of a grid cell, if it has one. */
	private Basin computeBasin(int cx, int cz, boolean pool) {
		int cell = pool ? POOL_CELL : LAKE_CELL;
		long h = hash(cx, cz, pool ? 101 : 202);
		double chance = pool ? 0.32 : 0.5 * lakeFrequency;
		if (unit(h, 0) >= Math.min(1.0, chance)) {
			return null;
		}
		double margin = pool ? 12 : 60;
		double centerX = cx * (double) cell + margin + unit(h, 1) * (cell - 2 * margin);
		double centerZ = cz * (double) cell + margin + unit(h, 2) * (cell - 2 * margin);
		double radius;
		double depth;
		if (pool) {
			radius = 2.5 + unit(h, 3) * 4.0;
			depth = 1.0 + unit(h, 4) * 1.8;
		} else {
			double size = unit(h, 3);
			if (size < 0.07) {
				radius = 52 + unit(h, 4) * 36; // a giant: rare
			} else if (size < 0.35) {
				radius = 28 + unit(h, 4) * 16;
			} else if (size < 0.75) {
				radius = 15 + unit(h, 4) * 10;
			} else {
				radius = 7 + unit(h, 4) * 6;
			}
			depth = 2.5 + radius / 7.0;
		}
		double centerGround = ground(centerX, centerZ);
		// The water settles at the lowest point of the rim, so it never stands above the land around it.
		double rim = centerGround;
		double high = centerGround;
		int samples = pool ? 8 : 20;
		for (int i = 0; i < samples; i++) {
			double angle = i * Math.PI * 2 / samples;
			for (double k : new double[] {0.95, 1.25}) {
				double g = ground(centerX + Math.cos(angle) * radius * k, centerZ + Math.sin(angle) * radius * k);
				rim = Math.min(rim, g);
				high = Math.max(high, g);
			}
		}
		// Too rough for standing water (a hillside, a cliff): no lake here.
		if (high - rim > (pool ? 4.5 : 14.0 + radius * 0.3)) {
			return null;
		}
		int level = (int) Math.floor(rim) - 1;
		double shore = pool ? 1.8 : 1.0 + Math.min(0.9, 14.0 / radius + 0.25);
		return new Basin(centerX, centerZ, radius, level, depth, shore, pool);
	}

	/** Every neighbour of a water column is water or rim (which never sits below the water). */
	private boolean contained(Basin basin, int x, int z, double rim) {
		return basinDistance(basin, x + 1, z) < rim && basinDistance(basin, x - 1, z) < rim
			&& basinDistance(basin, x, z + 1) < rim && basinDistance(basin, x, z - 1) < rim;
	}

	/** Distance from the basin's centre in radii, through a warp that makes the outline irregular. */
	private double basinDistance(Basin basin, double x, double z) {
		// Two scales of warp: broad lobes and bays, then a ragged shoreline.
		double r = basin.radius();
		double broad = r * 0.25;
		// Sampled so a lake of any size gets two or three lobes around its shore, and gently enough
		// that the outline never folds back on itself.
		double s = 28.0 / Math.max(4.0, r);
		double wx = x + n(warpX, x * s + basin.cx(), z * s) * broad + n(warpX, x, z) * Math.min(6.0, r * 0.18);
		double wz = z + n(warpZ, x * s, z * s + basin.cz()) * broad + n(warpZ, x, z) * Math.min(6.0, r * 0.18);
		double dx = wx - basin.cx();
		double dz = wz - basin.cz();
		return Math.sqrt(dx * dx + dz * dz) / basin.radius();
	}

	// ---- spires -----------------------------------------------------------------------------

	/** Blocks of spire rock above the ground here (0 = none). */
	private int spire(int x, int z) {
		int cx = Math.floorDiv(x, SPIRE_CELL);
		int cz = Math.floorDiv(z, SPIRE_CELL);
		int best = 0;
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				long h = hash(cx + dx, cz + dz, 303);
				if (unit(h, 0) >= 0.16) {
					continue;
				}
				int count = 1 + (int) (unit(h, 1) * 3);
				double baseX = (cx + dx) * (double) SPIRE_CELL + 24 + unit(h, 2) * (SPIRE_CELL - 48);
				double baseZ = (cz + dz) * (double) SPIRE_CELL + 24 + unit(h, 3) * (SPIRE_CELL - 48);
				for (int i = 0; i < count; i++) {
					double sx = baseX + (unit(h, 4 + i) - 0.5) * 18;
					double sz = baseZ + (unit(h, 8 + i) - 0.5) * 18;
					double radius = 1.6 + unit(h, 12 + i) * 3.2;
					double tall = 7 + unit(h, 16 + i) * 18 * (radius / 4.8);
					double d = Math.hypot(x + 0.5 - sx, z + 0.5 - sz) / radius;
					if (d < 1.0) {
						double rough = 1.0 + n(fine, x * 3.1, z * 3.1) * 0.12;
						best = Math.max(best, (int) (tall * Math.pow(1.0 - d, 0.7) * rough));
					}
				}
			}
		}
		return best;
	}

	// ---- hashing ----------------------------------------------------------------------------

	private long hash(int cx, int cz, int salt) {
		long h = seed ^ (cx * 0x632BE59BD9B4E019L) ^ (cz * 0x9E3779B97F4A7C15L) ^ (salt * 0xC2B2AE3D27D4EB4FL);
		h ^= h >>> 33;
		h *= 0xFF51AFD7ED558CCDL;
		h ^= h >>> 33;
		h *= 0xC4CEB9FE1A85EC53L;
		h ^= h >>> 33;
		return h;
	}

	/** The {@code i}-th independent number in [0, 1) from a hash. */
	private static double unit(long h, int i) {
		long v = h + i * 0x9E3779B97F4A7C15L;
		v ^= v >>> 31;
		v *= 0xBF58476D1CE4E5B9L;
		v ^= v >>> 29;
		v *= 0x94D049BB133111EBL;
		v ^= v >>> 32;
		return (v >>> 11) * 0x1.0p-53;
	}

	/** A per-position random number in [0, 1), for decoration. */
	public double random(int x, int z, int salt) {
		return unit(hash(x, z, salt), 0);
	}
}
