package net.unchartedsmp.bloodbath;

import java.util.List;
import net.unchartedsmp.bloodbath.ability.Cooldowns;
import net.unchartedsmp.bloodbath.ability.NullField;
import net.unchartedsmp.bloodbath.ability.ServerClock;
import net.unchartedsmp.bloodbath.ability.TickScheduler;
import net.unchartedsmp.bloodbath.anvil.BloodAnvils;
import net.unchartedsmp.bloodbath.armor.BloodKnightSet;
import net.unchartedsmp.bloodbath.blood.Bleeding;
import net.unchartedsmp.bloodbath.bloodlands.Bloodlands;
import net.unchartedsmp.bloodbath.armor.SetBonus;
import net.unchartedsmp.bloodbath.boss.BossManager;
import net.unchartedsmp.bloodbath.fx.Particles;
import net.unchartedsmp.bloodbath.pack.PackState;
import net.unchartedsmp.bloodbath.command.BloodbathCommand;
import net.unchartedsmp.bloodbath.config.Settings;
import net.unchartedsmp.bloodbath.gui.MenuListener;
import net.unchartedsmp.bloodbath.hud.Hud;
import net.unchartedsmp.bloodbath.listener.MirrorGuard;
import net.unchartedsmp.bloodbath.listener.SessionListener;
import net.unchartedsmp.bloodbath.listener.WeaponListener;
import net.unchartedsmp.bloodbath.pack.ResourcePackService;
import net.unchartedsmp.bloodbath.portal.Portals;
import net.unchartedsmp.bloodbath.recipe.Recipes;
import net.unchartedsmp.bloodbath.weapon.Behaviors;
import net.unchartedsmp.bloodbath.weapon.WeaponType;
import net.unchartedsmp.bloodbath.weapon.Weapons;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Bloodbath for Paper: twelve blood-themed 3D ability weapons.
 *
 * <p>All timing runs off one repeating task: it advances {@link ServerClock}, drains
 * {@link TickScheduler} (every multi-tick effect in the plugin), ticks the weapons and the HUD,
 * and once a minute sweeps expired state out of memory.
 */
public class BloodbathPlugin extends JavaPlugin {
	private static final int PRUNE_INTERVAL_TICKS = 1200;

	private ResourcePackService packs;
	private WeaponListener weaponListener;
	private BossManager bosses;
	private Bloodlands bloodlands;
	private Portals portals;
	private BloodAnvils anvils;
	private BukkitTask ticker;

	@Override
	public void onEnable() {
		Keys.init(this);
		TickScheduler.setLogger(getLogger());
		saveDefaultConfig();
		migrateConfig();
		applySettings();

		packs = new ResourcePackService(this);
		packs.start();
		Recipes.register(this);

		weaponListener = new WeaponListener();
		PluginManager plugins = getServer().getPluginManager();
		plugins.registerEvents(weaponListener, this);
		plugins.registerEvents(new MirrorGuard(), this);
		plugins.registerEvents(new SessionListener(this), this);
		plugins.registerEvents(new MenuListener(), this);
		plugins.registerEvents(new BloodKnightSet(), this);
		plugins.registerEvents(new SetBonus(), this);
		bosses = new BossManager(this);
		plugins.registerEvents(bosses, this);
		SetBonus.init();

		// The Bloodlands, their portals and the Blood Anvil.
		bloodlands = new Bloodlands(this);
		bloodlands.enable();
		plugins.registerEvents(bloodlands, this);
		portals = new Portals(this);
		plugins.registerEvents(portals, this);
		portals.enable(bloodlands);
		anvils = new BloodAnvils(this);
		plugins.registerEvents(anvils, this);
		anvils.enable();

		PluginCommand command = getCommand("bloodbath");
		if (command != null) {
			BloodbathCommand executor = new BloodbathCommand(this);
			command.setExecutor(executor);
			command.setTabCompleter(executor);
		}

		ticker = getServer().getScheduler().runTaskTimer(this, this::tick, 1L, 1L);
		// Enabled late (or reloaded) with players online: set them up as if they'd just joined.
		for (Player player : getServer().getOnlinePlayers()) {
			if (!PackState.restore(player, packs.hash())) {
				packs.sendWhenReady(player); // the plugin was updated in place: they have the old pack
			}
			SessionListener.welcome(player);
		}
		getLogger().info("Bloodbath ready: " + WeaponType.values().length + " weapons. Resource pack: " + packs.status() + ".");
	}

