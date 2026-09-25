package net.unchartedsmp.bloodbath.blood;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.unchartedsmp.bloodbath.Keys;
import net.unchartedsmp.bloodbath.config.BloodConfig;
import net.unchartedsmp.bloodbath.config.Settings;
import net.unchartedsmp.bloodbath.weapon.WeaponType;
import net.unchartedsmp.bloodbath.weapon.Weapons;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * Blood Levels: the Blood Anvil's progression, layered on top of an existing Bloodbath weapon.
 *
 * <p>The level is one integer in the weapon's persistent data, next to its id and kills; nothing
 * else about the weapon is replaced. {@link Weapons} reads it whenever it (re)builds the item, so
 * the level shows in the weapon's own tooltip and its melee bonus goes into the weapon's own attack
 * damage, and survives every rebuild (a config reload, a rank-up, a plugin update). The rest of the
 * power is applied where Bloodbath already deals damage: ability hits scale in {@code Damage.deal},
 * melee and arrow hits may make the target bleed.
 */
public final class BloodLevels {
	public static final TextColor BLOOD = TextColor.color(0xE0303C);
	public static final TextColor DEEP = TextColor.color(0x8A0F1B);
	private static final String[] NUMERALS = {"0", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X",
		"XI", "XII", "XIII", "XIV", "XV", "XVI", "XVII", "XVIII", "XIX", "XX"};

	private BloodLevels() {
	}

	public static BloodConfig config() {
		return Settings.get().blood;
	}

	public static int level(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return 0;
		}
		return level(stack.getPersistentDataContainer());
	}

	/** From an item's data (while its meta is being edited). Never above the configured maximum. */
	public static int level(io.papermc.paper.persistence.PersistentDataContainerView data) {
		Integer level = data.get(Keys.BLOOD_LEVEL, PersistentDataType.INTEGER);
		return level == null ? 0 : Math.max(0, Math.min(config().maxLevel(), level));
	}

	/** Sets a weapon's Blood Level and rebuilds its tooltip and damage. Other data is untouched. */
	public static void set(ItemStack weapon, int level) {
		WeaponType type = Weapons.typeOf(weapon);
		if (type == null) {
			throw new IllegalArgumentException("not a Bloodbath weapon");
		}
		int clamped = Math.max(0, Math.min(config().maxLevel(), level));
		weapon.editMeta(meta -> {
			PersistentDataContainer data = meta.getPersistentDataContainer();
			if (clamped == 0) {
				data.remove(Keys.BLOOD_LEVEL);
			} else {
				data.set(Keys.BLOOD_LEVEL, PersistentDataType.INTEGER, clamped);
			}
		});
		Weapons.rebuild(weapon);
	}

	public static boolean isMax(int level) {
		return level >= config().maxLevel();
	}

	/** Blood Drops to go from {@code level} to the next, or -1 at the maximum. */
	public static int cost(int level) {
		return isMax(level) ? -1 : config().level(level + 1).cost();
	}

	public static double damageBonus(int level) {
		return config().level(level).damageBonus();
	}

	public static double abilityPower(int level) {
		return config().level(level).abilityPower();
	}

	public static double bleedChance(int level) {
		return config().level(level).bleedChance();
	}

	/** Ability damage multiplier for a hit by {@code source}, from the Blood Level of that weapon in the attacker's hands. */
	public static double abilityMultiplier(Player attacker, WeaponType source) {
		if (attacker == null || source == null) {
			return 1.0;
		}
		PlayerInventory inventory = attacker.getInventory();
		ItemStack main = inventory.getItemInMainHand();
		ItemStack held = Weapons.typeOf(main) == source ? main : Weapons.typeOf(inventory.getItemInOffHand()) == source
			? inventory.getItemInOffHand() : null;
		return held == null ? 1.0 : 1.0 + abilityPower(level(held));
	}

	public static String numeral(int level) {
		return level >= 0 && level < NUMERALS.length ? NUMERALS[level] : String.valueOf(level);
	}

	/** "◆◆◇◇◇" for level 2 of 5. */
	public static String pips(int level) {
		int max = config().maxLevel();
		return "◆".repeat(Math.min(level, max)) + "◇".repeat(Math.max(0, max - level));
	}

	/**
	 * The lines a bled weapon adds to its own tooltip (nothing at level 0, so an un-bled weapon
	 * looks exactly as it always has).
	 */
	public static List<Component> weaponLore(WeaponType type, int level) {
		if (level <= 0) {
			return List.of();
		}
		List<Component> lines = new ArrayList<>();
		lines.add(Component.empty());
		lines.add(text("Blood Level " + numeral(level) + "  ", BLOOD).append(text(pips(level), DEEP)));
		lines.add(text(effects(type, level), NamedTextColor.GRAY));
		return lines;
	}

	/** "+2 melee damage · +10% ability damage · 8% bleed on hit" */
	public static String effects(WeaponType type, int level) {
		List<String> parts = new ArrayList<>();
		double damage = damageBonus(level);
		if (damage > 0) {
			parts.add("+" + fmt(damage) + (type == WeaponType.PARADOX_BOW ? " arrow damage" : " melee damage"));
		}
		double power = abilityPower(level);
		if (power > 0) {
			parts.add("+" + Math.round(power * 100) + "% ability damage");
		}
		double bleed = bleedChance(level);
		if (bleed > 0) {
			parts.add(Math.round(bleed * 100) + "% bleed on hit");
		}
		return parts.isEmpty() ? "no bonus" : String.join(" · ", parts);
	}

	/** The weapon's melee (or arrow) damage at a Blood Level, as its tooltip would state it. */
	public static double damageAt(WeaponType type, int level) {
		return type.attackDamage() + damageBonus(level);
	}

	public static String fmt(double value) {
		return value == Math.rint(value) ? String.valueOf((int) value) : String.format(Locale.ROOT, "%.1f", value);
	}

	public static String percent(double share) {
		double pct = share * 100.0;
		return (pct == Math.rint(pct) ? String.valueOf((int) pct) : String.format(Locale.ROOT, "%.1f", pct)) + "%";
	}

	static Component text(String text, TextColor color) {
		return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
	}
}
