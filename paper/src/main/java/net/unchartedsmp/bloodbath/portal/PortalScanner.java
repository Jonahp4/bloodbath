package net.unchartedsmp.bloodbath.portal;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import net.unchartedsmp.bloodbath.config.BloodConfig;
import org.bukkit.Material;
import org.bukkit.block.Block;

/**
 * Finds the portal a Bloodstone frame block belongs to, at any size.
 *
 * <p>From the lit frame block it tries both upright orientations and each open block beside it,
 * flood-filling the open space in that plane. The fill must be closed in on every side by
 * registered Bloodstone Frame blocks, be a full rectangle, and fit between the configured minimum
 * and maximum sizes. Corners are optional (like a nether portal's). The fill never visits more than
 * {@code maximum-width × maximum-height} blocks, so an open frame (or none at all) costs a few
 * hundred block reads at most.
 *
 * <p>When no orientation makes a valid portal, the result says exactly what's wrong and where:
 * the first missing or wrong frame block, a portal that's too small or too big, an inside that
 * isn't a rectangle.
 */
public final class PortalScanner {
	/** A found portal, or why there isn't one. {@code where} marks the problem block (may be null). */
	public record Result(Portal portal, String problem, Block where, int frames) {
		public boolean valid() {
			return portal != null;
		}
	}

	private PortalScanner() {
	}

	/** Open blocks a portal can fill: air, fire, light, and nothing solid. */
	public static boolean open(Block block) {
		Material type = block.getType();
		return type.isAir() || type == Material.FIRE || type == Material.SOUL_FIRE || type == Material.LIGHT;
	}

	/**
	 * @param frame a Bloodstone frame block
	 * @param taken open blocks already inside another active portal
	 */
	public static Result scan(Block frame, BloodConfig.Portal config, Predicate<Block> taken) {
		Result best = null;
		for (Portal.Axis axis : Portal.Axis.values()) {
			int ax = axis == Portal.Axis.X ? 1 : 0;
			int az = axis == Portal.Axis.Z ? 1 : 0;
			int[][] dirs = {{ax, 0, az}, {-ax, 0, -az}, {0, 1, 0}, {0, -1, 0}};
			for (int[] d : dirs) {
				Block start = frame.getRelative(d[0], d[1], d[2]);
				if (!open(start) || taken.test(start)) {
					continue;
				}
				Result result = fill(start, axis, config);
				if (result.valid()) {
					return result;
				}
				// Complain about the plane the player built in: the one where the fill met the most frame
				// blocks before it failed (the other plane leaks out into the open almost at once).
				if (best == null || result.frames() > best.frames()) {
					best = result;
				}
			}
		}
		if (best == null) {
			return new Result(null, "There's no open space beside this frame block to fill. Portals stand upright, "
				+ "inside a ring of Bloodstone Frames.", null, 0);
		}
		return best;
	}

	private static Result fill(Block start, Portal.Axis axis, BloodConfig.Portal config) {
		int ax = axis == Portal.Axis.X ? 1 : 0;
		int az = axis == Portal.Axis.Z ? 1 : 0;
		int[][] steps = {{ax, 0, az}, {-ax, 0, -az}, {0, 1, 0}, {0, -1, 0}};
		int limit = config.maxWidth() * config.maxHeight();
		Set<Block> inside = new HashSet<>();
		ArrayDeque<Block> queue = new ArrayDeque<>();
		inside.add(start);
		queue.add(start);
		int minU = Integer.MAX_VALUE;
		int maxU = Integer.MIN_VALUE;
		int minY = Integer.MAX_VALUE;
		int maxY = Integer.MIN_VALUE;
		int frames = 0;
		while (!queue.isEmpty()) {
			Block cell = queue.poll();
			int u = axis == Portal.Axis.X ? cell.getX() : cell.getZ();
			minU = Math.min(minU, u);
			maxU = Math.max(maxU, u);
			minY = Math.min(minY, cell.getY());
			maxY = Math.max(maxY, cell.getY());
			if (maxU - minU + 1 > config.maxWidth() || maxY - minY + 1 > config.maxHeight()) {
				return new Result(null, "The frame is open, or bigger than the largest portal allowed ("
					+ config.maxWidth() + " wide × " + config.maxHeight() + " tall inside).", null, frames);
			}
			for (int[] s : steps) {
				Block next = cell.getRelative(s[0], s[1], s[2]);
				if (inside.contains(next)) {
					continue;
				}
				if (open(next)) {
					if (inside.size() >= limit) {
						return new Result(null, "The frame is open, or bigger than the largest portal allowed ("
							+ config.maxWidth() + " × " + config.maxHeight() + ").", null, frames);
					}
					inside.add(next);
					queue.add(next);
				} else if (Frames.isFrame(next)) {
					frames++;
				} else {
					String what = next.getType() == Frames.BLOCK ? "reinforced deepslate, not a Bloodstone Frame"
						: next.getType().getKey().getKey().replace('_', ' ').toLowerCase(Locale.ROOT);
					return new Result(null, "The frame has a gap: the block at " + next.getX() + " " + next.getY() + " "
						+ next.getZ() + " is " + what + ".", next, frames);
				}
			}
		}
		int width = maxU - minU + 1;
		int height = maxY - minY + 1;
		if (inside.size() != width * height) {
			return new Result(null, "The inside of the frame has to be a rectangle (it's " + inside.size()
				+ " blocks inside a " + width + "×" + height + " box).", null, frames);
		}
		if (width < config.minWidth() || height < config.minHeight()) {
			return new Result(null, "The portal is " + width + "×" + height + " inside; the smallest is "
				+ config.minWidth() + " wide × " + config.minHeight() + " tall.", null, frames);
		}
		int x = axis == Portal.Axis.X ? minU : start.getX();
		int z = axis == Portal.Axis.Z ? minU : start.getZ();
		Portal portal = new Portal(UUID.randomUUID(), start.getWorld().getUID(), axis, x, minY, z, width, height);
		return new Result(portal, null, null, frames);
	}
}
