package net.unchartedsmp.item;

import java.util.Set;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.particle.ParticleEffect;
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
 * Crimson Thunder Pike: throw a bolt of charged blood up to 22 blocks, call crimson lightning
 * where it lands and ride it there.
 *
 * <p>The lightning is now cosmetic and the pike deals its own damage: real lightning struck the
 * wielder after the teleport, set fires, and could be farmed to convert villagers into witches,
 * pigs into piglins and creepers into charged creepers on demand.
 */
public class ThunderPikeItem extends AbilityWeapon {
	private static final double RANGE = 22.0;
	private static final int TRAVEL_TICKS = 8;
	private static final double STRIKE_RADIUS = 3.0;
	private static final float STRIKE_DAMAGE = 5.0F;

	public ThunderPikeItem(Settings settings) {
		super(settings, Ability.THUNDER_PIKE);
	}

	@Override
	protected ActionResult useAbility(ServerWorld world, ServerPlayerEntity player) {
		if (!Cooldowns.checkReady(player, ability)) {
			return ActionResult.FAIL;
		}
		Cooldowns.start(player, ability);

		Vec3d start = player.getEyePos();
		Vec3d impact = Targeting.lookTarget(world, player, RANGE);
		// Telegraph the landing spot so the target (and you) can see where the bolt will fall.
		BloodFx.ring(world, BloodFx.BLOOD_FADE, impact.add(0.0, 0.1, 0.0), STRIKE_RADIUS, 24);
		TickScheduler.repeat(0, 1, TRAVEL_TICKS, tick -> {
			Vec3d p = start.lerp(impact, (tick + 1) / (double) TRAVEL_TICKS);
			BloodFx.burst(world, BloodFx.SPARK, p, 6, 0.12);
			BloodFx.burst(world, BloodFx.BLOOD_FADE, p, 3, 0.12);
			if (tick % 2 == 0) {
				BloodFx.ring(world, BloodFx.SPARK, impact.add(0.0, 0.1, 0.0), STRIKE_RADIUS * (1.0 - tick / (double) TRAVEL_TICKS), 16);
			}
			return true;
		});
		TickScheduler.schedule(TRAVEL_TICKS, () -> strike(world, player, start, impact));
		return ActionResult.SUCCESS;
	}

	@Override
	public ParticleEffect auraAccent() {
		return BloodFx.SPARK;
	}

	private static void strike(ServerWorld world, ServerPlayerEntity player, Vec3d start, Vec3d impact) {
		LightningEntity bolt = new LightningEntity(EntityType.LIGHTNING_BOLT, world);
		bolt.refreshPositionAfterTeleport(impact);
		bolt.setCosmetic(true);
		world.spawnEntity(bolt);

		BloodFx.burst(world, BloodFx.SPARK, impact, 45, 0.6);
		BloodFx.splash(world, impact, 8);
		BloodFx.spray(world, impact, STRIKE_RADIUS, 12, 8);
		BloodFx.line(world, BloodFx.BLOOD_FADE, impact, impact.add(0.0, 12.0, 0.0), 2.0);
		BloodFx.play(world, impact, BloodFx.THUNDER, 1.0F, 1.2F);

		DamageSource source = player.isAlive()
			? world.getDamageSources().playerAttack(player)
			: world.getDamageSources().lightningBolt();
		for (LivingEntity target : Targeting.livingInRadius(world, impact, STRIKE_RADIUS, player)) {
			target.damage(world, source, STRIKE_DAMAGE);
		}

		// Only ride the bolt if we're still in the same dimension (no portal-hopping mid-cast)
		// and there's room to stand where it landed.
		if (Targeting.stillIn(player, world)) {
			Targeting.safeLanding(world, player, start, impact).ifPresent(landing ->
				player.teleport(world, landing.x, landing.y, landing.z, Set.of(), player.getYaw(), player.getPitch(), false)
			);
		}
	}
}
