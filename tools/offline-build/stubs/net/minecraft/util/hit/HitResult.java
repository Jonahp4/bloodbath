package net.minecraft.util.hit;
import net.minecraft.util.math.Vec3d;
public abstract class HitResult {
	public abstract Type getType();
	public Vec3d getPos() { throw new UnsupportedOperationException(); }
	public enum Type { MISS, BLOCK, ENTITY }
}
