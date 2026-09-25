package net.unchartedsmp.bloodbath.anvil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.unchartedsmp.bloodbath.BloodbathPlugin;
import net.unchartedsmp.bloodbath.Keys;
import net.unchartedsmp.bloodbath.config.Settings;
import net.unchartedsmp.bloodbath.fx.BloodFx;
import net.unchartedsmp.bloodbath.hud.Hud;
import net.unchartedsmp.bloodbath.weapon.Weapons;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemRarity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

/**
 * Blood Anvils: the blocks, and every open Blood Anvil screen.
 *
 * <p>A Blood Anvil is a barrier block (solid, unbreakable in survival, immune to explosions and
 * pistons) with the anvil's model standing in it as an item display; like frames, it's known by
 * its chunk's persistent data, not by its look. Right-click opens it; sneak and punch it with an
 * empty hand to take it apart (if a protection plugin lets you break blocks there).
 *
 * <p>The screens: see {@link AnvilMenu}. This class feeds them clicks, and makes sure the items in
 * them always go back to their owner, whatever ends the session: closing it, logging out, dying,
 * the anvil being taken apart, the plugin stopping, or the server crashing (the escrow).
 */
public final class BloodAnvils implements Listener {
	public static final String ID = "blood_anvil";

	private final BloodbathPlugin plugin;
	private final Escrow escrow;
	private final Map<UUID, AnvilMenu> open = new HashMap<>();
	/** Anvil block key -> its display (while loaded). */
	private final Map<Long, ItemDisplay> displays = new HashMap<>();
	private final Set<UUID> sweeping = new HashSet<>();

	public BloodAnvils(BloodbathPlugin plugin) {
		this.plugin = plugin;
		this.escrow = new Escrow(plugin.getDataFolder(), plugin.getLogger());
	}

	public void enable() {
		for (World world : Bukkit.getWorlds()) {
			for (Chunk chunk : world.getLoadedChunks()) {
				spawnDisplays(chunk);
			}
		}
		for (Player player : Bukkit.getOnlinePlayers()) {
			restore(player);
		}
	}

	/** Plugin stopping: every open screen gives its items back and closes; the displays go. */
	public void disable() {
		for (AnvilMenu menu : new ArrayList<>(open.values())) {
			Player player = Bukkit.getPlayer(menu.owner());
			if (player != null) {
				finish(player, menu);
				player.closeInventory();
			}
		}
		open.clear();
		displays.values().forEach(ItemDisplay::remove);
		displays.clear();
	}

	// ---- the item and the block ---------------------------------------------------------------

	public static ItemStack item(int amount) {
		ItemStack anvil = new ItemStack(Material.ANVIL, Math.max(1, Math.min(64, amount)));
		anvil.editMeta(meta -> {
			meta.itemName(Component.text("Blood Anvil", NamedTextColor.RED));
			meta.lore(List.of(
				line("Black iron, split by old red cracks.", NamedTextColor.GRAY),
				Component.empty(),
				line("Bleed Blood Drops into a Bloodbath", NamedTextColor.DARK_RED),
				line("weapon to raise its Blood Level.", NamedTextColor.DARK_RED),
				line("Sneak and punch it with an empty hand", NamedTextColor.DARK_GRAY),
				line("to take it apart.", NamedTextColor.DARK_GRAY)));
			CustomModelDataComponent model = meta.getCustomModelDataComponent();
			model.setStrings(List.of(Weapons.MODEL_PREFIX + ID));
			meta.setCustomModelDataComponent(model);
			meta.setRarity(ItemRarity.EPIC);
			meta.setMaxStackSize(16);
			meta.getPersistentDataContainer().set(Keys.ANVIL_ITEM, PersistentDataType.BYTE, (byte) 1);
		});
		return anvil;
	}

	public static boolean isItem(ItemStack stack) {
		return stack != null && stack.getType() == Material.ANVIL && stack.getPersistentDataContainer().has(Keys.ANVIL_ITEM, PersistentDataType.BYTE);
	}

	private static Component line(String text, NamedTextColor color) {
		return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
	}

	public boolean isAnvil(Block block) {
		return block.getType() == Material.BARRIER && facing(block) >= 0;
	}

