package net.unchartedsmp.bloodbath.boss;

import java.util.Arrays;

/**
 * One frame of the Blood Knight's body: an XYZ Euler rotation (radians) per bone, relative to the
 * modelled pose, plus an offset for the whole body (bobbing, crouching, collapsing). Reused
 * every frame, never reallocated.
 */
public final class Pose {
	final float[] rotation;
	final float[] offset = new float[3];

	Pose(int bones) {
		rotation = new float[bones * 3];
	}

	void reset() {
		Arrays.fill(rotation, 0.0F);
		Arrays.fill(offset, 0.0F);
	}

	void add(int bone, float x, float y, float z, float weight) {
		if (bone < 0) {
			return;
		}
		rotation[bone * 3] += x * weight;
		rotation[bone * 3 + 1] += y * weight;
		rotation[bone * 3 + 2] += z * weight;
	}

	void addOffset(float x, float y, float z, float weight) {
		offset[0] += x * weight;
		offset[1] += y * weight;
		offset[2] += z * weight;
	}
}
