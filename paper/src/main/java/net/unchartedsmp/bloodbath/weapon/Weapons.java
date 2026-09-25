package net.unchartedsmp.bloodbath.weapon;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.unchartedsmp.bloodbath.Keys;
import net.unchartedsmp.bloodbath.armor.ArmorPiece;
import net.unchartedsmp.bloodbath.armor.BloodArmor;
import net.unchartedsmp.bloodbath.blood.BloodLevels;
import net.unchartedsmp.bloodbath.config.Settings;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * Builds and recognises weapon items.
 *
 * <p>A weapon is a vanilla netherite sword (or bow) tagged with its id in persistent data and a
 * {@code custom_model_data} string {@code "bloodbath:<id>"}. The resource pack overrides the
 * vanilla sword and bow item definitions to swap in our 3D model for those strings and fall back
 * to the vanilla look otherwise, so a player who declines the pack sees a named netherite sword
 * instead of a missing-texture cube.
 */
public final class Weapons {
	/** Prefix of the custom_model_data strings the pack dispatches on. */
	public static final String MODEL_PREFIX = "bloodbath:";
	/** Tooltip frame sprites in the resource pack: {@code unchartedsmp:tooltip/bloodbath_*}. */
	public static final NamespacedKey TOOLTIP_STYLE = Keys.pack("bloodbath");
	/** Vanilla's own modifier ids, so the tooltip shows "10 Attack Damage" rather than "+9". */
	private static final NamespacedKey BASE_DAMAGE = NamespacedKey.minecraft("base_attack_damage");
	private static final NamespacedKey BASE_SPEED = NamespacedKey.minecraft("base_attack_speed");

	private static String revision = "1";

	private Weapons() {
	}

	/**
	 * Items built under a different revision (plugin update, changed config) are rebuilt the next
	 * time they're seen, keeping kills, enchantments, damage and anvil names.
	 */
	public static void setRevision(String value) {
		revision = value;
	}

	public static String revision() {
		return revision;
	}

	public static WeaponType typeOf(ItemStack stack) {
		if (stack == null) {
			return null;
		}
		Material material = stack.getType();
		if (material != Material.NETHERITE_SWORD && material != Material.BOW) {
			return null; // cheap reject before touching persistent data
		}
		WeaponType type = WeaponType.byId(stack.getPersistentDataContainer().get(Keys.WEAPON, PersistentDataType.STRING));
		// A picture of a weapon in a menu is never a weapon, even if one somehow got out.
		return type != null && stack.getPersistentDataContainer().has(Keys.GUI, PersistentDataType.BYTE) ? null : type;
	}

	public static boolean isWeapon(ItemStack stack) {
		return typeOf(stack) != null;
	}

	public static ItemStack create(WeaponType type) {
		ItemStack stack = new ItemStack(type.base());
		build(stack, type, 0);
		return stack;
	}

	public static int kills(ItemStack stack) {
		Integer kills = stack.getPersistentDataContainer().get(Keys.KILLS, PersistentDataType.INTEGER);
		return kills == null ? 0 : kills;
	}

	/** Rebuilds a weapon made under an older revision. Returns true if anything changed. */
	public static boolean refresh(ItemStack stack) {
		WeaponType type = typeOf(stack);
		if (type == null || revision.equals(stack.getPersistentDataContainer().get(Keys.REVISION, PersistentDataType.STRING))) {
			return false;
		}
		build(stack, type, kills(stack));
		return true;
	}

	/** Rebuilds a weapon's name, tooltip and attributes now (after its Blood Level changed), keeping everything else. */
	public static void rebuild(ItemStack stack) {
		WeaponType type = typeOf(stack);
		if (type != null) {
			build(stack, type, kills(stack));
		}
	}

	static void build(ItemStack stack, WeaponType type, int kills) {
		stack.editMeta(meta -> {
			// The Blood Anvil's level rides along in the same data; every rebuild keeps and shows it.
			int blood = BloodLevels.level(meta.getPersistentDataContainer());
			meta.itemName(Component.text(type.displayName(), NamedTextColor.RED));
			List<Component> lore = lore(type, kills);
			lore.addAll(BloodLevels.weaponLore(type, blood));
			meta.lore(lore);
			model(meta, type);
			meta.setMaxStackSize(1);
			// Enchantment glint smears across every face of a 3D model; the blood aura says "special" instead.
			meta.setEnchantmentGlintOverride(false);
			if (type.attackDamage() > 0) {
				meta.removeAttributeModifier(Attribute.ATTACK_DAMAGE);
				meta.removeAttributeModifier(Attribute.ATTACK_SPEED);
				meta.addAttributeModifier(Attribute.ATTACK_DAMAGE, new AttributeModifier(BASE_DAMAGE, BloodLevels.damageAt(type, blood) - 1.0,
					AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));
				meta.addAttributeModifier(Attribute.ATTACK_SPEED, new AttributeModifier(BASE_SPEED, type.attackSpeed() - 4.0,
					AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));
			}
			if (meta instanceof Damageable damageable) {
				if (type.durability() > 0) {
					meta.setUnbreakable(false);
					damageable.setMaxDamage(type.durability());
				} else {
					meta.setUnbreakable(true);
					meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
				}
			}
			PersistentDataContainer data = meta.getPersistentDataContainer();
			data.set(Keys.WEAPON, PersistentDataType.STRING, type.id());
			data.set(Keys.REVISION, PersistentDataType.STRING, revision);
			data.set(Keys.KILLS, PersistentDataType.INTEGER, kills);
		});
	}

