package net.unchartedsmp.item;

import net.minecraft.entity.LivingEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.Vec3d;
import net.unchartedsmp.ability.Ability;
import net.unchartedsmp.ability.Cooldowns;
import net.unchartedsmp.ability.TickScheduler;
import net.unchartedsmp.fx.BloodFx;
import net.unchartedsmp.util.Targeting;

/**
 * Crimson Gravestone: opens a blood pool at your feet that drags everything within 6 blocks
 * inward for 3s, then erupts and hurls them away.
 */
public class GravestoneItem extends AbilityWeapon {
	private static final double FIELD_RADIUS = 6.0;
	private static final int PULL_DURATION_TICKS = 60;
	private static final double PULL_STRENGTH = 0.22;
	private static final double LAUNCH_STRENGTH = 1.7;
	private static final double LAUNCH_VERTICAL = 1.3;

	public GravestoneItem(Settings settings) {
		super(settings, Ability.GRAVESTONE);
	}

	@Override
	protected ActionResult useAbility(ServerWorld world, ServerPlayerEntity player) {
		if (!Cooldowns.checkReady(player, ability)) {
			return ActionResult.FAIL;
		}
		Cooldowns.start(player, ability);
		slam(world, player);
		return ActionResult.SUCCESS;
	}

	private static void slam(ServerWorld world, ServerPlayerEntity caster) {
		Vec3d center = caster.getEntityPos();
		Vec3d floor = center.add(0.0, 0.1, 0.0);
		BloodFx.play(world, center, BloodFx.HEARTBEAT, 1.2F, 0.5F);
		BloodFx.play(world, center, BloodFx.SQUELCH, 1.0F, 0.6F);
		BloodFx.burst(world, BloodFx.BURST, center, 1, 0.0);
		BloodFx.burst(world, BloodFx.GORE, floor, 30, 1.5, 0.1);

		// One repeating task instead of 60 separately scheduled lambdas.
		TickScheduler.repeat(0, 1, PULL_DURATION_TICKS, tick -> {
			for (LivingEntity target : Targeting.livingInRadius(world, center, FIELD_RADIUS, caster)) {
				Targeting.pullTowards(target, center, PULL_STRENGTH);
			}
			if (tick % 5 == 0) {
				double shrinking = FIELD_RADIUS * (1.0 - tick / (double) PULL_DURATION_TICKS) + 0.5;
				BloodFx.ring(world, BloodFx.BLOOD_FADE, floor, shrinking, 24);
				BloodFx.burst(world, BloodFx.SPORE, center, 8, FIELD_RADIUS * 0.4);
				BloodFx.gather(world, center.add(0.0, 0.4, 0.0), FIELD_RADIUS, 6, 12);
			}
			return true;
		});

		TickScheduler.schedule(PULL_DURATION_TICKS, () -> {
			BloodFx.play(world, center, BloodFx.IMPACT, 1.0F, 1.4F);
			BloodFx.burst(world, BloodFx.SPLATTER, center, 50, 3.0, 0.25);
			BloodFx.burst(world, BloodFx.BLOOD_LARGE, center, 30, 2.0);
			BloodFx.spray(world, center.add(0.0, 0.3, 0.0), FIELD_RADIUS, 16, 10);
			for (LivingEntity target : Targeting.livingInRadius(world, center, FIELD_RADIUS, caster)) {
				Targeting.launchOutward(target, center, LAUNCH_STRENGTH, LAUNCH_VERTICAL);
			}
		});
	}
}
