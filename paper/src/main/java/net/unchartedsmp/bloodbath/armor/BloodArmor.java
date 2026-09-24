package net.unchartedsmp.bloodbath.armor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.unchartedsmp.bloodbath.Keys;
import net.unchartedsmp.bloodbath.ability.Ability;
import net.unchartedsmp.bloodbath.config.Settings;
import net.unchartedsmp.bloodbath.weapon.Weapons;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;
import org.bukkit.inventory.meta.components.EquippableComponent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * Builds and recognises Blood Knight armour. Like the weapons, each piece is a vanilla item
 * (netherite armour) tagged in persistent data, with a {@code custom_model_data} string for its
 * inventory model and an {@code equippable} asset for the worn look, both from the resource pack.
 */
public final class BloodArmor {
	/** The worn look: {@code assets/unchartedsmp/equipment/blood_knight.json} in the pack. */
	public static final NamespacedKey EQUIPMENT = Keys.pack("blood_knight");

	private BloodArmor() {
	}

	public static ArmorPiece typeOf(ItemStack stack) {
		if (stack == null) {
			return null;
		}
		Material material = stack.getType();
		if (material != Material.NETHERITE_HELMET && material != Material.NETHERITE_CHESTPLATE
			&& material != Material.NETHERITE_LEGGINGS && material != Material.NETHERITE_BOOTS) {
			return null; // cheap reject before touching persistent data
		}
		String id = stack.getPersistentDataContainer().get(Keys.ARMOR, PersistentDataType.STRING);
		ArmorPiece piece = id == null ? null : ArmorPiece.find(id);
		return piece != null && piece.base() == material ? piece : null;
	}

	public static boolean isArmor(ItemStack stack) {
		return typeOf(stack) != null;
	}

	/** How many Blood Knight pieces the player is wearing, each in its own slot. */
	public static int worn(Player player) {
		EntityEquipment equipment = player.getEquipment();
		int count = 0;
		for (ArmorPiece piece : ArmorPiece.values()) {
			if (typeOf(equipment.getItem(piece.slot())) == piece) {
				count++;
			}
		}
		return count;
	}

	public static ItemStack create(ArmorPiece piece) {
		ItemStack stack = new ItemStack(piece.base());
		build(stack, piece);
		return stack;
	}

	/** Rebuilds a piece made under an older revision (plugin update, changed config). */
	public static boolean refresh(ItemStack stack) {
		ArmorPiece piece = typeOf(stack);
		if (piece == null || Weapons.revision().equals(stack.getPersistentDataContainer().get(Keys.REVISION, PersistentDataType.STRING))) {
			return false;
		}
		build(stack, piece);
		return true;
	}

	private static void build(ItemStack stack, ArmorPiece piece) {
		stack.editMeta(meta -> {
			meta.itemName(Component.text(piece.displayName(), NamedTextColor.RED));
			meta.lore(lore(piece));
			look(meta, piece);
			meta.setEnchantmentGlintOverride(false);
			for (Attribute attribute : List.of(Attribute.ARMOR, Attribute.ARMOR_TOUGHNESS, Attribute.KNOCKBACK_RESISTANCE, Attribute.MAX_HEALTH)) {
				meta.removeAttributeModifier(attribute);
			}
			// Setting any modifier replaces the item's defaults, so netherite's go back on first, then
			// the extra heart. One id per attribute: Bukkit refuses two modifiers with the same id.
			add(meta, Attribute.ARMOR, "armor", piece.armor(), piece);
			add(meta, Attribute.ARMOR_TOUGHNESS, "toughness", ArmorPiece.TOUGHNESS, piece);
			add(meta, Attribute.KNOCKBACK_RESISTANCE, "knockback_resistance", ArmorPiece.KNOCKBACK_RESISTANCE, piece);
			add(meta, Attribute.MAX_HEALTH, "health", ArmorPiece.HEALTH, piece);
			PersistentDataContainer data = meta.getPersistentDataContainer();
			data.set(Keys.ARMOR, PersistentDataType.STRING, piece.id());
			data.set(Keys.REVISION, PersistentDataType.STRING, Weapons.revision());
		});
	}

