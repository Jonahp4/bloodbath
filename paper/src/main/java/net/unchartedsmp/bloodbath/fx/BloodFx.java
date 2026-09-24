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

/**
 * The shared blood palette: every weapon draws its particles and sounds from here so the whole
 * set reads as one theme. Sounds are played by vanilla id, so nothing breaks if a constant moves.
 *
 * <p>Ability particles are sent long-range (up to 512 blocks, configurable). Every count is scaled
 * by {@code effects.particle-multiplier}.
 */
public final class BloodFx {
	/** A particle type plus the data it needs (dust colour, block state, ...), or null. */
	public record Fx(Particle particle, Object data) {
	}

	public static final Color BLOOD_RED = Color.fromRGB(0xB00010);
	public static final Color BRIGHT_RED = Color.fromRGB(0xFF2A35);
	public static final Color CLOT_RED = Color.fromRGB(0x4A0006);

	public static final Fx BLOOD = new Fx(Particle.DUST, new Particle.DustOptions(BLOOD_RED, 1.1F));
	public static final Fx BLOOD_LARGE = new Fx(Particle.DUST, new Particle.DustOptions(BLOOD_RED, 2.2F));
	public static final Fx BLOOD_FADE = new Fx(Particle.DUST_COLOR_TRANSITION, new Particle.DustTransition(BRIGHT_RED, CLOT_RED, 1.6F));
	public static final Fx CLOT = new Fx(Particle.DUST, new Particle.DustOptions(CLOT_RED, 1.8F));
	public static final Fx SPLATTER = new Fx(Particle.BLOCK, Material.REDSTONE_BLOCK.createBlockData());
	public static final Fx GORE = new Fx(Particle.BLOCK, Material.NETHER_WART_BLOCK.createBlockData());
	public static final Fx DRIP = new Fx(Particle.FALLING_LAVA, null);
	public static final Fx HURT = new Fx(Particle.DAMAGE_INDICATOR, null);
	public static final Fx SPORE = new Fx(Particle.CRIMSON_SPORE, null);
	public static final Fx SOUL = new Fx(Particle.SCULK_SOUL, null);
	public static final Fx SMOKE = new Fx(Particle.LARGE_SMOKE, null);
	public static final Fx SPARK = new Fx(Particle.ELECTRIC_SPARK, null);
	public static final Fx SWEEP = new Fx(Particle.SWEEP_ATTACK, null);
	public static final Fx BURST = new Fx(Particle.EXPLOSION, null);
	public static final Fx BURST_HUGE = new Fx(Particle.EXPLOSION_EMITTER, null);
	public static final Fx GLYPH = new Fx(Particle.ENCHANT, null);

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

	private BloodFx() {
	}

	private static int scaled(int count) {
		double multiplier = Settings.get().particleMultiplier;
		if (count <= 0 || multiplier <= 0.0) {
			return 0;
		}
		return Math.max(1, (int) Math.round(count * multiplier));
	}

	// ---- spawning helpers ------------------------------------------------------------------

	/** Ability particle: long-range (configurable), count scaled by the multiplier. */
	public static void emit(World world, Fx fx, double x, double y, double z, int count, double dx, double dy, double dz, double speed) {
		int n = scaled(count);
		if (n > 0) {
			world.spawnParticle(fx.particle(), x, y, z, n, dx, dy, dz, speed, fx.data(), Settings.get().longRangeParticles);
		}
	}

	/** Ambient particle (held-weapon drips): normal range. */
	public static void ambient(Location at, Fx fx, int count, double spread) {
		int n = scaled(count);
		if (n > 0) {
			at.getWorld().spawnParticle(fx.particle(), at.getX(), at.getY(), at.getZ(), n, spread, spread, spread, 0.0, fx.data(), false);
		}
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
		int steps = Math.max(2, (int) Math.ceil(length * perBlock * Math.min(1.0, Settings.get().particleMultiplier)));
		World w = from.getWorld();
		for (int i = 0; i <= steps; i++) {
			double t = i / (double) steps;
			emit(w, fx, lerp(from.getX(), to.getX(), t), lerp(from.getY(), to.getY(), t), lerp(from.getZ(), to.getZ(), t), 1, 0, 0, 0, 0);
		}
	}

	/** Flat horizontal ring. */
	public static void ring(Location center, Fx fx, double radius, int points) {
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

	public static void play(Location at, String sound, float volume, float pitch) {
		at.getWorld().playSound(at, sound, SoundCategory.PLAYERS, volume, pitch);
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
