package net.unchartedsmp.bloodbath.boss;

/**
 * The Blood Knight's moves. Rotations are deltas on top of the modelled battle stance, in the
 * model's own frame (it faces -Z): +X swings a hanging limb forward and tips a head back, Z
 * raises an arm sideways. Each clip starts and ends at rest so it blends over the idle cycle.
 */
final class Animations {
	final Clip rise;
	final Clip roar;
	final Clip swipe;
	final Clip cleave;
	final Clip slam;
	final Clip cast;
	final Clip charge;
	final Clip flinch;
	final Clip death;
	final Clip retreat;

	private final int body;
	private final int chest;
	private final int head;
	private final int jaw;
	private final int lArm;
	private final int rArm;
	private final int lForearm;
	private final int rForearm;
	private final int lLeg;
	private final int rLeg;
	private final int lForeleg;
	private final int rForeleg;

	Animations(Rig rig) {
		body = rig.bone("body");
		chest = rig.bone("chest");
		head = rig.bone("head");
		jaw = rig.bone("jaw");
		lArm = rig.bone("l_arm");
		rArm = rig.bone("r_arm");
		lForearm = rig.bone("l_forearm");
		rForearm = rig.bone("r_forearm");
		lLeg = rig.bone("l_leg");
		rLeg = rig.bone("r_leg");
		lForeleg = rig.bone("l_foreleg");
		rForeleg = rig.bone("r_foreleg");

		// Out of the ground, then straighten up with a roar.
		rise = Clip.builder(rig, "rise", 60)
			.offset(0, 0, -3.3F, 0).offset(34, 0, -0.25F, 0).offset(42, 0, 0.1F, 0).offset(60, 0, 0, 0)
			.key("chest", 30, 0.6F, 0, 0).key("chest", 44, -0.3F, 0, 0).key("chest", 60, 0, 0, 0)
			.key("head", 30, 0.5F, 0, 0).key("head", 44, -0.55F, 0, 0).key("head", 60, 0, 0, 0)
			.key("jaw", 38, 0, 0, 0).key("jaw", 44, 0.5F, 0, 0).key("jaw", 56, 0.4F, 0, 0).key("jaw", 60, 0, 0, 0)
			.key("l_arm", 30, 0.5F, 0, 0).key("l_arm", 44, 0, 0, -0.7F).key("l_arm", 60, 0, 0, 0)
			.key("r_arm", 30, 0.5F, 0, 0).key("r_arm", 44, 0, 0, 0.7F).key("r_arm", 60, 0, 0, 0)
			.build();
		roar = Clip.builder(rig, "roar", 40)
			.key("head", 8, -0.55F, 0, 0).key("head", 30, -0.5F, 0.1F, 0).key("head", 40, 0, 0, 0)
			.key("jaw", 8, 0.5F, 0, 0).key("jaw", 30, 0.45F, 0, 0).key("jaw", 40, 0, 0, 0)
			.key("chest", 8, -0.3F, 0, 0).key("chest", 30, -0.28F, 0, 0).key("chest", 40, 0, 0, 0)
			.key("l_arm", 8, 0, 0, -0.7F).key("l_arm", 30, 0, 0, -0.65F).key("l_arm", 40, 0, 0, 0)
			.key("r_arm", 8, 0, 0, 0.7F).key("r_arm", 30, 0, 0, 0.65F).key("r_arm", 40, 0, 0, 0)
			.build();
		// A quick sword swipe for its ordinary melee hits.
		swipe = Clip.builder(rig, "swipe", 12)
			.key("r_arm", 4, -1.2F, 0, 0.3F).key("r_arm", 7, 0.7F, 0, -0.2F).key("r_arm", 12, 0, 0, 0)
			.key("chest", 4, 0, 0.3F, 0).key("chest", 7, 0.1F, -0.35F, 0).key("chest", 12, 0, 0, 0)
			.build();
		// Crimson Cleave: sword raised high behind, then one huge sweep. Strike lands at tick 15.
		cleave = Clip.builder(rig, "cleave", 30)
			.key("r_arm", 13, -2.0F, 0, 0.5F).key("r_arm", 16, 0.9F, 0, -0.3F).key("r_arm", 22, 0.8F, 0, -0.25F).key("r_arm", 30, 0, 0, 0)
			.key("r_forearm", 13, -0.5F, 0, 0).key("r_forearm", 16, 0.2F, 0, 0).key("r_forearm", 30, 0, 0, 0)
			.key("chest", 13, 0, 0.5F, 0).key("chest", 16, 0.15F, -0.6F, 0).key("chest", 22, 0.12F, -0.55F, 0).key("chest", 30, 0, 0, 0)
			.key("head", 13, 0, -0.3F, 0).key("head", 16, 0, 0.3F, 0).key("head", 30, 0, 0, 0)
			.offset(13, 0, 0.05F, 0).offset(16, 0, -0.12F, 0).offset(30, 0, 0, 0)
			.build();
		// Blood Slam: both arms up, rise on the toes, then crash down. Impact at tick 21.
		slam = Clip.builder(rig, "slam", 40)
			.key("l_arm", 18, -2.3F, 0, -0.2F).key("l_arm", 22, 0.7F, 0, 0).key("l_arm", 30, 0.65F, 0, 0).key("l_arm", 40, 0, 0, 0)
			.key("r_arm", 18, -2.3F, 0, 0.2F).key("r_arm", 22, 0.7F, 0, 0).key("r_arm", 30, 0.65F, 0, 0).key("r_arm", 40, 0, 0, 0)
			.key("chest", 18, -0.25F, 0, 0).key("chest", 22, 0.45F, 0, 0).key("chest", 30, 0.4F, 0, 0).key("chest", 40, 0, 0, 0)
			.key("head", 18, -0.3F, 0, 0).key("head", 22, 0.2F, 0, 0).key("head", 40, 0, 0, 0)
			.offset(18, 0, 0.18F, 0).offset(22, 0, -0.28F, 0).offset(30, 0, -0.25F, 0).offset(40, 0, 0, 0)
			.build();
		// Blood Spikes: the sword raised straight up, then driven into the ground. Plunge at tick 16.
		cast = Clip.builder(rig, "cast", 44)
			.key("r_arm", 14, -2.6F, 0, 0.2F).key("r_arm", 17, 0.6F, 0, 0).key("r_arm", 36, 0.55F, 0, 0).key("r_arm", 44, 0, 0, 0)
			.key("chest", 14, -0.2F, 0.2F, 0).key("chest", 17, 0.35F, 0, 0).key("chest", 36, 0.3F, 0, 0).key("chest", 44, 0, 0, 0)
			.key("head", 14, -0.35F, 0, 0).key("head", 17, 0.25F, 0, 0).key("head", 44, 0, 0, 0)
			.offset(17, 0, -0.3F, 0).offset(36, 0, -0.28F, 0).offset(44, 0, 0, 0)
			.build();
		// Crimson Charge: shield up and forward, lean in for the dash (ticks 14-26).
		charge = Clip.builder(rig, "charge", 34)
			.key("l_arm", 12, -1.0F, 0, 0).key("l_arm", 26, -1.0F, 0, 0).key("l_arm", 34, 0, 0, 0)
			.key("chest", 12, 0.1F, -0.3F, 0).key("chest", 26, 0.25F, -0.3F, 0).key("chest", 34, 0, 0, 0)
			.key("body", 12, 0.05F, 0, 0).key("body", 16, 0.25F, 0, 0).key("body", 26, 0.25F, 0, 0).key("body", 34, 0, 0, 0)
			.key("head", 12, -0.2F, 0, 0).key("head", 26, -0.2F, 0, 0).key("head", 34, 0, 0, 0)
			.build();
		flinch = Clip.builder(rig, "flinch", 8)
			.key("chest", 2, -0.15F, 0, 0).key("chest", 8, 0, 0, 0)
			.key("head", 2, -0.12F, 0, 0).key("head", 8, 0, 0, 0)
			.build();
		// Stagger, fall to one knee, then collapse face down; holds the last frame.
		death = Clip.builder(rig, "death", 64).hold()
			.key("chest", 12, -0.35F, 0, 0).key("chest", 34, 0.25F, 0, 0).key("chest", 64, 0.15F, 0, 0)
			.key("head", 12, -0.4F, 0, 0).key("head", 34, 0.4F, 0, 0).key("head", 64, 0.3F, 0, 0)
			.key("jaw", 12, 0.45F, 0, 0).key("jaw", 64, 0.2F, 0, 0)
			.key("l_arm", 12, 0, 0, -0.5F).key("l_arm", 34, 0.4F, 0, 0).key("l_arm", 64, -0.6F, 0, -0.4F)
			.key("r_arm", 12, 0, 0, 0.5F).key("r_arm", 34, 0.4F, 0, 0).key("r_arm", 64, -0.6F, 0, 0.4F)
			.key("l_leg", 34, -1.0F, 0, 0).key("l_leg", 64, -0.5F, 0, 0)
			.key("l_foreleg", 34, 1.3F, 0, 0).key("l_foreleg", 64, 0.4F, 0, 0)
			.key("r_leg", 34, -0.4F, 0, 0).key("r_leg", 64, -0.3F, 0, 0)
			.key("body", 34, 0.3F, 0, 0).key("body", 54, 1.35F, 0, 0).key("body", 64, 1.35F, 0, 0)
			.offset(12, 0, 0.05F, 0.1F).offset(34, 0, -0.55F, 0).offset(54, 0, -1.35F, -0.6F).offset(64, 0, -1.35F, -0.6F)
			.build();
		// Sinks back into the ground (a cancelled fight).
		retreat = Clip.builder(rig, "retreat", 50).hold()
			.key("head", 10, 0.3F, 0, 0).key("head", 50, 0.3F, 0, 0)
			.offset(10, 0, 0.1F, 0).offset(50, 0, -3.4F, 0)
			.build();
	}

