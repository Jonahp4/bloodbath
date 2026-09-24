package net.unchartedsmp.bloodbath.core;

import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.unchartedsmp.bloodbath.Keys;
import net.unchartedsmp.bloodbath.weapon.Weapons;
import org.bukkit.Material;
import org.bukkit.inventory.ItemRarity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;
import org.bukkit.persistence.PersistentDataType;

/**
 * The Blood Core: a nether star underneath, reskinned by the resource pack as a pulsing blood
 * crystal. Every Bloodbath weapon and armour recipe needs one; the Blood Knight drops them and they
 * can be crafted from a nether star.
 *
 * <p>Its look never changes with the config, so a core crafted today still matches the recipes
 * (which compare the exact item) after any reload or update. Nether stars already shrug off
 * explosions, and cores never despawn.
 */
public final class BloodCore {
	public static final String ID = "blood_core";

	private BloodCore() {
	}

	public static ItemStack create(int amount) {
		ItemStack core = new ItemStack(Material.NETHER_STAR, Math.max(1, Math.min(64, amount)));
		core.editMeta(meta -> {
			meta.itemName(Component.text("Blood Core", NamedTextColor.RED));
			meta.lore(List.of(
				line("A knight's heart. It's still beating.", NamedTextColor.GRAY),
				Component.empty(),
				line("Forges every Bloodbath weapon", NamedTextColor.DARK_RED),
				line("and piece of Blood Knight armour.", NamedTextColor.DARK_RED)));
			CustomModelDataComponent model = meta.getCustomModelDataComponent();
			model.setStrings(List.of(Weapons.MODEL_PREFIX + ID));
			meta.setCustomModelDataComponent(model);
			meta.setEnchantmentGlintOverride(false); // the texture pulses instead
			meta.setRarity(ItemRarity.EPIC);
			meta.getPersistentDataContainer().set(Keys.CORE, PersistentDataType.BYTE, (byte) 1);
		});
		return core;
	}

	/** For menus: the core's look on a sheet of paper, like the weapon icons. */
	public static ItemStack icon(List<Component> extraLore) {
		ItemStack icon = new ItemStack(Material.PAPER);
		icon.editMeta(meta -> {
			meta.itemName(Component.text("Blood Core", NamedTextColor.RED));
			meta.setItemModel(Material.NETHER_STAR.getKey());
			CustomModelDataComponent model = meta.getCustomModelDataComponent();
			model.setStrings(List.of(Weapons.MODEL_PREFIX + ID));
			meta.setCustomModelDataComponent(model);
			meta.setRarity(ItemRarity.EPIC);
			List<Component> lore = new java.util.ArrayList<>(List.of(
				line("Needed for every Bloodbath recipe.", NamedTextColor.GRAY),
				line("The Blood Knight drops them; they can", NamedTextColor.GRAY),
				line("also be crafted from a nether star.", NamedTextColor.GRAY)));
			lore.addAll(extraLore);
			meta.lore(lore);
		});
		return icon;
	}

	public static boolean isCore(ItemStack stack) {
		return stack != null && stack.getType() == Material.NETHER_STAR && stack.getPersistentDataContainer().has(Keys.CORE, PersistentDataType.BYTE);
	}

	private static Component line(String text, NamedTextColor color) {
		return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
	}
}
