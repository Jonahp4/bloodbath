package net.unchartedsmp.fx;

import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.DustColorTransitionParticleEffect;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleType;
import net.minecraft.particle.TrailParticleEffect;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.unchartedsmp.UnchartedSMP;

/**
 * The shared blood palette: every weapon draws its particles and sounds from here so the whole
 * set reads as one theme. Vanilla particles/sounds are resolved by registry id when the server
 * starts (Yarn doesn't name the {@code ParticleTypes}/{@code SoundEvents} constants, and ids are
 * stable across versions anyway).
 *
 * <p>Ability particles are sent as <i>important, long-range</i> particles so they still show for
 * players on "Decreased"/"Minimal" particle settings and from further away. Ambient effects
 * (held-weapon drips) use normal particles and respect those settings.
 */
public final class BloodFx {
	/** Fresh arterial red. */
	public static final int BLOOD_RED = 0xB00010;
	/** Bright, just-spilled red. */
	public static final int BRIGHT_RED = 0xFF2A35;
	/** Dark, drying venous red. */
	public static final int CLOT_RED = 0x4A0006;

	public static ParticleEffect BLOOD;
	public static ParticleEffect BLOOD_LARGE;
	public static ParticleEffect BLOOD_FADE;
	public static ParticleEffect CLOT;
	public static ParticleEffect SPLATTER;
	public static ParticleEffect GORE;
	public static ParticleEffect DRIP;
	public static ParticleEffect HURT;
	public static ParticleEffect SPORE;
	public static ParticleEffect SOUL;
	public static ParticleEffect SMOKE;
	public static ParticleEffect SPARK;
	public static ParticleEffect SWEEP;
	public static ParticleEffect BURST;
	public static ParticleEffect BURST_HUGE;

	public static SoundEvent HEARTBEAT;
	public static SoundEvent SQUELCH;
	public static SoundEvent WET_SLIDE;
	public static SoundEvent FANGS;
	public static SoundEvent CHAIN;
	public static SoundEvent CHAIN_SNAP;
	public static SoundEvent RIFT_OPEN;
	public static SoundEvent RIFT_STEP;
	public static SoundEvent MIRROR;
	public static SoundEvent ROAR;
	public static SoundEvent IMPACT;
	public static SoundEvent THUNDER;
	public static SoundEvent BOW_RELEASE;
	public static SoundEvent BOW_DRAWN;
	public static SoundEvent BOW_ECHO;
	public static SoundEvent CLOCK_MARK;
	public static SoundEvent CLOCK_RECALL;
	public static SoundEvent CLOCK_TICK;
	public static SoundEvent NULLIFY;
	public static SoundEvent HARVEST;
	public static SoundEvent READY;

	/** Flips off (once) if this server's TrailParticleEffect doesn't match; trails then fall back to dust lines. */
	private static boolean trailsWork = true;

	private BloodFx() {
	}

	public static void init() {
		BLOOD = new DustParticleEffect(BLOOD_RED, 1.1F);
		BLOOD_LARGE = new DustParticleEffect(BLOOD_RED, 2.2F);
		BLOOD_FADE = new DustColorTransitionParticleEffect(BRIGHT_RED, CLOT_RED, 1.6F);
		CLOT = new DustParticleEffect(CLOT_RED, 1.8F);
		SPLATTER = blockParticle("minecraft:redstone_block");
		GORE = blockParticle("minecraft:nether_wart_block");
		DRIP = particle("minecraft:falling_lava", BLOOD);
		HURT = particle("minecraft:damage_indicator", BLOOD);
		SPORE = particle("minecraft:crimson_spore", BLOOD);
		SOUL = particle("minecraft:sculk_soul", CLOT);
		SMOKE = particle("minecraft:large_smoke", CLOT);
		SPARK = particle("minecraft:electric_spark", BLOOD);
		SWEEP = particle("minecraft:sweep_attack", BLOOD);
		BURST = particle("minecraft:explosion", BLOOD_LARGE);
		BURST_HUGE = particle("minecraft:explosion_emitter", BLOOD_LARGE);

		HEARTBEAT = sound("minecraft:entity.warden.heartbeat");
		SQUELCH = sound("minecraft:entity.slime.squish");
		WET_SLIDE = sound("minecraft:block.honey_block.slide");
		FANGS = sound("minecraft:entity.evoker_fangs.attack");
		CHAIN = sound("minecraft:item.trident.throw");
		CHAIN_SNAP = sound("minecraft:item.trident.return");
		RIFT_OPEN = sound("minecraft:block.respawn_anchor.charge");
		RIFT_STEP = sound("minecraft:entity.enderman.teleport");
		MIRROR = sound("minecraft:entity.illusioner.mirror_move");
		ROAR = sound("minecraft:entity.ravager.roar");
		IMPACT = sound("minecraft:entity.generic.explode");
		THUNDER = sound("minecraft:entity.lightning_bolt.impact");
		BOW_RELEASE = sound("minecraft:item.crossbow.shoot");
		BOW_DRAWN = sound("minecraft:item.crossbow.loading_end");
		BOW_ECHO = sound("minecraft:entity.warden.sonic_boom");
		CLOCK_MARK = sound("minecraft:block.respawn_anchor.set_spawn");
		CLOCK_RECALL = sound("minecraft:block.respawn_anchor.deplete");
		CLOCK_TICK = sound("minecraft:block.note_block.hat");
		NULLIFY = sound("minecraft:block.beacon.deactivate");
		HARVEST = sound("minecraft:entity.player.attack.sweep");
		READY = sound("minecraft:block.note_block.bell");
	}

