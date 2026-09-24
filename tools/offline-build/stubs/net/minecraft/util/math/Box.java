package net.minecraft.util.math;
import java.util.Optional;
public class Box {
	public Box(double x1, double y1, double z1, double x2, double y2, double z2) { throw new UnsupportedOperationException(); }
	public static Box of(Vec3d center, double dx, double dy, double dz) { throw new UnsupportedOperationException(); }
	public Box expand(double value) { throw new UnsupportedOperationException(); }
	public Box offset(Vec3d vec) { throw new UnsupportedOperationException(); }
	public Box offset(double x, double y, double z) { throw new UnsupportedOperationException(); }
	public Optional<Vec3d> raycast(Vec3d min, Vec3d max) { throw new UnsupportedOperationException(); }
}
