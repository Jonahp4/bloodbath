package net.unchartedsmp.hud;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;
import net.unchartedsmp.ability.Ability;
import net.unchartedsmp.ability.Cooldowns;
import net.unchartedsmp.ability.NullField;
import net.unchartedsmp.ability.ServerClock;
import net.unchartedsmp.fx.BloodFx;
import net.unchartedsmp.item.BloodWeapon;
import net.unchartedsmp.item.VoidScytheItem;

/**
 * Everything the player sees without using an ability:
 * <ul>
 *   <li>an action-bar status line while a Bloodbath weapon is held (cooldown bar, open rift,
 *       blood-mark timer, bow draw, bleed stacks...);</li>
 *   <li>a "ready" ping and blood ring when any ability comes off cooldown;</li>
 *   <li>a dripping aura around a held Bloodbath weapon;</li>
 *   <li>clot particles over players whose abilities are suppressed.</li>
 * </ul>
 * One-off messages go through {@link #flash} so the status line doesn't immediately overwrite them.
 */
public final class Hud {
	private static final int INTERVAL_TICKS = 4;
	private static final int FLASH_HOLD_TICKS = 40;
	private static final int BAR_SEGMENTS = 20;
	private static final Map<UUID, Long> FLASH_UNTIL = new HashMap<>();

	private Hud() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Hud::tick);
	}

	/** Shows {@code message} on the action bar and holds it there for 2 seconds. */
	public static void flash(PlayerEntity player, Text message) {
		player.sendMessage(message, true);
		FLASH_UNTIL.put(player.getUuid(), ServerClock.now() + FLASH_HOLD_TICKS);
	}

	private static void tick(MinecraftServer server) {
		long now = ServerClock.now();
		if (now % INTERVAL_TICKS != 0) {
			return;
		}
		if (now % (INTERVAL_TICKS * 2) == 0) {
			VoidScytheItem.drip();
		}
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			if (!(player.getEntityWorld() instanceof ServerWorld world) || !player.isAlive()) {
				continue;
			}
			for (Ability ready : Cooldowns.drainReady(player)) {
				announceReady(world, player, ready);
			}
			long clotted = NullField.remainingTicks(player);
			if (clotted > 0 && now % (INTERVAL_TICKS * 2) == 0) {
				BloodFx.burst(world, BloodFx.CLOT, player.getEntityPos().add(0.0, player.getHeight() + 0.35, 0.0), 3, 0.25);
			}
			ItemStack held = player.getMainHandStack();
			if (held.getItem() instanceof BloodWeapon weapon) {
				aura(world, player, weapon, now);
				if (now >= FLASH_UNTIL.getOrDefault(player.getUuid(), 0L)) {
					player.sendMessage(clotted > 0 ? clottedStatus(clotted) : weapon.hudStatus(player), true);
				}
			}
		}
	}

	private static void announceReady(ServerWorld world, ServerPlayerEntity player, Ability ability) {
		Vec3d chest = player.getEntityPos().add(0.0, 1.0, 0.0);
		BloodFx.play(world, player, BloodFx.READY, 0.5F, 0.7F);
		BloodFx.ring(world, BloodFx.BLOOD_FADE, chest, 0.9, 14);
		BloodFx.gather(world, handPos(player), 1.2, 4, 8);
		flash(player, Text.literal("✦ " + ability.displayName() + " ready").formatted(Formatting.RED));
	}

	/** Blood slowly dripping off whatever Bloodbath weapon is in hand. */
	private static void aura(ServerWorld world, ServerPlayerEntity player, BloodWeapon weapon, long now) {
		Vec3d hand = handPos(player);
		BloodFx.ambient(world, BloodFx.DRIP, hand, 1, 0.08);
		if ((now / INTERVAL_TICKS) % 3 == 0) {
			BloodFx.ambient(world, weapon.auraAccent(), hand.add(0.0, 0.2, 0.0), 1, 0.15);
		}
		if (Cooldowns.isReady(player, weapon.ability()) && (now / INTERVAL_TICKS) % 5 == 0) {
			BloodFx.ambient(world, BloodFx.BLOOD_FADE, hand.add(0.0, 0.3, 0.0), 2, 0.2);
		}
	}

	/** Roughly where the right hand is, from the player's facing. */
	public static Vec3d handPos(PlayerEntity player) {
		double yaw = Math.toRadians(player.getYaw());
		double forwardX = -Math.sin(yaw);
		double forwardZ = Math.cos(yaw);
		double rightX = -Math.cos(yaw);
		double rightZ = -Math.sin(yaw);
		double height = player.isSneaking() ? 0.55 : 0.8;
		return player.getEntityPos().add(rightX * 0.38 + forwardX * 0.25, height, rightZ * 0.38 + forwardZ * 0.25);
	}

	// ---- status line pieces ----------------------------------------------------------------

	/** "Bloodrift |||||||||||||||||||| 6.2s" or "Bloodrift ● READY". */
	public static MutableText cooldownBar(PlayerEntity player, Ability ability) {
		MutableText line = Text.literal(ability.displayName() + "  ").formatted(Formatting.DARK_RED);
		long remaining = Cooldowns.remainingTicks(player, ability);
		if (remaining <= 0) {
			return line.append(Text.literal("● READY").formatted(Formatting.RED));
		}
		return line.append(bar(Cooldowns.progress(player, ability)))
			.append(Text.literal(String.format(Locale.ROOT, " %.1fs", remaining / 20.0)).formatted(Formatting.GRAY));
	}

	public static MutableText bar(float progress) {
		int filled = Math.max(0, Math.min(BAR_SEGMENTS, Math.round(progress * BAR_SEGMENTS)));
		return Text.literal("|".repeat(filled)).formatted(Formatting.RED)
			.append(Text.literal("|".repeat(BAR_SEGMENTS - filled)).formatted(Formatting.DARK_GRAY));
	}

	public static MutableText timer(String label, long ticks) {
		return Text.literal(label).formatted(Formatting.RED)
			.append(Text.literal(String.format(Locale.ROOT, " %.1fs", ticks / 20.0)).formatted(Formatting.GRAY));
	}

	private static Text clottedStatus(long ticks) {
		return Text.literal("✖ CLOTTED  ").formatted(Formatting.DARK_RED)
			.append(Text.literal("abilities suppressed").formatted(Formatting.GRAY))
			.append(Text.literal(String.format(Locale.ROOT, " %.1fs", ticks / 20.0)).formatted(Formatting.RED));
	}

	public static void forget(UUID playerId) {
		FLASH_UNTIL.remove(playerId);
	}

	public static void clearAll() {
		FLASH_UNTIL.clear();
	}
}
