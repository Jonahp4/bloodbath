package net.unchartedsmp.item;

import java.util.Optional;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.unchartedsmp.ability.Ability;
import net.unchartedsmp.ability.Cooldowns;
import net.unchartedsmp.ability.NullField;
import net.unchartedsmp.ability.TickScheduler;
import net.unchartedsmp.fx.BloodFx;
import net.unchartedsmp.hud.Hud;
import net.unchartedsmp.util.Targeting;

/**
 * Blood Meteor Gauntlet: punch a block, or right-click the ground up to 24 blocks away, to call
 * down a blood meteor. After a 2s telegraph it deals 5 damage and launches everything within
 * 4.5 blocks - except the caster (the old version blew its own wielder up).
 */
public class MeteorGauntletItem extends AbilityWeapon {
	private static final int TELEGRAPH_TICKS = 40;
	private static final double IMPACT_RADIUS = 4.5;
	private static final float IMPACT_DAMAGE = 5.0F;
	private static final double LAUNCH_STRENGTH = 1.6;
	private static final double LAUNCH_VERTICAL = 1.1;
	private static final double CALL_RANGE = 24.0;

	public MeteorGauntletItem(Settings settings) {
		super(settings, Ability.METEOR_GAUNTLET);
	}

	@Override
	protected ActionResult useAbility(ServerWorld world, ServerPlayerEntity player) {
		Optional<Vec3d> ground = Targeting.lookBlock(world, player, CALL_RANGE);
		if (ground.isEmpty()) {
			Hud.flash(player, Text.literal("No ground within reach for the meteor.").formatted(Formatting.GRAY));
			return ActionResult.FAIL;
		}
		if (!Cooldowns.checkReady(player, ability)) {
			return ActionResult.FAIL;
		}
		Cooldowns.start(player, ability);
		telegraphAndStrike(world, player, ground.get().add(0.0, 0.1, 0.0));
		return ActionResult.SUCCESS;
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

	@Override
	public ParticleEffect auraAccent() {
		return BloodFx.BLOOD_LARGE;
	}

	private static void telegraphAndStrike(ServerWorld world, ServerPlayerEntity caster, Vec3d center) {
		BloodFx.play(world, center, BloodFx.HEARTBEAT, 1.0F, 0.6F);
		BloodFx.play(world, center, BloodFx.ROAR, 0.4F, 1.6F);
		TickScheduler.repeat(0, 4, TELEGRAPH_TICKS / 4, tick -> {
			// Danger ring at full radius plus an inner ring closing in: impact when they meet.
			BloodFx.ring(world, BloodFx.CLOT, center, IMPACT_RADIUS, 28);
			BloodFx.ring(world, BloodFx.BLOOD_FADE, center, IMPACT_RADIUS * (1.0 - tick / 10.0), 20);
			// The meteor: a falling clot that closes in on the impact point.
			Vec3d meteor = center.add(0.0, 14.0 - tick * 1.4, 0.0);
			BloodFx.burst(world, BloodFx.BLOOD_LARGE, meteor, 8, 0.3);
			BloodFx.burst(world, BloodFx.SMOKE, meteor.add(0.0, 0.8, 0.0), 2, 0.2, 0.0);
			BloodFx.burst(world, BloodFx.DRIP, meteor, 3, 0.2, 0.0);
			return true;
		});
		TickScheduler.schedule(TELEGRAPH_TICKS, () -> strike(world, caster, center));
	}

	private static void strike(ServerWorld world, ServerPlayerEntity caster, Vec3d center) {
		BloodFx.play(world, center, BloodFx.IMPACT, 1.4F, 0.8F);
		BloodFx.play(world, center, BloodFx.SQUELCH, 1.2F, 0.5F);
		BloodFx.burst(world, BloodFx.BURST_HUGE, center, 1, 0.0);
		BloodFx.burst(world, BloodFx.SPLATTER, center, 50, 1.5, 0.25);
		BloodFx.burst(world, BloodFx.BLOOD_LARGE, center, 25, 1.2);
		BloodFx.spray(world, center, IMPACT_RADIUS, 20, 10);
		BloodFx.ring(world, BloodFx.CLOT, center, IMPACT_RADIUS * 0.7, 24);

		DamageSource source = caster.isAlive()
			? world.getDamageSources().playerAttack(caster)
			: world.getDamageSources().generic();
		for (LivingEntity target : Targeting.livingInRadius(world, center, IMPACT_RADIUS, caster)) {
			target.damage(world, source, IMPACT_DAMAGE);
			Targeting.launchOutward(target, center, LAUNCH_STRENGTH, LAUNCH_VERTICAL);
		}
	}
}
