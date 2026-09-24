package net.unchartedsmp.bloodbath.listener;

import net.unchartedsmp.bloodbath.ability.TickScheduler;
import net.unchartedsmp.bloodbath.weapon.Behaviors;
import net.unchartedsmp.bloodbath.weapon.behavior.Mirrorfang;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.event.world.EntitiesUnloadEvent;

/**
 * Blood Mirrors are armor stands dressed in copies of the caster's gear, which makes them the one
 * place an item dupe could hide. Nothing gets in or out: no interaction, no damage, no drops,
 * never saved, and any mirror that isn't live in this session is deleted on sight.
 */
public final class MirrorGuard implements Listener {
	@EventHandler(priority = EventPriority.LOWEST)
	public void onInteract(PlayerInteractEntityEvent event) {
		if (Mirrorfang.isMirror(event.getRightClicked())) {
			event.setCancelled(true);
		}
	}

	@EventHandler(priority = EventPriority.LOWEST)
	public void onInteractAt(PlayerInteractAtEntityEvent event) {
		if (Mirrorfang.isMirror(event.getRightClicked())) {
			event.setCancelled(true);
		}
	}

	@EventHandler(priority = EventPriority.LOWEST)
	public void onManipulate(PlayerArmorStandManipulateEvent event) {
		if (Mirrorfang.isMirror(event.getRightClicked())) {
			event.setCancelled(true);
		}
	}

	@EventHandler(priority = EventPriority.LOWEST)
	public void onDamage(EntityDamageEvent event) {
		if (Mirrorfang.isMirror(event.getEntity())) {
			event.setCancelled(true);
		}
	}

	/** If something kills one anyway (/kill, another plugin), it drops nothing. */
	@EventHandler(priority = EventPriority.HIGHEST)
	public void onDeath(EntityDeathEvent event) {
		if (Mirrorfang.isMirror(event.getEntity())) {
			event.getDrops().clear();
			event.setDroppedExp(0);
		}
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onLoad(EntitiesLoadEvent event) {
		for (Entity entity : event.getEntities()) {
			if (Mirrorfang.isMirror(entity) && !Behaviors.MIRRORFANG.isLive(entity)) {
				// Deferred a tick: removing entities from inside their own load event is unsafe.
				TickScheduler.schedule(0, entity::remove);
			}
		}
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onUnload(EntitiesUnloadEvent event) {
		for (Entity entity : event.getEntities()) {
			if (Mirrorfang.isMirror(entity)) {
				Behaviors.MIRRORFANG.unloaded(entity);
			}
		}
	}
}
