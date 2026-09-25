package net.unchartedsmp.bloodbath;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.unchartedsmp.bloodbath.anvil.AnvilMenu;
import net.unchartedsmp.bloodbath.anvil.BloodAnvils;
import net.unchartedsmp.bloodbath.blood.Bleeding;
import net.unchartedsmp.bloodbath.blood.BloodDrop;
import net.unchartedsmp.bloodbath.blood.BloodLevels;
import net.unchartedsmp.bloodbath.bloodlands.Bloodlands;
import net.unchartedsmp.bloodbath.bloodlands.BloodlandsGenerator;
import net.unchartedsmp.bloodbath.bloodlands.Terrain;
import net.unchartedsmp.bloodbath.portal.Frames;
import net.unchartedsmp.bloodbath.portal.Portal;
import net.unchartedsmp.bloodbath.portal.PortalScanner;
import net.unchartedsmp.bloodbath.support.TestPlayer;
import net.unchartedsmp.bloodbath.support.TestServer;
import net.unchartedsmp.bloodbath.support.TestWorld;
import net.unchartedsmp.bloodbath.support.TestZombie;
import net.unchartedsmp.bloodbath.util.Damage;
import net.unchartedsmp.bloodbath.weapon.WeaponType;
import net.unchartedsmp.bloodbath.weapon.Weapons;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.DragType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

/**
 * The Bloodlands, their portals, bleeding, Blood Drops and the Blood Anvil, driven the way players
 * drive them: clicks with real click types, events, commands and server ticks.
 */
@SuppressWarnings("removal") // MockBukkit's click simulation and a few event constructors are due for renames
class BloodlandsTest {
	private TestServer server;
	private TestWorld world;
	private TestWorld lands;
	private BloodbathPlugin plugin;
	private TestPlayer player;
	private final List<LogRecord> errors = new ArrayList<>();
	private final Handler errorCatcher = new Handler() {
		@Override
		public void publish(LogRecord record) {
			if (record.getLevel().intValue() >= Level.SEVERE.intValue()) {
				errors.add(record);
			}
		}

		@Override
		public void flush() {
		}

		@Override
		public void close() {
		}
	};

	@BeforeEach
	void setUp() throws Exception {
		Logger.getLogger("").addHandler(errorCatcher);
		server = MockBukkit.mock(new TestServer());
		world = server.addTestWorld("world");
		lands = server.addTestWorld("Bloodlands"); // stands in for the generated world (MockBukkit can't run a generator)
		YamlConfiguration config = new YamlConfiguration();
		try (InputStream in = getClass().getClassLoader().getResourceAsStream("config.yml")) {
			config.load(new InputStreamReader(in, StandardCharsets.UTF_8));
		}
		config.set("resource-pack.port", 0);
		config.set("resource-pack.mirrors", List.of());
		config.set("bloodlands.biome-datapack", false);
		plugin = MockBukkit.loadWithConfig(BloodbathPlugin.class, config);
		plugin.getLogger().addHandler(errorCatcher);
		player = server.addTestPlayer("Steve");
		player.setOp(true);
		player.teleport(new Location(world, 0.5, 5.0, 0.5));
	}

	@AfterEach
	void tearDown() {
		MockBukkit.unmock();
		Logger.getLogger("").removeHandler(errorCatcher);
		for (LogRecord record : errors) {
			System.err.println(record.getLevel() + " " + record.getMessage() + (record.getThrown() == null ? "" : " " + record.getThrown()));
		}
		assertTrue(errors.isEmpty(), "the plugin logged " + errors.size() + " error(s)");
	}

	// ---- helpers ------------------------------------------------------------------------------

	/** Changes config.yml and reloads, the way an admin would. */
	private void configure(String path, Object value) {
		plugin.getConfig().set(path, value);
		plugin.saveConfig();
		plugin.reload();
	}

	private void ticks(int count) {
		server.getScheduler().performTicks(count);
	}

	private static String plain(Component component) {
		return component == null ? "" : PlainTextComponentSerializer.plainText().serialize(component);
	}

	private static List<String> lore(ItemStack item) {
		List<String> out = new ArrayList<>();
		if (item.getItemMeta().lore() != null) {
			item.getItemMeta().lore().forEach(line -> out.add(plain(line)));
		}
		return out;
	}

	private static boolean any(List<String> lines, String fragment) {
		return lines.stream().anyMatch(line -> line.contains(fragment));
	}

	private static List<String> chat(TestPlayer who) {
		List<String> out = new ArrayList<>();
		for (Component message; (message = who.nextComponentMessage()) != null; ) {
			out.add(plain(message));
		}
		return out;
	}

	/** A Blood Anvil standing on the ground, and its screen open for the player. */
	private AnvilMenu openAnvil(TestPlayer who) {
		Block block = world.getBlockAt(2, 5, 0);
		if (!plugin.anvils().isAnvil(block)) {
			plugin.anvils().place(block, 0);
		}
		server.getPluginManager().callEvent(new PlayerInteractEvent(who, Action.RIGHT_CLICK_BLOCK, null, block, BlockFace.UP, EquipmentSlot.HAND));
		InventoryView view = who.getOpenInventory();
		assertInstanceOf(AnvilMenu.class, view.getTopInventory().getHolder(false));
		return (AnvilMenu) view.getTopInventory().getHolder(false);
	}

	/** A click with a given type on a raw slot of the open view, through the event pipeline. */
	private InventoryClickEvent click(TestPlayer who, int rawSlot, ClickType type, InventoryAction action, int hotbar) {
		InventoryView view = who.getOpenInventory();
		InventoryType.SlotType slotType = rawSlot < view.getTopInventory().getSize() ? InventoryType.SlotType.CONTAINER
			: InventoryType.SlotType.CONTAINER;
		InventoryClickEvent event = hotbar >= 0
			? new InventoryClickEvent(view, slotType, rawSlot, type, action, hotbar)
			: new InventoryClickEvent(view, slotType, rawSlot, type, action);
		server.getPluginManager().callEvent(event);
		return event;
	}

	private InventoryClickEvent click(TestPlayer who, int rawSlot, ClickType type) {
		InventoryAction action = switch (type) {
			case SHIFT_LEFT, SHIFT_RIGHT -> InventoryAction.MOVE_TO_OTHER_INVENTORY;
			case DOUBLE_CLICK -> InventoryAction.COLLECT_TO_CURSOR;
			case NUMBER_KEY -> InventoryAction.HOTBAR_SWAP;
			default -> InventoryAction.PICKUP_ALL;
		};
		return click(who, rawSlot, type, action, -1);
	}

