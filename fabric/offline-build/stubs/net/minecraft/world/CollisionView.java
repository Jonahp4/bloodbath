package net.minecraft.world;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Box;
public interface CollisionView extends BlockView { boolean isSpaceEmpty(Entity entity, Box box); }
