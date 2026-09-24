package net.fabricmc.fabric.api.event.player;
import net.fabricmc.fabric.api.event.Event;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
public interface AttackBlockCallback {
	Event<AttackBlockCallback> EVENT = null;
	ActionResult interact(PlayerEntity player, World world, Hand hand, BlockPos pos, Direction direction);
}
