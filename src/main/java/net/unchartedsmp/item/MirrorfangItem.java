package net.unchartedsmp.item;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;
import net.unchartedsmp.ability.Ability;
import net.unchartedsmp.ability.Cooldowns;
import net.unchartedsmp.ability.TickScheduler;
import net.unchartedsmp.fx.BloodFx;
import net.unchartedsmp.util.Targeting;

/**
 * Blood Mirrorfang: conjures a blood-mirror of you for 7s that slashes the nearest enemy
 * within 3.5 blocks every half second.
 *
 * <h2>Dupe fixes</h2>
 * The mirror is an armor stand wearing a copy of the caster's gear. The old version:
 * <ul>
 *   <li>copied the <i>full</i> stacks (enchants, shulker contents, everything) and let anyone
 *       right-click the stand to take them - a free, repeatable item dupe;</li>
 *   <li>left the stand (and its copied gear) in the world forever if the chunk unloaded or the
 *       server stopped during those 7 seconds.</li>
 * </ul>
 * Now: the stand only wears bare display copies, every interaction with it is cancelled, and any
 * mirror that isn't live in this session is deleted on sight (chunk load, server stop).
 */
public class MirrorfangItem extends AbilityWeapon {
	public static final String MIRROR_TAG = "unchartedsmp.blood_mirror";
	private static final int LIFETIME_TICKS = 140;
	private static final int ATTACK_INTERVAL_TICKS = 10;
	private static final double ATTACK_RANGE = 3.5;
	private static final float ATTACK_DAMAGE = 4.0F;
	private static final EquipmentSlot[] MIRRORED_SLOTS = {
		EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.MAINHAND
	};

	/** Mirrors spawned this session and still alive, by entity UUID. */
	private static final Map<UUID, ArmorStandEntity> LIVE_MIRRORS = new HashMap<>();

	public MirrorfangItem(Settings settings) {
		super(settings, Ability.MIRRORFANG);
	}

	@Override
	protected ActionResult useAbility(ServerWorld world, ServerPlayerEntity player) {
		if (!Cooldowns.checkReady(player, ability)) {
			return ActionResult.FAIL;
		}
		Vec3d spawn = player.getEntityPos().add(1.0, 0.0, 1.0);
		ArmorStandEntity mirror = new ArmorStandEntity(world, spawn.x, spawn.y, spawn.z);
		if (!Targeting.fitsAt(world, mirror, spawn)) {
			spawn = player.getEntityPos();
		}
		mirror.refreshPositionAndAngles(spawn.x, spawn.y, spawn.z, player.getYaw(), 0.0F);
		mirror.setNoGravity(true);
		mirror.setInvulnerable(true);
		mirror.setShowArms(true);
		mirror.setCustomName(Text.literal("Blood Mirror of " + player.getName().getString()).formatted(Formatting.DARK_RED));
		mirror.setCustomNameVisible(true);
		mirror.addCommandTag(MIRROR_TAG);
		for (EquipmentSlot slot : MIRRORED_SLOTS) {
			ItemStack worn = player.getEquippedStack(slot);
			if (!worn.isEmpty()) {
				// Bare item only: no enchantments, no container contents, no custom data.
				mirror.equipStack(slot, new ItemStack(worn.getItem()));
			}
		}

		// Register before spawning: spawning fires ENTITY_LOAD, which purges unknown mirrors.
		LIVE_MIRRORS.put(mirror.getUuid(), mirror);
		if (!world.spawnEntity(mirror)) {
			LIVE_MIRRORS.remove(mirror.getUuid());
			return ActionResult.FAIL;
		}
		Cooldowns.start(player, ability);

		Vec3d mirrorChest = Targeting.chest(mirror);
		BloodFx.burst(world, BloodFx.BLOOD_LARGE, mirrorChest, 25, 0.4);
		BloodFx.play(world, mirror, BloodFx.MIRROR, 1.0F, 1.2F);

		TickScheduler.repeat(1, 1, LIFETIME_TICKS, tick -> {
			if (!mirror.isAlive()) {
				LIVE_MIRRORS.remove(mirror.getUuid());
				return false;
			}
			if (tick % ATTACK_INTERVAL_TICKS == ATTACK_INTERVAL_TICKS - 1) {
				LivingEntity target = nearestEnemy(world, mirror, player);
				if (target != null) {
					target.damage(world, world.getDamageSources().mobAttack(mirror), ATTACK_DAMAGE);
					BloodFx.burst(world, BloodFx.SWEEP, Targeting.chest(target), 1, 0.0);
					BloodFx.splash(world, Targeting.chest(target), 3);
				}
			}
			if (tick == LIFETIME_TICKS - 1) {
				dismiss(mirror);
				return false;
			}
			if (tick % 5 == 0) {
				BloodFx.burst(world, BloodFx.BLOOD, Targeting.chest(mirror), 3, 0.2);
				BloodFx.burst(world, BloodFx.DRIP, Targeting.chest(mirror), 1, 0.25, 0.0);
			}
			return true;
		});
		return ActionResult.SUCCESS;
	}

	private static LivingEntity nearestEnemy(ServerWorld world, ArmorStandEntity mirror, ServerPlayerEntity owner) {
		LivingEntity nearest = null;
		double nearestDistance = Double.MAX_VALUE;
		for (LivingEntity candidate : Targeting.livingInRadius(world, mirror.getEntityPos(), ATTACK_RANGE, owner)) {
			double distance = candidate.squaredDistanceTo(mirror.getEntityPos());
			if (distance < nearestDistance) {
				nearestDistance = distance;
				nearest = candidate;
			}
		}
		return nearest;
	}

	private static void dismiss(ArmorStandEntity mirror) {
		LIVE_MIRRORS.remove(mirror.getUuid());
		if (mirror.getEntityWorld() instanceof ServerWorld world) {
			BloodFx.splash(world, Targeting.chest(mirror), 8);
		}
		mirror.discard();
	}

	// ---- dupe guards (wired up in UnchartedSMP) ---------------------------------------------

	public static boolean isMirror(Entity entity) {
		return entity instanceof ArmorStandEntity && entity.getCommandTags().contains(MIRROR_TAG);
	}

	/** Right-click on a mirror: always refused, so its gear can never be taken or swapped. */
	public static ActionResult onInteract(Entity entity) {
		return isMirror(entity) ? ActionResult.FAIL : ActionResult.PASS;
	}

	/** A mirror loaded from disk (chunk reload, restart, crash) is always stale: delete it. */
	public static void onEntityLoad(Entity entity) {
		if (isMirror(entity) && !LIVE_MIRRORS.containsKey(entity.getUuid())) {
			// Deferred a tick: removing an entity from inside its own load callback is unsafe.
			TickScheduler.schedule(0, entity::discard);
		}
	}

	/** A live mirror whose chunk unloads stops being live; it'll be purged if it ever loads again. */
	public static void onEntityUnload(Entity entity) {
		if (isMirror(entity)) {
			LIVE_MIRRORS.remove(entity.getUuid());
		}
	}

	/** Server stopping: remove every live mirror before the worlds are saved. */
	public static void discardAll() {
		for (ArmorStandEntity mirror : new ArrayList<>(LIVE_MIRRORS.values())) {
			mirror.discard();
		}
		LIVE_MIRRORS.clear();
	}
}
