package net.unchartedsmp.item;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ToolMaterial;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.Vec3d;
import net.unchartedsmp.ability.Ability;
import net.unchartedsmp.ability.Cooldowns;
import net.unchartedsmp.ability.NullField;
import net.unchartedsmp.ability.TickScheduler;
import net.unchartedsmp.fx.BloodFx;
import net.unchartedsmp.util.Targeting;

/**
 * Clotblade (item id {@code nullblade}): hitting a player clots their blood, suppressing their
 * abilities for 4s. Right-click throws down an 8s clot field that suppresses everyone inside.
 */
public class NullbladeItem extends AbilityWeapon {
	private static final int ON_HIT_NULLIFY_TICKS = 80;
	private static final double ZONE_RADIUS = 4.0;
	private static final int ZONE_DURATION_TICKS = 160;
	private static final double ZONE_PLACE_DISTANCE = 8.0;

	public NullbladeItem(Settings settings) {
		super(settings.sword(ToolMaterial.IRON, 3.0F, -2.4F), Ability.NULLBLADE_ZONE);
	}

	@Override
	protected boolean canBeNullified() {
		return false;
	}

	@Override
	public void postHit(ItemStack stack, LivingEntity target, LivingEntity attacker) {
		if (target instanceof PlayerEntity targetPlayer && target.getEntityWorld() instanceof ServerWorld world) {
			NullField.debuff(targetPlayer, ON_HIT_NULLIFY_TICKS);
			BloodFx.play(world, target, BloodFx.NULLIFY, 0.6F, 1.5F);
			BloodFx.burst(world, BloodFx.CLOT, Targeting.chest(target), 10, 0.3);
			NullField.notifyNullified(targetPlayer);
		}
		super.postHit(stack, target, attacker);
	}

	@Override
	protected ActionResult useAbility(ServerWorld world, ServerPlayerEntity player) {
		if (!Cooldowns.checkReady(player, ability)) {
			return ActionResult.FAIL;
		}
		Vec3d center = Targeting.lookTarget(world, player, ZONE_PLACE_DISTANCE);
		NullField.createZone(world, center, ZONE_RADIUS, ZONE_DURATION_TICKS);
		BloodFx.burst(world, BloodFx.CLOT, center, 30, 2.0);
		BloodFx.burst(world, BloodFx.SOUL, center, 20, 2.0);
		BloodFx.play(world, center, BloodFx.NULLIFY, 1.0F, 0.7F);
		BloodFx.play(world, center, BloodFx.HEARTBEAT, 1.0F, 0.6F);
		Cooldowns.start(player, ability);

		// Show the field's edge for as long as it's active so players can see where it ends.
		Vec3d ringCenter = center.add(0.0, 0.1, 0.0);
		TickScheduler.repeat(10, 10, ZONE_DURATION_TICKS / 10, tick -> {
			BloodFx.ring(world, BloodFx.CLOT, ringCenter, ZONE_RADIUS, 24);
			return true;
		});
		return ActionResult.SUCCESS;
	}
}
