package net.minecraft.world;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
public class RaycastContext {
	public RaycastContext(Vec3d start, Vec3d end, ShapeType shapeType, FluidHandling fluidHandling, Entity entity) { throw new UnsupportedOperationException(); }
	public enum ShapeType { COLLIDER, OUTLINE }
	public enum FluidHandling { NONE }
}
