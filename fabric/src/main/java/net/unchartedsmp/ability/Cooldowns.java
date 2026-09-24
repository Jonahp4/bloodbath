package net.unchartedsmp.ability;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.unchartedsmp.hud.Hud;

/**
 * Per-player ability cooldowns.
 *
 * <p>Entries are deliberately <b>not</b> dropped when a player disconnects: doing so would let
 * players reset every cooldown by relogging. Fully-expired entries are pruned periodically instead.
 *
 * <p>A slot holds the tick the ability becomes ready, or 0 once that moment has been announced
 * to the player (see {@link #drainReady}).
 */
public final class Cooldowns {
	private static final Map<UUID, long[]> READY_AT = new HashMap<>();

	private Cooldowns() {
	}

	public static boolean isReady(PlayerEntity player, Ability ability) {
		long[] readyAt = READY_AT.get(player.getUuid());
		return readyAt == null || ServerClock.now() >= readyAt[ability.ordinal()];
	}

	public static long remainingTicks(PlayerEntity player, Ability ability) {
		long[] readyAt = READY_AT.get(player.getUuid());
		return readyAt == null ? 0L : Math.max(0L, readyAt[ability.ordinal()] - ServerClock.now());
	}

	/** 0 = just started, 1 = ready. */
	public static float progress(PlayerEntity player, Ability ability) {
		return 1.0F - remainingTicks(player, ability) / (float) ability.cooldownTicks();
	}

	public static void start(PlayerEntity player, Ability ability) {
		READY_AT.computeIfAbsent(player.getUuid(), id -> new long[Ability.COUNT])[ability.ordinal()] =
			ServerClock.now() + ability.cooldownTicks();
	}

	/** Returns true when the ability is ready; otherwise tells the player how long is left. */
	public static boolean checkReady(PlayerEntity player, Ability ability) {
		if (isReady(player, ability)) {
			return true;
		}
		double seconds = remainingTicks(player, ability) / 20.0;
		Hud.flash(player, Text.literal(String.format(Locale.ROOT, "%s is still clotting: %.1fs", ability.displayName(), seconds))
			.formatted(Formatting.DARK_RED));
		return false;
	}

	/** Abilities whose cooldown has just run out for this player; each is reported once. */
	public static List<Ability> drainReady(PlayerEntity player) {
		long[] readyAt = READY_AT.get(player.getUuid());
		if (readyAt == null) {
			return List.of();
		}
		long now = ServerClock.now();
		List<Ability> ready = null;
		for (int i = 0; i < readyAt.length; i++) {
			if (readyAt[i] != 0L && readyAt[i] <= now) {
				readyAt[i] = 0L;
				if (ready == null) {
					ready = new ArrayList<>(2);
				}
				ready.add(Ability.values()[i]);
			}
		}
		return ready == null ? List.of() : ready;
	}

	public static void prune() {
		long now = ServerClock.now();
		READY_AT.values().removeIf(readyAt -> {
			for (long tick : readyAt) {
				if (tick > now) {
					return false;
				}
			}
			return true;
		});
	}

	public static void clearAll() {
		READY_AT.clear();
	}
}
