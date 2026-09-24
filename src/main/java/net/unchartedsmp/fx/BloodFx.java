package net.unchartedsmp.fx;

import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleType;
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
 */
public final class BloodFx {
	/** Fresh arterial red. */
	public static final int BLOOD_RED = 0xB00010;
	/** Dark, drying venous red. */
	public static final int CLOT_RED = 0x4A0006;

	public static ParticleEffect BLOOD;
	public static ParticleEffect BLOOD_LARGE;
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
	public static SoundEvent BOW_ECHO;
	public static SoundEvent CLOCK_MARK;
	public static SoundEvent CLOCK_RECALL;
	public static SoundEvent NULLIFY;
	public static SoundEvent HARVEST;

	private BloodFx() {
	}

	public static void init() {
		BLOOD = new DustParticleEffect(BLOOD_RED, 1.1F);
		BLOOD_LARGE = new DustParticleEffect(BLOOD_RED, 2.2F);
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
		BOW_ECHO = sound("minecraft:entity.warden.sonic_boom");
		CLOCK_MARK = sound("minecraft:block.respawn_anchor.set_spawn");
		CLOCK_RECALL = sound("minecraft:block.respawn_anchor.deplete");
		NULLIFY = sound("minecraft:block.beacon.deactivate");
		HARVEST = sound("minecraft:entity.player.attack.sweep");
	}

	// ---- spawning helpers ------------------------------------------------------------------

	public static void burst(ServerWorld world, ParticleEffect particle, Vec3d pos, int count, double spread) {
		world.spawnParticles(particle, pos.x, pos.y, pos.z, count, spread, spread, spread, 0.02);
	}

	public static void burst(ServerWorld world, ParticleEffect particle, Vec3d pos, int count, double spread, double speed) {
		world.spawnParticles(particle, pos.x, pos.y, pos.z, count, spread, spread, spread, speed);
	}

	/** A wet blood splash: fine mist, chunky block crumbs and a few drips. */
	public static void splash(ServerWorld world, Vec3d pos, int intensity) {
		world.spawnParticles(BLOOD, pos.x, pos.y, pos.z, intensity * 3, 0.35, 0.35, 0.35, 0.0);
		world.spawnParticles(SPLATTER, pos.x, pos.y, pos.z, intensity * 2, 0.3, 0.3, 0.3, 0.15);
		world.spawnParticles(DRIP, pos.x, pos.y, pos.z, Math.max(1, intensity / 2), 0.3, 0.2, 0.3, 0.0);
	}

	/** Particle line between two points, {@code perBlock} samples per block of distance. */
	public static void line(ServerWorld world, ParticleEffect particle, Vec3d from, Vec3d to, double perBlock) {
		double length = from.distanceTo(to);
		int steps = Math.max(2, (int) Math.ceil(length * perBlock));
		for (int i = 0; i <= steps; i++) {
			Vec3d p = from.lerp(to, i / (double) steps);
			world.spawnParticles(particle, p.x, p.y, p.z, 1, 0.0, 0.0, 0.0, 0.0);
		}
	}

	/** Flat horizontal ring. */
	public static void ring(ServerWorld world, ParticleEffect particle, Vec3d center, double radius, int points) {
		for (int i = 0; i < points; i++) {
			double angle = (Math.PI * 2.0 * i) / points;
			world.spawnParticles(
				particle, center.x + Math.cos(angle) * radius, center.y, center.z + Math.sin(angle) * radius, 1, 0.0, 0.0, 0.0, 0.0
			);
		}
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
