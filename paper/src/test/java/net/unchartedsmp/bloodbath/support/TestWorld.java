package net.unchartedsmp.bloodbath.support;

import io.papermc.paper.math.Position;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LightningStrike;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import org.mockbukkit.mockbukkit.world.WorldMock;

/**
 * A flat grass world (ground surface at y=5) filling in what MockBukkit leaves unimplemented or
 * does differently from Paper: particles (validated the way Paper does, then counted), block and
 * entity ray traces, collision checks, hitbox-based entity queries and cosmetic lightning.
 */
public final class TestWorld extends WorldMock {
	public int particles;
	public int lightning;
	/** Every particle spawn: where, and who it was sent to (null = everyone around). */
	public final List<Spawn> spawns = new ArrayList<>();

	public record Spawn(Particle particle, Location at, List<Player> receivers) {
	}

	public TestWorld(String name) {
		super(Material.GRASS_BLOCK, 4);
		setName(name);
	}

	@Override
	public <T> void spawnParticle(Particle particle, double x, double y, double z, int count, double offsetX, double offsetY,
		double offsetZ, double extra, T data, boolean force) {
		spawnParticle(particle, null, null, x, y, z, count, offsetX, offsetY, offsetZ, extra, data, force);
	}

	@Override
	public <T> void spawnParticle(Particle particle, List<Player> receivers, Player source, double x, double y, double z, int count,
		double offsetX, double offsetY, double offsetZ, double extra, T data, boolean force) {
		Class<?> type = particle.getDataType();
		if (type == Void.class ? data != null : data == null || !type.isInstance(data)) {
			// What CraftWorld does: the wrong data type is an IllegalArgumentException.
			throw new IllegalArgumentException(particle + " needs " + type.getSimpleName() + ", got " + (data == null ? "null" : data.getClass()));
		}
		if (count < 0) {
			throw new IllegalArgumentException("negative particle count");
		}
		particles += count;
		spawns.add(new Spawn(particle, new Location(this, x, y, z), receivers == null ? null : List.copyOf(receivers)));
	}

	/** MockBukkit's ravager and wither skeleton lack the mob controls the Blood Knight uses: spawn ours instead. */
	@Override
	@SuppressWarnings("unchecked")
	public <T extends Entity> T spawn(Location location, Class<T> clazz, java.util.function.Consumer<? super T> function,
		org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason reason) {
		if (clazz == org.bukkit.entity.WitherSkeleton.class) {
			TestKnightSkeleton skeleton = new TestKnightSkeleton(getServer());
			skeleton.setLocation(location.clone());
			if (function != null) {
				function.accept((T) skeleton);
			}
			getServer().registerEntity(skeleton);
			return (T) skeleton;
		}
		if (clazz == org.bukkit.entity.Ravager.class) {
			TestKnightRavager ravager = new TestKnightRavager(getServer());
			ravager.setLocation(location.clone());
			if (function != null) {
				function.accept((T) ravager);
			}
			getServer().registerEntity(ravager);
			return (T) ravager;
		}
		if (clazz == org.bukkit.entity.ItemDisplay.class) {
			TestItemDisplay display = new TestItemDisplay(getServer());
			display.setLocation(location.clone());
			if (function != null) {
				function.accept((T) display);
			}
			getServer().registerEntity(display);
			return (T) display;
		}
		return super.spawn(location, clazz, function, reason);
	}

	@Override
	public org.bukkit.entity.Item dropItem(Location location, org.bukkit.inventory.ItemStack stack,
		java.util.function.Consumer<? super org.bukkit.entity.Item> function) {
		TestItem item = new TestItem(getServer(), stack);
		item.setLocation(location.clone());
		if (function != null) {
			function.accept(item);
		}
		getServer().registerEntity(item);
		return item;
	}

	private org.mockbukkit.mockbukkit.ServerMock getServer() {
		return org.mockbukkit.mockbukkit.MockBukkit.getMock();
	}

