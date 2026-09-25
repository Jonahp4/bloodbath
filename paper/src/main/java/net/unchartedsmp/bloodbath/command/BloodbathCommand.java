package net.unchartedsmp.bloodbath.command;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.unchartedsmp.bloodbath.BloodbathPlugin;
import net.unchartedsmp.bloodbath.ability.Cooldowns;
import net.unchartedsmp.bloodbath.ability.NullField;
import net.unchartedsmp.bloodbath.ability.TickScheduler;
import net.unchartedsmp.bloodbath.anvil.BloodAnvils;
import net.unchartedsmp.bloodbath.armor.ArmorPiece;
import net.unchartedsmp.bloodbath.armor.BloodArmor;
import net.unchartedsmp.bloodbath.blood.Bleeding;
import net.unchartedsmp.bloodbath.blood.BloodDrop;
import net.unchartedsmp.bloodbath.blood.BloodLevels;
import net.unchartedsmp.bloodbath.bloodlands.Bloodlands;
import net.unchartedsmp.bloodbath.config.Settings;
import net.unchartedsmp.bloodbath.core.BloodCore;
import net.unchartedsmp.bloodbath.fx.BloodFx;
import net.unchartedsmp.bloodbath.gui.ArmoryMenu;
import net.unchartedsmp.bloodbath.hud.Hud;
import net.unchartedsmp.bloodbath.pack.PackState;
import net.unchartedsmp.bloodbath.pack.ResourcePackService;
import net.unchartedsmp.bloodbath.portal.Frames;
import net.unchartedsmp.bloodbath.portal.Portal;
import net.unchartedsmp.bloodbath.recipe.Recipes;
import net.unchartedsmp.bloodbath.weapon.Behaviors;
import net.unchartedsmp.bloodbath.util.Damage;
import net.unchartedsmp.bloodbath.util.Targeting;
import net.unchartedsmp.bloodbath.weapon.WeaponType;
import net.unchartedsmp.bloodbath.weapon.Weapons;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** {@code /bloodbath} (aliases {@code /bb}, {@code /blood}). */
public final class BloodbathCommand implements TabExecutor {
	private static final String ADMIN = "bloodbath.admin";
	private static final String GIVE = "bloodbath.give";
	private static final String ARMORY = "bloodbath.armory";

	private record Sub(String name, String usage, String description, String permission) {
	}

	private static final List<Sub> SUBS = List.of(
		new Sub("armory", "armory", "Browse every weapon", ARMORY),
		new Sub("list", "list", "List the weapons", null),
		new Sub("info", "info <weapon|armor>", "What a weapon or armor piece does", null),
		new Sub("hud", "hud [on|off]", "Toggle the cooldown line", null),
		new Sub("pack", "pack", "Get the 3D resource pack again", null),
		new Sub("visuals", "visuals [on|off|auto]", "Custom particles, boss model and icons for you", null),
		new Sub("give", "give <player> <weapon|armor|core [n]|drop [n]|<n>|anvil|frame [n]|all>", "Give weapons, armor, cores, Blood Drops, anvils, frames", GIVE),
		new Sub("bloodlands", "bloodlands [tp]", "The Bloodlands: status, or go there", null),
		new Sub("portal", "portal <build <w> <h>|remove|list|frames [n]>", "Build, remove and list Bloodlands portals", ADMIN),
		new Sub("setspawn", "setspawn", "Set where portal travellers arrive in the Bloodlands", ADMIN),
		new Sub("level", "level <player> <level>", "Set the Blood Level of a player's held weapon", ADMIN),
		new Sub("reset", "reset [player]", "Clear cooldowns and clots", ADMIN),
		new Sub("status", "status", "Pack server, recipes, effects", ADMIN),
		new Sub("boss", "boss <summon|stop|status>", "The Blood Knight boss fight", ADMIN),
		new Sub("debug", "debug", "Show why ability hits land or don't", ADMIN),
		new Sub("reload", "reload", "Reload config.yml", ADMIN));

	private final BloodbathPlugin plugin;

