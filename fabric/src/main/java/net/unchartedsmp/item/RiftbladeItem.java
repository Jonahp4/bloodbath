package net.unchartedsmp.item;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.entity.LivingEntity;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.item.ToolMaterial;
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
 * Bloodrift Blade: tears a bleeding rift up to 14 blocks ahead that drags nearby enemies in.
 * Use again within 6s to step through it.
 */
public class RiftbladeItem extends AbilityWeapon {
	private static final double RIFT_MAX_DISTANCE = 14.0;
	private static final double RIFT_PULL_RADIUS = 3.5;
	private static final double RIFT_PULL_STRENGTH = 0.28;
	private static final int RIFT_LIFETIME_TICKS = 120;
	private static final int PULL_DURATION_TICKS = 10;

	private record ActiveRift(ServerWorld world, Vec3d origin, Vec3d pos, long expiresAt) {
	}

	private static final Map<UUID, ActiveRift> ACTIVE_RIFTS = new HashMap<>();

	public RiftbladeItem(Settings settings) {
		super(settings.sword(ToolMaterial.DIAMOND, 6.0F, -2.2F), Ability.RIFTBLADE);
	}

	@Override
	protected ActionResult useAbility(ServerWorld world, ServerPlayerEntity player) {
		ActiveRift rift = ACTIVE_RIFTS.remove(player.getUuid());
		if (rift != null && rift.world() == world && ServerClock.now() <= rift.expiresAt()) {
			stepThrough(world, player, rift);
			return ActionResult.SUCCESS;
		}
		if (!Cooldowns.checkReady(player, ability)) {
			return ActionResult.FAIL;
		}
		openRift(world, player);
		Cooldowns.start(player, ability);
		return ActionResult.SUCCESS;
	}

	private static void openRift(ServerWorld world, ServerPlayerEntity player) {
		Vec3d origin = player.getEyePos();
		Vec3d riftPos = Targeting.lookTarget(world, player, RIFT_MAX_DISTANCE);
		ActiveRift rift = new ActiveRift(world, origin, riftPos, ServerClock.now() + RIFT_LIFETIME_TICKS);
		ACTIVE_RIFTS.put(player.getUuid(), rift);

		BloodFx.play(world, riftPos, BloodFx.RIFT_OPEN, 1.0F, 1.3F);
		BloodFx.play(world, riftPos, BloodFx.HEARTBEAT, 1.0F, 1.0F);
		BloodFx.burst(world, BloodFx.BLOOD_LARGE, riftPos, 40, 0.4);
		BloodFx.burst(world, BloodFx.SPORE, riftPos, 10, 0.2);

		TickScheduler.repeat(0, 1, PULL_DURATION_TICKS, tick -> {
			for (LivingEntity target : Targeting.livingInRadius(world, riftPos, RIFT_PULL_RADIUS, player)) {
				Targeting.pullTowards(target, riftPos, RIFT_PULL_STRENGTH);
				if (tick % 3 == 0) {
					BloodFx.flow(world, Targeting.chest(target), riftPos, 2, 0.2, BloodFx.BRIGHT_RED, 6);
				}
			}
			if (tick % 3 == 0) {
				BloodFx.burst(world, BloodFx.CLOT, riftPos, 6, 0.5);
			}
			return true;
		});

		// The rift keeps weeping until it's used or closes.
		TickScheduler.repeat(10, 10, RIFT_LIFETIME_TICKS / 10, tick -> {
			if (ACTIVE_RIFTS.get(player.getUuid()) != rift) {
				return false;
			}
			BloodFx.ring(world, BloodFx.BLOOD_FADE, riftPos, 0.7, 12);
			BloodFx.burst(world, BloodFx.DRIP, riftPos, 2, 0.3, 0.0);
			BloodFx.gather(world, riftPos, 2.2, 3, 10);
			return true;
		});
	}

	private static void stepThrough(ServerWorld world, ServerPlayerEntity player, ActiveRift rift) {
		var landing = Targeting.safeLanding(world, player, rift.origin(), rift.pos());
		if (landing.isEmpty()) {
			BloodFx.splash(world, rift.pos(), 6);
			Hud.flash(player, Text.literal("The rift clotted shut. Nowhere to land.").formatted(Formatting.DARK_RED));
			return;
		}
		Vec3d target = landing.get();
		Vec3d departure = Targeting.chest(player);
		BloodFx.burst(world, BloodFx.BLOOD_LARGE, departure, 25, 0.4);
		BloodFx.flow(world, departure, target.add(0.0, 1.0, 0.0), 8, 0.3, BloodFx.BRIGHT_RED, 8);
		player.teleport(world, target.x, target.y, target.z, Set.of(), player.getYaw(), player.getPitch(), false);
		BloodFx.play(world, target, BloodFx.RIFT_STEP, 1.0F, 0.8F);
		BloodFx.splash(world, target.add(0.0, 1.0, 0.0), 8);
	}

	@Override
	public Text hudStatus(ServerPlayerEntity player) {
		ActiveRift rift = ACTIVE_RIFTS.get(player.getUuid());
		long left = rift == null ? 0 : rift.expiresAt() - ServerClock.now();
		if (rift != null && left > 0 && rift.world() == player.getEntityWorld()) {
			MutableText line = Hud.timer("\u25C9 Rift open", left);
			return line.append(Text.literal("  use again to step through").formatted(Formatting.GRAY));
		}
		return Hud.cooldownBar(player, ability);
	}

	@Override
	public ParticleEffect auraAccent() {
		return BloodFx.SPORE;
	}

	public static void forget(UUID playerId) {
		ACTIVE_RIFTS.remove(playerId);
	}

	public static void prune() {
		long now = ServerClock.now();
		ACTIVE_RIFTS.values().removeIf(rift -> rift.expiresAt() < now);
	}

	public static void clearAll() {
		ACTIVE_RIFTS.clear();
	}
}