	/** The raw slot of a player-inventory slot (0-35) in a 45-slot menu view. */
	private static int raw(int inventorySlot) {
		return inventorySlot < 9 ? 45 + 27 + inventorySlot : 45 + inventorySlot - 9;
	}

	/** Every item the player has anywhere, counted by kind. */
	private static int count(Player who, java.util.function.Predicate<ItemStack> what) {
		int n = 0;
		for (ItemStack stack : who.getInventory().getContents()) {
			if (stack != null && what.test(stack)) {
				n += stack.getAmount();
			}
		}
		ItemStack cursor = who.getItemOnCursor();
		if (!cursor.isEmpty() && what.test(cursor)) {
			n += cursor.getAmount();
		}
		return n;
	}

	private static boolean gui(ItemStack stack) {
		return stack != null && stack.getPersistentDataContainer().has(Keys.GUI, PersistentDataType.BYTE);
	}

	private static double attackDamage(ItemStack weapon) {
		double total = 1.0;
		for (AttributeModifier modifier : weapon.getItemMeta().getAttributeModifiers(Attribute.ATTACK_DAMAGE)) {
			if (modifier.getSlotGroup() == EquipmentSlotGroup.MAINHAND) {
				total += modifier.getAmount();
			}
		}
		return total;
	}

	/** A weapon with history: kills, an enchantment, a name given at an anvil. */
	private static ItemStack veteran(WeaponType type) {
		ItemStack weapon = Weapons.create(type);
		weapon.editMeta(meta -> {
			meta.getPersistentDataContainer().set(Keys.KILLS, PersistentDataType.INTEGER, 7);
			meta.addEnchant(Enchantment.SHARPNESS, 3, true);
			meta.displayName(Component.text("Old Faithful"));
		});
		Weapons.rebuild(weapon);
		return weapon;
	}

	// ---- the Blood Anvil: a whole upgrade ----------------------------------------------------

	@Test
	void theAnvilBleedsAnExistingWeaponAndKeepsEverythingElse() {
		ItemStack weapon = veteran(WeaponType.RIFTBLADE);
		double before = attackDamage(weapon);
		assertEquals(9.0, before, 1e-6);
		AnvilMenu menu = openAnvil(player);
		Inventory top = player.getOpenInventory().getTopInventory();
		assertEquals(AnvilMenu.class, top.getHolder(false).getClass());

		// Empty anvil: placeholders and the idle button, all pictures.
		assertTrue(gui(top.getItem(11)) && gui(top.getItem(22)) && gui(top.getItem(31)));
		assertEquals("BLEED WEAPON", plain(top.getItem(31).getItemMeta().itemName()));
		assertTrue(any(lore(top.getItem(11)), "Place an existing Bloodbath"));

		// Weapon in (from the cursor).
		player.setItemOnCursor(weapon);
		assertTrue(click(player, 11, ClickType.LEFT).isCancelled());
		assertTrue(player.getItemOnCursor().isEmpty());
		ItemStack shown = top.getItem(11);
		assertTrue(gui(shown), "the slot shows a picture of the weapon");
		assertNull(Weapons.typeOf(shown), "a picture is never a weapon");
		List<String> shownLore = lore(shown);
		assertTrue(any(shownLore, "Blood Infusion"), shownLore.toString());
		assertTrue(any(shownLore, "Next Level: I"));
		assertTrue(any(shownLore, "Upgrade Cost: 1 Blood Drop"));
		assertTrue(any(shownLore, "cooldown"), "its own tooltip is still there");
		assertEquals("NEED MORE BLOOD", plain(top.getItem(31).getItemMeta().itemName()));
		assertTrue(any(lore(top.getItem(31)), "0 / 1 Blood Drops"));

		// The preview: built from the real weapon, one level up, never the real thing.
		ItemStack preview = top.getItem(15);
		assertTrue(gui(preview));
		assertTrue(any(lore(preview), "Melee damage: 9  →  10"), lore(preview).toString());
		assertTrue(any(lore(preview), "Bleeding chance: 0%  →  4%"));

		// A Blood Drop in: ready.
		player.setItemOnCursor(BloodDrop.create(3));
		click(player, 22, ClickType.LEFT);
		assertEquals("BLEED WEAPON · READY", plain(top.getItem(31).getItemMeta().itemName()));
		assertTrue(any(lore(top.getItem(31)), "Click to bleed weapon."));

		// Bleed it.
		click(player, 30, ClickType.LEFT); // any part of the button
		assertEquals("BLEEDING...", plain(top.getItem(31).getItemMeta().itemName()));
		ticks(40);
		assertEquals("BLEED WEAPON · READY", plain(top.getItem(31).getItemMeta().itemName()), "level II costs 2; 2 are left");
		assertEquals(2, top.getItem(22).getAmount(), "exactly one drop was used");

		// Take it back and look: the same weapon, one Blood Level up.
		click(player, 11, ClickType.LEFT);
		ItemStack after = player.getItemOnCursor();
		assertEquals(WeaponType.RIFTBLADE, Weapons.typeOf(after));
		assertFalse(gui(after));
		assertEquals(1, BloodLevels.level(after));
		assertEquals(7, Weapons.kills(after), "kills kept");
		assertEquals(3, after.getEnchantmentLevel(Enchantment.SHARPNESS), "enchantments kept");
		assertEquals("Old Faithful", plain(after.getItemMeta().displayName()), "its name kept");
		assertEquals(before + 1.0, attackDamage(after), 1e-6, "+1 melee damage, in the weapon's own attack damage");
		assertTrue(any(lore(after), "Blood Level I"), lore(after).toString());
		assertEquals(Material.NETHERITE_SWORD, after.getType(), "still the same item underneath");
		assertEquals(Weapons.create(WeaponType.RIFTBLADE).getItemMeta().getCustomModelDataComponent().getStrings(),
			after.getItemMeta().getCustomModelDataComponent().getStrings(), "its model kept");

		// Closing gives the rest back.
		player.getInventory().addItem(after);
		player.setItemOnCursor(null);
		player.closeInventory();
		assertEquals(2, count(player, BloodDrop::isDrop));
		assertEquals(0, count(player, BloodlandsTest::gui));
		assertEquals(0, plugin.anvils().openCount());
	}

