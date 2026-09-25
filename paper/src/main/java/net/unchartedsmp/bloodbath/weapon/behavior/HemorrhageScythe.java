package net.unchartedsmp.bloodbath.weapon.behavior;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.unchartedsmp.bloodbath.ability.Cooldowns;
import net.unchartedsmp.bloodbath.ability.ServerClock;
import net.unchartedsmp.bloodbath.ability.TickScheduler;
import net.unchartedsmp.bloodbath.fx.BloodFx;
import net.unchartedsmp.bloodbath.fx.Shapes;
import net.unchartedsmp.bloodbath.hud.Hud;
import net.unchartedsmp.bloodbath.util.Damage;
import net.unchartedsmp.bloodbath.util.Targeting;
import net.unchartedsmp.bloodbath.weapon.WeaponBehavior;
import net.unchartedsmp.bloodbath.weapon.WeaponType;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/**
 * Hemorrhage Scythe (id {@code void_scythe}): every hit makes the target bleed, and bleeding hurts:
 * each stack deals half a heart's worth every 1.5s until it fades. The 5th hit within
 * 8s of the last one hemorrhages them for 10 damage and rips 8 damage through everything within
 * 4 blocks. Right-click reaps: a blood arc in front of you that cuts and adds a bleed stack to
 * everything it touches.
 *
 * <p>Bleed stacks are tracked per attacker <i>and</i> target, so two scythes can't stack each
 * other's bleed, and stale entries are pruned.
 */
public final class HemorrhageScythe implements WeaponBehavior {
	private static final int STACK_TIMEOUT_TICKS = 160;
	private static final double REAP_RANGE = 3.5;
	private static final double REAP_HALF_ANGLE_COS = Math.cos(Math.toRadians(60));

	private record BleedKey(UUID attacker, UUID target) {
	}

	/** {@code nextTick}: when this bleed next hurts, counted from when it was first applied. */
	private record Bleed(int stacks, long expiresAt, LivingEntity target, long nextTick) {
	}

	private final Map<BleedKey, Bleed> bleeds = new HashMap<>();
	/** Each attacker's most recent bleed target, for the status line. */
	private final Map<UUID, BleedKey> lastTarget = new HashMap<>();

	@Override
	public WeaponType type() {
		return WeaponType.VOID_SCYTHE;
	}

	private int stacksToHemorrhage() {
		return Math.max(2, (int) setting("bleed-hits", 5));
	}

	@Override
	public void melee(Player player, LivingEntity target, double damage) {
		addBleed(player, target);
	}

	/** Adds a bleed stack; at the threshold the target hemorrhages (on the next tick, outside any damage event). */
	private void addBleed(Player player, LivingEntity target) {
		BleedKey key = new BleedKey(player.getUniqueId(), target.getUniqueId());
		long now = ServerClock.now();
		Bleed previous = bleeds.get(key);
		int stacks = previous != null && now <= previous.expiresAt() ? previous.stacks() + 1 : 1;
		int needed = stacksToHemorrhage();
		if (stacks >= needed) {
			bleeds.remove(key);
			Hud.flash(player, Component.text("☠ HEMORRHAGE", NamedTextColor.RED));
			TickScheduler.schedule(0, () -> hemorrhage(player, target));
			return;
		}
		long nextTick = previous != null && now <= previous.expiresAt() ? previous.nextTick() : now + bleedInterval();
		bleeds.put(key, new Bleed(stacks, now + STACK_TIMEOUT_TICKS, target, nextTick));
		lastTarget.put(player.getUniqueId(), key);
		Location chest = BloodFx.chest(target);
		BloodFx.burst(chest, BloodFx.BLOOD_FADE, 8 + stacks * 2, 0.25);
		BloodFx.burst(chest, BloodFx.DRIP, stacks, 0.25, 0.0);
		Hud.flash(player, bleedLine(stacks, needed));
	}

	private void hemorrhage(Player player, LivingEntity target) {
		if (!target.isValid() || !player.isOnline()) {
			return;
		}
		double radius = setting("radius", 4.0);
		Location center = target.getLocation();
		Location chest = BloodFx.chest(target);
		BloodFx.burst(chest, BloodFx.BLOOD_LARGE, 70, 1.0);
		BloodFx.splash(chest, 12);
		BloodFx.spray(chest, radius, 16, 8);
		BloodFx.ring(center.clone().add(0.0, 0.1, 0.0), BloodFx.CLOT, radius, 32);
		Shapes.shockwave(center.clone().add(0.0, 0.1, 0.0), BloodFx.BLOOD_FADE, radius, 8);
		Shapes.column(center, BloodFx.BLOOD_LARGE, 3.0, 14, 0.2);
		BloodFx.burst(chest, BloodFx.NOVA, 1, 0.0);
		BloodFx.play(center, BloodFx.FANGS, 0.9F, 0.7F);
		BloodFx.play(center, BloodFx.SQUELCH, 1.0F, 0.5F);

		double splash = setting("splash-damage", 6.0);
		for (LivingEntity nearby : Targeting.livingInRadius(center, radius, player)) {
			if (nearby != target) {
				Targeting.pullTowards(nearby, center, 0.8);
				Damage.deal(nearby, splash, player, type(), center);
			}
		}
		Damage.deal(target, setting("damage", 9.0), player, type());
	}

