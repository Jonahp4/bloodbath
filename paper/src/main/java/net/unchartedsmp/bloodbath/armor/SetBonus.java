package net.unchartedsmp.bloodbath.armor;

import com.destroystokyo.paper.event.player.PlayerArmorChangeEvent;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.unchartedsmp.bloodbath.ability.TickScheduler;
import net.unchartedsmp.bloodbath.config.Settings;
import net.unchartedsmp.bloodbath.fx.BloodFx;
import net.unchartedsmp.bloodbath.fx.Shapes;
import net.unchartedsmp.bloodbath.hud.Hud;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * Who is wearing how much Blood Knight armour, kept up to date from equipment changes rather than
 * re-reading four item stacks for every player every few ticks, and the full set's buffs: on the
 * moment the fourth piece goes on, off the moment any piece comes off.
 *
 * <p>The buffs are infinite, ambient, particle-free effects. They're re-applied after anything that
 * strips effects (milk, a totem, dying) and removed on the way out without touching a stronger
 * effect of the same kind the player got elsewhere (a Strength II potion keeps running).
 */
public final class SetBonus implements Listener {
	/** A player's Blood Knight pieces: how many, and whether the boots are among them. */
	public record Worn(int pieces, boolean sabatons) {
		static final Worn NONE = new Worn(0, false);
	}

	private static final Map<UUID, Worn> WORN = new HashMap<>();
	/** Full-set wearers and Sabatons wearers, the only players the per-tick visuals ever look at. */
	private static final Set<UUID> FULL = new HashSet<>();
	private static final Set<UUID> SABATONS = new HashSet<>();
	/** Players currently holding our buffs, so we only ever remove what we gave. */
	private static final Set<UUID> BUFFED = new HashSet<>();

	/** How many Blood Knight pieces the player wears (cached; computed on a miss). */
	public static int pieces(Player player) {
		Worn worn = WORN.get(player.getUniqueId());
		return worn != null ? worn.pieces() : refresh(player).pieces();
	}

	public static boolean hasSabatons(Player player) {
		Worn worn = WORN.get(player.getUniqueId());
		return worn != null ? worn.sabatons() : refresh(player).sabatons();
	}

	public static Collection<UUID> fullSetWearers() {
		return Collections.unmodifiableSet(FULL);
	}

	public static Collection<UUID> sabatonWearers() {
		return Collections.unmodifiableSet(SABATONS);
	}

	/** Re-reads the player's armour and applies or removes the full-set buffs to match. */
	public static Worn refresh(Player player) {
		UUID id = player.getUniqueId();
		if (!player.isOnline()) {
			forget(id);
			return Worn.NONE;
		}
		PlayerInventory inventory = player.getInventory();
		int pieces = 0;
		boolean sabatons = false;
		for (ArmorPiece piece : ArmorPiece.values()) {
			if (BloodArmor.typeOf(inventory.getItem(piece.slot())) == piece) {
				pieces++;
				sabatons |= piece == ArmorPiece.SABATONS;
			}
		}
		Worn worn = new Worn(pieces, sabatons);
		Worn before = WORN.put(id, worn);
		boolean full = pieces >= 4 && Settings.get().armorEnabled;
		if (full) {
			FULL.add(id);
		} else {
			FULL.remove(id);
		}
		if (sabatons) {
			SABATONS.add(id);
		} else {
			SABATONS.remove(id);
		}
		boolean wasFull = before != null && before.pieces() >= 4;
		if (full) {
			applyBuffs(player);
			if (!wasFull && player.isValid() && !player.isDead()) {
				completed(player);
			}
		} else if (BUFFED.contains(id)) {
			removeBuffs(player);
		}
		return worn;
	}

	/** The moment the fourth piece goes on. */
	private static void completed(Player player) {
		Hud.flash(player, Component.text("☠ The Blood Knight's strength is yours", NamedTextColor.RED));
		BloodFx.play(player, BloodFx.ARMOR_SET, 0.9F, 0.7F);
		BloodFx.play(player, BloodFx.HEARTBEAT, 0.8F, 0.9F);
		Shapes.spiral(player.getLocation().add(0, 0.1, 0), BloodFx.EMBER, 0.8, 2.0, 2.0, 26, 0.0);
		BloodFx.burst(player.getLocation().add(0, 0.2, 0), BloodFx.RING, 1, 0.0);
		BloodFx.ring(player.getLocation().add(0, 0.05, 0), BloodFx.BLOOD_FADE, 0.9, 16);
	}

