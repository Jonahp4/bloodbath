package net.unchartedsmp.item;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ToolMaterial;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;
import net.unchartedsmp.ability.Ability;
import net.unchartedsmp.ability.Cooldowns;
import net.unchartedsmp.ability.ServerClock;
import net.unchartedsmp.fx.BloodFx;
import net.unchartedsmp.util.Targeting;

/**
 * Hemorrhage Scythe (item id {@code void_scythe}): every hit makes the target bleed. The 5th hit
 * within 8s of the last one hemorrhages them for 10 damage and rips 8 damage through everything
 * within 4 blocks.
 *
 * <p>Bleed stacks are tracked per <i>attacker and</i> target. They used to be tracked per target
 * only, so two players with scythes could stack each other's bleed, and the map grew with every
 * mob ever hit.
 */
public class VoidScytheItem extends AbilityWeapon {
	private static final int STACKS_TO_HEMORRHAGE = 5;
	private static final int STACK_TIMEOUT_TICKS = 160;
	private static final double HEMORRHAGE_RADIUS = 4.0;
	private static final float HEMORRHAGE_TARGET_DAMAGE = 10.0F;
	private static final float HEMORRHAGE_SPLASH_DAMAGE = 8.0F;

	private record BleedKey(UUID attacker, UUID target) {
	}

	private record Bleed(int stacks, long expiresAt) {
	}

	private static final Map<BleedKey, Bleed> BLEEDS = new HashMap<>();

	public VoidScytheItem(Settings settings) {
		super(settings.sword(ToolMaterial.NETHERITE, 7.0F, -2.6F), Ability.VOID_SCYTHE);
	}

	@Override
	public void postHit(ItemStack stack, LivingEntity target, LivingEntity attacker) {
		if (attacker instanceof PlayerEntity player && target.getEntityWorld() instanceof ServerWorld world) {
			BleedKey key = new BleedKey(player.getUuid(), target.getUuid());
			long now = ServerClock.now();
			Bleed previous = BLEEDS.get(key);
			int stacks = previous != null && now <= previous.expiresAt() ? previous.stacks() + 1 : 1;
			if (stacks >= STACKS_TO_HEMORRHAGE) {
				BLEEDS.remove(key);
				hemorrhage(world, player, target);
			} else {
				BLEEDS.put(key, new Bleed(stacks, now + STACK_TIMEOUT_TICKS));
				BloodFx.burst(world, BloodFx.BLOOD, Targeting.chest(target), 8 + stacks * 2, 0.25);
				BloodFx.burst(world, BloodFx.DRIP, Targeting.chest(target), stacks, 0.25, 0.0);
				player.sendMessage(Text.literal("Bleed: " + stacks + "/" + STACKS_TO_HEMORRHAGE).formatted(Formatting.RED), true);
			}
		}
		super.postHit(stack, target, attacker);
	}

	private static void hemorrhage(ServerWorld world, PlayerEntity player, LivingEntity target) {
		Vec3d center = target.getEntityPos();
		Vec3d chest = Targeting.chest(target);
		BloodFx.burst(world, BloodFx.BLOOD_LARGE, chest, 70, 1.0);
		BloodFx.splash(world, chest, 12);
		BloodFx.play(world, center, BloodFx.FANGS, 0.9F, 0.7F);
		BloodFx.play(world, center, BloodFx.SQUELCH, 1.0F, 0.5F);

		DamageSource source = world.getDamageSources().playerAttack(player);
		for (LivingEntity nearby : Targeting.livingInRadius(world, center, HEMORRHAGE_RADIUS, player)) {
			if (nearby != target) {
				Targeting.pullTowards(nearby, center, 0.8);
				nearby.damage(world, source, HEMORRHAGE_SPLASH_DAMAGE);
			}
		}
		target.damage(world, source, HEMORRHAGE_TARGET_DAMAGE);
	}

	@Override
	protected ActionResult useAbility(ServerWorld world, ServerPlayerEntity player) {
		if (!Cooldowns.checkReady(player, ability)) {
			return ActionResult.FAIL;
		}
		Vec3d chest = Targeting.chest(player);
		BloodFx.ring(world, BloodFx.BLOOD, chest, 1.6, 20);
		BloodFx.burst(world, BloodFx.SWEEP, chest, 3, 0.8);
		BloodFx.play(world, player, BloodFx.HARVEST, 1.0F, 0.6F);
		Cooldowns.start(player, ability);
		return ActionResult.SUCCESS;
	}

	public static void prune() {
		long now = ServerClock.now();
		BLEEDS.values().removeIf(bleed -> bleed.expiresAt() < now);
	}

	public static void clearAll() {
		BLEEDS.clear();
	}
}
