package net.unchartedsmp.item;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.unchartedsmp.ability.Ability;
import net.unchartedsmp.ability.Cooldowns;
import net.unchartedsmp.ability.TickScheduler;
import net.unchartedsmp.fx.BloodFx;
import net.unchartedsmp.util.Targeting;

/**
 * Sanguine Paradox Bow: fires a blood-arrow up to 24 blocks. 3s later its echo flies back
 * along the same path to where you stood, dealing 7 damage to everything it passes through.
 *
 * <p>Each entity is now hit at most once per echo. The old echo sampled 16 overlapping boxes,
 * so anything standing on the line could be hit repeatedly as i-frames wore off.
 */
public class ParadoxBowItem extends AbilityWeapon {
	private static final int ECHO_DELAY_TICKS = 60;
	private static final double RANGE = 24.0;
	private static final int ECHO_STEPS = 16;
	private static final double HIT_SIZE = 1.3;
	private static final float ECHO_DAMAGE = 7.0F;

	public ParadoxBowItem(Settings settings) {
		super(settings, Ability.PARADOX_BOW);
	}

	@Override
	protected ActionResult useAbility(ServerWorld world, ServerPlayerEntity player) {
		if (!Cooldowns.checkReady(player, ability)) {
			return ActionResult.FAIL;
		}
		Cooldowns.start(player, ability);

		Vec3d start = player.getEyePos();
		Vec3d finish = Targeting.lookTarget(world, player, RANGE);
		BloodFx.line(world, BloodFx.BLOOD, start, finish, 2.0);
		BloodFx.burst(world, BloodFx.BLOOD_LARGE, finish, 15, 0.25);
		BloodFx.play(world, player, BloodFx.BOW_RELEASE, 1.0F, 0.8F);

		// The arrow's resting point keeps bleeding until the echo returns.
		TickScheduler.repeat(10, 10, ECHO_DELAY_TICKS / 10 - 1, tick -> {
			BloodFx.burst(world, BloodFx.DRIP, finish, 2, 0.15, 0.0);
			return true;
		});

		TickScheduler.schedule(ECHO_DELAY_TICKS, () -> {
			BloodFx.play(world, start, BloodFx.BOW_ECHO, 0.6F, 1.5F);
			Set<UUID> alreadyHit = new HashSet<>();
			TickScheduler.repeat(0, 1, ECHO_STEPS, step -> {
				Vec3d p = finish.lerp(start, step / (double) (ECHO_STEPS - 1));
				BloodFx.burst(world, BloodFx.BLOOD, p, 3, 0.08);
				BloodFx.burst(world, BloodFx.SPLATTER, p, 1, 0.05, 0.1);
				for (LivingEntity target : world.getEntitiesByClass(
					LivingEntity.class,
					Box.of(p, HIT_SIZE, HIT_SIZE, HIT_SIZE),
					e -> e != player && e.isAlive() && !e.isSpectator() && !Targeting.isDecoration(e)
				)) {
					if (alreadyHit.add(target.getUuid())) {
						target.damage(world, world.getDamageSources().playerAttack(player), ECHO_DAMAGE);
						BloodFx.splash(world, Targeting.chest(target), 4);
					}
				}
				return true;
			});
		});
		return ActionResult.SUCCESS;
	}
}
