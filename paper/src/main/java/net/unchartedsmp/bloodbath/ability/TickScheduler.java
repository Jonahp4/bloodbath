package net.unchartedsmp.bloodbath.ability;

import java.util.Comparator;
import java.util.PriorityQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Delayed and repeating ability effects, drained once per server tick.
 *
 * <p>A priority queue ordered by due tick means each tick only touches what is actually due, and
 * multi-tick effects are a single self-rescheduling {@link Repeating} task rather than one Bukkit
 * task per tick. A failing effect is logged instead of breaking the loop.
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
	private static Logger logger = Logger.getLogger("Bloodbath");

	private TickScheduler() {
	}

	public static void setLogger(Logger pluginLogger) {
		logger = pluginLogger;
	}

	/** Runs {@code action} after {@code delayTicks}; 0 means "later this tick". */
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

	/** Called once per tick by the plugin's main task. */
	public static void drain() {
		long now = ServerClock.now();
		draining = true;
		try {
			Task task;
			while ((task = QUEUE.peek()) != null && task.dueTick() <= now) {
				QUEUE.poll();
				try {
					task.action().run();
				} catch (RuntimeException e) {
					logger.log(Level.SEVERE, "Bloodbath ability effect failed", e);
				}
			}
		} finally {
			draining = false;
		}
	}

	public static int pending() {
		return QUEUE.size();
	}

	public static void clearAll() {
		QUEUE.clear();
	}
}
