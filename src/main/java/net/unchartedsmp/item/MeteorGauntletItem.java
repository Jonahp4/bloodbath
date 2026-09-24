package net.unchartedsmp.item;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.unchartedsmp.ability.Ability;
import net.unchartedsmp.ability.Cooldowns;
import net.unchartedsmp.ability.NullField;
import net.unchartedsmp.ability.TickScheduler;
import net.unchartedsmp.fx.BloodFx;
import net.unchartedsmp.util.Targeting;

/**
 * Blood Meteor Gauntlet: punch a block to call down a blood meteor. After a 2s telegraph it
 * deals 5 damage and launches everything within 4.5 blocks - except the caster (the old
 * version blew its own wielder up).
 */
public class MeteorGauntletItem extends Item {
	private static final int TELEGRAPH_TICKS = 40;
	private static final double IMPACT_RADIUS = 4.5;
	private static final float IMPACT_DAMAGE = 5.0F;
	private static final double LAUNCH_STRENGTH = 1.6;
	private static final double LAUNCH_VERTICAL = 1.1;

	public MeteorGauntletItem(Settings settings) {
		super(settings);
	}

	/** Called from the {@code AttackBlockCallback} registered in the mod initializer. */
	public static void onBlockPunched(PlayerEntity user, World world, BlockPos pos) {
		if (!(world instanceof ServerWorld serverWorld) || !(user instanceof ServerPlayerEntity player) || player.isSpectator()) {
			return;
		}
		if (NullField.isNullified(player)) {
			NullField.notifyNullified(player);
			return;
		}
		if (!Cooldowns.checkReady(player, Ability.METEOR_GAUNTLET)) {
			return;
		}
		Cooldowns.start(player, Ability.METEOR_GAUNTLET);
		telegraphAndStrike(serverWorld, player, Vec3d.ofCenter(pos).add(0.0, 0.55, 0.0));
	}

	private static void telegraphAndStrike(ServerWorld world, ServerPlayerEntity caster, Vec3d center) {
		BloodFx.play(world, center, BloodFx.HEARTBEAT, 1.0F, 0.6F);
		double ringRadius = IMPACT_RADIUS * 0.6;
		TickScheduler.repeat(0, 4, TELEGRAPH_TICKS / 4, tick -> {
			BloodFx.ring(world, BloodFx.BLOOD, center, ringRadius, 16);
			// The meteor: a falling clot that closes in on the impact point.
			Vec3d meteor = center.add(0.0, 12.0 - tick * 1.2, 0.0);
			BloodFx.burst(world, BloodFx.BLOOD_LARGE, meteor, 6, 0.25);
			BloodFx.burst(world, BloodFx.DRIP, meteor, 3, 0.2, 0.0);
			return true;
		});
		TickScheduler.schedule(TELEGRAPH_TICKS, () -> strike(world, caster, center));
	}

	private static void strike(ServerWorld world, ServerPlayerEntity caster, Vec3d center) {
		BloodFx.play(world, center, BloodFx.IMPACT, 1.4F, 0.8F);
		BloodFx.play(world, center, BloodFx.SQUELCH, 1.2F, 0.5F);
		BloodFx.burst(world, BloodFx.BURST_HUGE, center, 1, 0.0);
		BloodFx.burst(world, BloodFx.SPLATTER, center, 40, 1.5, 0.2);
		BloodFx.burst(world, BloodFx.BLOOD_LARGE, center, 25, 1.2);

		DamageSource source = caster.isAlive()
			? world.getDamageSources().playerAttack(caster)
			: world.getDamageSources().generic();
		for (LivingEntity target : Targeting.livingInRadius(world, center, IMPACT_RADIUS, caster)) {
			target.damage(world, source, IMPACT_DAMAGE);
			Targeting.launchOutward(target, center, LAUNCH_STRENGTH, LAUNCH_VERTICAL);
		}
	}
}
