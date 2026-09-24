package net.fabricmc.fabric.api.event.lifecycle.v1;
import net.fabricmc.fabric.api.event.Event;
import net.minecraft.server.MinecraftServer;
public final class ServerTickEvents {
	public static final Event<StartTick> START_SERVER_TICK = null;
	public static final Event<EndTick> END_SERVER_TICK = null;
	@FunctionalInterface public interface StartTick { void onStartTick(MinecraftServer server); }
	@FunctionalInterface public interface EndTick { void onEndTick(MinecraftServer server); }
}
