package net.fabricmc.fabric.api.event.player;
import net.fabricmc.fabric.api.event.Event;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.world.World;
public interface UseEntityCallback {
	Event<UseEntityCallback> EVENT = null;
	ActionResult interact(PlayerEntity player, World world, Hand hand, Entity entity, EntityHitResult hitResult);
}
