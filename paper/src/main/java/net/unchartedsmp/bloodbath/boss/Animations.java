package net.unchartedsmp.bloodbath.boss;

import net.unchartedsmp.bloodbath.boss.Clip.Ease;

/**
 * The Blood Knight's moves. Rotations are deltas on top of the modelled battle stance, in the
 * model's own frame (it faces -Z): +X swings an arm or leg forward and up and tips the chest or head
 * back (so -X hunches forward), +Y twists the chest to bring the sword shoulder forward and turns
 * the head to its left, +Z raises the right arm out to the side (-Z the left).
 *
 * <p>Every attack is built the same way: a wind-up that eases into its peak and hangs there for a
 * beat (the tell), a strike that snaps in, a follow-through that carries past, then a recovery. The
 * whole body takes part: legs brace and step, the free arm counterbalances, the body lunges and
 * crouches, the head keeps its eyes on the target. The tick each attack lands on is a constant
 * here, so the fight and its animation can't drift apart.
 */
final class Animations {
	static final int CLEAVE_HIT = 17;
	static final int SLAM_HIT = 24;
	static final int CAST_HIT = 18;
	static final int CHARGE_FROM = 14;
	static final int CHARGE_TO = 28;
	static final int LEAP_LAUNCH = 12;
	static final int GRASP_PULL = 14;
	static final int GRASP_SMASH = 27;
	static final int RAIN_FALL = 34;
	static final int WHIRL_FROM = 12;
	static final int WHIRL_TO = 52;
	static final int RISE_ROAR = 46;
	static final int LAST_STAND_ROAR = 44;
	private static final float SPIN = (float) (Math.PI * 6); // three full turns

	final Clip rise;
	final Clip roar;
	final Clip swipe;
	final Clip cleave;
	final Clip slam;
	final Clip cast;
	final Clip charge;
	final Clip leap;
	final Clip land;
	final Clip grasp;
	final Clip rain;
	final Clip whirl;
	final Clip flinch;
	final Clip stagger;
	final Clip taunt;
	final Clip lastStand;
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

