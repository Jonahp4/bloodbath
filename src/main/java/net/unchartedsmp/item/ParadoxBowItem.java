package net.unchartedsmp.item;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.BowItem;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Box;
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
 * Sanguine Paradox Bow: a real bow (draws, pulls back, shoots your arrows, takes bow
 * enchantments). A <b>fully drawn</b> shot also leaves a Paradox Echo: 3s later the shot's echo
 * tears back along the same path to where you stood, dealing 7 damage to everything it passes
 * through, once per target.
 *
 * <p>While drawing, blood visibly gathers into the nocked arrow; at full draw there's a click and,
 * if the echo is off cooldown, the arrow glows to show it's primed.
 */
public class ParadoxBowItem extends BowItem implements BloodWeapon {
	private static final int FULL_DRAW_TICKS = 20;
	private static final int ECHO_DELAY_TICKS = 60;
	private static final double RANGE = 24.0;
	private static final int ECHO_STEPS = 16;
	private static final double HIT_SIZE = 1.3;
	private static final float ECHO_DAMAGE = 7.0F;

	public ParadoxBowItem(Settings settings) {
		super(settings);
	}

	@Override
	public Ability ability() {
		return Ability.PARADOX_BOW;
	}

	@Override
	public void usageTick(World world, LivingEntity user, ItemStack stack, int remainingUseTicks) {
		super.usageTick(world, user, stack, remainingUseTicks);
		if (!(world instanceof ServerWorld serverWorld) || !(user instanceof ServerPlayerEntity player)) {
			return;
		}
		int drawn = getMaxUseTime(stack, user) - remainingUseTicks;
		Vec3d nock = nockPos(player);
		boolean primed = echoAvailable(player);
		if (drawn < FULL_DRAW_TICKS) {
			if (drawn % 3 == 0) {
				BloodFx.gather(serverWorld, nock, 1.8 - drawn * 0.06, 3, 6);
			}
		} else if (drawn == FULL_DRAW_TICKS) {
			BloodFx.play(serverWorld, player, BloodFx.BOW_DRAWN, 0.8F, primed ? 0.6F : 1.3F);
			if (primed) {
				BloodFx.play(serverWorld, player, BloodFx.HEARTBEAT, 0.7F, 1.4F);
				BloodFx.burst(serverWorld, BloodFx.BLOOD_FADE, nock, 14, 0.2);
			}
		} else if (primed && drawn % 5 == 0) {
			BloodFx.burst(serverWorld, BloodFx.BLOOD, nock, 3, 0.1);
			BloodFx.burst(serverWorld, BloodFx.DRIP, nock, 1, 0.1, 0.0);
		}
	}

	@Override
	public boolean onStoppedUsing(ItemStack stack, World world, LivingEntity user, int remainingUseTicks) {
		boolean shot = super.onStoppedUsing(stack, world, user, remainingUseTicks);
		if (shot && world instanceof ServerWorld serverWorld && user instanceof ServerPlayerEntity player
			&& getPullProgress(getMaxUseTime(stack, user) - remainingUseTicks) >= 1.0F) {
			releaseEcho(serverWorld, player);
		}
		return shot;
	}

	private static boolean echoAvailable(ServerPlayerEntity player) {
		return Cooldowns.isReady(player, Ability.PARADOX_BOW) && !NullField.isNullified(player);
	}

	private static Vec3d nockPos(ServerPlayerEntity player) {
		return player.getEyePos().add(player.getRotationVec(1.0F).multiply(0.9)).add(0.0, -0.2, 0.0);
	}

	private static void releaseEcho(ServerWorld world, ServerPlayerEntity player) {
		if (NullField.isNullified(player)) {
			NullField.notifyNullified(player);
			return;
		}
		if (!Cooldowns.checkReady(player, Ability.PARADOX_BOW)) {
			return;
		}
		Cooldowns.start(player, Ability.PARADOX_BOW);

		Vec3d start = player.getEyePos();
		Vec3d finish = Targeting.lookTarget(world, player, RANGE);
		BloodFx.flow(world, start, finish, 6, 0.05, BloodFx.BRIGHT_RED, 10);
		BloodFx.burst(world, BloodFx.BLOOD_LARGE, finish, 15, 0.25);
		BloodFx.play(world, player, BloodFx.BOW_RELEASE, 1.0F, 0.6F);
		Hud.flash(player, Text.literal("⧖ Paradox Echo returns in 3s").formatted(Formatting.RED));

		// Countdown at the arrow's resting point: a shrinking ring and a tick each second.
		TickScheduler.repeat(10, 10, ECHO_DELAY_TICKS / 10 - 1, tick -> {
			double radius = 1.2 - tick * 0.18;
			BloodFx.ring(world, BloodFx.BLOOD_FADE, finish, Math.max(0.2, radius), 12);
			BloodFx.burst(world, BloodFx.DRIP, finish, 2, 0.15, 0.0);
			if (tick % 2 == 1) {
				BloodFx.play(world, finish, BloodFx.CLOCK_TICK, 0.8F, 0.6F + tick * 0.1F);
			}
			return true;
		});

		TickScheduler.schedule(ECHO_DELAY_TICKS, () -> {
			BloodFx.play(world, finish, BloodFx.BOW_ECHO, 0.6F, 1.5F);
			BloodFx.flow(world, finish, start, 10, 0.1, BloodFx.BRIGHT_RED, ECHO_STEPS);
			Set<UUID> alreadyHit = new HashSet<>();
			TickScheduler.repeat(0, 1, ECHO_STEPS, step -> {
				Vec3d p = finish.lerp(start, step / (double) (ECHO_STEPS - 1));
				BloodFx.burst(world, BloodFx.BLOOD_FADE, p, 4, 0.1);
				BloodFx.burst(world, BloodFx.SPLATTER, p, 1, 0.05, 0.1);
				for (LivingEntity target : world.getEntitiesByClass(
					LivingEntity.class,
					Box.of(p, HIT_SIZE, HIT_SIZE, HIT_SIZE),
					e -> e != player && e.isAlive() && !e.isSpectator() && !Targeting.isDecoration(e)
				)) {
					if (alreadyHit.add(target.getUuid())) {
						target.damage(world, world.getDamageSources().playerAttack(player), ECHO_DAMAGE);
						BloodFx.splash(world, Targeting.chest(target), 5);
					}
				}
				return true;
			});
		});
	}

	@Override
	public Text hudStatus(ServerPlayerEntity player) {
		if (player.isUsingItem() && player.getActiveItem().getItem() == this) {
			float pull = getPullProgress(player.getItemUseTime());
			MutableText line = Text.literal("Draw  ").formatted(Formatting.DARK_RED).append(Hud.bar(pull));
			if (pull >= 1.0F) {
				line.append(echoAvailable(player)
					? Text.literal("  ● ECHO PRIMED").formatted(Formatting.RED)
					: Text.literal("  full draw").formatted(Formatting.GRAY));
			}
			return line;
		}
		MutableText line = Hud.cooldownBar(player, ability());
		return Cooldowns.isReady(player, ability())
			? line.append(Text.literal("  full draw releases it").formatted(Formatting.GRAY))
			: line;
	}
}
