package net.unchartedsmp.bloodbath.config;

import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.unchartedsmp.bloodbath.ability.Ability;
import net.unchartedsmp.bloodbath.weapon.WeaponType;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.potion.PotionEffectType;

/** Typed, immutable snapshot of config.yml. Replaced wholesale on /bloodbath reload. */
public final class Settings {
	private static Settings current = new Settings(null);

	public final boolean packEnabled;
	public final String packMode;
	public final int packPort;
	public final String packBind;
	public final String packPublicHost;
	public final String packUrl;
	public final String packSha1;
	public final boolean packRequired;
	public final Component packPrompt;

	public final boolean hudEnabled;
	public final boolean readyPing;
	/** A soft tick when an ability hit lands, and a ☠ under the crosshair on a kill. */
	public final boolean hitMarkers;

	public final double particleMultiplier;
	/** Players further than this from an effect aren't sent it at all. */
	public final double particleViewDistance;
	/** Within this, full detail; up to twice this, half; beyond, a quarter. */
	public final double fullDetailDistance;
	/** Custom sprites, HUD icons and GUI art for players who loaded the pack. */
	public final boolean packVisuals;
	/** Scales every Bloodbath sound. */
	public final double soundVolume;
	/** Sound id -> replacement id ("" mutes it). */
	public final Map<String, String> soundReplace;
	public final boolean heldAura;
	public final boolean killEffects;
	/** Red screen edges while Blood Rage lasts. */
	public final boolean rageVignette;

	public final Set<String> disabledWorlds;
	public final boolean respectWorldPvp;
	public final boolean neverDespawn;
	public final boolean killTracking;
	public final boolean countMobKills;
	/** "auto" (only when the pack is required), "true" or "false". */
	public final String tooltipFrame;

	public final boolean armorEnabled;
	/** Health restored per kill with 2+ Blood Knight pieces (half-hearts). */
	public final double armorKillHeal;
	/** Effects (and amplifiers) the full Blood Knight set gives for as long as it's worn. */
	public final Map<PotionEffectType, Integer> fullSetEffects;
	/** Damage melee attackers take from 3+ Blood Knight pieces (half-hearts). */
	public final double armorBarbDamage;
	/** Blood Rage triggers when health falls to this share of max health. */
	public final double rageThreshold;
	public final int rageDurationTicks;
	public final double rageRadius;
	private final int rageCooldownTicks;

	public final boolean recipesEnabled;
	public final BossSettings boss;

	/** The Blood Knight boss fight ({@code boss:} in config.yml). Times are already in ticks. */
	public record BossSettings(boolean enabled, double health, double armor, double meleeDamage, double scale, double arenaRadius,
		int maxActive, boolean ritual, int warningTicks, int leaveTimeoutTicks, int animationInterval, int attackCooldownTicks,
		double cleaveDamage, double slamDamage, double spikeDamage, double chargeDamage, double phaseTwoAt, boolean atmosphere,
		int coresMin, int coresMax, double armorChance, int experience, boolean announce) {
	}
	public final ConfigurationSection recipes;
	public final Component prefix;

	private final Map<WeaponType, ConfigurationSection> weapons = new EnumMap<>(WeaponType.class);

