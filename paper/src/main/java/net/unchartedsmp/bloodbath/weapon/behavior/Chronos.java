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
import org.bukkit.entity.Player;

/**
 * Bleeding Chronos: leave a blood-mark where you stand, then use again within 8s to snap back
 * to it (position and facing). The cooldown starts on recall.
 */
public final class Chronos implements WeaponBehavior {
	private record Mark(Location pos, long expiresAt) {
	}

	private final Map<UUID, Mark> marks = new HashMap<>();

	@Override
	public WeaponType type() {
		return WeaponType.CHRONOS;
	}

	@Override
	public void use(Player player) {
		Mark mark = marks.remove(player.getUniqueId());
		if (mark != null && ServerClock.now() <= mark.expiresAt() && Targeting.sameWorld(mark.pos(), player)) {
			recall(player, mark);
			return;
		}
		if (!Cooldowns.checkReady(player, ability())) {
			return;
		}
		placeMark(player);
	}

	private void placeMark(Player player) {
		int window = Math.max(10, ticksSetting("window", 160));
		Location pos = player.getLocation();
		Mark mark = new Mark(pos, ServerClock.now() + window);
		marks.put(player.getUniqueId(), mark);
		BloodFx.burst(pos.clone().add(0.0, 1.0, 0.0), BloodFx.BLOOD, 20, 0.35);
		BloodFx.play(pos, BloodFx.CLOCK_MARK, 0.8F, 1.8F);
		Hud.flash(player, Component.text("Blood-mark set. Use again within " + Math.round(window / 20.0) + "s to recall.", NamedTextColor.RED));

		// A clock of blood on the ground: the ring drains as the window runs out.
		Location floor = pos.clone().add(0.0, 0.1, 0.0);
		Location top = pos.clone().add(0.0, 2.2, 0.0);
		int pulses = window / 10;
		TickScheduler.repeat(10, 10, pulses, tick -> {
			if (marks.get(player.getUniqueId()) != mark) {
				return false;
			}
			double fraction = 1.0 - (tick + 1) / (double) pulses;
			BloodFx.ring(floor, BloodFx.BLOOD_FADE, 0.7, Math.max(3, (int) Math.round(16 * fraction)));
			BloodFx.line(floor, top, BloodFx.BLOOD, 3.0);
			if (tick % 2 == 1) {
				BloodFx.play(pos, BloodFx.CLOCK_TICK, 0.5F, 0.8F + tick * 0.05F);
			}
			return true;
		});
	}

	private void recall(Player player, Mark mark) {
		Location target = mark.pos();
		// Someone may have built over the mark since: don't clip the player into blocks.
		if (!Targeting.fitsAt(player, target)) {
			BloodFx.splash(target.clone().add(0.0, 1.0, 0.0), 4);
			Hud.flash(player, Component.text("Your blood-mark was sealed in stone.", NamedTextColor.DARK_RED));
			return;
		}
		Location departure = BloodFx.chest(player);
		BloodFx.burst(departure, BloodFx.BLOOD_LARGE, 30, 0.5);
		BloodFx.flow(departure, target.clone().add(0.0, 1.0, 0.0), 10, 0.4, BloodFx.BRIGHT_RED, 10);
		if (!Targeting.teleport(player, target, target.getYaw(), target.getPitch())) {
			return;
		}
		BloodFx.burst(target.clone().add(0.0, 1.0, 0.0), BloodFx.BLOOD_LARGE, 30, 0.5);
		BloodFx.play(target, BloodFx.CLOCK_RECALL, 1.0F, 1.5F);
		Cooldowns.start(player, ability());
	}

	@Override
	public Component hud(Player player) {
		Mark mark = marks.get(player.getUniqueId());
		long left = mark == null ? 0 : mark.expiresAt() - ServerClock.now();
		if (left > 0 && Targeting.sameWorld(mark.pos(), player)) {
			return Hud.timer("⧖ Blood-mark", left).append(Component.text("  use again to recall", NamedTextColor.GRAY));
		}
		return Hud.cooldownBar(player, ability());
	}

	@Override
	public BloodFx.Fx auraAccent() {
		return BloodFx.BLOOD_FADE;
	}

	@Override
	public void forget(UUID playerId) {
		marks.remove(playerId);
	}

	@Override
	public void prune() {
		long now = ServerClock.now();
		marks.values().removeIf(mark -> mark.expiresAt() < now);
	}

	@Override
	public void shutdown() {
		marks.clear();
	}
}
