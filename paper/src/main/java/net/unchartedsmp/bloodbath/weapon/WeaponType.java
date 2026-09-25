package net.unchartedsmp.bloodbath.weapon;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.unchartedsmp.bloodbath.ability.Ability;
import org.bukkit.Material;

/**
 * The armory. Ids match the Fabric mod's item ids and the resource pack's model names, so the
 * same pack serves both.
 *
 * <p>Base items: everything but the bow is built on a netherite sword (no right-click action of its
 * own, fire-proof, not fuel, not smeltable) with its attributes replaced; the Paradox Bow is a real
 * bow so vanilla clients play the draw animation themselves.
 */
public enum WeaponType {
	RIFTBLADE("riftblade", "Bloodrift Blade", Ability.RIFTBLADE, Material.NETHERITE_SWORD, 9, 1.6, 1561,
		"Right-click: tear a bleeding rift ahead", "that drags enemies in. Use again to", "step through, cutting all around you."),
	BLOODHOOK("bloodhook", "Bloodhook", Ability.BLOODHOOK, Material.NETHERITE_SWORD, 1, 4.0, 0,
		"Right-click: chain the enemy you're", "looking at, slow them and reel", "yourself in. Repeat hooks reach further."),
	NULLBLADE("nullblade", "Clotblade", Ability.NULLBLADE_ZONE, Material.NETHERITE_SWORD, 7, 1.6, 1561,
		"Hits clot a player's blood, suppressing", "their abilities (once every 8s each).", "Right-click: throw down a clot field."),
	METEOR_GAUNTLET("meteor_gauntlet", "Blood Meteor Gauntlet", Ability.METEOR_GAUNTLET, Material.NETHERITE_SWORD, 1, 4.0, 0,
		"Punch a block or right-click the ground:", "a blood meteor falls, launches everything", "nearby and leaves a crater that bleeds."),
	GRAVESTONE("gravestone", "Crimson Gravestone", Ability.GRAVESTONE, Material.NETHERITE_SWORD, 1, 4.0, 0,
		"Right-click: a blood pool drags everything", "nearby in, then erupts under them."),
	CHRONOS("chronos", "Bleeding Chronos", Ability.CHRONOS, Material.NETHERITE_SWORD, 1, 4.0, 0,
		"Right-click: leave a blood-mark. Use", "again to snap back to it and win back", "some of the blood you lost since."),
	THUNDER_PIKE("thunder_pike", "Crimson Thunder Pike", Ability.THUNDER_PIKE, Material.NETHERITE_SWORD, 1, 4.0, 0,
		"Right-click: hurl charged blood, call", "crimson lightning and ride the bolt.", "What it strikes is stunned."),
	MIRRORFANG("mirrorfang", "Blood Mirrorfang", Ability.MIRRORFANG, Material.NETHERITE_SWORD, 1, 4.0, 0,
		"Right-click: a blood mirror of you", "hunts down the nearest enemy."),
	VOID_SCYTHE("void_scythe", "Hemorrhage Scythe", Ability.VOID_SCYTHE, Material.NETHERITE_SWORD, 12, 1.0, 2031,
		"Every hit makes the target bleed.", "The 5th hemorrhages them and everything", "around them."),
	PARADOX_BOW("paradox_bow", "Sanguine Paradox Bow", Ability.PARADOX_BOW, Material.BOW, 0, 0, 384,
		"A real bow. A fully drawn shot fires", "twice: its echo homes in on whatever", "the arrow hit (and lights it up), or", "retraces its flight."),
	VAMPIRE_FANG("vampire_fang", "Vampire Fang", Ability.VAMPIRE_FANG, Material.NETHERITE_SWORD, 6, 2.2, 1561,
		"Hits drink blood: heal part of the", "damage you deal.", "Right-click: blood dash through enemies."),
	BLOOD_GRIMOIRE("blood_grimoire", "Blood Grimoire", Ability.BLOOD_GRIMOIRE, Material.NETHERITE_SWORD, 1, 4.0, 0,
		"Right-click a creature: drain its blood", "into you, slowing it as it goes.", "Sneak + right-click a player: give them yours.");

	private static final Map<String, WeaponType> BY_ID = new HashMap<>();
	private static final Map<String, WeaponType> BY_NAME = new HashMap<>();
	private static final Map<Ability, WeaponType> BY_ABILITY = new HashMap<>();

	static {
		for (WeaponType type : values()) {
			BY_ID.put(type.id, type);
			BY_ABILITY.put(type.ability, type);
			String name = normalize(type.displayName);
			BY_NAME.put(name, type);
			// "clotblade" as well as "nullblade", "hemorrhage_scythe" or just "scythe", ...
			BY_NAME.putIfAbsent(name.substring(name.lastIndexOf('_') + 1), type);
		}
	}

	private final String id;
	private final String displayName;
	private final Ability ability;
	private final Material base;
	private final double attackDamage;
	private final double attackSpeed;
	private final int durability;
	private final List<String> description;

	WeaponType(String id, String displayName, Ability ability, Material base, double attackDamage, double attackSpeed,
		int durability, String... description) {
		this.id = id;
		this.displayName = displayName;
		this.ability = ability;
		this.base = base;
		this.attackDamage = attackDamage;
		this.attackSpeed = attackSpeed;
		this.durability = durability;
		this.description = List.of(description);
	}

	public static WeaponType byId(String id) {
		return id == null ? null : BY_ID.get(id.toLowerCase(Locale.ROOT));
	}

	/** For commands: an id, a display name ("Hemorrhage Scythe") or its last word ("scythe"). */
	public static WeaponType find(String input) {
		if (input == null) {
			return null;
		}
		String key = normalize(input);
		WeaponType type = BY_ID.get(key);
		return type != null ? type : BY_NAME.get(key);
	}

	private static String normalize(String text) {
		return text.trim().toLowerCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
	}

	public static WeaponType of(Ability ability) {
		return BY_ABILITY.get(ability);
	}

	public String id() {
		return id;
	}

	public String displayName() {
		return displayName;
	}

	public Ability ability() {
		return ability;
	}

	public Material base() {
		return base;
	}

	/** Total melee damage (0 = keep the base item's attributes). */
	public double attackDamage() {
		return attackDamage;
	}

	/** Attacks per second. */
	public double attackSpeed() {
		return attackSpeed;
	}

	/** Max durability, or 0 for unbreakable. */
	public int durability() {
		return durability;
	}

	public List<String> description() {
		return description;
	}
}