	private Settings(FileConfiguration config) {
		ConfigurationSection c = config;
		packEnabled = bool(c, "resource-pack.enabled", true);
		packMode = str(c, "resource-pack.mode", "embedded").toLowerCase(Locale.ROOT);
		packPort = c == null ? 8163 : c.getInt("resource-pack.port", 8163);
		packBind = str(c, "resource-pack.bind", "0.0.0.0").trim();
		packPublicHost = str(c, "resource-pack.public-host", "").trim();
		packUrl = str(c, "resource-pack.url", "").trim();
		packSha1 = str(c, "resource-pack.sha1", "").trim().toLowerCase(Locale.ROOT);
		packRequired = bool(c, "resource-pack.required", false);
		packPrompt = MiniMessage.miniMessage().deserialize(str(c, "resource-pack.prompt",
			"<dark_red><bold>Bloodbath</bold></dark_red> <gray>uses a resource pack for its 3D weapons."));

		hudEnabled = bool(c, "hud.enabled", true);
		readyPing = bool(c, "hud.ready-ping", true);
		hitMarkers = bool(c, "hud.hit-markers", true);

		particleMultiplier = Math.max(0.0, c == null ? 1.0 : c.getDouble("effects.particle-multiplier", 1.0));
		// effects.long-range (1.2 and older) meant "send ability particles 512 blocks": now a view distance.
		double defaultView = bool(c, "effects.long-range", true) ? 64.0 : 32.0;
		particleViewDistance = Math.max(8.0, Math.min(256.0, c == null ? defaultView : c.getDouble("effects.view-distance", defaultView)));
		fullDetailDistance = Math.max(4.0, Math.min(particleViewDistance, c == null ? 24.0 : c.getDouble("effects.full-detail-distance", 24.0)));
		packVisuals = bool(c, "effects.pack-visuals", true);
		soundVolume = Math.max(0.0, Math.min(4.0, c == null ? 1.0 : c.getDouble("sounds.volume", 1.0)));
		Map<String, String> replace = new HashMap<>();
		ConfigurationSection swaps = c == null ? null : c.getConfigurationSection("sounds.replace");
		if (swaps != null) {
			for (String key : swaps.getKeys(false)) {
				replace.put(key.toLowerCase(Locale.ROOT), swaps.getString(key, "").trim().toLowerCase(Locale.ROOT));
			}
		}
		soundReplace = Map.copyOf(replace);
		heldAura = bool(c, "effects.held-aura", true);
		killEffects = bool(c, "effects.kill-effects", true);
		rageVignette = bool(c, "effects.rage-vignette", true);

		disabledWorlds = new HashSet<>();
		if (c != null) {
			for (String world : c.getStringList("gameplay.disabled-worlds")) {
				disabledWorlds.add(world.toLowerCase(Locale.ROOT));
			}
		}
		respectWorldPvp = bool(c, "gameplay.respect-world-pvp", true);
		neverDespawn = bool(c, "gameplay.weapons-never-despawn", true);
		killTracking = bool(c, "kill-tracking.enabled", true);
		countMobKills = bool(c, "kill-tracking.count-mobs", true);
		tooltipFrame = str(c, "items.tooltip-frame", "auto").trim().toLowerCase(Locale.ROOT);

		armorEnabled = bool(c, "armor.enabled", true);
		armorKillHeal = Math.max(0.0, c == null ? 3.0 : c.getDouble("armor.kill-heal", 3.0));
		armorBarbDamage = Math.max(0.0, c == null ? 2.0 : c.getDouble("armor.barb-damage", 2.0));
		fullSetEffects = effects(c, "armor.full-set-effects", List.of("strength:0", "speed:0", "fire_resistance:0"));
		rageThreshold = Math.max(0.05, Math.min(0.95, c == null ? 0.4 : c.getDouble("armor.rage-threshold", 0.4)));
		rageDurationTicks = Math.max(20, (int) Math.round((c == null ? 8.0 : c.getDouble("armor.rage-duration", 8.0)) * 20.0));
		rageRadius = Math.max(0.0, c == null ? 4.0 : c.getDouble("armor.rage-radius", 4.0));
		rageCooldownTicks = Math.max(0, (int) Math.round((c == null ? 60.0 : c.getDouble("armor.rage-cooldown", 60.0)) * 20.0));

		recipesEnabled = bool(c, "recipes.enabled", true);
		boss = new BossSettings(
			bool(c, "boss.enabled", true),
			num(c, "boss.health", 600, 20, 100000),
			num(c, "boss.armor", 12, 0, 30),
			num(c, "boss.melee-damage", 9, 0, 1000),
			num(c, "boss.scale", 1.0, 0.5, 3.0),
			num(c, "boss.arena-radius", 32, 12, 128),
			(int) num(c, "boss.max-active", 1, 1, 10),
			bool(c, "boss.summon-ritual", true),
			(int) Math.round(num(c, "boss.warning-seconds", 6, 1, 30) * 20),
			(int) Math.round(num(c, "boss.leave-timeout", 60, 5, 3600) * 20),
			(int) num(c, "boss.animation-interval", 2, 1, 5),
			(int) Math.round(num(c, "boss.attacks.cooldown", 3.5, 0.5, 30) * 20),
			num(c, "boss.attacks.cleave-damage", 12, 0, 1000),
			num(c, "boss.attacks.slam-damage", 10, 0, 1000),
			num(c, "boss.attacks.spike-damage", 9, 0, 1000),
			num(c, "boss.attacks.charge-damage", 8, 0, 1000),
			num(c, "boss.phase-two-at", 0.5, 0.05, 0.95),
			bool(c, "boss.atmosphere", true),
			(int) num(c, "boss.loot.cores-min", 2, 0, 64),
			(int) Math.max(num(c, "boss.loot.cores-min", 2, 0, 64), num(c, "boss.loot.cores-max", 4, 0, 64)),
			num(c, "boss.loot.armor-chance", 0.35, 0, 1),
			(int) num(c, "boss.loot.experience", 500, 0, 100000),
			bool(c, "boss.announce", true));
		recipes = c == null ? null : c.getConfigurationSection("recipes");
		prefix = MiniMessage.miniMessage().deserialize(str(c, "messages.prefix", "<dark_red>☠</dark_red> "));

		if (c != null) {
			for (WeaponType type : WeaponType.values()) {
				ConfigurationSection section = c.getConfigurationSection("weapons." + type.id());
				if (section != null) {
					weapons.put(type, section);
				}
			}
		}
	}

