package net.unchartedsmp.bloodbath.weapon.behavior;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.unchartedsmp.bloodbath.ability.Cooldowns;
import net.unchartedsmp.bloodbath.ability.ServerClock;
import net.unchartedsmp.bloodbath.ability.TickScheduler;
import net.unchartedsmp.bloodbath.fx.BloodFx;
import net.unchartedsmp.bloodbath.hud.Hud;
import net.unchartedsmp.bloodbath.util.Damage;
import net.unchartedsmp.bloodbath.util.Targeting;
import net.unchartedsmp.bloodbath.weapon.WeaponBehavior;
import net.unchartedsmp.bloodbath.weapon.WeaponType;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * Blood Grimoire: blood magic from a book.
 * <ul>
 *   <li><b>Right-click a creature</b> (12 blocks, line of sight): Transfusion. A tether of blood
 *       drains 6 health from it over 1.5s and pours it into you.</li>
 *   <li><b>Sneak + right-click a player</b>: give them your blood instead. You pay 4 health (never
 *       below 1 heart) and they heal 8 plus a few seconds of Regeneration. Works on PvP-off worlds.</li>
 * </ul>
 * With nothing in reach it costs nothing.
 */
public final class BloodGrimoire implements WeaponBehavior {
	private static final int PULSES = 3;
	private static final int PULSE_INTERVAL_TICKS = 10;
	private static final double MIN_HEALTH_AFTER_GIFT = 2.0;

	/** Caster → the channel in progress, for the status line. */
	private record Channel(LivingEntity target, long endsAt) {
	}

	private final Map<UUID, Channel> channels = new HashMap<>();

	@Override
	public WeaponType type() {
		return WeaponType.BLOOD_GRIMOIRE;
	}

	@Override
	public void use(Player player) {
		if (!Cooldowns.checkReady(player, ability())) {
			return;
		}
		double range = setting("range", 12.0);
		if (player.isSneaking()) {
			LivingEntity ally = Targeting.lookEntity(player, range,
				target -> target instanceof Player other && other.getGameMode() != GameMode.SPECTATOR && !other.isDead());
			if (ally instanceof Player friend) {
				give(player, friend);
			} else {
				Hud.flash(player, Component.text("Look at a player to give them your blood.", NamedTextColor.GRAY));
			}
			return;
		}
		LivingEntity victim = Targeting.lookEntity(player, range);
		if (victim == null) {
			Hud.flash(player, Component.text("No blood within reach.", NamedTextColor.GRAY));
			return;
		}
		drain(player, victim, range);
	}

	private void drain(Player caster, LivingEntity victim, double range) {
		Cooldowns.start(caster, ability());
		double perPulse = setting("drain", 6.0) / PULSES;
		double leash = (range + 4.0) * (range + 4.0);
		World world = caster.getWorld();
		channels.put(caster.getUniqueId(), new Channel(victim, ServerClock.now() + PULSES * PULSE_INTERVAL_TICKS));
		BloodFx.play(caster, BloodFx.PAGE, 1.0F, 0.6F);
		BloodFx.play(victim, BloodFx.HEARTBEAT, 1.0F, 1.2F);

		TickScheduler.repeat(0, 1, PULSES * PULSE_INTERVAL_TICKS, tick -> {
			if (!Targeting.stillIn(caster, world) || !Targeting.stillIn(victim, world)
				|| caster.getLocation().distanceSquared(victim.getLocation()) > leash
				|| !Targeting.hasLineOfSight(caster.getEyeLocation(), BloodFx.chest(victim))) {
				channels.remove(caster.getUniqueId());
				BloodFx.splash(BloodFx.chest(victim), 3);
				return false;
			}
			Location book = Hud.handPos(caster, false);
			Location heart = BloodFx.chest(victim);
			if (tick % 2 == 0) {
				BloodFx.line(heart, book, BloodFx.BLOOD, 2.5);
			}
			BloodFx.flow(heart, book, 2, 0.25, tick % 4 == 0 ? BloodFx.BRIGHT_RED : BloodFx.BLOOD_RED, 8);
			if (tick % PULSE_INTERVAL_TICKS == PULSE_INTERVAL_TICKS - 1) {
				double before = health(victim);
				Damage.deal(victim, perPulse, caster, type(), caster.getLocation());
				double taken = Math.max(0.0, before - health(victim));
				heal(caster, taken);
				BloodFx.splash(heart, 3);
				BloodFx.burst(BloodFx.chest(caster), BloodFx.HURT, 2, 0.3, 0.05);
				BloodFx.play(caster, BloodFx.DRINK, 0.5F, 0.9F);
			}
			if (tick == PULSES * PULSE_INTERVAL_TICKS - 1) {
				channels.remove(caster.getUniqueId());
			}
			return true;
		});
	}