	/** Hitbox overlap, like Paper (MockBukkit only checks whether the entity's feet are in the box). */
	@Override
	public Collection<Entity> getNearbyEntities(BoundingBox box, Predicate<? super Entity> filter) {
		List<Entity> out = new ArrayList<>();
		for (Entity entity : getEntities()) {
			if (entity.getBoundingBox().overlaps(box) && (filter == null || filter.test(entity))) {
				out.add(entity);
			}
		}
		return out;
	}

	@Override
	public LightningStrike strikeLightningEffect(Location location) {
		lightning++;
		return null;
	}

	private boolean solid(int x, int y, int z) {
		return y >= getMinHeight() && y < getMaxHeight() && getBlockAt(x, y, z).getType().isSolid();
	}

	@Override
	public boolean hasCollisionsIn(BoundingBox box) {
		for (int x = (int) Math.floor(box.getMinX()); x <= (int) Math.floor(box.getMaxX() - 1.0E-7); x++) {
			for (int y = (int) Math.floor(box.getMinY()); y <= (int) Math.floor(box.getMaxY() - 1.0E-7); y++) {
				for (int z = (int) Math.floor(box.getMinZ()); z <= (int) Math.floor(box.getMaxZ() - 1.0E-7); z++) {
					if (solid(x, y, z)) {
						return true;
					}
				}
			}
		}
		return false;
	}

	@Override
	public RayTraceResult rayTraceBlocks(Location start, Vector direction, double maxDistance, FluidCollisionMode fluids, boolean ignorePassable) {
		Vector from = start.toVector();
		Vector step = direction.clone().normalize().multiply(0.02);
		Vector p = from.clone();
		for (double travelled = 0.0; travelled <= maxDistance; travelled += 0.02) {
			int bx = p.getBlockX();
			int by = p.getBlockY();
			int bz = p.getBlockZ();
			if (solid(bx, by, bz)) {
				// Exact entry point on the block's face, like the real ray trace.
				Block block = getBlockAt(bx, by, bz);
				RayTraceResult face = BoundingBox.of(block).rayTrace(from, direction.clone().normalize(), maxDistance + 1.0);
				return face == null ? new RayTraceResult(p.clone(), block, BlockFace.UP)
					: new RayTraceResult(face.getHitPosition(), block, face.getHitBlockFace());
			}
			p.add(step);
		}
		return null;
	}

	@Override
	public RayTraceResult rayTraceEntities(Location start, Vector direction, double maxDistance, double raySize, Predicate<? super Entity> filter) {
		Vector from = start.toVector();
		Vector dir = direction.clone().normalize();
		RayTraceResult best = null;
		double bestDistance = Double.MAX_VALUE;
		for (Entity entity : getEntities()) {
			if (filter != null && !filter.test(entity)) {
				continue;
			}
			RayTraceResult hit = entity.getBoundingBox().clone().expand(raySize).rayTrace(from, dir, maxDistance);
			if (hit != null) {
				double distance = from.distance(hit.getHitPosition());
				if (distance < bestDistance) {
					bestDistance = distance;
					best = new RayTraceResult(hit.getHitPosition(), entity, hit.getHitBlockFace());
				}
			}
		}
		return best;
	}

	@Override
	public RayTraceResult rayTrace(Location start, Vector direction, double maxDistance, FluidCollisionMode fluids, boolean ignorePassable,
		double raySize, Predicate<? super Entity> filter) {
		RayTraceResult blocks = rayTraceBlocks(start, direction, maxDistance, fluids, ignorePassable);
		RayTraceResult entities = rayTraceEntities(start, direction, maxDistance, raySize, filter);
		if (entities == null) {
			return blocks;
		}
		if (blocks == null) {
			return entities;
		}
		Vector from = start.toVector();
		return from.distanceSquared(entities.getHitPosition()) < from.distanceSquared(blocks.getHitPosition()) ? entities : blocks;
	}

	@Override
	public RayTraceResult rayTrace(Position start, Vector direction, double maxDistance, FluidCollisionMode fluids, boolean ignorePassable,
		double raySize, Predicate<? super Entity> filter, Predicate<? super Block> canCollide) {
		return rayTrace(start.toLocation(this), direction, maxDistance, fluids, ignorePassable, raySize, filter);
	}
}