	public static Settings get() {
		return current;
	}

	public static Settings load(FileConfiguration config) {
		current = new Settings(config);
		return current;
	}

	/** Whether weapons get the blood tooltip frame (it needs the resource pack, or tooltips look broken). */
	public boolean tooltipFrame() {
		return switch (tooltipFrame) {
			case "true", "yes", "on", "always" -> true;
			case "false", "no", "off", "never" -> false;
			default -> packEnabled && packRequired;
		};
	}

	/**
	 * Everything that changes how a weapon item is built. Weapons made under a different value are
	 * rebuilt when next seen (see {@code Weapons.refresh}).
	 */
	public String itemFingerprint() {
		StringBuilder key = new StringBuilder();
		key.append(killTracking).append(tooltipFrame()).append(armorKillHeal).append(armorBarbDamage).append(rageThreshold)
			.append(fullSetEffects);
		for (Ability ability : Ability.values()) {
			key.append(',').append(cooldownTicks(ability));
		}
		return Integer.toHexString(key.toString().hashCode());
	}

	public boolean enabled(WeaponType type) {
		ConfigurationSection section = weapons.get(type);
		return section == null || section.getBoolean("enabled", true);
	}

	public boolean abilitiesAllowedIn(World world) {
		return !disabledWorlds.contains(world.getName().toLowerCase(Locale.ROOT));
	}

	public int cooldownTicks(Ability ability) {
		if (ability == Ability.BLOOD_RAGE) {
			return rageCooldownTicks;
		}
		WeaponType type = WeaponType.of(ability);
		ConfigurationSection section = type == null ? null : weapons.get(type);
		if (section == null || !section.contains("cooldown")) {
			return ability.defaultCooldownTicks();
		}
		return Math.max(0, (int) Math.round(section.getDouble("cooldown") * 20.0));
	}

	/** A per-weapon number from config.yml, e.g. {@code num(THUNDER_PIKE, "damage", 5)}. */
	public double num(WeaponType type, String key, double fallback) {
		ConfigurationSection section = weapons.get(type);
		return section == null ? fallback : section.getDouble(key, fallback);
	}

	public int ticks(WeaponType type, String secondsKey, int fallbackTicks) {
		return (int) Math.round(num(type, secondsKey, fallbackTicks / 20.0) * 20.0);
	}

	/** "effect:amplifier" entries (amplifier 0 = level I); unknown effects are skipped with a warning. */
	private static Map<PotionEffectType, Integer> effects(ConfigurationSection c, String path, List<String> fallback) {
		List<String> entries = c == null || !c.isList(path) ? fallback : c.getStringList(path);
		Map<PotionEffectType, Integer> out = new LinkedHashMap<>();
		for (String entry : entries) {
			String name = entry.trim().toLowerCase(Locale.ROOT);
			int amplifier = 0;
			int colon = name.lastIndexOf(':');
			if (colon > 0 && name.substring(colon + 1).chars().allMatch(Character::isDigit) && colon < name.length() - 1) {
				amplifier = Math.min(9, Integer.parseInt(name.substring(colon + 1)));
				name = name.substring(0, colon);
			}
			NamespacedKey key = NamespacedKey.fromString(name);
			PotionEffectType type = key == null ? null : Registry.EFFECT.get(key);
			if (type == null) {
				Bukkit.getLogger().warning("[Bloodbath] " + path + ": unknown effect '" + entry + "', skipped.");
				continue;
			}
			out.put(type, amplifier);
		}
		return Collections.unmodifiableMap(out);
	}

	private static double num(ConfigurationSection c, String path, double fallback, double min, double max) {
		double value = c == null ? fallback : c.getDouble(path, fallback);
		return Double.isNaN(value) ? fallback : Math.max(min, Math.min(max, value));
	}

	private static boolean bool(ConfigurationSection c, String path, boolean fallback) {
		return c == null ? fallback : c.getBoolean(path, fallback);
	}

	private static String str(ConfigurationSection c, String path, String fallback) {
		return c == null ? fallback : c.getString(path, fallback);
	}
}
