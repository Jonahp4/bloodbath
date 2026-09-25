package net.unchartedsmp.bloodbath.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.bukkit.configuration.ConfigurationSection;

/**
 * The Bloodlands, its portals, blood water, bleeding, Blood Drops, the Blood Anvil and Blood Levels:
 * the {@code bloodlands}, {@code portal}, {@code blood-water}, {@code bleeding}, {@code blood-drop},
 * {@code blood-anvil} and {@code blood-levels} sections of config.yml. Times are read in seconds
 * (like the rest of config.yml) and kept here in ticks.
 */
public record BloodConfig(Lands lands, Portal portal, Water water, Bleed bleed, Drops drops, Anvil anvil, List<Level> levels) {

	/** {@code bloodlands:} */
	public record Lands(boolean enabled, String worldName, long seed, boolean biomeDatapack, boolean freezeTime, long time,
		double lakeFrequency, double eliteChance, int eliteHealth) {
	}

	/** {@code portal:} */
	public record Portal(int minWidth, int maxWidth, int minHeight, int maxHeight, int warmupTicks, int cooldownTicks,
		boolean reclaimInactiveFrames, int arrivalRadius) {
	}

	/** {@code blood-water:} */
	public record Water(boolean enabled, double damage, int intervalTicks, int bleedingTicks, int bleedingStacks) {
	}

	/** {@code bleeding:} one Bloodbath bleeding for blood water, bled weapons and the Blood Knight. */
	public record Bleed(double damagePerStack, int intervalTicks, int durationTicks, int maxStacks, Stacking stacking, int particles) {
	}

	/** What a new bleeding does to one already running. */
	public enum Stacking {
		/** Stacks add up (to max-stacks) and the timer restarts. */
		STACK,
		/** The stronger of the two stays and the timer restarts. */
		REFRESH,
		/** A running bleed is left alone. */
		NONE;

		static Stacking parse(String text) {
			return switch (text.trim().toLowerCase(Locale.ROOT)) {
				case "refresh" -> REFRESH;
				case "none", "ignore" -> NONE;
				default -> STACK;
			};
		}
	}

	/** {@code blood-drop:} */
	public record Drops(double mobChance, double eliteChance, double shrineChance, double shrineFrequency, int bossMin, int bossMax,
		boolean pickupEffects) {
	}

	/** {@code blood-anvil:} */
	public record Anvil(boolean enabled, boolean craftable) {
	}

	/** One entry of {@code blood-levels:}. Level 1 is index 0. */
	public record Level(int level, int cost, double damageBonus, double abilityPower, double bleedChance) {
	}

	public int maxLevel() {
		return levels.size();
	}

	/** The level's numbers, or a level 0 with no bonus. */
	public Level level(int level) {
		if (level <= 0 || levels.isEmpty()) {
			return new Level(0, 0, 0.0, 0.0, 0.0);
		}
		return levels.get(Math.min(level, levels.size()) - 1);
	}

