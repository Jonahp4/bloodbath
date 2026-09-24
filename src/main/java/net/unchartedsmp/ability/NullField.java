package net.unchartedsmp.ability;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Clotblade (Nullblade) suppression: direct on-hit debuffs and placed clot fields.
 *
 * <p>Like cooldowns, debuffs survive a disconnect so a nullified player can't relog to clear it.
 */
public final class NullField {
	private record Zone(ServerWorld world, Vec3d center, double radiusSq, long expiresAt) {
	}

	private static final Map<UUID, Long> DEBUFFS = new HashMap<>();
	private static final List<Zone> ZONES = new ArrayList<>();

	private NullField() {
	}

	public static void debuff(PlayerEntity target, int durationTicks) {
		DEBUFFS.merge(target.getUuid(), ServerClock.now() + durationTicks, Math::max);
	}

	public static void createZone(ServerWorld world, Vec3d center, double radius, int durationTicks) {
		ZONES.add(new Zone(world, center, radius * radius, ServerClock.now() + durationTicks));
	}

	public static boolean isNullified(PlayerEntity player) {
		long now = ServerClock.now();
		Long debuffUntil = DEBUFFS.get(player.getUuid());
		if (debuffUntil != null && now <= debuffUntil) {
			return true;
		}
		if (ZONES.isEmpty()) {
			return false;
		}
		World world = player.getEntityWorld();
		Vec3d pos = player.getEntityPos();
		for (Zone zone : ZONES) {
			if (now <= zone.expiresAt() && zone.world() == world && pos.squaredDistanceTo(zone.center()) <= zone.radiusSq()) {
				return true;
			}
		}
		return false;
	}

	public static void notifyNullified(PlayerEntity player) {
		player.sendMessage(Text.literal("Your blood has clotted. Abilities suppressed!").formatted(Formatting.DARK_RED), true);
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
