package net.unchartedsmp.bloodbath.weapon.behavior;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.unchartedsmp.bloodbath.ability.Cooldowns;
import net.unchartedsmp.bloodbath.ability.NullField;
import net.unchartedsmp.bloodbath.ability.ServerClock;
import net.unchartedsmp.bloodbath.ability.TickScheduler;
import net.unchartedsmp.bloodbath.fx.BloodFx;
import net.unchartedsmp.bloodbath.hud.Hud;
import net.unchartedsmp.bloodbath.util.Damage;
import net.unchartedsmp.bloodbath.util.Targeting;
import net.unchartedsmp.bloodbath.weapon.WeaponBehavior;
import net.unchartedsmp.bloodbath.weapon.WeaponType;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;

/**
 * Sanguine Paradox Bow: a real bow (the client animates the draw, it shoots your arrows and takes
 * bow enchantments). A <b>fully drawn</b> shot also leaves a Paradox Echo: 3s later the shot's echo
 * tears back along the same path to where you stood, dealing 7 damage to everything it passes
 * through, once per target.
 *
 * <p>While drawing, blood visibly gathers into the nocked arrow; at full draw there's a click and,
 * if the echo is off cooldown, a heartbeat and a glow to show it's primed. Arrows from the bow
 * leave a blood trail.
 */
public final class ParadoxBow implements WeaponBehavior {
	private static final int FULL_DRAW_TICKS = 20;
	private static final int ECHO_STEPS = 16;
	private static final double HIT_SIZE = 1.3;
	private static final int TRAIL_MAX_TICKS = 60;

	private record Trail(AbstractArrow arrow, long until) {
	}

	private record Draw(Player player, long clickedAt) {
	}

	/** Players drawing this bow, from their right-click until the arrow leaves (or they stop). */
	private final Map<UUID, Draw> drawing = new HashMap<>();
	private final List<Trail> trails = new ArrayList<>();

	@Override
	public WeaponType type() {
		return WeaponType.PARADOX_BOW;
	}

	/** Right-click with the bow: vanilla starts the draw, we start watching it. */
	public void startDrawing(Player player) {
		drawing.put(player.getUniqueId(), new Draw(player, ServerClock.now()));
	}

	@Override
	public void tick(long now) {
		if (!drawing.isEmpty()) {
			Iterator<Draw> it = drawing.values().iterator();
			while (it.hasNext()) {
				Draw draw = it.next();
				Player player = draw.player();
				if (!isDrawing(player)) {
					// The server starts using the bow just after the click; give it a moment.
					if (!player.isOnline() || now - draw.clickedAt() > 2) {
						it.remove();
					}
					continue;
				}
				drawEffects(player, player.getActiveItemUsedTime());
			}
		}
		if (!trails.isEmpty()) {
			Iterator<Trail> it = trails.iterator();
			while (it.hasNext()) {
				Trail trail = it.next();
				AbstractArrow arrow = trail.arrow();
				if (!arrow.isValid() || arrow.isInBlock() || now > trail.until()) {
					it.remove();
					continue;
				}
				Location at = arrow.getLocation();
				BloodFx.burst(at, BloodFx.BLOOD_FADE, 2, 0.02, 0.0);
				if (now % 3 == 0) {
					BloodFx.burst(at, BloodFx.DRIP, 1, 0.02, 0.0);
				}
			}
		}
	}

	private void drawEffects(Player player, int drawn) {
		Location nock = nockPos(player);
		boolean primed = echoAvailable(player);
		if (drawn < FULL_DRAW_TICKS) {
			if (drawn % 3 == 0) {
				BloodFx.gather(nock, 1.8 - drawn * 0.06, 3, 6);
			}
		} else if (drawn == FULL_DRAW_TICKS) {
			BloodFx.play(player, BloodFx.BOW_DRAWN, 0.8F, primed ? 0.6F : 1.3F);
			if (primed) {
				BloodFx.play(player, BloodFx.HEARTBEAT, 0.7F, 1.4F);
				BloodFx.burst(nock, BloodFx.BLOOD_FADE, 14, 0.2);
			}
		} else if (primed && drawn % 5 == 0) {
			BloodFx.burst(nock, BloodFx.BLOOD, 3, 0.1);
			BloodFx.burst(nock, BloodFx.DRIP, 1, 0.1, 0.0);
		}
	}

	private static boolean isDrawing(Player player) {
		return player.isOnline() && player.hasActiveItem() && player.getActiveItem().getType() == Material.BOW;
	}

	private boolean echoAvailable(Player player) {
		return Cooldowns.isReady(player, ability()) && !NullField.isNullified(player);
	}

	private static Location nockPos(Player player) {
		Location eye = player.getEyeLocation();
		return eye.add(eye.getDirection().multiply(0.9)).add(0.0, -0.2, 0.0);
	}

