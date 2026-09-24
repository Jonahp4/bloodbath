package net.unchartedsmp.bloodbath.fx;

import java.util.concurrent.ThreadLocalRandom;
import net.unchartedsmp.bloodbath.config.Settings;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

/**
 * The shared blood palette: every weapon draws its particles and sounds from here so the whole
 * set reads as one theme. Sounds are played by vanilla id, so nothing breaks if a constant moves.
 *
 * <p>Particles go through {@link Particles}: only to players within {@code effects.view-distance},
 * with fewer particles for distant viewers, and every count scaled by
 * {@code effects.particle-multiplier}. Cosmetics that hang around a player's own hands (the
 * held-weapon drips, the bow's draw) go to everyone else only: in first person they'd be right
 * in front of the camera.
 */
public final class BloodFx {
	/**
	 * A particle type plus the data it needs (dust colour, block state, ...), optionally with a
	 * variant for players who loaded the resource pack (a custom sprite in place of a vanilla one).
	 */
	public record Fx(Particle particle, Object data, Fx packVariant, double countScale, double speed) {
		public Fx(Particle particle, Object data) {
			this(particle, data, null, 1.0, -1.0);
		}

		/**
		 * This effect, but players with the pack see {@code variant}, {@code countScale} as many.
		 * {@code speed} 0 or more is the variant's own speed; a negative one scales the original
		 * (-1 keeps it, -0.1 is a tenth: dust slows itself to a tenth, the custom sprites don't).
		 */
		public Fx withPack(Fx variant, double countScale, double speed) {
			return new Fx(particle, data, variant, countScale, speed);
		}
	}

	public static final Color BLOOD_RED = Color.fromRGB(0xB00010);
	public static final Color BRIGHT_RED = Color.fromRGB(0xFF2A35);
	public static final Color CLOT_RED = Color.fromRGB(0x4A0006);

	// The pack's blood sprites (tools/models/particles.py), drawn over vanilla particles that are
	// rare in normal play and not recoloured by the game. Only ever sent to players with the pack.
	/** A drop that bursts into a splash and dries (the pack's sculk charge). */
	private static final Fx PACK_SPLAT = new Fx(Particle.SCULK_CHARGE, 0.0F);
	/** A hot glowing fleck of blood (the pack's sculk charge pop). */
	private static final Fx PACK_SPARK = new Fx(Particle.SCULK_CHARGE_POP, null);
	/** A drop of blood that falls and splashes where it lands (the pack's obsidian tear). */
	private static final Fx PACK_DROP = new Fx(Particle.FALLING_OBSIDIAN_TEAR, null);
	/** A ring of blood rising off the ground (the pack's shriek). */
	private static final Fx PACK_RING = new Fx(Particle.SHRIEK, 0);

