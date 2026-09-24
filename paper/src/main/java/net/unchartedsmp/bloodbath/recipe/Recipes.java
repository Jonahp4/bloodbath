package net.unchartedsmp.bloodbath.recipe;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Logger;
import net.unchartedsmp.bloodbath.config.Settings;
import net.unchartedsmp.bloodbath.weapon.WeaponType;
import net.unchartedsmp.bloodbath.weapon.Weapons;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.recipe.CraftingBookCategory;
import org.bukkit.plugin.Plugin;

/**
 * Optional crafting recipes, one per weapon, read from the {@code recipes} section of config.yml.
 * Off by default: most servers hand weapons out with /bloodbath give or the armory.
 */
public final class Recipes {
	private static final List<NamespacedKey> REGISTERED = new ArrayList<>();

	private Recipes() {
	}

	public static void register(Plugin plugin) {
		unregister();
		Settings settings = Settings.get();
		if (!settings.recipesEnabled || settings.recipes == null) {
			return;
		}
		Logger log = plugin.getLogger();
		for (WeaponType type : WeaponType.values()) {
			ConfigurationSection section = settings.recipes.getConfigurationSection(type.id());
			if (section == null || !settings.enabled(type)) {
				continue;
			}
			ShapedRecipe recipe = parse(new NamespacedKey(plugin, type.id()), type, section, log);
			if (recipe != null && Bukkit.addRecipe(recipe)) {
				REGISTERED.add(recipe.getKey());
			}
		}
		if (!REGISTERED.isEmpty()) {
			Bukkit.updateRecipes();
			for (Player player : Bukkit.getOnlinePlayers()) {
				discover(player);
			}
			log.info("Registered " + REGISTERED.size() + " weapon recipes.");
		}
	}

	private static ShapedRecipe parse(NamespacedKey key, WeaponType type, ConfigurationSection section, Logger log) {
		List<String> shape = section.getStringList("shape");
		ConfigurationSection ingredients = section.getConfigurationSection("ingredients");
		String where = "recipes." + type.id();
		if (shape.isEmpty() || shape.size() > 3 || ingredients == null) {
			log.warning(where + ": needs a 'shape' of 1-3 rows and an 'ingredients' map. Skipped.");
			return null;
		}
		int width = shape.get(0).length();
		Set<Character> used = new HashSet<>();
		for (String row : shape) {
			if (row.isEmpty() || row.length() > 3 || row.length() != width) {
				log.warning(where + ": every shape row must be 1-3 characters and the same length. Skipped.");
				return null;
			}
			for (char c : row.toCharArray()) {
				if (c != ' ') {
					used.add(c);
				}
			}
		}
		ShapedRecipe recipe = new ShapedRecipe(key, Weapons.create(type));
		recipe.shape(shape.toArray(String[]::new));
		for (char symbol : used) {
			String name = ingredients.getString(String.valueOf(symbol));
			Material material = name == null ? null : Material.matchMaterial(name.trim().toUpperCase(Locale.ROOT));
			if (material == null || !material.isItem() || material.isAir()) {
				log.warning(where + ": '" + symbol + "' is " + (name == null ? "not in 'ingredients'" : "not an item: " + name) + ". Skipped.");
				return null;
			}
			recipe.setIngredient(symbol, material);
		}
		recipe.setGroup("bloodbath");
		recipe.setCategory(CraftingBookCategory.EQUIPMENT);
		return recipe;
	}

	public static void unregister() {
		if (REGISTERED.isEmpty()) {
			return;
		}
		for (NamespacedKey key : REGISTERED) {
			Bukkit.removeRecipe(key, false);
		}
		REGISTERED.clear();
		Bukkit.updateRecipes();
	}

	/** Puts the weapon recipes in the player's recipe book. */
	public static void discover(Player player) {
		if (!REGISTERED.isEmpty() && player.hasPermission("bloodbath.craft")) {
			player.discoverRecipes(REGISTERED);
		}
	}

	public static int count() {
		return REGISTERED.size();
	}
}
