package net.unchartedsmp.bloodbath.portal;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import net.unchartedsmp.bloodbath.BloodbathPlugin;
import net.unchartedsmp.bloodbath.Keys;
import net.unchartedsmp.bloodbath.ability.ServerClock;
import net.unchartedsmp.bloodbath.ability.TickScheduler;
import net.unchartedsmp.bloodbath.bloodlands.Bloodlands;
import net.unchartedsmp.bloodbath.config.BloodConfig;
import net.unchartedsmp.bloodbath.config.Settings;
import net.unchartedsmp.bloodbath.fx.BloodFx;
import net.unchartedsmp.bloodbath.fx.Shapes;
import net.unchartedsmp.bloodbath.hud.Hud;
import net.unchartedsmp.bloodbath.weapon.Weapons;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Levelled;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.StructureGrowEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

/**
 * Every Bloodlands portal: lighting them, keeping them (and their frames) intact, drawing them,
 * and carrying players through.
 *
 * <p>Portals are saved in {@code plugins/Bloodbath/portals.yml} and indexed by interior block, so
 * the move listener's check is one hash lookup. Their surface is drawn by a handful of item
 * displays (one per tile of up to 4×4 blocks) carrying an animated crimson swirl from the resource
 * pack (players without it see stained glass); the displays are never saved and are respawned with
 * their chunk. The inside is filled with light blocks, so the portal glows and nothing can be built
 * in it.
 *
 * <p>A player standing in a portal for the warm-up time is taken to the Bloodlands (or, from the
 * Bloodlands, back to the portal they came through). One teleport at a time per player, a cooldown
 * after each, and they always land on safe ground, spread apart, never inside a portal: no loops.
 */
public final class Portals implements Listener {
	private static final int TILE = 4;
	private static final int LIGHT_LEVEL = 10;
	private static final Color GLOW = Color.fromRGB(0xE0303C);

	private final BloodbathPlugin plugin;
	private final Logger log;
	private final File file;
	private final Map<UUID, Portal> portals = new HashMap<>();
	/** World -> interior block key -> portal. */
	private final Map<UUID, Map<Long, Portal>> index = new HashMap<>();
	/** Portal -> tile -> the display drawing it (while its chunk is loaded). */
	private final Map<UUID, Map<Integer, ItemDisplay>> displays = new HashMap<>();
	/** Players standing in a portal: which, and since when. */
	private final Map<UUID, Entry> inside = new HashMap<>();
	/** No portal travel until this tick. */
	private final Map<UUID, Long> cooldown = new HashMap<>();
	/** Teleports on their way (waiting for a chunk to load). */
	private final Set<UUID> travelling = new HashSet<>();
	/** Next ambient sound per portal. */
	private final Map<UUID, Long> nextHum = new HashMap<>();
	private Bloodlands lands;

	/** {@code side}: which face of the portal they walked in through (+1 or -1 along its normal). */
	private record Entry(Portal portal, long since, int side) {
	}

	public Portals(BloodbathPlugin plugin) {
		this.plugin = plugin;
		this.log = plugin.getLogger();
		this.file = new File(plugin.getDataFolder(), "portals.yml");
	}

	public void enable(Bloodlands bloodlands) {
		this.lands = bloodlands;
		load();
		for (World world : Bukkit.getWorlds()) {
			for (Chunk chunk : world.getLoadedChunks()) {
				spawnDisplays(chunk);
			}
		}
		if (lands.isOpen() && !lands.data().getBoolean("return-portal", false)) {
			try {
				buildReturnPortal();
			} catch (RuntimeException e) {
				log.log(java.util.logging.Level.WARNING, "Couldn't build the Bloodlands' return portal (build one with /bb portal build)", e);
			}
		}
	}

	public void disable() {
		for (Map<Integer, ItemDisplay> tiles : displays.values()) {
			for (ItemDisplay display : tiles.values()) {
				display.remove();
			}
		}
		displays.clear();
		inside.clear();
		travelling.clear();
		Frames.clearCache();
	}

	// ---- storage -------------------------------------------------------------------------------

	private void load() {
		portals.clear();
		index.clear();
		YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
		ConfigurationSection section = yaml.getConfigurationSection("portals");
		if (section == null) {
			return;
		}
		for (String key : section.getKeys(false)) {
			ConfigurationSection p = section.getConfigurationSection(key);
			try {
				Portal portal = new Portal(UUID.fromString(key), UUID.fromString(p.getString("world")),
					Portal.Axis.valueOf(p.getString("axis", "X")), p.getInt("x"), p.getInt("y"), p.getInt("z"),
					p.getInt("width"), p.getInt("height"));
				add(portal);
			} catch (RuntimeException e) {
				log.warning("portals.yml: skipped a broken portal entry '" + key + "'.");
			}
		}
	}