	// ---- spawning helpers ------------------------------------------------------------------

	/** Important + long-range: shows on low particle settings and from far away. */
	public static void emit(ServerWorld world, ParticleEffect particle, double x, double y, double z, int count,
		double dx, double dy, double dz, double speed) {
		world.spawnParticles(particle, true, true, x, y, z, count, dx, dy, dz, speed);
	}

	/** Normal particle: respects the viewer's particle setting (ambient effects). */
	public static void ambient(ServerWorld world, ParticleEffect particle, Vec3d pos, int count, double spread) {
		world.spawnParticles(particle, pos.x, pos.y, pos.z, count, spread, spread, spread, 0.0);
	}

	public static void burst(ServerWorld world, ParticleEffect particle, Vec3d pos, int count, double spread) {
		emit(world, particle, pos.x, pos.y, pos.z, count, spread, spread, spread, 0.02);
	}

	public static void burst(ServerWorld world, ParticleEffect particle, Vec3d pos, int count, double spread, double speed) {
		emit(world, particle, pos.x, pos.y, pos.z, count, spread, spread, spread, speed);
	}

	/** A wet blood splash: fine mist, chunky block crumbs and a few drips. */
	public static void splash(ServerWorld world, Vec3d pos, int intensity) {
		emit(world, BLOOD_FADE, pos.x, pos.y, pos.z, intensity * 3, 0.35, 0.35, 0.35, 0.0);
		emit(world, SPLATTER, pos.x, pos.y, pos.z, intensity * 2, 0.3, 0.3, 0.3, 0.15);
		emit(world, DRIP, pos.x, pos.y, pos.z, Math.max(1, intensity / 2), 0.3, 0.2, 0.3, 0.0);
	}

	/** Particle line between two points, {@code perBlock} samples per block of distance. */
	public static void line(ServerWorld world, ParticleEffect particle, Vec3d from, Vec3d to, double perBlock) {
		double length = from.distanceTo(to);
		int steps = Math.max(2, (int) Math.ceil(length * perBlock));
		for (int i = 0; i <= steps; i++) {
			Vec3d p = from.lerp(to, i / (double) steps);
			emit(world, particle, p.x, p.y, p.z, 1, 0.0, 0.0, 0.0, 0.0);
		}
	}

	/** Flat horizontal ring. */
	public static void ring(ServerWorld world, ParticleEffect particle, Vec3d center, double radius, int points) {
		for (int i = 0; i < points; i++) {
			double angle = (Math.PI * 2.0 * i) / points;
			emit(world, particle, center.x + Math.cos(angle) * radius, center.y, center.z + Math.sin(angle) * radius, 1, 0.0, 0.0, 0.0, 0.0);
		}
	}

	/**
	 * Streams of blood that visibly flow from around {@code from} to {@code to} over
	 * {@code durationTicks}. Uses the vanilla "trail" particle.
	 */
	public static void flow(ServerWorld world, Vec3d from, Vec3d to, int count, double spread, int color, int durationTicks) {
		if (trailsWork) {
			try {
				emit(world, new TrailParticleEffect(to, color, durationTicks), from.x, from.y, from.z, count, spread, spread, spread, 0.0);
				return;
			} catch (LinkageError e) {
				trailsWork = false;
				UnchartedSMP.LOGGER.warn("Trail particles unavailable, using dust lines instead", e);
			}
		}
		line(world, BLOOD, from, to, 2.0);
	}

