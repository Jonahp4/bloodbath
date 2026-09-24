package net.unchartedsmp.bloodbath.ability;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.unchartedsmp.bloodbath.hud.Hud;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

/**
 * Clotblade suppression: direct on-hit debuffs and placed clot fields.
 *
 * <p>Debuffs survive a disconnect so a clotted player can't relog to clear it.
 */
public final class NullField {
	private record Zone(UUID worldId, double x, double y, double z, double radiusSq, long expiresAt) {
	}

	private static final Map<UUID, Long> DEBUFFS = new HashMap<>();
	private static final List<Zone> ZONES = new ArrayList<>();

	private NullField() {
	}

	public static void debuff(Player target, int durationTicks) {
		DEBUFFS.merge(target.getUniqueId(), ServerClock.now() + durationTicks, Math::max);
	}

	public static void createZone(Location center, double radius, int durationTicks) {
		ZONES.add(new Zone(center.getWorld().getUID(), center.getX(), center.getY(), center.getZ(), radius * radius,
			ServerClock.now() + durationTicks));
	}

	public static boolean isNullified(Player player) {
		return remainingTicks(player) > 0;
	}

	/** Ticks until this player's abilities work again (0 = not suppressed). */
	public static long remainingTicks(Player player) {
		long now = ServerClock.now();
		long remaining = 0;
		Long debuffUntil = DEBUFFS.get(player.getUniqueId());
		if (debuffUntil != null && now <= debuffUntil) {
			remaining = debuffUntil - now + 1;
		}
		if (!ZONES.isEmpty()) {
			Location pos = player.getLocation();
			World world = pos.getWorld();
			for (Zone zone : ZONES) {
				if (now > zone.expiresAt() || !zone.worldId().equals(world.getUID())) {
					continue;
				}
				double dx = pos.getX() - zone.x();
				double dy = pos.getY() - zone.y();
				double dz = pos.getZ() - zone.z();
				if (dx * dx + dy * dy + dz * dz <= zone.radiusSq()) {
					remaining = Math.max(remaining, zone.expiresAt() - now + 1);
				}
			}
		}
		return remaining;
	}

	public static void notifyNullified(Player player) {
		Hud.flash(player, Component.text("Your blood has clotted. Abilities suppressed!", NamedTextColor.DARK_RED));
	}

	/** Lifts a player's clot debuff (fields still apply while they stand in one). */
	public static void clear(UUID playerId) {
		DEBUFFS.remove(playerId);
	}

	public static void prune() {
		long now = ServerClock.now();
		DEBUFFS.values().removeIf(until -> until < now);
		ZONES.removeIf(zone -> zone.expiresAt() < now);
	}

	public static void clearAll() {
		DEBUFFS.clear();
		ZONES.clear();
	}
}
