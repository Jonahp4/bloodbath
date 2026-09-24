package net.unchartedsmp.bloodbath.gui;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.unchartedsmp.bloodbath.command.BloodbathCommand;
import net.unchartedsmp.bloodbath.config.Settings;
import net.unchartedsmp.bloodbath.fx.BloodFx;
import net.unchartedsmp.bloodbath.weapon.WeaponType;
import net.unchartedsmp.bloodbath.weapon.Weapons;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

/**
 * The armory: every weapon on one screen. Players with {@code bloodbath.give} take weapons by
 * clicking; everyone else (and right-clicks) gets the weapon's details in chat.
 *
 * <p>Every icon is a sheet of paper wearing the weapon's look, and every click in the menu is
 * cancelled, so nothing real can be pulled out of it.
 */
public final class ArmoryMenu implements InventoryHolder {
	private static final int SIZE = 36;
	private static final int HEADER_SLOT = 4;
	private static final int CLOSE_SLOT = 31;
	private static final int[] WEAPON_SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25};

	private final Inventory inventory;
	private final Map<Integer, WeaponType> weapons = new HashMap<>();
	private final boolean canTake;

	private ArmoryMenu(Player viewer) {
		canTake = viewer.hasPermission("bloodbath.give");
		inventory = Bukkit.createInventory(this, SIZE, Component.text("☠ Bloodbath Armory", NamedTextColor.DARK_RED));
		ItemStack pane = pane();
		for (int slot = 0; slot < SIZE; slot++) {
			if (slot < 9 || slot >= SIZE - 9 || slot % 9 == 0 || slot % 9 == 8) {
				inventory.setItem(slot, pane);
			}
		}
		List<WeaponType> shown = Arrays.stream(WeaponType.values()).filter(Settings.get()::enabled).toList();
		// Rows that aren't full are centered.
		int firstRow = Math.min(7, shown.size());
		int secondRow = Math.min(7, shown.size() - firstRow);
		for (int i = 0; i < firstRow + secondRow; i++) {
			int slot = i < 7 ? WEAPON_SLOTS[(7 - firstRow) / 2 + i] : WEAPON_SLOTS[7 + (7 - secondRow) / 2 + (i - 7)];
			WeaponType type = shown.get(i);
			weapons.put(slot, type);
			inventory.setItem(slot, Weapons.icon(type, List.of(
				Component.empty(),
				hint(canTake ? "Click to take one" : "Click for details", NamedTextColor.RED),
				hint(canTake ? "Right-click for details" : "Ask an admin for one", NamedTextColor.DARK_GRAY))));
		}
		inventory.setItem(HEADER_SLOT, header(shown.size()));
		inventory.setItem(CLOSE_SLOT, button(Material.BARRIER, "Close"));
	}

	public static void open(Player player) {
		player.openInventory(new ArmoryMenu(player).getInventory());
		player.playSound(player.getLocation(), BloodFx.PAGE, SoundCategory.PLAYERS, 0.8F, 0.7F);
	}

	@Override
	public Inventory getInventory() {
		return inventory;
	}

	/** A click in the top inventory (the event is already cancelled). */
	void click(Player player, int slot, ClickType click) {
		if (slot == CLOSE_SLOT) {
			player.closeInventory();
			return;
		}
		WeaponType type = weapons.get(slot);
		if (type == null) {
			return;
		}
		if (!canTake || click.isRightClick() || !player.hasPermission("bloodbath.give")) {
			player.closeInventory();
			BloodbathCommand.sendInfo(player, type);
			return;
		}
		BloodbathCommand.giveTo(player, type);
	}

	private static ItemStack pane() {
		ItemStack pane = new ItemStack(Material.RED_STAINED_GLASS_PANE);
		pane.editMeta(meta -> meta.setHideTooltip(true));
		return pane;
	}

	private static ItemStack header(int count) {
		ItemStack header = Weapons.icon(WeaponType.BLOOD_GRIMOIRE, List.of());
		header.editMeta(meta -> {
			meta.itemName(Component.text("Bloodbath Armory", NamedTextColor.RED));
			meta.lore(List.of(
				hint(count + " blood weapons, each with its own ability.", NamedTextColor.GRAY),
				hint("Hold one to see its cooldown on your action bar.", NamedTextColor.GRAY),
				hint("/bloodbath help for commands", NamedTextColor.DARK_GRAY)));
		});
		return header;
	}

	private static ItemStack button(Material material, String name) {
		ItemStack button = new ItemStack(material);
		button.editMeta(meta -> meta.itemName(Component.text(name, NamedTextColor.RED)));
		return button;
	}

	private static Component hint(String text, NamedTextColor color) {
		return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
	}
}
