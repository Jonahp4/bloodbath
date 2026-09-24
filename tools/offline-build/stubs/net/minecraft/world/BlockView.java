package net.minecraft.world;
import net.minecraft.util.hit.BlockHitResult;
public interface BlockView { BlockHitResult raycast(RaycastContext context); }