	@Test
	void theLevelSurvivesEveryRebuildAndAReload() {
		ItemStack weapon = veteran(WeaponType.VOID_SCYTHE);
		BloodLevels.set(weapon, 3);
		assertEquals(3, BloodLevels.level(weapon));
		// A rank-up rebuilds the item: the level stays.
		net.unchartedsmp.bloodbath.weapon.KillTracker.record(player, weapon, WeaponType.VOID_SCYTHE);
		assertEquals(3, BloodLevels.level(weapon));
		assertEquals(8, Weapons.kills(weapon));
		// A config reload (new revision) rebuilds it too.
		configure("weapons.void_scythe.cooldown", 4);
		assertTrue(Weapons.refresh(weapon), "a new revision rebuilds the weapon");
		assertEquals(3, BloodLevels.level(weapon));
		assertEquals(12 + 3.0, attackDamage(weapon), 1e-6);
		// A copy (an item moved between inventories, saved and loaded) keeps it.
		ItemStack copy = weapon.clone();
		assertEquals(3, BloodLevels.level(copy));
		assertTrue(any(lore(copy), "Blood Level III"));
	}

	@Test
	void maxLevelAndMissingBloodAreEnforced() {
		ItemStack weapon = Weapons.create(WeaponType.VAMPIRE_FANG);
		BloodLevels.set(weapon, Settings().maxLevel());
		openAnvil(player);
		Inventory top = player.getOpenInventory().getTopInventory();
		player.setItemOnCursor(weapon);
		click(player, 11, ClickType.LEFT);
		assertEquals("MAXIMUM LEVEL", plain(top.getItem(31).getItemMeta().itemName()));
		player.setItemOnCursor(BloodDrop.create(64));
		click(player, 22, ClickType.LEFT);
		click(player, 31, ClickType.LEFT);
		assertEquals(Settings().maxLevel(), BloodLevels.level(weaponIn(top)));
		assertEquals(64, top.getItem(22).getAmount(), "no drops taken at the maximum");
	}

	private static net.unchartedsmp.bloodbath.config.BloodConfig Settings() {
		return net.unchartedsmp.bloodbath.config.Settings.get().blood;
	}

	/** Takes the weapon's level from its picture's lore, without taking it out. */
	private static ItemStack weaponIn(Inventory top) {
		ItemStack picture = top.getItem(11).clone();
		picture.editMeta(meta -> meta.getPersistentDataContainer().remove(Keys.GUI));
		return picture;
	}

	// ---- the Blood Anvil: nothing can be stolen, duplicated or lost -------------------------------

	@Test
	void everyPictureStaysInTheMenu() {
		openAnvil(player);
		Inventory top = player.getOpenInventory().getTopInventory();
		player.setItemOnCursor(Weapons.create(WeaponType.CHRONOS));
		click(player, 11, ClickType.LEFT);
		player.setItemOnCursor(BloodDrop.create(1));
		click(player, 22, ClickType.LEFT);
		// Every slot of the menu, every way of clicking it, with an empty cursor.
		ClickType[] types = {ClickType.LEFT, ClickType.RIGHT, ClickType.SHIFT_LEFT, ClickType.MIDDLE, ClickType.DOUBLE_CLICK,
			ClickType.DROP, ClickType.CONTROL_DROP, ClickType.CREATIVE};
		for (int slot = 0; slot < 45; slot++) {
			if (slot == 11 || slot == 22 || slot >= 29 && slot <= 33) {
				continue; // the real slots and the button are tested on their own
			}
			for (ClickType type : types) {
				InventoryClickEvent event = click(player, slot, type);
				assertTrue(event.isCancelled(), "slot " + slot + " " + type);
				assertTrue(player.getItemOnCursor().isEmpty(), "slot " + slot + " " + type + " put something on the cursor");
			}
			// The number keys too.
			InventoryClickEvent swap = click(player, slot, ClickType.NUMBER_KEY, InventoryAction.HOTBAR_SWAP, 4);
			assertTrue(swap.isCancelled());
		}
		assertEquals(0, count(player, BloodlandsTest::gui), "no picture ever reached the player");
		assertEquals(0, count(player, stack -> true), "and nothing real either");
		// A drag across the menu does nothing.
		player.setItemOnCursor(BloodDrop.create(4));
		InventoryDragEvent drag = new InventoryDragEvent(player.getOpenInventory(), null, player.getItemOnCursor(), false,
			java.util.Map.of(21, BloodDrop.create(2), 23, BloodDrop.create(2)));
		server.getPluginManager().callEvent(drag);
		assertTrue(drag.isCancelled());
		assertEquals(DragType.EVEN, drag.getType());
	}

