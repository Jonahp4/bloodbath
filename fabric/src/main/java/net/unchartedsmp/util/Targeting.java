package net.unchartedsmp.util;

import java.util.List;
import java.util.Optional;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

/** Raycasts, area queries, movement and safe-teleport helpers shared by every weapon. */
public final class Targeting {
	private static final double TELEPORT_BACKOFF_STEP = 0.25;

	private Targeting() {
	}

	/** Where the player's look ray first hits a solid block, or the end of the ray. */
	public static Vec3d lookTarget(World world, PlayerEntity player, double range) {
		Vec3d start = player.getEyePos();
		Vec3d end = start.add(player.getRotationVec(1.0F).multiply(range));
		HitResult hit = world.raycast(new RaycastContext(start, end, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player));
		return hit.getType() == HitResult.Type.MISS ? end : hit.getPos();
	}

	/** Where the look ray hits a block within {@code range}; empty if it hits nothing. */
	public static Optional<Vec3d> lookBlock(World world, PlayerEntity player, double range) {
		Vec3d start = player.getEyePos();
		Vec3d end = start.add(player.getRotationVec(1.0F).multiply(range));
		HitResult hit = world.raycast(new RaycastContext(start, end, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player));
		return hit.getType() == HitResult.Type.MISS ? Optional.empty() : Optional.of(hit.getPos());
	}

	/** True when no solid block sits between the two points. */
	public static boolean hasLineOfSight(World world, Vec3d from, Vec3d to, Entity viewer) {
		HitResult hit = world.raycast(new RaycastContext(from, to, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, viewer));
		return hit.getType() == HitResult.Type.MISS;
	}

	/** Living, non-spectator entities within a true sphere (not just the bounding cube). */
	public static List<LivingEntity> livingInRadius(ServerWorld world, Vec3d center, double radius, Entity exclude) {
		double radiusSq = radius * radius;
		Box box = new Box(center.x - radius, center.y - radius, center.z - radius, center.x + radius, center.y + radius, center.z + radius);
		return world.getEntitiesByClass(
			LivingEntity.class,
			box,
			e -> e != exclude && e.isAlive() && !e.isSpectator() && !isDecoration(e) && e.squaredDistanceTo(center) <= radiusSq
		);
	}

	/** Armor stands (including Blood Mirrors) are props, never ability targets. */
	public static boolean isDecoration(Entity entity) {
		return entity instanceof ArmorStandEntity;
	}

	/**
	 * Finds a spot at or before {@code to} (walking back toward {@code from}) where the entity's
	 * hitbox fits without overlapping blocks. Prevents teleport abilities from clipping players
	 * into or through walls.
	 */
	public static Optional<Vec3d> safeLanding(ServerWorld world, Entity entity, Vec3d from, Vec3d to) {
		Vec3d feet = entity.getEntityPos();
		Box box = entity.getBoundingBox();
		Vec3d delta = to.subtract(from);
		double length = delta.length();
		int steps = (int) Math.ceil(length / TELEPORT_BACKOFF_STEP);
		Vec3d step = length < 1.0E-4 ? Vec3d.ZERO : delta.multiply(TELEPORT_BACKOFF_STEP / length);
		Vec3d candidate = to;
		for (int i = 0; i <= steps; i++) {
			if (fits(world, entity, box, feet, candidate)) {
				return Optional.of(candidate);
			}
			// Landing on a ledge: try standing one block up before backing off.
			Vec3d raised = candidate.add(0.0, 1.0, 0.0);
			if (fits(world, entity, box, feet, raised)) {
				return Optional.of(raised);
			}
			candidate = candidate.subtract(step);
		}
		return Optional.empty();
	}

	/** Whether the entity's hitbox would fit at {@code target} (feet position). */
	public static boolean fitsAt(ServerWorld world, Entity entity, Vec3d target) {
		return fits(world, entity, entity.getBoundingBox(), entity.getEntityPos(), target);
	}

	private static boolean fits(ServerWorld world, Entity entity, Box box, Vec3d feet, Vec3d target) {
		// A hair of lift so a box resting exactly on a floor isn't counted as overlapping it.
		return world.isSpaceEmpty(entity, box.offset(target.subtract(feet)).offset(0.0, 1.0E-3, 0.0));
	}

	public static void addVelocity(LivingEntity entity, Vec3d delta) {
		entity.setVelocity(entity.getVelocity().add(delta));
		if (entity instanceof ServerPlayerEntity player) {
			// Players are client-authoritative for movement, so the server has to push the change.
			player.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(player));
		}
	}

	public static void pullTowards(LivingEntity entity, Vec3d center, double strength) {
		Vec3d toCenter = center.subtract(entity.getEntityPos());
		if (toCenter.lengthSquared() >= 0.01) {
			addVelocity(entity, toCenter.normalize().multiply(strength));
		}
	}

	public static void launchOutward(LivingEntity entity, Vec3d center, double strength, double verticalBoost) {
		Vec3d away = entity.getEntityPos().subtract(center);
		if (away.lengthSquared() < 0.01) {
			away = new Vec3d(1.0, 0.0, 0.0);
		}
		addVelocity(entity, away.normalize().multiply(strength).add(0.0, verticalBoost, 0.0));
	}

	public static Vec3d chest(Entity entity) {
		return entity.getEntityPos().add(0.0, entity.getHeight() * 0.5, 0.0);
	}

	/** Still in the game, alive, and in the world the ability was cast in. */
	public static boolean stillIn(Entity entity, ServerWorld world) {
		return entity.isAlive() && entity.getEntityWorld() == world;
	}
}
