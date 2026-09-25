package net.unchartedsmp.bloodbath.bloodlands;

import java.util.List;
import java.util.Random;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.type.Leaves;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.LimitedRegion;
import org.bukkit.generator.WorldInfo;

/**
 * Builds the Bloodlands from {@link Terrain}: stone and deepslate under a skin of dirt and red grass,
 * blood lakes and pools with clotted beds, dark rock spires, then (in the populator) the crimson
 * grass, roots, flowers, dead scrub, bare and red-leaved trees, and the odd ancient rib cage.
 *
 * <p>Caves, ores and mob spawns are vanilla's, from the Bloodlands biome. Everything here is
 * deterministic and thread-safe, as Paper generates chunks in parallel.
 */
public final class BloodlandsGenerator extends ChunkGenerator {
	private final Terrain terrain;
	private final BiomeProvider biomes;
	private final boolean customBiome;

	public BloodlandsGenerator(Terrain terrain, Biome biome, boolean customBiome) {
		this.terrain = terrain;
		this.customBiome = customBiome;
		this.biomes = new BiomeProvider() {
			@Override
			public Biome getBiome(WorldInfo worldInfo, int x, int y, int z) {
				return biome;
			}

			@Override
			public List<Biome> getBiomes(WorldInfo worldInfo) {
				return List.of(biome);
			}
		};
	}

	public Terrain terrain() {
		return terrain;
	}

