package net.unchartedsmp.bloodbath;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.zip.ZipInputStream;
import net.kyori.adventure.resource.ResourcePackInfo;
import net.kyori.adventure.resource.ResourcePackRequest;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.unchartedsmp.bloodbath.ability.Ability;
import net.unchartedsmp.bloodbath.ability.Cooldowns;
import net.unchartedsmp.bloodbath.ability.NullField;
import net.unchartedsmp.bloodbath.armor.ArmorPiece;
import net.unchartedsmp.bloodbath.armor.BloodArmor;
import net.unchartedsmp.bloodbath.config.Settings;
import net.unchartedsmp.bloodbath.gui.ArmoryMenu;
import net.unchartedsmp.bloodbath.hud.Hud;
import net.unchartedsmp.bloodbath.pack.ResourcePackService;
import net.unchartedsmp.bloodbath.recipe.Recipes;
import net.unchartedsmp.bloodbath.util.Targeting;
import net.unchartedsmp.bloodbath.support.TestArrow;
import net.unchartedsmp.bloodbath.support.TestPlayer;
import net.unchartedsmp.bloodbath.support.TestServer;
import net.unchartedsmp.bloodbath.support.TestWorld;
import net.unchartedsmp.bloodbath.support.TestZombie;
import net.unchartedsmp.bloodbath.weapon.Behaviors;
import net.unchartedsmp.bloodbath.weapon.WeaponType;
import net.unchartedsmp.bloodbath.weapon.Weapons;
import net.unchartedsmp.bloodbath.weapon.behavior.Mirrorfang;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

/**
 * Loads the plugin into MockBukkit (a mock Paper 1.21.11 server) and plays it: commands, every
 * weapon's ability and passive, kill tracking, the armory, the mirror's dupe guards, recipes and
 * the resource pack server. The test world validates particle data the way Paper does, and any
 * error the plugin logs fails the test.
 */