	/** Breathing, a slow look around and a little sway: always on underneath everything else. */
	void idle(Pose pose, long now, float weight) {
		float t = now;
		float breath = (float) Math.sin(t * 0.09F);
		pose.add(chest, -0.035F * breath, 0, 0, weight);
		pose.add(head, 0.03F * breath, 0.1F * (float) Math.sin(t * 0.031F), 0, weight);
		pose.add(jaw, 0.03F + 0.025F * breath, 0, 0, weight);
		pose.add(lArm, 0, 0, -0.04F * breath, weight);
		pose.add(rArm, 0.03F * breath, 0, 0.04F * breath, weight);
		pose.addOffset(0, 0.015F * breath, 0, weight);
	}

	/** A heavy stride: legs and arms swing opposite, the body bobs and twists with each step. */
	void walk(Pose pose, float phase, float weight) {
		float s = (float) Math.sin(phase);
		float c = (float) Math.cos(phase);
		pose.add(lLeg, 0.45F * s, 0, 0, weight);
		pose.add(rLeg, -0.45F * s, 0, 0, weight);
		pose.add(lForeleg, -0.3F * Math.max(0, c), 0, 0, weight);
		pose.add(rForeleg, -0.3F * Math.max(0, -c), 0, 0, weight);
		pose.add(lArm, -0.28F * s, 0, 0, weight);
		pose.add(rArm, 0.22F * s, 0, 0, weight);
		pose.add(lForearm, -0.1F * Math.max(0, s), 0, 0, weight);
		pose.add(rForearm, -0.1F * Math.max(0, -s), 0, 0, weight);
		pose.add(chest, 0.04F, 0.09F * s, 0, weight);
		pose.add(body, 0, -0.05F * s, 0, weight);
		pose.addOffset(0, 0.06F * Math.abs(c) - 0.03F, 0, weight);
	}
}
