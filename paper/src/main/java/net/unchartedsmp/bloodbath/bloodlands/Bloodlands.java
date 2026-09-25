package net.unchartedsmp.bloodbath.bloodlands;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.unchartedsmp.bloodbath.BloodbathPlugin;
import net.unchartedsmp.bloodbath.Keys;
import net.unchartedsmp.bloodbath.blood.BloodDrop;
import net.unchartedsmp.bloodbath.blood.Bleeding;
import net.unchartedsmp.bloodbath.config.BloodConfig;
import net.unchartedsmp.bloodbath.config.Settings;
import net.unchartedsmp.bloodbath.fx.BloodFx;
import net.unchartedsmp.bloodbath.hud.Hud;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Color;
import org.bukkit.GameRules;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.block.data.type.Candle;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * The Bloodlands: a world of their own, reached only through Bloodstone portals.
 *
 * <p>The world is made by {@link BloodlandsGenerator}. Its red grass, blood-red water, crimson fog
 * and dark sky come from a biome, {@code bloodbath:bloodlands}, which a Paper plugin can only add
 * through a datapack. So on first start the plugin writes that datapack into the main world's
 * {@code datapacks} folder, and the Bloodlands open after the next restart, once the server has
 * loaded it (the biome is written into every chunk as it generates, so the world is never made
 * without it). The datapack is written for Minecraft 1.21.11 only; on any other version, or with
 * {@code bloodlands.biome-datapack: false}, the Bloodlands open straight away under a vanilla sky.
 *
 * <p>Also here: blood water (any water in the Bloodlands), the Bloodbound (elite mobs that carry
 * Blood Drops) and the ruined shrines whose chests may hold them.
 */
public final class Bloodlands implements Listener {
	public static final Key BIOME = Key.key("bloodbath", "bloodlands");
	private static final String DATAPACK_FOLDER = "bloodbath_bloodlands";
	private static final String[] DATAPACK_FILES = {"pack.mcmeta", "data/bloodbath/worldgen/biome/bloodlands.json"};
	/** The only Minecraft version the biome's datapack is written for. */
	public static final String DATAPACK_VERSION = "1.21.11";

	private final BloodbathPlugin plugin;
	private final Logger log;
	private World world;
	private BloodlandsGenerator generator;
	private String status = "not started";
	/** Players standing in blood water, for the enter/leave messages. */
	private final Set<UUID> wading = new HashSet<>();
	private final File dataFile;
	private final YamlConfiguration data;

	public Bloodlands(BloodbathPlugin plugin) {
		this.plugin = plugin;
		this.log = plugin.getLogger();
		this.dataFile = new File(plugin.getDataFolder(), "bloodlands.yml");
		this.data = YamlConfiguration.loadConfiguration(dataFile);
	}

	// ---- the world ------------------------------------------------------------------------------

	public void enable() {
		try {
			open();
		} catch (RuntimeException | LinkageError e) {
			// Whatever goes wrong here, the rest of Bloodbath keeps working.
			status = "failed to open (" + e.getClass().getSimpleName() + ": " + e.getMessage() + ")";
			world = null;
			log.log(java.util.logging.Level.SEVERE, "The Bloodlands couldn't be opened", e);
		}
	}

