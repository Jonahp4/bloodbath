package net.unchartedsmp.bloodbath.boss;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import net.unchartedsmp.bloodbath.Keys;
import net.unchartedsmp.bloodbath.ability.ServerClock;
import net.unchartedsmp.bloodbath.config.Settings;
import net.unchartedsmp.bloodbath.core.BloodCore;
import net.unchartedsmp.bloodbath.fx.BloodFx;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Every Blood Knight in the world, from the first omen to the last bell.
 *
 * <p>A summoning (the ritual, or {@code /bloodbath boss summon}) plays a warning that escalates
 * over a few seconds, then the Knight rises. The manager ticks the warnings and fights from the
 * plugin's one main loop, and owns every way a fight can end early: nobody left in the arena, an
 * admin stopping it, its chunk or world unloading, the plugin shutting down. Nothing it spawns is
 * ever saved to disk, so a crash can't leave a model or a skeleton behind either.
 */
public final class BossManager implements Listener {
	/** A summoning in progress: the warning before the Knight rises. */
	private record Summoning(Location center, long start, int ticks) {
	}

	private final JavaPlugin plugin;
	private Rig rig;
	private Animations animations;
	private final List<BloodKnightBoss> bosses = new ArrayList<>();
	private final List<Summoning> summonings = new ArrayList<>();

	public BossManager(JavaPlugin plugin) {
		this.plugin = plugin;
		try {
			rig = Rig.load(plugin.getResource("boss/blood_knight.json"));
			animations = new Animations(rig);
		} catch (IOException | RuntimeException e) {
			plugin.getLogger().log(Level.SEVERE, "Couldn't load the Blood Knight's model; the boss is unavailable", e);
		}
	}

	long now() {
		return ServerClock.now();
	}

	public boolean available() {
		return rig != null && Settings.get().boss.enabled();
	}

	public int active() {
		return bosses.size() + summonings.size();
	}

	// ---- summoning ----------------------------------------------------------------------------

	/** Why a Knight can't be summoned here right now, or null if it can. */
	public String refusal(Location at) {
		Settings.BossSettings config = Settings.get().boss;
		if (rig == null) {
			return "The Blood Knight's model failed to load (see the console).";
		}
		if (!config.enabled()) {
			return "The Blood Knight is disabled in config.yml.";
		}
		if (active() >= config.maxActive()) {
			return "The Blood Knight is already abroad (" + active() + "/" + config.maxActive() + ").";
		}
		double apart = config.arenaRadius() * 2;
		for (BloodKnightBoss boss : bosses) {
			if (boss.world() == at.getWorld() && boss.center().distanceSquared(at) < apart * apart) {
				return "Another Blood Knight is fighting nearby.";
			}
		}
		for (Summoning summoning : summonings) {
			if (summoning.center().getWorld() == at.getWorld() && summoning.center().distanceSquared(at) < apart * apart) {
				return "A Blood Knight is already rising nearby.";
			}
		}
		return null;
	}

	/** Starts the warning at {@code at}; the Knight rises when it's over. Returns false if refused. */
	public boolean summon(Location at) {
		if (refusal(at) != null) {
			return false;
		}
		Location center = at.clone();
		summonings.add(new Summoning(center, now(), Settings.get().boss.warningTicks()));
		broadcastNear(center, Component.text("☠ The ground begins to bleed...", NamedTextColor.DARK_RED), 64);
		return true;
	}

	/** Stops every fight within {@code radius} of {@code near} (all of them for null); returns how many. */
	public int stop(Location near, double radius) {
		int stopped = 0;
		long now = now();
		for (BloodKnightBoss boss : bosses) {
			if (near == null || boss.world() == near.getWorld() && boss.center().distanceSquared(near) <= radius * radius) {
				boss.retreat(now, "was sent back into the earth.");
				stopped++;
			}
		}
		Iterator<Summoning> it = summonings.iterator();
		while (it.hasNext()) {
			Summoning summoning = it.next();
			if (near == null || summoning.center().getWorld() == near.getWorld() && summoning.center().distanceSquared(near) <= radius * radius) {
				it.remove();
				stopped++;
			}
		}
		return stopped;
	}

	public List<String> describe() {
		List<String> out = new ArrayList<>();
		for (Summoning summoning : summonings) {
			out.add("rising at " + format(summoning.center()));
		}
		for (BloodKnightBoss boss : bosses) {
			out.add(boss.state().name().toLowerCase() + " (phase " + boss.phase() + ") at " + format(boss.center()) + ", "
				+ Math.round(boss.healthFraction() * 100) + "% health, " + boss.participants().size() + " fighting");
		}
		return out;
	}

