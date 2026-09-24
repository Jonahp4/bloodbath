package net.unchartedsmp.bloodbath.boss;

import java.util.ArrayList;
import java.util.List;
import net.unchartedsmp.bloodbath.Keys;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * The Blood Knight's body in the world: one item display entity per rig part, all standing at the
 * boss's feet and placed by their transformation matrices.
 *
 * <p>Each update computes every bone's matrix from the pose (parents first), then each part's.
 * A part's matrix is only sent when it actually changed, and the client interpolates between
 * updates, so a model updated every other tick still moves smoothly. The displays are never saved
 * (not persistent), are invisible to players without the resource pack (they'd see sheets of
 * paper) and are only shown to those who have it.
 */
final class BossModel {
	/** Item displays draw their item turned half round about Y; every part matrix undoes that. */
	private static final float ITEM_DISPLAY_TURN = (float) Math.PI;

	private final Rig rig;
	private final Plugin plugin;
	private final List<ItemDisplay> parts = new ArrayList<>();
	private final Matrix4f[] bones;
	private final Matrix4f[] sent;
	private final Matrix4f root = new Matrix4f();
	private final Matrix4f scratch = new Matrix4f();
	private Location lastOrigin;

	BossModel(Rig rig, Plugin plugin, Location origin, float scale, int interpolation) {
		this.rig = rig;
		this.plugin = plugin;
		bones = new Matrix4f[rig.bones().size()];
		for (int i = 0; i < bones.length; i++) {
			bones[i] = new Matrix4f();
		}
		sent = new Matrix4f[rig.parts().size()];
		World world = origin.getWorld();
		Location at = level(origin);
		boolean shadowed = false;
		for (Rig.Part part : rig.parts()) {
			ItemStack item = new ItemStack(Material.PAPER);
			item.editMeta(meta -> meta.setItemModel(part.model()));
			boolean shadow = !shadowed && rig.bones().get(part.bone()).parent() < 0;
			shadowed |= shadow;
			parts.add(world.spawn(at, ItemDisplay.class, display -> {
				display.setItemStack(item);
				display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
				display.setPersistent(false);
				display.setVisibleByDefault(false);
				display.setInterpolationDuration(interpolation);
				display.setTeleportDuration(Math.min(59, interpolation + 1));
				display.setDisplayWidth(4.0F * scale);
				display.setDisplayHeight(4.2F * scale);
				display.setViewRange(1.25F);
				if (shadow) {
					display.setShadowRadius(1.3F * scale);
					display.setShadowStrength(0.85F);
				}
				display.getPersistentDataContainer().set(Keys.BOSS, PersistentDataType.BYTE, (byte) 1);
			}));
		}
		lastOrigin = at;
	}

	/**
	 * The displays stand at the boss's feet facing nowhere in particular: a display draws its item
	 * turned by its own yaw and pitch, so they're kept at zero and the part matrices do the turning
	 * (otherwise the model would turn twice as the boss turns).
	 */
	private static Location level(Location origin) {
		Location at = origin.clone();
		at.setYaw(0.0F);
		at.setPitch(0.0F);
		return at;
	}

	/** Makes the model visible to a player who has the resource pack. */
	void showTo(Player player) {
		for (ItemDisplay part : parts) {
			player.showEntity(plugin, part);
		}
	}

	void hideFrom(Player player) {
		for (ItemDisplay part : parts) {
			player.hideEntity(plugin, part);
		}
	}

	boolean valid() {
		return !parts.isEmpty() && parts.get(0).isValid();
	}

	/**
	 * Poses the model. {@code yaw} is Minecraft's (0 = facing south); the model itself faces -Z,
	 * so it's turned by 180 - yaw.
	 */
	void update(Pose pose, float yaw, float scale, Location feet) {
		Location origin = level(feet);
		boolean moved = lastOrigin == null || lastOrigin.getWorld() != origin.getWorld() || lastOrigin.distanceSquared(origin) > 1.0E-4;
		for (int i = 0; i < parts.size(); i++) {
			ItemDisplay display = parts.get(i);
			partMatrix(rig, pose, yaw, scale, i, bones, root, i == 0, scratch);
			if (!display.isValid()) {
				continue;
			}
			if (sent[i] == null || !sent[i].equals(scratch, 1.0E-4F)) {
				if (sent[i] == null) {
					sent[i] = new Matrix4f();
				}
				sent[i].set(scratch);
				display.setInterpolationDelay(0);
				display.setTransformationMatrix(scratch);
			}
			if (moved) {
				display.teleport(origin);
			}
		}
		if (moved) {
			lastOrigin = origin;
		}
	}

	/**
	 * Where a point fixed to a bone ({@code local}, in the bone's frame at rest) is after the last
	 * {@link #update}, relative to the boss's feet.
	 */
	Vector3f pointOn(int bone, Vector3f local, Vector3f out) {
		return bones[bone].transformPosition(local, out);
	}

	/** Every bone's matrix for a pose, without touching any display (for sampling between frames). */
	static void boneMatrices(Rig rig, Pose pose, float yaw, float scale, Matrix4f[] bones, Matrix4f root) {
		root.identity()
			.rotateY((float) Math.toRadians(180.0 - yaw))
			.scale(scale)
			.translate(pose.offset[0], pose.offset[1], pose.offset[2]);
		List<Rig.Bone> boneList = rig.bones();
		for (int i = 0; i < bones.length; i++) {
			Rig.Bone bone = boneList.get(i);
			Matrix4f parent = bone.parent() < 0 ? root : bones[bone.parent()];
			bones[i].set(parent)
				.translate(bone.pivot())
				.rotateXYZ(pose.rotation[i * 3], pose.rotation[i * 3 + 1], pose.rotation[i * 3 + 2]);
		}
	}

	/**
	 * The transformation for part {@code index} of the posed model (what its display is sent).
	 * {@code bones} is scratch space, filled when {@code computeBones} (the first part of a frame).
	 */
	static void partMatrix(Rig rig, Pose pose, float yaw, float scale, int index, Matrix4f[] bones, Matrix4f root,
		boolean computeBones, Matrix4f out) {
		if (computeBones) {
			boneMatrices(rig, pose, yaw, scale, bones, root);
		}
		Rig.Part part = rig.parts().get(index);
		out.set(bones[part.bone()])
			.translate(part.offset())
			.rotate(part.rotation())
			.scale(part.scale())
			.rotateY(ITEM_DISPLAY_TURN);
	}

	List<? extends Entity> entities() {
		return parts;
	}

	void remove() {
		for (ItemDisplay part : parts) {
			if (part.isValid()) {
				part.remove();
			}
		}
		parts.clear();
	}
}