	private void save() {
		YamlConfiguration yaml = new YamlConfiguration();
		for (Portal portal : portals.values()) {
			String path = "portals." + portal.id();
			yaml.set(path + ".world", portal.world().toString());
			yaml.set(path + ".axis", portal.axis().name());
			yaml.set(path + ".x", portal.x());
			yaml.set(path + ".y", portal.y());
			yaml.set(path + ".z", portal.z());
			yaml.set(path + ".width", portal.width());
			yaml.set(path + ".height", portal.height());
		}
		try {
			yaml.save(file);
		} catch (IOException e) {
			log.warning("Couldn't save " + file + ": " + e.getMessage());
		}
	}

	private void add(Portal portal) {
		portals.put(portal.id(), portal);
		Map<Long, Portal> blocks = index.computeIfAbsent(portal.world(), w -> new HashMap<>());
		for (int j = 0; j < portal.height(); j++) {
			for (int i = 0; i < portal.width(); i++) {
				int bx = portal.axis() == Portal.Axis.X ? portal.x() + i : portal.x();
				int bz = portal.axis() == Portal.Axis.X ? portal.z() : portal.z() + i;
				blocks.put(key(bx, portal.y() + j, bz), portal);
			}
		}
	}

	/** A block position as one number (x and z to ±67 million, any build height). */
	static long key(int x, int y, int z) {
		return (x & 0x7FFFFFFL) | (z & 0x7FFFFFFL) << 27 | ((y + 1024L) & 0x3FFL) << 54;
	}

	public Collection<Portal> all() {
		return portals.values();
	}

	/** The portal whose inside holds this block, or null. */
	public Portal at(Block block) {
		Map<Long, Portal> blocks = index.get(block.getWorld().getUID());
		return blocks == null ? null : blocks.get(key(block.getX(), block.getY(), block.getZ()));
	}

	private Portal at(World world, int x, int y, int z) {
		Map<Long, Portal> blocks = index.get(world.getUID());
		return blocks == null ? null : blocks.get(key(x, y, z));
	}

	/** The active portal this frame block belongs to, or null. */
	public Portal framing(Block block) {
		for (BlockFace face : new BlockFace[] {BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST}) {
			Portal portal = at(block.getRelative(face));
			if (portal != null) {
				return portal;
			}
		}
		// A corner touches the inside only diagonally.
		for (int dx = -1; dx <= 1; dx++) {
			for (int dy = -1; dy <= 1; dy += 2) {
				for (int dz = -1; dz <= 1; dz++) {
					Portal portal = at(block.getRelative(dx, dy, dz));
					if (portal != null && portal.frame(block.getWorld()).contains(block)) {
						return portal;
					}
				}
			}
		}
		return null;
	}

	// ---- lighting and putting out ------------------------------------------------------------------

	/** Tries to light the portal around this frame block. The player (if any) is told how it went. */
	public PortalScanner.Result ignite(Block frame, Player player) {
		PortalScanner.Result result = PortalScanner.scan(frame, Settings.get().blood.portal(), block -> at(block) != null);
		if (!result.valid()) {
			if (player != null) {
				player.sendMessage(Settings.get().prefix.append(Component.text(result.problem(), NamedTextColor.RED)));
				BloodFx.playTo(player, "block.fire.extinguish", 0.6F, 0.7F);
				if (result.where() != null) {
					mark(player, result.where());
				}
			}
			return result;
		}
		activate(result.portal(), frame.getWorld());
		if (player != null) {
			boolean home = lands.isBloodlands(frame.getWorld());
			player.sendMessage(Settings.get().prefix.append(Component.text("The portal opens (" + result.portal().width() + "×"
				+ result.portal().height() + "). ", NamedTextColor.RED))
				.append(Component.text(home ? "It leads back to the world you came from." : lands.isOpen()
					? "It leads to the Bloodlands." : "The Bloodlands aren't open yet: " + lands.status() + ".", NamedTextColor.GRAY)));
		}
		return result;
	}

	public void activate(Portal portal, World world) {
		add(portal);
		save();
		BlockData light = Material.LIGHT.createBlockData();
		if (light instanceof Levelled level) {
			level.setLevel(LIGHT_LEVEL);
		}
		for (Block block : portal.interior(world)) {
			block.setBlockData(light, false);
		}
		for (Chunk chunk : chunksOf(portal, world)) {
			if (chunk.isLoaded()) {
				spawnDisplays(chunk);
			}
		}
		// It opens with a roar and a rush of blood out of the middle.
		Location center = portal.center(world);
		BloodFx.play(center, BloodFx.RIFT_OPEN, 1.2F, 0.5F);
		BloodFx.play(center, BloodFx.ROAR, 0.6F, 0.6F);
		BloodFx.burst(center, BloodFx.BLOOD_LARGE, 12 + portal.area() * 2, Math.max(portal.width(), portal.height()) * 0.3);
		BloodFx.burst(center, BloodFx.RING, 6, 0.4);
	}