	public BloodbathCommand(BloodbathPlugin plugin) {
		this.plugin = plugin;
	}

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		String sub = args.length == 0 ? "help" : switch (args[0].toLowerCase(Locale.ROOT)) {
			case "gui", "menu" -> "armory";
			case "?" -> "help";
			default -> args[0].toLowerCase(Locale.ROOT);
		};
		Sub known = SUBS.stream().filter(s -> s.name().equals(sub)).findFirst().orElse(null);
		if (known != null && known.permission() != null && !sender.hasPermission(known.permission())) {
			error(sender, "You don't have permission to do that.");
			return true;
		}
		String[] rest = Arrays.copyOfRange(args, Math.min(1, args.length), args.length);
		switch (sub) {
			case "armory" -> armory(sender);
			case "list" -> list(sender);
			case "info" -> info(sender, rest);
			case "hud" -> hud(sender, rest);
			case "pack" -> pack(sender, rest);
			case "visuals" -> visuals(sender, rest);
			case "give" -> give(sender, rest, label);
			case "reset" -> reset(sender, rest);
			case "status" -> status(sender);
			case "reload" -> reload(sender);
			case "debug" -> debug(sender);
			case "boss" -> boss(sender, rest);
			case "bloodlands" -> bloodlands(sender, rest);
			case "portal" -> portal(sender, rest);
			case "setspawn" -> setSpawn(sender);
			case "level" -> level(sender, rest);
			default -> help(sender, label);
		}
		return true;
	}

	// ---- subcommands -------------------------------------------------------------------------

	private void help(CommandSender sender, String label) {
		sender.sendMessage(Component.text("☠ ", NamedTextColor.DARK_RED)
			.append(Component.text("Bloodbath " + plugin.getPluginMeta().getVersion(), NamedTextColor.RED))
			.append(Component.text("  blood-soaked 3D ability weapons", NamedTextColor.GRAY)));
		for (Sub sub : SUBS) {
			if (sub.permission() != null && !sender.hasPermission(sub.permission())) {
				continue;
			}
			String usage = "/" + label + " " + sub.usage();
			sender.sendMessage(Component.text(" " + usage, NamedTextColor.RED)
				.clickEvent(ClickEvent.suggestCommand("/" + label + " " + sub.name() + " "))
				.hoverEvent(HoverEvent.showText(Component.text("Click to type it", NamedTextColor.GRAY)))
				.append(Component.text("  " + sub.description(), NamedTextColor.GRAY)));
		}
	}

	private void armory(CommandSender sender) {
		if (sender instanceof Player player) {
			ArmoryMenu.open(player);
		} else {
			list(sender);
		}
	}

	private void list(CommandSender sender) {
		Settings settings = Settings.get();
		sender.sendMessage(settings.prefix.append(Component.text("The armory (" + WeaponType.values().length + " weapons):", NamedTextColor.GRAY)));
		boolean canGive = sender.hasPermission(GIVE) && sender instanceof Player;
		for (WeaponType type : WeaponType.values()) {
			boolean enabled = settings.enabled(type);
			Component line = Component.text(" ▪ ", NamedTextColor.DARK_RED)
				.append(Component.text(type.displayName(), enabled ? NamedTextColor.RED : NamedTextColor.DARK_GRAY)
					.hoverEvent(Weapons.create(type).asHoverEvent())
					.clickEvent(ClickEvent.runCommand("/bloodbath info " + type.id())))
				.append(Component.text("  " + type.ability().displayName() + " · " + Weapons.cooldownText(type), NamedTextColor.GRAY));
			if (!enabled) {
				line = line.append(Component.text("  (disabled)", NamedTextColor.DARK_GRAY));
			} else if (canGive) {
				line = line.append(Component.text("  [take]", NamedTextColor.DARK_RED)
					.clickEvent(ClickEvent.runCommand("/bloodbath give " + sender.getName() + " " + type.id()))
					.hoverEvent(HoverEvent.showText(Component.text("Give yourself one", NamedTextColor.GRAY))));
			}
			sender.sendMessage(line);
		}
		sender.sendMessage(Component.text(" Blood Knight armor", settings.armorEnabled ? NamedTextColor.DARK_RED : NamedTextColor.DARK_GRAY)
			.append(Component.text(settings.armorEnabled ? "  (set bonus at 2 and 4 pieces)" : "  (disabled)", NamedTextColor.GRAY)));
		for (ArmorPiece piece : ArmorPiece.values()) {
			Component line = Component.text(" ▪ ", NamedTextColor.DARK_RED)
				.append(Component.text(piece.displayName(), settings.armorEnabled ? NamedTextColor.RED : NamedTextColor.DARK_GRAY)
					.hoverEvent(BloodArmor.create(piece).asHoverEvent())
					.clickEvent(ClickEvent.runCommand("/bloodbath info " + piece.id())));
			if (settings.armorEnabled && canGive) {
				line = line.append(Component.text("  [take]", NamedTextColor.DARK_RED)
					.clickEvent(ClickEvent.runCommand("/bloodbath give " + sender.getName() + " " + piece.id())));
			}
			sender.sendMessage(line);
		}
	}

	private void info(CommandSender sender, String[] args) {
		ArmorPiece piece = args.length == 0 ? null : ArmorPiece.find(String.join(" ", args));
		if (piece != null) {
			sendInfo(sender, piece);
			return;
		}
		WeaponType type = args.length == 0 ? null : WeaponType.find(String.join(" ", args));
		if (type == null && args.length == 0 && sender instanceof Player player) {
			type = Weapons.typeOf(player.getInventory().getItemInMainHand());
		}
		if (type == null && args.length == 0) {
			bloodlands(sender, new String[0]);
			sender.sendMessage(Component.text(" /bloodbath info <weapon> for a weapon's details.", NamedTextColor.DARK_GRAY));
			return;
		}
		if (type == null) {
			error(sender, "No weapon called '" + String.join(" ", args) + "'.");
			return;
		}
		sendInfo(sender, type);
		if (args.length == 0 && sender instanceof Player player && Weapons.typeOf(player.getInventory().getItemInMainHand()) == type) {
			int level = BloodLevels.level(player.getInventory().getItemInMainHand());
			sender.sendMessage(Component.text(" Blood Level " + BloodLevels.numeral(level) + " " + BloodLevels.pips(level) + "  ", NamedTextColor.DARK_RED)
				.append(Component.text(level == 0 ? "not bled yet: take it to a Blood Anvil" : BloodLevels.effects(type, level), NamedTextColor.GRAY)));
		}
	}

	/** An armour piece's name (hover for the item), what it does and the set bonus. */
	public static void sendInfo(CommandSender sender, ArmorPiece piece) {
		Settings settings = Settings.get();
		ItemStack item = BloodArmor.create(piece);
		sender.sendMessage(settings.prefix.append(Component.text(piece.displayName(), NamedTextColor.RED).hoverEvent(item.asHoverEvent()))
			.append(settings.armorEnabled ? Component.empty() : Component.text("  (disabled)", NamedTextColor.DARK_GRAY)));
		sender.sendMessage(Component.text(" " + String.join(" ", piece.description()), NamedTextColor.GRAY));
		sender.sendMessage(Component.text(" Netherite protection +" + (int) piece.armor() + " armor, plus 1 heart.", NamedTextColor.DARK_GRAY));
		for (Component line : item.getItemMeta().lore().subList(piece.description().size() + 1, item.getItemMeta().lore().size())) {
			sender.sendMessage(Component.text(" ").append(line));
		}
		if (sender instanceof Player && sender.hasPermission(GIVE) && settings.armorEnabled) {
			sender.sendMessage(Component.text(" [take one]", NamedTextColor.RED)
				.clickEvent(ClickEvent.runCommand("/bloodbath give " + sender.getName() + " " + piece.id())));
		}
	}

	/** A weapon's name (hover for the item), what it does, its ability and stats. */
	public static void sendInfo(CommandSender sender, WeaponType type) {
		Settings settings = Settings.get();
		ItemStack item = Weapons.create(type);
		sender.sendMessage(settings.prefix.append(Component.text(type.displayName(), NamedTextColor.RED).hoverEvent(item.asHoverEvent()))
			.append(settings.enabled(type) ? Component.empty() : Component.text("  (disabled)", NamedTextColor.DARK_GRAY)));
		sender.sendMessage(Component.text(" " + String.join(" ", type.description()), NamedTextColor.GRAY));
		sender.sendMessage(Component.text(" " + type.ability().displayName() + " · cooldown " + Weapons.cooldownText(type), NamedTextColor.DARK_RED)
			.append(Component.text("  " + Weapons.stats(type), NamedTextColor.DARK_GRAY)));
		if (sender instanceof Player && sender.hasPermission(GIVE) && settings.enabled(type)) {
			sender.sendMessage(Component.text(" [take one]", NamedTextColor.RED)
				.clickEvent(ClickEvent.runCommand("/bloodbath give " + sender.getName() + " " + type.id())));
		}
	}

	private void hud(CommandSender sender, String[] args) {
		if (!(sender instanceof Player player)) {
			error(sender, "Only players have a HUD.");
			return;
		}
		boolean hide = args.length == 0 ? !Hud.isHidden(player) : args[0].equalsIgnoreCase("off");
		Hud.setHidden(player, hide);
		reply(sender, hide ? "Cooldown line hidden. /bloodbath hud to bring it back." : "Cooldown line shown while you hold a weapon.");
	}

	private void pack(CommandSender sender, String[] args) {
		ResourcePackService packs = plugin.packs();
		if (!packs.isActive()) {
			error(sender, "The resource pack isn't being sent on this server (" + packs.status() + ").");
			return;
		}
		if (args.length == 0) {
			if (sender instanceof Player player) {
				packs.send(player);
				reply(sender, "Sending the Bloodbath resource pack...");
			} else {
				error(sender, "Usage: /bloodbath pack <player|all>");
			}
			return;
		}
		if (!sender.hasPermission(ADMIN)) {
			error(sender, "You can only send the pack to yourself.");
			return;
		}
		List<Player> targets = players(args[0]);
		if (targets.isEmpty()) {
			error(sender, "No online player '" + args[0] + "'.");
			return;
		}
		int sent = 0;
		for (Player target : targets) {
			if (packs.send(target)) {
				sent++;
			}
		}
		reply(sender, "Sent the pack to " + sent + (sent == 1 ? " player." : " players."));
	}

	/**
	 * The pack-only visuals for this player: on (they installed the pack themselves), off, or auto
	 * (whatever their game reports). Remembered on the player.
	 */
	private void visuals(CommandSender sender, String[] args) {
		if (!(sender instanceof Player player)) {
			error(sender, "Only players see visuals.");
			return;
		}
		if (args.length > 0) {
			switch (args[0].toLowerCase(Locale.ROOT)) {
				case "on" -> PackState.setOverride(player, Boolean.TRUE);
				case "off" -> PackState.setOverride(player, Boolean.FALSE);
				case "auto" -> PackState.setOverride(player, null);
				default -> {
					error(sender, "Usage: /bloodbath visuals [on|off|auto]");
					return;
				}
			}
			plugin.bosses().packChanged(player);
		}
		reply(sender, PackState.describe(player));
	}

	private void give(CommandSender sender, String[] args, String label) {
		if (args.length == 0) {
			error(sender, "Usage: /" + label + " give <player> <weapon|all>");
			return;
		}
		List<Player> targets;
		String what;
		if (args.length == 1) {
			if (!(sender instanceof Player self)) {
				error(sender, "Usage: /" + label + " give <player> <weapon|all>");
				return;
			}
			targets = List.of(self);
			what = args[0];
		} else {
			targets = players(args[0]);
			what = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
			if (targets.isEmpty()) {
				error(sender, "No online player '" + args[0] + "'.");
				return;
			}
		}
		Settings settings = Settings.get();
		List<WeaponType> types = new ArrayList<>();
		List<ArmorPiece> pieces = new ArrayList<>();
		String key = what.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
		Integer bare = number(key);
		if (bare != null || key.equals("drop") || key.startsWith("drop_") || key.equals("drops") || key.startsWith("drops_")
			|| key.equals("blood_drop") || key.startsWith("blood_drop_")) {
			// "/blood give Steve 5", "drop", "drop 5", "blood_drop 3"
			int amount = bare != null ? bare : trailingNumber(key, 1);
			for (Player target : targets) {
				give(target, BloodDrop.create(Math.min(64, amount)), amount == 1 ? "Blood Drop" : amount + " Blood Drops");
			}
			if (!(targets.size() == 1 && targets.get(0) == sender)) {
				reply(sender, "Gave " + amount + " Blood Drop" + (amount == 1 ? "" : "s") + " to " + (targets.size() == 1 ? targets.get(0).getName() : targets.size() + " players") + ".");
			}
			return;
		}
		if (key.equals("anvil") || key.equals("blood_anvil")) {
			for (Player target : targets) {
				give(target, BloodAnvils.item(1), "Blood Anvil");
			}
			return;
		}
		if (key.equals("frame") || key.startsWith("frame_") || key.equals("frames") || key.startsWith("frames_")
			|| key.startsWith("bloodstone_frame")) {
			int amount = trailingNumber(key, 16);
			for (Player target : targets) {
				give(target, Frames.item(Math.min(64, amount)), amount + " Bloodstone Frames");
			}
			return;
		}
		if (key.equals("core") || key.startsWith("core_") || key.equals("blood_core") || key.startsWith("blood_core_")) {
			// "core", "core 16", "blood_core 4"
			String[] parts = key.split("_");
			int amount = 1;
			try {
				amount = Integer.parseInt(parts[parts.length - 1]);
			} catch (NumberFormatException ignored) {
				// no amount: one
			}
			for (Player target : targets) {
				giveCores(target, amount);
			}
			return;
		}
		if (key.equals("all")) {
			types.addAll(Arrays.stream(WeaponType.values()).filter(settings::enabled).toList());
			if (settings.armorEnabled) {
				pieces.addAll(List.of(ArmorPiece.values()));
			}
		} else if (key.equals("armor") || key.equals("armour") || key.equals("blood_knight") || key.equals("set")) {
			pieces.addAll(List.of(ArmorPiece.values()));
		} else if (ArmorPiece.find(what) != null) {
			pieces.add(ArmorPiece.find(what));
		} else {
			WeaponType type = WeaponType.find(what);
			if (type == null) {
				error(sender, "No weapon or armor called '" + what + "'. Try /" + label + " list");
				return;
			}
			if (!settings.enabled(type)) {
				error(sender, "The " + type.displayName() + " is disabled in config.yml.");
				return;
			}
			types.add(type);
		}
		if (!pieces.isEmpty() && !settings.armorEnabled) {
			error(sender, "Blood Knight armor is disabled in config.yml.");
			return;
		}
		for (Player target : targets) {
			for (WeaponType type : types) {
				giveTo(target, type);
			}
			for (ArmorPiece piece : pieces) {
				giveTo(target, piece);
			}
		}
		int count = types.size() + pieces.size();
		String things = count == 1 ? "a " + (types.isEmpty() ? pieces.get(0).displayName() : types.get(0).displayName())
			: pieces.size() == 4 && types.isEmpty() ? "the Blood Knight set" : count + " items";
		String who = targets.size() == 1 ? targets.get(0).getName() : targets.size() + " players";
		if (!(targets.size() == 1 && targets.get(0) == sender)) {
			reply(sender, "Gave " + things + " to " + who + ".");
		}
	}

	/** Puts a fresh weapon in the player's inventory (or at their feet, only they can pick it up). */
	public static void giveTo(Player player, WeaponType type) {
		give(player, Weapons.create(type), type.displayName());
	}

	public static void giveTo(Player player, ArmorPiece piece) {
		give(player, BloodArmor.create(piece), piece.displayName());
	}

	public static void giveCores(Player player, int amount) {
		ItemStack cores = BloodCore.create(amount);
		give(player, cores, cores.getAmount() == 1 ? "Blood Core" : cores.getAmount() + " Blood Cores");
	}

	private static void give(Player player, ItemStack stack, String name) {
		for (ItemStack overflow : player.getInventory().addItem(stack.clone()).values()) {
			player.getWorld().dropItem(player.getLocation(), overflow, item -> item.setOwner(player.getUniqueId()));
		}
		BloodFx.gather(Hud.handPos(player, false), 1.2, 6, 10);
		BloodFx.play(player, BloodFx.HEARTBEAT, 0.8F, 1.2F);
		player.sendMessage(Settings.get().prefix.append(Component.text("You take up the ", NamedTextColor.GRAY))
			.append(Component.text(name, NamedTextColor.RED).hoverEvent(stack.asHoverEvent()))
			.append(Component.text(".", NamedTextColor.GRAY)));
	}

	private void reset(CommandSender sender, String[] args) {
		List<Player> targets;
		if (args.length == 0) {
			if (!(sender instanceof Player self)) {
				error(sender, "Usage: /bloodbath reset <player|all>");
				return;
			}
			targets = List.of(self);
		} else {
			targets = players(args[0]);
		}
		if (targets.isEmpty()) {
			error(sender, "No online player '" + args[0] + "'.");
			return;
		}
		for (Player target : targets) {
			Cooldowns.reset(target.getUniqueId());
			NullField.clear(target.getUniqueId());
		}
		reply(sender, "Cleared cooldowns and clots for " + (targets.size() == 1 ? targets.get(0).getName() : targets.size() + " players") + ".");
	}

	private void status(CommandSender sender) {
		Settings settings = Settings.get();
		ResourcePackService packs = plugin.packs();
		reply(sender, "Bloodbath " + plugin.getPluginMeta().getVersion());
		line(sender, "Resource pack", packs.status() + (packs.isActive() && !settings.packMode.equals("url")
			? " · " + packs.downloads() + " served here · " + packs.size() / 1024 + " KB · sha1 " + packs.sha1().substring(0, 10) : ""));
		if (sender instanceof Player player && packs.isActive()) {
			line(sender, "Your pack address", String.valueOf(packs.address(player)));
		}
		line(sender, "Pack visuals", settings.packVisuals + " · " + PackState.count() + " of "
			+ Bukkit.getOnlinePlayers().size() + " online get them"
			+ (sender instanceof Player player ? (PackState.hasPack(player) ? " (you do)" : " (you don't: rejoin, /bb pack, or set pack-visuals: always)") : ""));
		line(sender, "Tooltip frame", settings.tooltipFrame() ? "on" : "off (" + settings.tooltipFrame + ")");
		line(sender, "Recipes", settings.recipesEnabled ? Recipes.count() + " registered" : "off");
		line(sender, "Bloodlands", plugin.bloodlands().status() + " · " + plugin.portals().all().size() + " lit portals · "
			+ plugin.anvils().openCount() + " Blood Anvils in use · " + Bleeding.count() + " bleeding");
		line(sender, "Live effects", TickScheduler.pending() + " scheduled · " + Behaviors.MIRRORFANG.liveCount() + " blood mirrors");
		List<String> disabled = Arrays.stream(WeaponType.values()).filter(type -> !settings.enabled(type)).map(WeaponType::id).toList();
		line(sender, "Disabled weapons", disabled.isEmpty() ? "none" : String.join(", ", disabled));
		line(sender, "Disabled worlds", settings.disabledWorlds.isEmpty() ? "none" : String.join(", ", settings.disabledWorlds));
	}

	private void boss(CommandSender sender, String[] args) {
		String action = args.length == 0 ? "status" : args[0].toLowerCase(Locale.ROOT);
		switch (action) {
			case "summon" -> {
				if (!(sender instanceof Player player)) {
					error(sender, "Stand where it should rise.");
					return;
				}
				Location at = Targeting.lookBlock(player, 24.0).map(hit -> hit.getBlock().getLocation().add(0.5, 1.0, 0.5))
					.orElse(player.getLocation());
				at.setYaw(player.getLocation().getYaw() + 180.0F);
				String refused = plugin.bosses().refusal(at);
				if (refused != null) {
					error(sender, refused);
					return;
				}
				plugin.bosses().summon(at);
				reply(sender, "The Blood Knight is rising at " + at.getBlockX() + " " + at.getBlockY() + " " + at.getBlockZ() + ".");
			}
			case "stop" -> {
				int stopped = sender instanceof Player player && !(args.length > 1 && args[1].equalsIgnoreCase("all"))
					? plugin.bosses().stop(player.getLocation(), 256.0) : plugin.bosses().stop(null, 0);
				reply(sender, stopped == 0 ? "No Blood Knight nearby. (/bloodbath boss stop all for every world.)"
					: "Sent " + stopped + " Blood Knight" + (stopped == 1 ? "" : "s") + " back into the earth.");
			}
			default -> {
				List<String> lines = plugin.bosses().describe();
				reply(sender, lines.isEmpty() ? "No Blood Knight is abroad. Summon one: sneak and right-click crying obsidian"
					+ " with a Blood Core, or /bloodbath boss summon." : "Blood Knights: " + String.join("; ", lines));
			}
		}
	}

	private void debug(CommandSender sender) {
		if (!(sender instanceof Player player)) {
			error(sender, "Only players can watch hits.");
			return;
		}
		boolean on = Damage.toggleDebug(player.getUniqueId());
		reply(sender, on
			? "Watching ability hits: each one is reported in chat with what happened to it. /bb debug again to stop."
			: "Stopped watching ability hits.");
	}

	private void reload(CommandSender sender) {
		plugin.reload();
		reply(sender, "Reloaded config.yml. Weapons update as players hold them.");
	}

	// ---- the Bloodlands ------------------------------------------------------------------------

	private void bloodlands(CommandSender sender, String[] args) {
		Bloodlands lands = plugin.bloodlands();
		if (args.length > 0 && args[0].equalsIgnoreCase("tp")) {
			if (!sender.hasPermission(ADMIN)) {
				error(sender, "Only admins can go straight there. Find or build a portal.");
				return;
			}
			if (!(sender instanceof Player player)) {
				error(sender, "Only players can travel.");
				return;
			}
			if (!lands.isOpen()) {
				error(sender, "The Bloodlands aren't open: " + lands.status() + ".");
				return;
			}
			plugin.portals().arrival(lands.world(), lands.spawn(), 2).thenAccept(spot -> Bukkit.getScheduler().runTask(plugin,
				() -> player.teleportAsync(spot)));
			reply(sender, "Into the Bloodlands...");
			return;
		}
		reply(sender, "The Bloodlands: " + lands.status() + ".");
		if (lands.isOpen()) {
			Location spawn = lands.spawn();
			line(sender, "World", lands.world().getName() + " · arrivals at " + spawn.getBlockX() + " " + spawn.getBlockY() + " " + spawn.getBlockZ());
			line(sender, "Players there", String.valueOf(lands.world().getPlayers().size()));
		}
		line(sender, "Portals", plugin.portals().all().size() + " lit");
		line(sender, "How to get there", "a ring of Bloodstone Frames (any size, " + Settings.get().blood.portal().minWidth() + "×"
			+ Settings.get().blood.portal().minHeight() + " inside at least), lit with flint and steel");
	}

	private void portal(CommandSender sender, String[] args) {
		String action = args.length == 0 ? "list" : args[0].toLowerCase(Locale.ROOT);
		switch (action) {
			case "build", "create" -> {
				if (!(sender instanceof Player player)) {
					error(sender, "Stand where it should go.");
					return;
				}
				int width = args.length > 1 ? parse(args[1], 3) : 3;
				int height = args.length > 2 ? parse(args[2], 4) : 4;
				if (width < 1 || height < 1 || width > 64 || height > 64) {
					error(sender, "Sizes are 1 to 64 (the inside of the frame).");
					return;
				}
				Portal portal = plugin.portals().build(player, width, height);
				if (portal == null) {
					error(sender, "Another portal is in the way.");
					return;
				}
				reply(sender, "Built and lit a " + portal.width() + "×" + portal.height() + " portal at " + portal.x() + " " + portal.y() + " " + portal.z() + ".");
			}
			case "remove", "delete" -> {
				if (!(sender instanceof Player player)) {
					error(sender, "Look at the portal to remove.");
					return;
				}
				Portal portal = lookedAt(player);
				if (portal == null) {
					error(sender, "Look at a lit portal (its inside or its frame), within 16 blocks.");
					return;
				}
				plugin.portals().destroy(portal, true);
				reply(sender, "Removed the " + portal.width() + "×" + portal.height() + " portal and its frame (the frame blocks dropped).");
			}
			case "frames" -> {
				if (!(sender instanceof Player player)) {
					error(sender, "Only players can hold frames.");
					return;
				}
				int amount = args.length > 1 ? parse(args[1], 16) : 16;
				give(player, Frames.item(Math.max(1, Math.min(64, amount))), Math.max(1, Math.min(64, amount)) + " Bloodstone Frames");
			}
			default -> {
				reply(sender, plugin.portals().all().isEmpty() ? "No portals are lit." : plugin.portals().all().size() + " lit portals:");
				for (Portal portal : plugin.portals().all()) {
					World world = Bukkit.getWorld(portal.world());
					line(sender, world == null ? "(unloaded world)" : world.getName(), portal.describe());
				}
			}
		}
	}

	private Portal lookedAt(Player player) {
		var hit = player.rayTraceBlocks(16.0, org.bukkit.FluidCollisionMode.NEVER);
		if (hit == null || hit.getHitBlock() == null) {
			// The inside is light blocks, which a ray passes through: walk the ray instead.
			for (double d = 0.5; d <= 16; d += 0.5) {
				Portal portal = plugin.portals().at(player.getEyeLocation().add(player.getEyeLocation().getDirection().multiply(d)).getBlock());
				if (portal != null) {
					return portal;
				}
			}
			return null;
		}
		Portal direct = plugin.portals().at(hit.getHitBlock());
		if (direct != null) {
			return direct;
		}
		for (double d = 0.5; d <= 16; d += 0.5) {
			Portal portal = plugin.portals().at(player.getEyeLocation().add(player.getEyeLocation().getDirection().multiply(d)).getBlock());
			if (portal != null) {
				return portal;
			}
		}
		return Frames.isFrame(hit.getHitBlock()) ? plugin.portals().framing(hit.getHitBlock()) : null;
	}

	private void setSpawn(CommandSender sender) {
		if (!(sender instanceof Player player)) {
			error(sender, "Stand where travellers should arrive.");
			return;
		}
		Bloodlands lands = plugin.bloodlands();
		if (!lands.isBloodlands(player.getWorld())) {
			error(sender, "Stand in the Bloodlands (" + (lands.isOpen() ? lands.world().getName() : "not open yet") + ") to set its arrival point.");
			return;
		}
		lands.setSpawn(player.getLocation());
		Location at = player.getLocation();
		reply(sender, "Portal travellers now arrive at " + at.getBlockX() + " " + at.getBlockY() + " " + at.getBlockZ() + ".");
	}

	private void level(CommandSender sender, String[] args) {
		if (args.length < 2) {
			error(sender, "Usage: /bloodbath level <player> <level 0-" + Settings.get().blood.maxLevel() + ">");
			return;
		}
		Player target = Bukkit.getPlayerExact(args[0]);
		if (target == null) {
			error(sender, "No online player '" + args[0] + "'.");
			return;
		}
		Integer level = number(args[1]);
		int max = Settings.get().blood.maxLevel();
		if (level == null || level < 0 || level > max) {
			error(sender, "The Blood Level is a number from 0 to " + max + ".");
			return;
		}
		ItemStack held = target.getInventory().getItemInMainHand();
		WeaponType type = Weapons.typeOf(held);
		if (type == null) {
			error(sender, target.getName() + " isn't holding a Bloodbath weapon.");
			return;
		}
		BloodLevels.set(held, level);
		reply(sender, target.getName() + "'s " + type.displayName() + " is now Blood Level " + BloodLevels.numeral(level) + ".");
	}

	private static Integer number(String text) {
		try {
			return Integer.parseInt(text.trim());
		} catch (NumberFormatException e) {
			return null;
		}
	}

	private static int trailingNumber(String key, int fallback) {
		String[] parts = key.split("_");
		Integer n = number(parts[parts.length - 1]);
		return n == null ? fallback : Math.max(1, n);
	}

	private static int parse(String text, int fallback) {
		Integer n = number(text);
		return n == null ? fallback : n;
	}

	// ---- helpers -----------------------------------------------------------------------------

	private static List<Player> players(String name) {
		if (name.equalsIgnoreCase("all") || name.equals("@a")) {
			return new ArrayList<>(Bukkit.getOnlinePlayers());
		}
		Player player = Bukkit.getPlayerExact(name);
		return player == null ? List.of() : List.of(player);
	}

	private static void reply(CommandSender sender, String message) {
		sender.sendMessage(Settings.get().prefix.append(Component.text(message, NamedTextColor.GRAY)));
	}

	private static void line(CommandSender sender, String label, String value) {
		sender.sendMessage(Component.text(" " + label + ": ", NamedTextColor.DARK_RED).append(Component.text(value, NamedTextColor.GRAY)));
	}

	private static void error(CommandSender sender, String message) {
		sender.sendMessage(Settings.get().prefix.append(Component.text(message, NamedTextColor.RED)));
	}

	// ---- tab completion ----------------------------------------------------------------------

	@Override
	public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
		if (args.length == 1) {
			return matching(args[0], SUBS.stream()
				.filter(sub -> sub.permission() == null || sender.hasPermission(sub.permission()))
				.map(Sub::name));
		}
		String sub = args[0].toLowerCase(Locale.ROOT);
		String last = args[args.length - 1];
		return switch (sub) {
			case "info" -> args.length == 2 ? matching(last, Stream.concat(weaponIds(), armorIds())) : List.of();
			case "hud" -> args.length == 2 ? matching(last, Stream.of("on", "off")) : List.of();
			case "visuals" -> args.length == 2 ? matching(last, Stream.of("on", "off", "auto")) : List.of();
			case "pack", "reset" -> args.length == 2 && sender.hasPermission(ADMIN) ? matching(last, playerNames(true)) : List.of();
			case "boss" -> args.length == 2 && sender.hasPermission(ADMIN) ? matching(last, Stream.of("summon", "stop", "status")) : List.of();
			case "portal" -> args.length == 2 && sender.hasPermission(ADMIN) ? matching(last, Stream.of("build", "remove", "list", "frames")) : List.of();
			case "bloodlands" -> args.length == 2 && sender.hasPermission(ADMIN) ? matching(last, Stream.of("tp")) : List.of();
			case "level" -> !sender.hasPermission(ADMIN) ? List.of() : args.length == 2 ? matching(last, playerNames(false))
				: args.length == 3 ? matching(last, java.util.stream.IntStream.rangeClosed(0, Settings.get().blood.maxLevel()).mapToObj(String::valueOf)) : List.of();
			case "give" -> {
				if (!sender.hasPermission(GIVE)) {
					yield List.of();
				}
				if (args.length == 2) {
					yield matching(last, Stream.concat(playerNames(false), Stream.concat(Stream.concat(weaponIds(), armorIds()), Stream.of("all", "blood_knight", "core", "drop", "anvil", "frame"))));
				}
				yield args.length == 3 ? matching(last, Stream.concat(Stream.concat(weaponIds(), armorIds()), Stream.of("all", "blood_knight", "core", "drop", "anvil", "frame"))) : List.of();
			}
			default -> List.of();
		};
	}

	private static Stream<String> armorIds() {
		return Arrays.stream(ArmorPiece.values()).map(ArmorPiece::id);
	}

	private static Stream<String> weaponIds() {
		return Arrays.stream(WeaponType.values()).map(WeaponType::id);
	}

	private static Stream<String> playerNames(boolean withAll) {
		Stream<String> names = Bukkit.getOnlinePlayers().stream().map(Player::getName);
		return withAll ? Stream.concat(names, Stream.of("all")) : names;
	}

	private static List<String> matching(String prefix, Stream<String> options) {
		String lower = prefix.toLowerCase(Locale.ROOT);
		return options.filter(option -> option.toLowerCase(Locale.ROOT).startsWith(lower)).sorted().toList();
	}
}
