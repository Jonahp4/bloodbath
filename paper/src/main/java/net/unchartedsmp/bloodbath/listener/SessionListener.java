package net.unchartedsmp.bloodbath.listener;

import java.util.UUID;
import net.unchartedsmp.bloodbath.BloodbathPlugin;
import net.unchartedsmp.bloodbath.armor.BloodArmor;
import net.unchartedsmp.bloodbath.armor.BloodKnightSet;
import net.unchartedsmp.bloodbath.armor.SetBonus;
import net.unchartedsmp.bloodbath.blood.Bleeding;
import net.unchartedsmp.bloodbath.pack.PackState;
import net.unchartedsmp.bloodbath.util.Damage;
import net.unchartedsmp.bloodbath.hud.Hud;
import net.unchartedsmp.bloodbath.recipe.Recipes;
import net.unchartedsmp.bloodbath.weapon.Behaviors;
import net.unchartedsmp.bloodbath.weapon.Weapons;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Join: load the player's HUD preference, bring old weapons up to date, send the resource pack and
 * unlock recipes. Quit: drop short-lived personal state. Cooldowns and clot debuffs are kept on
 * purpose, otherwise relogging would reset them.
 */
public final class SessionListener implements Listener {
	private final BloodbathPlugin plugin;

	public SessionListener(BloodbathPlugin plugin) {
		this.plugin = plugin;
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onJoin(PlayerJoinEvent event) {
		Player player = event.getPlayer();
		PackState.joined(player);
		welcome(player);
		plugin.packs().sendOnJoin(player);
	}

	/** Everything a player needs when they join (or when the plugin is enabled with them online). */
	public static void welcome(Player player) {
		Hud.load(player);
		refreshInventory(player);
		SetBonus.refresh(player);
		Recipes.discover(player);
	}

	public static void refreshInventory(Player player) {
		for (ItemStack stack : player.getInventory().getContents()) {
			if (stack != null && !Weapons.refresh(stack)) {
				BloodArmor.refresh(stack);
			}
		}
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onQuit(PlayerQuitEvent event) {
		UUID id = event.getPlayer().getUniqueId();
		Behaviors.forget(id);
		Hud.forget(id);
		BloodKnightSet.forget(id);
		PackState.forget(event.getPlayer());
		plugin.packs().forget(id);
		Damage.forget(id);
		plugin.weaponListener().forget(id);
		plugin.bloodlands().forget(id);
		Bleeding.forget(id);
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onPackStatus(PlayerResourcePackStatusEvent event) {
		plugin.packs().onStatus(event.getPlayer(), event.getID(), event.getStatus());
		plugin.bosses().packChanged(event.getPlayer());
	}
}