	private void open() {
		BloodConfig.Lands config = Settings.get().blood.lands();
		if (!config.enabled()) {
			status = "turned off in config.yml";
			return;
		}
		boolean supported = DATAPACK_VERSION.equals(Bukkit.getMinecraftVersion());
		boolean wantBiome = config.biomeDatapack() && supported;
		boolean installedNow = wantBiome && installDatapack();
		Biome biome = wantBiome ? RegistryAccess.registryAccess().getRegistry(RegistryKey.BIOME).get(BIOME) : null;
		if (wantBiome && biome == null && !new File(Bukkit.getWorldContainer(), config.worldName()).isDirectory()) {
			status = "waiting for one restart (the Bloodlands biome was just installed)";
			log.warning("The Bloodlands need their biome, which Minecraft only loads at startup. "
				+ (installedNow ? "It has been installed" : "It is installed") + " in " + datapackFolder()
				+ ". Restart the server once and the Bloodlands open.");
			return;
		}
		if (config.biomeDatapack() && !supported) {
			log.warning("The Bloodlands biome (red grass, blood water, crimson fog) is only made for Minecraft " + DATAPACK_VERSION
				+ "; this server runs " + Bukkit.getMinecraftVersion() + ". The Bloodlands open with a vanilla sky and grass.");
		}
		boolean custom = biome != null;
		long seed = config.seed() != 0 ? config.seed() : Bukkit.getWorlds().get(0).getSeed() ^ 0x5EEDB10DL;
		Terrain terrain = new Terrain(seed, config.lakeFrequency());
		generator = new BloodlandsGenerator(terrain, custom ? biome : Biome.PLAINS, custom);
		World existing = Bukkit.getWorld(config.worldName());
		if (existing != null) {
			// Already loaded (the plugin was reloaded): it keeps generating as it was made.
			world = existing;
			status = "open";
			return;
		}
		long started = System.currentTimeMillis();
		world = new WorldCreator(config.worldName())
			.environment(World.Environment.NORMAL)
			.seed(seed)
			.generator(generator)
			.createWorld();
		if (world == null) {
			status = "could not be created (see the log)";
			return;
		}
		setUp(world, config);
		status = "open" + (custom ? "" : " (vanilla sky: no Bloodlands biome)");
		log.info("The Bloodlands are open (" + world.getName() + ", " + (System.currentTimeMillis() - started) + " ms).");
	}

	private void setUp(World world, BloodConfig.Lands config) {
		if (config.freezeTime()) {
			world.setGameRule(GameRules.ADVANCE_TIME, false);
			world.setTime(config.time());
		} else {
			world.setGameRule(GameRules.ADVANCE_TIME, true);
		}
		world.setGameRule(GameRules.ADVANCE_WEATHER, false);
		world.setStorm(false);
		world.setThundering(false);
		if (!data.getBoolean("spawn-set", false)) {
			int[] spot = generator.findDryLand(0, 0, 256);
			world.setSpawnLocation(spot[0], spot[1] + 1, spot[2]);
		}
	}

	/** Writes the biome's datapack into the main world if it isn't there (or is out of date). True if it was written now. */
	private boolean installDatapack() {
		File folder = datapackFolder();
		boolean wrote = false;
		try {
			for (String name : DATAPACK_FILES) {
				File target = new File(folder, name);
				byte[] bytes;
				try (InputStream in = plugin.getResource("datapack/bloodlands/" + name)) {
					if (in == null) {
						log.warning("The Bloodlands datapack is missing from the plugin jar: " + name);
						return false;
					}
					bytes = in.readAllBytes();
				}
				if (target.isFile() && java.util.Arrays.equals(Files.readAllBytes(target.toPath()), bytes)) {
					continue;
				}
				Files.createDirectories(target.getParentFile().toPath());
				Files.write(target.toPath(), bytes);
				wrote = true;
			}
		} catch (IOException e) {
			log.warning("Couldn't write the Bloodlands datapack to " + folder + ": " + e.getMessage());
			return false;
		}
		if (wrote) {
			log.info("Installed the Bloodlands biome datapack in " + folder + ".");
		}
		return wrote;
	}

	/** {@code <server>/<main world>/datapacks/bloodbath_bloodlands}, where Minecraft looks for the main world's datapacks. */
	private static File datapackFolder() {
		World main = Bukkit.getWorlds().get(0);
		return new File(new File(new File(Bukkit.getWorldContainer(), main.getName()), "datapacks"), DATAPACK_FOLDER);
	}

	public World world() {
		return world;
	}

	public boolean isOpen() {
		return world != null;
	}

	public boolean isBloodlands(World candidate) {
		return world != null && candidate == world;
	}

	public String status() {
		return status;
	}

	public BloodlandsGenerator generator() {
		return generator;
	}

	/** Where portal travellers arrive. */
	public Location spawn() {
		return world == null ? null : world.getSpawnLocation().toCenterLocation().subtract(0, 0.5, 0);
	}

	/** /bb setspawn */
	public void setSpawn(Location location) {
		world.setSpawnLocation(location);
		data.set("spawn-set", true);
		save();
	}

	public YamlConfiguration data() {
		return data;
	}

