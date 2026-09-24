package net.minecraft.server.world;
import java.util.List;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.World;
public abstract class ServerWorld extends World {
	public <T extends ParticleEffect> int spawnParticles(T particle, double x, double y, double z, int count, double dx, double dy, double dz, double speed) { throw new UnsupportedOperationException(); }
	@Override public List<ServerPlayerEntity> getPlayers() { throw new UnsupportedOperationException(); }
}