	public static final Fx BLOOD = new Fx(Particle.DUST, new Particle.DustOptions(BLOOD_RED, 1.1F))
		.withPack(PACK_SPLAT, 0.8, -0.1);
	public static final Fx BLOOD_LARGE = new Fx(Particle.DUST, new Particle.DustOptions(BLOOD_RED, 2.2F))
		.withPack(PACK_SPLAT, 1.0, -0.1);
	public static final Fx BLOOD_FADE = new Fx(Particle.DUST_COLOR_TRANSITION, new Particle.DustTransition(BRIGHT_RED, CLOT_RED, 1.6F))
		.withPack(PACK_SPLAT, 0.8, -0.1);
	public static final Fx CLOT = new Fx(Particle.DUST, new Particle.DustOptions(CLOT_RED, 1.8F))
		.withPack(PACK_SPLAT, 0.7, -0.1);
	/** Chunks of blood flung out of a wound (block particles pop out at their own speed). */
	public static final Fx SPLATTER = new Fx(Particle.BLOCK, Material.REDSTONE_BLOCK.createBlockData())
		.withPack(PACK_SPLAT, 0.6, 0.06);
	public static final Fx GORE = new Fx(Particle.BLOCK, Material.NETHER_WART_BLOCK.createBlockData())
		.withPack(PACK_SPLAT, 0.5, 0.05);
	/** Blood dripping: red falling dust; with the pack, real drops that splash on the ground. */
	public static final Fx DRIP = new Fx(Particle.FALLING_DUST, Material.REDSTONE_BLOCK.createBlockData())
		.withPack(PACK_DROP, 1.0, 0.0);
	/** A fine, small blood mote for trails that pass close to the camera. */
	public static final Fx MOTE = new Fx(Particle.DUST, new Particle.DustOptions(BRIGHT_RED, 0.7F))
		.withPack(PACK_SPARK, 1.0, -0.1);
	public static final Fx HURT = new Fx(Particle.DAMAGE_INDICATOR, null);
	public static final Fx SPORE = new Fx(Particle.CRIMSON_SPORE, null);
	public static final Fx SOUL = new Fx(Particle.SCULK_SOUL, null);
	public static final Fx SMOKE = new Fx(Particle.LARGE_SMOKE, null);
	public static final Fx SPARK = new Fx(Particle.ELECTRIC_SPARK, null);
	public static final Fx SWEEP = new Fx(Particle.SWEEP_ATTACK, null);
	public static final Fx BURST = new Fx(Particle.EXPLOSION, null);
	public static final Fx BURST_HUGE = new Fx(Particle.EXPLOSION_EMITTER, null);
	public static final Fx GLYPH = new Fx(Particle.ENCHANT, null);
	/** Low, heavy, dark-red haze: the Blood Mist. */
	public static final Fx MIST = new Fx(Particle.DUST_COLOR_TRANSITION, new Particle.DustTransition(Color.fromRGB(0x3A0008), Color.fromRGB(0x12000A), 3.2F));
	/** Tiny bright sparks of blood, for rising and orbiting streams. */
	public static final Fx EMBER = new Fx(Particle.DUST, new Particle.DustOptions(Color.fromRGB(0xFF4050), 0.55F))
		.withPack(PACK_SPARK, 1.0, -0.1);
	/** A ring of blood rising off the ground: rage, the boss's roar. Dust for players without the pack. */
	public static final Fx RING = new Fx(Particle.DUST, new Particle.DustOptions(BRIGHT_RED, 1.4F))
		.withPack(PACK_RING, 1.0, 0.0);
	/**
	 * An impact flash. Players with the pack see the "blood nova" sprite animation (the pack
	 * redraws the warden's sonic boom, which nothing else uses); everyone else a dust burst.
	 */
	public static final Fx NOVA = BLOOD_LARGE.withPack(new Fx(Particle.SONIC_BOOM, null), 0.0, 0.0);

	public static final String HEARTBEAT = "entity.warden.heartbeat";
	public static final String SQUELCH = "entity.slime.squish";
	public static final String WET_SLIDE = "block.honey_block.slide";
	public static final String FANGS = "entity.evoker_fangs.attack";
	public static final String CHAIN = "item.trident.throw";
	public static final String CHAIN_SNAP = "item.trident.return";
	public static final String RIFT_OPEN = "block.respawn_anchor.charge";
	public static final String RIFT_STEP = "entity.enderman.teleport";
	public static final String MIRROR = "entity.illusioner.mirror_move";
	public static final String ROAR = "entity.ravager.roar";
	public static final String IMPACT = "entity.generic.explode";
	public static final String THUNDER = "entity.lightning_bolt.impact";
	public static final String BOW_RELEASE = "item.crossbow.shoot";
	public static final String BOW_DRAWN = "item.crossbow.loading_end";
	public static final String BOW_ECHO = "entity.warden.sonic_boom";
	/** The classic "your arrow hit a player" ding. */
	public static final String MARKED = "entity.arrow.hit_player";
	public static final String ECHO_HIT = "item.trident.hit";
	public static final String BARBS = "enchant.thorns.hit";
	public static final String ARMOR_SET = "block.beacon.power_select";
	public static final String RAGE_FADES = "block.fire.extinguish";
	public static final String HIT_MARKER = "block.note_block.hat";
	public static final String CLOCK_MARK = "block.respawn_anchor.set_spawn";
	public static final String CLOCK_RECALL = "block.respawn_anchor.deplete";
	public static final String CLOCK_TICK = "block.note_block.hat";
	public static final String NULLIFY = "block.beacon.deactivate";
	public static final String HARVEST = "entity.player.attack.sweep";
	public static final String READY = "block.note_block.bell";
	public static final String DASH = "entity.breeze.wind_burst";
	public static final String DRINK = "entity.generic.drink";
	public static final String PAGE = "item.book.page_turn";
	public static final String RANK_UP = "ui.toast.challenge_complete";

	/** Held-weapon drips and other ambience: nobody further than this needs them. */
	private static final double AMBIENT_RANGE = 32.0;

	private BloodFx() {
	}

