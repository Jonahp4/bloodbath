package net.unchartedsmp.bloodbath.hud;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.unchartedsmp.bloodbath.Keys;
import net.unchartedsmp.bloodbath.ability.Ability;
import net.unchartedsmp.bloodbath.ability.Cooldowns;
import net.unchartedsmp.bloodbath.ability.NullField;
import net.unchartedsmp.bloodbath.ability.ServerClock;
import net.unchartedsmp.bloodbath.config.Settings;
import net.unchartedsmp.bloodbath.fx.BloodFx;
import net.unchartedsmp.bloodbath.weapon.Behaviors;
import net.unchartedsmp.bloodbath.weapon.WeaponBehavior;
import net.unchartedsmp.bloodbath.weapon.WeaponType;
import net.unchartedsmp.bloodbath.weapon.Weapons;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.MainHand;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataType;

/**
 * Everything the player sees without using an ability:
 * <ul>
 *   <li>an action-bar status line while a Bloodbath weapon is held (cooldown bar, open rift,
 *       blood-mark timer, bow draw, bleed stacks...);</li>
 *   <li>a "ready" ping and blood ring when any ability comes off cooldown;</li>
 *   <li>a dripping aura around a held Bloodbath weapon;</li>
 *   <li>clot particles over players whose abilities are suppressed.</li>
 * </ul>
 * One-off messages go through {@link #flash} so the status line doesn't immediately overwrite them.
 */
public final class Hud {
	private static final int INTERVAL_TICKS = 4;
	private static final int FLASH_HOLD_TICKS = 40;
	private static final int BAR_SEGMENTS = 20;
	private static final Map<UUID, Long> FLASH_UNTIL = new HashMap<>();
	/** Players who turned the status line off with /bloodbath hud (mirrors their persistent flag). */
	private static final Set<UUID> HIDDEN = new HashSet<>();

	private Hud() {
	}

	/** Shows {@code message} on the action bar and holds it there for 2 seconds. */
	public static void flash(Player player, Component message) {
		player.sendActionBar(message);
		FLASH_UNTIL.put(player.getUniqueId(), ServerClock.now() + FLASH_HOLD_TICKS);
	}

	public static void tick(long now) {
		if (now % INTERVAL_TICKS != 0) {
			return;
		}
		Settings settings = Settings.get();
		for (Player player : Bukkit.getOnlinePlayers()) {
			if (player.isDead()) {
				continue;
			}
			for (Ability ready : Cooldowns.drainReady(player)) {
				if (settings.readyPing) {
					announceReady(player, ready);
				}
			}
			long clotted = NullField.remainingTicks(player);
			if (clotted > 0 && now % (INTERVAL_TICKS * 2) == 0) {
				BloodFx.burst(player.getLocation().add(0.0, player.getHeight() + 0.35, 0.0), BloodFx.CLOT, 3, 0.25);
			}
			PlayerInventory inventory = player.getInventory();
			boolean offhand = false;
			WeaponType type = Weapons.typeOf(inventory.getItemInMainHand());
			if (type == null) {
				type = Weapons.typeOf(inventory.getItemInOffHand());
				offhand = true;
			}
			if (type == null) {
				continue;
			}
			WeaponBehavior behavior = Behaviors.of(type);
			if (settings.heldAura) {
				aura(player, behavior, offhand, now);
			}
			if (settings.hudEnabled && !HIDDEN.contains(player.getUniqueId())
				&& now >= FLASH_UNTIL.getOrDefault(player.getUniqueId(), 0L)) {
				player.sendActionBar(clotted > 0 && behavior.canBeNullified() ? clottedStatus(clotted) : behavior.hud(player));
			}
		}
	}

	private static void announceReady(Player player, Ability ability) {
		Location chest = player.getLocation().add(0.0, 1.0, 0.0);
		BloodFx.play(player, BloodFx.READY, 0.5F, 0.7F);
		BloodFx.ring(chest, BloodFx.BLOOD_FADE, 0.9, 14);
		BloodFx.gather(handPos(player, false), 1.2, 4, 8);
		flash(player, Component.text("✦ " + ability.displayName() + " ready", NamedTextColor.RED));
	}

