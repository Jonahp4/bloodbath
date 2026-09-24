package net.minecraft.entity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;
public class LightningEntity extends Entity {
	public LightningEntity(EntityType<? extends LightningEntity> type, World world) { throw new UnsupportedOperationException(); }
	public void setCosmetic(boolean cosmetic) { throw new UnsupportedOperationException(); }
	@Override public boolean damage(ServerWorld world, DamageSource source, float amount) { throw new UnsupportedOperationException(); }
}
