package net.minecraft.world;
import net.minecraft.entity.Entity;
import net.minecraft.entity.damage.DamageSources;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
public abstract class World implements EntityView, CollisionView, WorldAccess {
	public boolean spawnEntity(Entity entity) { throw new UnsupportedOperationException(); }
	public DamageSources getDamageSources() { throw new UnsupportedOperationException(); }
	public void playSound(Entity source, double x, double y, double z, SoundEvent sound, SoundCategory category, float volume, float pitch) { throw new UnsupportedOperationException(); }
}
