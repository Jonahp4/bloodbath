package net.unchartedsmp.bloodbath.util;

import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.unchartedsmp.bloodbath.ability.ServerClock;
import net.unchartedsmp.bloodbath.blood.BloodLevels;
import net.unchartedsmp.bloodbath.config.Settings;
import net.unchartedsmp.bloodbath.fx.BloodFx;
import net.unchartedsmp.bloodbath.weapon.WeaponType;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

/**
 * Ability damage. It goes through the normal damage pipeline as a player attack, so armour,
 * protection plugins (claims, PvP flags) and kill credit all work, while {@link #isAbilityDamage()}
 * lets our melee listener ignore it (otherwise ability hits would stack bleed, drink blood, etc.).
 *
 * <p>Abilities always land: the target's hurt-immunity from a melee hit a moment earlier is
 * skipped for the ability hit (and restored if the hit gets cancelled).
 *
 * <p>A hit that lands plays a soft tick to the attacker alone (once per tick, however many it
 * hits), so an ability hitting something behind a wall or in a crowd is never in doubt.
 */
public final class Damage {
	private static int depth;
	private static WeaponType weapon;
	private static final Map<Player, Long> LAST_MARKER = new WeakHashMap<>();

	private Damage() {
	}

	public static void deal(LivingEntity target, double amount, Player attacker, WeaponType source) {
		deal(target, amount, attacker, source, null);
	}

	/**
	 * @param attacker gets kill credit; if they logged off or died meanwhile the hit is sourceless
	 * @param source which weapon's ability this is, for kill tracking
	 * @param from where the blow comes from (knockback direction), or null for the attacker
	 */
	public static void deal(LivingEntity target, double amount, Player attacker, WeaponType source, Location from) {
		deal(target, amount, attacker, source, from, true);
	}

	/**
	 * @param blow true for a hit (a player attack: knockback, armour applies); false for damage
	 *     over time (bleeding): magic damage that ignores armour and doesn't shove the target,
	 *     still credited to the attacker
	 */
	public static void deal(LivingEntity target, double amount, Player attacker, WeaponType source, Location from, boolean blow) {
		if (!target.isValid() || target.isDead()) {
			return;
		}
		double pierce = 0.0;
		if (blow) {
			// A weapon bled at the Blood Anvil hits harder with its ability too.
			amount *= BloodLevels.abilityMultiplier(attacker, source);
			if (target instanceof Player) {
				amount *= Settings.get().pvpAbilityDamage;
				// Part of every ability hit on a player goes straight through armour: against full
				// netherite a hit that only armour sees does almost nothing, which made abilities
				// decoration in PvP. (Mobs and the boss take one ordinary hit, as before.)
				pierce = amount * Settings.get().abilityArmorPierce;
				amount -= pierce;
			}
		}
		int immunity = target.getNoDamageTicks();
		double lastDamage = target.getLastDamage();
		double healthBefore = target.getHealth();
		observed = null;
		target.setNoDamageTicks(0);
		WeaponType outer = weapon;
		depth++;
		weapon = source;
		try {
			if (attacker != null && attacker.isValid() && !blow) {
				target.damage(amount, DamageSource.builder(DamageType.MAGIC).withCausingEntity(attacker).build());
			} else if (attacker != null && attacker.isValid()) {
				DamageSource.Builder hit = DamageSource.builder(DamageType.PLAYER_ATTACK)
					.withCausingEntity(attacker)
					.withDirectEntity(attacker);
				if (from != null && from.getWorld() == target.getWorld()) {
					hit.withDamageLocation(from);
				}
				if (amount > 0.0) {
					target.damage(amount, hit.build());
				}
				if (pierce > 0.0 && target.isValid() && !target.isDead()) {
					target.setNoDamageTicks(0);
					target.damage(pierce, DamageSource.builder(DamageType.MAGIC).withCausingEntity(attacker).build());
				}
			} else {
				target.damage(amount + pierce);
			}
		} finally {
			depth--;
			weapon = outer;
			boolean landed = target.getHealth() < healthBefore || target.isDead() || target.getNoDamageTicks() > 0;
			if (target.isValid() && !target.isDead()) {
				// Ability hits neither use up nor grant hurt-immunity. Vanilla would give the target
				// half a second of i-frames here, which swallowed the next melee swing (and with it
				// every on-hit passive: bleed, lifesteal, clots) right after an ability.
				target.setNoDamageTicks(immunity);
				target.setLastDamage(lastDamage);
			}
			if (landed && attacker != null && attacker != target) {
				hitMarker(attacker);
			}
			if (!DEBUGGERS.isEmpty()) {
				report(target, amount, source, landed, healthBefore);
			}
		}
	}

	/** What the damage event said about the hit in progress (null: no event fired at all). */
	private static Boolean observed;
	/** Admins watching ability hits with /bloodbath debug. */
	private static final Set<UUID> DEBUGGERS = new HashSet<>();

	/** Called by the MONITOR damage listener for our own hits. */
	public static void observe(boolean cancelled) {
		observed = cancelled;
	}

	public static boolean toggleDebug(UUID admin) {
		if (DEBUGGERS.remove(admin)) {
			return false;
		}
		DEBUGGERS.add(admin);
		return true;
	}

	public static void forget(UUID player) {
		DEBUGGERS.remove(player);
	}

	private static void report(LivingEntity target, double amount, WeaponType source, boolean landed, double before) {
		String what = (source == null ? "Blood Knight armour" : source.displayName()) + " → " + target.getName() + " ("
			+ String.format(Locale.ROOT, "%.1f", amount) + "): ";
		String outcome;
		if (landed) {
			outcome = String.format(Locale.ROOT, "hit, health %.1f → %.1f", before, target.isDead() ? 0.0 : target.getHealth());
		} else if (observed == null) {
			outcome = "no damage event: the target can't be hurt right now (creative, invulnerable, dead)";
		} else if (observed) {
			outcome = "CANCELLED by another plugin (claims, PvP or region protection)";
		} else {
			outcome = "went through but did no damage (armour, resistance or absorption took it)";
		}
		Component line = Component.text("[Bloodbath debug] ", NamedTextColor.DARK_GRAY)
			.append(Component.text(what, NamedTextColor.GRAY))
			.append(Component.text(outcome, landed ? NamedTextColor.GREEN : NamedTextColor.RED));
		for (UUID id : DEBUGGERS) {
			Player admin = Bukkit.getPlayer(id);
			if (admin != null) {
				admin.sendMessage(line);
			}
		}
	}

	private static void hitMarker(Player attacker) {
		long now = ServerClock.now();
		Long last = LAST_MARKER.put(attacker, now);
		if ((last == null || last != now) && attacker.isOnline() && Settings.get().hitMarkers) {
			BloodFx.playTo(attacker, BloodFx.HIT_MARKER, 0.35F, 1.9F);
		}
	}

	/** True while one of our abilities is dealing damage (on the main thread). */
	public static boolean isAbilityDamage() {
		return depth > 0;
	}

	/** The weapon whose ability is dealing damage right now, or null. */
	public static WeaponType currentWeapon() {
		return depth > 0 ? weapon : null;
	}
}