	/** Puts a portal out: the inside empties, its surface goes. Its frame stays. */
	public void deactivate(Portal portal) {
		portals.remove(portal.id());
		Map<Long, Portal> blocks = index.get(portal.world());
		if (blocks != null) {
			blocks.values().removeIf(p -> p.id().equals(portal.id()));
		}
		Map<Integer, ItemDisplay> tiles = displays.remove(portal.id());
		if (tiles != null) {
			tiles.values().forEach(ItemDisplay::remove);
		}
		inside.values().removeIf(entry -> entry.portal().id().equals(portal.id()));
		save();
		World world = Bukkit.getWorld(portal.world());
		if (world != null) {
			for (Block block : portal.interior(world)) {
				if (block.getType() == Material.LIGHT) {
					block.setType(Material.AIR, false);
				}
			}
			BloodFx.play(portal.center(world), BloodFx.NULLIFY, 1.0F, 0.6F);
			BloodFx.burst(portal.center(world), BloodFx.CLOT, 20, 1.0);
		}
	}

	/** Removes a portal and its frame (an admin's /bb portal remove). Frame items drop where it stood. */
	public void destroy(Portal portal, boolean dropFrames) {
		deactivate(portal);
		World world = Bukkit.getWorld(portal.world());
		if (world == null) {
			return;
		}
		int dropped = 0;
		for (Block block : portal.frame(world)) {
			if (Frames.isFrame(block)) {
				Frames.remove(block);
				block.setType(Material.AIR);
				dropped++;
			}
		}
		if (dropFrames && dropped > 0) {
			world.dropItemNaturally(portal.center(world), Frames.item(dropped));
		}
	}

	/** Flashes a block for one player, to show them what's wrong with their frame. */
	private static void mark(Player player, Block block) {
		Location at = block.getLocation().clone().add(0.5, 0.5, 0.5);
		Particle.DustOptions red = new Particle.DustOptions(Color.fromRGB(0xFF2A35), 1.4F);
		TickScheduler.repeat(0, 5, 12, i -> {
			if (!player.isOnline() || player.getWorld() != block.getWorld()) {
				return false;
			}
			for (double dx = -0.5; dx <= 0.5; dx += 0.5) {
				for (double dy = -0.5; dy <= 0.5; dy += 0.5) {
					player.spawnParticle(Particle.DUST, at.clone().add(dx, dy, 0.52), 1, 0, 0, 0, 0, red);
					player.spawnParticle(Particle.DUST, at.clone().add(dx, dy, -0.52), 1, 0, 0, 0, 0, red);
					player.spawnParticle(Particle.DUST, at.clone().add(0.52, dy, dx), 1, 0, 0, 0, 0, red);
					player.spawnParticle(Particle.DUST, at.clone().add(-0.52, dy, dx), 1, 0, 0, 0, 0, red);
				}
			}
			return true;
		});
	}

	// ---- the surface ---------------------------------------------------------------------------------

	private static List<Chunk> chunksOf(Portal portal, World world) {
		List<Chunk> chunks = new ArrayList<>();
		int x2 = portal.axis() == Portal.Axis.X ? portal.x() + portal.width() - 1 : portal.x();
		int z2 = portal.axis() == Portal.Axis.Z ? portal.z() + portal.width() - 1 : portal.z();
		for (int cx = portal.x() >> 4; cx <= x2 >> 4; cx++) {
			for (int cz = portal.z() >> 4; cz <= z2 >> 4; cz++) {
				if (world.isChunkLoaded(cx, cz)) {
					chunks.add(world.getChunkAt(cx, cz));
				}
			}
		}
		return chunks;
	}

	/** Spawns the missing surface tiles anchored in this chunk. */
	private void spawnDisplays(Chunk chunk) {
		World world = chunk.getWorld();
		for (Portal portal : portals.values()) {
			if (!portal.world().equals(world.getUID())) {
				continue;
			}
			int tilesU = (portal.width() + TILE - 1) / TILE;
			int tilesV = (portal.height() + TILE - 1) / TILE;
			for (int tu = 0; tu < tilesU; tu++) {
				for (int tv = 0; tv < tilesV; tv++) {
					int u0 = tu * TILE;
					int v0 = tv * TILE;
					int bx = portal.axis() == Portal.Axis.X ? portal.x() + u0 : portal.x();
					int bz = portal.axis() == Portal.Axis.X ? portal.z() : portal.z() + u0;
					if (bx >> 4 != chunk.getX() || bz >> 4 != chunk.getZ()) {
						continue;
					}
					int tile = tu * 1000 + tv;
					Map<Integer, ItemDisplay> tiles = displays.computeIfAbsent(portal.id(), id -> new HashMap<>());
					ItemDisplay existing = tiles.get(tile);
					if (existing != null && existing.isValid()) {
						continue;
					}
					int w = Math.min(TILE, portal.width() - u0);
					int h = Math.min(TILE, portal.height() - v0);
					tiles.put(tile, spawnTile(world, portal, u0, v0, w, h));
				}
			}
		}
	}