	private static double viewDistance() {
		return Settings.get().particleViewDistance;
	}

	// ---- spawning helpers ------------------------------------------------------------------

	/** Ability particle: seen from {@code effects.view-distance}, fewer the further away the viewer is. */
	public static void emit(World world, Fx fx, double x, double y, double z, int count, double dx, double dy, double dz, double speed) {
		Particles.spawn(world, fx, x, y, z, count, dx, dy, dz, speed, Particles.Audience.ALL, null, viewDistance());
	}

	/** Ambient particle (held-weapon drips, auras): close range only. */
	public static void ambient(Location at, Fx fx, int count, double spread) {
		Particles.spawn(at.getWorld(), fx, at.getX(), at.getY(), at.getZ(), count, spread, spread, spread, 0.0, Particles.Audience.ALL,
			null, AMBIENT_RANGE);
	}

	/** Ambient particle that everyone but {@code self} sees. */
	public static void ambientForOthers(Player self, Location at, Fx fx, int count, double spread) {
		Particles.spawn(at.getWorld(), fx, at.getX(), at.getY(), at.getZ(), count, spread, spread, spread, 0.0, Particles.Audience.OTHERS,
			self, AMBIENT_RANGE);
	}

	/** Ambient particle only {@code self} sees (their own, toned-down view of their aura). */
	public static void ambientForSelf(Player self, Location at, Fx fx, int count, double spread) {
		Particles.spawn(at.getWorld(), fx, at.getX(), at.getY(), at.getZ(), count, spread, spread, spread, 0.0, Particles.Audience.SELF,
			self, AMBIENT_RANGE);
	}

	/** Ability-style burst that everyone but {@code self} sees. */
	public static void burstForOthers(Player self, Location at, Fx fx, int count, double spread) {
		Particles.spawn(at.getWorld(), fx, at.getX(), at.getY(), at.getZ(), count, spread, spread, spread, 0.02, Particles.Audience.OTHERS,
			self, viewDistance());
	}

	public static void burst(Location at, Fx fx, int count, double spread) {
		emit(at.getWorld(), fx, at.getX(), at.getY(), at.getZ(), count, spread, spread, spread, 0.02);
	}

	public static void burst(Location at, Fx fx, int count, double spread, double speed) {
		emit(at.getWorld(), fx, at.getX(), at.getY(), at.getZ(), count, spread, spread, spread, speed);
	}

	/** A wet blood splash: fine mist, chunky block crumbs and a few drips. */
	public static void splash(Location at, int intensity) {
		World w = at.getWorld();
		emit(w, BLOOD_FADE, at.getX(), at.getY(), at.getZ(), intensity * 3, 0.35, 0.35, 0.35, 0.0);
		emit(w, SPLATTER, at.getX(), at.getY(), at.getZ(), intensity * 2, 0.3, 0.3, 0.3, 0.15);
		emit(w, DRIP, at.getX(), at.getY(), at.getZ(), Math.max(1, intensity / 2), 0.3, 0.2, 0.3, 0.0);
	}

	/** Particle line between two points, {@code perBlock} samples per block of distance. */
	public static void line(Location from, Location to, Fx fx, double perBlock) {
		double length = from.distance(to);
		if (!Particles.anyoneNear(from.getWorld(), (from.getX() + to.getX()) / 2, (from.getY() + to.getY()) / 2,
			(from.getZ() + to.getZ()) / 2, viewDistance() + length / 2)) {
			return;
		}
		int steps = Math.max(2, (int) Math.ceil(length * perBlock * Math.min(1.0, Settings.get().particleMultiplier)));
		World w = from.getWorld();
		for (int i = 0; i <= steps; i++) {
			double t = i / (double) steps;
			emit(w, fx, lerp(from.getX(), to.getX(), t), lerp(from.getY(), to.getY(), t), lerp(from.getZ(), to.getZ(), t), 1, 0, 0, 0, 0);
		}
	}

	/** Flat horizontal ring. */
	public static void ring(Location center, Fx fx, double radius, int points) {
		if (!Particles.anyoneNear(center.getWorld(), center.getX(), center.getY(), center.getZ(), viewDistance() + radius)) {
			return;
		}
		int n = Math.max(3, (int) Math.round(points * Math.min(1.0, Settings.get().particleMultiplier)));
		World w = center.getWorld();
		for (int i = 0; i < n; i++) {
			double angle = (Math.PI * 2.0 * i) / n;
			emit(w, fx, center.getX() + Math.cos(angle) * radius, center.getY(), center.getZ() + Math.sin(angle) * radius, 1, 0, 0, 0, 0);
		}
	}

