package net.unchartedsmp.bloodbath.gui;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

/** Nothing moves in or out of the armory; clicks on its top half are handed to the menu. */
public final class MenuListener implements Listener {
	@EventHandler(priority = EventPriority.LOWEST)
	public void onClick(InventoryClickEvent event) {
		if (!(event.getView().getTopInventory().getHolder(false) instanceof ArmoryMenu menu)) {
			return;
		}
		event.setCancelled(true);
		if (event.getWhoClicked() instanceof Player player && event.getClickedInventory() == event.getView().getTopInventory()) {
			menu.click(player, event.getSlot(), event.getClick());
		}
	}

	@EventHandler(priority = EventPriority.LOWEST)
	public void onDrag(InventoryDragEvent event) {
		if (event.getView().getTopInventory().getHolder(false) instanceof ArmoryMenu) {
			event.setCancelled(true);
		}
	}
}
