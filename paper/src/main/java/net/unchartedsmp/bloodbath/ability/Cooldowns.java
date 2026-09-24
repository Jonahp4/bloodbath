package net.unchartedsmp.bloodbath.ability;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.unchartedsmp.bloodbath.config.Settings;
import net.unchartedsmp.bloodbath.hud.Hud;
import org.bukkit.entity.Player;

/**
 * Per-player ability cooldowns.
 *
 * <p>Entries are deliberately <b>not</b> dropped when a player leaves: doing so would let players
 * reset every cooldown by relogging. Fully-expired entries are pruned periodically instead.
 *
 * <p>A slot holds the tick the ability becomes ready, or 0 once that moment has been announced to
 * the player (see {@link #drainReady}).
 */
public final class Cooldowns {
	private record Entry(long[] readyAt, int[] length) {
	}

	private static final Map<UUID, Entry> ENTRIES = new HashMap<>();

	private Cooldowns() {
	}

	public static boolean isReady(Player player, Ability ability) {
		return remainingTicks(player, ability) <= 0;
	}

	public static long remainingTicks(Player player, Ability ability) {
		Entry entry = ENTRIES.get(player.getUniqueId());
		return entry == null ? 0L : Math.max(0L, entry.readyAt()[ability.ordinal()] - ServerClock.now());
	}

	/** 0 = just started, 1 = ready. */
	public static float progress(Player player, Ability ability) {
		Entry entry = ENTRIES.get(player.getUniqueId());
		if (entry == null || entry.length()[ability.ordinal()] <= 0) {
			return 1.0F;
		}
		return 1.0F - remainingTicks(player, ability) / (float) entry.length()[ability.ordinal()];
	}

	public static void start(Player player, Ability ability) {
		int length = Settings.get().cooldownTicks(ability);
		Entry entry = ENTRIES.computeIfAbsent(player.getUniqueId(), id -> new Entry(new long[Ability.COUNT], new int[Ability.COUNT]));
		entry.readyAt()[ability.ordinal()] = length <= 0 ? 0L : ServerClock.now() + length;
		entry.length()[ability.ordinal()] = length;
	}

	/** Returns true when the ability is ready; otherwise tells the player how long is left. */
	public static boolean checkReady(Player player, Ability ability) {
		if (isReady(player, ability)) {
			return true;
		}
		double seconds = remainingTicks(player, ability) / 20.0;
		Hud.flash(player, Component.text(String.format(Locale.ROOT, "%s is still clotting: %.1fs", ability.displayName(), seconds),
			NamedTextColor.DARK_RED));
		return false;
	}

	/** Abilities whose cooldown has just run out for this player; each is reported once. */
	public static List<Ability> drainReady(Player player) {
		Entry entry = ENTRIES.get(player.getUniqueId());
		if (entry == null) {
			return List.of();
		}
		long now = ServerClock.now();
		List<Ability> ready = null;
		long[] readyAt = entry.readyAt();
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

	public static void reset(UUID playerId) {
		ENTRIES.remove(playerId);
	}

	public static void prune() {
		long now = ServerClock.now();
		ENTRIES.values().removeIf(entry -> {
			for (long tick : entry.readyAt()) {
				if (tick > now) {
					return false;
				}
			}
			return true;
		});
	}

	public static void clearAll() {
		ENTRIES.clear();
	}
}