	@Test
	void shiftClicksNumberKeysAndDoubleClicksOnlyMoveRealItems() {
		openAnvil(player);
		Inventory top = player.getOpenInventory().getTopInventory();
		player.getInventory().setItem(0, new ItemStack(Material.NETHERITE_SWORD)); // a vanilla sword
		player.getInventory().setItem(1, Weapons.create(WeaponType.BLOODHOOK));
		player.getInventory().setItem(2, BloodDrop.create(5));
		player.getInventory().setItem(3, BloodDrop.create(3));
		player.getInventory().setItem(4, new ItemStack(Material.FIREWORK_STAR)); // a plain star
		ItemStack renamed = new ItemStack(Material.FIREWORK_STAR);
		renamed.editMeta(meta -> meta.itemName(Component.text("Blood Drop")));
		player.getInventory().setItem(5, renamed); // a fake

		// Shift-click: the vanilla sword and the fakes are refused.
		click(player, raw(0), ClickType.SHIFT_LEFT);
		click(player, raw(4), ClickType.SHIFT_LEFT);
		click(player, raw(5), ClickType.SHIFT_LEFT);
		assertEquals(Material.NETHERITE_SWORD, player.getInventory().getItem(0).getType());
		assertNotNull(player.getInventory().getItem(4));
		assertNotNull(player.getInventory().getItem(5));
		assertFalse(BloodDrop.isDrop(renamed), "a renamed firework star is not a Blood Drop");
		// The real ones go in.
		click(player, raw(1), ClickType.SHIFT_LEFT);
		click(player, raw(2), ClickType.SHIFT_LEFT);
		click(player, raw(3), ClickType.SHIFT_LEFT);
		assertNull(player.getInventory().getItem(1));
		assertEquals(8, top.getItem(22).getAmount());
		assertEquals(WeaponType.BLOODHOOK, Weapons.typeOf(weaponIn(top)));

		// Number key on the drop slot: swaps with that hotbar slot (empty hotbar slot 6: they come out).
		click(player, 22, ClickType.NUMBER_KEY, InventoryAction.HOTBAR_SWAP, 6);
		assertEquals(8, player.getInventory().getItem(6).getAmount());
		assertTrue(BloodDrop.isDrop(player.getInventory().getItem(6)));
		assertTrue(gui(top.getItem(22)), "the slot shows its placeholder again");
		// ...and back in with the same key.
		click(player, 22, ClickType.NUMBER_KEY, InventoryAction.HOTBAR_SWAP, 6);
		assertNull(player.getInventory().getItem(6));
		assertEquals(8, top.getItem(22).getAmount());
		// A number key can't put a vanilla sword on the anvil.
		click(player, 11, ClickType.NUMBER_KEY, InventoryAction.HOTBAR_SWAP, 0);
		assertEquals(Material.NETHERITE_SWORD, player.getInventory().getItem(0).getType());
		assertEquals(WeaponType.BLOODHOOK, Weapons.typeOf(weaponIn(top)));

		// Double-click gathers from the player's inventory only, never from the anvil.
		player.getInventory().setItem(7, BloodDrop.create(2));
		player.setItemOnCursor(BloodDrop.create(1));
		InventoryClickEvent gather = click(player, raw(8), ClickType.DOUBLE_CLICK);
		assertTrue(gather.isCancelled());
		assertEquals(3, player.getItemOnCursor().getAmount());
		assertEquals(8, top.getItem(22).getAmount(), "the anvil's drops weren't touched");

		// Everything adds up: 8 in the anvil, 3 on the cursor.
		player.getInventory().addItem(player.getItemOnCursor());
		player.setItemOnCursor(null);
		player.closeInventory();
		assertEquals(11, count(player, BloodDrop::isDrop));
		assertEquals(1, count(player, stack -> Weapons.typeOf(stack) == WeaponType.BLOODHOOK));
		assertEquals(0, count(player, BloodlandsTest::gui));
	}

	@Test
	void rapidClicksBleedOnce() {
		openAnvil(player);
		Inventory top = player.getOpenInventory().getTopInventory();
		player.setItemOnCursor(Weapons.create(WeaponType.RIFTBLADE));
		click(player, 11, ClickType.LEFT);
		player.setItemOnCursor(BloodDrop.create(10));
		click(player, 22, ClickType.LEFT);
		for (int i = 0; i < 6; i++) {
			click(player, 31, ClickType.LEFT);
			click(player, 31, ClickType.DOUBLE_CLICK);
		}
		assertEquals(1, BloodLevels.level(weaponIn(top)), "one bleed, however fast the clicks");
		assertEquals(9, top.getItem(22).getAmount());
		// While it bleeds, the weapon can't be pulled out either.
		click(player, 11, ClickType.LEFT);
		assertTrue(player.getItemOnCursor().isEmpty());
		ticks(40);
		click(player, 31, ClickType.LEFT);
		ticks(40);
		assertEquals(2, BloodLevels.level(weaponIn(top)));
		assertEquals(7, top.getItem(22).getAmount(), "level II cost 2");
	}

	@Test
	void closingQuittingAndDyingNeverLoseItems() {
		// A full inventory: what doesn't fit lands at their feet, theirs alone.
		openAnvil(player);
		player.setItemOnCursor(Weapons.create(WeaponType.CHRONOS));
		click(player, 11, ClickType.LEFT);
		player.setItemOnCursor(BloodDrop.create(6));
		click(player, 22, ClickType.LEFT);
		File escrow = new File(plugin.getDataFolder(), "anvil-escrow/" + player.getUniqueId() + ".yml");
		assertTrue(escrow.isFile(), "what's on the anvil is on disk the moment it's there");
		for (int i = 0; i < player.getInventory().getSize(); i++) {
			player.getInventory().setItem(i, new ItemStack(Material.DIRT, 64));
		}
		player.closeInventory();
		assertFalse(escrow.isFile());
		List<org.bukkit.entity.Item> dropped = world.getEntitiesByClass(org.bukkit.entity.Item.class).stream().toList();
		assertEquals(2, dropped.size(), "the weapon and the drops, on the ground");
		for (org.bukkit.entity.Item item : dropped) {
			assertEquals(player.getUniqueId(), item.getOwner());
		}

		// Logging out with it open.
		player.getInventory().clear();
		openAnvil(player);
		player.setItemOnCursor(BloodDrop.create(2));
		click(player, 22, ClickType.LEFT);
		player.disconnect();
		assertEquals(0, plugin.anvils().openCount());
		assertEquals(2, count(player, BloodDrop::isDrop));
		player.reconnect();

		// Dying with it open: the items drop with everything else.
		player.getInventory().clear();
		openAnvil(player);
		player.setItemOnCursor(Weapons.create(WeaponType.GRAVESTONE));
		click(player, 11, ClickType.LEFT);
		List<ItemStack> drops = new ArrayList<>();
		PlayerDeathEvent death = new PlayerDeathEvent(player, DamageSource.builder(DamageType.GENERIC).build(), drops, 0, Component.empty(), false);
		server.getPluginManager().callEvent(death);
		assertEquals(1, drops.stream().filter(stack -> Weapons.typeOf(stack) == WeaponType.GRAVESTONE).count());
		assertEquals(0, plugin.anvils().openCount());
	}

	@Test
	void thePluginStoppingGivesEverythingBack() {
		openAnvil(player);
		player.setItemOnCursor(Weapons.create(WeaponType.MIRRORFANG));
		click(player, 11, ClickType.LEFT);
		plugin.anvils().disable();
		assertEquals(1, count(player, stack -> Weapons.typeOf(stack) == WeaponType.MIRRORFANG));
	}

