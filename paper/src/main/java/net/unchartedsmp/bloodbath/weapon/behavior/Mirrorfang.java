package net.unchartedsmp.bloodbath.weapon.behavior;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.unchartedsmp.bloodbath.Keys;
import net.unchartedsmp.bloodbath.ability.Cooldowns;
import net.unchartedsmp.bloodbath.ability.ServerClock;
import net.unchartedsmp.bloodbath.ability.TickScheduler;
import net.unchartedsmp.bloodbath.fx.BloodFx;
import net.unchartedsmp.bloodbath.hud.Hud;
import net.unchartedsmp.bloodbath.util.Damage;
import net.unchartedsmp.bloodbath.util.Targeting;
import net.unchartedsmp.bloodbath.weapon.WeaponBehavior;
import net.unchartedsmp.bloodbath.weapon.WeaponType;
import net.unchartedsmp.bloodbath.weapon.Weapons;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.EulerAngle;
import org.bukkit.util.Vector;

/**
 * Blood Mirrorfang: conjures a blood mirror of you for 6s that hunts the nearest enemy within 10
 * blocks, gliding after it (slower than a sprint, so it can be outrun) and slashing it every
 * 0.7s once it's within 3 blocks.
 *
 * <h2>Dupe safety</h2>
 * The mirror is an armor stand dressed like the caster. It only ever wears bare display copies
 * (no enchantments, no contents, no custom data), every slot is locked, every interaction and
 * all damage are cancelled, its death drops nothing, it is never saved to disk, and any mirror
 * that isn't live in this session is deleted on sight. See {@code MirrorGuard}.
 */
public final class Mirrorfang implements WeaponBehavior {
	/** How far the mirror glides toward its prey every few ticks, and how often. */
	private static final double GLIDE_STEP = 0.55;
	private static final int GLIDE_EVERY_TICKS = 4;
	private static final EulerAngle ARM_READY = new EulerAngle(Math.toRadians(-40), 0.0, Math.toRadians(-8));
	private static final EulerAngle ARM_RAISED = new EulerAngle(Math.toRadians(-125), Math.toRadians(-20), Math.toRadians(-15));
	private static final EulerAngle ARM_SLASH = new EulerAngle(Math.toRadians(-15), Math.toRadians(25), Math.toRadians(-5));
	private static final EquipmentSlot[] MIRRORED = {
		EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.HAND, EquipmentSlot.OFF_HAND
	};

	/** Mirrors spawned this session and still alive, by entity UUID. */
	private final Map<UUID, ArmorStand> live = new HashMap<>();
	/** Owner UUID → tick their current mirror dissolves, for the status line. */
	private final Map<UUID, Long> until = new HashMap<>();

	@Override
	public WeaponType type() {
		return WeaponType.MIRRORFANG;
	}

	@Override
	public void use(Player player) {
		if (!Cooldowns.checkReady(player, ability())) {
			return;
		}
		int interval = Math.max(4, ticksSetting("interval", 14));
		int lifetime = Math.max(interval, ticksSetting("duration", 120));
		double range = setting("range", 3.0);
		double damage = setting("damage", 3.0);
		double hunt = setting("hunt-range", 10.0);

		Location spawn = player.getLocation().add(rightOf(player).multiply(1.2));
		if (!Targeting.fitsAt(player, spawn)) {
			spawn = player.getLocation();
		}
		spawn.setPitch(0.0F);
		ArmorStand mirror = player.getWorld().spawn(spawn, ArmorStand.class, stand -> dress(stand, player));
		if (!mirror.isValid()) {
			live.remove(mirror.getUniqueId()); // another plugin cancelled the spawn
			return;
		}
		Cooldowns.start(player, ability());
		until.put(player.getUniqueId(), ServerClock.now() + lifetime);

		Location mirrorChest = BloodFx.chest(mirror);
		BloodFx.burst(mirrorChest, BloodFx.BLOOD_LARGE, 25, 0.4);
		BloodFx.flow(BloodFx.chest(player), mirrorChest, 8, 0.3, BloodFx.BRIGHT_RED, 8);
		BloodFx.play(mirror, BloodFx.MIRROR, 1.0F, 1.2F);

		TickScheduler.repeat(1, 1, lifetime, tick -> {
			if (!mirror.isValid()) {
				live.remove(mirror.getUniqueId());
				return false;
			}
			int phase = tick % interval;
			if (phase == interval - 3) {
				mirror.setRightArmPose(ARM_RAISED); // wind-up
			} else if (phase == interval - 1) {
				slash(mirror, player, range, damage);
			} else if (phase == 1) {
				mirror.setRightArmPose(ARM_READY);
			}
			if (tick % GLIDE_EVERY_TICKS == 0) {
				glide(mirror, player, range, hunt);
			}
			if (tick >= lifetime - 1) {
				dismiss(mirror);
				return false;
			}
			if (tick % 5 == 0) {
				Location chest = BloodFx.chest(mirror);
				BloodFx.burst(chest, BloodFx.BLOOD_FADE, 4, 0.3);
				BloodFx.burst(chest, BloodFx.DRIP, 1, 0.25, 0.0);
				BloodFx.ring(mirror.getLocation().add(0.0, 0.1, 0.0), BloodFx.BLOOD, range, 16);
			}
			return true;
		});
	}

