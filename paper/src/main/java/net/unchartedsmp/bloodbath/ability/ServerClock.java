package net.unchartedsmp.bloodbath.ability;

/**
 * Monotonic tick counter advanced once per server tick by the plugin's main task. All ability
 * timing (cooldowns, debuffs, scheduled effects) reads from here, never from world time.
 */
public final class ServerClock {
	private static long ticks;

	private ServerClock() {
	}

	public static long advance() {
		return ++ticks;
	}

	public static long now() {
		return ticks;
	}
}