	public void save() {
		try {
			data.save(dataFile);
		} catch (IOException e) {
			log.warning("Couldn't save " + dataFile + ": " + e.getMessage());
		}
	}

	// ---- blood water ---------------------------------------------------------------------------

	/** Called every tick. */
	public void tick(long now) {
		if (world == null) {
			return;
		}
		BloodConfig.Water water = Settings.get().blood.water();
		if (!water.enabled()) {
			return;
		}
		boolean hurtTick = now % water.intervalTicks() == 0;
		boolean fxTick = now % 10 == 0;
		if (!hurtTick && !fxTick && now % 40 != 0) {
			return;
		}
		for (Player player : world.getPlayers()) {
			boolean in = player.isInWater() && !player.isDead() && player.getGameMode() != org.bukkit.GameMode.SPECTATOR;
			UUID id = player.getUniqueId();
			if (in && wading.add(id)) {
				Hud.flash(player, Component.text("The blood opens your wounds", NamedTextColor.RED));
				BloodFx.playTo(player, BloodFx.SQUELCH, 0.7F, 0.6F);
				BloodFx.playTo(player, BloodFx.HEARTBEAT, 0.6F, 1.1F);
			} else if (!in && wading.remove(id) && Settings.get().blood.water().bleedingTicks() > 0) {
				// Out of the water: the blood water's own bleed stops with it.
				if (Bleeding.stacks(player) > 0 && Bleeding.remaining(player) <= water.bleedingTicks()) {
					Bleeding.stop(player);
				}
			}
			if (!in) {
				continue;
			}
			if (hurtTick) {
				if (water.bleedingStacks() > 0 && water.bleedingTicks() > 0) {
					Bleeding.apply(player, water.bleedingStacks(), water.bleedingTicks(), null, null);
				}
				if (water.damage() > 0 && player.getGameMode() != org.bukkit.GameMode.CREATIVE) {
					int immunity = player.getNoDamageTicks();
					player.setNoDamageTicks(0);
					player.damage(water.damage(), org.bukkit.damage.DamageSource.builder(org.bukkit.damage.DamageType.MAGIC).build());
					if (!player.isDead()) {
						player.setNoDamageTicks(immunity);
					}
				}
			}
			if (fxTick) {
				Location at = player.getLocation().add(0, 0.6, 0);
				BloodFx.burst(at, BloodFx.BLOOD_FADE, 3, 0.45);
				BloodFx.burst(at, BloodFx.DRIP, 1, 0.4, 0.0);
			}
		}
		if (now % 40 == 0) {
			ambientLakes();
		}
	}

	/** Now and then, a slow bubble of blood breaks the surface of a lake near someone. */
	private void ambientLakes() {
		ThreadLocalRandom random = ThreadLocalRandom.current();
		for (Player player : world.getPlayers()) {
			for (int i = 0; i < 3; i++) {
				int x = player.getLocation().getBlockX() + random.nextInt(-14, 15);
				int z = player.getLocation().getBlockZ() + random.nextInt(-14, 15);
				if (!world.isChunkLoaded(x >> 4, z >> 4)) {
					continue;
				}
				Block top = world.getHighestBlockAt(x, z);
				if (top.getType() == Material.WATER) {
					Location at = top.getLocation().clone().add(0.5, 1.02, 0.5);
					BloodFx.burst(at, BloodFx.MOTE, 2, 0.15, 0.01);
					if (random.nextInt(4) == 0) {
						world.playSound(at, org.bukkit.Sound.BLOCK_BUBBLE_COLUMN_BUBBLE_POP, 0.25F, 0.5F);
					}
				}
			}
		}
	}

	public void forget(UUID player) {
		wading.remove(player);
	}

	// ---- the Bloodbound and their drops ---------------------------------------------------------

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onSpawn(CreatureSpawnEvent event) {
		if (!isBloodlands(event.getEntity().getWorld()) || !(event.getEntity() instanceof Monster monster)
			|| event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.NATURAL) {
			return;
		}
		if (ThreadLocalRandom.current().nextDouble() < Settings.get().blood.lands().eliteChance()) {
			makeBloodbound(monster);
		}
	}

