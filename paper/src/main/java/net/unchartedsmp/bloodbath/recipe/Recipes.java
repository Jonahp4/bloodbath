package net.unchartedsmp.bloodbath.recipe;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Logger;
import net.unchartedsmp.bloodbath.anvil.BloodAnvils;
import net.unchartedsmp.bloodbath.armor.ArmorPiece;
import net.unchartedsmp.bloodbath.armor.BloodArmor;
import net.unchartedsmp.bloodbath.config.Settings;
import net.unchartedsmp.bloodbath.core.BloodCore;
import net.unchartedsmp.bloodbath.portal.Frames;
import net.unchartedsmp.bloodbath.weapon.WeaponType;
import net.unchartedsmp.bloodbath.weapon.Weapons;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.recipe.CraftingBookCategory;
import org.bukkit.plugin.Plugin;

/**
 * Crafting recipes, one per weapon and Blood Knight piece plus the Blood Core itself, read from
 * the {@code recipes} section of config.yml.
 *
 * <p>Every weapon and armour recipe needs a Blood Core ({@code BLOOD_CORE} in the ingredients, an
 * exact-item match). A recipe in an older config that has no core falls back to the built-in one
 * for that item (with a note in the log), so nothing Bloodbath is ever craftable without a core.
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
		ConfigurationSection defaults = plugin.getConfig().getDefaults() == null ? null
			: plugin.getConfig().getDefaults().getConfigurationSection("recipes");
		for (WeaponType type : WeaponType.values()) {
			if (settings.enabled(type)) {
				add(plugin, type.id(), Weapons.create(type), settings.recipes, defaults, true, log);
			}
		}
		if (settings.armorEnabled) {
			for (ArmorPiece piece : ArmorPiece.values()) {
				add(plugin, piece.id(), BloodArmor.create(piece), settings.recipes, defaults, true, log);
			}
		}
		add(plugin, BloodCore.ID, BloodCore.create(1), settings.recipes, defaults, false, log);
		if (settings.blood.lands().enabled()) {
			add(plugin, Frames.ID, Frames.item(8), settings.recipes, defaults, true, log);
		}
		if (settings.blood.anvil().enabled() && settings.blood.anvil().craftable()) {
			add(plugin, BloodAnvils.ID, BloodAnvils.item(1), settings.recipes, defaults, true, log);
		}
		if (!REGISTERED.isEmpty()) {
			Bukkit.updateRecipes();
			for (Player player : Bukkit.getOnlinePlayers()) {
				discover(player);
			}
			log.info("Registered " + REGISTERED.size() + " Bloodbath recipes.");
		}
	}

	private static void add(Plugin plugin, String id, ItemStack result, ConfigurationSection recipes, ConfigurationSection defaults,
		boolean needsCore, Logger log) {
		ConfigurationSection section = recipes.getConfigurationSection(id);
		if (section == null) {
			return;
		}
		if (needsCore && !mentionsCore(section)) {
			ConfigurationSection fallback = defaults == null ? null : defaults.getConfigurationSection(id);
			if (fallback == null || !mentionsCore(fallback)) {
				log.warning("recipes." + id + " has no BLOOD_CORE and there's no built-in recipe to use instead. Skipped.");
				return;
			}
			log.info("recipes." + id + " has no BLOOD_CORE (an older config?): using the built-in recipe, which needs one.");
			section = fallback;
		}
		ShapedRecipe recipe = parse(new NamespacedKey(plugin, id), result, section, log);
		if (recipe != null && Bukkit.addRecipe(recipe)) {
			REGISTERED.add(recipe.getKey());
		}
	}

	private static ShapedRecipe parse(NamespacedKey key, ItemStack result, ConfigurationSection section, Logger log) {
		List<String> shape = section.getStringList("shape");
		ConfigurationSection ingredients = section.getConfigurationSection("ingredients");
		String where = "recipes." + key.getKey();
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
		ShapedRecipe recipe = new ShapedRecipe(key, result);
		recipe.shape(shape.toArray(String[]::new));
		for (char symbol : used) {
			String name = ingredients.getString(String.valueOf(symbol));
			if (name != null && isCore(name)) {
				recipe.setIngredient(symbol, new RecipeChoice.ExactChoice(BloodCore.create(1)));
				continue;
			}
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

	private static boolean isCore(String name) {
		return name.trim().equalsIgnoreCase("BLOOD_CORE") || name.trim().equalsIgnoreCase("bloodbath:blood_core");
	}

	private static boolean mentionsCore(ConfigurationSection section) {
		ConfigurationSection ingredients = section.getConfigurationSection("ingredients");
		if (ingredients == null) {
			return false;
		}
		for (String key : ingredients.getKeys(false)) {
			String name = ingredients.getString(key);
			if (name != null && isCore(name)) {
				return true;
			}
		}
		return false;
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

	/** Puts the Bloodbath recipes in the player's recipe book. */
	public static void discover(Player player) {
		if (!REGISTERED.isEmpty() && player.hasPermission("bloodbath.craft")) {
			player.discoverRecipes(REGISTERED);
		}
	}

	public static int count() {
		return REGISTERED.size();
	}
}