	private static ItemDisplay spawnTile(World world, Portal portal, int u0, int v0, int w, int h) {
		double cx = portal.axis() == Portal.Axis.X ? portal.x() + u0 + w / 2.0 : portal.x() + 0.5;
		double cz = portal.axis() == Portal.Axis.X ? portal.z() + 0.5 : portal.z() + u0 + w / 2.0;
		Location at = new Location(world, cx, portal.y() + v0 + h / 2.0, cz);
		return world.spawn(at, ItemDisplay.class, display -> {
			display.setPersistent(false);
			display.setItemStack(surfaceItem());
			display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
			display.setBrightness(new Display.Brightness(15, 15));
			display.setViewRange(1.5F);
			display.setShadowRadius(0.0F);
			AxisAngle4f turn = portal.axis() == Portal.Axis.X ? new AxisAngle4f() : new AxisAngle4f((float) (Math.PI / 2), 0, 1, 0);
			display.setTransformation(new Transformation(new Vector3f(), turn, new Vector3f(w, h, 1.0F), new AxisAngle4f()));
			display.getPersistentDataContainer().set(Keys.FIXTURE, PersistentDataType.STRING, "portal:" + portal.id());
		});
	}

	/** The portal's surface: an animated crimson swirl with the pack, red stained glass without. */
	static ItemStack surfaceItem() {
		ItemStack item = new ItemStack(Material.RED_STAINED_GLASS_PANE);
		item.editMeta(meta -> {
			CustomModelDataComponent model = meta.getCustomModelDataComponent();
			model.setStrings(List.of(Weapons.MODEL_PREFIX + "blood_portal"));
			meta.setCustomModelDataComponent(model);
		});
		return item;
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onChunkLoad(ChunkLoadEvent event) {
		if (!portals.isEmpty()) {
			spawnDisplays(event.getChunk());
		}
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onChunkUnload(ChunkUnloadEvent event) {
		Frames.unload(event.getChunk());
	}

	@EventHandler
	public void onWorldLoad(WorldLoadEvent event) {
		for (Chunk chunk : event.getWorld().getLoadedChunks()) {
			spawnDisplays(chunk);
		}
	}

	@EventHandler
	public void onWorldUnload(WorldUnloadEvent event) {
		Frames.unload(event.getWorld());
	}

	// ---- ambience ---------------------------------------------------------------------------------

	/** Called every tick. */
	public void tick(long now) {
		if (!inside.isEmpty()) {
			warmUp(now);
		}
		if (now % 10 != 0 || portals.isEmpty()) {
			return;
		}
		ThreadLocalRandom random = ThreadLocalRandom.current();
		for (Portal portal : portals.values()) {
			World world = Bukkit.getWorld(portal.world());
			if (world == null) {
				continue;
			}
			Location center = portal.center(world);
			if (!world.isChunkLoaded(center.getBlockX() >> 4, center.getBlockZ() >> 4) || !anyoneNear(world, center, 40)) {
				continue;
			}
			// A few embers drifting off the surface: never more than four at a time, whatever its size.
			int count = Math.min(4, 1 + portal.area() / 16);
			for (int i = 0; i < count; i++) {
				Location at = randomPoint(portal, world, random);
				BloodFx.burst(at, random.nextInt(3) == 0 ? BloodFx.EMBER : BloodFx.MOTE, 1, 0.05, 0.02);
			}
			if (random.nextInt(5) == 0) {
				BloodFx.burst(randomPoint(portal, world, random), BloodFx.DRIP, 1, 0.1, 0.0);
			}
			Long hum = nextHum.get(portal.id());
			if (hum == null || now >= hum) {
				world.playSound(center, "block.respawn_anchor.ambient", org.bukkit.SoundCategory.BLOCKS, 0.45F, 0.55F);
				nextHum.put(portal.id(), now + 60 + random.nextInt(60));
			}
		}
	}

	private static Location randomPoint(Portal portal, World world, ThreadLocalRandom random) {
		double u = random.nextDouble() * portal.width();
		double v = random.nextDouble() * portal.height();
		double off = (random.nextBoolean() ? 1 : -1) * 0.55;
		return portal.axis() == Portal.Axis.X
			? new Location(world, portal.x() + u, portal.y() + v, portal.z() + 0.5 + off)
			: new Location(world, portal.x() + 0.5 + off, portal.y() + v, portal.z() + u);
	}

	private static boolean anyoneNear(World world, Location at, double range) {
		double sq = range * range;
		for (Player player : world.getPlayers()) {
			if (player.getLocation().distanceSquared(at) <= sq) {
				return true;
			}
		}
		return false;
	}

	// ---- travel ----------------------------------------------------------------------------------

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onMove(PlayerMoveEvent event) {
		if (portals.isEmpty() || !event.hasChangedBlock()) {
			return;
		}
		check(event.getPlayer(), event.getFrom(), event.getTo());
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onTeleported(PlayerTeleportEvent event) {
		if (!portals.isEmpty()) {
			check(event.getPlayer(), event.getFrom(), event.getTo());
		}
	}

	private void check(Player player, Location from, Location to) {
		World world = to.getWorld();
		Portal portal = at(world, to.getBlockX(), to.getBlockY(), to.getBlockZ());
		if (portal == null) {
			portal = at(world, to.getBlockX(), (int) Math.floor(to.getY() + 1.2), to.getBlockZ());
		}
		UUID id = player.getUniqueId();
		if (portal == null) {
			inside.remove(id);
			return;
		}
		Entry entry = inside.get(id);
		if (entry == null || !entry.portal().id().equals(portal.id())) {
			int side = from != null && from.getWorld() == world ? portal.sideOf(from) : 1;
			inside.put(id, new Entry(portal, ServerClock.now(), side));
			if (!onCooldown(id)) {
				BloodFx.play(player, BloodFx.HEARTBEAT, 0.9F, 0.8F);
				BloodFx.play(player, "block.respawn_anchor.charge", 0.5F, 0.6F);
			}
		}
	}

	private boolean onCooldown(UUID id) {
		Long until = cooldown.get(id);
		return until != null && ServerClock.now() < until;
	}

	private void warmUp(long now) {
		int warmup = Settings.get().blood.portal().warmupTicks();
		Iterator<Map.Entry<UUID, Entry>> it = inside.entrySet().iterator();
		List<Player> ready = new ArrayList<>();
		while (it.hasNext()) {
			Map.Entry<UUID, Entry> e = it.next();
			Player player = Bukkit.getPlayer(e.getKey());
			if (player == null || !portals.containsKey(e.getValue().portal().id())) {
				it.remove();
				continue;
			}
			if (travelling.contains(e.getKey()) || onCooldown(e.getKey())) {
				continue;
			}
			long held = now - e.getValue().since();
			if (held % 2 == 0) {
				// Blood spirals up around them, tighter and faster as the portal takes hold.
				double pull = Math.min(1.0, held / (double) Math.max(1, warmup));
				Shapes.spiral(player.getLocation(), BloodFx.BLOOD_FADE, 0.9 - 0.4 * pull, 2.1, 1.0 + pull, 10, held * 0.5);
				if (held % 6 == 0) {
					Shapes.converge(BloodFx.chest(player), 1.6, 3, 6);
				}
			}
			if (held >= warmup) {
				ready.add(player);
			}
		}
		for (Player player : ready) {
			Entry entry = inside.remove(player.getUniqueId());
			if (entry != null) {
				travel(player, entry.portal(), entry.side());
			}
		}
	}

	/** Takes a player through a portal: to the Bloodlands, or out of them back where they came from. */
	public void travel(Player player, Portal portal, int side) {
		UUID id = player.getUniqueId();
		if (!travelling.add(id)) {
			return; // already on their way: a second trigger does nothing
		}
		cooldown.put(id, ServerClock.now() + Settings.get().blood.portal().cooldownTicks());
		World from = player.getWorld();
		CompletableFuture<Location> target;
		if (lands.isBloodlands(from)) {
			target = returnPoint(player);
		} else {
			if (!lands.isOpen()) {
				travelling.remove(id);
				player.sendMessage(Settings.get().prefix.append(Component.text("The portal stirs but won't open: the Bloodlands aren't open ("
					+ lands.status() + ").", NamedTextColor.RED)));
				pushOut(player, portal);
				return;
			}
			remember(player, portal, side);
			target = arrival(lands.world(), lands.spawn(), Settings.get().blood.portal().arrivalRadius());
		}
		BloodFx.play(player.getLocation(), BloodFx.RIFT_STEP, 1.0F, 0.6F);
		BloodFx.burst(BloodFx.chest(player), BloodFx.BLOOD_LARGE, 18, 0.5);
		target.whenComplete((location, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
			if (!player.isOnline()) {
				travelling.remove(id);
				return;
			}
			if (error != null || location == null) {
				travelling.remove(id);
				player.sendMessage(Settings.get().prefix.append(Component.text("The portal couldn't find you a safe place to land. Try again.", NamedTextColor.RED)));
				pushOut(player, portal);
				return;
			}
			boolean toLands = lands.isBloodlands(location.getWorld());
			player.teleportAsync(location, PlayerTeleportEvent.TeleportCause.PLUGIN).whenComplete((ok, failure) ->
				Bukkit.getScheduler().runTask(plugin, () -> {
					travelling.remove(id);
					inside.remove(id);
					cooldown.put(id, ServerClock.now() + Settings.get().blood.portal().cooldownTicks());
					if (failure != null || !Boolean.TRUE.equals(ok) || !player.isOnline()) {
						return;
					}
					arrived(player, toLands);
				}));
		}));
	}

	private void arrived(Player player, boolean toLands) {
		if (!toLands) {
			player.getPersistentDataContainer().remove(Keys.RETURN); // home: the way back is used up
		} else {
			player.showTitle(Title.title(Component.text("The Bloodlands", NamedTextColor.DARK_RED),
				Component.text("Find blood. Don't drink the water.", NamedTextColor.GRAY),
				Title.Times.times(Duration.ofMillis(400), Duration.ofMillis(2600), Duration.ofMillis(900))));
		}
		BloodFx.playTo(player, BloodFx.RIFT_STEP, 0.9F, 0.5F);
		BloodFx.playTo(player, BloodFx.HEARTBEAT, 1.0F, 0.7F);
		BloodFx.burst(player.getLocation().add(0, 1, 0), BloodFx.BLOOD_FADE, 20, 0.6);
	}

	/** Remembers the portal a player took into the Bloodlands (on the side they walked in from). */
	private void remember(Player player, Portal portal, int side) {
		World world = player.getWorld();
		Location exit = portal.exit(world, side);
		String value = world.getUID() + ";" + exit.getX() + ";" + exit.getY() + ";" + exit.getZ() + ";" + exit.getYaw();
		player.getPersistentDataContainer().set(Keys.RETURN, PersistentDataType.STRING, value);
	}

	/** Where a player leaving the Bloodlands goes: the portal they came through, or the main world's spawn. */
	private CompletableFuture<Location> returnPoint(Player player) {
		String stored = player.getPersistentDataContainer().get(Keys.RETURN, PersistentDataType.STRING);
		Location to = null;
		if (stored != null) {
			String[] parts = stored.split(";");
			try {
				World world = Bukkit.getWorld(UUID.fromString(parts[0]));
				if (world != null) {
					to = new Location(world, Double.parseDouble(parts[1]), Double.parseDouble(parts[2]), Double.parseDouble(parts[3]),
						Float.parseFloat(parts[4]), 0.0F);
				}
			} catch (RuntimeException ignored) {
				// broken entry: fall back to spawn
			}
		}
		if (to == null) {
			World main = Bukkit.getWorlds().get(0);
			Location bed = player.getRespawnLocation();
			to = bed != null && !lands.isBloodlands(bed.getWorld()) ? bed : main.getSpawnLocation();
		}
		return arrival(to.getWorld(), to, 1);
	}

	/**
	 * A safe spot near {@code around}: solid ground (not liquid), two open blocks above, not inside a
	 * portal and not on top of another player. Chunks are loaded without stalling the server.
	 */
	public CompletableFuture<Location> arrival(World world, Location around, int spread) {
		CompletableFuture<Location> done = new CompletableFuture<>();
		tryArrival(world, around, spread, 0, done);
		return done;
	}

	private void tryArrival(World world, Location around, int spread, int attempt, CompletableFuture<Location> done) {
		ThreadLocalRandom random = ThreadLocalRandom.current();
		int radius = attempt < 8 ? spread : spread + attempt;
		int x = around.getBlockX() + (radius <= 0 ? 0 : random.nextInt(-radius, radius + 1));
		int z = around.getBlockZ() + (radius <= 0 ? 0 : random.nextInt(-radius, radius + 1));
		if (attempt == 0) {
			x = around.getBlockX() + (spread <= 0 ? 0 : random.nextInt(-spread, spread + 1));
			z = around.getBlockZ() + (spread <= 0 ? 0 : random.nextInt(-spread, spread + 1));
		}
		final int fx = x;
		final int fz = z;
		chunkAt(world, fx >> 4, fz >> 4).whenComplete((chunk, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
			if (error != null) {
				done.completeExceptionally(error);
				return;
			}
			Location spot = safeSpot(world, fx, around.getBlockY(), fz);
			if (spot != null) {
				spot.setYaw(around.getYaw());
				done.complete(spot);
			} else if (attempt < 20) {
				tryArrival(world, around, spread, attempt + 1, done);
			} else {
				// Last resort: stand on the highest block at the spot itself.
				Block top = world.getHighestBlockAt(around.getBlockX(), around.getBlockZ(), HeightMap.MOTION_BLOCKING_NO_LEAVES);
				done.complete(top.getLocation().clone().add(0.5, 1.0, 0.5));
			}
		}));
	}

	/** Loads a chunk without stalling the server (or, where that isn't available, right away). */
	private static CompletableFuture<Chunk> chunkAt(World world, int cx, int cz) {
		try {
			return world.getChunkAtAsync(cx, cz);
		} catch (RuntimeException e) {
			return CompletableFuture.completedFuture(world.getChunkAt(cx, cz));
		}
	}

	/** Safe standing spot in this column, as close as possible to {@code nearY}, or null. */
	private Location safeSpot(World world, int x, int nearY, int z) {
		int top = world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
		int[] candidates = nearY >= world.getMinHeight() && nearY <= top ? new int[] {nearY - 1, nearY, nearY - 2, top} : new int[] {top};
		for (int groundY : candidates) {
			Block ground = world.getBlockAt(x, groundY, z);
			Block feet = ground.getRelative(BlockFace.UP);
			Block head = feet.getRelative(BlockFace.UP);
			if (!ground.getType().isSolid() || ground.isLiquid() || ground.getType() == Material.MAGMA_BLOCK
				|| ground.getType() == Material.CAMPFIRE || ground.getType() == Material.CACTUS) {
				continue;
			}
			if (feet.getType().isSolid() || feet.isLiquid() || head.getType().isSolid() || head.isLiquid()) {
				continue;
			}
			if (at(feet) != null || at(head) != null || feet.getType() == Material.FIRE || feet.getType() == Material.LAVA) {
				continue;
			}
			Location spot = feet.getLocation().clone().add(0.5, 0.0, 0.5);
			boolean crowded = false;
			for (Player other : world.getPlayers()) {
				if (other.getLocation().distanceSquared(spot) < 1.0) {
					crowded = true;
					break;
				}
			}
			if (!crowded) {
				return spot;
			}
		}
		return null;
	}

	/** Out of the portal, a step back the way they came. */
	private static void pushOut(Player player, Portal portal) {
		int side = portal.sideOf(player.getLocation());
		player.setVelocity(portal.normal().multiply(0.45 * side).setY(0.2));
	}

	@EventHandler
	public void onQuit(PlayerQuitEvent event) {
		UUID id = event.getPlayer().getUniqueId();
		inside.remove(id);
	}

	public void prune() {
		long now = ServerClock.now();
		cooldown.values().removeIf(until -> until < now);
	}

	// ---- building: frame items, lighting, admin portals ---------------------------------------

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onPlace(BlockPlaceEvent event) {
		if (Frames.isItem(event.getItemInHand())) {
			Frames.add(event.getBlockPlaced());
		}
	}

	/** Flint and steel (or a fire charge) on a frame lights the portal instead of starting a fire. */
	@EventHandler(priority = EventPriority.HIGH)
	public void onIgnite(PlayerInteractEvent event) {
		if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null || event.getHand() != EquipmentSlot.HAND) {
			return;
		}
		ItemStack hand = event.getItem();
		if (hand == null || hand.getType() != Material.FLINT_AND_STEEL && hand.getType() != Material.FIRE_CHARGE) {
			return;
		}
		Block clicked = event.getClickedBlock();
		if (!Frames.isFrame(clicked) || event.useInteractedBlock() == org.bukkit.event.Event.Result.DENY) {
			return;
		}
		event.setCancelled(true);
		if (framing(clicked) != null) {
			return; // already lit
		}
		PortalScanner.Result result = ignite(clicked, event.getPlayer());
		if (result.valid() && event.getPlayer().getGameMode() != GameMode.CREATIVE) {
			if (hand.getType() == Material.FIRE_CHARGE) {
				hand.subtract();
			} else {
				event.getPlayer().damageItemStack(EquipmentSlot.HAND, 1);
			}
		}
		BloodFx.play(clicked.getLocation(), "item.flintandsteel.use", 1.0F, 0.8F);
	}