	private static void add(ItemMeta meta, Attribute attribute, String name, double amount, ArmorPiece piece) {
		NamespacedKey id = new NamespacedKey(Keys.ARMOR.getNamespace(), name + "." + piece.kind());
		meta.addAttributeModifier(attribute, new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_NUMBER, piece.group()));
	}

	/** Inventory model, worn look and (when enabled) the blood tooltip frame. */
	private static void look(ItemMeta meta, ArmorPiece piece) {
		CustomModelDataComponent model = meta.getCustomModelDataComponent();
		model.setStrings(List.of(Weapons.MODEL_PREFIX + piece.id()));
		meta.setCustomModelDataComponent(model);
		EquippableComponent equippable = meta.getEquippable();
		equippable.setSlot(piece.slot());
		equippable.setModel(EQUIPMENT);
		equippable.setEquipSound(Sound.ITEM_ARMOR_EQUIP_NETHERITE);
		meta.setEquippable(equippable);
		meta.setTooltipStyle(Settings.get().tooltipFrame() ? Weapons.TOOLTIP_STYLE : null);
	}

	private static List<Component> lore(ArmorPiece piece) {
		Settings settings = Settings.get();
		List<Component> lines = new ArrayList<>();
		for (String line : piece.description()) {
			lines.add(plain(line, NamedTextColor.GRAY));
		}
		lines.add(Component.empty());
		lines.add(plain("Blood Knight set", NamedTextColor.DARK_RED));
		lines.add(plain("(2) Bloodlust: kills heal " + hearts(settings.armorKillHeal), NamedTextColor.GRAY));
		lines.add(plain("(3) Barbed Blood: melee attackers", NamedTextColor.GRAY));
		lines.add(plain("    take " + hearts(settings.armorBarbDamage) + " back", NamedTextColor.GRAY));
		lines.add(plain("(4) Blood Rage: below " + Math.round(settings.rageThreshold * 100) + "% health, gain", NamedTextColor.GRAY));
		lines.add(plain("    Strength and Resistance and hurl", NamedTextColor.GRAY));
		lines.add(plain("    enemies back. Every " + Math.round(settings.cooldownTicks(Ability.BLOOD_RAGE) / 20.0) + "s.", NamedTextColor.GRAY));
		return lines;
	}

	private static String hearts(double health) {
		double hearts = health / 2.0;
		return (hearts == Math.rint(hearts) ? String.valueOf((int) hearts) : String.format(Locale.ROOT, "%.1f", hearts))
			+ (hearts == 1.0 ? " heart" : " hearts");
	}

	private static Component plain(String text, TextColor color) {
		return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
	}

	/** For menus: the piece's look on a sheet of paper, like the weapon icons. */
	public static ItemStack icon(ArmorPiece piece, List<Component> extraLore) {
		ItemStack icon = new ItemStack(Material.PAPER);
		icon.editMeta(meta -> {
			meta.itemName(Component.text(piece.displayName(), NamedTextColor.RED));
			meta.setItemModel(piece.base().getKey());
			CustomModelDataComponent model = meta.getCustomModelDataComponent();
			model.setStrings(List.of(Weapons.MODEL_PREFIX + piece.id()));
			meta.setCustomModelDataComponent(model);
			meta.setTooltipStyle(Settings.get().tooltipFrame() ? Weapons.TOOLTIP_STYLE : null);
			meta.setMaxStackSize(1);
			List<Component> lore = new ArrayList<>(lore(piece));
			lore.addAll(extraLore);
			meta.lore(lore);
		});
		return icon;
	}

	/** What a Blood Mirror wears for this piece: its look, none of its stats or data. */
	public static ItemStack displayCopy(ItemStack worn, ArmorPiece piece) {
		ItemStack copy = new ItemStack(worn.getType());
		copy.editMeta(meta -> {
			CustomModelDataComponent model = meta.getCustomModelDataComponent();
			model.setStrings(List.of(Weapons.MODEL_PREFIX + piece.id()));
			meta.setCustomModelDataComponent(model);
			EquippableComponent equippable = meta.getEquippable();
			equippable.setSlot(piece.slot());
			equippable.setModel(EQUIPMENT);
			meta.setEquippable(equippable);
		});
		return copy;
	}
}
