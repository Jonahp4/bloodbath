package net.fabricmc.fabric.api.event.lifecycle.v1;
import net.fabricmc.fabric.api.event.Event;
import net.minecraft.entity.Entity;
import net.minecraft.server.world.ServerWorld;
public final class ServerEntityEvents {
	public static final Event<Load> ENTITY_LOAD = null;
	public static final Event<Unload> ENTITY_UNLOAD = null;
	@FunctionalInterface public interface Load { void onLoad(Entity entity, ServerWorld world); }
	@FunctionalInterface public interface Unload { void onUnload(Entity entity, ServerWorld world); }
}