	@Test
	void twoPlayersNeverShareAnAnvil() {
		TestPlayer alex = server.addTestPlayer("Alex");
		alex.teleport(new Location(world, 1.5, 5.0, 1.5));
		AnvilMenu mine = openAnvil(player);
		AnvilMenu theirs = openAnvil(alex);
		assertFalse(mine == theirs);
		player.setItemOnCursor(Weapons.create(WeaponType.THUNDER_PIKE));
		click(player, 11, ClickType.LEFT);
		alex.setItemOnCursor(BloodDrop.create(2));
		click(alex, 22, ClickType.LEFT);
		assertTrue(gui(alex.getOpenInventory().getTopInventory().getItem(11)));
		assertTrue(any(lore(alex.getOpenInventory().getTopInventory().getItem(11)), "Place an existing"), "Alex doesn't see Steve's weapon");
		assertTrue(any(lore(player.getOpenInventory().getTopInventory().getItem(22)), "Inserted: 0"), "Steve doesn't see Alex's drops");
		player.closeInventory();
		alex.closeInventory();
		assertEquals(1, count(player, stack -> Weapons.typeOf(stack) == WeaponType.THUNDER_PIKE));
		assertEquals(2, count(alex, BloodDrop::isDrop));
	}

	@Test
	void aPictureThatEscapesIsDestroyed() {
		ItemStack picture = new ItemStack(Material.PAPER);
		picture.editMeta(meta -> meta.getPersistentDataContainer().set(Keys.GUI, PersistentDataType.BYTE, (byte) 1));
		player.getInventory().setItem(3, picture);
		player.disconnect();
		player.reconnect();
		assertNull(player.getInventory().getItem(3), "gone the next time they join");
	}

	@Test
	void theAnvilIsPlacedAndTakenApart() {
		Block block = world.getBlockAt(4, 5, 4);
		ItemStack item = BloodAnvils.item(1);
		assertTrue(BloodAnvils.isItem(item));
		block.setType(Material.ANVIL);
		player.getInventory().setItemInMainHand(item);
		server.getPluginManager().callEvent(new org.bukkit.event.block.BlockPlaceEvent(block, block.getState(), block.getRelative(BlockFace.DOWN),
			item, player, true, EquipmentSlot.HAND));
		assertEquals(Material.BARRIER, block.getType());
		assertTrue(plugin.anvils().isAnvil(block));
		assertFalse(plugin.anvils().isAnvil(world.getBlockAt(5, 5, 4)), "a plain barrier is not an anvil");
		// Sneak-punch with an empty hand: taken apart, the item drops.
		player.getInventory().setItemInMainHand(null);
		player.setSneaking(true);
		player.setGameMode(GameMode.SURVIVAL);
		server.getPluginManager().callEvent(new PlayerInteractEvent(player, Action.LEFT_CLICK_BLOCK, null, block, BlockFace.UP, EquipmentSlot.HAND));
		assertEquals(Material.AIR, block.getType());
		assertFalse(plugin.anvils().isAnvil(block));
		assertTrue(world.getEntitiesByClass(org.bukkit.entity.Item.class).stream().anyMatch(i -> BloodAnvils.isItem(i.getItemStack())));
	}

	// ---- Blood Levels in combat ------------------------------------------------------------------

	@Test
	void bledWeaponsHitHarderAndOpenWounds() {
		configure("blood-levels.1.bleeding-chance", 1.0);
		TestZombie target = new TestZombie(server, new Location(world, 2.5, 5.0, 0.5));
		ItemStack weapon = Weapons.create(WeaponType.RIFTBLADE);
		BloodLevels.set(weapon, 1);
		player.getInventory().setItemInMainHand(weapon);
		EntityDamageByEntityEvent hit = new EntityDamageByEntityEvent(player, target, EntityDamageEvent.DamageCause.ENTITY_ATTACK,
			DamageSource.builder(DamageType.PLAYER_ATTACK).withCausingEntity(player).withDirectEntity(player).build(), 11.0);
		server.getPluginManager().callEvent(hit);
		assertEquals(1, Bleeding.stacks(target), "a 100% bleed chance always opens a wound");
		double health = target.getHealth();
		ticks(40);
		assertTrue(target.getHealth() < health, "and the wound bleeds");

		// Abilities: +5% at level I.
		TestZombie a = new TestZombie(server, new Location(world, 4.5, 5.0, 0.5));
		TestZombie b = new TestZombie(server, new Location(world, 6.5, 5.0, 0.5));
		player.getInventory().setItemInMainHand(Weapons.create(WeaponType.RIFTBLADE));
		double aBefore = a.getHealth();
		Damage.deal(a, 10.0, player, WeaponType.RIFTBLADE);
		double plainHit = aBefore - a.getHealth();
		player.getInventory().setItemInMainHand(weapon);
		double bBefore = b.getHealth();
		Damage.deal(b, 10.0, player, WeaponType.RIFTBLADE);
		double bledHit = bBefore - b.getHealth();
		assertEquals(plainHit * 1.05, bledHit, 0.05, "the bled weapon's ability hit harder");
	}

	@Test
	void bleedingIsOneTickerAndStacksAsConfigured() {
		TestZombie target = new TestZombie(server, new Location(world, 2.5, 5.0, 0.5));
		Bleeding.apply(target, 2, null, null);
		Bleeding.apply(target, 2, null, null);
		assertEquals(4, Bleeding.stacks(target));
		for (int i = 0; i < 10; i++) {
			Bleeding.apply(target, 1, null, null);
		}
		assertEquals(Settings().bleed().maxStacks(), Bleeding.stacks(target), "capped at max-stacks");
		configure("bleeding.stacking", "refresh");
		TestZombie other = new TestZombie(server, new Location(world, 4.5, 5.0, 0.5));
		Bleeding.apply(other, 2, null, null);
		Bleeding.apply(other, 1, null, null);
		assertEquals(2, Bleeding.stacks(other), "refresh keeps the stronger bleed");
		double health = other.getHealth();
		ticks(Settings().bleed().intervalTicks() + 1);
		assertEquals(health - Settings().bleed().damagePerStack() * 2, other.getHealth(), 1e-6);
		ticks(Settings().bleed().durationTicks() + 5);
		assertEquals(0, Bleeding.stacks(other), "it fades");
	}

	// ---- blood water ---------------------------------------------------------------------------

	@Test
	void bloodWaterHurtsBleedsAndStopsWhenYouLeave() {
		assertTrue(plugin.bloodlands().isOpen(), plugin.bloodlands().status());
		lands.getBlockAt(0, 5, 0).setType(Material.WATER);
		lands.getBlockAt(0, 6, 0).setType(Material.WATER);
		player.teleport(new Location(lands, 0.5, 5.0, 0.5));
		player.setGameMode(GameMode.SURVIVAL);
		double health = player.getHealth();
		ticks(21);
		assertTrue(player.getHealth() < health, "blood water hurts");
		assertTrue(Bleeding.stacks(player) > 0, "and makes you bleed");
		assertFalse(player.isDead(), "but never all at once");
		player.teleport(new Location(lands, 3.5, 5.0, 0.5));
		ticks(21);
		assertEquals(0, Bleeding.stacks(player), "out of the water, the bleeding stops");
		// Water anywhere else is just water.
		world.getBlockAt(0, 5, 0).setType(Material.WATER);
		player.teleport(new Location(world, 0.5, 5.0, 0.5));
		health = player.getHealth();
		ticks(41);
		assertEquals(health, player.getHealth(), 1e-6);
	}