	/** The anvil's facing (0-3, in quarter turns), or -1 if the block isn't one. */
	private static int facing(Block block) {
		int[] stored = block.getChunk().getPersistentDataContainer().get(Keys.ANVILS, PersistentDataType.INTEGER_ARRAY);
		if (stored == null) {
			return -1;
		}
		int pos = pack(block);
		for (int value : stored) {
			if ((value & 0xFFFFF) == pos) {
				return value >>> 20 & 3;
			}
		}
		return -1;
	}

	private static int pack(Block block) {
		return (block.getX() & 15) | (block.getZ() & 15) << 4 | (block.getY() + 2048 & 0xFFF) << 8;
	}

	/** Makes {@code block} a Blood Anvil facing {@code quarterTurns}. */
	public void place(Block block, int quarterTurns) {
		block.setType(Material.BARRIER, false);
		Chunk chunk = block.getChunk();
		int[] stored = chunk.getPersistentDataContainer().get(Keys.ANVILS, PersistentDataType.INTEGER_ARRAY);
		List<Integer> list = new ArrayList<>();
		if (stored != null) {
			for (int value : stored) {
				if ((value & 0xFFFFF) != pack(block)) {
					list.add(value);
				}
			}
		}
		list.add(pack(block) | (quarterTurns & 3) << 20);
		chunk.getPersistentDataContainer().set(Keys.ANVILS, PersistentDataType.INTEGER_ARRAY, list.stream().mapToInt(Integer::intValue).toArray());
		spawnDisplay(block, quarterTurns & 3);
		Location center = block.getLocation().clone().add(0.5, 0.7, 0.5);
		BloodFx.play(center, "block.anvil.place", 0.8F, 0.6F);
		BloodFx.burst(center, BloodFx.BLOOD_FADE, 16, 0.4);
	}

	/** Takes an anvil away: its block, its display, its entry; anyone using it gets their items back. */
	public void remove(Block block) {
		Chunk chunk = block.getChunk();
		int[] stored = chunk.getPersistentDataContainer().get(Keys.ANVILS, PersistentDataType.INTEGER_ARRAY);
		if (stored != null) {
			int pos = pack(block);
			int[] kept = java.util.Arrays.stream(stored).filter(v -> (v & 0xFFFFF) != pos).toArray();
			if (kept.length == 0) {
				chunk.getPersistentDataContainer().remove(Keys.ANVILS);
			} else {
				chunk.getPersistentDataContainer().set(Keys.ANVILS, PersistentDataType.INTEGER_ARRAY, kept);
			}
		}
		ItemDisplay display = displays.remove(key(block));
		if (display != null) {
			display.remove();
		}
		for (AnvilMenu menu : new ArrayList<>(open.values())) {
			if (menu.anvil().getBlock().equals(block)) {
				Player player = Bukkit.getPlayer(menu.owner());
				if (player != null) {
					player.closeInventory();
				}
			}
		}
		if (block.getType() == Material.BARRIER) {
			block.setType(Material.AIR);
		}
	}

	private static long key(Block block) {
		return (block.getX() & 0x7FFFFFFL) | (block.getZ() & 0x7FFFFFFL) << 27 | ((block.getY() + 1024L) & 0x3FFL) << 54
			^ (long) block.getWorld().getUID().hashCode() << 20;
	}

	private void spawnDisplays(Chunk chunk) {
		int[] stored = chunk.getPersistentDataContainer().get(Keys.ANVILS, PersistentDataType.INTEGER_ARRAY);
		if (stored == null) {
			return;
		}
		for (int value : stored) {
			int x = (chunk.getX() << 4) + (value & 15);
			int z = (chunk.getZ() << 4) + (value >> 4 & 15);
			int y = (value >> 8 & 0xFFF) - 2048;
			Block block = chunk.getWorld().getBlockAt(x, y, z);
			if (block.getType() != Material.BARRIER) {
				continue; // gone some other way (a world edit): leave the entry for an admin to clear
			}
			ItemDisplay existing = displays.get(key(block));
			if (existing == null || !existing.isValid()) {
				spawnDisplay(block, value >>> 20 & 3);
			}
		}
	}

