package net.unchartedsmp.bloodbath.util;

import net.unchartedsmp.bloodbath.weapon.WeaponType;
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
 */
public final class Damage {
	private static int depth;
	private static WeaponType weapon;

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
		if (!target.isValid() || target.isDead()) {
			return;
		}
		int immunity = target.getNoDamageTicks();
		target.setNoDamageTicks(0);
		WeaponType outer = weapon;
		depth++;
		weapon = source;
		try {
			if (attacker != null && attacker.isValid()) {
				DamageSource.Builder hit = DamageSource.builder(DamageType.PLAYER_ATTACK)
					.withCausingEntity(attacker)
					.withDirectEntity(attacker);
				if (from != null && from.getWorld() == target.getWorld()) {
					hit.withDamageLocation(from);
				}
				target.damage(amount, hit.build());
			} else {
				target.damage(amount);
			}
		} finally {
			depth--;
			weapon = outer;
			if (target.isValid() && target.getNoDamageTicks() == 0) {
				target.setNoDamageTicks(immunity); // the hit was cancelled: give the immunity back
			}
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
