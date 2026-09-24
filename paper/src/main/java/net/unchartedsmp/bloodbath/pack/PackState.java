package net.unchartedsmp.bloodbath.pack;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.unchartedsmp.bloodbath.config.Settings;
import org.bukkit.entity.Player;

/**
 * Which players have the Bloodbath resource pack loaded right now. Custom sprites, HUD icons and
 * the armory background are only sent to them; everyone else gets the vanilla-safe version, so
 * nobody ever sees a missing texture or an egg-shell particle where blood should be.
 */
public final class PackState {
	private static final Set<UUID> LOADED = new HashSet<>();

	private PackState() {
	}

	public static boolean hasPack(Player player) {
		return LOADED.contains(player.getUniqueId()) && Settings.get().packVisuals;
	}

	public static void set(UUID player, boolean loaded) {
		if (loaded) {
			LOADED.add(player);
		} else {
			LOADED.remove(player);
		}
	}

	public static void forget(UUID player) {
		LOADED.remove(player);
	}

	public static void clearAll() {
		LOADED.clear();
	}
}