	/** Points the item at our model and, when enabled, our blood tooltip frame. */
	private static void model(ItemMeta meta, WeaponType type) {
		look(meta, type.id(), type.base());
		meta.setTooltipStyle(Settings.get().tooltipFrame() ? TOOLTIP_STYLE : null);
	}

	/**
	 * An item's look: its own model id {@code unchartedsmp:<id>} (items.custom-ids), else the vanilla
	 * item's model, which the pack switches to ours on the {@code bloodbath:<id>} custom model data
	 * (kept either way, so turning the option off needs nothing else).
	 */
	public static void look(ItemMeta meta, String id, Material base) {
		CustomModelDataComponent model = meta.getCustomModelDataComponent();
		model.setStrings(List.of(MODEL_PREFIX + id));
		meta.setCustomModelDataComponent(model);
		meta.setItemModel(itemModel(id, base));
	}

	/** The item model id an item with this id gets: its own, or the vanilla base's. */
	public static NamespacedKey itemModel(String id, Material base) {
		return Settings.get().customItemIds ? Keys.pack(id) : base.getKey();
	}

	private static List<Component> lore(WeaponType type, int kills) {
		List<Component> lines = new ArrayList<>();
		for (String line : type.description()) {
			lines.add(plain(line, NamedTextColor.GRAY));
		}
		lines.add(Component.empty());
		lines.add(plain(type.ability().displayName() + " · cooldown " + cooldownText(type), NamedTextColor.DARK_RED));
		if (Settings.get().killTracking) {
			Rank rank = Rank.of(kills);
			lines.add(plain("☠ " + kills + (kills == 1 ? " kill" : " kills") + " · ", NamedTextColor.DARK_GRAY)
				.append(plain(rank.title(), rank.color())));
		}
		return lines;
	}

	/** "15s" or "7.5s". */
	public static String cooldownText(WeaponType type) {
		double seconds = Settings.get().cooldownTicks(type.ability()) / 20.0;
		return (seconds == Math.rint(seconds) ? String.valueOf((int) seconds) : String.format(Locale.ROOT, "%.1f", seconds)) + "s";
	}

	static Component plain(String text, TextColor color) {
		return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
	}

	/**
	 * A picture of a weapon for menus: looks exactly like it (with the pack: the 3D model; without:
	 * a netherite sword or bow) but is a plain sheet of paper underneath, so even if one ever
	 * escaped a menu it would be worthless.
	 */
	public static ItemStack icon(WeaponType type, List<Component> extraLore) {
		ItemStack icon = new ItemStack(Material.PAPER);
		icon.editMeta(meta -> {
			meta.itemName(Component.text(type.displayName(), NamedTextColor.RED));
			model(meta, type);
			meta.setMaxStackSize(1);
			List<Component> lore = new ArrayList<>(lore(type, 0).subList(0, type.description().size() + 2));
			lore.add(plain(stats(type), NamedTextColor.DARK_GRAY));
			lore.addAll(extraLore);
			meta.lore(lore);
		});
		return icon;
	}

	/** "10 damage · 1.8 speed · 1561 durability" (melee weapons) or "unbreakable". */
	public static String stats(WeaponType type) {
		StringBuilder out = new StringBuilder();
		if (type.attackDamage() > 1) {
			out.append(fmt(type.attackDamage())).append(" damage · ").append(fmt(type.attackSpeed())).append(" speed · ");
		}
		out.append(type.durability() > 0 ? type.durability() + " durability" : "unbreakable");
		return out.toString();
	}

	private static String fmt(double value) {
		return value == Math.rint(value) ? String.valueOf((int) value) : String.format(Locale.ROOT, "%.1f", value);
	}

	/**
	 * What a Blood Mirror wears: just the look of the item, never the item itself. Weapons and
	 * Blood Knight armour keep their look; everything else is a bare item of the same type (no
	 * enchants, no contents).
	 */
	public static ItemStack displayCopy(ItemStack worn) {
		if (worn == null || worn.getType().isAir()) {
			return null;
		}
		ArmorPiece piece = BloodArmor.typeOf(worn);
		if (piece != null) {
			return BloodArmor.displayCopy(worn, piece);
		}
		ItemStack copy = new ItemStack(worn.getType());
		WeaponType type = typeOf(worn);
		if (type != null) {
			copy.editMeta(meta -> look(meta, type.id(), type.base()));
		}
		return copy;
	}
}
