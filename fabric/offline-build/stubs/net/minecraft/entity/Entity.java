package net.minecraft.entity;
import java.util.Set;
import java.util.UUID;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Nameable;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
public abstract class Entity implements Nameable {
	public UUID getUuid() { throw new UnsupportedOperationException(); }
	public Vec3d getEntityPos() { throw new UnsupportedOperationException(); }
	public World getEntityWorld() { throw new UnsupportedOperationException(); }
	public double getX() { throw new UnsupportedOperationException(); }
	public double getY() { throw new UnsupportedOperationException(); }
	public double getZ() { throw new UnsupportedOperationException(); }
	public Vec3d getVelocity() { throw new UnsupportedOperationException(); }
	public void setVelocity(Vec3d velocity) { throw new UnsupportedOperationException(); }
	public boolean isAlive() { throw new UnsupportedOperationException(); }
	public double squaredDistanceTo(Vec3d vector) { throw new UnsupportedOperationException(); }
	public final Vec3d getEyePos() { throw new UnsupportedOperationException(); }
	public final Vec3d getRotationVec(float tickProgress) { throw new UnsupportedOperationException(); }
	public final Box getBoundingBox() { throw new UnsupportedOperationException(); }
	public float getYaw() { throw new UnsupportedOperationException(); }
	public float getPitch() { throw new UnsupportedOperationException(); }
	public Text getName() { throw new UnsupportedOperationException(); }
	public final void discard() { throw new UnsupportedOperationException(); }
	public boolean addCommandTag(String tag) { throw new UnsupportedOperationException(); }
	public Set<String> getCommandTags() { throw new UnsupportedOperationException(); }
	public void refreshPositionAndAngles(double x, double y, double z, float yaw, float pitch) { throw new UnsupportedOperationException(); }
	public void refreshPositionAfterTeleport(Vec3d pos) { throw new UnsupportedOperationException(); }
	public void setNoGravity(boolean noGravity) { throw new UnsupportedOperationException(); }
	public void setCustomName(Text name) { throw new UnsupportedOperationException(); }
	public void setCustomNameVisible(boolean visible) { throw new UnsupportedOperationException(); }
	public void setInvulnerable(boolean invulnerable) { throw new UnsupportedOperationException(); }
	public boolean isSpectator() { throw new UnsupportedOperationException(); }
	public boolean isSneaking() { throw new UnsupportedOperationException(); }
	public final float getHeight() { throw new UnsupportedOperationException(); }
	public boolean teleport(ServerWorld world, double x, double y, double z, Set<?> flags, float yaw, float pitch, boolean resetCamera) { throw new UnsupportedOperationException(); }
	public abstract boolean damage(ServerWorld world, DamageSource source, float amount);
}