	@Override
	public void onDisable() {
		if (ticker != null) {
			ticker.cancel();
			ticker = null;
		}
		if (anvils != null) {
			anvils.disable(); // every open Blood Anvil gives its items back first
		}
		if (portals != null) {
			portals.disable();
		}
		if (bloodlands != null) {
			bloodlands.save();
		}
		Bleeding.clearAll();
		Behaviors.shutdown(); // removes live blood mirrors before the worlds save
		if (bosses != null) {
			bosses.shutdown(); // every Blood Knight and its model, before the worlds save
		}
		BloodKnightSet.shutdown(); // nobody keeps a red screen
		SetBonus.shutdown(); // the full-set buffs are infinite: take them back
		Particles.invalidate();
		PackState.clearAll();
		TickScheduler.clearAll();
		Cooldowns.clearAll();
		NullField.clearAll();
		Hud.clearAll();
		Recipes.unregister();
		if (packs != null) {
			packs.stop();
		}
	}

	private void tick() {
		long now = ServerClock.advance();
		TickScheduler.drain();
		Behaviors.tick(now);
		Hud.tick(now);
		BloodKnightSet.tick(now);
		bosses.tick(now);
		Bleeding.tick(now);
		bloodlands.tick(now);
		portals.tick(now);
		anvils.tick(now);
		if (now % PRUNE_INTERVAL_TICKS == 0) {
			portals.prune();
			Cooldowns.prune();
			NullField.prune();
			Behaviors.prune();
			weaponListener.prune();
		}
	}

	/**
	 * Brings a config.yml written by an older version up to date. Before 1.3.2 the default was
	 * {@code resource-pack.mode: embedded}, which only works with the pack port open to the
	 * internet; a config still on that untouched default moves to {@code auto}, which also uses the
	 * published copy of the pack. Anything the owner changed is left alone.
	 */
	private static final List<String> MIRROR_COMMENTS = List.of(
		"auto and embedded modes: public copies of the pack. {sha1} is the pack's hash and {version} the",
		"plugin's version. A mirror is only used after the plugin has downloaded it and checked it's",
		"identical to its own pack, so it can never hand players a different or outdated one. Remove them",
		"all to never use a mirror.");

	/**
	 * 1.6.0's PvP rebalance: {path, the old default, the new one}. A server that never changed a
	 * value gets the new one; anything an owner tuned themselves is theirs and stays.
	 */
	private static final Object[][] REBALANCED = {
		{"weapons.riftblade.cooldown", 15, 14},
		{"weapons.bloodhook.cooldown", 6, 7}, {"weapons.bloodhook.range", 10, 12},
		{"weapons.nullblade.hit-clot", 4, 2.5},
		{"weapons.meteor_gauntlet.cooldown", 14, 15}, {"weapons.meteor_gauntlet.damage", 5, 8}, {"weapons.meteor_gauntlet.delay", 2, 1.6},
		{"weapons.gravestone.cooldown", 20, 18}, {"weapons.gravestone.duration", 3, 2},
		{"weapons.thunder_pike.cooldown", 12, 13}, {"weapons.thunder_pike.range", 22, 20}, {"weapons.thunder_pike.damage", 5, 7},
		{"weapons.mirrorfang.duration", 7, 6}, {"weapons.mirrorfang.damage", 4, 3}, {"weapons.mirrorfang.range", 3.5, 3},
		{"weapons.void_scythe.cooldown", 3, 5}, {"weapons.void_scythe.damage", 10, 9}, {"weapons.void_scythe.splash-damage", 8, 6},
		{"weapons.paradox_bow.damage", 7, 6},
		{"weapons.vampire_fang.cooldown", 8, 9}, {"weapons.vampire_fang.dash-damage", 4, 5},
		{"weapons.blood_grimoire.cooldown", 14, 16}, {"weapons.blood_grimoire.range", 12, 10}, {"weapons.blood_grimoire.drain", 6, 5},
		{"armor.barb-damage", 2, 1.5},
		{"armor.full-set-effects", List.of("strength:0", "speed:0", "fire_resistance:0"), List.of("speed:0", "fire_resistance:0")},
	};