	private void give(Player caster, Player friend) {
		double cost = setting("gift-cost", 4.0);
		if (caster.getHealth() - cost < MIN_HEALTH_AFTER_GIFT) {
			Hud.flash(caster, Component.text("You don't have enough blood to give.", NamedTextColor.DARK_RED));
			return;
		}
		Cooldowns.start(caster, ability());
		caster.setHealth(Math.max(MIN_HEALTH_AFTER_GIFT, caster.getHealth() - cost));
		caster.playHurtAnimation(0.0F);
		heal(friend, setting("gift-heal", 8.0));
		int regen = ticksSetting("gift-regeneration", 80);
		if (regen > 0) {
			friend.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, regen, 0, true, true, true));
		}
		Location from = BloodFx.chest(caster);
		Location to = BloodFx.chest(friend);
		BloodFx.flow(from, to, 16, 0.3, BloodFx.BRIGHT_RED, 12);
		BloodFx.line(from, to, BloodFx.BLOOD_FADE, 3.0);
		BloodFx.burst(to, BloodFx.BLOOD_FADE, 20, 0.4);
		BloodFx.ring(friend.getLocation().add(0.0, 0.1, 0.0), BloodFx.BLOOD_FADE, 0.9, 16);
		BloodFx.play(caster, BloodFx.PAGE, 1.0F, 1.2F);
		BloodFx.play(friend, BloodFx.READY, 0.6F, 1.4F);
		Hud.flash(caster, Component.text("You gave " + friend.getName() + " your blood.", NamedTextColor.RED));
		Hud.flash(friend, Component.text(caster.getName() + " gave you their blood.", NamedTextColor.RED));
	}

	private static void heal(LivingEntity entity, double amount) {
		if (amount <= 0.0 || entity.isDead()) {
			return;
		}
		AttributeInstance max = entity.getAttribute(Attribute.MAX_HEALTH);
		double room = (max == null ? 20.0 : max.getValue()) - entity.getHealth();
		if (room > 0.0) {
			entity.heal(Math.min(amount, room), EntityRegainHealthEvent.RegainReason.MAGIC);
		}
	}

	private static double health(LivingEntity entity) {
		return entity.isDead() ? 0.0 : entity.getHealth() + entity.getAbsorptionAmount();
	}

	@Override
	public Component hud(Player player) {
		Channel channel = channels.get(player.getUniqueId());
		long left = channel == null ? 0 : channel.endsAt() - ServerClock.now();
		if (left > 0) {
			float progress = 1.0F - left / (float) (PULSES * PULSE_INTERVAL_TICKS);
			return Component.text("✚ Transfusion  ", NamedTextColor.DARK_RED).append(Hud.bar(progress))
				.append(Component.text("  draining " + name(channel.target()), NamedTextColor.GRAY));
		}
		Component line = Hud.cooldownBar(player, ability());
		return Cooldowns.isReady(player, ability())
			? line.append(Component.text("  sneak to give", NamedTextColor.GRAY))
			: line;
	}

	private static String name(LivingEntity entity) {
		return entity instanceof Player player ? player.getName() : entity.getName();
	}

	@Override
	public BloodFx.Fx auraAccent() {
		return BloodFx.GLYPH;
	}

	@Override
	public void forget(UUID playerId) {
		channels.remove(playerId);
	}

	@Override
	public void prune() {
		long now = ServerClock.now();
		channels.values().removeIf(channel -> channel.endsAt() < now);
	}

	@Override
	public void shutdown() {
		channels.clear();
	}
}
