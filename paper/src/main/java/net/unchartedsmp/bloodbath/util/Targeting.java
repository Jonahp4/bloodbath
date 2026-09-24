package net.unchartedsmp.bloodbath.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import net.unchartedsmp.bloodbath.config.Settings;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

/** Raycasts, area queries, movement and safe-teleport helpers shared by every weapon. */
public final class Targeting {
	private static final double TELEPORT_BACKOFF_STEP = 0.25;

	private Targeting() {
	}

	/** Where the player's look ray first hits a solid block, or the end of the ray. */
	public static Location lookTarget(Player player, double range) {
		Location eye = player.getEyeLocation();
		Vector direction = eye.getDirection();
		RayTraceResult hit = player.getWorld().rayTraceBlocks(eye, direction, range, FluidCollisionMode.NEVER, true);
		if (hit == null) {
			return eye.clone().add(direction.multiply(range));
		}
		return hit.getHitPosition().toLocation(player.getWorld());
	}

	/** Where the look ray hits a block within {@code range}; empty if it hits nothing. */
	public static Optional<Location> lookBlock(Player player, double range) {
		Location eye = player.getEyeLocation();
		RayTraceResult hit = player.getWorld().rayTraceBlocks(eye, eye.getDirection(), range, FluidCollisionMode.NEVER, true);
		return hit == null ? Optional.empty() : Optional.of(hit.getHitPosition().toLocation(player.getWorld()));
	}

	/** True when no solid block sits between the two points. */
	public static boolean hasLineOfSight(Location from, Location to) {
		Vector delta = to.toVector().subtract(from.toVector());
		double distance = delta.length();
		if (distance < 1.0E-4) {
			return true;
		}
		return from.getWorld().rayTraceBlocks(from, delta.multiply(1.0 / distance), distance, FluidCollisionMode.NEVER, true) == null;
	}

	/** The living creature the player is looking at within {@code range}, if nothing solid is in the way. */
	public static LivingEntity lookEntity(Player player, double range) {
		return lookEntity(player, range, target -> validTarget(player, target));
	}

	/**
	 * The first living entity matching {@code filter} along the player's look ray. The ray stops at
	 * the first solid block, so nothing can be targeted through walls.
	 */
	public static LivingEntity lookEntity(Player player, double range, Predicate<LivingEntity> filter) {
		Location eye = player.getEyeLocation();
		RayTraceResult hit = player.getWorld().rayTrace(eye, eye.getDirection(), range, FluidCollisionMode.NEVER, true, 0.35,
			e -> e != player && e instanceof LivingEntity living && filter.test(living));
		return hit != null && hit.getHitEntity() instanceof LivingEntity target ? target : null;
	}

	/**
	 * Living targets within a true sphere (not just the bounding cube), excluding {@code exclude},
	 * spectators, armor stands, and - on PvP-off worlds - players when the source is a player.
	 */
	public static List<LivingEntity> livingInRadius(Location center, double radius, Entity exclude) {
		double radiusSq = radius * radius;
		List<LivingEntity> out = new ArrayList<>();
		for (LivingEntity e : center.getWorld().getNearbyLivingEntities(center, radius)) {
			if (e != exclude && e.getLocation().distanceSquared(center) <= radiusSq && validTarget(exclude, e)) {
				out.add(e);
			}
		}
		return out;
	}

	/** Whether {@code source}'s abilities may touch {@code target} at all. */
	@SuppressWarnings("deprecation") // World#getPVP reads the pvp game rule on 1.21.9+ and works on every version
	public static boolean validTarget(Entity source, LivingEntity target) {
		if (!target.isValid() || target.isDead() || target instanceof ArmorStand) {
			return false;
		}
		if (target instanceof Player player) {
			if (player.getGameMode() == GameMode.SPECTATOR) {
				return false;
			}
			if (source instanceof Player && Settings.get().respectWorldPvp && !target.getWorld().getPVP()) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Finds a spot at or before {@code to} (walking back toward {@code from}) where the entity's
	 * hitbox fits without overlapping blocks, so teleports can't clip anyone into or through walls.
	 */
	public static Optional<Location> safeLanding(Entity entity, Location from, Location to) {
		Vector delta = to.toVector().subtract(from.toVector());
		double length = delta.length();
		int steps = (int) Math.ceil(length / TELEPORT_BACKOFF_STEP);
		Vector step = length < 1.0E-4 ? new Vector() : delta.multiply(TELEPORT_BACKOFF_STEP / length);
		Location candidate = to.clone();
		for (int i = 0; i <= steps; i++) {
			if (fitsAt(entity, candidate)) {
				return Optional.of(candidate);
			}
			// The point is in a block (a floor hit a hair inside it, or a ledge): stand on top of that
			// block before backing off.
			Location onTop = candidate.clone();
			onTop.setY(Math.floor(candidate.getY()) + 1.0);
			if (fitsAt(entity, onTop)) {
				return Optional.of(onTop);
			}
			candidate.subtract(step);
		}
		return Optional.empty();
	}

	/** Whether the entity's hitbox would fit with its feet at {@code target}. */
	public static boolean fitsAt(Entity entity, Location target) {
		Location feet = entity.getLocation();
		BoundingBox box = entity.getBoundingBox().shift(target.getX() - feet.getX(), target.getY() - feet.getY() + 1.0E-3, target.getZ() - feet.getZ());
		return !target.getWorld().hasCollisionsIn(box);
	}

	/** Teleport keeping the given facing. Returns false if something (another plugin, a vehicle) refused it. */
	public static boolean teleport(Player player, Location target, float yaw, float pitch) {
		Location destination = target.clone();
		destination.setYaw(yaw);
		destination.setPitch(pitch);
		if (player.isInsideVehicle()) {
			player.leaveVehicle();
		}
		boolean moved = player.teleport(destination, PlayerTeleportEvent.TeleportCause.PLUGIN);
		if (moved) {
			player.setFallDistance(0.0F);
		}
		return moved;
	}

	public static void addVelocity(Entity entity, Vector delta) {
		entity.setVelocity(entity.getVelocity().add(delta));
	}

	public static void pullTowards(Entity entity, Location center, double strength) {
		Vector toCenter = center.toVector().subtract(entity.getLocation().toVector());
		if (toCenter.lengthSquared() >= 0.01) {
			addVelocity(entity, toCenter.normalize().multiply(strength));
		}
	}

	public static void launchOutward(Entity entity, Location center, double strength, double verticalBoost) {
		Vector away = entity.getLocation().toVector().subtract(center.toVector());
		if (away.lengthSquared() < 0.01) {
			away = new Vector(1, 0, 0);
		}
		addVelocity(entity, away.normalize().multiply(strength).add(new Vector(0.0, verticalBoost, 0.0)));
	}

	/** Whether {@code location} is in the (still loaded) world the entity is in. */
	public static boolean sameWorld(Location location, Entity entity) {
		return location.isWorldLoaded() && location.getWorld().equals(entity.getWorld());
	}

	/** Still valid, alive, and in the world the ability was cast in. */
	public static boolean stillIn(Entity entity, World world) {
		return entity.isValid() && !entity.isDead() && entity.getWorld().equals(world);
	}
}