	private void rebalance(FileConfiguration config) {
		int changed = 0;
		for (Object[] entry : REBALANCED) {
			String path = (String) entry[0];
			if (!config.isSet(path)) {
				continue;
			}
			boolean untouched = entry[1] instanceof List<?> old
				? config.getStringList(path).stream().map(String::trim).toList().equals(old)
				: config.isInt(path) || config.isDouble(path)
					? Math.abs(config.getDouble(path) - ((Number) entry[1]).doubleValue()) < 1.0E-9 : false;
			if (untouched) {
				config.set(path, entry[2]);
				changed++;
			}
		}
		// Settings new in 1.6.0 show up in old configs too, so they can be found and tuned.
		var defaults = config.getDefaults();
		if (defaults != null && config.isConfigurationSection("weapons")) {
			for (String key : defaults.getKeys(true)) {
				if ((key.startsWith("weapons.") || key.startsWith("gameplay.")) && !defaults.isConfigurationSection(key)
					&& !config.isSet(key) && config.isConfigurationSection(key.substring(0, key.lastIndexOf('.')))) {
					config.set(key, defaults.get(key));
					changed++;
				}
			}
		}
		if (changed > 0) {
			saveConfig();
			getLogger().info("Updated config.yml for 1.6.0's PvP rebalance: " + changed + " settings you hadn't changed now have the new values.");
		}
	}

	private void migrateConfig() {
		FileConfiguration config = getConfig();
		rebalance(config);
		// 1.5.0: the Blood Anvil and Bloodstone Frame recipes, for configs written before they existed.
		boolean added = false;
		for (String recipe : List.of("blood_anvil", "bloodstone_frame")) {
			if (config.isConfigurationSection("recipes") && !config.isSet("recipes." + recipe) && config.getDefaults() != null
				&& config.getDefaults().isConfigurationSection("recipes." + recipe)) {
				var defaults = config.getDefaults().getConfigurationSection("recipes." + recipe);
				for (String key : defaults.getKeys(true)) {
					if (!defaults.isConfigurationSection(key)) {
						config.set("recipes." + recipe + "." + key, defaults.get(key));
					}
				}
				added = true;
			}
		}
		if (added) {
			saveConfig();
		}
		if (config.isSet("resource-pack.mirrors")) {
			// 1.6.0: the old default mirrors named the file by version, so rebuilding a version changed the
			// file under servers that had already checked it. Swap untouched defaults for the per-build ones.
			if (config.getStringList("resource-pack.mirrors").stream().map(String::trim).toList().equals(Settings.LEGACY_MIRRORS)) {
				config.set("resource-pack.mirrors", Settings.DEFAULT_MIRRORS);
				config.setComments("resource-pack.mirrors", MIRROR_COMMENTS);
				saveConfig();
				getLogger().info("Updated config.yml: the resource pack mirrors now point at one file per pack build.");
			}
			return;
		}
		boolean untouched = "embedded".equalsIgnoreCase(config.getString("resource-pack.mode", ""))
			&& config.getString("resource-pack.public-host", "").isBlank();
		config.set("resource-pack.mirrors", Settings.DEFAULT_MIRRORS);
		config.setComments("resource-pack.mirrors", MIRROR_COMMENTS);
		if (untouched) {
			config.set("resource-pack.mode", "auto");
			config.setComments("resource-pack.mode", List.of(
				"auto:     players download the pack from a public mirror (below), checked at startup to be",
				"          byte-for-byte the pack inside this plugin, and from the plugin's own little web",
				"          server when there's no mirror. No ports to open. If a player's download fails from",
				"          one, they're sent the other straight away.",
				"embedded: the plugin's own web server first (needs the port below open to the internet), the",
				"          mirror if a player can't reach it.",
				"url:      send a pack you host yourself (url and sha1 below)."));
			getLogger().info("Updated config.yml: resource-pack.mode embedded -> auto, so players get the pack even when port "
				+ config.getInt("resource-pack.port", 8163) + " isn't open to the internet.");
		}
		saveConfig();
	}

	/** Reads config.yml into {@link Settings} and bumps the item revision if weapon items change. */
	private void applySettings() {
		Settings settings = Settings.load(getConfig());
		Weapons.setRevision(getPluginMeta().getVersion() + "-" + settings.itemFingerprint());
	}

	/** /bloodbath reload */
	public void reload() {
		reloadConfig();
		applySettings();
		packs.start();
		Recipes.register(this);
		for (Player player : getServer().getOnlinePlayers()) {
			SessionListener.refreshInventory(player);
		}
		SetBonus.refreshAll();
	}

	public ResourcePackService packs() {
		return packs;
	}

	public BossManager bosses() {
		return bosses;
	}

	public Bloodlands bloodlands() {
		return bloodlands;
	}

	public Portals portals() {
		return portals;
	}

	public BloodAnvils anvils() {
		return anvils;
	}

	public WeaponListener weaponListener() {
		return weaponListener;
	}
}