	// ---- Blood Drops ---------------------------------------------------------------------------

	@Test
	void bloodDropsAreRealAndRare() {
		ItemStack drop = BloodDrop.create(3);
		assertTrue(BloodDrop.isDrop(drop));
		assertTrue(BloodDrop.isDrop(drop.clone()));
		assertEquals("Blood Drop", plain(drop.getItemMeta().itemName()));
		ItemStack fake = new ItemStack(Material.FIREWORK_STAR);
		fake.editMeta(meta -> meta.displayName(Component.text("Blood Drop")));
		assertFalse(BloodDrop.isDrop(fake));
		// From the commands.
		player.performCommand("blood give Steve 5");
		assertEquals(5, count(player, BloodDrop::isDrop));
		player.performCommand("bb give Steve drop 2");
		assertEquals(7, count(player, BloodDrop::isDrop));
		// From the Bloodbound (elite chance forced).
		plugin.getConfig().set("blood-drop.elite-drop-chance", 1.0);
		configure("blood-drop.mob-drop-chance", 0.0);
		TestZombie elite = new TestZombie(server, new Location(lands, 2.5, 5.0, 0.5));
		Bloodlands.makeBloodbound(elite);
		assertTrue(Bloodlands.isBloodbound(elite));
		TestZombie plainZombie = new TestZombie(server, new Location(lands, 4.5, 5.0, 0.5));
		assertEquals(1, deathDrops(elite).stream().filter(BloodDrop::isDrop).count());
		assertEquals(0, deathDrops(plainZombie).stream().filter(BloodDrop::isDrop).count());
		// And never outside the Bloodlands.
		TestZombie home = new TestZombie(server, new Location(world, 4.5, 5.0, 0.5));
		Bloodlands.makeBloodbound(home);
		assertEquals(0, deathDrops(home).stream().filter(BloodDrop::isDrop).count());
	}

	private List<ItemStack> deathDrops(TestZombie mob) {
		mob.setKiller(player);
		List<ItemStack> drops = new ArrayList<>();
		EntityDeathEvent death = new EntityDeathEvent(mob, DamageSource.builder(DamageType.PLAYER_ATTACK).withCausingEntity(player).build(), drops);
		server.getPluginManager().callEvent(death);
		return drops;
	}

	@Test
	void pickingUpADropSaysHowManyYouHold() {
		player.getInventory().addItem(BloodDrop.create(2));
		while (player.nextActionBar() != null) {
			// drain
		}
		net.unchartedsmp.bloodbath.support.TestItem item = new net.unchartedsmp.bloodbath.support.TestItem(server, BloodDrop.create(1));
		server.getPluginManager().callEvent(new org.bukkit.event.entity.EntityPickupItemEvent(player, item, 0));
		Component bar = player.nextActionBar();
		assertTrue(bar != null && PlainTextComponentSerializer.plainText().serialize(bar).contains("3 held"));
		// Turned off: nothing.
		configure("blood-drop.pickup-effects", false);
		server.getPluginManager().callEvent(new org.bukkit.event.entity.EntityPickupItemEvent(player, item, 0));
		assertEquals(null, player.nextActionBar());
	}

	@Test
	void dropsStayOutOfVanillaCrafting() {
		// A firework star with the drop's tag is still kept out of firework recipes by the crafting guard.
		ItemStack drop = BloodDrop.create(1);
		assertEquals(Material.FIREWORK_STAR, drop.getType());
		assertTrue(drop.getPersistentDataContainer().has(Keys.BLOOD_DROP, PersistentDataType.BYTE));
	}

	// ---- portals -------------------------------------------------------------------------------------

	/** A ring of frame blocks (corners included) around a w×h inside, facing north/south, bottom-left inside at (x, y, z). */
	private void frame(World in, int x, int y, int z, int w, int h) {
		for (int i = -1; i <= w; i++) {
			for (int j = -1; j <= h; j++) {
				Block block = in.getBlockAt(x + i, y + j, z);
				if (i >= 0 && i < w && j >= 0 && j < h) {
					block.setType(Material.AIR);
				} else {
					block.setType(Frames.BLOCK);
					Frames.add(block);
				}
			}
		}
	}

	@Test
	void portalsOfEverySizeLightAndBadFramesSayWhy() {
		int[][] sizes = {{2, 3}, {3, 5}, {5, 7}, {7, 10}, {10, 10}, {15, 20}};
		int x = 20;
		for (int[] size : sizes) {
			frame(world, x, 5, 40, size[0], size[1]);
			PortalScanner.Result result = plugin.portals().ignite(world.getBlockAt(x - 1, 6, 40), player);
			assertTrue(result.valid(), size[0] + "x" + size[1] + ": " + result.problem());
			Portal portal = result.portal();
			assertEquals(size[0], portal.width());
			assertEquals(size[1], portal.height());
			assertEquals(Material.LIGHT, world.getBlockAt(x, 5, 40).getType(), "the inside glows");
			assertEquals(portal, plugin.portals().at(world.getBlockAt(x + size[0] - 1, 5 + size[1] - 1, 40)));
			x += size[0] + 4;
		}
		// Too small, too big, a gap, the wrong block, not a rectangle, an open ring.
		frame(world, 200, 5, 60, 1, 3);
		assertTrue(plugin.portals().ignite(world.getBlockAt(199, 6, 60), player).problem().contains("smallest"));
		frame(world, 200, 5, 70, 21, 4);
		assertTrue(plugin.portals().ignite(world.getBlockAt(199, 6, 70), player).problem().contains("largest"));
		frame(world, 200, 5, 80, 3, 4);
		Block gap = world.getBlockAt(201, 9, 80);
		Frames.remove(gap);
		gap.setType(Material.STONE);
		PortalScanner.Result stone = plugin.portals().ignite(world.getBlockAt(199, 6, 80), player);
		assertFalse(stone.valid());
		assertTrue(stone.problem().contains("gap"), stone.problem());
		assertEquals(gap, stone.where(), "it points at the block");
		gap.setType(Frames.BLOCK); // reinforced deepslate, but not a registered frame
		assertTrue(plugin.portals().ignite(world.getBlockAt(199, 6, 80), player).problem().contains("not a Bloodstone Frame"));
		frame(world, 200, 5, 90, 3, 4);
		Block notch = world.getBlockAt(200, 5, 90);
		notch.setType(Frames.BLOCK);
		Frames.add(notch); // a frame block inside the frame: the inside isn't a rectangle
		assertTrue(plugin.portals().ignite(world.getBlockAt(199, 6, 90), player).problem().contains("rectangle"));
		assertEquals(sizes.length, plugin.portals().all().stream().filter(p -> p.world().equals(world.getUID())).count());
		// Sideways portals (running along Z) light too.
		for (int j = -1; j <= 4; j++) {
			for (int i = -1; i <= 3; i++) {
				Block block = world.getBlockAt(300, 5 + j, 10 + i);
				if (i >= 0 && i < 3 && j >= 0 && j < 4) {
					block.setType(Material.AIR);
				} else {
					block.setType(Frames.BLOCK);
					Frames.add(block);
				}
			}
		}
		PortalScanner.Result side = plugin.portals().ignite(world.getBlockAt(300, 6, 9), player);
		assertTrue(side.valid(), side.problem());
		assertEquals(Portal.Axis.Z, side.portal().axis());
	}