	/** An elite: blood-red armour, more health, and a real chance at a Blood Drop. */
	public static void makeBloodbound(LivingEntity mob) {
		mob.getPersistentDataContainer().set(Keys.BLOODBOUND, PersistentDataType.BYTE, (byte) 1);
		mob.customName(Component.text("Bloodbound", NamedTextColor.DARK_RED).decoration(TextDecoration.ITALIC, false));
		mob.setCustomNameVisible(false);
		AttributeInstance health = mob.getAttribute(Attribute.MAX_HEALTH);
		if (health != null) {
			health.setBaseValue(Settings.get().blood.lands().eliteHealth());
			mob.setHealth(health.getValue());
		}
		EntityEquipment gear = mob.getEquipment();
		if (gear != null) {
			gear.setChestplate(dyed(Material.LEATHER_CHESTPLATE));
			gear.setHelmet(dyed(Material.LEATHER_HELMET));
			gear.setChestplateDropChance(0.0F);
			gear.setHelmetDropChance(0.0F);
		}
	}

	private static ItemStack dyed(Material material) {
		ItemStack armor = new ItemStack(material);
		armor.editMeta(LeatherArmorMeta.class, meta -> meta.setColor(Color.fromRGB(0x5A0610)));
		return armor;
	}

	public static boolean isBloodbound(Entity entity) {
		return entity.getPersistentDataContainer().has(Keys.BLOODBOUND, PersistentDataType.BYTE);
	}

	@EventHandler(priority = EventPriority.HIGH)
	public void onMobDeath(EntityDeathEvent event) {
		LivingEntity mob = event.getEntity();
		if (mob instanceof Player || mob.getKiller() == null || !isBloodlands(mob.getWorld())) {
			return;
		}
		boolean elite = isBloodbound(mob);
		if (!elite && !(mob instanceof Monster)) {
			return;
		}
		BloodConfig.Drops drops = Settings.get().blood.drops();
		double chance = elite ? drops.eliteChance() : drops.mobChance();
		if (ThreadLocalRandom.current().nextDouble() < chance) {
			event.getDrops().add(BloodDrop.create(1));
			BloodFx.burst(BloodFx.chest(mob), BloodFx.BLOOD_FADE, 14, 0.4);
			BloodFx.play(mob.getLocation(), BloodFx.HEARTBEAT, 0.7F, 1.4F);
		}
	}

	/** Picking up a Blood Drop: a heartbeat, a puff of blood and a line saying how many you hold. */
	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onPickup(EntityPickupItemEvent event) {
		ItemStack stack = event.getItem().getItemStack();
		if (!(event.getEntity() instanceof Player player) || !BloodDrop.isDrop(stack)
			|| !Settings.get().blood.drops().pickupEffects()) {
			return;
		}
		int held = stack.getAmount();
		for (ItemStack item : player.getInventory().getContents()) {
			if (BloodDrop.isDrop(item)) {
				held += item.getAmount();
			}
		}
		BloodFx.burst(event.getItem().getLocation().add(0, 0.2, 0), BloodFx.BLOOD_FADE, 8, 0.25);
		BloodFx.playTo(player, BloodFx.HEARTBEAT, 0.8F, 1.3F);
		Hud.flash(player, Component.text("Blood Drop", NamedTextColor.RED)
			.append(Component.text("  ·  " + held + " held", NamedTextColor.DARK_GRAY)));
	}

	// ---- shrines -----------------------------------------------------------------------------------

	/** New Bloodlands chunks may hold a ruined shrine. Built here, where the chest can be filled. */
	@EventHandler(priority = EventPriority.MONITOR)
	public void onChunk(ChunkLoadEvent event) {
		if (!event.isNewChunk() || !isBloodlands(event.getWorld()) || generator == null) {
			return;
		}
		Chunk chunk = event.getChunk();
		double frequency = Settings.get().blood.drops().shrineFrequency();
		Terrain terrain = generator.terrain();
		if (frequency <= 0 || terrain.random(chunk.getX(), chunk.getZ(), 71) >= 0.0035 * frequency) {
			return;
		}
		int cx = (chunk.getX() << 4) + 8;
		int cz = (chunk.getZ() << 4) + 8;
		Terrain.Column c = terrain.column(cx, cz);
		if (c.kind() != Terrain.Kind.LAND) {
			return;
		}
		buildShrine(chunk.getWorld(), cx, c.height(), cz, terrain);
	}

