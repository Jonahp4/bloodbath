package net.unchartedsmp.bloodbath.config;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.unchartedsmp.bloodbath.ability.Ability;
import net.unchartedsmp.bloodbath.weapon.WeaponType;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

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

	public final double particleMultiplier;
	public final boolean longRangeParticles;
	public final boolean heldAura;
	public final boolean killEffects;

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
	/** Blood Rage triggers when health falls to this share of max health. */
	public final double rageThreshold;
	public final int rageDurationTicks;
	public final double rageRadius;
	private final int rageCooldownTicks;

	public final boolean recipesEnabled;
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

		particleMultiplier = Math.max(0.0, c == null ? 1.0 : c.getDouble("effects.particle-multiplier", 1.0));
		longRangeParticles = bool(c, "effects.long-range", true);
		heldAura = bool(c, "effects.held-aura", true);
		killEffects = bool(c, "effects.kill-effects", true);

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
		rageThreshold = Math.max(0.05, Math.min(0.95, c == null ? 0.4 : c.getDouble("armor.rage-threshold", 0.4)));
		rageDurationTicks = Math.max(20, (int) Math.round((c == null ? 8.0 : c.getDouble("armor.rage-duration", 8.0)) * 20.0));
		rageRadius = Math.max(0.0, c == null ? 4.0 : c.getDouble("armor.rage-radius", 4.0));
		rageCooldownTicks = Math.max(0, (int) Math.round((c == null ? 60.0 : c.getDouble("armor.rage-cooldown", 60.0)) * 20.0));

		recipesEnabled = bool(c, "recipes.enabled", false);
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
		key.append(killTracking).append(tooltipFrame()).append(armorKillHeal).append(rageThreshold);
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

	private static boolean bool(ConfigurationSection c, String path, boolean fallback) {
		return c == null ? fallback : c.getBoolean(path, fallback);
	}

	private static String str(ConfigurationSection c, String path, String fallback) {
		return c == null ? fallback : c.getString(path, fallback);
	}
}
