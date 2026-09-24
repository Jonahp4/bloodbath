package net.unchartedsmp.ability;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

/**
 * Monotonic server tick counter. All ability timing (cooldowns, debuffs, scheduled tasks) reads
 * from here instead of per-world time, so it can't be skewed by dimension changes or /time.
 */
public final class ServerClock {
	private static long ticks;

	private ServerClock() {
	}

	public static void register() {
		ServerTickEvents.START_SERVER_TICK.register(server -> ticks++);
	}

	public static long now() {
		return ticks;
	}
}
