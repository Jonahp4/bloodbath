package net.unchartedsmp.ability;

import java.util.Comparator;
import java.util.PriorityQueue;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.unchartedsmp.UnchartedSMP;

/**
 * Delayed and repeating ability tasks, driven once per server tick.
 *
 * <p>The old scheduler scanned one flat list on every world tick (3+ times per server tick) and
 * abilities queued one lambda per tick (Mirrorfang alone queued 140). Tasks now live in a
 * priority queue ordered by due tick, so each tick only touches the tasks that are actually due,
 * and multi-tick effects use a single self-rescheduling {@link Repeating} task.
 */
public final class TickScheduler {
	@FunctionalInterface
	public interface Repeating {
		/**
		 * @param iteration 0-based iteration index
		 * @return false to stop early
		 */
		boolean tick(int iteration);
	}

	private record Task(long dueTick, long sequence, Runnable action) {
	}

	private static final PriorityQueue<Task> QUEUE = new PriorityQueue<>(
		Comparator.comparingLong(Task::dueTick).thenComparingLong(Task::sequence)
	);
	private static long nextSequence;
	private static boolean draining;

	private TickScheduler() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(TickScheduler::drain);
	}

	/** Runs {@code action} after {@code delayTicks}; 0 means "at the end of this tick". */
	public static void schedule(int delayTicks, Runnable action) {
		// Anything queued while draining is pushed to the next tick so a task can never starve the loop.
		long delay = Math.max(delayTicks, draining ? 1 : 0);
		QUEUE.add(new Task(ServerClock.now() + delay, nextSequence++, action));
	}

	/** Runs {@code body} {@code iterations} times, {@code intervalTicks} apart, starting after {@code initialDelay}. */
	public static void repeat(int initialDelay, int intervalTicks, int iterations, Repeating body) {
		if (iterations > 0) {
			step(initialDelay, Math.max(1, intervalTicks), iterations, body, 0);
		}
	}

	private static void step(int delay, int interval, int iterations, Repeating body, int iteration) {
		schedule(delay, () -> {
			if (body.tick(iteration) && iteration + 1 < iterations) {
				step(interval, interval, iterations, body, iteration + 1);
			}
		});
	}

	private static void drain(MinecraftServer server) {
		long now = ServerClock.now();
		draining = true;
		try {
			Task task;
			while ((task = QUEUE.peek()) != null && task.dueTick() <= now) {
				QUEUE.poll();
				try {
					task.action().run();
				} catch (RuntimeException e) {
					UnchartedSMP.LOGGER.error("Uncharted SMP ability task failed", e);
				}
			}
		} finally {
			draining = false;
		}
	}

	public static void clearAll() {
		QUEUE.clear();
	}
}
