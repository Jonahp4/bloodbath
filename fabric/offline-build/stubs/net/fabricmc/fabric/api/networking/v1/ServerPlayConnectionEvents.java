package net.fabricmc.fabric.api.networking.v1;
import net.fabricmc.fabric.api.event.Event;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayNetworkHandler;
public final class ServerPlayConnectionEvents {
	public static final Event<Disconnect> DISCONNECT = null;
	@FunctionalInterface public interface Disconnect { void onPlayDisconnect(ServerPlayNetworkHandler handler, MinecraftServer server); }
}