	private static void applyBuffs(Player player) {
		boolean any = false;
		for (Map.Entry<PotionEffectType, Integer> buff : Settings.get().fullSetEffects.entrySet()) {
			PotionEffect current = player.getPotionEffect(buff.getKey());
			if (current != null && (current.getAmplifier() > buff.getValue() || isOurs(current, buff.getValue()))) {
				continue; // something stronger is already running, or ours already is
			}
			player.addPotionEffect(new PotionEffect(buff.getKey(), PotionEffect.INFINITE_DURATION, buff.getValue(), true, false, true));
			any = true;
		}
		if (any || !Settings.get().fullSetEffects.isEmpty()) {
			BUFFED.add(player.getUniqueId());
		}
	}

	private static void removeBuffs(Player player) {
		BUFFED.remove(player.getUniqueId());
		for (Map.Entry<PotionEffectType, Integer> buff : Settings.get().fullSetEffects.entrySet()) {
			PotionEffect current = player.getPotionEffect(buff.getKey());
			if (current == null) {
				continue;
			}
			// Removing an effect removes the whole stack under it, ours included. If what's on top
			// is someone else's (a potion), put it straight back without ours underneath.
			player.removePotionEffect(buff.getKey());
			if (!isOurs(current, buff.getValue())) {
				player.addPotionEffect(current);
			}
		}
	}

	private static boolean isOurs(PotionEffect effect, int amplifier) {
		return effect.isInfinite() && effect.getAmplifier() == amplifier && effect.isAmbient() && !effect.hasParticles();
	}

	private static void later(Player player) {
		TickScheduler.schedule(1, () -> {
			if (player.isOnline()) {
				refresh(player);
			}
		});
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onArmorChange(PlayerArmorChangeEvent event) {
		// Next tick: the inventory already holds the new item then, whatever changed it (a click,
		// a dispenser, breaking, /item, another plugin).
		later(event.getPlayer());
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onJoin(PlayerJoinEvent event) {
		later(event.getPlayer());
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onRespawn(PlayerRespawnEvent event) {
		later(event.getPlayer());
	}

	/** Milk, a totem or /effect clear took our buffs: put them back while the set is still on. */
	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onEffectLost(EntityPotionEffectEvent event) {
		if (event.getEntity() instanceof Player player && FULL.contains(player.getUniqueId())
			&& (event.getAction() == EntityPotionEffectEvent.Action.CLEARED || event.getAction() == EntityPotionEffectEvent.Action.REMOVED)
			&& event.getCause() != EntityPotionEffectEvent.Cause.PLUGIN && event.getCause() != EntityPotionEffectEvent.Cause.DEATH) {
			later(player);
		}
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onQuit(PlayerQuitEvent event) {
		forget(event.getPlayer().getUniqueId());
	}

	/** After /bloodbath reload: the set might have been switched off, or its buffs changed. */
	public static void refreshAll() {
		for (Player player : Bukkit.getOnlinePlayers()) {
			if (BUFFED.contains(player.getUniqueId())) {
				removeBuffsWith(player, lastApplied);
			}
			refresh(player);
		}
		lastApplied = Settings.get().fullSetEffects;
	}

	/** The buff list the current buffs were given under (a reload may change it). */
	private static Map<PotionEffectType, Integer> lastApplied = Map.of();

	private static void removeBuffsWith(Player player, Map<PotionEffectType, Integer> buffs) {
		for (Map.Entry<PotionEffectType, Integer> buff : buffs.entrySet()) {
			PotionEffect current = player.getPotionEffect(buff.getKey());
			if (current != null && isOurs(current, buff.getValue())) {
				player.removePotionEffect(buff.getKey());
			}
		}
		BUFFED.remove(player.getUniqueId());
	}

	public static void forget(UUID id) {
		WORN.remove(id);
		FULL.remove(id);
		SABATONS.remove(id);
		BUFFED.remove(id);
	}

	/** Plugin disabling: take the buffs back (they're infinite), forget everything. */
	public static void shutdown() {
		for (UUID id : new HashSet<>(BUFFED)) {
			Player player = Bukkit.getPlayer(id);
			if (player != null) {
				removeBuffsWith(player, lastApplied);
			}
		}
		WORN.clear();
		FULL.clear();
		SABATONS.clear();
		BUFFED.clear();
	}

	/** Remembers the buff list in force at startup (see {@link #refreshAll}). */
	public static void init() {
		lastApplied = Settings.get().fullSetEffects;
	}
}