	@Override
	public void generateNoise(WorldInfo world, Random random, int chunkX, int chunkZ, ChunkData data) {
		int minY = data.getMinHeight();
		int maxY = data.getMaxHeight();
		int baseX = chunkX << 4;
		int baseZ = chunkZ << 4;
		// Heights one block beyond the chunk, for slopes at its edges.
		Terrain.Column[][] columns = new Terrain.Column[18][18];
		for (int dx = -1; dx <= 16; dx++) {
			for (int dz = -1; dz <= 16; dz++) {
				columns[dx + 1][dz + 1] = terrain.column(baseX + dx, baseZ + dz);
			}
		}
		for (int x = 0; x < 16; x++) {
			for (int z = 0; z < 16; z++) {
				Terrain.Column c = columns[x + 1][z + 1];
				int wx = baseX + x;
				int wz = baseZ + z;
				int slope = 0;
				for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
					slope = Math.max(slope, Math.abs(c.height() - columns[x + 1 + d[0]][z + 1 + d[1]].height()));
				}
				int top = Math.min(maxY - 2, c.height());
				int bedrockTop = minY + (int) (terrain.random(wx, wz, 7) * 4);
				int deepslate = (int) (terrain.random(wx, wz, 8) * 6);
				for (int y = minY; y <= top; y++) {
					Material m;
					if (y <= bedrockTop) {
						m = Material.BEDROCK;
					} else if (y < deepslate) {
						m = Material.DEEPSLATE;
					} else if (y <= top - 4) {
						m = Material.STONE;
					} else {
						m = subsurface(c, slope, wx, y, wz, top - y);
					}
					data.setBlock(x, y, z, m);
				}
				if (c.spire() > 0) {
					for (int y = top + 1; y <= Math.min(maxY - 2, top + c.spire()); y++) {
						data.setBlock(x, y, z, spireRock(wx, y, wz));
					}
				}
				if (c.wet()) {
					for (int y = top + 1; y <= Math.min(maxY - 2, c.water()); y++) {
						data.setBlock(x, y, z, Material.WATER);
					}
				}
			}
		}
	}

	/** The top few blocks of a column: {@code depth} 0 is the surface. */
	private Material subsurface(Terrain.Column c, int slope, int x, int y, int z, int depth) {
		switch (c.kind()) {
			case LAKE, POOL -> {
				if (depth == 0) {
					double p = terrain.patch(x, z);
					if (c.kind() == Terrain.Kind.POOL ? c.lakeDepth() > 0.45 : p > 0.35 && c.lakeDepth() > 0.2) {
						return Material.NETHER_WART_BLOCK; // clotted blood
					}
					return p < -0.55 ? Material.CLAY : Material.MUD;
				}
				return depth < 3 ? Material.MUD : Material.DIRT;
			}
			case SHORE -> {
				if (depth == 0 && c.height() <= c.water()) {
					return terrain.grain(x, z) > 0.2 ? Material.PACKED_MUD : Material.MUD;
				}
				// One block up the bank the mud comes and goes in patches, so the shore isn't a ring.
				if (depth == 0 && c.height() == c.water() + 1) {
					double p = terrain.patch(x * 3, z * 3) + terrain.grain(x, z) * 0.35;
					if (p > 0.1) {
						return p > 0.35 ? Material.MUD : Material.COARSE_DIRT;
					}
				}
			}
			default -> {
			}
		}
		if (slope >= 4) {
			// Cliff faces: bare banded rock.
			int band = Math.floorMod(y + (int) (terrain.grain(x / 3, z / 3) * 2), 7);
			return band < 2 ? Material.TUFF : band == 4 ? Material.ANDESITE : Material.STONE;
		}
		if (depth > 0) {
			return c.kind() == Terrain.Kind.SPIRE && depth < 2 ? Material.COARSE_DIRT : Material.DIRT;
		}
		double p = terrain.patch(x, z);
		if (c.kind() == Terrain.Kind.SPIRE) {
			return Material.COARSE_DIRT;
		}
		if (p > 0.56) {
			return Material.COARSE_DIRT;
		}
		if (p < -0.6) {
			return Material.PODZOL;
		}
		if (terrain.grain(x, z) > 0.86) {
			return Material.ROOTED_DIRT;
		}
		return Material.GRASS_BLOCK;
	}

	private Material spireRock(int x, int y, int z) {
		int band = Math.floorMod(y + (int) (terrain.random(x, z, 21) * 3), 9);
		if (band < 3) {
			return Material.BLACKSTONE;
		}
		if (band < 5) {
			return Material.BASALT;
		}
		if (band == 5) {
			return Material.TUFF;
		}
		return terrain.random(x, y * 7 + z, 22) < 0.12 ? Material.GILDED_BLACKSTONE : Material.BLACKSTONE;
	}

	@Override
	public BiomeProvider getDefaultBiomeProvider(WorldInfo worldInfo) {
		return biomes;
	}

	@Override
	public List<BlockPopulator> getDefaultPopulators(World world) {
		return List.of(new Vegetation());
	}

	@Override
	public boolean shouldGenerateCaves() {
		return true;
	}

	/** Ores, geodes and dungeons come from the Bloodlands biome; with a stand-in biome they'd bring its trees too. */
	@Override
	public boolean shouldGenerateDecorations() {
		return customBiome;
	}

	@Override
	public boolean shouldGenerateMobs() {
		return customBiome;
	}

	@Override
	public boolean shouldGenerateStructures() {
		return false;
	}

	@Override
	public Location getFixedSpawnLocation(World world, Random random) {
		int[] spot = findDryLand(0, 0, 256);
		return new Location(world, spot[0] + 0.5, spot[1] + 1, spot[2] + 0.5);
	}

	/** Dry, level, open ground near (x, z): {x, top y, z}. */
	public int[] findDryLand(int x, int z, int searchRadius) {
		for (int r = 0; r <= searchRadius; r += 4) {
			for (int i = 0; i < Math.max(1, r * 2); i++) {
				double angle = i * Math.PI * 2 / Math.max(1, r * 2);
				int cx = x + (int) Math.round(Math.cos(angle) * r);
				int cz = z + (int) Math.round(Math.sin(angle) * r);
				Terrain.Column c = terrain.column(cx, cz);
				if (c.kind() != Terrain.Kind.LAND) {
					continue;
				}
				boolean level = true;
				for (int dx = -4; dx <= 4 && level; dx += 4) {
					for (int dz = -4; dz <= 4 && level; dz += 4) {
						Terrain.Column n = terrain.column(cx + dx, cz + dz);
						level = n.kind() == Terrain.Kind.LAND && Math.abs(n.height() - c.height()) <= 2;
					}
				}
				if (level) {
					return new int[] {cx, c.height(), cz};
				}
			}
		}
		return new int[] {x, terrain.column(x, z).height(), z};
	}

	// ---- vegetation ----------------------------------------------------------------------------

	/** Crimson grass and everything that grows in it. */
	private final class Vegetation extends BlockPopulator {
		@Override
		public void populate(WorldInfo world, Random random, int chunkX, int chunkZ, LimitedRegion region) {
			int baseX = chunkX << 4;
			int baseZ = chunkZ << 4;
			for (int x = 0; x < 16; x++) {
				for (int z = 0; z < 16; z++) {
					plant(region, baseX + x, baseZ + z);
				}
			}
			long h = (long) chunkX * 341873128712L + chunkZ * 132897987541L;
			double roll = terrain.random(chunkX, chunkZ, 41);
			if (roll < 0.22) {
				tree(region, baseX + 4 + (int) (terrain.random(chunkX, chunkZ, 42) * 8), baseZ + 4 + (int) (terrain.random(chunkX, chunkZ, 43) * 8),
					roll < 0.09);
			} else if (roll > 0.9975) {
				ribs(region, baseX + 3, baseZ + 3 + (int) (terrain.random(chunkX, chunkZ, 44) * 10), (h & 1) == 0);
			}
		}

		private void plant(LimitedRegion region, int x, int z) {
			Terrain.Column c = terrain.column(x, z);
			if (c.wet() || c.kind() == Terrain.Kind.SPIRE) {
				return;
			}
			int y = c.height();
			if (!region.isInRegion(x, y + 2, z)) {
				return;
			}
			Material ground = region.getType(x, y, z);
			if (!region.getType(x, y + 1, z).isAir()) {
				return;
			}
			double r = terrain.random(x, z, 11);
			double p = terrain.patch(x, z);
			if (ground == Material.GRASS_BLOCK) {
				// Thicker grass in the lush patches, thinner towards the bare ones.
				double lush = 0.34 + (0.4 - Math.abs(p - 0.0)) * 0.3;
				if (r < lush) {
					region.setType(x, y + 1, z, r < lush * 0.1 ? Material.FERN : Material.SHORT_GRASS);
				} else if (r < lush + 0.05) {
					tall(region, x, y + 1, z, r < lush + 0.012 ? Material.LARGE_FERN : Material.TALL_GRASS);
				} else if (r < lush + 0.085) {
					region.setType(x, y + 1, z, Material.CRIMSON_ROOTS);
				} else if (r < lush + 0.092) {
					region.setType(x, y + 1, z, r < lush + 0.088 ? Material.POPPY : Material.RED_TULIP);
				} else if (r < lush + 0.095) {
					tall(region, x, y + 1, z, Material.ROSE_BUSH);
				} else if (r < lush + 0.1) {
					region.setType(x, y + 1, z, Material.SHORT_DRY_GRASS);
				} else if (r < lush + 0.101) {
					region.setType(x, y + 1, z, Material.WITHER_ROSE);
				} else if (r < lush + 0.105) {
					region.setType(x, y + 1, z, Material.CRIMSON_FUNGUS);
				}
			} else if (ground == Material.COARSE_DIRT || ground == Material.PODZOL || ground == Material.ROOTED_DIRT) {
				// The dead patches.
				if (r < 0.08) {
					region.setType(x, y + 1, z, Material.DEAD_BUSH);
				} else if (r < 0.2) {
					region.setType(x, y + 1, z, Material.SHORT_DRY_GRASS);
				} else if (r < 0.24) {
					tall(region, x, y + 1, z, Material.TALL_DRY_GRASS);
				} else if (r < 0.27 && ground == Material.PODZOL) {
					region.setType(x, y + 1, z, Material.CRIMSON_ROOTS);
				}
			} else if (ground == Material.MUD && r < 0.05) {
				region.setType(x, y + 1, z, Material.CRIMSON_ROOTS);
			}
		}

		private void tall(LimitedRegion region, int x, int y, int z, Material plant) {
			if (!region.isInRegion(x, y + 1, z) || !region.getType(x, y + 1, z).isAir()) {
				return;
			}
			BlockData lower = plant.createBlockData();
			BlockData upper = plant.createBlockData();
			if (lower instanceof Bisected bottom && upper instanceof Bisected tip) {
				bottom.setHalf(Bisected.Half.BOTTOM);
				tip.setHalf(Bisected.Half.TOP);
				region.setBlockData(x, y, z, lower);
				region.setBlockData(x, y + 1, z, upper);
			}
		}

		/** A dead tree, or (red = true) a dark oak crowned with blood-red leaves. */
		private void tree(LimitedRegion region, int x, int z, boolean red) {
			Terrain.Column c = terrain.column(x, z);
			if (c.kind() != Terrain.Kind.LAND) {
				return;
			}
			int y = c.height();
			if (!region.isInRegion(x, y + 9, z) || region.getType(x, y, z) != Material.GRASS_BLOCK && !red) {
				return;
			}
			int height = 4 + (int) (terrain.random(x, z, 51) * 4);
			Material log = red ? Material.DARK_OAK_LOG : terrain.random(x, z, 52) < 0.5 ? Material.STRIPPED_DARK_OAK_LOG : Material.DARK_OAK_LOG;
			region.setType(x, y, z, Material.DIRT);
			for (int i = 1; i <= height; i++) {
				region.setType(x, y + i, z, log);
			}
			// Bare branches.
			int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
			int branches = 1 + (int) (terrain.random(x, z, 53) * 3);
			for (int b = 0; b < branches; b++) {
				int[] d = dirs[(int) (terrain.random(x + b, z, 54) * 4)];
				int by = y + height - 1 - b;
				int len = 1 + (int) (terrain.random(x, z + b, 55) * 2);
				for (int l = 1; l <= len; l++) {
					int bx = x + d[0] * l;
					int bz = z + d[1] * l;
					int yy = by + (l > 1 ? 1 : 0);
					if (region.isInRegion(bx, yy, bz) && region.getType(bx, yy, bz).isAir()) {
						region.setType(bx, yy, bz, Material.DARK_OAK_WOOD);
					}
				}
			}
			if (!red) {
				return;
			}
			// A ragged crown of blood-red leaves and a litter of fallen ones.
			BlockData leaves = Material.DARK_OAK_LEAVES.createBlockData();
			if (leaves instanceof Leaves l) {
				l.setPersistent(true);
			}
			int top = y + height;
			for (int dx = -2; dx <= 2; dx++) {
				for (int dz = -2; dz <= 2; dz++) {
					for (int dy = -1; dy <= 1; dy++) {
						int ly = top + dy;
						double reach = Math.abs(dx) + Math.abs(dz) + Math.max(0, dy) * 1.5;
						if (reach > 3.2 || terrain.random(x + dx * 7, z + dz * 13 + dy * 31, 56) < 0.18) {
							continue;
						}
						if (region.isInRegion(x + dx, ly, z + dz) && region.getType(x + dx, ly, z + dz).isAir()) {
							region.setBlockData(x + dx, ly, z + dz, leaves);
						}
					}
				}
			}
			for (int dx = -3; dx <= 3; dx++) {
				for (int dz = -3; dz <= 3; dz++) {
					int gx = x + dx;
					int gz = z + dz;
					if (terrain.random(gx, gz, 57) > 0.35) {
						continue;
					}
					int gy = terrain.column(gx, gz).height();
					if (region.isInRegion(gx, gy + 1, gz) && region.getType(gx, gy, gz) == Material.GRASS_BLOCK) {
						Material above = region.getType(gx, gy + 1, gz);
						if (above.isAir() || above == Material.SHORT_GRASS) {
							region.setType(gx, gy + 1, gz, Material.LEAF_LITTER);
						}
					}
				}
			}
		}

		/** The half-buried rib cage of something enormous. */
		private void ribs(LimitedRegion region, int x, int z, boolean alongX) {
			int count = 5;
			for (int i = 0; i < count; i++) {
				int rx = alongX ? x + i * 2 : x;
				int rz = alongX ? z : z + i * 2;
				int ground = terrain.column(rx, rz).height();
				int span = 3 + (i == 1 || i == 2 ? 1 : 0);
				int tall = 4 + (i < 3 ? 2 : 0);
				for (int s = -span; s <= span; s++) {
					double t = Math.abs(s) / (double) span;
					int hy = ground + (int) Math.round(Math.sqrt(Math.max(0, 1 - t * t)) * tall) - 1;
					int bx = alongX ? rx : rx + s;
					int bz = alongX ? rz + s : rz;
					if (region.isInRegion(bx, hy, bz) && (Math.abs(s) != span || terrain.random(bx, bz, 61) < 0.7)) {
						region.setType(bx, hy, bz, Material.BONE_BLOCK);
					}
				}
			}
		}
	}
}
