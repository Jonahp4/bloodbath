package net.unchartedsmp.bloodbath.weapon.behavior;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.unchartedsmp.bloodbath.ability.Cooldowns;
import net.unchartedsmp.bloodbath.ability.NullField;
import net.unchartedsmp.bloodbath.ability.ServerClock;
import net.unchartedsmp.bloodbath.ability.TickScheduler;
import net.unchartedsmp.bloodbath.fx.BloodFx;
import net.unchartedsmp.bloodbath.fx.Shapes;
import net.unchartedsmp.bloodbath.util.Targeting;
import net.unchartedsmp.bloodbath.weapon.WeaponBehavior;
import net.unchartedsmp.bloodbath.weapon.WeaponType;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

/**
 * Clotblade (id {@code nullblade}): hitting a player clots their blood, suppressing their
 * abilities for 2.5s. A player can only be clotted by hits once every 8s, so chain-hitting can't
 * lock someone out for a whole fight. Right-click throws down an 8s clot field that suppresses
 * everyone inside. Its own field never stops the Clotblade.
 */
public final class Clotblade implements WeaponBehavior {
	@Override
	public WeaponType type() {
		return WeaponType.NULLBLADE;
	}

	@Override
	public boolean canBeNullified() {
		return false;
	}

	/** Victim → the tick a hit may clot them again: one clot per window, never a permanent lock. */
	private final Map<UUID, Long> clotImmune = new HashMap<>();

	@Override
	public void melee(Player player, LivingEntity target, double damage) {
		if (target instanceof Player victim) {
			long now = ServerClock.now();
			Long immuneUntil = clotImmune.get(victim.getUniqueId());
			if (immuneUntil != null && now < immuneUntil) {
				BloodFx.burst(BloodFx.chest(victim), BloodFx.CLOT, 3, 0.25);
				return;
			}
			clotImmune.put(victim.getUniqueId(), now + ticksSetting("clot-immunity", 160));
			NullField.debuff(victim, ticksSetting("hit-clot", 50));
			Location chest = BloodFx.chest(victim);
			BloodFx.play(victim, BloodFx.NULLIFY, 0.6F, 1.5F);
			BloodFx.burst(chest, BloodFx.CLOT, 10, 0.3);
			BloodFx.burst(chest, BloodFx.SOUL, 4, 0.3);
			Location feet = victim.getLocation();
			for (double h : new double[] {0.25, 1.0, 1.75}) {
				BloodFx.ring(feet.clone().add(0.0, h, 0.0), BloodFx.CLOT, 0.55, 10);
			}
			Shapes.spiral(feet, BloodFx.BLOOD_FADE, 0.6, 2.0, 1.5, 14, 0.0);
			NullField.notifyNullified(victim);
		}
	}

	@Override
	public void use(Player player) {
		if (!Cooldowns.checkReady(player, ability())) {
			return;
		}
		double radius = setting("field-radius", 4.0);
		int duration = ticksSetting("field-duration", 160);
		Location center = Targeting.lookTarget(player, 8.0);
		NullField.createZone(center, radius, duration);
		BloodFx.burst(center, BloodFx.CLOT, 30, 2.0);
		BloodFx.burst(center, BloodFx.SOUL, 20, 2.0);
		BloodFx.play(center, BloodFx.NULLIFY, 1.0F, 0.7F);
		BloodFx.play(center, BloodFx.HEARTBEAT, 1.0F, 0.6F);
		Cooldowns.start(player, ability());

		// Show the field's edge for as long as it's active so players can see where it ends.
		Location floor = center.clone().add(0.0, 0.1, 0.0);
		Location band = center.clone().add(0.0, 0.7, 0.0);
		Shapes.shockwave(floor, BloodFx.CLOT, radius, 8);
		TickScheduler.repeat(10, 10, duration / 10, tick -> {
			BloodFx.ring(floor, BloodFx.CLOT, radius, 28);
			Shapes.dome(floor, BloodFx.BLOOD_FADE, radius, 4, 20, tick * 0.25);
			BloodFx.burst(center, BloodFx.SOUL, 3, radius * 0.5);
			BloodFx.burst(center, BloodFx.MIST, 2, radius * 0.4, 0.0);
			return true;
		});
	}

	@Override
	public BloodFx.Fx auraAccent() {
		return BloodFx.CLOT;
	}

	@Override
	public void forget(UUID playerId) {
		clotImmune.remove(playerId);
	}

	@Override
	public void prune() {
		long now = ServerClock.now();
		clotImmune.values().removeIf(until -> until < now);
	}

	@Override
	public void shutdown() {
		clotImmune.clear();
	}
}
