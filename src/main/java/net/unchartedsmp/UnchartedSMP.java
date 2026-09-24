package net.unchartedsmp;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import net.unchartedsmp.ability.Cooldowns;
import net.unchartedsmp.ability.NullField;
import net.unchartedsmp.ability.ServerClock;
import net.unchartedsmp.ability.TickScheduler;
import net.unchartedsmp.fx.BloodFx;
import net.unchartedsmp.hud.Hud;
import net.unchartedsmp.item.BloodWeapon;
import net.unchartedsmp.item.BloodhookItem;
import net.unchartedsmp.item.ChronosItem;
import net.unchartedsmp.item.MeteorGauntletItem;
import net.unchartedsmp.item.MirrorfangItem;
import net.unchartedsmp.item.ModItems;
import net.unchartedsmp.item.RiftbladeItem;
import net.unchartedsmp.item.VoidScytheItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class UnchartedSMP implements ModInitializer {
	public static final String MOD_ID = "unchartedsmp";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	/** How often expired per-player state is swept out of memory (1 minute). */
	private static final int PRUNE_INTERVAL_TICKS = 1200;

	public static Identifier id(String path) {
		return Identifier.of(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		LOGGER.info("Initializing Uncharted SMP: Bloodbath");
		ModItems.registerAll();
		// Resolved once vanilla registries are guaranteed to be bootstrapped.
		ServerLifecycleEvents.SERVER_STARTING.register(server -> BloodFx.init());
		ServerClock.register();
		TickScheduler.register();
		Hud.register();

		AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> {
			if (player.getStackInHand(hand).getItem() instanceof MeteorGauntletItem) {
				MeteorGauntletItem.onBlockPunched(player, world, pos);
			}
			return ActionResult.PASS;
		});

		// Blood Mirror dupe guards - see MirrorfangItem.
		UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> MirrorfangItem.onInteract(entity));
		ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> MirrorfangItem.onEntityLoad(entity));
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, world) -> MirrorfangItem.onEntityUnload(entity));

		// Kills with a Bloodbath weapon (melee, abilities or Paradox Bow arrows) burst and bleed
		// into the killer.
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (source.getAttacker() instanceof ServerPlayerEntity killer
				&& killer.getMainHandStack().getItem() instanceof BloodWeapon
				&& entity.getEntityWorld() instanceof ServerWorld world) {
				BloodFx.killBurst(world, entity, killer);
			}
		});

		// Short-lived personal ability state is dropped on disconnect. Cooldowns and clot debuffs
		// are NOT, otherwise relogging would reset them.
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			var playerId = handler.player.getUuid();
			RiftbladeItem.forget(playerId);
			BloodhookItem.forget(playerId);
			ChronosItem.forget(playerId);
			Hud.forget(playerId);
		});

		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (ServerClock.now() % PRUNE_INTERVAL_TICKS == 0) {
				pruneExpired();
			}
		});

		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			MirrorfangItem.discardAll();
			clearAll();
		});
	}

	private static void pruneExpired() {
		Cooldowns.prune();
		NullField.prune();
		RiftbladeItem.prune();
		BloodhookItem.prune();
		ChronosItem.prune();
		VoidScytheItem.prune();
	}

	private static void clearAll() {
		TickScheduler.clearAll();
		Cooldowns.clearAll();
		NullField.clearAll();
		RiftbladeItem.clearAll();
		BloodhookItem.clearAll();
		ChronosItem.clearAll();
		VoidScytheItem.clearAll();
		Hud.clearAll();
	}
}