	/** A sunken, broken altar of blackstone and red brick, candles still burning, and a chest. */
	public void buildShrine(World world, int cx, int y, int cz, Terrain terrain) {
		for (int dx = -3; dx <= 3; dx++) {
			for (int dz = -3; dz <= 3; dz++) {
				int x = cx + dx;
				int z = cz + dz;
				boolean edge = Math.abs(dx) == 3 || Math.abs(dz) == 3;
				double r = terrain.random(x, z, 72);
				if (edge && r < 0.35) {
					continue; // crumbled away
				}
				Material floor = r < 0.2 ? Material.CRACKED_POLISHED_BLACKSTONE_BRICKS : r < 0.3 ? Material.RED_NETHER_BRICKS
					: r < 0.36 ? Material.NETHER_WART_BLOCK : Material.POLISHED_BLACKSTONE_BRICKS;
				world.getBlockAt(x, y, z).setType(floor, false);
				for (int up = 1; up <= 4; up++) {
					Block above = world.getBlockAt(x, y + up, z);
					if (!above.getType().isAir() && !above.getType().isSolid()) {
						above.setType(Material.AIR, false); // grass and flowers under the stones
					}
				}
			}
		}
		// Four broken pillars.
		int[][] corners = {{-3, -3}, {3, -3}, {-3, 3}, {3, 3}};
		for (int i = 0; i < corners.length; i++) {
			int height = 1 + (int) (terrain.random(cx + i, cz, 73) * 4);
			for (int up = 1; up <= height; up++) {
				Material m = up == height && height > 2 ? Material.CHISELED_POLISHED_BLACKSTONE : Material.POLISHED_BLACKSTONE_BRICKS;
				world.getBlockAt(cx + corners[i][0], y + up, cz + corners[i][1]).setType(m, false);
			}
		}
		// The altar.
		world.getBlockAt(cx, y + 1, cz).setType(Material.CHISELED_POLISHED_BLACKSTONE, false);
		Block chestBlock = world.getBlockAt(cx, y + 2, cz);
		chestBlock.setType(Material.CHEST, false);
		for (int[] d : new int[][] {{1, 0}, {-1, 0}}) {
			Block candle = world.getBlockAt(cx + d[0], y + 1, cz + d[1]);
			candle.setType(Material.RED_CANDLE, false);
			if (candle.getBlockData() instanceof Candle lit) {
				lit.setCandles(2 + (int) (terrain.random(cx + d[0], cz, 74) * 2));
				lit.setLit(true);
				candle.setBlockData(lit, false);
			}
		}
		if (chestBlock.getState() instanceof Chest chest) {
			fillShrineChest(chest);
		}
	}

	private static void fillShrineChest(Chest chest) {
		ThreadLocalRandom random = ThreadLocalRandom.current();
		BloodConfig.Drops drops = Settings.get().blood.drops();
		List<ItemStack> loot = new java.util.ArrayList<>();
		if (random.nextDouble() < drops.shrineChance()) {
			loot.add(BloodDrop.create(random.nextDouble() < 0.25 ? 2 : 1));
		}
		loot.add(new ItemStack(Material.BONE, 2 + random.nextInt(5)));
		loot.add(new ItemStack(Material.REDSTONE, 3 + random.nextInt(9)));
		if (random.nextBoolean()) {
			loot.add(new ItemStack(Material.GOLD_NUGGET, 4 + random.nextInt(12)));
		}
		if (random.nextInt(3) == 0) {
			loot.add(new ItemStack(Material.IRON_INGOT, 1 + random.nextInt(4)));
		}
		if (random.nextInt(6) == 0) {
			loot.add(new ItemStack(Material.GHAST_TEAR, 1));
		}
		if (random.nextInt(10) == 0) {
			loot.add(new ItemStack(Material.DIAMOND, 1));
		}
		var inventory = chest.getBlockInventory();
		for (ItemStack stack : loot) {
			int slot;
			int guard = 0;
			do {
				slot = random.nextInt(inventory.getSize());
			} while (inventory.getItem(slot) != null && ++guard < 40);
			inventory.setItem(slot, stack);
		}
	}
}