	/**
	 * An admin portal: builds a ring of frames of the given inside size in front of the player,
	 * facing them, and lights it. Null if it couldn't.
	 */
	public Portal build(Player player, int width, int height) {
		Location eye = player.getLocation();
		BlockFace facing = player.getFacing();
		Portal.Axis axis = facing == BlockFace.NORTH || facing == BlockFace.SOUTH ? Portal.Axis.X : Portal.Axis.Z;
		Block base = eye.getBlock().getRelative(facing, 3);
		int x = axis == Portal.Axis.X ? base.getX() - width / 2 : base.getX();
		int z = axis == Portal.Axis.Z ? base.getZ() - width / 2 : base.getZ();
		int y = base.getY();
		return buildAt(player.getWorld(), axis, x, y, z, width, height);
	}

	Portal buildAt(World world, Portal.Axis axis, int x, int y, int z, int width, int height) {
		Portal outline = new Portal(UUID.randomUUID(), world.getUID(), axis, x, y, z, width, height);
		for (Block block : outline.interior(world)) {
			if (at(block) != null) {
				return null;
			}
		}
		for (Block block : outline.frame(world)) {
			block.setType(Frames.BLOCK);
			Frames.add(block);
		}
		for (Block block : outline.interior(world)) {
			block.setType(Material.AIR);
		}
		Block anyFrame = outline.frame(world).get(0).getRelative(0, 1, 0);
		PortalScanner.Result result = PortalScanner.scan(anyFrame, Settings.get().blood.portal(), b -> at(b) != null);
		if (!result.valid()) {
			// A size outside the configured limits: light it anyway, an admin asked for it.
			activate(outline, world);
			return outline;
		}
		activate(result.portal(), world);
		return result.portal();
	}

