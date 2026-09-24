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
import net.unchartedsmp.bloodbath.config.Settings;
import net.unchartedsmp.bloodbath.fx.BloodFx;
import net.unchartedsmp.bloodbath.gui.ArmoryMenu;
import net.unchartedsmp.bloodbath.hud.Hud;
import net.unchartedsmp.bloodbath.pack.ResourcePackService;
import net.unchartedsmp.bloodbath.recipe.Recipes;
import net.unchartedsmp.bloodbath.weapon.Behaviors;
import net.unchartedsmp.bloodbath.weapon.WeaponType;
import net.unchartedsmp.bloodbath.weapon.Weapons;
import org.bukkit.Bukkit;
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
		new Sub("info", "info <weapon>", "What a weapon does", null),
		new Sub("hud", "hud [on|off]", "Toggle the cooldown line", null),
		new Sub("pack", "pack", "Get the 3D resource pack again", null),
		new Sub("give", "give <player> <weapon|all>", "Give weapons", GIVE),
		new Sub("reset", "reset [player]", "Clear cooldowns and clots", ADMIN),
		new Sub("status", "status", "Pack server, recipes, effects", ADMIN),
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
	}

	private void info(CommandSender sender, String[] args) {
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
		List<WeaponType> types;
		if (what.equalsIgnoreCase("all")) {
			types = Arrays.stream(WeaponType.values()).filter(Settings.get()::enabled).toList();
		} else {
			WeaponType type = WeaponType.find(what);
			if (type == null) {
				error(sender, "No weapon called '" + what + "'. Try /" + label + " list");
				return;
			}
			if (!Settings.get().enabled(type)) {
				error(sender, "The " + type.displayName() + " is disabled in config.yml.");
				return;
			}
			types = List.of(type);
		}
		for (Player target : targets) {
			for (WeaponType type : types) {
				giveTo(target, type);
			}
		}
		String weapons = types.size() == 1 ? "a " + types.get(0).displayName() : types.size() + " weapons";
		String who = targets.size() == 1 ? targets.get(0).getName() : targets.size() + " players";
		if (!(targets.size() == 1 && targets.get(0) == sender)) {
			reply(sender, "Gave " + weapons + " to " + who + ".");
		}
	}

	/** Puts a fresh weapon in the player's inventory (or at their feet, only they can pick it up). */
	public static void giveTo(Player player, WeaponType type) {
		for (ItemStack overflow : player.getInventory().addItem(Weapons.create(type)).values()) {
			player.getWorld().dropItem(player.getLocation(), overflow, item -> item.setOwner(player.getUniqueId()));
		}
		BloodFx.gather(Hud.handPos(player, false), 1.2, 6, 10);
		BloodFx.play(player, BloodFx.HEARTBEAT, 0.8F, 1.2F);
		player.sendMessage(Settings.get().prefix.append(Component.text("You take up the ", NamedTextColor.GRAY))
			.append(Component.text(type.displayName(), NamedTextColor.RED).hoverEvent(Weapons.create(type).asHoverEvent()))
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
		line(sender, "Tooltip frame", settings.tooltipFrame() ? "on" : "off (" + settings.tooltipFrame + ")");
		line(sender, "Recipes", settings.recipesEnabled ? Recipes.count() + " registered" : "off");
		line(sender, "Live effects", TickScheduler.pending() + " scheduled · " + Behaviors.MIRRORFANG.liveCount() + " blood mirrors");
		List<String> disabled = Arrays.stream(WeaponType.values()).filter(type -> !settings.enabled(type)).map(WeaponType::id).toList();
		line(sender, "Disabled weapons", disabled.isEmpty() ? "none" : String.join(", ", disabled));
		line(sender, "Disabled worlds", settings.disabledWorlds.isEmpty() ? "none" : String.join(", ", settings.disabledWorlds));
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
			case "info" -> args.length == 2 ? matching(last, weaponIds()) : List.of();
			case "hud" -> args.length == 2 ? matching(last, Stream.of("on", "off")) : List.of();
			case "pack", "reset" -> args.length == 2 && sender.hasPermission(ADMIN) ? matching(last, playerNames(true)) : List.of();
			case "give" -> {
				if (!sender.hasPermission(GIVE)) {
					yield List.of();
				}
				if (args.length == 2) {
					yield matching(last, Stream.concat(playerNames(false), Stream.concat(weaponIds(), Stream.of("all"))));
				}
				yield args.length == 3 ? matching(last, Stream.concat(weaponIds(), Stream.of("all"))) : List.of();
			}
			default -> List.of();
		};
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
