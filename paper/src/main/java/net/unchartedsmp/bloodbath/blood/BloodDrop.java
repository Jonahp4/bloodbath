package net.unchartedsmp.bloodbath.blood;

import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.TooltipDisplay;
import net.unchartedsmp.bloodbath.Keys;
import net.unchartedsmp.bloodbath.weapon.Weapons;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Material;
import org.bukkit.inventory.ItemRarity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkEffectMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;
import org.bukkit.persistence.PersistentDataType;

/**
 * The Blood Drop: a rare bead of blood from the Bloodlands, the currency of the Blood Anvil.
 *
 * <p>A firework star underneath (it can't be placed, eaten, brewed or used as fuel, and it's kept
 * out of every crafting grid), coloured blood red so it reads right even without the resource pack.
 * It's recognised only by its persistent-data tag, which survives restarts, relogs and every kind
 * of inventory movement, and which a player can't forge: renaming a firework star "Blood Drop" in
 * an anvil makes nothing but a firework star with a silly name.
 *
 * <p>Its look never changes with the config, so drops from any version stack together.
 */
public final class BloodDrop {
	public static final String ID = "blood_drop";
	private static final Color BLOOD = Color.fromRGB(0x9E0A18);

	private BloodDrop() {
	}

	public static ItemStack create(int amount) {
		ItemStack drop = new ItemStack(Material.FIREWORK_STAR, Math.max(1, Math.min(64, amount)));
		drop.editMeta(meta -> {
			meta.itemName(Component.text("Blood Drop", NamedTextColor.RED));
			meta.lore(List.of(
				line("A bead of blood that never dries,", NamedTextColor.GRAY),
				line("found only in the Bloodlands.", NamedTextColor.GRAY),
				Component.empty(),
				line("Bleed a weapon at a Blood Anvil.", NamedTextColor.DARK_RED)));
			CustomModelDataComponent model = meta.getCustomModelDataComponent();
			model.setStrings(List.of(Weapons.MODEL_PREFIX + ID));
			meta.setCustomModelDataComponent(model);
			meta.setRarity(ItemRarity.RARE);
			if (meta instanceof FireworkEffectMeta star) {
				// Tints the vanilla star red for players without the pack (the line it adds is hidden below).
				star.setEffect(FireworkEffect.builder().with(FireworkEffect.Type.BALL).withColor(BLOOD).build());
			}
			meta.getPersistentDataContainer().set(Keys.BLOOD_DROP, PersistentDataType.BYTE, (byte) 1);
		});
		hideStarLine(drop);
		return drop;
	}

	public static boolean isDrop(ItemStack stack) {
		return stack != null && stack.getType() == Material.FIREWORK_STAR && !stack.isEmpty()
			&& stack.getPersistentDataContainer().has(Keys.BLOOD_DROP, PersistentDataType.BYTE)
			&& !stack.getPersistentDataContainer().has(Keys.GUI, PersistentDataType.BYTE);
	}

	/** For menus: the drop's look on a sheet of paper, never a real drop. */
	public static ItemStack icon(int amount, List<Component> lore) {
		ItemStack icon = new ItemStack(Material.PAPER, Math.max(1, Math.min(99, amount)));
		icon.editMeta(meta -> {
			meta.itemName(Component.text("Blood Drop", NamedTextColor.RED));
			meta.setItemModel(Material.FIREWORK_STAR.getKey());
			CustomModelDataComponent model = meta.getCustomModelDataComponent();
			model.setStrings(List.of(Weapons.MODEL_PREFIX + ID));
			meta.setCustomModelDataComponent(model);
			meta.setMaxStackSize(99);
			meta.lore(new ArrayList<>(lore));
		});
		return icon;
	}

	@SuppressWarnings("UnstableApiUsage")
	private static void hideStarLine(ItemStack drop) {
		try {
			drop.setData(DataComponentTypes.TOOLTIP_DISPLAY,
				TooltipDisplay.tooltipDisplay().addHiddenComponents(DataComponentTypes.FIREWORK_EXPLOSION));
		} catch (UnsupportedOperationException | IllegalStateException e) {
			// A test server without item components: only the tooltip is affected.
		}
	}

	static Component line(String text, NamedTextColor color) {
		return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
	}
}