	/** Harvest: a blood arc in front of you. */
	@Override
	public void use(Player player) {
		if (!Cooldowns.checkReady(player, ability())) {
			return;
		}
		Cooldowns.start(player, ability());
		Location eye = player.getEyeLocation();
		Vector facing = eye.getDirection().setY(0.0);
		if (facing.lengthSquared() < 1.0E-4) {
			facing = new Vector(0, 0, 1);
		}
		facing.normalize();
		Location chest = BloodFx.chest(player);
		// The arc: a full crescent over the 120° it cuts, a sweep flash at its middle.
		double yaw = Math.atan2(facing.getZ(), facing.getX());
		Shapes.crescent(chest.clone().add(0.0, 0.1, 0.0), 2.4, yaw, Math.toRadians(60), 0.9);
		BloodFx.burst(chest.clone().add(facing.clone().multiply(1.8)), BloodFx.SWEEP, 1, 0.0);
		BloodFx.play(player, BloodFx.HARVEST, 1.0F, 0.6F);
		BloodFx.play(player, BloodFx.SQUELCH, 0.6F, 0.8F);

		double damage = setting("reap-damage", 4.0);
		for (LivingEntity target : Targeting.livingInRadius(player.getLocation(), REAP_RANGE, player)) {
			Vector to = target.getLocation().toVector().subtract(player.getLocation().toVector()).setY(0.0);
			if (to.lengthSquared() > 1.0E-4 && to.normalize().dot(facing) < REAP_HALF_ANGLE_COS) {
				continue;
			}
			if (!Targeting.hasLineOfSight(eye, BloodFx.chest(target))) {
				continue;
			}
			Damage.deal(target, damage, player, type(), player.getLocation());
			BloodFx.splash(BloodFx.chest(target), 3);
			if (target.isValid() && !target.isDead()) {
				addBleed(player, target);
			}
		}
	}

	private static Component bleedLine(int stacks, int needed) {
		return Component.text("Bleed  ", NamedTextColor.DARK_RED)
			.append(Component.text("⬤".repeat(stacks), NamedTextColor.RED))
			.append(Component.text("⬤".repeat(Math.max(0, needed - stacks)), NamedTextColor.DARK_GRAY))
			.append(Component.text("  " + stacks + "/" + needed, NamedTextColor.GRAY));
	}

	@Override
	public Component hud(Player player) {
		BleedKey key = lastTarget.get(player.getUniqueId());
		Bleed bleed = key == null ? null : bleeds.get(key);
		long now = ServerClock.now();
		Component reap = Component.text("   ").append(Hud.cooldownBar(player, ability()));
		if (bleed != null && now <= bleed.expiresAt()) {
			return bleedLine(bleed.stacks(), stacksToHemorrhage())
				.append(Component.text(" · fades" + Hud.seconds(bleed.expiresAt() - now), NamedTextColor.GRAY))
				.append(reap);
		}
		return Component.text("Bleed  ", NamedTextColor.DARK_RED)
			.append(Component.text("hit " + stacksToHemorrhage() + " times to hemorrhage", NamedTextColor.GRAY))
			.append(reap);
	}

	@Override
	public BloodFx.Fx auraAccent() {
		return BloodFx.SOUL;
	}

	/** Bleeding targets visibly drip, more the more stacks they carry, and take damage over time. */
	@Override
	public void tick(long now) {
		if (bleeds.isEmpty()) {
			return;
		}
		bleedDamage(now);
		if (now % 8 != 0) {
			return;
		}
		int nearDeath = stacksToHemorrhage() - 1;
		for (Bleed bleed : bleeds.values()) {
			LivingEntity target = bleed.target();
			if (now <= bleed.expiresAt() && target.isValid() && !target.isDead()) {
				Location chest = BloodFx.chest(target);
				BloodFx.burst(chest, BloodFx.DRIP, bleed.stacks(), 0.25, 0.0);
				Shapes.orbit(chest, bleed.stacks(), 0.6, now * 0.25);
				if (bleed.stacks() >= nearDeath) {
					BloodFx.burst(chest, BloodFx.BLOOD_FADE, 3, 0.3);
				}
			}
		}
	}

	private int bleedInterval() {
		return Math.max(10, ticksSetting("bleed-interval", 30));
	}

	private void bleedDamage(long now) {
		double perStack = setting("bleed-damage", 0.5);
		if (perStack <= 0.0) {
			return;
		}
		// Collect first: the damage can kill, and death handlers must not see a map mid-iteration.
		List<Map.Entry<BleedKey, Bleed>> due = null;
		for (Map.Entry<BleedKey, Bleed> entry : bleeds.entrySet()) {
			Bleed bleed = entry.getValue();
			if (now >= bleed.nextTick() && now <= bleed.expiresAt() && bleed.target().isValid() && !bleed.target().isDead()) {
				if (due == null) {
					due = new ArrayList<>();
				}
				due.add(entry);
			}
		}
		if (due == null) {
			return;
		}
		int interval = bleedInterval();
		for (Map.Entry<BleedKey, Bleed> entry : due) {
			Bleed bleed = entry.getValue();
			bleeds.replace(entry.getKey(), bleed, new Bleed(bleed.stacks(), bleed.expiresAt(), bleed.target(), now + interval));
		}
		for (Map.Entry<BleedKey, Bleed> entry : due) {
			Player attacker = Bukkit.getPlayer(entry.getKey().attacker());
			LivingEntity target = entry.getValue().target();
			if (attacker == null || !Targeting.validTarget(attacker, target)) {
				continue;
			}
			Damage.deal(target, perStack * entry.getValue().stacks(), attacker, type(), null, false);
			BloodFx.burst(BloodFx.chest(target), BloodFx.SPLATTER, 3, 0.2, 0.05);
		}
	}

	@Override
	public void prune() {
		long now = ServerClock.now();
		bleeds.values().removeIf(bleed -> bleed.expiresAt() < now || !bleed.target().isValid());
		lastTarget.values().removeIf(key -> !bleeds.containsKey(key));
	}

	@Override
	public void shutdown() {
		bleeds.clear();
		lastTarget.clear();
	}
}
