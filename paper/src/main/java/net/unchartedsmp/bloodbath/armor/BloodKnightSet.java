package net.unchartedsmp.bloodbath.armor;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.unchartedsmp.bloodbath.ability.Ability;
import net.unchartedsmp.bloodbath.ability.Cooldowns;
import net.unchartedsmp.bloodbath.ability.NullField;
import net.unchartedsmp.bloodbath.ability.TickScheduler;
import net.unchartedsmp.bloodbath.config.Settings;
import net.unchartedsmp.bloodbath.fx.BloodFx;
import net.unchartedsmp.bloodbath.hud.Hud;
import net.unchartedsmp.bloodbath.util.Targeting;
import net.unchartedsmp.bloodbath.weapon.Gate;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * The Blood Knight set bonuses.
 * <ul>
 *   <li><b>2 pieces, Bloodlust:</b> every kill heals you (1.5 hearts by default).</li>
 *   <li><b>4 pieces, Blood Rage:</b> when a hit leaves you at 40% health or less, you get Strength
 *       and Resistance for 8s and a blood shockwave hurls everything nearby away. 60s cooldown;
 *       a clot (Clotblade) suppresses it like any ability.</li>
 * </ul>
 * A full set also drips blood, so everyone can see who's wearing it.
 */
public final class BloodKnightSet implements Listener {
	private static final int AURA_INTERVAL_TICKS = 10;

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onKill(EntityDeathEvent event) {
		Settings settings = Settings.get();
		if (!settings.armorEnabled || settings.armorKillHeal <= 0.0
			|| !(event.getDamageSource().getCausingEntity() instanceof Player killer) || killer == event.getEntity()
			|| killer.isDead() || BloodArmor.worn(killer) < 2) {
			return;
		}
		AttributeInstance max = killer.getAttribute(Attribute.MAX_HEALTH);
		double room = (max == null ? 20.0 : max.getValue()) - killer.getHealth();
		if (room > 0.0) {
			killer.heal(Math.min(room, settings.armorKillHeal), EntityRegainHealthEvent.RegainReason.CUSTOM);
		}
		BloodFx.flow(BloodFx.chest(event.getEntity()), BloodFx.chest(killer), 4, 0.3, BloodFx.BRIGHT_RED, 10);
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onHurt(EntityDamageEvent event) {
		if (!(event.getEntity() instanceof Player player) || !Settings.get().armorEnabled) {
			return;
		}
		double left = player.getHealth() - event.getFinalDamage();
		if (left <= 0.0 || left > threshold(player) || !Cooldowns.isReady(player, Ability.BLOOD_RAGE)
			|| BloodArmor.worn(player) < 4) {
			return;
		}
		// After the hit has landed (and outside the damage event, which the shockwave would re-enter).
		TickScheduler.schedule(0, () -> rage(player));
	}

	private static double threshold(Player player) {
		AttributeInstance max = player.getAttribute(Attribute.MAX_HEALTH);
		return (max == null ? 20.0 : max.getValue()) * Settings.get().rageThreshold;
	}

	private static void rage(Player player) {
		if (!player.isValid() || player.isDead() || !Cooldowns.isReady(player, Ability.BLOOD_RAGE) || BloodArmor.worn(player) < 4
			|| player.getGameMode() == GameMode.SPECTATOR || !Settings.get().abilitiesAllowedIn(player.getWorld())
			|| !player.hasPermission(Gate.USE_PERMISSION)) {
			return;
		}
		if (NullField.isNullified(player)) {
			NullField.notifyNullified(player);
			return;
		}
		Settings settings = Settings.get();
		Cooldowns.start(player, Ability.BLOOD_RAGE);
		int duration = settings.rageDurationTicks;
		player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, duration, 0, false, true, true));
		player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, duration, 0, false, true, true));
		Location center = player.getLocation();
		for (LivingEntity target : Targeting.livingInRadius(center, settings.rageRadius, player)) {
			Targeting.launchOutward(target, center, 1.3, 0.6);
		}
		Location chest = BloodFx.chest(player);
		BloodFx.burst(chest, BloodFx.BURST, 1, 0.0);
		BloodFx.splash(chest, 10);
		BloodFx.spray(chest, settings.rageRadius, 18, 10);
		BloodFx.ring(center.clone().add(0.0, 0.1, 0.0), BloodFx.CLOT, settings.rageRadius, 32);
		BloodFx.play(player, BloodFx.ROAR, 0.8F, 1.3F);
		BloodFx.play(player, BloodFx.HEARTBEAT, 1.2F, 0.8F);
		Hud.flash(player, Component.text("☠ BLOOD RAGE", NamedTextColor.RED));
		// The rage visibly boils off the wearer while it lasts.
		TickScheduler.repeat(5, 5, duration / 5, tick -> {
			if (!player.isValid() || player.isDead()) {
				return false;
			}
			BloodFx.burst(BloodFx.chest(player), BloodFx.BLOOD_FADE, 4, 0.35);
			return true;
		});
	}

	/** A full set drips blood (cheap reject on the helmet slot first). */
	public static void tick(long now) {
		if (now % AURA_INTERVAL_TICKS != 0 || !Settings.get().armorEnabled || !Settings.get().heldAura) {
			return;
		}
		for (Player player : Bukkit.getOnlinePlayers()) {
			if (player.getInventory().getHelmet() == null || player.getInventory().getHelmet().getType() != Material.NETHERITE_HELMET
				|| player.isDead() || BloodArmor.worn(player) < 4) {
				continue;
			}
			Location at = player.getLocation().add(0.0, 0.9, 0.0);
			BloodFx.ambient(at, BloodFx.DRIP, 2, 0.3);
			if (now % (AURA_INTERVAL_TICKS * 3) == 0) {
				BloodFx.ambient(at, BloodFx.BLOOD_FADE, 2, 0.35);
			}
		}
	}
}
