package net.unchartedsmp.bloodbath.support;

import java.util.ArrayList;
import org.bukkit.Bukkit;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;

/** Paper's damage pipeline in miniature: damage event, then health, then a death event with the same source. */
public final class Hits {
	private Hits() {
	}

	/** Returns the health to set, or -1 if the hit was cancelled. Fires the death event when it kills. */
	@SuppressWarnings("removal") // the simple event constructor is all a test needs
	static double apply(LivingEntity victim, double health, double amount, DamageSource source) {
		Entity causing = source.getCausingEntity();
		EntityDamageEvent.DamageCause cause = source.getDamageType() == DamageType.MAGIC
			? EntityDamageEvent.DamageCause.MAGIC : EntityDamageEvent.DamageCause.ENTITY_ATTACK;
		EntityDamageEvent event = causing != null
			? new EntityDamageByEntityEvent(causing, victim, cause, source, amount)
			: new EntityDamageEvent(victim, EntityDamageEvent.DamageCause.CUSTOM, source, amount);
		Bukkit.getPluginManager().callEvent(event);
		if (event.isCancelled()) {
			return -1;
		}
		double left = Math.max(0.0, health - event.getFinalDamage());
		// Like vanilla: a landed hit starts the victim's hurt-immunity.
		victim.setNoDamageTicks(victim.getMaximumNoDamageTicks());
		victim.setLastDamage(event.getFinalDamage());
		if (left <= 0.0) {
			Bukkit.getPluginManager().callEvent(new EntityDeathEvent(victim, source, new ArrayList<>()));
		}
		return left;
	}
}