	/** Vanilla's bow power curve: 0..1 over 20 ticks. */
	private static float pull(int ticks) {
		float t = ticks / 20.0F;
		return Math.min(1.0F, (t * t + t * 2.0F) / 3.0F);
	}

	/** Any arrow shot from the bow. */
	public void arrowShot(AbstractArrow arrow) {
		trails.add(new Trail(arrow, ServerClock.now() + TRAIL_MAX_TICKS));
	}

	/**
	 * A fully drawn shot (the caller checked permission, world and that it's enabled): releases
	 * the echo if it's off cooldown and the shooter isn't clotted.
	 */
	public void fullDrawShot(Player player) {
		drawing.remove(player.getUniqueId());
		if (NullField.isNullified(player)) {
			NullField.notifyNullified(player);
			return;
		}
		if (!Cooldowns.isReady(player, ability())) {
			return; // a normal shot: the bow is still a bow while the echo recharges
		}
		Cooldowns.start(player, ability());
		releaseEcho(player);
	}

	private void releaseEcho(Player player) {
		double damage = setting("damage", 7.0);
		int delay = Math.max(10, ticksSetting("delay", 60));
		World world = player.getWorld();
		Location start = player.getEyeLocation();
		Location finish = Targeting.lookTarget(player, setting("range", 24.0));
		BloodFx.flow(start, finish, 6, 0.05, BloodFx.BRIGHT_RED, 10);
		BloodFx.burst(finish, BloodFx.BLOOD_LARGE, 15, 0.25);
		BloodFx.play(player, BloodFx.BOW_RELEASE, 1.0F, 0.6F);
		Hud.flash(player, Component.text("⧖ Paradox Echo returns in " + Math.round(delay / 20.0) + "s", NamedTextColor.RED));

		// Countdown at the arrow's resting point: a shrinking ring and a tick each second.
		int pulses = delay / 10 - 1;
		TickScheduler.repeat(10, 10, pulses, tick -> {
			double radius = 1.2 * (1.0 - tick / (double) Math.max(1, pulses));
			BloodFx.ring(finish, BloodFx.BLOOD_FADE, Math.max(0.2, radius), 12);
			BloodFx.burst(finish, BloodFx.DRIP, 2, 0.15, 0.0);
			if (tick % 2 == 1) {
				BloodFx.play(finish, BloodFx.CLOCK_TICK, 0.8F, 0.6F + tick * 0.1F);
			}
			return true;
		});

		TickScheduler.schedule(delay, () -> {
			BloodFx.play(finish, BloodFx.BOW_ECHO, 0.6F, 1.5F);
			BloodFx.flow(finish, start, 10, 0.1, BloodFx.BRIGHT_RED, ECHO_STEPS);
			Set<UUID> alreadyHit = new HashSet<>();
			TickScheduler.repeat(0, 1, ECHO_STEPS, step -> {
				double t = step / (double) (ECHO_STEPS - 1);
				Location p = finish.clone().add(start.clone().subtract(finish).toVector().multiply(t));
				BloodFx.burst(p, BloodFx.BLOOD_FADE, 4, 0.1);
				BloodFx.burst(p, BloodFx.SPLATTER, 1, 0.05, 0.1);
				BoundingBox box = BoundingBox.of(p, HIT_SIZE / 2, HIT_SIZE / 2, HIT_SIZE / 2);
				for (var entity : world.getNearbyEntities(box, e -> e instanceof LivingEntity && e != player)) {
					LivingEntity target = (LivingEntity) entity;
					if (Targeting.validTarget(player, target) && alreadyHit.add(target.getUniqueId())) {
						Damage.deal(target, damage, player, type(), p);
						BloodFx.splash(BloodFx.chest(target), 5);
					}
				}
				return true;
			});
		});
	}

	@Override
	public Component hud(Player player) {
		if (drawing.containsKey(player.getUniqueId()) && isDrawing(player)) {
			float pull = pull(player.getActiveItemUsedTime());
			Component line = Component.text("Draw  ", NamedTextColor.DARK_RED).append(Hud.bar(pull));
			if (pull >= 1.0F) {
				line = line.append(echoAvailable(player)
					? Component.text("  ● ECHO PRIMED", NamedTextColor.RED)
					: Component.text("  full draw", NamedTextColor.GRAY));
			}
			return line;
		}
		Component line = Hud.cooldownBar(player, ability());
		return Cooldowns.isReady(player, ability())
			? line.append(Component.text("  full draw releases it", NamedTextColor.GRAY))
			: line;
	}

	@Override
	public BloodFx.Fx auraAccent() {
		return BloodFx.BLOOD_FADE;
	}

	@Override
	public void forget(UUID playerId) {
		drawing.remove(playerId);
	}

	@Override
	public void shutdown() {
		drawing.clear();
		trails.clear();
	}
}
