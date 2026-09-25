package net.unchartedsmp.bloodbath.armor;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import net.unchartedsmp.bloodbath.ability.Ability;
import net.unchartedsmp.bloodbath.ability.Cooldowns;
import net.unchartedsmp.bloodbath.ability.NullField;
import net.unchartedsmp.bloodbath.ability.ServerClock;
import net.unchartedsmp.bloodbath.ability.TickScheduler;
import net.unchartedsmp.bloodbath.config.Settings;
import net.unchartedsmp.bloodbath.fx.BloodFx;
import net.unchartedsmp.bloodbath.fx.Shapes;
import net.unchartedsmp.bloodbath.hud.Hud;
import net.unchartedsmp.bloodbath.util.Damage;
import net.unchartedsmp.bloodbath.util.Targeting;
import net.unchartedsmp.bloodbath.weapon.Gate;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.WorldBorder;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * The Blood Knight set bonuses.
 * <ul>
 *   <li><b>2 pieces, Bloodlust:</b> every kill heals you (1.5 hearts by default).</li>
 *   <li><b>3 pieces, Barbed Blood:</b> whoever hits you in melee takes damage back (1 heart).</li>
 *   <li><b>4 pieces, Blood Rage:</b> when a hit leaves you at 40% health or less, you get Strength
 *       and Resistance for 8s and a blood shockwave hurls everything nearby away. The screen
 *       edges run red and your heart pounds until it fades. 60s cooldown; a clot (Clotblade)
 *       suppresses it like any ability.</li>
 * </ul>
 * The Sabatons leave bloody footprints and a full set drips blood (both for everyone else to
 * see). With the full set on, the action bar carries the rage's state.
 */
public final class BloodKnightSet implements Listener {
	private static final int AURA_INTERVAL_TICKS = 10;
	private static final int STEP_INTERVAL_TICKS = 6;
	/** When each raging player's rage ends. */
	private static final Map<UUID, Long> RAGING = new HashMap<>();
	/** Where each Sabatons wearer last left a footprint. */
	private static final Map<UUID, Location> LAST_STEP = new HashMap<>();

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onKill(EntityDeathEvent event) {
		Settings settings = Settings.get();
		if (!settings.armorEnabled || settings.armorKillHeal <= 0.0
			|| !(event.getDamageSource().getCausingEntity() instanceof Player killer) || killer == event.getEntity()
			|| killer.isDead() || SetBonus.pieces(killer) < 2) {
			return;
		}
		AttributeInstance max = killer.getAttribute(Attribute.MAX_HEALTH);
		double room = (max == null ? 20.0 : max.getValue()) - killer.getHealth();
		if (room > 0.0) {
			killer.heal(Math.min(room, settings.armorKillHeal), EntityRegainHealthEvent.RegainReason.CUSTOM);
		}
		BloodFx.flow(BloodFx.chest(event.getEntity()), BloodFx.chest(killer), 4, 0.3, BloodFx.BRIGHT_RED, 10);
	}

