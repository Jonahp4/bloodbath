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
import net.unchartedsmp.bloodbath.armor.ArmorPiece;
import net.unchartedsmp.bloodbath.armor.BloodArmor;
import net.unchartedsmp.bloodbath.config.Settings;
import net.unchartedsmp.bloodbath.core.BloodCore;
import net.unchartedsmp.bloodbath.fx.BloodFx;
import net.unchartedsmp.bloodbath.gui.ArmoryMenu;
import net.unchartedsmp.bloodbath.hud.Hud;
import net.unchartedsmp.bloodbath.pack.PackState;
import net.unchartedsmp.bloodbath.pack.ResourcePackService;
import net.unchartedsmp.bloodbath.recipe.Recipes;
import net.unchartedsmp.bloodbath.weapon.Behaviors;
import net.unchartedsmp.bloodbath.util.Damage;
import net.unchartedsmp.bloodbath.util.Targeting;
import net.unchartedsmp.bloodbath.weapon.WeaponType;
import net.unchartedsmp.bloodbath.weapon.Weapons;
import org.bukkit.Bukkit;
import org.bukkit.Location;
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
		new Sub("give", "give <player> <weapon|armor|core [n]|all>", "Give weapons, armor and Blood Cores", GIVE),
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
			case "give" -> give(sender, rest, label);
			case "reset" -> reset(sender, rest);
			case "status" -> status(sender);
			case "reload" -> reload(sender);
			case "debug" -> debug(sender);
			case "boss" -> boss(sender, rest);
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
		if (type == null) {
			error(sender, args.length == 0 ? "Which weapon? /bloodbath info <weapon>" : "No weapon called '" + String.join(" ", args) + "'.");
			return;
		}
		sendInfo(sender, type);
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
		line(sender, "Resource pack", packs.status() + (packs.isActive() && settings.packMode.equals("embedded")
			? " · " + packs.downloads() + " downloads · " + packs.size() / 1024 + " KB · sha1 " + packs.sha1().substring(0, 10) : ""));
		if (sender instanceof Player player && packs.isActive()) {
			line(sender, "Your pack address", String.valueOf(packs.address(player)));
		}
		line(sender, "Pack visuals", settings.packVisuals + " · " + PackState.count() + " of "
			+ Bukkit.getOnlinePlayers().size() + " online get them"
			+ (sender instanceof Player player ? (PackState.hasPack(player) ? " (you do)" : " (you don't: rejoin, /bb pack, or set pack-visuals: always)") : ""));
		line(sender, "Tooltip frame", settings.tooltipFrame() ? "on" : "off (" + settings.tooltipFrame + ")");
		line(sender, "Recipes", settings.recipesEnabled ? Recipes.count() + " registered" : "off");
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
			case "pack", "reset" -> args.length == 2 && sender.hasPermission(ADMIN) ? matching(last, playerNames(true)) : List.of();
			case "boss" -> args.length == 2 && sender.hasPermission(ADMIN) ? matching(last, Stream.of("summon", "stop", "status")) : List.of();
			case "give" -> {
				if (!sender.hasPermission(GIVE)) {
					yield List.of();
				}
				if (args.length == 2) {
					yield matching(last, Stream.concat(playerNames(false), Stream.concat(Stream.concat(weaponIds(), armorIds()), Stream.of("all", "blood_knight", "core"))));
				}
				yield args.length == 3 ? matching(last, Stream.concat(Stream.concat(weaponIds(), armorIds()), Stream.of("all", "blood_knight", "core"))) : List.of();
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
