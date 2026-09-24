package net.unchartedsmp.bloodbath;

import net.unchartedsmp.bloodbath.ability.Cooldowns;
import net.unchartedsmp.bloodbath.ability.NullField;
import net.unchartedsmp.bloodbath.ability.ServerClock;
import net.unchartedsmp.bloodbath.ability.TickScheduler;
import net.unchartedsmp.bloodbath.armor.BloodKnightSet;
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
import net.unchartedsmp.bloodbath.recipe.Recipes;
import net.unchartedsmp.bloodbath.weapon.Behaviors;
import net.unchartedsmp.bloodbath.weapon.WeaponType;
import net.unchartedsmp.bloodbath.weapon.Weapons;
import org.bukkit.command.PluginCommand;
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
	private BukkitTask ticker;

	@Override
	public void onEnable() {
		Keys.init(this);
		TickScheduler.setLogger(getLogger());
		saveDefaultConfig();
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

		PluginCommand command = getCommand("bloodbath");
		if (command != null) {
			BloodbathCommand executor = new BloodbathCommand(this);
			command.setExecutor(executor);
			command.setTabCompleter(executor);
		}

		ticker = getServer().getScheduler().runTaskTimer(this, this::tick, 1L, 1L);
		// Enabled late (or reloaded) with players online: set them up as if they'd just joined.
		for (Player player : getServer().getOnlinePlayers()) {
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
		if (now % PRUNE_INTERVAL_TICKS == 0) {
			Cooldowns.prune();
			NullField.prune();
			Behaviors.prune();
			weaponListener.prune();
		}
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

	public WeaponListener weaponListener() {
		return weaponListener;
	}
}