	static BloodConfig read(ConfigurationSection c) {
		Lands lands = new Lands(
			bool(c, "bloodlands.enabled", true),
			str(c, "bloodlands.world-name", "Bloodlands").trim().isEmpty() ? "Bloodlands" : str(c, "bloodlands.world-name", "Bloodlands").trim(),
			c == null ? 0L : c.getLong("bloodlands.seed", 0L),
			bool(c, "bloodlands.biome-datapack", true),
			bool(c, "bloodlands.freeze-time", true),
			(long) num(c, "bloodlands.time", 13800, 0, 23999),
			num(c, "bloodlands.lake-frequency", 1.0, 0.0, 4.0),
			num(c, "bloodlands.elite-chance", 0.08, 0.0, 1.0),
			(int) num(c, "bloodlands.elite-health", 40, 1, 1024));
		int minWidth = (int) num(c, "portal.minimum-width", 2, 1, 64);
		int minHeight = (int) num(c, "portal.minimum-height", 3, 1, 64);
		Portal portal = new Portal(minWidth, (int) Math.max(minWidth, num(c, "portal.maximum-width", 20, 1, 64)),
			minHeight, (int) Math.max(minHeight, num(c, "portal.maximum-height", 20, 1, 64)),
			ticks(c, "portal.warmup", 1.0, 0, 30),
			ticks(c, "portal.cooldown", 5.0, 1, 600),
			bool(c, "portal.reclaim-inactive-frames", true),
			(int) num(c, "portal.arrival-spread", 4, 0, 32));
		Water water = new Water(
			bool(c, "blood-water.enabled", true),
			num(c, "blood-water.damage", 1.0, 0.0, 100.0),
			ticks(c, "blood-water.interval", 1.0, 0.05, 60),
			ticks(c, "blood-water.bleeding-duration", 5.0, 0, 600),
			(int) num(c, "blood-water.bleeding-stacks", 1, 0, 20));
		Bleed bleed = new Bleed(
			num(c, "bleeding.damage", 0.5, 0.0, 100.0),
			ticks(c, "bleeding.interval", 1.5, 0.25, 60),
			ticks(c, "bleeding.duration", 5.0, 0.5, 600),
			(int) num(c, "bleeding.max-stacks", 5, 1, 50),
			Stacking.parse(str(c, "bleeding.stacking", "stack")),
			(int) num(c, "bleeding.particles", 2, 0, 16));
		int bossMin = (int) num(c, "blood-drop.boss-min", 1, 0, 64);
		Drops drops = new Drops(
			num(c, "blood-drop.mob-drop-chance", 0.02, 0.0, 1.0),
			num(c, "blood-drop.elite-drop-chance", 0.35, 0.0, 1.0),
			num(c, "blood-drop.structure-chance", 0.6, 0.0, 1.0),
			num(c, "blood-drop.shrine-frequency", 1.0, 0.0, 10.0),
			bossMin, (int) Math.max(bossMin, num(c, "blood-drop.boss-max", 3, 0, 64)),
			bool(c, "blood-drop.pickup-effects", true));
		Anvil anvil = new Anvil(bool(c, "blood-anvil.enabled", true), bool(c, "blood-anvil.craftable", true));
		return new BloodConfig(lands, portal, water, bleed, drops, anvil, levels(c));
	}

	/** The built-in progression, used when config.yml has no (or a broken) blood-levels section. */
	private static final double[][] DEFAULT_LEVELS = {
		// cost, damage, ability power, bleed chance
		{1, 1.0, 0.05, 0.04},
		{2, 2.0, 0.10, 0.08},
		{3, 3.0, 0.15, 0.12},
		{5, 4.5, 0.20, 0.16},
		{8, 6.0, 0.30, 0.22}};

	private static List<Level> levels(ConfigurationSection c) {
		ConfigurationSection section = c == null ? null : c.getConfigurationSection("blood-levels");
		List<Level> out = new ArrayList<>();
		if (section != null) {
			// Levels 1, 2, 3... in order; the first gap ends the list.
			for (int level = 1; level <= 20; level++) {
				ConfigurationSection entry = section.getConfigurationSection(String.valueOf(level));
				if (entry == null) {
					break;
				}
				out.add(new Level(level,
					Math.max(0, Math.min(64 * 27, entry.getInt("cost", level))),
					clamp(entry.getDouble("damage-bonus", 0.0), 0.0, 1000.0),
					clamp(entry.getDouble("ability-power", 0.0), 0.0, 10.0),
					clamp(entry.getDouble("bleeding-chance", 0.0), 0.0, 1.0)));
			}
		}
		if (out.isEmpty()) {
			for (int i = 0; i < DEFAULT_LEVELS.length; i++) {
				double[] row = DEFAULT_LEVELS[i];
				out.add(new Level(i + 1, (int) row[0], row[1], row[2], row[3]));
			}
		}
		return List.copyOf(out);
	}

	/** Everything that changes how a bled weapon is built, for the item revision. */
	String fingerprint() {
		return levels.toString();
	}

	private static double clamp(double value, double min, double max) {
		return Double.isNaN(value) ? min : Math.max(min, Math.min(max, value));
	}

	private static int ticks(ConfigurationSection c, String path, double fallbackSeconds, double min, double max) {
		return (int) Math.max(1, Math.round(num(c, path, fallbackSeconds, min, max) * 20.0));
	}

	private static double num(ConfigurationSection c, String path, double fallback, double min, double max) {
		double value = c == null ? fallback : c.getDouble(path, fallback);
		return clamp(Double.isNaN(value) ? fallback : value, min, max);
	}

	private static boolean bool(ConfigurationSection c, String path, boolean fallback) {
		return c == null ? fallback : c.getBoolean(path, fallback);
	}

	private static String str(ConfigurationSection c, String path, String fallback) {
		return c == null ? fallback : c.getString(path, fallback);
	}
}
