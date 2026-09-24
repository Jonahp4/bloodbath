package net.unchartedsmp.bloodbath.pack;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.unchartedsmp.bloodbath.Keys;
import net.unchartedsmp.bloodbath.config.Settings;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

/**
 * Which players have the Bloodbath resource pack loaded right now. Custom sprites, the boss model,
 * HUD icons and the armory background are only sent to them; everyone else gets the vanilla-safe
 * version, so nobody ever sees a missing texture where blood should be.
 *
 * <p>{@code effects.pack-visuals}:
 * <ul>
 * <li>{@code auto}: a player has the pack once their game reports loading Bloodbath's pack. When
 * Bloodbath isn't sending its own pack (it's merged into the server's pack instead), any server
 * pack they load counts, and so does the pack from server.properties unless they refused it.</li>
 * <li>{@code always}: everyone is treated as having it (the pack is forced some other way, or
 * installed on every client by hand).</li>
 * <li>{@code off}: nobody gets the pack-only visuals.</li>
 * </ul>
 *
 * <p>The state is also written on the player (and cleared when they join), so a plugin reload
 * with players online doesn't forget who has the pack: their game won't report it again.
 */
public final class PackState {
	private static final Set<UUID> LOADED = new HashSet<>();
	private static final Set<UUID> REFUSED = new HashSet<>();
	/** Stored in place of a hash when the pack came from somewhere else. */
	private static final String EXTERNAL = "external";

	private PackState() {
	}

	public static boolean hasPack(Player player) {
		return switch (Settings.get().packVisuals) {
			case "off" -> false;
			case "always" -> true;
			default -> LOADED.contains(player.getUniqueId())
				|| serverPackCarriesBloodbath() && !REFUSED.contains(player.getUniqueId());
		};
	}

	/** Bloodbath isn't sending its own pack, but server.properties sends one: it's been merged in. */
	private static boolean serverPackCarriesBloodbath() {
		return !Settings.get().packEnabled && Bukkit.getServer().getServerResourcePack() != null;
	}

	/**
	 * A pack status from the player's game. {@code ours}: it's about Bloodbath's own pack, whose
	 * current hash is {@code hash}.
	 */
	public static void status(Player player, boolean ours, boolean loaded, boolean refused, String hash) {
		if (!ours && Settings.get().packEnabled) {
			return; // another plugin's pack; ours reports for itself
		}
		UUID id = player.getUniqueId();
		if (loaded) {
			LOADED.add(id);
			REFUSED.remove(id);
			player.getPersistentDataContainer().set(Keys.PACK, PersistentDataType.STRING, ours ? hash : EXTERNAL);
		} else if (refused) {
			LOADED.remove(id);
			REFUSED.add(id);
			player.getPersistentDataContainer().remove(Keys.PACK);
		}
	}

	/** A fresh connection: the game reports its packs again, so start from nothing. */
	public static void joined(Player player) {
		forget(player);
	}

	/**
	 * The plugin was (re)enabled with this player online: pick up what we knew before. Returns
	 * false when their game has an older Bloodbath pack than {@code hash} (the plugin was updated),
	 * so it should be sent again.
	 */
	public static boolean restore(Player player, String hash) {
		String loaded = player.getPersistentDataContainer().get(Keys.PACK, PersistentDataType.STRING);
		if (loaded == null) {
			return true;
		}
		if (!loaded.equals(EXTERNAL) && !loaded.equals(hash)) {
			player.getPersistentDataContainer().remove(Keys.PACK);
			return false;
		}
		LOADED.add(player.getUniqueId());
		return true;
	}

	public static void forget(Player player) {
		LOADED.remove(player.getUniqueId());
		REFUSED.remove(player.getUniqueId());
		player.getPersistentDataContainer().remove(Keys.PACK);
	}

	/** How many of the online players get the pack visuals. */
	public static long count() {
		return Bukkit.getOnlinePlayers().stream().filter(PackState::hasPack).count();
	}

	public static void clearAll() {
		LOADED.clear();
		REFUSED.clear();
	}
}
