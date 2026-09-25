package net.unchartedsmp.bloodbath.portal;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.util.Vector;

/**
 * An active Bloodlands portal: the rectangle of open blocks inside a Bloodstone frame. Portals
 * stand upright; {@code axis} is the horizontal direction their width runs along ({@code X}: the
 * portal faces north and south, {@code Z}: east and west). ({@code x, y, z}) is the lowest corner
 * of the inside.
 */
public record Portal(UUID id, UUID world, Axis axis, int x, int y, int z, int width, int height) {
	public enum Axis {
		X, Z
	}

	/** The interior's blocks, bottom row first. */
	public List<Block> interior(World w) {
		List<Block> blocks = new ArrayList<>(width * height);
		for (int j = 0; j < height; j++) {
			for (int i = 0; i < width; i++) {
				blocks.add(axis == Axis.X ? w.getBlockAt(x + i, y + j, z) : w.getBlockAt(x, y + j, z + i));
			}
		}
		return blocks;
	}

	/** The frame around the inside (corners included). */
	public List<Block> frame(World w) {
		List<Block> blocks = new ArrayList<>();
		for (int i = -1; i <= width; i++) {
			for (int j = -1; j <= height; j++) {
				if (i >= 0 && i < width && j >= 0 && j < height) {
					continue;
				}
				blocks.add(axis == Axis.X ? w.getBlockAt(x + i, y + j, z) : w.getBlockAt(x, y + j, z + i));
			}
		}
		return blocks;
	}

	public boolean contains(int bx, int by, int bz) {
		if (by < y || by >= y + height) {
			return false;
		}
		return axis == Axis.X ? bz == z && bx >= x && bx < x + width : bx == x && bz >= z && bz < z + width;
	}

	/** Unit vector out of the portal's face. */
	public Vector normal() {
		return axis == Axis.X ? new Vector(0, 0, 1) : new Vector(1, 0, 0);
	}

	/** The middle of the inside. */
	public Location center(World w) {
		double cx = axis == Axis.X ? x + width / 2.0 : x + 0.5;
		double cz = axis == Axis.X ? z + 0.5 : z + width / 2.0;
		return new Location(w, cx, y + height / 2.0, cz);
	}

	/** Where someone stepping out of it on {@code side} (+1 or -1 along the normal) stands, facing away. */
	public Location exit(World w, int side) {
		Vector n = normal().multiply(side);
		double cx = axis == Axis.X ? x + width / 2.0 : x + 0.5;
		double cz = axis == Axis.X ? z + 0.5 : z + width / 2.0;
		Location at = new Location(w, cx + n.getX() * 1.6, y, cz + n.getZ() * 1.6);
		at.setDirection(n);
		at.setPitch(0.0F);
		return at;
	}

	/** Which side of the portal a location is on (+1 or -1 along the normal). */
	public int sideOf(Location location) {
		double d = axis == Axis.X ? location.getZ() - (z + 0.5) : location.getX() - (x + 0.5);
		return d >= 0 ? 1 : -1;
	}

	public String describe() {
		return width + "×" + height + " at " + x + " " + y + " " + z;
	}

	public int area() {
		return width * height;
	}
}
