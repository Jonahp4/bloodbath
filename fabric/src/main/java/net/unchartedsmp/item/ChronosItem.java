package net.unchartedsmp.item;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;
import net.unchartedsmp.ability.Ability;
import net.unchartedsmp.ability.Cooldowns;
import net.unchartedsmp.ability.ServerClock;
import net.unchartedsmp.ability.TickScheduler;
import net.unchartedsmp.fx.BloodFx;
import net.unchartedsmp.hud.Hud;
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
		Hud.flash(player, Text.literal("Blood-mark set. Use again within 8s to recall.").formatted(Formatting.RED));

		// A slow pulse over the mark, like a clock hand dripping.
		Vec3d floor = pos.add(0.0, 0.1, 0.0);
		TickScheduler.repeat(10, 10, MARK_WINDOW_TICKS / 10, tick -> {
			if (MARKS.get(player.getUuid()) != mark) {
				return false;
			}
			// A clock of blood on the ground: the ring drains as the window runs out.
			double fraction = 1.0 - (tick + 1) / (double) (MARK_WINDOW_TICKS / 10);
			BloodFx.ring(world, BloodFx.BLOOD_FADE, floor, 0.7, Math.max(3, (int) Math.round(16 * fraction)));
			BloodFx.line(world, BloodFx.BLOOD, floor, floor.add(0.0, 2.2, 0.0), 3.0);
			if (tick % 2 == 1) {
				BloodFx.play(world, pos, BloodFx.CLOCK_TICK, 0.5F, 0.8F + tick * 0.05F);
			}
			return true;
		});
	}

	private static void recall(ServerWorld world, ServerPlayerEntity player, Mark mark) {
		Vec3d target = mark.pos();
		// Someone may have built over the mark since - don't clip the player into blocks.
		if (!Targeting.fitsAt(world, player, target)) {
			BloodFx.splash(world, target.add(0.0, 1.0, 0.0), 4);
			Hud.flash(player, Text.literal("Your blood-mark was sealed in stone.").formatted(Formatting.DARK_RED));
			return;
		}
		Vec3d departure = Targeting.chest(player);
		BloodFx.burst(world, BloodFx.BLOOD_LARGE, departure, 30, 0.5);
		BloodFx.flow(world, departure, target.add(0.0, 1.0, 0.0), 10, 0.4, BloodFx.BRIGHT_RED, 10);
		player.teleport(world, target.x, target.y, target.z, Set.of(), mark.yaw(), mark.pitch(), false);
		BloodFx.burst(world, BloodFx.BLOOD_LARGE, target.add(0.0, 1.0, 0.0), 30, 0.5);
		BloodFx.play(world, target, BloodFx.CLOCK_RECALL, 1.0F, 1.5F);
		Cooldowns.start(player, Ability.CHRONOS);
	}

	@Override
	public Text hudStatus(ServerPlayerEntity player) {
		Mark mark = MARKS.get(player.getUuid());
		long left = mark == null ? 0 : mark.expiresAt() - ServerClock.now();
		if (mark != null && left > 0 && mark.world() == player.getEntityWorld()) {
			MutableText line = Hud.timer("\u29D6 Blood-mark", left);
			return line.append(Text.literal("  use again to recall").formatted(Formatting.GRAY));
		}
		return Hud.cooldownBar(player, ability);
	}

	@Override
	public ParticleEffect auraAccent() {
		return BloodFx.BLOOD_FADE;
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
