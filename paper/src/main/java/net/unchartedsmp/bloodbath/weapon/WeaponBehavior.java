package net.unchartedsmp.bloodbath.weapon;

import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.unchartedsmp.bloodbath.ability.Ability;
import net.unchartedsmp.bloodbath.config.Settings;
import net.unchartedsmp.bloodbath.fx.BloodFx;
import net.unchartedsmp.bloodbath.hud.Hud;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

/** What a weapon does. One instance per weapon type, see {@link Behaviors}. */
public interface WeaponBehavior {
	WeaponType type();

	default Ability ability() {
		return type().ability();
	}

	/** Right-click (already checked: permission, world, enabled, not clotted). */
	default void use(Player player) {
	}

	/** Left-click on a block (same checks as {@link #use}). */
	default void punchBlock(Player player, Block block) {
	}

	/** A melee hit landed with this weapon (never called for ability damage). */
	default void melee(Player player, LivingEntity target, double damage) {
	}

	/** The Clotblade's own field doesn't stop the Clotblade. */
	default boolean canBeNullified() {
		return true;
	}

	/** Action-bar line while held. */
	default Component hud(Player player) {
		return Hud.cooldownBar(player, ability());
	}

	/** Accent particle mixed into the dripping aura while held. */
	default BloodFx.Fx auraAccent() {
		return BloodFx.BLOOD;
	}

	/** A number from this weapon's section of config.yml, e.g. {@code setting("damage", 5)}. */
	default double setting(String key, double fallback) {
		return Settings.get().num(type(), key, fallback);
	}

	/** A duration configured in seconds, as ticks. */
	default int ticksSetting(String secondsKey, int fallbackTicks) {
		return Settings.get().ticks(type(), secondsKey, fallbackTicks);
	}

	/** Called every server tick. */
	default void tick(long now) {
	}

	/** The player left: drop short-lived personal state (never cooldowns). */
	default void forget(UUID playerId) {
	}

	/** Periodic cleanup of expired state. */
	default void prune() {
	}

	/** Plugin disabling. */
	default void shutdown() {
	}
}
