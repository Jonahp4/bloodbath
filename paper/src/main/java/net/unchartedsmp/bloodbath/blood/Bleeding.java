package net.unchartedsmp.bloodbath.blood;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.unchartedsmp.bloodbath.ability.ServerClock;
import net.unchartedsmp.bloodbath.config.BloodConfig;
import net.unchartedsmp.bloodbath.config.Settings;
import net.unchartedsmp.bloodbath.fx.BloodFx;
import net.unchartedsmp.bloodbath.util.Damage;
import net.unchartedsmp.bloodbath.util.Targeting;
import net.unchartedsmp.bloodbath.weapon.WeaponType;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

/**
 * Bloodbath's bleeding: one entry per bleeding creature, all of them driven by the plugin's single
 * tick (never a task per bleed). Blood water, bled weapons and the Blood Knight all apply it.
 *
 * <p>Each interval a bleed deals {@code damage × stacks} as magic damage (armour doesn't stop blood
 * loss), credited to whoever caused it when they're still around, and drips. It fades after its
 * duration unless it's topped up; {@code bleeding.stacking} decides what a new bleed does to a
 * running one. Bleeding never shoves or grants hurt-immunity, so it can't swallow a real hit.
 *
 * <p>(The Hemorrhage Scythe keeps its own per-attacker stack counter: its stacks are a combo
 * meter that ends in a hemorrhage, not a damage-over-time.)
 */
public final class Bleeding {
	private record Bleed(LivingEntity target, int stacks, long expiresAt, long nextTick, UUID attacker, WeaponType source) {
	}

	private static final Map<UUID, Bleed> BLEEDS = new HashMap<>();

	private Bleeding() {
	}

	private static BloodConfig.Bleed config() {
		return Settings.get().blood.bleed();
	}

	/** Makes {@code target} bleed for the configured duration. */
	public static void apply(LivingEntity target, int stacks, Player attacker, WeaponType source) {
		apply(target, stacks, config().durationTicks(), attacker, source);
	}

	/**
	 * @param attacker who gets the kill if it bleeds out (null: nobody, e.g. blood water)
	 * @param source the weapon behind it, for kill tracking (or null)
	 */
	public static void apply(LivingEntity target, int stacks, int durationTicks, Player attacker, WeaponType source) {
		if (stacks <= 0 || durationTicks <= 0 || target == null || !target.isValid() || target.isDead()) {
			return;
		}
		BloodConfig.Bleed config = config();
		long now = ServerClock.now();
		Bleed running = BLEEDS.get(target.getUniqueId());
		if (running != null && now > running.expiresAt()) {
			running = null;
		}
		int total;
		long expires = now + durationTicks;
		long next = running == null ? now + config.intervalTicks() : running.nextTick();
		if (running == null) {
			total = Math.min(config.maxStacks(), stacks);
		} else {
			switch (config.stacking()) {
				case NONE -> {
					return;
				}
				case REFRESH -> total = Math.max(running.stacks(), Math.min(config.maxStacks(), stacks));
				default -> total = Math.min(config.maxStacks(), running.stacks() + stacks);
			}
			expires = Math.max(expires, running.expiresAt());
		}
		UUID credit = attacker != null ? attacker.getUniqueId() : running == null ? null : running.attacker();
		WeaponType weapon = attacker != null ? source : running == null ? null : running.source();
		BLEEDS.put(target.getUniqueId(), new Bleed(target, total, expires, next, credit, weapon));
		Location chest = BloodFx.chest(target);
		BloodFx.burst(chest, BloodFx.BLOOD_FADE, 3 + total * 2, 0.25);
	}

	public static int stacks(LivingEntity target) {
		Bleed bleed = BLEEDS.get(target.getUniqueId());
		return bleed == null || ServerClock.now() > bleed.expiresAt() ? 0 : bleed.stacks();
	}

	/** Ticks left on the target's bleed, or 0. */
	public static long remaining(LivingEntity target) {
		Bleed bleed = BLEEDS.get(target.getUniqueId());
		return bleed == null ? 0 : Math.max(0, bleed.expiresAt() - ServerClock.now());
	}

	public static void stop(LivingEntity target) {
		BLEEDS.remove(target.getUniqueId());
	}

	public static int count() {
		return BLEEDS.size();
	}

	/** Called every tick by the plugin: hurts whatever bleed is due. */
	public static void tick(long now) {
		if (BLEEDS.isEmpty()) {
			return;
		}
		BloodConfig.Bleed config = config();
		// Collect first: the damage can kill, and death handlers must not see the map mid-iteration.
		List<Bleed> due = null;
		var it = BLEEDS.values().iterator();
		while (it.hasNext()) {
			Bleed bleed = it.next();
			if (now > bleed.expiresAt() || !bleed.target().isValid() || bleed.target().isDead()) {
				it.remove();
				continue;
			}
			if (now >= bleed.nextTick()) {
				if (due == null) {
					due = new ArrayList<>();
				}
				due.add(bleed);
			}
		}
		if (due == null) {
			return;
		}
		for (Bleed bleed : due) {
			BLEEDS.replace(bleed.target().getUniqueId(), bleed,
				new Bleed(bleed.target(), bleed.stacks(), bleed.expiresAt(), now + config.intervalTicks(), bleed.attacker(), bleed.source()));
		}
		for (Bleed bleed : due) {
			hurt(bleed, config);
		}
	}

	private static void hurt(Bleed bleed, BloodConfig.Bleed config) {
		LivingEntity target = bleed.target();
		double amount = config.damagePerStack() * bleed.stacks();
		Location chest = BloodFx.chest(target);
		int drops = Math.min(8, config.particles() * bleed.stacks());
		if (drops > 0) {
			BloodFx.burst(chest, BloodFx.DRIP, drops, 0.25, 0.0);
			BloodFx.burst(chest, BloodFx.SPLATTER, Math.min(3, bleed.stacks()), 0.2, 0.05);
			// A little spurt from the wound with every pulse, higher the deeper it is.
			double side = (target.getEntityId() % 2 == 0 ? 1 : -1) * 0.3;
			BloodFx.flow(chest, chest.clone().add(side, 0.35 + 0.12 * Math.min(5, bleed.stacks()), 0.0), Math.min(4, bleed.stacks() + 1),
				0.1, BloodFx.BRIGHT_RED, 6);
		}
		if (amount <= 0.0) {
			return;
		}
		Player attacker = bleed.attacker() == null ? null : Bukkit.getPlayer(bleed.attacker());
		if (attacker != null && attacker.isValid() && attacker != target) {
			if (Targeting.validTarget(attacker, target)) {
				Damage.deal(target, amount, attacker, bleed.source(), null, false);
			}
			return;
		}
		// Nobody to credit (blood water, a player who left): plain blood loss.
		int immunity = target.getNoDamageTicks();
		double last = target.getLastDamage();
		target.setNoDamageTicks(0);
		target.damage(amount, DamageSource.builder(DamageType.MAGIC).build());
		if (target.isValid() && !target.isDead()) {
			target.setNoDamageTicks(immunity);
			target.setLastDamage(last);
		}
	}

	public static void forget(UUID entity) {
		BLEEDS.remove(entity);
	}

	public static void clearAll() {
		BLEEDS.clear();
	}
}