	@Test
	void flintAndSteelLightsAFrameAndNothingBreaksIt() {
		frame(world, 10, 5, 20, 3, 4);
		ItemStack flint = new ItemStack(Material.FLINT_AND_STEEL);
		player.getInventory().setItemInMainHand(flint);
		Block frameBlock = world.getBlockAt(9, 6, 20);
		PlayerInteractEvent light = new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK, flint, frameBlock, BlockFace.EAST, EquipmentSlot.HAND);
		server.getPluginManager().callEvent(light);
		assertTrue(light.isCancelled(), "no fire");
		Portal portal = plugin.portals().at(world.getBlockAt(10, 5, 20));
		assertNotNull(portal);

		// Mining.
		player.setGameMode(GameMode.SURVIVAL);
		BlockBreakEvent mine = new BlockBreakEvent(frameBlock, player);
		server.getPluginManager().callEvent(mine);
		assertTrue(mine.isCancelled());
		// Even sneaking (lit frames are never reclaimed).
		player.setSneaking(true);
		BlockBreakEvent sneakMine = new BlockBreakEvent(frameBlock, player);
		server.getPluginManager().callEvent(sneakMine);
		assertTrue(sneakMine.isCancelled());
		assertTrue(Frames.isFrame(frameBlock));
		// Explosions.
		List<Block> blast = new ArrayList<>(List.of(frameBlock, world.getBlockAt(10, 5, 20), world.getBlockAt(30, 5, 30)));
		EntityExplodeEvent boom = new EntityExplodeEvent(new TestZombie(server, frameBlock.getLocation()), frameBlock.getLocation(), blast, 1.0F,
			org.bukkit.ExplosionResult.DESTROY);
		server.getPluginManager().callEvent(boom);
		assertEquals(List.of(world.getBlockAt(30, 5, 30)), boom.blockList(), "the frame and the inside are left out of the blast");
		// Pistons.
		Block piston = world.getBlockAt(8, 6, 20);
		BlockPistonExtendEvent push = new BlockPistonExtendEvent(piston, List.of(frameBlock), BlockFace.EAST);
		server.getPluginManager().callEvent(push);
		assertTrue(push.isCancelled());
		// Building inside it.
		Block inside = world.getBlockAt(11, 6, 20);
		org.bukkit.event.block.BlockPlaceEvent place = new org.bukkit.event.block.BlockPlaceEvent(inside, inside.getState(),
			inside.getRelative(BlockFace.DOWN), new ItemStack(Material.STONE), player, true, EquipmentSlot.HAND);
		server.getPluginManager().callEvent(place);
		assertTrue(place.isCancelled());
		// Only an admin in creative, sneaking, on purpose: that puts the portal out.
		player.setGameMode(GameMode.CREATIVE);
		BlockBreakEvent admin = new BlockBreakEvent(frameBlock, player);
		server.getPluginManager().callEvent(admin);
		assertFalse(admin.isCancelled());
		assertNull(plugin.portals().at(world.getBlockAt(10, 5, 20)), "the portal went out");
	}

	@Test
	void unlitFramesCanBeTakenBack() {
		frame(world, 10, 5, 50, 3, 4);
		Block block = world.getBlockAt(9, 6, 50);
		player.setGameMode(GameMode.SURVIVAL);
		BlockBreakEvent notSneaking = new BlockBreakEvent(block, player);
		server.getPluginManager().callEvent(notSneaking);
		assertTrue(notSneaking.isCancelled());
		player.setSneaking(true);
		server.getPluginManager().callEvent(new BlockBreakEvent(block, player));
		assertEquals(Material.AIR, block.getType());
		assertFalse(Frames.isFrame(block));
		assertTrue(world.getEntitiesByClass(org.bukkit.entity.Item.class).stream().anyMatch(i -> Frames.isItem(i.getItemStack())));
	}

	@Test
	void walkingIntoAPortalTakesYouToTheBloodlandsAndBack() {
		frame(world, 10, 5, 20, 3, 4);
		plugin.portals().ignite(world.getBlockAt(9, 6, 20), player);
		player.teleport(new Location(world, 11.5, 5.0, 18.5)); // just south... north of it
		Location into = new Location(world, 11.5, 5.0, 20.5);
		server.getPluginManager().callEvent(new PlayerMoveEvent(player, player.getLocation(), into));
		player.teleport(into);
		ticks(Settings().portal().warmupTicks() + 10);
		assertEquals(lands, player.getWorld(), "into the Bloodlands");
		assertTrue(player.getPersistentDataContainer().has(Keys.RETURN, PersistentDataType.STRING), "the way back is remembered");
		assertFalse(plugin.portals().at(player.getLocation().getBlock()) != null, "never landing inside a portal");

		// Straight back in: nothing, the cooldown holds.
		Portal home = plugin.portals().all().stream().filter(p -> p.world().equals(lands.getUID())).findFirst().orElseThrow();
		Location homeInside = new Location(lands, home.x() + 0.5, home.y(), home.z() + 0.5);
		server.getPluginManager().callEvent(new PlayerMoveEvent(player, player.getLocation(), homeInside));
		player.teleport(homeInside);
		ticks(Settings().portal().warmupTicks() + 2);
		assertEquals(lands, player.getWorld(), "no teleport loops: the cooldown holds");
		// After the cooldown, standing in the return portal takes them home, in front of the portal they used.
		ticks(Settings().portal().cooldownTicks());
		server.getPluginManager().callEvent(new PlayerMoveEvent(player, player.getLocation().clone().add(0, 0, 1), homeInside));
		ticks(Settings().portal().warmupTicks() + 10);
		assertEquals(world, player.getWorld(), "home again");
		assertTrue(player.getLocation().distance(new Location(world, 11.5, 5.0, 18.5)) < 3.0, "beside the portal they took: " + player.getLocation());
		assertFalse(player.getPersistentDataContainer().has(Keys.RETURN, PersistentDataType.STRING));
	}

	@Test
	void adminsBuildAndRemovePortalsByCommand() {
		player.teleport(new Location(world, 0.5, 5.0, 0.5, 180.0F, 0.0F)); // facing north
		assertTrue(player.performCommand("blood portal build 5 7"));
		Portal portal = plugin.portals().all().stream().filter(p -> p.world().equals(world.getUID())).findFirst().orElseThrow();
		assertEquals(5, portal.width());
		assertEquals(7, portal.height());
		int frames = 0;
		for (Block block : portal.frame(world)) {
			frames += Frames.isFrame(block) ? 1 : 0;
		}
		assertEquals(2 * (5 + 2) + 2 * 7, frames);
		TestPlayer alex = server.addTestPlayer("Alex");
		alex.performCommand("blood portal build 3 4");
		assertEquals(1, plugin.portals().all().stream().filter(p -> p.world().equals(world.getUID())).count(),
			"ordinary players can't build portals by command");
		plugin.portals().destroy(portal, true);
		assertNull(plugin.portals().at(world.getBlockAt(portal.x(), portal.y(), portal.z())));
		assertEquals(Material.AIR, world.getBlockAt(portal.x() - 1, portal.y(), portal.z()).getType());
	}

	@Test
	void portalsAreSavedAndComeBack() {
		frame(world, 10, 5, 20, 4, 5);
		plugin.portals().ignite(world.getBlockAt(9, 6, 20), player);
		int lit = plugin.portals().all().size();
		plugin.portals().disable();
		plugin.portals().enable(plugin.bloodlands());
		assertEquals(lit, plugin.portals().all().size());
		assertNotNull(plugin.portals().at(world.getBlockAt(12, 8, 20)));
		assertTrue(Frames.isFrame(world.getBlockAt(9, 6, 20)), "frames are known by their chunk's data, not a cache");
	}

	// ---- commands ----------------------------------------------------------------------------------

	@Test
	void levelCommandSetsTheHeldWeaponsLevel() {
		player.getInventory().setItemInMainHand(Weapons.create(WeaponType.NULLBLADE));
		assertTrue(player.performCommand("blood level Steve 4"));
		assertEquals(4, BloodLevels.level(player.getInventory().getItemInMainHand()));
		assertTrue(player.performCommand("blood level Steve 99"));
		assertEquals(4, BloodLevels.level(player.getInventory().getItemInMainHand()), "out of range is refused");
		TestPlayer alex = server.addTestPlayer("Alex");
		alex.getInventory().setItemInMainHand(Weapons.create(WeaponType.NULLBLADE));
		alex.performCommand("blood level Alex 5");
		assertEquals(0, BloodLevels.level(alex.getInventory().getItemInMainHand()), "players can't set their own level");
		chat(player);
		player.performCommand("blood info");
		assertTrue(any(chat(player), "Blood Level IV"));
	}

	// ---- the Bloodlands' shape ------------------------------------------------------------------------

	@Test
	void theBloodlandsAreNotFlatAndHaveLakes() {
		Terrain terrain = new Terrain(12345, 1.0);
		Set<Integer> heights = new HashSet<>();
		int min = Integer.MAX_VALUE;
		int max = Integer.MIN_VALUE;
		int lake = 0;
		int pool = 0;
		for (int x = -512; x < 512; x += 4) {
			for (int z = -512; z < 512; z += 4) {
				Terrain.Column c = terrain.column(x, z);
				heights.add(c.height());
				min = Math.min(min, c.height());
				max = Math.max(max, c.height());
				if (c.kind() == Terrain.Kind.LAKE) {
					lake++;
					assertTrue(c.water() > c.height(), "water stands on its bed");
				}
				if (c.kind() == Terrain.Kind.POOL) {
					pool++;
				}
			}
		}
		assertTrue(max - min >= 30, "hills and valleys: " + min + ".." + max);
		assertTrue(heights.size() >= 30);
		assertTrue(lake > 0, "blood lakes");
		assertTrue(pool > 0, "blood pools");
		assertTrue(lake < 65536 / 20, "lakes are uncommon: " + lake);
		// The same seed, the same land.
		assertEquals(terrain.column(137, -2981), new Terrain(12345, 1.0).column(137, -2981));
		// Arrivals land on dry, level ground.
		BloodlandsGenerator generator = new BloodlandsGenerator(terrain, org.bukkit.block.Biome.PLAINS, false);
		int[] spot = generator.findDryLand(0, 0, 256);
		assertEquals(Terrain.Kind.LAND, terrain.column(spot[0], spot[2]).kind());
	}

	@Test
	void everyLakeHoldsItsWater() {
		// Next to every water column, the land is at least as high as the water (or it's water too):
		// nothing spills when a block update wakes the water.
		Terrain terrain = new Terrain(987654321L, 1.5);
		int checked = 0;
		for (int x = -700; x < 700; x++) {
			for (int z = -700; z < 700; z += 3) {
				Terrain.Column c = terrain.column(x, z);
				if (!c.wet()) {
					continue;
				}
				for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
					Terrain.Column n = terrain.column(x + d[0], z + d[1]);
					assertTrue(n.wet() && n.water() == c.water() || n.height() >= c.water(),
						"water at " + x + "," + z + " (level " + c.water() + ") spills into " + (x + d[0]) + "," + (z + d[1]) + " (" + n + ")");
				}
				checked++;
			}
		}
		assertTrue(checked > 200, "checked " + checked + " wet columns");
	}
}