	/** The mirror closes on the nearest enemy it can see, never through walls or into blocks. */
	private static void glide(ArmorStand mirror, Player owner, double range, double hunt) {
		LivingEntity prey = nearestEnemy(mirror, owner, hunt);
		if (prey == null) {
			return;
		}
		Location from = mirror.getLocation();
		Vector to = prey.getLocation().toVector().subtract(from.toVector());
		to.setY(0.0);
		double distance = to.length();
		mirror.setRotation((float) Math.toDegrees(Math.atan2(-to.getX(), to.getZ())), 0.0F);
		if (distance <= range * 0.8 || distance < 1.0E-3) {
			return;
		}
		Location next = from.clone().add(to.multiply(Math.min(GLIDE_STEP, distance - range * 0.8) / distance));
		next.setY(prey.getLocation().getY());
		if (Targeting.fitsAt(mirror, next) && Targeting.hasLineOfSight(BloodFx.chest(mirror), next.clone().add(0.0, 1.0, 0.0))) {
			BloodFx.burst(from.clone().add(0.0, 0.1, 0.0), BloodFx.BLOOD_FADE, 2, 0.15, 0.0);
			BloodFx.burst(from.clone().add(0.0, 0.9, 0.0), BloodFx.EMBER, 2, 0.25, 0.0);
			mirror.teleport(next);
		}
	}

	private void dress(ArmorStand stand, Player owner) {
		live.put(stand.getUniqueId(), stand);
		stand.setPersistent(false);
		stand.setInvulnerable(true);
		stand.setGravity(false);
		stand.setArms(true);
		stand.setBasePlate(false);
		stand.setSilent(true);
		stand.setRightArmPose(ARM_READY);
		stand.customName(Component.text("Blood Mirror of " + owner.getName(), NamedTextColor.DARK_RED));
		stand.setCustomNameVisible(true);
		stand.getPersistentDataContainer().set(Keys.MIRROR, PersistentDataType.STRING, owner.getUniqueId().toString());
		stand.setDisabledSlots(MIRRORED);
		EntityEquipment from = owner.getEquipment();
		EntityEquipment to = stand.getEquipment();
		for (EquipmentSlot slot : MIRRORED) {
			to.setItem(slot, Weapons.displayCopy(from.getItem(slot)));
		}
	}

	private void slash(ArmorStand mirror, Player owner, double range, double damage) {
		mirror.setRightArmPose(ARM_SLASH);
		LivingEntity target = nearestEnemy(mirror, owner, range);
		if (target == null) {
			return;
		}
		Location from = mirror.getLocation();
		Vector look = target.getLocation().toVector().subtract(from.toVector());
		mirror.setRotation((float) Math.toDegrees(Math.atan2(-look.getX(), look.getZ())), 0.0F);
		Location chest = BloodFx.chest(target);
		Damage.deal(target, damage, owner, type(), from);
		BloodFx.line(BloodFx.chest(mirror), chest, BloodFx.BLOOD_FADE, 4.0);
		BloodFx.burst(chest, BloodFx.SWEEP, 1, 0.0);
		BloodFx.play(target, BloodFx.FANGS, 0.5F, 1.6F);
		BloodFx.splash(chest, 3);
	}

	private static LivingEntity nearestEnemy(ArmorStand mirror, Player owner, double range) {
		LivingEntity nearest = null;
		double nearestDistance = Double.MAX_VALUE;
		Location at = mirror.getLocation();
		for (LivingEntity candidate : Targeting.livingInRadius(at, range, owner)) {
			double distance = candidate.getLocation().distanceSquared(at);
			if (distance < nearestDistance && Targeting.hasLineOfSight(BloodFx.chest(mirror), BloodFx.chest(candidate))) {
				nearestDistance = distance;
				nearest = candidate;
			}
		}
		return nearest;
	}

	private void dismiss(ArmorStand mirror) {
		live.remove(mirror.getUniqueId());
		long now = ServerClock.now();
		until.values().removeIf(tick -> tick <= now);
		BloodFx.splash(BloodFx.chest(mirror), 8);
		mirror.remove();
	}

	private static Vector rightOf(Player player) {
		double yaw = Math.toRadians(player.getLocation().getYaw());
		return new Vector(-Math.cos(yaw), 0.0, -Math.sin(yaw));
	}

	@Override
	public Component hud(Player player) {
		Long end = until.get(player.getUniqueId());
		long left = end == null ? 0 : end - ServerClock.now();
		if (left > 0) {
			return Hud.timer("♦ Blood Mirror fighting", left).append(Component.text("  ")).append(Hud.cooldownBar(player, ability()));
		}
		return Hud.cooldownBar(player, ability());
	}

	@Override
	public BloodFx.Fx auraAccent() {
		return BloodFx.BLOOD_FADE;
	}

	// ---- dupe guards (wired up in MirrorGuard) ----------------------------------------------

	public static boolean isMirror(Entity entity) {
		return entity instanceof ArmorStand && entity.getPersistentDataContainer().has(Keys.MIRROR, PersistentDataType.STRING);
	}

	public boolean isLive(Entity entity) {
		return live.containsKey(entity.getUniqueId());
	}

	/** A live mirror whose chunk unloads is gone for good (mirrors are never saved). */
	public void unloaded(Entity entity) {
		live.remove(entity.getUniqueId());
	}

	public int liveCount() {
		return live.size();
	}

	@Override
	public void forget(UUID playerId) {
		until.remove(playerId);
	}

	@Override
	public void prune() {
		long now = ServerClock.now();
		until.values().removeIf(tick -> tick <= now);
		live.values().removeIf(stand -> !stand.isValid());
	}

	/** Plugin disabling: remove every live mirror before the worlds are saved. */
	@Override
	public void shutdown() {
		for (ArmorStand mirror : new ArrayList<>(live.values())) {
			mirror.remove();
		}
		live.clear();
		until.clear();
	}
}
