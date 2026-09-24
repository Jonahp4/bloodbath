package net.unchartedsmp.item;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;
import net.unchartedsmp.ability.Ability;
import net.unchartedsmp.ability.Cooldowns;
import net.unchartedsmp.ability.ServerClock;
import net.unchartedsmp.ability.TickScheduler;
import net.unchartedsmp.fx.BloodFx;
import net.unchartedsmp.util.Targeting;

/**
 * Bleeding Chronos: leave a blood-mark where you stand, then use again within 8s to snap back
 * to it (position and facing). The cooldown starts on recall.
 */
public class ChronosItem extends AbilityWeapon {
	private static final int MARK_WINDOW_TICKS = 160;

	private record Mark(ServerWorld world, Vec3d pos, float yaw, float pitch, long expiresAt) {
	}

	private static final Map<UUID, Mark> MARKS = new HashMap<>();

	public ChronosItem(Settings settings) {
		super(settings, Ability.CHRONOS);
	}

	@Override
	protected ActionResult useAbility(ServerWorld world, ServerPlayerEntity player) {
		Mark mark = MARKS.remove(player.getUuid());
		if (mark != null && mark.world() == world && ServerClock.now() <= mark.expiresAt()) {
			recall(world, player, mark);
			return ActionResult.SUCCESS;
		}
		if (!Cooldowns.checkReady(player, ability)) {
			return ActionResult.FAIL;
		}
		placeMark(world, player);
		return ActionResult.SUCCESS;
	}

	private static void placeMark(ServerWorld world, ServerPlayerEntity player) {
		Vec3d pos = player.getEntityPos();
		Mark mark = new Mark(world, pos, player.getYaw(), player.getPitch(), ServerClock.now() + MARK_WINDOW_TICKS);
		MARKS.put(player.getUuid(), mark);
		BloodFx.burst(world, BloodFx.BLOOD, pos.add(0.0, 1.0, 0.0), 20, 0.35);
		BloodFx.play(world, pos, BloodFx.CLOCK_MARK, 0.8F, 1.8F);
		player.sendMessage(Text.literal("Blood-mark set. Use again within 8s to recall.").formatted(Formatting.RED), true);

		// A slow pulse over the mark, like a clock hand dripping.
		Vec3d floor = pos.add(0.0, 0.1, 0.0);
		TickScheduler.repeat(20, 20, MARK_WINDOW_TICKS / 20, tick -> {
			if (MARKS.get(player.getUuid()) != mark) {
				return false;
			}
			BloodFx.ring(world, BloodFx.BLOOD, floor, 0.6, 12);
			BloodFx.play(world, pos, BloodFx.HEARTBEAT, 0.4F, 1.4F);
			return true;
		});
	}

	private static void recall(ServerWorld world, ServerPlayerEntity player, Mark mark) {
		Vec3d target = mark.pos();
		// Someone may have built over the mark since - don't clip the player into blocks.
		if (!Targeting.fitsAt(world, player, target)) {
			BloodFx.splash(world, target.add(0.0, 1.0, 0.0), 4);
			player.sendMessage(Text.literal("Your blood-mark was sealed in stone.").formatted(Formatting.DARK_RED), true);
			return;
		}
		BloodFx.burst(world, BloodFx.BLOOD_LARGE, Targeting.chest(player), 30, 0.5);
		player.teleport(world, target.x, target.y, target.z, Set.of(), mark.yaw(), mark.pitch(), false);
		BloodFx.burst(world, BloodFx.BLOOD_LARGE, target.add(0.0, 1.0, 0.0), 30, 0.5);
		BloodFx.play(world, target, BloodFx.CLOCK_RECALL, 1.0F, 1.5F);
		Cooldowns.start(player, Ability.CHRONOS);
	}

	public static void forget(UUID playerId) {
		MARKS.remove(playerId);
	}

	public static void prune() {
		long now = ServerClock.now();
		MARKS.values().removeIf(mark -> mark.expiresAt() < now);
	}

	public static void clearAll() {
		MARKS.clear();
	}
}
