package net.unchartedsmp.bloodbath.weapon;

import java.time.Duration;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import net.unchartedsmp.bloodbath.config.Settings;
import net.unchartedsmp.bloodbath.fx.BloodFx;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** Counts kills on each weapon and celebrates rank-ups. */
public final class KillTracker {
	private KillTracker() {
	}

	/** {@code weapon} must be the live stack in the killer's hand so the change sticks. */
	public static void record(Player killer, ItemStack weapon, WeaponType type) {
		int kills = Weapons.kills(weapon) + 1;
		Rank before = Rank.of(kills - 1);
		Rank after = Rank.of(kills);
		Weapons.build(weapon, type, kills);
		if (after != before) {
			killer.showTitle(Title.title(
				Component.text(after.title(), after.color()),
				Component.text("Your " + type.displayName() + " has tasted " + kills + " kills", NamedTextColor.GRAY),
				Title.Times.times(Duration.ofMillis(250), Duration.ofSeconds(2), Duration.ofMillis(750))));
			killer.playSound(killer.getLocation(), BloodFx.RANK_UP, SoundCategory.PLAYERS, 0.8F, 0.8F);
			killer.sendMessage(Settings.get().prefix.append(Component.text("Your ", NamedTextColor.GRAY))
				.append(Component.text(type.displayName(), NamedTextColor.RED))
				.append(Component.text(" is now ", NamedTextColor.GRAY))
				.append(Component.text(after.title(), after.color()))
				.append(Component.text(".", NamedTextColor.GRAY)));
		}
	}
}
