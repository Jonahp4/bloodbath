package net.unchartedsmp.item;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
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
 * Bloodhook: hurls a chain of blood at the player you're looking at and reels you in.
 * Hooking the same player again within 12s extends the range (10 → 28 blocks).
 */
public class BloodhookItem extends AbilityWeapon {
	private static final double BASE_RANGE = 10.0;
	private static final double RANGE_PER_STACK = 3.0;
	private static final double MAX_RANGE = 28.0;
	private static final int STREAK_TIMEOUT_TICKS = 240;
	private static final double PULL_STRENGTH = 0.55;
	private static final int CHAIN_TICKS = 8;

	private record HookStreak(UUID targetId, int stacks, long expiresAt) {
	}

	private static final Map<UUID, HookStreak> STREAKS = new HashMap<>();

	public BloodhookItem(Settings settings) {
		super(settings, Ability.BLOODHOOK);
	}

	@Override
	protected ActionResult useAbility(ServerWorld world, ServerPlayerEntity player) {
		if (!Cooldowns.checkReady(player, ability)) {
			return ActionResult.FAIL;
		}
		Cooldowns.start(player, ability);

		ServerPlayerEntity target = findTarget(world, player, currentRange(player));
		if (target == null) {
			BloodFx.play(world, player, BloodFx.WET_SLIDE, 0.8F, 1.4F);
			BloodFx.flow(world, player.getEyePos(), Targeting.lookTarget(world, player, currentRange(player)), 4, 0.05, BloodFx.BLOOD_RED, 6);
			Hud.flash(player, Text.literal("The hook found no blood.").formatted(Formatting.GRAY));
			return ActionResult.SUCCESS;
		}
		registerHit(player, target);
		drawChainAndPull(world, player, target);
		return ActionResult.SUCCESS;
	}

	private static double rangeFor(int stacks) {
		return Math.min(MAX_RANGE, BASE_RANGE + stacks * RANGE_PER_STACK);
	}

	private static double currentRange(ServerPlayerEntity player) {
		HookStreak streak = STREAKS.get(player.getUuid());
		return streak != null && ServerClock.now() <= streak.expiresAt() ? rangeFor(streak.stacks()) : BASE_RANGE;
	}

	private static void registerHit(ServerPlayerEntity player, ServerPlayerEntity target) {
		HookStreak previous = STREAKS.get(player.getUuid());
		long now = ServerClock.now();
		int stacks = previous != null && previous.targetId().equals(target.getUuid()) && now <= previous.expiresAt()
			? previous.stacks() + 1
			: 1;
		STREAKS.put(player.getUuid(), new HookStreak(target.getUuid(), stacks, now + STREAK_TIMEOUT_TICKS));
		if (stacks > 1) {
			Hud.flash(player, Text.literal("Bloodhook range: " + Math.round(rangeFor(stacks)) + " blocks").formatted(Formatting.RED));
		}
	}

	/**
	 * Closest player whose (slightly padded) hitbox the look ray passes through, <b>with line of
	 * sight</b>. The old version ignored walls, so you could hook players through your base.
	 */
	private static ServerPlayerEntity findTarget(ServerWorld world, ServerPlayerEntity player, double range) {
		Vec3d start = player.getEyePos();
		Vec3d end = start.add(player.getRotationVec(1.0F).multiply(range));
		ServerPlayerEntity closest = null;
		double closestDistance = range;
		for (ServerPlayerEntity candidate : world.getPlayers()) {
			if (candidate == player || !candidate.isAlive() || candidate.isSpectator()) {
				continue;
			}
			Optional<Vec3d> hit = candidate.getBoundingBox().expand(0.35).raycast(start, end);
			if (hit.isPresent()) {
				double distance = start.distanceTo(hit.get());
				if (distance < closestDistance && Targeting.hasLineOfSight(world, start, hit.get(), player)) {
					closestDistance = distance;
					closest = candidate;
				}
			}
		}
		return closest;
	}

	private static void drawChainAndPull(ServerWorld world, ServerPlayerEntity player, ServerPlayerEntity target) {
		BloodFx.play(world, player, BloodFx.CHAIN, 1.0F, 0.8F);
		TickScheduler.repeat(0, 1, CHAIN_TICKS, tick -> {
			if (!Targeting.stillIn(player, world) || !Targeting.stillIn(target, world)) {
				return false;
			}
			BloodFx.line(world, BloodFx.BLOOD_FADE, player.getEyePos(), Targeting.chest(target), 2.0);
			if (tick % 2 == 0) {
				BloodFx.flow(world, Targeting.chest(target), player.getEyePos(), 3, 0.2, BloodFx.BRIGHT_RED, 6);
			}
			return true;
		});
		TickScheduler.schedule(CHAIN_TICKS, () -> {
			if (!Targeting.stillIn(player, world) || !Targeting.stillIn(target, world)) {
				return;
			}
			Vec3d toTarget = target.getEntityPos().subtract(player.getEntityPos());
			if (toTarget.lengthSquared() > 1.0E-4) {
				Targeting.addVelocity(player, toTarget.normalize().multiply(PULL_STRENGTH).add(0.0, 0.15, 0.0));
			}
			BloodFx.play(world, target, BloodFx.CHAIN_SNAP, 0.6F, 1.6F);
			BloodFx.play(world, target, BloodFx.SQUELCH, 0.8F, 0.7F);
			BloodFx.splash(world, Targeting.chest(target), 6);
		});
	}

	@Override
	public Text hudStatus(ServerPlayerEntity player) {
		MutableText line = Hud.cooldownBar(player, ability);
		HookStreak streak = STREAKS.get(player.getUuid());
		if (streak != null && ServerClock.now() <= streak.expiresAt() && streak.stacks() > 0) {
			line.append(Text.literal("  range " + Math.round(rangeFor(streak.stacks())) + "m").formatted(Formatting.RED));
		}
		return line;
	}

	@Override
	public ParticleEffect auraAccent() {
		return BloodFx.BLOOD_FADE;
	}

	public static void forget(UUID playerId) {
		STREAKS.remove(playerId);
	}

	public static void prune() {
		long now = ServerClock.now();
		STREAKS.values().removeIf(streak -> streak.expiresAt() < now);
	}

	public static void clearAll() {
		STREAKS.clear();
	}
}