	/** Streams of blood that visibly flow from around {@code from} to {@code to} (vanilla "trail" particle). */
	public static void flow(Location from, Location to, int count, double spread, Color color, int durationTicks) {
		emit(from.getWorld(), new Fx(Particle.TRAIL, new Particle.Trail(to, color, durationTicks)),
			from.getX(), from.getY(), from.getZ(), count, spread, spread, spread, 0.0);
	}

	/** Blood drawn inward from a ring of {@code radius} around {@code center}. */
	public static void gather(Location center, double radius, int streams, int durationTicks) {
		ThreadLocalRandom random = ThreadLocalRandom.current();
		for (int i = 0; i < streams; i++) {
			double angle = (Math.PI * 2.0 * i) / streams + random.nextDouble() * 0.6;
			double lift = (random.nextDouble() - 0.5) * radius;
			Location from = center.clone().add(Math.cos(angle) * radius, lift, Math.sin(angle) * radius);
			flow(from, center, 1, 0.05, i % 2 == 0 ? BRIGHT_RED : BLOOD_RED, durationTicks);
		}
	}

	/** Blood bursting outward from {@code center} to a ring of {@code radius}. */
	public static void spray(Location center, double radius, int streams, int durationTicks) {
		ThreadLocalRandom random = ThreadLocalRandom.current();
		for (int i = 0; i < streams; i++) {
			double angle = (Math.PI * 2.0 * i) / streams + random.nextDouble() * 0.6;
			double lift = random.nextDouble() * radius * 0.6;
			Location to = center.clone().add(Math.cos(angle) * radius, lift, Math.sin(angle) * radius);
			flow(center, to, 1, 0.05, i % 2 == 0 ? BRIGHT_RED : BLOOD_RED, durationTicks);
		}
	}

	/** What a Bloodbath weapon leaves behind when it kills. */
	public static void killBurst(Entity victim, Entity killer) {
		Location chest = chest(victim);
		burst(chest, BURST, 1, 0.0);
		burst(chest, SPLATTER, 60, 0.6, 0.3);
		burst(chest, GORE, 30, 0.5, 0.25);
		burst(chest, BLOOD_FADE, 40, 0.8);
		burst(chest, HURT, 8, 0.4, 0.1);
		ring(victim.getLocation().add(0.0, 0.05, 0.0), CLOT, 1.2, 18);
		// The weapon drinks: blood streams from the corpse into the killer.
		Location killerChest = killer.getLocation().add(0.0, killer.getHeight() * 0.6, 0.0);
		for (int i = 0; i < 6; i++) {
			flow(chest, killerChest, 1, 0.4, i % 2 == 0 ? BRIGHT_RED : BLOOD_RED, 14 + i * 2);
		}
		play(chest, SQUELCH, 1.2F, 0.5F);
		play(chest, HEARTBEAT, 1.0F, 0.6F);
	}

	/** A sound at a place (heard by whoever is close enough), after the config's volume and swaps. */
	public static void play(Location at, String sound, float volume, float pitch) {
		String id = resolve(sound);
		float scaled = volume * (float) Settings.get().soundVolume;
		if (id != null && scaled > 0.0F) {
			at.getWorld().playSound(at, id, SoundCategory.PLAYERS, scaled, pitch);
		}
	}

	/** A sound only {@code player} hears (hit markers, heartbeats, menu clicks). */
	public static void playTo(Player player, String sound, float volume, float pitch) {
		String id = resolve(sound);
		float scaled = volume * (float) Settings.get().soundVolume;
		if (id != null && scaled > 0.0F) {
			player.playSound(player, id, SoundCategory.PLAYERS, scaled, pitch);
		}
	}

	/** {@code sounds.replace} lets owners swap or mute (empty string) any sound by id. */
	private static String resolve(String sound) {
		String id = Settings.get().soundReplace.getOrDefault(sound, sound);
		return id.isEmpty() ? null : id;
	}

	public static void play(Entity at, String sound, float volume, float pitch) {
		play(at.getLocation(), sound, volume, pitch);
	}

	public static Location chest(Entity entity) {
		return entity.getLocation().add(0.0, entity.getHeight() * 0.5, 0.0);
	}

	private static double lerp(double a, double b, double t) {
		return a + (b - a) * t;
	}
}
