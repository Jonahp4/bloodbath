package net.unchartedsmp.ability;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Per-player ability cooldowns.
 *
 * <p>Entries are deliberately <b>not</b> dropped when a player disconnects: doing so would let
 * players reset every cooldown by relogging. Fully-expired entries are pruned periodically instead.
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
		player.sendMessage(
			Text.literal(String.format(Locale.ROOT, "%s is still clotting: %.1fs", ability.displayName(), seconds))
				.formatted(Formatting.DARK_RED),
			true
		);
		return false;
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
