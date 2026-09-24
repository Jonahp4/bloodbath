package net.unchartedsmp.bloodbath.weapon;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.unchartedsmp.bloodbath.config.Settings;
import net.unchartedsmp.bloodbath.hud.Hud;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;

/** Whether a player may use a weapon's powers right now, with a reason on the action bar if not. */
public final class Gate {
	public static final String USE_PERMISSION = "bloodbath.use";

	private Gate() {
	}

	/** @param silent true for passive effects (on-hit, bow shots) that shouldn't nag */
	public static boolean allows(Player player, WeaponType type, boolean silent) {
		if (player.getGameMode() == GameMode.SPECTATOR) {
			return false;
		}
		Settings settings = Settings.get();
		String refusal = null;
		if (!settings.enabled(type)) {
			refusal = "The " + type.displayName() + " has been sealed away on this server.";
		} else if (!player.hasPermission(USE_PERMISSION)) {
			refusal = "You can't wield Bloodbath weapons.";
		} else if (!settings.abilitiesAllowedIn(player.getWorld())) {
			refusal = "Blood magic doesn't work in this world.";
		}
		if (refusal == null) {
			return true;
		}
		if (!silent) {
			Hud.flash(player, Component.text(refusal, NamedTextColor.DARK_RED));
		}
		return false;
	}
}
