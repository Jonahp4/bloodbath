package net.unchartedsmp.bloodbath.anvil;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

/**
 * What a player has put into a Blood Anvil, written to disk the moment it changes
 * ({@code plugins/Bloodbath/anvil-escrow/<uuid>.yml}), so a crash with the menu open never costs
 * them a weapon: the items come back when they next join.
 *
 * <p>The player's own data is saved right after an item moves from them into the anvil, and their
 * escrow file is deleted only after the items are back in their inventory and saved again, so at
 * any moment an item is in exactly one of the two places on disk.
 */
final class Escrow {
	private final File folder;
	private final Logger log;

	Escrow(File dataFolder, Logger log) {
		this.folder = new File(dataFolder, "anvil-escrow");
		this.log = log;
	}

	void save(UUID player, List<ItemStack> items) {
		File file = new File(folder, player + ".yml");
		List<ItemStack> real = items.stream().filter(stack -> stack != null && !stack.isEmpty()).toList();
		if (real.isEmpty()) {
			delete(player);
			return;
		}
		YamlConfiguration yaml = new YamlConfiguration();
		try {
			List<String> encoded = new ArrayList<>();
			for (ItemStack stack : real) {
				encoded.add(Base64.getEncoder().encodeToString(stack.serializeAsBytes()));
			}
			yaml.set("items", encoded);
		} catch (RuntimeException e) {
			// A server without binary item serialization: Bukkit's own YAML form.
			yaml.set("items", null);
			yaml.set("stacks", real);
		}
		try {
			if (!folder.isDirectory() && !folder.mkdirs()) {
				throw new IOException("can't create " + folder);
			}
			yaml.save(file);
		} catch (IOException e) {
			log.warning("Couldn't write the Blood Anvil escrow for " + player + ": " + e.getMessage());
		}
	}

	/** The items a player left in an anvil when the server stopped, or an empty list. */
	List<ItemStack> load(UUID player) {
		File file = new File(folder, player + ".yml");
		if (!file.isFile()) {
			return List.of();
		}
		List<ItemStack> items = new ArrayList<>();
		YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
		for (Object stack : yaml.getList("stacks", List.of())) {
			if (stack instanceof ItemStack item) {
				items.add(item);
			}
		}
		for (String line : yaml.getStringList("items")) {
			try {
				items.add(ItemStack.deserializeBytes(Base64.getDecoder().decode(line)));
			} catch (RuntimeException e) {
				log.warning("A Blood Anvil escrow entry for " + player + " couldn't be read and was kept in " + file + ".");
				return List.of(); // leave the file for an admin rather than lose the rest
			}
		}
		return items;
	}

	void delete(UUID player) {
		File file = new File(folder, player + ".yml");
		if (file.isFile() && !file.delete()) {
			log.warning("Couldn't delete " + file);
		}
	}

	boolean has(UUID player) {
		return new File(folder, player + ".yml").isFile();
	}
}
