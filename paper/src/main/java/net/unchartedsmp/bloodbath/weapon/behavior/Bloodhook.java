package net.unchartedsmp.bloodbath.weapon.behavior;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.unchartedsmp.bloodbath.ability.Cooldowns;
import net.unchartedsmp.bloodbath.ability.ServerClock;
import net.unchartedsmp.bloodbath.ability.TickScheduler;
import net.unchartedsmp.bloodbath.fx.BloodFx;
import net.unchartedsmp.bloodbath.hud.Hud;
import net.unchartedsmp.bloodbath.util.Targeting;
import net.unchartedsmp.bloodbath.weapon.WeaponBehavior;
import net.unchartedsmp.bloodbath.weapon.WeaponType;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/**
 * Bloodhook: hurls a chain of blood at whatever you're looking at and reels you in. Hooking the
 * same target again within 12s extends the range (10 → 28 blocks by default). The chain can't
 * pass through walls.
 */
public final class Bloodhook implements WeaponBehavior {
	private static final int STREAK_TIMEOUT_TICKS = 240;
	private static final int CHAIN_TICKS = 8;

	private record Streak(UUID targetId, int stacks, long expiresAt) {
	}

	private final Map<UUID, Streak> streaks = new HashMap<>();

	@Override
	public WeaponType type() {
		return WeaponType.BLOODHOOK;
	}

	@Override
	public void use(Player player) {
		if (!Cooldowns.checkReady(player, ability())) {
			return;
		}
		Cooldowns.start(player, ability());
		double range = currentRange(player);
		LivingEntity target = Targeting.lookEntity(player, range);
		if (target == null) {
			BloodFx.play(player, BloodFx.WET_SLIDE, 0.8F, 1.4F);
			BloodFx.flow(player.getEyeLocation(), Targeting.lookTarget(player, range), 4, 0.05, BloodFx.BLOOD_RED, 6);
			Hud.flash(player, Component.text("The hook found no blood.", NamedTextColor.GRAY));
			return;
		}
		registerHit(player, target);
		drawChainAndPull(player, target);
	}

	private double rangeFor(int stacks) {
		return Math.min(setting("max-range", 28.0), setting("range", 10.0) + stacks * setting("range-per-hook", 3.0));
	}

	private double currentRange(Player player) {
		Streak streak = streaks.get(player.getUniqueId());
		return streak != null && ServerClock.now() <= streak.expiresAt() ? rangeFor(streak.stacks()) : setting("range", 10.0);
	}

	private void registerHit(Player player, LivingEntity target) {
		Streak previous = streaks.get(player.getUniqueId());
		long now = ServerClock.now();
		int stacks = previous != null && previous.targetId().equals(target.getUniqueId()) && now <= previous.expiresAt()
			? previous.stacks() + 1
			: 1;
		streaks.put(player.getUniqueId(), new Streak(target.getUniqueId(), stacks, now + STREAK_TIMEOUT_TICKS));
		if (stacks > 1) {
			Hud.flash(player, Component.text("Bloodhook range: " + Math.round(rangeFor(stacks)) + " blocks", NamedTextColor.RED));
		}
	}

	private void drawChainAndPull(Player player, LivingEntity target) {
		World world = player.getWorld();
		BloodFx.play(player, BloodFx.CHAIN, 1.0F, 0.8F);
		TickScheduler.repeat(0, 1, CHAIN_TICKS, tick -> {
			if (!Targeting.stillIn(player, world) || !Targeting.stillIn(target, world)) {
				return false;
			}
			BloodFx.line(player.getEyeLocation(), BloodFx.chest(target), BloodFx.BLOOD_FADE, 2.0);
			if (tick % 2 == 0) {
				BloodFx.flow(BloodFx.chest(target), player.getEyeLocation(), 3, 0.2, BloodFx.BRIGHT_RED, 6);
			}
			return true;
		});
		TickScheduler.schedule(CHAIN_TICKS, () -> {
			if (!Targeting.stillIn(player, world) || !Targeting.stillIn(target, world)) {
				return;
			}
			Location from = player.getLocation();
			Vector toTarget = target.getLocation().toVector().subtract(from.toVector());
			double distance = toTarget.length();
			if (distance > 1.0E-2) {
				// Scaled so you land about at the target: ~11 blocks of air travel per unit of speed.
				double speed = Math.max(0.6, Math.min(2.3, 0.075 * distance + 0.25));
				double lift = 0.3 + Math.max(0.0, toTarget.getY()) * 0.06;
				Targeting.addVelocity(player, toTarget.multiply(speed / distance).setY(0.0).add(new Vector(0.0, lift, 0.0)));
				player.setFallDistance(0.0F);
			}
			BloodFx.play(target, BloodFx.CHAIN_SNAP, 0.6F, 1.6F);
			BloodFx.play(target, BloodFx.SQUELCH, 0.8F, 0.7F);
			BloodFx.splash(BloodFx.chest(target), 6);
		});
	}

	@Override
	public Component hud(Player player) {
		Component line = Hud.cooldownBar(player, ability());
		Streak streak = streaks.get(player.getUniqueId());
		if (streak != null && ServerClock.now() <= streak.expiresAt() && streak.stacks() > 0) {
			line = line.append(Component.text("  range " + Math.round(rangeFor(streak.stacks())) + "m", NamedTextColor.RED));
		}
		return line;
	}

	@Override
	public BloodFx.Fx auraAccent() {
		return BloodFx.BLOOD_FADE;
	}

	@Override
	public void forget(UUID playerId) {
		streaks.remove(playerId);
	}

	@Override
	public void prune() {
		long now = ServerClock.now();
		streaks.values().removeIf(streak -> streak.expiresAt() < now);
	}

	@Override
	public void shutdown() {
		streaks.clear();
	}
}