	private static String format(Location at) {
		return at.getWorld().getName() + " " + at.getBlockX() + " " + at.getBlockY() + " " + at.getBlockZ();
	}

	// ---- the loop -------------------------------------------------------------------------------

	public void tick(long now) {
		if (!summonings.isEmpty()) {
			Iterator<Summoning> it = summonings.iterator();
			while (it.hasNext()) {
				Summoning summoning = it.next();
				Location center = summoning.center();
				if (!center.isWorldLoaded() || !center.getChunk().isLoaded()) {
					it.remove();
					continue;
				}
				int t = (int) (now - summoning.start());
				BossFx.warningBeat(center, t, summoning.ticks());
				if (t == summoning.ticks() / 2) {
					broadcastNear(center, Component.text("☠ Something ancient answers.", NamedTextColor.RED), 64);
				}
				if (t >= summoning.ticks()) {
					it.remove();
					rise(center);
				}
			}
		}
		if (!bosses.isEmpty()) {
			Iterator<BloodKnightBoss> it = bosses.iterator();
			while (it.hasNext()) {
				BloodKnightBoss boss = it.next();
				boss.tick(now);
				if (boss.done()) {
					it.remove();
				}
			}
		}
	}

	private void rise(Location center) {
		BloodKnightBoss boss = new BloodKnightBoss(this, plugin, rig, animations, center);
		bosses.add(boss);
		Title title = Title.title(Component.text("THE BLOOD KNIGHT", NamedTextColor.DARK_RED, TextDecoration.BOLD),
			Component.text("has risen", NamedTextColor.GRAY),
			Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(2600), Duration.ofMillis(800)));
		for (Player player : center.getWorld().getPlayers()) {
			if (player.getLocation().distanceSquared(center) <= 48 * 48) {
				player.showTitle(title);
			}
		}
	}

	void broadcastNear(Location center, Component message, double radius) {
		Component line = Settings.get().prefix.append(message);
		for (Player player : center.getWorld().getPlayers()) {
			if (player.getLocation().distanceSquared(center) <= radius * radius) {
				player.sendMessage(line);
			}
		}
	}

	/** The fight whose body this is. */
	private BloodKnightBoss bossOf(Entity entity) {
		if (!(entity instanceof LivingEntity) || !entity.getPersistentDataContainer().has(Keys.BOSS, PersistentDataType.BYTE)) {
			return null;
		}
		for (BloodKnightBoss boss : bosses) {
			if (boss.brain() == entity) {
				return boss;
			}
		}
		return null;
	}

	/** The fight whose stand-in (the look players without the pack get) this is. */
	private BloodKnightBoss standInOf(Entity entity) {
		if (!(entity instanceof LivingEntity) || !entity.getPersistentDataContainer().has(Keys.BOSS, PersistentDataType.BYTE)) {
			return null;
		}
		for (BloodKnightBoss boss : bosses) {
			if (boss.puppet() == entity) {
				return boss;
			}
		}
		return null;
	}

	// ---- the ritual ---------------------------------------------------------------------------

	/** Sneak and right-click crying obsidian with a Blood Core: the Knight answers. */
	@EventHandler(priority = EventPriority.HIGH)
	public void onRitual(PlayerInteractEvent event) {
		Block block = event.getClickedBlock();
		if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND || block == null
			|| block.getType() != Material.CRYING_OBSIDIAN || !event.getPlayer().isSneaking() || !BloodCore.isCore(event.getItem())
			|| event.useInteractedBlock() == Event.Result.DENY || !Settings.get().boss.ritual()) {
			return;
		}
		event.setCancelled(true);
		Player player = event.getPlayer();
		Location altar = block.getLocation().add(0.5, 1.0, 0.5);
		altar.setYaw(player.getLocation().getYaw() + 180.0F);
		String refused = refusal(altar);
		if (refused != null) {
			player.sendMessage(Settings.get().prefix.append(Component.text(refused, NamedTextColor.GRAY)));
			return;
		}
		if (player.getGameMode() != GameMode.CREATIVE) {
			ItemStack core = event.getItem();
			core.setAmount(core.getAmount() - 1);
		}
		summon(altar);
		BloodFx.play(altar, BossFx.BELL, 1.4F, 0.4F);
	}

	// ---- the Knight's body --------------------------------------------------------------------

	@EventHandler(priority = EventPriority.HIGH)
	public void onBossDamaged(EntityDamageEvent event) {
		BloodKnightBoss standIn = standInOf(event.getEntity());
		if (standIn != null) {
			// The stand-in is only a look: a player's hit on it lands on the Knight itself.
			event.setCancelled(true);
			if (event instanceof EntityDamageByEntityEvent && event.getDamageSource().getCausingEntity() instanceof Player player) {
				standIn.hitThroughStandIn(player, event.getDamage());
			}
			return;
		}
		BloodKnightBoss boss = bossOf(event.getEntity());
		if (boss == null) {
			return;
		}
		switch (event.getCause()) {
			case FALL, SUFFOCATION, DROWNING, FIRE, FIRE_TICK, LAVA, HOT_FLOOR, CRAMMING, FREEZE, CONTACT, WITHER, POISON -> {
				event.setCancelled(true);
				return;
			}
			case VOID -> {
				event.setCancelled(true);
				boss.brain().teleport(boss.center());
				return;
			}
			default -> {
			}
		}
		if (!boss.vulnerable()) {
			event.setCancelled(true);
			return;
		}
		boss.shapeIncoming(event);
	}

	/** No name tags, leads or saddles on the Knight or its stand-in. */
	@EventHandler(priority = EventPriority.HIGH)
	public void onInteract(PlayerInteractEntityEvent event) {
		if (bossOf(event.getRightClicked()) != null || standInOf(event.getRightClicked()) != null) {
			event.setCancelled(true);
		}
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onBossHurt(EntityDamageByEntityEvent event) {
		BloodKnightBoss boss = bossOf(event.getEntity());
		if (boss != null) {
			Entity attacker = event.getDamageSource().getCausingEntity();
			boss.onHurt(attacker == null ? event.getDamager() : attacker, now());
			return;
		}
		BloodKnightBoss hitter = bossOf(event.getDamager());
		if (hitter != null) {
			hitter.onMelee(event.getEntity(), now());
		}
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onBossDeath(EntityDeathEvent event) {
		if (event.getEntity().getPersistentDataContainer().has(Keys.THRALL, PersistentDataType.BYTE)) {
			event.getDrops().clear(); // a thrall leaves nothing but a little experience
			event.setDroppedExp(5);
			BossFx.hit(event.getEntity().getLocation().add(0, 1.0, 0));
			return;
		}
		BloodKnightBoss boss = bossOf(event.getEntity());
		if (boss == null) {
			return;
		}
		event.getDrops().clear();
		event.setDroppedExp(0);
		boss.onDeath(event.getEntity().getKiller(), now());
	}

	/** Someone died in an arena. */
	@EventHandler(priority = EventPriority.MONITOR)
	public void onPlayerDeath(PlayerDeathEvent event) {
		Player player = event.getPlayer();
		Location at = player.getLocation();
		for (BloodKnightBoss boss : bosses) {
			if (!boss.done() && boss.inArena(at) && boss.participants().contains(player.getUniqueId())) {
				BossFx.elimination(at);
				broadcastNear(boss.center(), Component.text("☠ " + player.getName() + " was claimed by the Blood Knight.", NamedTextColor.RED),
					Settings.get().boss.arenaRadius() + 16);
				boss.onPlayerKilled(now());
				return;
			}
		}
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onQuit(PlayerQuitEvent event) {
		UUID id = event.getPlayer().getUniqueId();
		for (BloodKnightBoss boss : bosses) {
			boss.forgetViewer(id);
		}
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onWorldUnload(WorldUnloadEvent event) {
		World world = event.getWorld();
		for (BloodKnightBoss boss : bosses) {
			if (boss.world() == world) {
				boss.end();
			}
		}
		bosses.removeIf(BloodKnightBoss::done);
		summonings.removeIf(summoning -> summoning.center().getWorld() == world);
	}

	/** Called when a player's resource pack status changes. */
	public void packChanged(Player player) {
		for (BloodKnightBoss boss : bosses) {
			if (!boss.done() && boss.world() == player.getWorld()) {
				boss.packChanged(player);
			}
		}
	}

	/** Plugin disabling: every Knight and every omen gone at once. */
	public void shutdown() {
		for (BloodKnightBoss boss : bosses) {
			boss.end();
		}
		bosses.clear();
		summonings.clear();
	}
}