		// Claws its way out: a hand breaks the surface, it heaves itself up, straightens and roars.
		rise = Clip.builder(rig, "rise", 72)
			.offset(0, 0, -3.4F, 0).offset(12, 0, -2.9F, 0, Ease.OUT).offset(24, 0, -2.0F, 0.1F, Ease.OUT)
			.offset(36, 0, -0.9F, 0.05F, Ease.OUT).offset(46, 0, 0.05F, 0, Ease.BACK).offset(60, 0, 0.08F, 0).offset(72, 0, 0, 0)
			.key("l_arm", 12, 2.6F, 0, -0.3F, Ease.OUT).key("l_arm", 24, 0.6F, 0, -0.5F, Ease.IN).key("l_arm", 36, 0.2F, 0, -0.8F)
			.key("l_arm", 46, -0.3F, 0, -1.15F, Ease.OUT).key("l_arm", 60, -0.3F, 0, -1.05F).key("l_arm", 72, 0, 0, 0)
			.key("r_arm", 14, 2.2F, 0, 0.4F, Ease.OUT).key("r_arm", 24, 0.4F, 0, 0.5F, Ease.IN).key("r_arm", 36, 0.2F, 0, 0.8F)
			.key("r_arm", 46, -0.3F, 0, 1.15F, Ease.OUT).key("r_arm", 60, -0.3F, 0, 1.05F).key("r_arm", 72, 0, 0, 0)
			.key("chest", 12, -0.2F, 0, 0).key("chest", 24, -0.6F, 0, 0, Ease.IN).key("chest", 36, -0.3F, 0, 0)
			.key("chest", 46, 0.35F, 0, 0, Ease.OUT).key("chest", 60, 0.3F, 0, 0).key("chest", 72, 0, 0, 0)
			.key("head", 24, -0.3F, 0, 0).key("head", 36, -0.3F, 0, 0).key("head", 46, 0.55F, 0, 0, Ease.OUT)
			.key("head", 50, 0.5F, 0.12F, 0).key("head", 54, 0.55F, -0.12F, 0).key("head", 60, 0.5F, 0, 0).key("head", 72, 0, 0, 0)
			.key("jaw", 40, 0.05F, 0, 0).key("jaw", 46, 0.6F, 0, 0, Ease.OUT).key("jaw", 60, 0.55F, 0, 0).key("jaw", 72, 0, 0, 0)
			.build();
		// Arms thrown wide, head back, the whole body shaking with it.
		roar = Clip.builder(rig, "roar", 44)
			.key("chest", 8, 0.45F, 0, 0, Ease.OUT).key("chest", 16, 0.49F, 0, 0).key("chest", 24, 0.43F, 0, 0).key("chest", 34, 0.46F, 0, 0).key("chest", 44, 0, 0, 0)
			.key("head", 8, 0.6F, 0, 0, Ease.OUT).key("head", 12, 0.58F, 0.14F, 0).key("head", 16, 0.62F, -0.14F, 0).key("head", 20, 0.58F, 0.12F, 0)
			.key("head", 24, 0.62F, -0.12F, 0).key("head", 34, 0.55F, 0, 0).key("head", 44, 0, 0, 0)
			.key("jaw", 8, 0.7F, 0, 0, Ease.OUT).key("jaw", 34, 0.65F, 0, 0).key("jaw", 44, 0, 0, 0)
			.key("l_arm", 8, -0.4F, 0, -1.2F, Ease.OUT).key("l_arm", 34, -0.35F, 0, -1.15F).key("l_arm", 44, 0, 0, 0)
			.key("r_arm", 8, -0.4F, 0, 1.2F, Ease.OUT).key("r_arm", 34, -0.35F, 0, 1.15F).key("r_arm", 44, 0, 0, 0)
			.key("l_forearm", 8, -0.4F, 0, 0).key("l_forearm", 44, 0, 0, 0)
			.key("r_forearm", 8, -0.4F, 0, 0).key("r_forearm", 44, 0, 0, 0)
			.offset(8, 0, 0.12F, 0.08F, Ease.OUT).offset(34, 0, 0.1F, 0.06F).offset(44, 0, 0, 0)
			.build();
		// Its ordinary melee hit: a short, vicious backhand.
		swipe = Clip.builder(rig, "swipe", 14)
			.key("r_arm", 5, -1.2F, 0, 0.4F, Ease.OUT).key("r_arm", 7, 0.9F, 0, -0.3F, Ease.IN).key("r_arm", 9, 1.0F, 0, -0.4F).key("r_arm", 14, 0, 0, 0)
			.key("chest", 5, 0, -0.45F, 0, Ease.OUT).key("chest", 7, -0.15F, 0.5F, 0, Ease.IN).key("chest", 9, -0.18F, 0.55F, 0).key("chest", 14, 0, 0, 0)
			.key("l_arm", 7, -0.3F, 0, -0.3F).key("l_arm", 14, 0, 0, 0)
			.offset(5, 0, 0, 0.1F).offset(7, 0, -0.05F, -0.25F, Ease.IN).offset(14, 0, 0, 0)
			.build();
		// Crimson Cleave: sword drawn out wide to the side, a beat of stillness, then a lunging
		// sweep across the body that carries through to the far side.
		cleave = Clip.builder(rig, "cleave", 34)
			.key("r_arm", 11, -0.3F, 0, 1.4F, Ease.OUT).key("r_arm", 14, -0.4F, 0, 1.45F, Ease.LINEAR).key("r_arm", CLEAVE_HIT, 0.9F, 0, -0.5F, Ease.IN)
			.key("r_arm", 22, 1.0F, 0, -0.8F, Ease.OUT).key("r_arm", 34, 0, 0, 0)
			.key("r_forearm", 11, -0.4F, 0, 0, Ease.OUT).key("r_forearm", CLEAVE_HIT, 0.2F, 0, 0, Ease.IN).key("r_forearm", 34, 0, 0, 0)
			.key("chest", 11, 0.1F, -0.55F, 0, Ease.OUT).key("chest", 14, 0.12F, -0.6F, 0, Ease.LINEAR).key("chest", CLEAVE_HIT, -0.3F, 0.7F, 0, Ease.IN)
			.key("chest", 22, -0.35F, 0.85F, 0, Ease.OUT).key("chest", 34, 0, 0, 0)
			.key("body", 11, 0, -0.15F, 0, Ease.OUT).key("body", CLEAVE_HIT, 0, 0.25F, 0, Ease.IN).key("body", 34, 0, 0, 0)
			.key("head", 11, 0, 0.4F, 0, Ease.OUT).key("head", CLEAVE_HIT, 0.05F, -0.35F, 0, Ease.IN).key("head", 34, 0, 0, 0)
			.key("l_arm", 11, 0.6F, 0, -0.3F, Ease.OUT).key("l_arm", CLEAVE_HIT, 0.1F, 0, -0.5F, Ease.IN).key("l_arm", 34, 0, 0, 0)
			.key("l_leg", 11, 0.45F, 0, 0).key("l_leg", CLEAVE_HIT, 0.6F, 0, 0, Ease.IN).key("l_leg", 34, 0, 0, 0)
			.key("r_leg", 11, -0.3F, 0, 0).key("r_leg", CLEAVE_HIT, -0.45F, 0, 0, Ease.IN).key("r_leg", 34, 0, 0, 0)
			.offset(11, 0, -0.08F, 0.15F, Ease.OUT).offset(CLEAVE_HIT, 0, -0.2F, -0.5F, Ease.IN).offset(22, 0, -0.22F, -0.58F, Ease.OUT)
			.offset(34, 0, 0, 0)
			.build();
		// Blood Slam: both arms high, up on its toes, a hang in the air, then everything comes down.
		slam = Clip.builder(rig, "slam", 44)
			.key("r_arm", 16, -2.7F, 0, 0.25F, Ease.OUT).key("r_arm", 20, -2.85F, 0, 0.25F, Ease.LINEAR).key("r_arm", SLAM_HIT, 0.9F, 0, 0, Ease.IN)
			.key("r_arm", 30, 0.85F, 0, 0, Ease.BACK).key("r_arm", 44, 0, 0, 0)
			.key("l_arm", 16, -2.5F, 0, -0.25F, Ease.OUT).key("l_arm", 20, -2.65F, 0, -0.25F, Ease.LINEAR).key("l_arm", SLAM_HIT, 0.8F, 0, 0, Ease.IN)
			.key("l_arm", 30, 0.75F, 0, 0, Ease.BACK).key("l_arm", 44, 0, 0, 0)
			.key("r_forearm", 16, -0.4F, 0, 0).key("r_forearm", SLAM_HIT, 0, 0, 0, Ease.IN).key("r_forearm", 44, 0, 0, 0)
			.key("l_forearm", 16, -0.3F, 0, 0).key("l_forearm", SLAM_HIT, 0, 0, 0, Ease.IN).key("l_forearm", 44, 0, 0, 0)
			.key("chest", 16, 0.35F, 0, 0, Ease.OUT).key("chest", 20, 0.38F, 0, 0).key("chest", SLAM_HIT, -0.65F, 0, 0, Ease.IN)
			.key("chest", 30, -0.6F, 0, 0, Ease.BACK).key("chest", 44, 0, 0, 0)
			.key("head", 16, 0.3F, 0, 0).key("head", SLAM_HIT, -0.25F, 0, 0, Ease.IN).key("head", 44, 0, 0, 0)
			.key("jaw", 16, 0.45F, 0, 0).key("jaw", SLAM_HIT, 0.2F, 0, 0).key("jaw", 44, 0, 0, 0)
			.key("l_leg", 16, -0.15F, 0, 0).key("l_leg", SLAM_HIT, 0.6F, 0, 0, Ease.IN).key("l_leg", 30, 0.55F, 0, 0).key("l_leg", 44, 0, 0, 0)
			.key("l_foreleg", SLAM_HIT, -0.9F, 0, 0, Ease.IN).key("l_foreleg", 30, -0.85F, 0, 0).key("l_foreleg", 44, 0, 0, 0)
			.key("r_leg", SLAM_HIT, -0.2F, 0, 0, Ease.IN).key("r_leg", 44, 0, 0, 0)
			.key("r_foreleg", SLAM_HIT, -0.5F, 0, 0, Ease.IN).key("r_foreleg", 44, 0, 0, 0)
			.offset(16, 0, 0.35F, 0.1F, Ease.OUT).offset(20, 0, 0.42F, 0.1F, Ease.LINEAR).offset(SLAM_HIT, 0, -0.55F, -0.35F, Ease.IN)
			.offset(30, 0, -0.5F, -0.33F, Ease.BACK).offset(44, 0, 0, 0)
			.build();
		// Blood Spikes: the sword raised to the sky with a howl, then driven into the ground on one knee.
		cast = Clip.builder(rig, "cast", 46)
			.key("r_arm", 12, -2.6F, 0, 0.2F, Ease.OUT).key("r_arm", 15, -2.75F, 0, 0.2F, Ease.LINEAR).key("r_arm", CAST_HIT, 0.35F, 0, 0, Ease.IN)
			.key("r_arm", 36, 0.35F, 0, 0).key("r_arm", 46, 0, 0, 0)
			.key("r_forearm", CAST_HIT, -0.5F, 0, 0, Ease.IN).key("r_forearm", 36, -0.5F, 0, 0).key("r_forearm", 46, 0, 0, 0)
			.key("l_arm", 12, 0, 0, -0.9F, Ease.OUT).key("l_arm", CAST_HIT, 0.3F, 0, -0.5F, Ease.IN).key("l_arm", 36, 0.3F, 0, -0.5F).key("l_arm", 46, 0, 0, 0)
			.key("chest", 12, 0.3F, 0, 0, Ease.OUT).key("chest", CAST_HIT, -0.7F, 0, 0, Ease.IN).key("chest", 24, -0.66F, 0, 0)
			.key("chest", 30, -0.72F, 0, 0).key("chest", 36, -0.68F, 0, 0).key("chest", 46, 0, 0, 0)
			.key("head", 12, 0.45F, 0, 0, Ease.OUT).key("head", CAST_HIT, -0.2F, 0, 0, Ease.IN).key("head", 36, -0.15F, 0, 0).key("head", 46, 0, 0, 0)
			.key("jaw", 12, 0.6F, 0, 0).key("jaw", CAST_HIT, 0.25F, 0, 0).key("jaw", 46, 0, 0, 0)
			.key("l_leg", CAST_HIT, 0.9F, 0, 0, Ease.IN).key("l_leg", 36, 0.9F, 0, 0).key("l_leg", 46, 0, 0, 0)
			.key("l_foreleg", CAST_HIT, -1.2F, 0, 0, Ease.IN).key("l_foreleg", 36, -1.2F, 0, 0).key("l_foreleg", 46, 0, 0, 0)
			.key("r_leg", CAST_HIT, -0.35F, 0, 0, Ease.IN).key("r_leg", 36, -0.35F, 0, 0).key("r_leg", 46, 0, 0, 0)
			.key("r_foreleg", CAST_HIT, -1.1F, 0, 0, Ease.IN).key("r_foreleg", 36, -1.1F, 0, 0).key("r_foreleg", 46, 0, 0, 0)
			.offset(12, 0, 0.1F, 0, Ease.OUT).offset(CAST_HIT, 0, -0.75F, -0.3F, Ease.IN).offset(36, 0, -0.72F, -0.28F).offset(46, 0, 0, 0)
			.build();
		// Crimson Charge: crouched behind the shield, then a thundering run with the shield leading.
		Clip.Builder run = Clip.builder(rig, "charge", 38)
			.key("l_arm", 12, 1.3F, 0, 0.2F, Ease.OUT).key("l_arm", CHARGE_TO, 1.3F, 0, 0.2F).key("l_arm", 38, 0, 0, 0)
			.key("r_arm", 12, -0.6F, 0, 0.3F, Ease.OUT).key("r_arm", CHARGE_TO, -0.7F, 0, 0.35F).key("r_arm", 38, 0, 0, 0)
			.key("chest", 12, -0.45F, -0.3F, 0, Ease.OUT).key("chest", CHARGE_TO, -0.55F, -0.35F, 0).key("chest", 31, -0.2F, -0.1F, 0, Ease.BACK)
			.key("chest", 38, 0, 0, 0)
			.key("head", 12, 0.2F, 0.25F, 0).key("head", CHARGE_TO, 0.25F, 0.3F, 0).key("head", 38, 0, 0, 0)
			.offset(12, 0, -0.2F, 0.25F, Ease.OUT).offset(CHARGE_FROM, 0, -0.25F, -0.1F, Ease.IN);
		for (int stride = 0; stride < 5; stride++) {
			float t = CHARGE_FROM + 2 + stride * 3;
			float s = stride % 2 == 0 ? 1.0F : -1.0F;
			run.key("l_leg", t, 0.7F * s, 0, 0).key("r_leg", t, -0.6F * s, 0, 0)
				.key("l_foreleg", t, s > 0 ? -0.2F : -0.9F, 0, 0).key("r_foreleg", t, s > 0 ? -0.9F : -0.2F, 0, 0)
				.offset(t, 0, stride % 2 == 0 ? -0.12F : -0.26F, -0.1F);
		}
		charge = run.key("l_leg", 31, 0.3F, 0, 0).key("r_leg", 31, -0.2F, 0, 0).key("l_leg", 38, 0, 0, 0).key("r_leg", 38, 0, 0, 0)
			.key("l_foreleg", 38, 0, 0, 0).key("r_foreleg", 38, 0, 0, 0)
			.offset(31, 0, -0.1F, -0.35F, Ease.BACK).offset(38, 0, 0, 0)
			.build();
		// Leap: a deep crouch, an explosive spring, sword overhead in the air. Holds until it lands.
		leap = Clip.builder(rig, "leap", 22).hold()
			.offset(10, 0, -0.55F, 0.15F, Ease.OUT).offset(LEAP_LAUNCH + 1, 0, 0.25F, -0.2F, Ease.IN).offset(22, 0, 0.1F, 0, Ease.OUT)
			.key("chest", 10, -0.5F, 0, 0, Ease.OUT).key("chest", LEAP_LAUNCH + 1, 0.25F, 0, 0, Ease.IN).key("chest", 22, 0.2F, 0, 0)
			.key("head", 10, 0.3F, 0, 0).key("head", 22, -0.2F, 0, 0)
			.key("r_arm", 10, -0.8F, 0, 0.4F, Ease.OUT).key("r_arm", LEAP_LAUNCH + 1, -2.6F, 0, 0.3F, Ease.IN).key("r_arm", 22, -2.8F, 0, 0.3F)
			.key("l_arm", 10, -0.6F, 0, -0.4F, Ease.OUT).key("l_arm", LEAP_LAUNCH + 1, 1.4F, 0, -0.3F, Ease.IN).key("l_arm", 22, 1.2F, 0, -0.4F)
			.key("l_leg", 10, 0.8F, 0, 0, Ease.OUT).key("l_leg", LEAP_LAUNCH + 1, -0.2F, 0, 0, Ease.IN).key("l_leg", 22, 0.7F, 0, 0)
			.key("l_foreleg", 10, -1.1F, 0, 0, Ease.OUT).key("l_foreleg", LEAP_LAUNCH + 1, 0.1F, 0, 0, Ease.IN).key("l_foreleg", 22, -1.0F, 0, 0)
			.key("r_leg", 10, 0.2F, 0, 0, Ease.OUT).key("r_leg", LEAP_LAUNCH + 1, -0.5F, 0, 0, Ease.IN).key("r_leg", 22, 0.3F, 0, 0)
			.key("r_foreleg", 10, -1.0F, 0, 0, Ease.OUT).key("r_foreleg", LEAP_LAUNCH + 1, 0.1F, 0, 0, Ease.IN).key("r_foreleg", 22, -0.9F, 0, 0)
			.build();
		// Landing from the leap: straight out of the airborne pose into a sword-first impact crouch.
		land = Clip.builder(rig, "land", 22)
			.startOffset(0, 0.1F, 0).start("chest", 0.2F, 0, 0).start("head", -0.2F, 0, 0).start("r_arm", -2.8F, 0, 0.3F)
			.start("l_arm", 1.2F, 0, -0.4F).start("l_leg", 0.7F, 0, 0).start("l_foreleg", -1.0F, 0, 0).start("r_leg", 0.3F, 0, 0)
			.start("r_foreleg", -0.9F, 0, 0)
			.offset(3, 0, -0.6F, -0.3F, Ease.IN).offset(8, 0, -0.52F, -0.28F, Ease.BACK).offset(22, 0, 0, 0)
			.key("chest", 3, -0.7F, 0, 0, Ease.IN).key("chest", 8, -0.62F, 0, 0, Ease.BACK).key("chest", 22, 0, 0, 0)
			.key("head", 3, -0.1F, 0, 0).key("head", 22, 0, 0, 0)
			.key("r_arm", 3, 0.9F, 0, -0.2F, Ease.IN).key("r_arm", 8, 0.85F, 0, -0.2F).key("r_arm", 22, 0, 0, 0)
			.key("l_arm", 3, 0.5F, 0, -0.6F, Ease.IN).key("l_arm", 22, 0, 0, 0)
			.key("l_leg", 3, 0.8F, 0, 0, Ease.IN).key("l_leg", 22, 0, 0, 0)
			.key("l_foreleg", 3, -1.2F, 0, 0, Ease.IN).key("l_foreleg", 22, 0, 0, 0)
			.key("r_leg", 3, -0.4F, 0, 0, Ease.IN).key("r_leg", 22, 0, 0, 0)
			.key("r_foreleg", 3, -1.0F, 0, 0, Ease.IN).key("r_foreleg", 22, 0, 0, 0)
			.build();
		// Blood Grasp: the sword levelled at its victim, a yank that drags them in, then a hammer blow.
		grasp = Clip.builder(rig, "grasp", 40)
			.key("r_arm", 10, 1.55F, 0, -0.1F, Ease.OUT).key("r_arm", GRASP_PULL, 1.6F, 0, -0.12F, Ease.LINEAR).key("r_arm", 18, -1.2F, 0, 0.5F, Ease.IN)
			.key("r_arm", 24, -2.5F, 0, 0.3F, Ease.OUT).key("r_arm", GRASP_SMASH, 0.9F, 0, -0.1F, Ease.IN).key("r_arm", 40, 0, 0, 0)
			.key("r_forearm", 10, 0.1F, 0, 0).key("r_forearm", 24, -0.4F, 0, 0).key("r_forearm", GRASP_SMASH, 0.2F, 0, 0, Ease.IN).key("r_forearm", 40, 0, 0, 0)
			.key("chest", 10, -0.2F, 0.45F, 0, Ease.OUT).key("chest", 18, 0.2F, -0.6F, 0, Ease.IN).key("chest", 24, 0.25F, -0.2F, 0, Ease.OUT)
			.key("chest", GRASP_SMASH, -0.6F, 0.3F, 0, Ease.IN).key("chest", 40, 0, 0, 0)
			.key("head", 10, 0, -0.3F, 0).key("head", 18, 0.1F, 0.3F, 0).key("head", 40, 0, 0, 0)
			.key("l_arm", 10, 0.3F, 0, -0.5F).key("l_arm", 18, 0.6F, 0, -0.3F).key("l_arm", 40, 0, 0, 0)
			.key("l_leg", 10, 0.3F, 0, 0).key("l_leg", 18, -0.1F, 0, 0).key("l_leg", GRASP_SMASH, 0.55F, 0, 0, Ease.IN).key("l_leg", 40, 0, 0, 0)
			.offset(10, 0, -0.1F, -0.15F, Ease.OUT).offset(18, 0, -0.05F, 0.3F, Ease.IN).offset(24, 0, 0.05F, 0.2F)
			.offset(GRASP_SMASH, 0, -0.35F, -0.45F, Ease.IN).offset(40, 0, 0, 0)
			.build();
		// Blood Rain: the sword raised to the sky, head thrown back, until the sky answers.
		rain = Clip.builder(rig, "rain", 50)
			.key("r_arm", 14, 2.4F, 0, 0.3F, Ease.OUT).key("r_arm", 22, 2.5F, 0, 0.35F).key("r_arm", 30, 2.4F, 0, 0.3F)
			.key("r_arm", RAIN_FALL, 2.5F, 0, 0.3F).key("r_arm", 38, 0.6F, 0, 0, Ease.IN).key("r_arm", 50, 0, 0, 0)
			.key("l_arm", 14, 0.3F, 0, -1.2F, Ease.OUT).key("l_arm", RAIN_FALL, 0.25F, 0, -1.25F).key("l_arm", 50, 0, 0, 0)
			.key("chest", 14, 0.4F, 0, 0, Ease.OUT).key("chest", 22, 0.44F, 0, 0).key("chest", 30, 0.38F, 0, 0)
			.key("chest", RAIN_FALL, 0.42F, 0, 0).key("chest", 38, -0.4F, 0, 0, Ease.IN).key("chest", 50, 0, 0, 0)
			.key("head", 14, 0.6F, 0, 0, Ease.OUT).key("head", RAIN_FALL, 0.62F, 0, 0).key("head", 38, -0.1F, 0, 0, Ease.IN).key("head", 50, 0, 0, 0)
			.key("jaw", 14, 0.65F, 0, 0).key("jaw", RAIN_FALL, 0.6F, 0, 0).key("jaw", 38, 0.2F, 0, 0).key("jaw", 50, 0, 0, 0)
			.offset(14, 0, 0.15F, 0, Ease.OUT).offset(RAIN_FALL, 0, 0.15F, 0).offset(38, 0, -0.2F, -0.2F, Ease.IN).offset(50, 0, 0, 0)
			.build();
		// Whirlwind: arms out, three full turns with the sword at arm's length.
		whirl = Clip.builder(rig, "whirl", 64)
			.key("r_arm", WHIRL_FROM, 0.2F, 0, 1.45F, Ease.OUT).key("r_arm", WHIRL_TO, 0.25F, 0, 1.4F).key("r_arm", 64, 0, 0, 0)
			.key("l_arm", WHIRL_FROM, 0.2F, 0, -1.2F, Ease.OUT).key("l_arm", WHIRL_TO, 0.2F, 0, -1.15F).key("l_arm", 64, 0, 0, 0)
			.key("chest", WHIRL_FROM, -0.25F, -0.9F, 0, Ease.OUT).key("chest", 16, -0.25F, 0.4F, 0, Ease.IN)
			.key("chest", WHIRL_TO, -0.25F, 0.4F, 0).key("chest", 64, 0, 0, 0)
			.key("body", WHIRL_FROM, 0, 0, 0).key("body", WHIRL_TO, 0, SPIN, 0, Ease.LINEAR).key("body", 64, 0, SPIN, 0)
			.key("l_leg", WHIRL_FROM, 0.25F, 0, 0).key("l_leg", WHIRL_TO, 0.25F, 0, 0).key("l_leg", 64, 0, 0, 0)
			.key("r_leg", WHIRL_FROM, -0.25F, 0, 0).key("r_leg", WHIRL_TO, -0.25F, 0, 0).key("r_leg", 64, 0, 0, 0)
			.offset(WHIRL_FROM, 0, -0.2F, 0, Ease.OUT).offset(WHIRL_TO, 0, -0.2F, 0).offset(64, 0, 0, 0)
			.build();
		flinch = Clip.builder(rig, "flinch", 10)
			.key("chest", 2, 0.3F, 0.1F, 0, Ease.OUT).key("chest", 10, 0, 0, 0)
			.key("head", 2, 0.35F, -0.1F, 0, Ease.OUT).key("head", 10, 0, 0, 0)
			.key("l_arm", 2, 0.3F, 0, -0.2F, Ease.OUT).key("l_arm", 10, 0, 0, 0)
			.offset(2, 0, 0, 0.15F, Ease.OUT).offset(10, 0, 0, 0)
			.build();
		// Reeling after a charge into a wall.
		stagger = Clip.builder(rig, "stagger", 30)
			.key("chest", 4, 0.5F, 0.3F, 0, Ease.OUT).key("chest", 10, 0.3F, -0.2F, 0).key("chest", 16, 0.35F, 0.15F, 0).key("chest", 30, 0, 0, 0)
			.key("head", 4, 0.5F, 0, 0, Ease.OUT).key("head", 16, 0.3F, 0.2F, 0).key("head", 30, 0, 0, 0)
			.key("l_arm", 4, 0, 0, -0.9F, Ease.OUT).key("l_arm", 30, 0, 0, 0)
			.key("r_arm", 4, 0, 0, 0.9F, Ease.OUT).key("r_arm", 30, 0, 0, 0)
			.key("l_leg", 10, -0.3F, 0, 0).key("l_leg", 30, 0, 0, 0)
			.offset(4, 0, 0, 0.35F, Ease.OUT).offset(16, 0, -0.1F, 0.3F).offset(30, 0, 0, 0)
			.build();
		// Between attacks, when its prey keeps its distance: the sword tip dragged along the ground,
		// two taps, a beckoning hand.
		taunt = Clip.builder(rig, "taunt", 48)
			.key("r_arm", 10, 0.3F, 0, 0.6F).key("r_arm", 14, 0.45F, 0, 0.5F, Ease.IN).key("r_arm", 18, 0.3F, 0, 0.62F, Ease.OUT)
			.key("r_arm", 22, 0.45F, 0, 0.5F, Ease.IN).key("r_arm", 38, 0.3F, 0, 0.6F).key("r_arm", 48, 0, 0, 0)
			.key("head", 10, 0, -0.4F, 0.25F).key("head", 38, 0, -0.35F, 0.2F).key("head", 48, 0, 0, 0)
			.key("chest", 10, 0, 0.3F, 0).key("chest", 38, 0, 0.3F, 0).key("chest", 48, 0, 0, 0)
			.key("l_arm", 20, 1.2F, 0, -0.2F, Ease.OUT).key("l_arm", 38, 1.2F, 0, -0.2F).key("l_arm", 48, 0, 0, 0)
			.key("l_forearm", 20, 0, 0, 0).key("l_forearm", 24, -1.0F, 0, 0).key("l_forearm", 28, 0, 0, 0)
			.key("l_forearm", 32, -1.0F, 0, 0).key("l_forearm", 36, 0, 0, 0)
			.key("jaw", 24, 0.3F, 0, 0).key("jaw", 30, 0.05F, 0, 0).key("jaw", 48, 0, 0, 0)
			.build();
		// Its last stand: down on one knee, shaking, then up with a roar that splits the air.
		lastStand = Clip.builder(rig, "last_stand", 70)
			.offset(12, 0, -0.8F, 0, Ease.OUT).offset(40, 0, -0.78F, 0).offset(LAST_STAND_ROAR + 4, 0, 0.2F, 0, Ease.OUT)
			.offset(60, 0, 0.15F, 0).offset(70, 0, 0, 0)
			.key("l_leg", 12, 1.0F, 0, 0, Ease.OUT).key("l_leg", 40, 1.0F, 0, 0).key("l_leg", LAST_STAND_ROAR + 4, 0, 0, 0, Ease.OUT)
			.key("l_foreleg", 12, -1.4F, 0, 0, Ease.OUT).key("l_foreleg", 40, -1.4F, 0, 0).key("l_foreleg", LAST_STAND_ROAR + 4, 0, 0, 0, Ease.OUT)
			.key("r_leg", 12, -0.4F, 0, 0, Ease.OUT).key("r_leg", 40, -0.4F, 0, 0).key("r_leg", LAST_STAND_ROAR + 4, 0, 0, 0, Ease.OUT)
			.key("r_foreleg", 12, -1.3F, 0, 0, Ease.OUT).key("r_foreleg", 40, -1.3F, 0, 0).key("r_foreleg", LAST_STAND_ROAR + 4, 0, 0, 0, Ease.OUT)
			.key("chest", 12, -0.7F, 0, 0, Ease.OUT).key("chest", 20, -0.65F, 0, 0).key("chest", 28, -0.73F, 0, 0).key("chest", 40, -0.68F, 0, 0)
			.key("chest", LAST_STAND_ROAR + 4, 0.55F, 0, 0, Ease.OUT).key("chest", 60, 0.5F, 0, 0).key("chest", 70, 0, 0, 0)
			.key("head", 12, -0.4F, 0, 0).key("head", 40, -0.45F, 0, 0).key("head", LAST_STAND_ROAR + 4, 0.7F, 0, 0, Ease.OUT)
			.key("head", 52, 0.66F, 0.14F, 0).key("head", 56, 0.7F, -0.14F, 0).key("head", 60, 0.66F, 0, 0).key("head", 70, 0, 0, 0)
			.key("jaw", 40, 0.1F, 0, 0).key("jaw", LAST_STAND_ROAR + 4, 0.75F, 0, 0, Ease.OUT).key("jaw", 60, 0.7F, 0, 0).key("jaw", 70, 0, 0, 0)
			.key("r_arm", 12, 0.5F, 0, 0).key("r_arm", 40, 0.5F, 0, 0).key("r_arm", LAST_STAND_ROAR + 4, -0.5F, 0, 1.3F, Ease.OUT)
			.key("r_arm", 60, -0.45F, 0, 1.25F).key("r_arm", 70, 0, 0, 0)
			.key("l_arm", 12, 0.6F, 0, -0.2F).key("l_arm", 40, 0.6F, 0, -0.2F).key("l_arm", LAST_STAND_ROAR + 4, -0.5F, 0, -1.3F, Ease.OUT)
			.key("l_arm", 60, -0.45F, 0, -1.25F).key("l_arm", 70, 0, 0, 0)
			.build();
		// Staggers back, drops to its knees, sways, then topples forward into its own blood. Holds.
		death = Clip.builder(rig, "death", 70).hold()
			.key("chest", 8, 0.5F, 0.2F, 0, Ease.OUT).key("chest", 22, -0.3F, 0, 0, Ease.IN).key("chest", 28, -0.4F, 0.1F, 0)
			.key("chest", 34, -0.35F, -0.05F, 0).key("chest", 70, -0.3F, 0, 0)
			.key("head", 8, 0.6F, 0, 0, Ease.OUT).key("head", 22, 0.2F, 0, 0).key("head", 34, -0.5F, 0, 0).key("head", 70, -0.4F, 0.3F, 0)
			.key("jaw", 8, 0.6F, 0, 0).key("jaw", 34, 0.4F, 0, 0).key("jaw", 70, 0.3F, 0, 0)
			.key("l_arm", 8, 0, 0, -0.6F, Ease.OUT).key("l_arm", 22, 0.4F, 0, -0.3F).key("l_arm", 50, 1.2F, 0, -0.5F, Ease.IN).key("l_arm", 70, 1.1F, 0, -0.6F)
			.key("r_arm", 8, 0, 0, 0.6F, Ease.OUT).key("r_arm", 22, 0.6F, 0, 0.3F).key("r_arm", 50, 1.2F, 0, 0.6F, Ease.IN).key("r_arm", 70, 1.1F, 0, 0.7F)
			.key("l_leg", 22, 1.0F, 0, 0, Ease.IN).key("l_leg", 50, 0.3F, 0, 0).key("l_leg", 70, 0.3F, 0, 0)
			.key("l_foreleg", 22, -1.4F, 0, 0, Ease.IN).key("l_foreleg", 50, -0.6F, 0, 0).key("l_foreleg", 70, -0.6F, 0, 0)
			.key("r_leg", 22, 0.9F, 0, 0, Ease.IN).key("r_leg", 50, 0.2F, 0, 0).key("r_leg", 70, 0.2F, 0, 0)
			.key("r_foreleg", 22, -1.4F, 0, 0, Ease.IN).key("r_foreleg", 50, -0.5F, 0, 0).key("r_foreleg", 70, -0.5F, 0, 0)
			.key("body", 34, 0, 0, 0).key("body", 50, -1.4F, 0, 0, Ease.IN).key("body", 56, -1.45F, 0, 0, Ease.BACK).key("body", 70, -1.45F, 0, 0)
			.offset(8, 0, 0, 0.25F, Ease.OUT).offset(22, 0, -0.7F, 0.1F, Ease.IN).offset(34, 0, -0.72F, 0.1F)
			.offset(50, 0, -1.3F, -0.9F, Ease.IN).offset(56, 0, -1.4F, -1.0F, Ease.BACK).offset(70, 0, -1.4F, -1.0F)
			.build();
		// Sinks back into the ground (a cancelled fight).
		retreat = Clip.builder(rig, "retreat", 50).hold()
			.key("head", 10, 0.3F, 0, 0).key("head", 50, 0.3F, 0, 0)
			.key("chest", 10, -0.3F, 0, 0).key("chest", 50, -0.3F, 0, 0)
			.offset(10, 0, 0.1F, 0).offset(50, 0, -3.4F, 0, Ease.IN)
			.build();
	}

	/**
	 * Always on underneath everything else: breathing, a slow weight shift from foot to foot, the
	 * sword tip swaying, the shield arm easing up and down, the jaw working.
	 */
	void idle(Pose pose, long now, float weight) {
		float t = now;
		float breath = (float) Math.sin(t * 0.09F);
		float shift = (float) Math.sin(t * 0.035F);
		pose.add(chest, -0.04F * breath, 0.04F * shift, 0, weight);
		pose.add(head, 0.035F * breath, 0.1F * (float) Math.sin(t * 0.031F), 0.03F * shift, weight);
		pose.add(jaw, 0.04F + 0.03F * breath + 0.05F * Math.max(0, (float) Math.sin(t * 0.21F) - 0.8F) * 5, 0, 0, weight);
		pose.add(lArm, 0.04F * shift, 0, -0.05F * breath, weight);
		pose.add(rArm, 0.04F * breath + 0.05F * (float) Math.sin(t * 0.05F), 0, 0.05F * breath, weight);
		pose.add(rForearm, 0.04F * (float) Math.sin(t * 0.05F + 1.0F), 0, 0, weight);
		pose.add(body, 0, 0, 0.03F * shift, weight);
		pose.add(lLeg, 0, 0, -0.03F * shift, weight);
		pose.add(rLeg, 0, 0, -0.03F * shift, weight);
		pose.addOffset(0.03F * shift, 0.02F * breath, 0, weight);
	}

	/**
	 * A heavy, stalking stride: long steps with the knee lifting, the body rolling side to side and
	 * bobbing down onto each foot, shoulders counter-twisting, the head holding steady on its prey.
	 */
	void walk(Pose pose, float phase, float weight) {
		float s = (float) Math.sin(phase);
		float c = (float) Math.cos(phase);
		pose.add(lLeg, 0.55F * s, 0, 0, weight);
		pose.add(rLeg, -0.55F * s, 0, 0, weight);
		pose.add(lForeleg, -0.55F * Math.max(0, c), 0, 0, weight);
		pose.add(rForeleg, -0.55F * Math.max(0, -c), 0, 0, weight);
		pose.add(lArm, -0.22F * s, 0, 0, weight);
		pose.add(rArm, 0.16F * s, 0, 0, weight);
		pose.add(lForearm, -0.1F * Math.max(0, s), 0, 0, weight);
		pose.add(rForearm, -0.12F * Math.max(0, -s), 0, 0, weight);
		pose.add(chest, -0.06F, 0.12F * s, 0.04F * s, weight);
		pose.add(body, 0, -0.07F * s, 0.05F * s, weight);
		pose.add(head, 0, -0.08F * s, -0.04F * s, weight);
		pose.addOffset(0.04F * s, 0.1F * Math.abs(c) - 0.07F, 0, weight);
	}

	/** Turns the head (and a little of the chest) toward something: yaw to its left, pitch down. */
	void lookAt(Pose pose, float yaw, float pitch, float weight) {
		pose.add(head, pitch * 0.8F, yaw * 0.7F, 0, weight);
		pose.add(chest, pitch * 0.2F, yaw * 0.3F, 0, weight);
	}

	int headBone() {
		return head;
	}
}
