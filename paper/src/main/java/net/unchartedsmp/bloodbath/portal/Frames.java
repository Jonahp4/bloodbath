package net.unchartedsmp.bloodbath.portal;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.unchartedsmp.bloodbath.Keys;
import net.unchartedsmp.bloodbath.weapon.Weapons;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemRarity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;
import org.bukkit.persistence.PersistentDataType;

/**
 * Bloodstone Frame blocks: the frame of a Bloodlands portal.
 *
 * <p>In the world a frame is reinforced deepslate (which the resource pack paints as cracked
 * bloodstone), but reinforced deepslate alone is not a frame: every frame block placed from a
 * Bloodstone Frame item is written into its chunk's persistent data, and only those count. The list
 * survives restarts and is cached in memory per chunk, so the checks the protection listeners make
 * on every explosion and piston stay cheap.
 */
public final class Frames {
	public static final Material BLOCK = Material.REINFORCED_DEEPSLATE;
	public static final String ID = "bloodstone_frame";

	/** Per chunk (world, chunk key): the packed positions of its frame blocks. */
	private static final Map<ChunkRef, java.util.Set<Integer>> CACHE = new HashMap<>();

	private record ChunkRef(UUID world, long chunk) {
	}

	private Frames() {
	}

	// ---- the item ------------------------------------------------------------------------------

	public static ItemStack item(int amount) {
		ItemStack frame = new ItemStack(BLOCK, Math.max(1, Math.min(64, amount)));
		frame.editMeta(meta -> {
			meta.itemName(Component.text("Bloodstone Frame", NamedTextColor.RED));
			meta.lore(List.of(
				line("Build a ring of these, as wide and as", NamedTextColor.GRAY),
				line("tall as you like, and set it alight", NamedTextColor.GRAY),
				line("with flint and steel.", NamedTextColor.GRAY),
				Component.empty(),
				line("Opens the way to the Bloodlands.", NamedTextColor.DARK_RED),
				line("Nothing can break it once it's lit.", NamedTextColor.DARK_GRAY)));
			CustomModelDataComponent model = meta.getCustomModelDataComponent();
			model.setStrings(List.of(Weapons.MODEL_PREFIX + ID));
			meta.setCustomModelDataComponent(model);
			meta.setRarity(ItemRarity.RARE);
			meta.getPersistentDataContainer().set(Keys.FRAME_ITEM, PersistentDataType.BYTE, (byte) 1);
		});
		return frame;
	}

	public static boolean isItem(ItemStack stack) {
		return stack != null && stack.getType() == BLOCK && stack.getPersistentDataContainer().has(Keys.FRAME_ITEM, PersistentDataType.BYTE);
	}

	private static Component line(String text, NamedTextColor color) {
		return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
	}

	// ---- the registry --------------------------------------------------------------------------

	public static boolean isFrame(Block block) {
		return block.getType() == BLOCK && positions(block.getChunk()).contains(pack(block));
	}

	public static void add(Block block) {
		java.util.Set<Integer> set = positions(block.getChunk());
		if (set.add(pack(block))) {
			save(block.getChunk(), set);
		}
	}

	public static void remove(Block block) {
		java.util.Set<Integer> set = positions(block.getChunk());
		if (set.remove(pack(block))) {
			save(block.getChunk(), set);
		}
	}

	/** Frame blocks in a chunk (for tests and /bb status). */
	public static int count(Chunk chunk) {
		return positions(chunk).size();
	}

	private static java.util.Set<Integer> positions(Chunk chunk) {
		ChunkRef ref = new ChunkRef(chunk.getWorld().getUID(), chunk.getChunkKey());
		java.util.Set<Integer> set = CACHE.get(ref);
		if (set == null) {
			set = new java.util.HashSet<>();
			int[] stored = chunk.getPersistentDataContainer().get(Keys.FRAMES, PersistentDataType.INTEGER_ARRAY);
			if (stored != null) {
				for (int value : stored) {
					set.add(value);
				}
			}
			CACHE.put(ref, set);
		}
		return set;
	}

	private static void save(Chunk chunk, java.util.Set<Integer> set) {
		if (set.isEmpty()) {
			chunk.getPersistentDataContainer().remove(Keys.FRAMES);
		} else {
			chunk.getPersistentDataContainer().set(Keys.FRAMES, PersistentDataType.INTEGER_ARRAY,
				set.stream().mapToInt(Integer::intValue).toArray());
		}
	}

	/** Chunk-local x (4 bits), z (4 bits) and y (the rest, offset so negative heights fit). */
	private static int pack(Block block) {
		return (block.getX() & 15) | (block.getZ() & 15) << 4 | (block.getY() + 2048) << 8;
	}

	/** The chunk unloaded: its entry can be read back from its data when needed. */
	public static void unload(Chunk chunk) {
		CACHE.remove(new ChunkRef(chunk.getWorld().getUID(), chunk.getChunkKey()));
	}

	public static void unload(World world) {
		CACHE.keySet().removeIf(ref -> ref.world().equals(world.getUID()));
	}

	public static void clearCache() {
		CACHE.clear();
	}
}