	/** Blood slowly dripping off whatever Bloodbath weapon is in hand. */
	private static void aura(Player player, WeaponBehavior behavior, boolean offhand, long now) {
		Location hand = handPos(player, offhand);
		BloodFx.ambient(hand, BloodFx.DRIP, 1, 0.08);
		if ((now / INTERVAL_TICKS) % 3 == 0) {
			BloodFx.ambient(hand.clone().add(0.0, 0.2, 0.0), behavior.auraAccent(), 1, 0.15);
		}
		if ((now / INTERVAL_TICKS) % 5 == 0 && Cooldowns.isReady(player, behavior.ability())) {
			BloodFx.ambient(hand.clone().add(0.0, 0.3, 0.0), BloodFx.BLOOD_FADE, 2, 0.2);
		}
	}

	/** Roughly where the holding hand is, from the player's facing and handedness. */
	public static Location handPos(Player player, boolean offhand) {
		Location feet = player.getLocation();
		double yaw = Math.toRadians(feet.getYaw());
		double forwardX = -Math.sin(yaw);
		double forwardZ = Math.cos(yaw);
		boolean rightSide = (player.getMainHand() == MainHand.RIGHT) != offhand;
		double side = rightSide ? 1.0 : -1.0;
		double rightX = -Math.cos(yaw) * side;
		double rightZ = -Math.sin(yaw) * side;
		double height = player.isSneaking() ? 0.55 : 0.8;
		return feet.add(rightX * 0.38 + forwardX * 0.25, height, rightZ * 0.38 + forwardZ * 0.25);
	}

	// ---- status line pieces ----------------------------------------------------------------

	/** "Bloodrift |||||||||||||||||||| 6.2s" or "Bloodrift ● READY". */
	public static Component cooldownBar(Player player, Ability ability) {
		Component name = Component.text(ability.displayName() + "  ", NamedTextColor.DARK_RED);
		long remaining = Cooldowns.remainingTicks(player, ability);
		if (remaining <= 0) {
			return name.append(Component.text("● READY", NamedTextColor.RED));
		}
		return name.append(bar(Cooldowns.progress(player, ability)))
			.append(Component.text(seconds(remaining), NamedTextColor.GRAY));
	}

	public static Component bar(float progress) {
		int filled = Math.max(0, Math.min(BAR_SEGMENTS, Math.round(progress * BAR_SEGMENTS)));
		return Component.text("|".repeat(filled), NamedTextColor.RED)
			.append(Component.text("|".repeat(BAR_SEGMENTS - filled), NamedTextColor.DARK_GRAY));
	}

	public static Component timer(String label, long ticks) {
		return Component.text(label, NamedTextColor.RED).append(Component.text(seconds(ticks), NamedTextColor.GRAY));
	}

	/** " 6.2s" */
	public static String seconds(long ticks) {
		return String.format(Locale.ROOT, " %.1fs", ticks / 20.0);
	}

	private static Component clottedStatus(long ticks) {
		return Component.text("✖ CLOTTED  ", NamedTextColor.DARK_RED)
			.append(Component.text("abilities suppressed", NamedTextColor.GRAY))
			.append(Component.text(seconds(ticks), NamedTextColor.RED));
	}

	// ---- per-player toggle -----------------------------------------------------------------

	public static boolean isHidden(Player player) {
		return HIDDEN.contains(player.getUniqueId());
	}

	/** Turns the status line on or off for this player, remembered across restarts. */
	public static void setHidden(Player player, boolean hidden) {
		if (hidden) {
			HIDDEN.add(player.getUniqueId());
			player.getPersistentDataContainer().set(Keys.HUD_OFF, PersistentDataType.BYTE, (byte) 1);
			player.sendActionBar(Component.empty());
		} else {
			HIDDEN.remove(player.getUniqueId());
			player.getPersistentDataContainer().remove(Keys.HUD_OFF);
		}
	}

	public static void load(Player player) {
		if (player.getPersistentDataContainer().has(Keys.HUD_OFF, PersistentDataType.BYTE)) {
			HIDDEN.add(player.getUniqueId());
		}
	}

	public static void forget(UUID playerId) {
		FLASH_UNTIL.remove(playerId);
		HIDDEN.remove(playerId);
	}

	public static void clearAll() {
		FLASH_UNTIL.clear();
		HIDDEN.clear();
	}
}