	/** The way home, standing a few steps from where travellers arrive in the Bloodlands. */
	private void buildReturnPortal() {
		World world = lands.world();
		Location spawn = lands.spawn();
		chunkAt(world, spawn.getBlockX() >> 4, spawn.getBlockZ() >> 4).thenAccept(chunk -> Bukkit.getScheduler().runTask(plugin, () -> {
			int x = spawn.getBlockX() - 1;
			int z = spawn.getBlockZ() - 7;
			int ground = world.getHighestBlockYAt(x + 1, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
			// A dark stone footing, so the frame stands on something even on a slope.
			for (int dx = -2; dx <= 4; dx++) {
				for (int dz = -2; dz <= 2; dz++) {
					world.getBlockAt(x + dx, ground, z + dz).setType(dx == -2 || dx == 4 || Math.abs(dz) == 2
						? Material.POLISHED_BLACKSTONE_BRICKS : Material.POLISHED_BLACKSTONE);
					for (int up = 1; up <= 6; up++) {
						Block air = world.getBlockAt(x + dx, ground + up, z + dz);
						if (!air.getType().isAir()) {
							air.setType(Material.AIR, false);
						}
					}
				}
			}
			Portal built = buildAt(world, Portal.Axis.X, x, ground + 2, z, 3, 4);
			if (built != null) {
				lands.data().set("return-portal", true);
				lands.save();
				log.info("Built the Bloodlands' return portal at " + built.describe() + ".");
			}
		}));
	}

	// ---- nothing breaks a frame ----------------------------------------------------------------------

	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onBreak(BlockBreakEvent event) {
		Block block = event.getBlock();
		if (!Frames.isFrame(block)) {
			return;
		}
		Player player = event.getPlayer();
		Portal portal = framing(block);
		if (player.getGameMode() == GameMode.CREATIVE && player.isSneaking() && player.hasPermission("bloodbath.admin")) {
			// An admin taking it apart on purpose.
			Frames.remove(block);
			if (portal != null) {
				deactivate(portal);
			}
			return;
		}
		event.setCancelled(true);
		BloodConfig.Portal config = Settings.get().blood.portal();
		if (portal == null && config.reclaimInactiveFrames() && player.isSneaking()) {
			// An unlit frame block can be taken back.
			Frames.remove(block);
			block.setType(Material.AIR);
			if (player.getGameMode() != GameMode.CREATIVE) {
				block.getWorld().dropItemNaturally(block.getLocation().clone().add(0.5, 0.5, 0.5), Frames.item(1));
			}
			BloodFx.play(block.getLocation(), "block.deepslate.break", 1.0F, 0.7F);
			return;
		}
		Hud.flash(player, Component.text(portal != null ? "A lit Bloodstone Frame can't be broken"
			: "Sneak to take back an unlit Bloodstone Frame", NamedTextColor.RED));
	}

	/** No cracks spreading on a frame being mined: it isn't going anywhere. */
	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onDamage(BlockDamageEvent event) {
		if (Frames.isFrame(event.getBlock()) && !(event.getPlayer().isSneaking() && framing(event.getBlock()) == null)) {
			event.setCancelled(true);
		}
	}

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onEntityExplode(EntityExplodeEvent event) {
		event.blockList().removeIf(this::guarded);
	}

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onBlockExplode(BlockExplodeEvent event) {
		event.blockList().removeIf(this::guarded);
	}

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onPistonExtend(BlockPistonExtendEvent event) {
		for (Block moved : event.getBlocks()) {
			if (guarded(moved) || guarded(moved.getRelative(event.getDirection()))) {
				event.setCancelled(true);
				return;
			}
		}
		if (guarded(event.getBlock().getRelative(event.getDirection()))) {
			event.setCancelled(true);
		}
	}

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onPistonRetract(BlockPistonRetractEvent event) {
		for (Block moved : event.getBlocks()) {
			if (guarded(moved) || guarded(moved.getRelative(event.getDirection()))) {
				event.setCancelled(true);
				return;
			}
		}
	}

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onEntityChange(EntityChangeBlockEvent event) {
		if (guarded(event.getBlock())) {
			event.setCancelled(true);
		}
	}

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onBurn(BlockBurnEvent event) {
		if (guarded(event.getBlock())) {
			event.setCancelled(true);
		}
	}

	/** Nothing flows into, grows into or is built in a lit portal. */
	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onFlow(BlockFromToEvent event) {
		if (at(event.getToBlock()) != null) {
			event.setCancelled(true);
		}
	}

	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onBuildInside(BlockPlaceEvent event) {
		if (at(event.getBlockPlaced()) != null) {
			event.setCancelled(true);
		}
	}

	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onBucket(PlayerBucketEmptyEvent event) {
		if (at(event.getBlock()) != null || at(event.getBlockClicked()) != null) {
			event.setCancelled(true);
		}
	}

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onGrow(StructureGrowEvent event) {
		event.getBlocks().removeIf(state -> guarded(state.getBlock()));
	}

	/** A frame block or a portal's inside. */
	private boolean guarded(Block block) {
		return block.getType() == Frames.BLOCK && Frames.isFrame(block) || at(block) != null;
	}
}