	private void spawnDisplay(Block block, int quarterTurns) {
		ItemStack look = new ItemStack(Material.ANVIL);
		look.editMeta(meta -> {
			CustomModelDataComponent model = meta.getCustomModelDataComponent();
			model.setStrings(List.of(Weapons.MODEL_PREFIX + ID));
			meta.setCustomModelDataComponent(model);
		});
		Location at = block.getLocation().clone().add(0.5, 0.5, 0.5);
		ItemDisplay display = block.getWorld().spawn(at, ItemDisplay.class, d -> {
			d.setPersistent(false);
			d.setItemStack(look);
			d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
			d.setBrightness(new Display.Brightness(9, 15));
			d.setViewRange(0.75F);
			d.setShadowRadius(0.6F);
			d.setShadowStrength(0.7F);
			float yaw = (float) (-quarterTurns * Math.PI / 2);
			d.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(yaw, 0, 1, 0), new Vector3f(1.0F, 1.0F, 1.0F), new AxisAngle4f()));
			d.getPersistentDataContainer().set(Keys.FIXTURE, PersistentDataType.STRING, ID);
		});
		displays.put(key(block), display);
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onChunkLoad(ChunkLoadEvent event) {
		spawnDisplays(event.getChunk());
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onPlace(BlockPlaceEvent event) {
		if (!isItem(event.getItemInHand())) {
			return;
		}
		// The item is an anvil so it places like one (and looks like one without the pack); the
		// block becomes a Blood Anvil the same tick, before an anvil could ever fall.
		int turns = Math.floorMod(Math.round(event.getPlayer().getLocation().getYaw() / 90.0F), 4);
		place(event.getBlockPlaced(), turns);
	}

	@EventHandler(priority = EventPriority.HIGH)
	public void onInteract(PlayerInteractEvent event) {
		Block block = event.getClickedBlock();
		if (block == null || block.getType() != Material.BARRIER || event.getHand() != EquipmentSlot.HAND || !isAnvil(block)) {
			return;
		}
		Player player = event.getPlayer();
		if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
			// A protection plugin's "no" comes first (cancelling ourselves would read as one).
			boolean denied = event.useInteractedBlock() == Event.Result.DENY;
			event.setCancelled(true);
			if (!denied) {
				openFor(player, block);
			}
		} else if (event.getAction() == Action.LEFT_CLICK_BLOCK && player.isSneaking() && player.getInventory().getItemInMainHand().isEmpty()) {
			event.setCancelled(true);
			dismantle(player, block);
		}
	}

	/** Sneak-punching an anvil: a real break attempt first, so claims and protection plugins get their say. */
	private void dismantle(Player player, Block block) {
		BlockBreakEvent check = new BlockBreakEvent(block, player);
		sweeping.add(player.getUniqueId());
		try {
			Bukkit.getPluginManager().callEvent(check);
		} finally {
			sweeping.remove(player.getUniqueId());
		}
		if (check.isCancelled()) {
			Hud.flash(player, Component.text("You can't take this anvil apart here", NamedTextColor.RED));
			return;
		}
		remove(block);
		if (player.getGameMode() != GameMode.CREATIVE) {
			block.getWorld().dropItemNaturally(block.getLocation().clone().add(0.5, 0.5, 0.5), item(1));
		}
		BloodFx.play(block.getLocation(), "block.anvil.break", 0.8F, 0.7F);
	}

	/** Creative players can break the barrier outright; the anvil goes with it. */
	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onBreak(BlockBreakEvent event) {
		if (sweeping.contains(event.getPlayer().getUniqueId())) {
			return;
		}
		Block block = event.getBlock();
		if (block.getType() == Material.BARRIER && isAnvil(block)) {
			remove(block);
		}
	}

	// ---- the screens ------------------------------------------------------------------------------

	public void openFor(Player player, Block anvil) {
		if (!Settings.get().blood.anvil().enabled()) {
			player.sendMessage(Settings.get().prefix.append(Component.text("The Blood Anvil is turned off on this server.", NamedTextColor.RED)));
			return;
		}
		if (!player.hasPermission("bloodbath.anvil")) {
			player.sendMessage(Settings.get().prefix.append(Component.text("You don't have permission to use the Blood Anvil.", NamedTextColor.RED)));
			return;
		}
		AnvilMenu previous = open.get(player.getUniqueId());
		if (previous != null) {
			finish(player, previous);
		}
		restore(player); // anything left over from a crash comes back first
		AnvilMenu menu = new AnvilMenu(this, player, anvil.getLocation());
		open.put(player.getUniqueId(), menu);
		player.openInventory(menu.getInventory());
		Location top = anvil.getLocation().clone().add(0.5, 1.0, 0.5);
		BloodFx.playTo(player, "block.anvil.place", 0.35F, 0.5F);
		BloodFx.playTo(player, BloodFx.HEARTBEAT, 0.6F, 0.8F);
		BloodFx.burst(top, BloodFx.BLOOD_FADE, 10, 0.3);
		BloodFx.burst(top, BloodFx.EMBER, 6, 0.3);
	}

	/** Saves what's in the anvil; when it came out of the player's inventory, the player too (see {@link Escrow}). */
	void persist(AnvilMenu menu, Player player, boolean tookFromPlayer) {
		escrow.save(menu.owner(), menu.contents());
		if (tookFromPlayer) {
			player.saveData();
		}
	}

	/** Ends a session: its items go back to the player (or at their feet if their inventory is full). */
	private void finish(Player player, AnvilMenu menu) {
		if (menu.closed()) {
			return;
		}
		menu.markClosed();
		open.remove(menu.owner());
		List<ItemStack> items = menu.contents();
		menu.clearContents();
		if (items.isEmpty()) {
			escrow.delete(menu.owner());
			return;
		}
		giveBack(player, items);
		player.saveData();
		escrow.delete(menu.owner());
	}

	private static void giveBack(Player player, List<ItemStack> items) {
		boolean spilled = false;
		for (ItemStack stack : items) {
			for (ItemStack left : player.getInventory().addItem(stack.clone()).values()) {
				player.getWorld().dropItem(player.getLocation(), left, drop -> {
					drop.setOwner(player.getUniqueId());
					drop.setPickupDelay(0);
				});
				spilled = true;
			}
		}
		if (spilled) {
			player.sendMessage(Settings.get().prefix.append(Component.text(
				"Your inventory was full: what didn't fit is at your feet (only you can pick it up).", NamedTextColor.GRAY)));
		}
	}

	/** Items from a Blood Anvil the server stopped (or crashed) with open, back to their owner. */
	public void restore(Player player) {
		if (!escrow.has(player.getUniqueId()) || open.containsKey(player.getUniqueId())) {
			return;
		}
		List<ItemStack> items = escrow.load(player.getUniqueId());
		if (items.isEmpty()) {
			return;
		}
		giveBack(player, items);
		player.saveData();
		escrow.delete(player.getUniqueId());
		player.sendMessage(Settings.get().prefix.append(Component.text(
			"You had items on a Blood Anvil when the server stopped. They're back in your inventory.", NamedTextColor.GRAY)));
	}

	@EventHandler(priority = EventPriority.LOWEST)
	public void onClick(InventoryClickEvent event) {
		Inventory top = event.getView().getTopInventory();
		if (!(top.getHolder(false) instanceof AnvilMenu menu)) {
			sweep(event);
			return;
		}
		if (!(event.getWhoClicked() instanceof Player player) || !player.getUniqueId().equals(menu.owner()) || menu.closed()) {
			event.setCancelled(true);
			return;
		}
		Inventory clicked = event.getClickedInventory();
		if (clicked == top) {
			event.setCancelled(true);
			menu.clickTop(player, event.getSlot(), event.getClick(), event.getHotbarButton());
			return;
		}
		if (clicked == null) {
			return; // clicking outside the window: dropping the cursor, as usual
		}
		InventoryAction action = event.getAction();
		if (action == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
			event.setCancelled(true);
			menu.shiftIn(player, event.getSlot());
		} else if (action == InventoryAction.COLLECT_TO_CURSOR) {
			// Double-click gathers matching items from the player's own inventory only.
			event.setCancelled(true);
			collect(player);
		}
		// Everything else in their own inventory works as it always does.
	}

	/** Double-click: fill the cursor from the player's inventory, never from the anvil. */
	private static void collect(Player player) {
		ItemStack cursor = player.getItemOnCursor();
		if (cursor.isEmpty()) {
			return;
		}
		PlayerInventory inv = player.getInventory();
		int max = cursor.getMaxStackSize();
		for (int slot = 0; slot < 36 && cursor.getAmount() < max; slot++) {
			ItemStack stack = inv.getItem(slot);
			if (stack == null || !stack.isSimilar(cursor)) {
				continue;
			}
			int moved = Math.min(max - cursor.getAmount(), stack.getAmount());
			cursor.setAmount(cursor.getAmount() + moved);
			stack.setAmount(stack.getAmount() - moved);
			inv.setItem(slot, stack.getAmount() > 0 ? stack : null);
		}
		player.setItemOnCursor(cursor);
	}

	@EventHandler(priority = EventPriority.LOWEST)
	public void onDrag(InventoryDragEvent event) {
		if (!(event.getView().getTopInventory().getHolder(false) instanceof AnvilMenu)) {
			return;
		}
		int topSize = event.getView().getTopInventory().getSize();
		for (int raw : event.getRawSlots()) {
			if (raw < topSize) {
				// Dragging across the anvil does nothing; dragging within your own inventory still works.
				event.setCancelled(true);
				return;
			}
		}
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onClose(InventoryCloseEvent event) {
		if (event.getInventory().getHolder(false) instanceof AnvilMenu menu && event.getPlayer() instanceof Player player) {
			finish(player, menu);
			BloodFx.playTo(player, "block.iron_trapdoor.close", 0.4F, 0.6F);
		}
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onQuit(PlayerQuitEvent event) {
		AnvilMenu menu = open.get(event.getPlayer().getUniqueId());
		if (menu != null) {
			finish(event.getPlayer(), menu);
		}
	}

	/** Dying with the anvil open: its items drop with everything else (or are kept, with keepInventory). */
	@EventHandler(priority = EventPriority.LOWEST)
	public void onDeath(PlayerDeathEvent event) {
		Player player = event.getEntity();
		AnvilMenu menu = open.get(player.getUniqueId());
		if (menu == null || menu.closed()) {
			return;
		}
		menu.markClosed();
		open.remove(player.getUniqueId());
		List<ItemStack> items = menu.contents();
		menu.clearContents();
		if (event.getKeepInventory()) {
			giveBack(player, items);
		} else {
			event.getDrops().addAll(items);
		}
		escrow.delete(player.getUniqueId());
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onJoin(PlayerJoinEvent event) {
		restore(event.getPlayer());
		purge(event.getPlayer().getInventory());
	}

	/** Called every tick: animations of open screens, and the odd ember off each anvil. */
	public void tick(long now) {
		for (AnvilMenu menu : new ArrayList<>(open.values())) {
			if (menu.animating()) {
				Player player = Bukkit.getPlayer(menu.owner());
				if (player != null) {
					menu.tick(player);
				}
			}
		}
		if (now % 20 == 0 && !displays.isEmpty()) {
			ThreadLocalRandom random = ThreadLocalRandom.current();
			for (ItemDisplay display : displays.values()) {
				if (!display.isValid() || random.nextInt(3) != 0) {
					continue;
				}
				Location at = display.getLocation().add(random.nextDouble(-0.35, 0.35), 0.5, random.nextDouble(-0.2, 0.2));
				if (Bukkit.getOnlinePlayers().stream().anyMatch(p -> p.getWorld() == at.getWorld() && p.getLocation().distanceSquared(at) < 400)) {
					BloodFx.burst(at, random.nextBoolean() ? BloodFx.EMBER : BloodFx.DRIP, 1, 0.05, 0.0);
				}
			}
		}
	}

	public int openCount() {
		return open.size();
	}

	/** The screen a player has open (tests and /bb status). */
	public AnvilMenu menuOf(Player player) {
		return open.get(player.getUniqueId());
	}

	// ---- menu pictures never leave a menu -------------------------------------------------------

	/** A click in any other screen: a menu picture found there is destroyed on the spot. */
	private static void sweep(InventoryClickEvent event) {
		if (isGui(event.getCurrentItem())) {
			event.setCancelled(true);
			event.setCurrentItem(null);
		}
		if (isGui(event.getCursor())) {
			event.setCancelled(true);
			event.getView().setCursor(null);
		}
	}

	private static void purge(PlayerInventory inventory) {
		ItemStack[] contents = inventory.getContents();
		for (int i = 0; i < contents.length; i++) {
			if (isGui(contents[i])) {
				inventory.setItem(i, null);
			}
		}
	}

	static boolean isGui(ItemStack stack) {
		return stack != null && !stack.isEmpty() && stack.getPersistentDataContainer().has(Keys.GUI, PersistentDataType.BYTE);
	}

	@EventHandler(priority = EventPriority.LOWEST)
	public void onItemSpawn(ItemSpawnEvent event) {
		if (isGui(event.getEntity().getItemStack())) {
			event.setCancelled(true);
		}
	}

	@EventHandler(priority = EventPriority.LOWEST)
	public void onPickup(EntityPickupItemEvent event) {
		if (isGui(event.getItem().getItemStack())) {
			event.setCancelled(true);
			event.getItem().remove();
		}
	}
}