	/** Blood drawn inward from a ring of {@code radius} around {@code center}. */
	public static void gather(ServerWorld world, Vec3d center, double radius, int streams, int durationTicks) {
		for (int i = 0; i < streams; i++) {
			double angle = (Math.PI * 2.0 * i) / streams + world.getRandom().nextDouble() * 0.6;
			double lift = (world.getRandom().nextDouble() - 0.5) * radius;
			Vec3d from = center.add(Math.cos(angle) * radius, lift, Math.sin(angle) * radius);
			flow(world, from, center, 1, 0.05, i % 2 == 0 ? BRIGHT_RED : BLOOD_RED, durationTicks);
		}
	}

	/** Blood bursting outward from {@code center} to a ring of {@code radius}. */
	public static void spray(ServerWorld world, Vec3d center, double radius, int streams, int durationTicks) {
		for (int i = 0; i < streams; i++) {
			double angle = (Math.PI * 2.0 * i) / streams + world.getRandom().nextDouble() * 0.6;
			double lift = world.getRandom().nextDouble() * radius * 0.6;
			Vec3d to = center.add(Math.cos(angle) * radius, lift, Math.sin(angle) * radius);
			flow(world, center, to, 1, 0.05, i % 2 == 0 ? BRIGHT_RED : BLOOD_RED, durationTicks);
		}
	}

	/** What a Bloodbath weapon leaves behind when it kills. */
	public static void killBurst(ServerWorld world, Entity victim, Entity killer) {
		Vec3d chest = victim.getEntityPos().add(0.0, victim.getHeight() * 0.5, 0.0);
		burst(world, BURST, chest, 1, 0.0);
		burst(world, SPLATTER, chest, 60, 0.6, 0.3);
		burst(world, GORE, chest, 30, 0.5, 0.25);
		burst(world, BLOOD_FADE, chest, 40, 0.8);
		burst(world, HURT, chest, 8, 0.4, 0.1);
		ring(world, CLOT, victim.getEntityPos().add(0.0, 0.05, 0.0), 1.2, 18);
		// The weapon drinks: blood streams from the corpse into the killer.
		Vec3d killerChest = killer.getEntityPos().add(0.0, killer.getHeight() * 0.6, 0.0);
		for (int i = 0; i < 6; i++) {
			flow(world, chest, killerChest, 1, 0.4, i % 2 == 0 ? BRIGHT_RED : BLOOD_RED, 14 + i * 2);
		}
		play(world, chest, SQUELCH, 1.2F, 0.5F);
		play(world, chest, HEARTBEAT, 1.0F, 0.6F);
	}

	public static void play(ServerWorld world, Vec3d pos, SoundEvent sound, float volume, float pitch) {
		if (sound != null) {
			world.playSound(null, pos.x, pos.y, pos.z, sound, SoundCategory.PLAYERS, volume, pitch);
		}
	}

	public static void play(ServerWorld world, Entity at, SoundEvent sound, float volume, float pitch) {
		if (sound != null) {
			world.playSound(null, at.getX(), at.getY(), at.getZ(), sound, SoundCategory.PLAYERS, volume, pitch);
		}
	}

	// ---- registry lookups ------------------------------------------------------------------

	private static ParticleEffect particle(String id, ParticleEffect fallback) {
		ParticleType<?> type = Registries.PARTICLE_TYPE.get(Identifier.of(id));
		if (type instanceof ParticleEffect effect) {
			return effect;
		}
		UnchartedSMP.LOGGER.warn("Particle {} unavailable, using blood dust instead", id);
		return fallback;
	}

	@SuppressWarnings("unchecked")
	private static ParticleEffect blockParticle(String blockId) {
		ParticleType<?> type = Registries.PARTICLE_TYPE.get(Identifier.of("minecraft:block"));
		Block block = Registries.BLOCK.get(Identifier.of(blockId));
		if (type == null || block == null) {
			return BLOOD;
		}
		return new BlockStateParticleEffect((ParticleType<BlockStateParticleEffect>) type, block.getDefaultState());
	}

	private static SoundEvent sound(String id) {
		SoundEvent sound = Registries.SOUND_EVENT.get(Identifier.of(id));
		if (sound == null) {
			UnchartedSMP.LOGGER.warn("Sound {} unavailable, it will be skipped", id);
		}
		return sound;
	}
}