class BloodbathPluginTest {
	private TestServer server;
	private TestWorld world;
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
		YamlConfiguration config = new YamlConfiguration();
		try (InputStream in = getClass().getClassLoader().getResourceAsStream("config.yml")) {
			config.load(new InputStreamReader(in, StandardCharsets.UTF_8));
		}
		config.set("resource-pack.port", 0); // any free port, so tests never collide
		plugin = MockBukkit.loadWithConfig(BloodbathPlugin.class, config);
		plugin.getLogger().addHandler(errorCatcher);
		player = server.addTestPlayer("Steve");
		player.setOp(true);
		stand(player, 0.5, 0.5, -90.0F, 0.0F); // facing +X (east)
	}

	@AfterEach
	void tearDown() {
		MockBukkit.unmock();
		Logger.getLogger("").removeHandler(errorCatcher);
		for (LogRecord record : errors) {
			System.err.println(record.getLevel() + " " + record.getMessage() + (record.getThrown() == null ? "" : " " + record.getThrown()));
			if (record.getThrown() != null) {
				record.getThrown().printStackTrace();
			}
		}
		assertTrue(errors.isEmpty(), "the plugin logged " + errors.size() + " error(s)");
	}

	// ---- helpers ------------------------------------------------------------------------------

	private void stand(TestPlayer who, double x, double z, float yaw, float pitch) {
		who.teleport(new Location(world, x, 5.0, z, yaw, pitch));
	}

	private ItemStack hold(TestPlayer who, WeaponType type) {
		who.getInventory().setItemInMainHand(Weapons.create(type));
		return who.getInventory().getItemInMainHand();
	}

	private void rightClick(TestPlayer who) {
		server.getPluginManager().callEvent(new PlayerInteractEvent(who, Action.RIGHT_CLICK_AIR,
			who.getInventory().getItemInMainHand(), null, BlockFace.SELF, EquipmentSlot.HAND));
	}

	private void ticks(int count) {
		server.getScheduler().performTicks(count);
	}

	private TestZombie zombie(double x, double z) {
		return new TestZombie(server, new Location(world, x, 5.0, z));
	}

	private static DamageSource attackBy(Entity attacker) {
		return DamageSource.builder(DamageType.PLAYER_ATTACK).withCausingEntity(attacker).withDirectEntity(attacker).build();
	}

	/** A landed melee hit (the event only; health is the test's business). */
	@SuppressWarnings("removal")
	private EntityDamageByEntityEvent melee(TestPlayer attacker, LivingEntity target, double damage) {
		EntityDamageByEntityEvent event = new EntityDamageByEntityEvent(attacker, target,
			EntityDamageEvent.DamageCause.ENTITY_ATTACK, attackBy(attacker), damage);
		server.getPluginManager().callEvent(event);
		return event;
	}

	private static String plain(Component component) {
		return component == null ? "" : PlainTextComponentSerializer.plainText().serialize(component);
	}

	private static List<String> chat(TestPlayer who) {
		List<String> out = new ArrayList<>();
		for (Component message; (message = who.nextComponentMessage()) != null; ) {
			out.add(plain(message));
		}
		return out;
	}

	private static List<String> actionBars(TestPlayer who) {
		List<String> out = new ArrayList<>();
		for (Component message; (message = who.nextActionBar()) != null; ) {
			out.add(plain(message));
		}
		return out;
	}

	private static boolean any(List<String> lines, String fragment) {
		return lines.stream().anyMatch(line -> line.contains(fragment));
	}

	private static List<String> lore(ItemStack item) {
		ItemMeta meta = item.getItemMeta();
		List<String> out = new ArrayList<>();
		if (meta.lore() != null) {
			meta.lore().forEach(line -> out.add(plain(line)));
		}
		return out;
	}

	private static String sha1(byte[] data) throws Exception {
		return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(data));
	}

	// ---- the resource pack ------------------------------------------------------------------

	@Test
	void packIsExportedAndServedAndNothingElseIs() throws Exception {
		ResourcePackService packs = plugin.packs();
		assertTrue(packs.isActive(), packs.status());
		byte[] bundled;
		try (InputStream in = plugin.getResource("resourcepack.zip")) {
			bundled = in.readAllBytes();
		}
		byte[] exported = Files.readAllBytes(plugin.getDataFolder().toPath().resolve(ResourcePackService.EXPORT_NAME));
		assertEquals(sha1(bundled), sha1(exported));
		assertEquals(sha1(bundled), packs.sha1());

		URI address = packs.address(player);
		assertEquals("play.example.com", address.getHost(), "players get the host they connected with");
		URI local = URI.create("http://127.0.0.1:" + address.getPort() + address.getPath());

		HttpURLConnection get = (HttpURLConnection) local.toURL().openConnection();
		assertEquals(200, get.getResponseCode());
		byte[] served = get.getInputStream().readAllBytes();
		assertEquals(packs.sha1(), sha1(served));
		List<String> entries = new ArrayList<>();
		try (ZipInputStream zip = new ZipInputStream(new java.io.ByteArrayInputStream(served))) {
			for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
				entries.add(entry.getName());
			}
		}
		assertTrue(entries.contains("pack.mcmeta"));
		assertTrue(entries.contains("assets/minecraft/items/netherite_sword.json"));
		assertTrue(entries.contains("assets/unchartedsmp/textures/gui/sprites/tooltip/bloodbath_frame.png"));
		assertEquals(1, packs.downloads());

		HttpURLConnection head = (HttpURLConnection) local.toURL().openConnection();
		head.setRequestMethod("HEAD");
		assertEquals(200, head.getResponseCode());
		assertEquals(String.valueOf(served.length), head.getHeaderField("Content-Length"));

		for (String path : List.of("/", "/config.yml", address.getPath() + "x", "/../plugins/Bloodbath/config.yml")) {
			HttpURLConnection other = (HttpURLConnection) URI.create("http://127.0.0.1:" + address.getPort() + path).toURL().openConnection();
			assertEquals(404, other.getResponseCode(), path);
		}
		HttpURLConnection post = (HttpURLConnection) local.toURL().openConnection();
		post.setRequestMethod("POST");
		assertEquals(405, post.getResponseCode());
	}

	@Test
	void joiningPlayersAreSentThePackAndToldIfTheyDeclineIt() {
		ticks(21);
		assertEquals(1, player.packs.size());
		ResourcePackRequest request = player.packs.get(0);
		ResourcePackInfo info = request.packs().get(0);
		assertEquals(ResourcePackService.PACK_ID, info.id());
		assertEquals("play.example.com", info.uri().getHost());
		assertEquals(plugin.packs().sha1(), info.hash());
		assertFalse(request.required());
		assertFalse(request.replace(), "other server packs stay");

		chat(player);
		server.getPluginManager().callEvent(new PlayerResourcePackStatusEvent(player, ResourcePackService.PACK_ID,
			PlayerResourcePackStatusEvent.Status.DECLINED));
		assertTrue(any(chat(player), "No pack, no 3D weapons"));
		player.performCommand("bloodbath pack");
		assertEquals(2, player.packs.size());
	}

	// ---- items ---------------------------------------------------------------------------------

	@Test
	void giveAllCreatesEveryWeaponProperly() {
		assertTrue(player.performCommand("bloodbath give Steve all"));
		List<ItemStack> given = new ArrayList<>();
		for (ItemStack item : player.getInventory().getContents()) {
			if (item != null) {
				given.add(item);
			}
		}
		assertEquals(WeaponType.values().length + ArmorPiece.values().length, given.size(), "every weapon and the Blood Knight set");
		for (WeaponType type : WeaponType.values()) {
			ItemStack item = given.stream().filter(i -> Weapons.typeOf(i) == type).findFirst().orElse(null);
			assertNotNull(item, type.id());
			assertEquals(type.base(), item.getType());
			assertEquals(1, item.getMaxStackSize(), type.id());
			ItemMeta meta = item.getItemMeta();
			assertEquals(type.displayName(), plain(meta.itemName()));
			assertTrue(any(lore(item), type.ability().displayName() + " · cooldown "), type.id());
			assertTrue(any(lore(item), "0 kills · Unblooded"), type.id());
			assertEquals(0, Weapons.kills(item));
			if (type.durability() > 0) {
				assertEquals(type.durability(), ((Damageable) meta).getMaxDamage(), type.id());
				assertFalse(meta.isUnbreakable());
			} else {
				assertTrue(meta.isUnbreakable(), type.id());
			}
			assertFalse(meta.getEnchantmentGlintOverride(), type.id());
			assertNull(meta.getTooltipStyle(), "the tooltip frame needs a required pack by default");
		}
		assertEquals(WeaponType.values().length + ArmorPiece.values().length,
			chat(player).stream().filter(line -> line.startsWith("☠ You take up the")).count());
	}

	@Test
	void giveNeedsPermission() {
		TestPlayer alex = server.addTestPlayer("Alex");
		stand(alex, 3.5, 3.5, 0.0F, 0.0F);
		alex.performCommand("bloodbath give Alex riftblade");
		assertTrue(any(chat(alex), "You don't have permission"));
		assertTrue(alex.getInventory().isEmpty());
		assertTrue(alex.performCommand("bloodbath info riftblade"), "info is for everyone");
		assertTrue(any(chat(alex), "Bloodrift Blade"));
	}

	@Test
	void weaponsAreFoundByFriendlyNamesAndPlainItemsAreNotWeapons() {
		assertEquals(WeaponType.VOID_SCYTHE, WeaponType.find("scythe"));
		assertEquals(WeaponType.VOID_SCYTHE, WeaponType.find("Hemorrhage Scythe"));
		assertEquals(WeaponType.NULLBLADE, WeaponType.find("clotblade"));
		assertEquals(WeaponType.PARADOX_BOW, WeaponType.find("PARADOX_BOW"));
		assertEquals(WeaponType.BLOOD_GRIMOIRE, WeaponType.find("grimoire"));
		assertNull(WeaponType.find("excalibur"));

		assertNull(Weapons.typeOf(null));
		assertNull(Weapons.typeOf(new ItemStack(Material.NETHERITE_SWORD)));
		assertNull(Weapons.typeOf(new ItemStack(Material.DIAMOND)));
		ItemStack fake = new ItemStack(Material.NETHERITE_SWORD);
		fake.editMeta(meta -> meta.getPersistentDataContainer().set(Keys.WEAPON, PersistentDataType.STRING, "excalibur"));
		assertNull(Weapons.typeOf(fake));
		// A weapon's id on the wrong base item doesn't count either.
		ItemStack wrongBase = new ItemStack(Material.STICK);
		wrongBase.editMeta(meta -> meta.getPersistentDataContainer().set(Keys.WEAPON, PersistentDataType.STRING, "riftblade"));
		assertNull(Weapons.typeOf(wrongBase));
	}

	// ---- abilities -----------------------------------------------------------------------------

	@Test
	void abilitiesGoOnCooldownAndAnnounceWhenReady() {
		hold(player, WeaponType.GRAVESTONE);
		rightClick(player);
		assertFalse(Cooldowns.isReady(player, Ability.GRAVESTONE));
		ticks(1);
		actionBars(player);
		rightClick(player);
		assertTrue(any(actionBars(player), "Grave Pull is still clotting"));
		ticks(Ability.GRAVESTONE.defaultCooldownTicks() + 4);
		assertTrue(Cooldowns.isReady(player, Ability.GRAVESTONE));
		assertTrue(any(actionBars(player), "✦ Grave Pull ready"));
	}

	@Test
	void oneClickFiresOneAbility() {
		hold(player, WeaponType.GRAVESTONE);
		player.getInventory().setItemInOffHand(Weapons.create(WeaponType.CHRONOS));
		rightClick(player);
		server.getPluginManager().callEvent(new PlayerInteractEvent(player, Action.RIGHT_CLICK_AIR,
			player.getInventory().getItemInOffHand(), null, BlockFace.SELF, EquipmentSlot.OFF_HAND));
		assertFalse(Cooldowns.isReady(player, Ability.GRAVESTONE));
		ticks(20);
		// The off-hand event in the same tick was ignored: no blood-mark was placed.
		assertFalse(any(actionBars(player), "Blood-mark set"));
	}

	@Test
	void gravestonePullsEverythingInThenLaunchesIt() {
		TestZombie zombie = zombie(4.5, 0.5);
		hold(player, WeaponType.GRAVESTONE);
		rightClick(player);
		ticks(5);
		assertTrue(zombie.getVelocity().getX() < 0.0, "pulled toward the caster");
		assertEquals(0.0, player.getVelocity().length(), 1.0E-9, "the caster isn't pulled");
		ticks(60);
		assertTrue(zombie.getVelocity().getY() > 1.0, "launched");
		assertTrue(world.particles > 0);
	}

	@Test
	void meteorHitsEverythingButTheCaster() {
		TestZombie zombie = zombie(2.5, 0.5);
		hold(player, WeaponType.METEOR_GAUNTLET);
		server.getPluginManager().callEvent(new PlayerInteractEvent(player, Action.LEFT_CLICK_BLOCK,
			player.getInventory().getItemInMainHand(), world.getBlockAt(0, 4, 0), BlockFace.UP, EquipmentSlot.HAND));
		assertFalse(Cooldowns.isReady(player, Ability.METEOR_GAUNTLET));
		ticks(20);
		assertEquals(20.0, zombie.getHealth(), 1.0E-9, "nothing lands before the telegraph ends");
		ticks(25);
		assertEquals(15.0, zombie.getHealth(), 1.0E-9);
		assertEquals(20.0, player.getHealth(), 1.0E-9);
		assertTrue(zombie.getVelocity().getY() > 0.5, "launched");
	}

	@Test
	void thunderPikeStrikesWhereYouLookAndCarriesYouThere() {
		stand(player, 0.5, 0.5, -90.0F, 10.0F); // ray meets the ground ~9 blocks east
		TestZombie zombie = zombie(9.5, 0.5);
		hold(player, WeaponType.THUNDER_PIKE);
		rightClick(player);
		ticks(10);
		assertEquals(1, world.lightning);
		assertEquals(15.0, zombie.getHealth(), 1.0E-9);
		assertTrue(player.getLocation().getX() > 7.0, "rode the bolt, now at " + player.getLocation());
		assertEquals(5.0, player.getLocation().getY(), 0.01, "landed on the ground, not in it");
	}

	@Test
	void landingsSnapOntoTheGroundAndBackOffFromWalls() {
		Location from = new Location(world, 0.5, 6.6, 0.5);
		// A floor hit just inside the ground block: stand on top of it, not a block above.
		Location landing = Targeting.safeLanding(player, from, new Location(world, 6.5, 4.95, 0.5)).orElseThrow();
		assertEquals(5.0, landing.getY(), 1.0E-9);
		// A wall two blocks high in the way: stop in front of it instead of inside it.
		world.getBlockAt(5, 5, 0).setType(Material.STONE);
		world.getBlockAt(5, 6, 0).setType(Material.STONE);
		landing = Targeting.safeLanding(player, from, new Location(world, 5.2, 5.0, 0.5)).orElseThrow();
		assertTrue(landing.getX() <= 4.7 + 1.0E-9, "stopped at " + landing);
		assertTrue(Targeting.fitsAt(player, landing));
	}

	@Test
	void riftbladeOpensARiftAndStepsThroughIt() {
		stand(player, 0.5, 0.5, -90.0F, 10.0F);
		TestZombie zombie = zombie(8.5, 2.5);
		hold(player, WeaponType.RIFTBLADE);
		rightClick(player);
		ticks(3);
		assertTrue(zombie.getVelocity().length() > 0.0, "the rift drags things in");
		assertTrue(plain(Behaviors.RIFTBLADE.hud(player)).contains("Rift open"));
		rightClick(player);
		assertTrue(player.getLocation().getX() > 7.0, "stepped through to " + player.getLocation());
		ticks(1);
		rightClick(player);
		assertTrue(any(actionBars(player), "still clotting"), "stepping through was the free recast");
	}

	@Test
	void chronosMarksThenRecalls() {
		hold(player, WeaponType.CHRONOS);
		rightClick(player);
		assertTrue(any(actionBars(player), "Blood-mark set"));
		assertTrue(Cooldowns.isReady(player, Ability.CHRONOS), "cooldown starts on recall");
		stand(player, 6.5, 6.5, 90.0F, 30.0F);
		ticks(20);
		rightClick(player);
		assertEquals(0.5, player.getLocation().getX(), 1.0E-9);
		assertEquals(0.5, player.getLocation().getZ(), 1.0E-9);
		assertEquals(-90.0F, player.getLocation().getYaw(), 1.0E-4, "facing restored too");
		assertFalse(Cooldowns.isReady(player, Ability.CHRONOS));
	}

	@Test
	void bloodhookReelsYouTowardWhatYouHook() {
		TestZombie zombie = zombie(6.5, 0.5);
		hold(player, WeaponType.BLOODHOOK);
		rightClick(player);
		ticks(10);
		assertTrue(player.getVelocity().getX() > 0.5, "yanked east, velocity " + player.getVelocity());
		assertTrue(player.getVelocity().getY() > 0.0);
		assertEquals(20.0, zombie.getHealth(), 1.0E-9, "the hook itself doesn't hurt");
	}

	@Test
	void bloodhookCantHookThroughWalls() {
		zombie(6.5, 0.5);
		world.getBlockAt(3, 6, 0).setType(Material.STONE); // at eye height between them
		hold(player, WeaponType.BLOODHOOK);
		rightClick(player);
		assertTrue(any(actionBars(player), "The hook found no blood."));
	}

	@Test
	void hemorrhageScytheBleedsThenBursts() {
		TestZombie target = zombie(2.0, 0.5);
		TestZombie bystander = zombie(3.5, 1.5);
		hold(player, WeaponType.VOID_SCYTHE);
		for (int i = 1; i <= 4; i++) {
			melee(player, target, 6.0);
			assertTrue(any(actionBars(player), i + "/5"), "hit " + i);
			ticks(1);
		}
		melee(player, target, 6.0);
		assertTrue(any(actionBars(player), "HEMORRHAGE"));
		ticks(2);
		assertEquals(10.0, target.getHealth(), 1.0E-9, "10 hemorrhage damage");
		assertEquals(12.0, bystander.getHealth(), 1.0E-9, "8 splash damage");
	}

	@Test
	void harvestCutsWhatsInFrontAndBleedsIt() {
		TestZombie front = zombie(2.5, 0.5);
		TestZombie behind = zombie(-1.5, 0.5);
		hold(player, WeaponType.VOID_SCYTHE);
		rightClick(player);
		assertEquals(16.0, front.getHealth(), 1.0E-9);
		assertEquals(20.0, behind.getHealth(), 1.0E-9, "the arc only reaps in front");
		assertTrue(any(actionBars(player), "1/5"), "and adds bleed");
	}

	@Test
	void vampireFangDrinksAndDashes() {
		TestZombie zombie = zombie(1.5, 0.5);
		hold(player, WeaponType.VAMPIRE_FANG);
		player.setHealth(10.0);
		melee(player, zombie, 8.0);
		assertEquals(12.0, player.getHealth(), 1.0E-9, "25% of 8 damage");
		rightClick(player);
		assertTrue(player.getVelocity().getX() > 1.0, "dashing east");
		ticks(2);
		assertEquals(16.0, zombie.getHealth(), 1.0E-9, "cut by the dash");
		assertEquals(14.0, player.getHealth(), 1.0E-9, "and drank from it");
		assertTrue(plain(Behaviors.VAMPIRE_FANG.hud(player)).contains("drunk"));
	}

	@Test
	void grimoireDrainsACreatureIntoYou() {
		TestZombie zombie = zombie(5.5, 0.5);
		hold(player, WeaponType.BLOOD_GRIMOIRE);
		player.setHealth(10.0);
		rightClick(player);
		assertTrue(plain(Behaviors.BLOOD_GRIMOIRE.hud(player)).contains("Transfusion"));
		ticks(35);
		assertEquals(14.0, zombie.getHealth(), 1.0E-6);
		assertEquals(16.0, player.getHealth(), 1.0E-6);
	}

	@Test
	void grimoireGivesBloodToAFriendEvenWithPvpOff() {
		world.setPVP(false);
		TestPlayer alex = server.addTestPlayer("Alex");
		stand(alex, 4.5, 0.5, 90.0F, 0.0F);
		alex.setHealth(10.0);
		hold(player, WeaponType.BLOOD_GRIMOIRE);
		player.setSneaking(true);
		rightClick(player);
		assertEquals(16.0, player.getHealth(), 1.0E-9);
		assertEquals(18.0, alex.getHealth(), 1.0E-9);
		assertTrue(alex.hasPotionEffect(PotionEffectType.REGENERATION));
	}

	@Test
	void abilitiesDontTouchPlayersWhenPvpIsOff() {
		world.setPVP(false);
		TestPlayer alex = server.addTestPlayer("Alex");
		stand(alex, 2.5, 0.5, 90.0F, 0.0F);
		hold(player, WeaponType.GRAVESTONE);
		rightClick(player);
		ticks(65);
		assertEquals(0.0, alex.getVelocity().length(), 1.0E-9);
	}

	@Test
	void clotbladeSuppressesAbilities() {
		TestPlayer alex = server.addTestPlayer("Alex");
		stand(alex, 1.5, 0.5, 90.0F, 0.0F);
		hold(alex, WeaponType.RIFTBLADE);
		hold(player, WeaponType.NULLBLADE);
		melee(player, alex, 6.0);
		assertTrue(NullField.isNullified(alex));
		actionBars(alex);
		rightClick(alex);
		assertTrue(any(actionBars(alex), "Your blood has clotted"));
		assertTrue(Cooldowns.isReady(alex, Ability.RIFTBLADE), "nothing fired");
		ticks(84);
		assertFalse(NullField.isNullified(alex), "wears off after 4s");
	}

	@Test
	void clotFieldSuppressesEveryoneInsideButNotTheClotblade() {
		TestPlayer alex = server.addTestPlayer("Alex");
		stand(alex, 2.5, 0.5, 90.0F, 0.0F);
		TestPlayer far = server.addTestPlayer("Sam");
		stand(far, 9.5, 0.5, 90.0F, 0.0F);
		stand(player, 0.5, 0.5, -90.0F, 90.0F); // looking at his own feet
		hold(player, WeaponType.NULLBLADE);
		rightClick(player);
		assertFalse(Cooldowns.isReady(player, Ability.NULLBLADE_ZONE));
		assertTrue(NullField.isNullified(alex));
		assertFalse(NullField.isNullified(far), "only inside the field");
		hold(player, WeaponType.GRAVESTONE);
		ticks(1);
		rightClick(player);
		assertTrue(Cooldowns.isReady(player, Ability.GRAVESTONE), "the field stops its caster's other weapons too");
		ticks(165);
		assertFalse(NullField.isNullified(alex), "gone after 8s");
	}

	@Test
	void mirrorFightsIsSealedAndDissolves() {
		TestZombie zombie = zombie(2.5, 2.5);
		hold(player, WeaponType.MIRRORFANG);
		rightClick(player);
		ArmorStand mirror = world.getEntitiesByClass(ArmorStand.class).stream().filter(Mirrorfang::isMirror).findFirst().orElseThrow();
		assertFalse(mirror.isPersistent(), "never saved to disk");
		assertTrue(mirror.isInvulnerable());
		assertTrue(plain(mirror.customName()).contains("Steve"));
		ItemStack copy = mirror.getEquipment().getItemInMainHand();
		assertEquals(Material.NETHERITE_SWORD, copy.getType());
		assertNull(Weapons.typeOf(copy), "the mirror holds a picture of the weapon, not the weapon");

		PlayerInteractAtEntityEvent click = new PlayerInteractAtEntityEvent(player, mirror, new Vector(0, 1, 0));
		server.getPluginManager().callEvent(click);
		assertTrue(click.isCancelled());
		EntityDamageEvent hit = new EntityDamageEvent(mirror, EntityDamageEvent.DamageCause.ENTITY_ATTACK, attackBy(player), 5.0);
		server.getPluginManager().callEvent(hit);
		assertTrue(hit.isCancelled());
		List<ItemStack> drops = new ArrayList<>(List.of(new ItemStack(Material.NETHERITE_CHESTPLATE)));
		server.getPluginManager().callEvent(new EntityDeathEvent(mirror, attackBy(player), drops));
		assertTrue(drops.isEmpty(), "a killed mirror drops nothing");

		ticks(11);
		assertEquals(16.0, zombie.getHealth(), 1.0E-9, "slashed once");
		ticks(140);
		assertFalse(mirror.isValid(), "dissolved");
		assertEquals(0, Behaviors.MIRRORFANG.liveCount());
	}

	@Test
	void disablingThePluginRemovesLiveMirrors() {
		hold(player, WeaponType.MIRRORFANG);
		rightClick(player);
		ArmorStand mirror = world.getEntitiesByClass(ArmorStand.class).stream().filter(Mirrorfang::isMirror).findFirst().orElseThrow();
		server.getPluginManager().disablePlugin(plugin);
		assertFalse(mirror.isValid());
	}

	@Test
	void paradoxBowEchoesFullyDrawnShots() {
		TestZombie near = zombie(5.5, 0.5);
		TestZombie far = zombie(10.5, 0.5);
		ItemStack bow = hold(player, WeaponType.PARADOX_BOW);
		Arrow weak = new TestArrow(server, player.getEyeLocation());
		server.getPluginManager().callEvent(new EntityShootBowEvent(player, bow, new ItemStack(Material.ARROW), weak, EquipmentSlot.HAND, 0.5F, true));
		assertTrue(Cooldowns.isReady(player, Ability.PARADOX_BOW), "a half-drawn shot is just an arrow");
		assertEquals(WeaponType.PARADOX_BOW.id(), weak.getPersistentDataContainer().get(Keys.WEAPON, PersistentDataType.STRING));

		Arrow full = new TestArrow(server, player.getEyeLocation());
		full.setCritical(true);
		server.getPluginManager().callEvent(new EntityShootBowEvent(player, bow, new ItemStack(Material.ARROW), full, EquipmentSlot.HAND, 1.0F, true));
		assertFalse(Cooldowns.isReady(player, Ability.PARADOX_BOW));
		ticks(50);
		assertEquals(20.0, near.getHealth(), 1.0E-9, "the echo waits 3s");
		ticks(30);
		assertEquals(13.0, near.getHealth(), 1.0E-9);
		assertEquals(13.0, far.getHealth(), 1.0E-9, "hit once each along the path");
	}

	// ---- kills ---------------------------------------------------------------------------------

	@Test
	void killsAreCountedOnTheWeaponAndRankItUp() {
		hold(player, WeaponType.RIFTBLADE);
		for (int i = 0; i < 5; i++) {
			TestZombie zombie = zombie(1.5, 0.5);
			server.getPluginManager().callEvent(new EntityDeathEvent(zombie, attackBy(player), new ArrayList<>()));
		}
		ItemStack weapon = player.getInventory().getItemInMainHand();
		assertEquals(5, Weapons.kills(weapon));
		assertTrue(any(lore(weapon), "5 kills · Blooded"));
		assertTrue(any(chat(player), "is now Blooded"));
	}

	@Test
	void abilityKillsCountForTheWeaponThatMadeThem() {
		stand(player, 0.5, 0.5, -90.0F, 10.0F);
		TestZombie zombie = zombie(9.5, 0.5);
		zombie.setHealth(3.0);
		hold(player, WeaponType.THUNDER_PIKE);
		rightClick(player);
		ticks(10);
		assertTrue(zombie.isDead());
		assertEquals(1, Weapons.kills(player.getInventory().getItemInMainHand()));
	}

	@Test
	void mobKillsCanBeLeftOut() {
		plugin.getConfig().set("kill-tracking.count-mobs", false);
		plugin.saveConfig();
		player.performCommand("bloodbath reload");
		hold(player, WeaponType.RIFTBLADE);
		server.getPluginManager().callEvent(new EntityDeathEvent(zombie(1.5, 0.5), attackBy(player), new ArrayList<>()));
		assertEquals(0, Weapons.kills(player.getInventory().getItemInMainHand()));
	}

	// ---- Blood Knight armour -------------------------------------------------------------------

	private void wear(TestPlayer who, ArmorPiece... pieces) {
		for (ArmorPiece piece : pieces) {
			who.getInventory().setItem(piece.slot(), BloodArmor.create(piece));
		}
	}

	private static double modifier(ItemStack item, Attribute attribute) {
		var modifiers = item.getItemMeta().getAttributeModifiers(attribute);
		assertNotNull(modifiers, attribute.toString());
		return modifiers.stream().mapToDouble(AttributeModifier::getAmount).sum();
	}

	@Test
	void theBloodKnightSetIsNetheritePlusAHeartAndWearsTheKnightsLook() {
		assertTrue(player.performCommand("bloodbath give Steve armor"));
		for (ArmorPiece piece : ArmorPiece.values()) {
			ItemStack item = null;
			for (ItemStack stack : player.getInventory().getContents()) {
				if (BloodArmor.typeOf(stack) == piece) {
					item = stack;
				}
			}
			assertNotNull(item, piece.id());
			assertEquals(piece.base(), item.getType());
			ItemMeta meta = item.getItemMeta();
			assertEquals(piece.displayName(), plain(meta.itemName()));
			// (The look isn't checked: MockBukkit drops the equippable and custom_model_data
			// components whenever item meta is copied.)
			assertEquals(piece.armor(), modifier(item, Attribute.ARMOR), 1.0E-9, piece.id());
			assertEquals(ArmorPiece.TOUGHNESS, modifier(item, Attribute.ARMOR_TOUGHNESS), 1.0E-9, piece.id());
			assertEquals(ArmorPiece.HEALTH, modifier(item, Attribute.MAX_HEALTH), 1.0E-9, piece.id());
			assertTrue(any(lore(item), "(2) Bloodlust: kills heal 1.5 hearts"), piece.id());
			assertTrue(any(lore(item), "Every 60s."), piece.id());
			assertNull(Weapons.typeOf(item), "armour is never mistaken for a weapon");
			assertFalse(BloodArmor.refresh(item), "already current");
		}
		assertNull(BloodArmor.typeOf(new ItemStack(Material.NETHERITE_HELMET)));
		ItemStack forged = new ItemStack(Material.DIAMOND_HELMET);
		forged.editMeta(meta -> meta.getPersistentDataContainer().set(Keys.ARMOR, PersistentDataType.STRING, "blood_knight_helm"));
		assertNull(BloodArmor.typeOf(forged), "a tag on the wrong base item doesn't count");
	}

	@Test
	void armourIsFoundByNameAndGivenPieceByPiece() {
		assertEquals(ArmorPiece.GREAVES, ArmorPiece.find("greaves"));
		assertEquals(ArmorPiece.HELM, ArmorPiece.find("Blood Knight Helm"));
		assertEquals(ArmorPiece.SABATONS, ArmorPiece.find("blood_knight_sabatons"));
		player.performCommand("bloodbath give Steve helm");
		assertEquals(ArmorPiece.HELM, BloodArmor.typeOf(player.getInventory().getItem(0)));
		chat(player);
		player.performCommand("bloodbath info cuirass");
		assertTrue(any(chat(player), "Blood Rage"));
		var command = plugin.getCommand("bloodbath");
		assertTrue(command.tabComplete(player, "bb", new String[] {"give", "Steve", "blood_k"}).contains("blood_knight"));
		assertTrue(command.tabComplete(player, "bb", new String[] {"give", "Steve", "blood_knight_h"}).contains("blood_knight_helm"));
	}

	@Test
	void twoPiecesHealOnEveryKill() {
		wear(player, ArmorPiece.HELM);
		player.setHealth(10.0);
		server.getPluginManager().callEvent(new EntityDeathEvent(zombie(1.5, 0.5), attackBy(player), new ArrayList<>()));
		assertEquals(10.0, player.getHealth(), 1.0E-9, "one piece isn't a set");
		wear(player, ArmorPiece.CUIRASS);
		server.getPluginManager().callEvent(new EntityDeathEvent(zombie(1.5, 0.5), attackBy(player), new ArrayList<>()));
		assertEquals(13.0, player.getHealth(), 1.0E-9);
	}

	@Test
	void theFullSetRagesWhenAHitLeavesYouLow() {
		TestZombie zombie = zombie(2.5, 0.5);
		wear(player, ArmorPiece.HELM, ArmorPiece.CUIRASS, ArmorPiece.GREAVES);
		player.setHealth(10.0);
		server.getPluginManager().callEvent(new EntityDamageEvent(player, EntityDamageEvent.DamageCause.ENTITY_ATTACK, attackBy(zombie), 4.0));
		ticks(1);
		assertTrue(Cooldowns.isReady(player, Ability.BLOOD_RAGE), "three pieces: no rage");

		wear(player, ArmorPiece.SABATONS);
		server.getPluginManager().callEvent(new EntityDamageEvent(player, EntityDamageEvent.DamageCause.ENTITY_ATTACK, attackBy(zombie), 1.0));
		ticks(1);
		assertTrue(Cooldowns.isReady(player, Ability.BLOOD_RAGE), "10 - 1 is still above 40%");

		server.getPluginManager().callEvent(new EntityDamageEvent(player, EntityDamageEvent.DamageCause.ENTITY_ATTACK, attackBy(zombie), 4.0));
		ticks(1);
		assertFalse(Cooldowns.isReady(player, Ability.BLOOD_RAGE));
		assertNotNull(player.getPotionEffect(PotionEffectType.STRENGTH));
		assertNotNull(player.getPotionEffect(PotionEffectType.RESISTANCE));
		assertTrue(zombie.getVelocity().getX() > 0.5, "hurled away: " + zombie.getVelocity());
	}

	@Test
	void aClotStopsTheRage() {
		wear(player, ArmorPiece.values());
		player.setHealth(6.0);
		NullField.debuff(player, 80);
		server.getPluginManager().callEvent(new EntityDamageEvent(player, EntityDamageEvent.DamageCause.ENTITY_ATTACK, attackBy(zombie(2.5, 0.5)), 1.0));
		ticks(1);
		assertTrue(Cooldowns.isReady(player, Ability.BLOOD_RAGE));
		assertNull(player.getPotionEffect(PotionEffectType.STRENGTH));
	}

	@Test
	@SuppressWarnings("removal")
	void theArmoryHasAnArmourRow() {
		player.performCommand("bloodbath armory");
		var view = player.getOpenInventory();
		assertEquals(45, view.getTopInventory().getSize());
		ItemStack icon = view.getTopInventory().getItem(29);
		assertEquals(Material.PAPER, icon.getType());
		assertNull(BloodArmor.typeOf(icon));
		assertEquals("Blood Knight Helm", plain(icon.getItemMeta().itemName()));
		player.simulateInventoryClick(view, ClickType.LEFT, 29);
		assertEquals(ArmorPiece.HELM, BloodArmor.typeOf(player.getInventory().getItem(0)));

		plugin.getConfig().set("armor.enabled", false);
		plugin.saveConfig();
		player.performCommand("bloodbath reload");
		player.performCommand("bloodbath armory");
		assertEquals(36, player.getOpenInventory().getTopInventory().getSize());
		chat(player);
		player.performCommand("bloodbath give Steve armor");
		assertTrue(any(chat(player), "disabled"));
	}

	@Test
	void theMirrorWearsOnlyAPictureOfTheArmour() {
		ItemStack copy = Weapons.displayCopy(BloodArmor.create(ArmorPiece.CUIRASS));
		assertEquals(Material.NETHERITE_CHESTPLATE, copy.getType());
		assertNull(BloodArmor.typeOf(copy));
		assertNull(copy.getItemMeta().getAttributeModifiers(), "no stats");
	}

	// ---- armory, commands, config ------------------------------------------------------------

	@Test
	@SuppressWarnings("removal") // MockBukkit's click simulation is due for a rename
	void armoryGivesAdminsWeaponsAndIsSealed() {
		player.performCommand("bloodbath armory");
		var view = player.getOpenInventory();
		assertInstanceOf(ArmoryMenu.class, view.getTopInventory().getHolder());
		ItemStack icon = view.getTopInventory().getItem(10);
		assertEquals(Material.PAPER, icon.getType(), "icons are paper underneath");
		assertNull(Weapons.typeOf(icon));
		assertEquals("Bloodrift Blade", plain(icon.getItemMeta().itemName()));

		InventoryClickEvent click = player.simulateInventoryClick(view, ClickType.LEFT, 10);
		assertTrue(click.isCancelled());
		assertEquals(WeaponType.RIFTBLADE, Weapons.typeOf(player.getInventory().getItem(0)));

		InventoryClickEvent shift = player.simulateInventoryClick(view, ClickType.SHIFT_LEFT, 0);
		assertTrue(shift.isCancelled(), "nothing moves while the armory is open");
	}

	@Test
	@SuppressWarnings("removal")
	void armoryIsViewOnlyForPlayers() {
		TestPlayer alex = server.addTestPlayer("Alex");
		alex.performCommand("bloodbath armory");
		var view = alex.getOpenInventory();
		assertInstanceOf(ArmoryMenu.class, view.getTopInventory().getHolder());
		alex.simulateInventoryClick(view, ClickType.LEFT, 10);
		assertTrue(alex.getInventory().isEmpty());
		assertTrue(any(chat(alex), "Bloodrift Blade"), "got the details instead");
	}

	@Test
	void hudToggleIsRemembered() {
		player.performCommand("bloodbath hud");
		assertTrue(Hud.isHidden(player));
		assertTrue(player.getPersistentDataContainer().has(Keys.HUD_OFF, PersistentDataType.BYTE));
		player.performCommand("bloodbath hud on");
		assertFalse(Hud.isHidden(player));
		assertFalse(player.getPersistentDataContainer().has(Keys.HUD_OFF, PersistentDataType.BYTE));
	}

	@Test
	void theStatusLineShowsWhileHoldingAWeapon() {
		hold(player, WeaponType.RIFTBLADE);
		ticks(8);
		assertTrue(any(actionBars(player), "Bloodrift  ● READY"));
		player.performCommand("bloodbath hud off");
		actionBars(player);
		ticks(8);
		assertTrue(actionBars(player).stream().allMatch(String::isEmpty));
	}

	@Test
	void reloadAppliesNewCooldownsToExistingItems() {
		player.performCommand("bloodbath give Steve riftblade");
		plugin.getConfig().set("weapons.riftblade.cooldown", 30);
		plugin.saveConfig();
		player.performCommand("bloodbath reload");
		assertEquals(600, Settings.get().cooldownTicks(Ability.RIFTBLADE));
		assertTrue(any(lore(player.getInventory().getItem(0)), "cooldown 30s"));
	}

	@Test
	void disabledWeaponsAreRefusedAndHidden() {
		ItemStack gravestone = Weapons.create(WeaponType.GRAVESTONE);
		plugin.getConfig().set("weapons.gravestone.enabled", false);
		plugin.saveConfig();
		player.performCommand("bloodbath reload");
		player.getInventory().setItemInMainHand(gravestone);
		rightClick(player);
		assertTrue(any(actionBars(player), "sealed away"));
		assertTrue(Cooldowns.isReady(player, Ability.GRAVESTONE));
		chat(player);
		player.performCommand("bloodbath give Steve gravestone");
		assertTrue(any(chat(player), "disabled"));
		player.performCommand("bloodbath armory");
		for (ItemStack icon : player.getOpenInventory().getTopInventory().getContents()) {
			if (icon != null && icon.getItemMeta().itemName() != null) {
				assertFalse(plain(icon.getItemMeta().itemName()).equals("Crimson Gravestone"));
			}
		}
	}

	@Test
	void recipesRegisterOnlyWhenEnabled() {
		assertNull(server.getRecipe(new NamespacedKey(plugin, "riftblade")));
		plugin.getConfig().set("recipes.enabled", true);
		plugin.saveConfig();
		player.performCommand("bloodbath reload");
		assertEquals(WeaponType.values().length + ArmorPiece.values().length, Recipes.count());
		ShapedRecipe recipe = (ShapedRecipe) server.getRecipe(new NamespacedKey(plugin, "riftblade"));
		assertNotNull(recipe);
		assertEquals(WeaponType.RIFTBLADE, Weapons.typeOf(recipe.getResult()));
		ShapedRecipe helm = (ShapedRecipe) server.getRecipe(new NamespacedKey(plugin, "blood_knight_helm"));
		assertEquals(ArmorPiece.HELM, BloodArmor.typeOf(helm.getResult()));
		plugin.getConfig().set("recipes.enabled", false);
		plugin.saveConfig();
		player.performCommand("bloodbath reload");
		assertNull(server.getRecipe(new NamespacedKey(plugin, "riftblade")));
	}

	@Test
	void everyCommandAnswersAndTabCompletes() {
		for (String command : List.of("bloodbath", "bb help", "blood list", "bb info scythe", "bb info", "bb status", "bb pack",
			"bb reset", "bb give Steve nope", "bb nonsense")) {
			assertTrue(player.performCommand(command), command);
			assertFalse(chat(player).isEmpty(), command + " said nothing");
		}
		var command = plugin.getCommand("bloodbath");
		assertTrue(command.tabComplete(player, "bb", new String[] {"gi"}).contains("give"));
		assertTrue(command.tabComplete(player, "bb", new String[] {"give", "Steve", "rift"}).contains("riftblade"));
		TestPlayer alex = server.addTestPlayer("Alex");
		assertFalse(command.tabComplete(alex, "bb", new String[] {""}).contains("give"), "no admin commands for players");
	}

	@Test
	void resetClearsCooldownsAndClots() {
		hold(player, WeaponType.GRAVESTONE);
		rightClick(player);
		NullField.debuff(player, 200);
		player.performCommand("bloodbath reset");
		assertTrue(Cooldowns.isReady(player, Ability.GRAVESTONE));
		assertFalse(NullField.isNullified(player));
	}

	@Test
	void cooldownsSurviveRelogging() {
		hold(player, WeaponType.GRAVESTONE);
		rightClick(player);
		player.disconnect();
		player.reconnect();
		assertFalse(Cooldowns.isReady(player, Ability.GRAVESTONE));
	}
}
