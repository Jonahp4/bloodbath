package net.unchartedsmp.bloodbath.boss;

import java.util.ArrayList;
import java.util.List;

/**
 * A keyframed animation: per-bone rotation keys (and body offset keys) at tick times. Each key
 * says how the motion arrives at it ({@link Ease}): a wind-up eases out into its peak and hangs
 * there, a strike snaps in, a follow-through overshoots and settles, a spin runs linear. Clips start
 * and end at rest unless they {@link #holds()} their last frame (death), so they layer cleanly over
 * the idle and walk cycles.
 */
public final class Clip {
	/** How the motion into a key is paced. */
	enum Ease {
		/** Accelerate, then settle (the default). */
		SMOOTH,
		/** Start slow and hit hard: strikes, slams, the moment of impact. */
		IN,
		/** Burst out and decelerate into the pose: wind-ups, recoveries. */
		OUT,
		/** Overshoot the pose and settle back: follow-throughs, landings. */
		BACK,
		/** Constant speed: spins. */
		LINEAR;

		float apply(float u) {
			return switch (this) {
				case SMOOTH -> u * u * (3.0F - 2.0F * u);
				case IN -> u * u * u;
				case OUT -> 1.0F - (1.0F - u) * (1.0F - u) * (1.0F - u);
				case BACK -> {
					float s = 1.70158F * 1.3F;
					float v = u - 1.0F;
					yield 1.0F + v * v * ((s + 1.0F) * v + s);
				}
				case LINEAR -> u;
			};
		}
	}

	private record Key(float time, float x, float y, float z, Ease ease) {
	}

	private record Track(int bone, List<Key> keys) {
	}

	private final String name;
	private final int duration;
	private final boolean hold;
	private final List<Track> tracks;
	private final List<Key> offsetKeys;

	private Clip(String name, int duration, boolean hold, List<Track> tracks, List<Key> offsetKeys) {
		this.name = name;
		this.duration = duration;
		this.hold = hold;
		this.tracks = List.copyOf(tracks);
		this.offsetKeys = List.copyOf(offsetKeys);
	}

	public String name() {
		return name;
	}

	public int duration() {
		return duration;
	}

	/** Keeps its last frame after it ends (the death collapse). */
	public boolean holds() {
		return hold;
	}

	/** Scratch for {@link #sample}; animation runs on the main thread only. */
	private static final float[] SAMPLE = new float[3];

	void apply(Pose pose, float time, float weight) {
		float t = hold ? Math.min(time, duration) : time;
		for (Track track : tracks) {
			sample(track.keys(), t);
			pose.add(track.bone(), SAMPLE[0], SAMPLE[1], SAMPLE[2], weight);
		}
		if (!offsetKeys.isEmpty()) {
			sample(offsetKeys, t);
			pose.addOffset(SAMPLE[0], SAMPLE[1], SAMPLE[2], weight);
		}
	}

	private static void sample(List<Key> keys, float t) {
		Key last = keys.get(keys.size() - 1);
		if (t >= last.time()) {
			SAMPLE[0] = last.x();
			SAMPLE[1] = last.y();
			SAMPLE[2] = last.z();
			return;
		}
		for (int i = 1; i < keys.size(); i++) {
			Key b = keys.get(i);
			if (t <= b.time()) {
				Key a = keys.get(i - 1);
				float u = Math.max(0.0F, (t - a.time()) / Math.max(1.0E-3F, b.time() - a.time()));
				u = b.ease().apply(u);
				SAMPLE[0] = a.x() + (b.x() - a.x()) * u;
				SAMPLE[1] = a.y() + (b.y() - a.y()) * u;
				SAMPLE[2] = a.z() + (b.z() - a.z()) * u;
				return;
			}
		}
	}

	static Builder builder(Rig rig, String name, int duration) {
		return new Builder(rig, name, duration);
	}

	static final class Builder {
		private final Rig rig;
		private final String name;
		private final int duration;
		private boolean hold;
		private final List<Track> tracks = new ArrayList<>();
		private final List<Key> offsetKeys = new ArrayList<>();

		private Builder(Rig rig, String name, int duration) {
			this.rig = rig;
			this.name = name;
			this.duration = duration;
		}

		/** A rotation key for a bone at a tick (radians, XYZ), eased smoothly. Keys go in time order. */
		Builder key(String bone, float time, float x, float y, float z) {
			return key(bone, time, x, y, z, Ease.SMOOTH);
		}

		/** A rotation key reached with the given pacing. */
		Builder key(String bone, float time, float x, float y, float z, Ease ease) {
			int index = rig.bone(bone);
			if (index < 0) {
				return this; // a rig without this bone just doesn't move it
			}
			Track track = tracks.stream().filter(t -> t.bone() == index).findFirst().orElse(null);
			if (track == null) {
				track = new Track(index, new ArrayList<>());
				track.keys().add(new Key(0, 0, 0, 0, Ease.SMOOTH));
				tracks.add(track);
			}
			track.keys().add(new Key(time, x, y, z, ease));
			return this;
		}

		/**
		 * Where a bone starts instead of rest: for a clip that picks up from another's last frame
		 * (the landing after a leap), so there's no snap between them. Call before its keys.
		 */
		Builder start(String bone, float x, float y, float z) {
			int index = rig.bone(bone);
			if (index < 0) {
				return this;
			}
			Track track = new Track(index, new ArrayList<>());
			track.keys().add(new Key(0, x, y, z, Ease.SMOOTH));
			tracks.removeIf(t -> t.bone() == index);
			tracks.add(track);
			return this;
		}

		/** The body offset a clip starts from (see {@link #start}). */
		Builder startOffset(float x, float y, float z) {
			offsetKeys.clear();
			offsetKeys.add(new Key(0, x, y, z, Ease.SMOOTH));
			return this;
		}

		/** Body offset key (blocks, in the model's frame: -Z is forward). */
		Builder offset(float time, float x, float y, float z) {
			return offset(time, x, y, z, Ease.SMOOTH);
		}

		Builder offset(float time, float x, float y, float z, Ease ease) {
			if (offsetKeys.isEmpty()) {
				offsetKeys.add(new Key(0, 0, 0, 0, Ease.SMOOTH));
			}
			offsetKeys.add(new Key(time, x, y, z, ease));
			return this;
		}

		Builder hold() {
			hold = true;
			return this;
		}

		Clip build() {
			return new Clip(name, duration, hold, tracks, offsetKeys);
		}
	}
}
