package net.unchartedsmp.bloodbath.weapon.behavior;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
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
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.util.Vector;

/**
 * Vampire Fang: a quick dagger that drinks. Every hit heals you for part of the damage dealt
 * (25% by default). Right-click: Blood Dash, a lunge along your aim that cuts everything you pass
 * through and drinks from each of them.
 */
public final class VampireFang implements WeaponBehavior {
	private static final int DASH_TICKS = 8;
	private static final int THIRST_WINDOW_TICKS = 100;

	/** Recently drunk health per player, shown on the status line until they stop hitting things. */
	private record Thirst(double drunk, long until) {
	}

	private final Map<UUID, Thirst> thirst = new HashMap<>();

	@Override
	public WeaponType type() {
		return WeaponType.VAMPIRE_FANG;
	}

	@Override
	public void melee(Player player, LivingEntity target, double damage) {
		drink(player, target, damage * setting("lifesteal", 0.25));
	}

	private void drink(Player player, LivingEntity from, double amount) {
		if (amount <= 0.0 || player.isDead()) {
			return;
		}
		AttributeInstance max = player.getAttribute(Attribute.MAX_HEALTH);
		double room = (max == null ? 20.0 : max.getValue()) - player.getHealth();
		double healed = Math.min(amount, Math.max(0.0, room));
		if (healed > 0.0) {
			player.heal(healed, EntityRegainHealthEvent.RegainReason.CUSTOM);
		}
		long now = ServerClock.now();
		Thirst previous = thirst.get(player.getUniqueId());
		double total = (previous != null && now <= previous.until() ? previous.drunk() : 0.0) + healed;
		thirst.put(player.getUniqueId(), new Thirst(total, now + THIRST_WINDOW_TICKS));

		Location mouth = BloodFx.chest(player);
		BloodFx.flow(BloodFx.chest(from), mouth, 3, 0.2, BloodFx.BRIGHT_RED, 8);
		BloodFx.burst(mouth, BloodFx.HURT, 1, 0.2, 0.05);
		BloodFx.play(player, BloodFx.DRINK, 0.35F, 1.5F);
	}

	/** Blood Dash. */
	@Override
	public void use(Player player) {
		if (!Cooldowns.checkReady(player, ability())) {
			return;
		}
		Cooldowns.start(player, ability());
		World world = player.getWorld();
		Vector aim = player.getEyeLocation().getDirection();
		aim.setY(Math.max(-0.25, Math.min(0.45, aim.getY())));
		double speed = setting("dash-speed", 1.7);
		player.setVelocity(aim.normalize().multiply(speed).add(new Vector(0.0, 0.18, 0.0)));
		player.setFallDistance(0.0F);
		BloodFx.play(player, BloodFx.DASH, 0.9F, 1.2F);
		BloodFx.play(player, BloodFx.FANGS, 0.6F, 1.4F);
		BloodFx.burst(BloodFx.chest(player), BloodFx.BLOOD_LARGE, 18, 0.35);

		double damage = setting("dash-damage", 5.0);
		double lifesteal = setting("lifesteal", 0.25);
		Set<UUID> cut = new HashSet<>();
		TickScheduler.repeat(1, 1, DASH_TICKS, tick -> {
			if (!Targeting.stillIn(player, world)) {
				return false;
			}
			player.setFallDistance(0.0F);
			Location chest = BloodFx.chest(player);
			BloodFx.burst(chest, BloodFx.BLOOD_FADE, 6, 0.25);
			BloodFx.burst(chest, BloodFx.SPLATTER, 2, 0.2, 0.05);
			for (LivingEntity target : Targeting.livingInRadius(chest, 1.8, player)) {
				if (cut.add(target.getUniqueId())) {
					double before = health(target);
					Damage.deal(target, damage, player, type(), player.getLocation());
					BloodFx.splash(BloodFx.chest(target), 5);
					drink(player, target, Math.max(0.0, before - health(target)) * lifesteal * 2.0);
				}
			}
			return true;
		});
	}

	private static double health(LivingEntity entity) {
		return entity.isDead() ? 0.0 : entity.getHealth() + entity.getAbsorptionAmount();
	}

	@Override
	public Component hud(Player player) {
		Component line = Hud.cooldownBar(player, ability());
		Thirst drunk = thirst.get(player.getUniqueId());
		if (drunk != null && ServerClock.now() <= drunk.until() && drunk.drunk() > 0.0) {
			line = line.append(Component.text(String.format(Locale.ROOT, "  ❤ +%.1f drunk", drunk.drunk() / 2.0), NamedTextColor.RED));
		} else {
			line = line.append(Component.text("  hits drink blood", NamedTextColor.GRAY));
		}
		return line;
	}

	@Override
	public BloodFx.Fx auraAccent() {
		return BloodFx.HURT;
	}

	@Override
	public void forget(UUID playerId) {
		thirst.remove(playerId);
	}

	@Override
	public void prune() {
		long now = ServerClock.now();
		thirst.values().removeIf(entry -> entry.until() < now);
	}

	@Override
	public void shutdown() {
		thirst.clear();
	}
}