	/** Barbed Blood: melee attackers take damage back. */
	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onStruck(EntityDamageByEntityEvent event) {
		Settings settings = Settings.get();
		if (Damage.isAbilityDamage() || event.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK
			|| !settings.armorEnabled || settings.armorBarbDamage <= 0.0
			|| !(event.getEntity() instanceof Player wearer) || !(event.getDamager() instanceof LivingEntity attacker)
			|| attacker == wearer || SetBonus.pieces(wearer) < 3 || !Targeting.validTarget(wearer, attacker)) {
			return;
		}
		// After this hit has finished (a hit inside the damage event would nest in it).
		TickScheduler.schedule(0, () -> {
			if (!attacker.isValid() || attacker.isDead() || !wearer.isValid()) {
				return;
			}
			Damage.deal(attacker, settings.armorBarbDamage, wearer, null, wearer.getLocation());
			BloodFx.burst(BloodFx.chest(attacker), BloodFx.SPLATTER, 8, 0.25, 0.1);
			BloodFx.play(attacker, BloodFx.BARBS, 0.7F, 1.3F);
		});
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onHurt(EntityDamageEvent event) {
		if (!(event.getEntity() instanceof Player player) || !Settings.get().armorEnabled) {
			return;
		}
		double left = player.getHealth() - event.getFinalDamage();
		if (left <= 0.0 || left > threshold(player) || !Cooldowns.isReady(player, Ability.BLOOD_RAGE)
			|| SetBonus.pieces(player) < 4) {
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
		if (!player.isValid() || player.isDead() || !Cooldowns.isReady(player, Ability.BLOOD_RAGE) || SetBonus.pieces(player) < 4
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
		RAGING.put(player.getUniqueId(), ServerClock.now() + duration);
		// Strength II: the full set already gives Strength I for as long as it's worn.
		player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, duration, 1, false, true, true));
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
		BloodFx.burst(center.clone().add(0.0, 0.2, 0.0), BloodFx.RING, 1, 0.0);
		Shapes.shockwave(center.clone().add(0.0, 0.1, 0.0), BloodFx.BLOOD_FADE, settings.rageRadius * 1.2, 8);
		Shapes.spiral(center, BloodFx.EMBER, 0.8, 2.4, 3.0, 30, 0.0);
		BloodFx.play(player, BloodFx.ROAR, 0.8F, 1.3F);
		BloodFx.play(player, BloodFx.HEARTBEAT, 1.2F, 0.8F);
		player.showTitle(Title.title(Component.empty(), Component.text("☠ BLOOD RAGE ☠", NamedTextColor.DARK_RED),
			Title.Times.times(Duration.ZERO, Duration.ofMillis(900), Duration.ofMillis(400))));
		Hud.flash(player, Component.text("☠ BLOOD RAGE", NamedTextColor.RED));
		if (settings.rageVignette) {
			vignette(player, true);
		}
		// The rage boils off the wearer and their heart pounds while it lasts.
		TickScheduler.repeat(5, 5, duration / 5, tick -> {
			if (!player.isValid() || player.isDead()) {
				return false;
			}
			BloodFx.burst(BloodFx.chest(player), BloodFx.BLOOD_FADE, 4, 0.35);
			if (tick % 4 == 3) {
				BloodFx.playTo(player, BloodFx.HEARTBEAT, 0.9F, 1.1F);
			}
			return true;
		});
		TickScheduler.schedule(duration, () -> endRage(player));
	}

	private static void endRage(Player player) {
		if (RAGING.remove(player.getUniqueId()) == null) {
			return;
		}
		if (player.isOnline()) {
			vignette(player, false);
			BloodFx.play(player, BloodFx.RAGE_FADES, 0.6F, 0.7F);
		}
	}

	/**
	 * Red screen edges: the vanilla "outside the world border" warning, sent to this player only
	 * through a private border so far away (a million blocks) that its wall is never visible.
	 */
	private static void vignette(Player player, boolean on) {
		if (!on) {
			player.setWorldBorder(null); // back to the world's own border
			return;
		}
		WorldBorder border = Bukkit.createWorldBorder();
		border.setCenter(player.getLocation());
		border.setSize(2_000_000.0);
		border.setWarningDistance(5_000_000); // distance 1,000,000 / 5,000,000: an 80% tint
		player.setWorldBorder(border);
	}

	/** Action-bar piece for a full-set wearer: rage active, recharging or ready; null otherwise. */
	public static Component hud(Player player, boolean evenWhenReady) {
		if (!Settings.get().armorEnabled) {
			return null;
		}
		Long until = RAGING.get(player.getUniqueId());
		long now = ServerClock.now();
		if (until != null && until > now) {
			return Component.text("☠ RAGE", NamedTextColor.RED).append(Component.text(Hud.seconds(until - now), NamedTextColor.GRAY));
		}
		if (!SetBonus.fullSetWearers().contains(player.getUniqueId())) {
			return null;
		}
		long cooling = Cooldowns.remainingTicks(player, Ability.BLOOD_RAGE);
		if (cooling > 0) {
			return Component.text("☠", NamedTextColor.DARK_GRAY).append(Component.text(Hud.seconds(cooling), NamedTextColor.DARK_GRAY));
		}
		return evenWhenReady ? Component.text("☠ ●", NamedTextColor.DARK_RED) : null;
	}

	/** Full-set blood drips and Sabatons footprints (only wearers are looked at, from the cache). */
	public static void tick(long now) {
		Settings settings = Settings.get();
		if (!settings.armorEnabled || !settings.heldAura) {
			return;
		}
		if (now % STEP_INTERVAL_TICKS == 0 && !SetBonus.sabatonWearers().isEmpty()) {
			for (UUID id : SetBonus.sabatonWearers()) {
				Player player = Bukkit.getPlayer(id);
				if (player != null) {
					footprints(player);
				}
			}
		}
		if (now % AURA_INTERVAL_TICKS != 0 || SetBonus.fullSetWearers().isEmpty()) {
			return;
		}
		for (UUID id : SetBonus.fullSetWearers()) {
			Player player = Bukkit.getPlayer(id);
			if (player == null || player.isDead()) {
				continue;
			}
			Location at = player.getLocation().add(0.0, 0.9, 0.0);
			// A drop every second or so: each one splashes and plips, so more would be a patter.
			if (now % (AURA_INTERVAL_TICKS * 2) == 0) {
				BloodFx.ambientForOthers(player, at, BloodFx.DRIP, 1, 0.3);
			}
			if (now % (AURA_INTERVAL_TICKS * 3) == 0) {
				BloodFx.ambientForOthers(player, at, BloodFx.BLOOD_FADE, 2, 0.35);
				// The wearer sees it running down their legs to the ground, below their line of sight.
				BloodFx.ambientForSelf(player, at.clone().add(0.0, -0.55, 0.0), BloodFx.DRIP, 1, 0.22);
			}
		}
	}

	private static void footprints(Player player) {
		if (player.isDead() || player.isSneaking() || player.isFlying() || player.isInsideVehicle() || !onGround(player)) {
			return;
		}
		Location feet = player.getLocation();
		Location last = LAST_STEP.get(player.getUniqueId());
		if (last != null && last.getWorld() == feet.getWorld() && last.distanceSquared(feet) < 0.8 * 0.8) {
			return;
		}
		LAST_STEP.put(player.getUniqueId(), feet);
		if (last != null) {
			BloodFx.ambient(feet.clone().add(0.0, 0.05, 0.0), BloodFx.BLOOD, 2, 0.08);
		}
	}

	/** The server's own ground check (Player#isOnGround is the client's claim, deprecated). */
	private static boolean onGround(Entity entity) {
		return entity.isOnGround();
	}

	public static void forget(UUID playerId) {
		RAGING.remove(playerId);
		LAST_STEP.remove(playerId);
	}

	/** Plugin disabling: nobody keeps a red screen. */
	public static void shutdown() {
		for (UUID id : RAGING.keySet()) {
			Player player = Bukkit.getPlayer(id);
			if (player != null) {
				vignette(player, false);
			}
		}
		RAGING.clear();
		LAST_STEP.clear();
	}
}
