package net.unchartedsmp.bloodbath.boss;

import com.google.common.collect.ImmutableMultimap;
import com.destroystokyo.paper.entity.Pathfinder;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import net.unchartedsmp.bloodbath.Keys;
import net.unchartedsmp.bloodbath.armor.ArmorPiece;
import net.unchartedsmp.bloodbath.armor.BloodArmor;
import net.unchartedsmp.bloodbath.blood.BloodDrop;
import net.unchartedsmp.bloodbath.config.Settings;
import net.unchartedsmp.bloodbath.core.BloodCore;
import net.unchartedsmp.bloodbath.fx.BloodFx;
import net.unchartedsmp.bloodbath.fx.Particles;
import net.unchartedsmp.bloodbath.pack.PackState;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Ravager;
import org.bukkit.entity.WitherSkeleton;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * One Blood Knight fight, from the moment it breaks the ground to its last bell.
 *
 * <p>The Knight is three things. Its <b>body</b> is an invisible ravager scaled to the model's
 * footprint (2.4 blocks wide at scale 1): that's what collides with walls, pathfinds, takes hits
 * and swings its sword, so the model can't walk into walls and hits land where the model is. The
 * <b>model</b> ({@link BossModel}) is drawn over the body for players with the resource pack. For
 * everyone else a <b>stand-in</b>, a netherite wither skeleton with no mind of its own, walks
 * where the body walks; players with the pack never receive it, and a hit on it counts as a hit
 * on the body.
 *
 * <p>The fight has three phases. Bloodied (60% health): faster, harder, Blood Rain. Last stand
 * (25%): it kneels, rises roaring, raises thralls that halve the damage it takes while they stand,
 * and fights with Whirlwind and lifesteal. It drinks from anyone it kills, never takes more than a
 * capped amount from one hit, shrugs off half of every arrow, regenerates if left alone, and goes
 * berserk if the fight drags on. Every attack is telegraphed on the ground and has a counter; the
 * sword, the shockwaves and the charge are stopped by walls, but the ground (spikes) and the sky
 * (rain) don't care where you hide.
 *
 * <p>Everything runs from {@link #tick}, called by the {@link BossManager} once a tick, so there
 * are no tasks of its own to leak: when the fight ends, the manager simply stops calling it.
 */
final class BloodKnightBoss {
	enum State {
		RISING, FIGHTING, ATTACKING, ENRAGING, LAST_STAND, DYING, RETREATING, DONE
	}

	enum Attack {
		CLEAVE, SLAM, SPIKES, CHARGE, LEAP, GRASP, RAIN, WHIRLWIND
	}

	private static final int ENRAGE_TICKS = 44;
	private static final int DEATH_TICKS = 150;
	private static final int RETREAT_TICKS = 52;
	private static final int BLEED_SECONDS = 6;
	private static final int MAX_BLEED_STACKS = 6;
	private static final double CLEAVE_RADIUS = 6.0;
	private static final double CLEAVE_HALF_ANGLE = Math.toRadians(70);
	private static final double SLAM_RADIUS = 10.0;
	private static final double CHARGE_LENGTH = 16.0;
	private static final double LEAP_RADIUS = 4.5;
	private static final double WHIRL_RADIUS = 4.5;
	private static final double GRASP_RANGE = 22.0;
	private static final double RAIN_RADIUS = 2.0;
	/** The body: a ravager (1.95 wide) scaled to this width at scale 1, about the model's footprint. */
	private static final double RAVAGER_WIDTH = 1.95;
	private static final double BODY_WIDTH = 2.4;
	/** The stand-in: a wither skeleton (2.4 tall) scaled to the model's 3 blocks. */
	private static final double SKELETON_HEIGHT = 2.4;
	private static final double MODEL_HEIGHT = 3.0;
	/** The sword's tip and the middle of its blade, in the sword hand's frame (model units). */
	private static final Vector3f SWORD_TIP = new Vector3f(0.012F, -1.32F, -1.594F);

	private final BossManager manager;
	private final Plugin plugin;
	private final Settings.BossSettings config;
	private final Rig rig;
	private final Animations animations;
	private final Location center;
	private final World world;
	private final Ravager brain;
	private final WitherSkeleton puppet;
	private final BossModel model;
	private final Pose pose;
	private final Pose trailPose;
	private final Matrix4f[] trailBones;
	private final Matrix4f trailRoot = new Matrix4f();
	private final Vector3f scratch = new Vector3f();
	private final int swordHand;
	private final float scale;
	private final BossBar packBar;
	private final BossBar plainBar;
	private final Set<UUID> barViewers = new HashSet<>();
	private final Set<UUID> modelViewers = new HashSet<>();
	/** Who has been shown (true) or hidden from (false) the stand-in. */
	private final Map<UUID, Boolean> puppetViewers = new HashMap<>();
	private final Set<UUID> participants = new HashSet<>();
	private final Set<UUID> struck = new HashSet<>();
	private final List<Location> spots = new ArrayList<>();
	private final List<WitherSkeleton> thralls = new ArrayList<>();
	/** Bleeding players: stacks and the tick it stops. */
	private final Map<UUID, int[]> bleeding = new HashMap<>();

	private State state = State.RISING;
	private long stateStart;
	private Attack attack;
	private Attack lastAttack;
	private Player target;
	private float attackYaw;
	private long nextAttack;
	private Clip action;
	private long actionStart;
	private float modelYaw;
	private float walkPhase;
	private float walkWeight;
	private float lookYaw;
	private float lookPitch;
	private float lastStepSign;
	private float sink;
	private Location feet;
	private int phase = 1;
	private boolean berserk;
	private long fightStart;
	private long lastHurt;
	private long lastFlinch;
	private int unreachableTicks;
	private long emptySince = -1;
	private String killer;
	// the attack in progress
	private Location leapSpot;
	private boolean landed;
	private int landedAt;
	private boolean grasped;
	// the sword trail
	private Location lastTip;
	private float lastTipTime;
	private Clip trailClip;

	BloodKnightBoss(BossManager manager, Plugin plugin, Rig rig, Animations animations, Location at) {
		this.manager = manager;
		this.plugin = plugin;
		this.config = Settings.get().boss;
		this.rig = rig;
		this.animations = animations;
		this.center = at.clone();
		this.world = at.getWorld();
		this.scale = (float) config.scale();
		this.pose = new Pose(rig.bones().size());
		this.trailPose = new Pose(rig.bones().size());
		this.trailBones = new Matrix4f[rig.bones().size()];
		for (int i = 0; i < trailBones.length; i++) {
			trailBones[i] = new Matrix4f();
		}
		this.swordHand = rig.bone("r_hand");
		long now = manager.now();
		this.stateStart = now;
		this.modelYaw = at.getYaw();
		this.feet = at.clone();
		this.brain = world.spawn(at, Ravager.class, this::configureBody);
		this.puppet = world.spawn(at, WitherSkeleton.class, this::configureStandIn);
		this.model = new BossModel(rig, plugin, at, scale, config.animationInterval());
		this.packBar = BossBar.bossBar(title(), 1.0F, BossBar.Color.YELLOW, BossBar.Overlay.PROGRESS);
		this.plainBar = BossBar.bossBar(title(), 1.0F, BossBar.Color.RED, BossBar.Overlay.NOTCHED_10);
		play(animations.rise, now);
		BossFx.emerge(at);
		updateViewers(now);
	}

	private void configureBody(Ravager body) {
		body.setPersistent(false);
		body.setRemoveWhenFarAway(false);
		body.setCanPickupItems(false);
		body.setSilent(true);
		body.setInvisible(true);
		body.setInvulnerable(true);
		body.setAware(false);
		body.setCanJoinRaid(false);
		body.customName(Component.text("Blood Knight", NamedTextColor.DARK_RED));
		body.setCustomNameVisible(false);
		body.getPersistentDataContainer().set(Keys.BOSS, PersistentDataType.BYTE, (byte) 1);
		base(body, Attribute.MAX_HEALTH, config.health());
		body.setHealth(config.health());
		base(body, Attribute.ARMOR, config.armor());
		base(body, Attribute.ARMOR_TOUGHNESS, config.armorToughness());
		base(body, Attribute.ATTACK_DAMAGE, config.meleeDamage());
		base(body, Attribute.ATTACK_KNOCKBACK, 1.2);
		base(body, Attribute.KNOCKBACK_RESISTANCE, 1.0);
		base(body, Attribute.MOVEMENT_SPEED, 0.3);
		base(body, Attribute.FOLLOW_RANGE, config.arenaRadius() + 16);
		base(body, Attribute.STEP_HEIGHT, 1.1);
		base(body, Attribute.SCALE, BODY_WIDTH * scale / RAVAGER_WIDTH);
	}

	/** What players without the resource pack see: the Knight's armour, sword and shield, walking. */
	private void configureStandIn(WitherSkeleton skeleton) {
		skeleton.setPersistent(false);
		skeleton.setRemoveWhenFarAway(false);
		skeleton.setCanPickupItems(false);
		skeleton.setAI(false);
		skeleton.setSilent(true);
		skeleton.setInvisible(true);
		skeleton.setCollidable(false);
		skeleton.setGravity(false);
		skeleton.setVisibleByDefault(false);
		skeleton.customName(Component.text("Blood Knight", NamedTextColor.DARK_RED));
		skeleton.setCustomNameVisible(false);
		skeleton.getPersistentDataContainer().set(Keys.BOSS, PersistentDataType.BYTE, (byte) 1);
		base(skeleton, Attribute.SCALE, MODEL_HEIGHT * scale / SKELETON_HEIGHT);
		EntityEquipment gear = skeleton.getEquipment();
		gear.setHelmet(new ItemStack(Material.NETHERITE_HELMET), true);
		gear.setChestplate(new ItemStack(Material.NETHERITE_CHESTPLATE), true);
		gear.setLeggings(new ItemStack(Material.NETHERITE_LEGGINGS), true);
		gear.setBoots(new ItemStack(Material.NETHERITE_BOOTS), true);
		ItemStack sword = new ItemStack(Material.NETHERITE_SWORD);
		sword.editMeta(meta -> meta.setAttributeModifiers(ImmutableMultimap.of()));
		gear.setItemInMainHand(sword, true);
		gear.setItemInOffHand(new ItemStack(Material.SHIELD), true);
		for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HAND, EquipmentSlot.OFF_HAND, EquipmentSlot.HEAD, EquipmentSlot.CHEST,
			EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
			gear.setDropChance(slot, 0.0F);
		}
	}

	private static void base(LivingEntity entity, Attribute attribute, double value) {
		AttributeInstance instance = entity.getAttribute(attribute);
		if (instance != null) {
			instance.setBaseValue(value);
		}
	}

	// ---- queries ------------------------------------------------------------------------------

	Ravager brain() {
		return brain;
	}

	WitherSkeleton puppet() {
		return puppet;
	}

	boolean isThrall(Entity entity) {
		return entity instanceof WitherSkeleton skeleton && thralls.contains(skeleton);
	}

	Location center() {
		return center;
	}

	World world() {
		return world;
	}

	State state() {
		return state;
	}

	int phase() {
		return phase;
	}

	boolean done() {
		return state == State.DONE;
	}

	boolean inArena(Location location) {
		return location.getWorld() == world && location.distanceSquared(center) <= config.arenaRadius() * config.arenaRadius();
	}

	boolean vulnerable() {
		return state == State.FIGHTING || state == State.ATTACKING;
	}

	private double maxHealth() {
		AttributeInstance max = brain.getAttribute(Attribute.MAX_HEALTH);
		return max == null ? config.health() : max.getValue();
	}

	double healthFraction() {
		return brain.isDead() ? 0.0 : brain.getHealth() / maxHealth();
	}

	/** Thralls are standing: the Knight takes half damage. */
	boolean shielded() {
		thralls.removeIf(thrall -> !thrall.isValid() || thrall.isDead());
		return !thralls.isEmpty();
	}

	// ---- the loop -------------------------------------------------------------------------------

	void tick(long now) {
		if (state == State.DONE) {
			return;
		}
		boolean alive = brain.isValid() && !brain.isDead();
		if (!alive && state != State.DYING && state != State.RETREATING) {
			// Removed by something else, or its chunk unloaded: nothing left to fight.
			end();
			return;
		}
		if (alive) {
			feet = brain.getLocation();
		}
		followWithStandIn();
		if (now % 10 == 0) {
			updateViewers(now);
		}
		if (now % 20 == 0) {
			bleedTick(now);
		}
		switch (state) {
			case RISING -> {
				long t = now - stateStart;
				if (t == Animations.RISE_ROAR) {
					BossFx.roar(feet);
				}
				if (t >= animations.rise.duration()) {
					enter(State.FIGHTING, now);
					brain.setInvulnerable(false);
					brain.setAware(true);
					scaleHealth();
					fightStart = now;
					lastHurt = now;
					nextAttack = now + 40;
				}
			}
			case FIGHTING -> fight(now);
			case ATTACKING -> {
				if (attackTick((int) (now - stateStart), now)) {
					finishAttack(now);
				}
			}
			case ENRAGING -> enrageTick(now);
			case LAST_STAND -> lastStandTick(now);
			case DYING -> dyingTick(now);
			case RETREATING -> {
				long t = now - stateStart;
				BossFx.retreat(feet, (int) t);
				if (t >= RETREAT_TICKS) {
					end();
					return;
				}
			}
			default -> {
			}
		}
		if (state != State.DONE && now % config.animationInterval() == 0 && Particles.anyoneNear(world, feet.getX(), feet.getY(), feet.getZ(), 96)) {
			animate(now);
		}
	}

	private void enter(State next, long now) {
		state = next;
		stateStart = now;
	}

	/** The stand-in walks where the body walks, turned the way the model faces. */
	private void followWithStandIn() {
		if (!puppet.isValid()) {
			return;
		}
		// While it rises it comes up out of the ground with the model.
		double lift = state == State.RISING ? pose.offset[1] * scale : sink;
		Location at = feet.clone().add(0, lift, 0);
		at.setYaw(modelYaw);
		at.setPitch(0.0F);
		puppet.teleport(at);
		puppet.setBodyYaw(modelYaw);
	}

	/** Once it's up: more health for every extra player in the arena, up to four times as much. */
	private void scaleHealth() {
		int fighters = 0;
		for (Player player : world.getPlayers()) {
			if (fightable(player)) {
				fighters++;
			}
		}
		double max = Math.min(config.health() * 4.0, config.health() * (1.0 + config.healthPerPlayer() * Math.max(0, fighters - 1)));
		base(brain, Attribute.MAX_HEALTH, max);
		brain.setHealth(max);
	}

	private void fight(long now) {
		if (now % 10 == 0) {
			BossFx.aura(feet, phase >= 2);
		}
		if (config.atmosphere() && now % 10 == 5) {
			for (UUID id : participants) {
				Player player = Bukkit.getPlayer(id);
				if (player != null && inArena(player.getLocation())) {
					BossFx.atmosphere(player.getLocation(), now);
				}
			}
		}
		if (phase == 1 && healthFraction() <= config.phaseTwoAt()) {
			enrage(now);
			return;
		}
		if (phase == 2 && healthFraction() <= config.phaseThreeAt()) {
			beginLastStand(now);
			return;
		}
		if (!berserk && config.berserkAfterTicks() > 0 && now - fightStart >= config.berserkAfterTicks()) {
			goBerserk();
		}
		if (config.regen() > 0 && now - lastHurt > 300 && now % 20 == 0 && brain.getHealth() < maxHealth()) {
			heal(maxHealth() * config.regen());
		}
		if (now % 20 == 0 && shielded()) {
			for (WitherSkeleton thrall : thralls) {
				BossFx.thrallLink(thrall.getLocation(), feet);
			}
		}
		if (feet.distanceSquared(center) > sq(config.arenaRadius())) {
			// Leashed to its arena: back to the middle.
			brain.getPathfinder().moveTo(center, 1.3);
		}
		Player hunted = pickTarget();
		if (hunted == null) {
			if (emptySince < 0) {
				emptySince = now;
			} else if (now - emptySince >= config.leaveTimeoutTicks()) {
				retreat(now, "sinks back into the earth. Nobody was left to fight it.");
			}
			return;
		}
		emptySince = -1;
		if (brain.getTarget() != hunted) {
			brain.setTarget(hunted);
		}
		if (now % 20 == 0) {
			unreachableTicks = reachable(hunted) ? 0 : unreachableTicks + 20;
		}
		if (now >= nextAttack && action == null) {
			startAttack(hunted, chooseAttack(hunted), now);
		} else if (action == null && nextAttack - now > 30 && hunted.getLocation().distanceSquared(feet) > 144
			&& ThreadLocalRandom.current().nextInt(150) == 0) {
			play(animations.taunt, now);
		}
	}

	/** Whether it can walk to its prey: not perched far above it, and a path exists. */
	private boolean reachable(Player hunted) {
		if (hunted.getLocation().getY() - feet.getY() > 3.0) {
			return false;
		}
		Pathfinder.PathResult path = brain.getPathfinder().findPath(hunted);
		return path != null && path.canReachFinalPoint();
	}

	private Player pickTarget() {
		if (brain.getTarget() instanceof Player current && fightable(current)) {
			return current;
		}
		Player best = null;
		double bestDistance = Double.MAX_VALUE;
		for (UUID id : participants) {
			Player player = Bukkit.getPlayer(id);
			if (player != null && fightable(player)) {
				double distance = player.getLocation().distanceSquared(feet);
				if (distance < bestDistance) {
					best = player;
					bestDistance = distance;
				}
			}
		}
		return best;
	}

	private boolean fightable(Player player) {
		return player.isValid() && !player.isDead() && player.getWorld() == world && inArena(player.getLocation())
			&& (player.getGameMode() == GameMode.SURVIVAL || player.getGameMode() == GameMode.ADVENTURE);
	}

	// ---- choosing an attack ---------------------------------------------------------------------

	private Attack chooseAttack(Player hunted) {
		ThreadLocalRandom random = ThreadLocalRandom.current();
		double distance = Math.hypot(hunted.getLocation().getX() - feet.getX(), hunted.getLocation().getZ() - feet.getZ());
		boolean sight = clearPath(chest(), hunted.getEyeLocation());
		boolean headroom = roofOver(feet, 8) == null; // no leaping under a roof: it would only hit its head
		if (unreachableTicks >= 60) {
			// Hiding up a pillar, in a hole, behind something it can't path round: drag them out, or
			// reach them from below and above.
			if (sight && distance <= GRASP_RANGE) {
				return Attack.GRASP;
			}
			return phase >= 2 && random.nextBoolean() ? Attack.RAIN : Attack.SPIKES;
		}
		Map<Attack, Double> weights = new EnumMap<>(Attack.class);
		if (distance <= CLEAVE_RADIUS * scale) {
			weights.put(Attack.CLEAVE, 45.0);
			weights.put(Attack.SLAM, crowdNear(8.0) >= 2 ? 50.0 : 25.0);
			if (phase >= 3) {
				weights.put(Attack.WHIRLWIND, 35.0);
			}
		} else if (distance <= 16.0) {
			if (sight) {
				weights.put(Attack.CHARGE, 30.0);
			}
			if (headroom) {
				weights.put(Attack.LEAP, 25.0);
			}
			weights.put(Attack.SPIKES, 20.0);
			if (sight && distance >= 8.0) {
				weights.put(Attack.GRASP, 15.0);
			}
			if (phase >= 2) {
				weights.put(Attack.RAIN, 20.0);
			}
			if (phase >= 3) {
				weights.put(Attack.WHIRLWIND, 10.0);
			}
		} else {
			if (distance <= 24.0 && headroom) {
				weights.put(Attack.LEAP, 35.0);
			}
			weights.put(Attack.SPIKES, 30.0);
			if (sight && distance <= GRASP_RANGE) {
				weights.put(Attack.GRASP, 20.0);
			}
			if (phase >= 2) {
				weights.put(Attack.RAIN, 25.0);
			}
		}
		if (lastAttack != null && weights.containsKey(lastAttack) && weights.size() > 1) {
			weights.put(lastAttack, weights.get(lastAttack) * 0.25);
		}
		double total = weights.values().stream().mapToDouble(Double::doubleValue).sum();
		double roll = random.nextDouble() * total;
		for (Map.Entry<Attack, Double> entry : weights.entrySet()) {
			roll -= entry.getValue();
			if (roll <= 0) {
				return entry.getKey();
			}
		}
		return Attack.SPIKES;
	}

	private int crowdNear(double radius) {
		int count = 0;
		for (UUID id : participants) {
			Player player = Bukkit.getPlayer(id);
			if (player != null && fightable(player) && player.getLocation().distanceSquared(feet) <= radius * radius) {
				count++;
			}
		}
		return count;
	}

	private void startAttack(Player hunted, Attack choice, long now) {
		attack = choice;
		lastAttack = choice;
		target = hunted;
		struck.clear();
		spots.clear();
		landed = false;
		grasped = false;
		leapSpot = hunted.getLocation();
		enter(State.ATTACKING, now);
		brain.setAware(false);
		brain.setVelocity(new Vector());
		attackYaw = yawTowards(feet, hunted.getLocation());
		brain.setRotation(attackYaw, 0);
		play(switch (choice) {
			case CLEAVE -> animations.cleave;
			case SLAM -> animations.slam;
			case SPIKES -> animations.cast;
			case CHARGE -> animations.charge;
			case LEAP -> animations.leap;
			case GRASP -> animations.grasp;
			case RAIN -> animations.rain;
			case WHIRLWIND -> animations.whirl;
		}, now);
		BloodFx.play(feet, BossFx.GROWL, 1.0F, choice == Attack.SLAM || choice == Attack.LEAP ? 0.5F : 0.8F);
	}

	private void finishAttack(long now) {
		enter(State.FIGHTING, now);
		brain.setAware(true);
		double factor = (phase >= 3 ? 0.5 : phase == 2 ? 0.7 : 1.0) * (berserk ? 0.6 : 1.0);
		int cooldown = Math.max(6, (int) (config.attackCooldownTicks() * factor));
		nextAttack = now + cooldown + ThreadLocalRandom.current().nextInt(Math.max(1, cooldown / 3));
		if (phase >= 2 && attack == Attack.CLEAVE && ThreadLocalRandom.current().nextDouble() < 0.35) {
			nextAttack = now + 4; // a bloodied Knight follows a cleave straight into something else
		}
	}

	// ---- the attacks ----------------------------------------------------------------------------

	/** One tick of the current attack; true when it's over. */
	private boolean attackTick(int t, long now) {
		double yaw = Math.toRadians(attackYaw);
		// Minecraft yaw 0 faces +Z; the arc helper measures angles from +X.
		double facing = Math.atan2(Math.cos(yaw), -Math.sin(yaw));
		double dx = -Math.sin(yaw);
		double dz = Math.cos(yaw);
		return switch (attack) {
			case CLEAVE -> {
				double radius = CLEAVE_RADIUS * scale;
				if (t < Animations.CLEAVE_HIT - 2 && t % 2 == 0) {
					BossFx.cleaveTelegraph(feet, facing, radius, CLEAVE_HALF_ANGLE, t / (float) (Animations.CLEAVE_HIT - 2));
				}
				if (t == Animations.CLEAVE_HIT) {
					BossFx.cleaveStrike(feet, facing, radius, CLEAVE_HALF_ANGLE);
					for (Player player : victims(radius + 0.5, chest())) {
						Vector to = player.getLocation().toVector().subtract(feet.toVector()).setY(0);
						double angle = Math.atan2(to.getZ(), to.getX());
						if (Math.abs(wrap(angle - facing)) <= CLEAVE_HALF_ANGLE + 0.15 || to.lengthSquared() < 3.0) {
							strike(player, config.cleaveDamage(), knockback(to, 1.3, 0.45));
						}
					}
				}
				yield t >= animations.cleave.duration();
			}
			case SLAM -> {
				double reach = SLAM_RADIUS * scale;
				if (t < Animations.SLAM_HIT) {
					BossFx.slamWindup(feet, reach, t);
				}
				if (t == Animations.SLAM_HIT) {
					BossFx.slamImpact(feet);
				}
				if (t >= Animations.SLAM_HIT && t <= Animations.SLAM_HIT + 12) {
					double radius = 1.0 + (t - Animations.SLAM_HIT) / 12.0 * (reach - 1.0);
					BossFx.slamWave(feet, radius);
					Location ground = feet.clone().add(0, 0.6, 0);
					for (Player player : victims(radius + 1.0, ground)) {
						Vector to = player.getLocation().toVector().subtract(feet.toVector());
						double flat = Math.hypot(to.getX(), to.getZ());
						boolean grounded = player.getLocation().getY() - feet.getY() < 0.7 && onGround(player);
						if (flat >= radius - 1.1 && grounded && struck.add(player.getUniqueId())) {
							strike(player, config.slamDamage(), knockback(to.setY(0), 0.9, 1.0));
							player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 1, false, true, true));
						}
					}
				}
				yield t >= animations.slam.duration();
			}
			case SPIKES -> {
				if (t == 0) {
					List<Player> marked = new ArrayList<>(victims(config.arenaRadius(), null));
					Collections.shuffle(marked);
					int count = phase >= 3 ? 6 : phase == 2 ? 4 : 3;
					for (Player player : marked.subList(0, Math.min(marked.size(), count))) {
						spots.add(player.getLocation());
					}
				}
				if (t <= 30 && t % 2 == 0) {
					for (Location spot : spots) {
						BossFx.spikeTelegraph(spot, t / 30.0F);
					}
				}
				if (t == Animations.CAST_HIT) {
					BloodFx.play(feet, BossFx.SPIKE, 1.2F, 0.5F);
					BloodFx.burst(feet, BloodFx.SPLATTER, 16, 0.6, 0.3);
				}
				if (t == 30) {
					for (Location spot : spots) {
						BossFx.spikeErupt(spot);
						for (Player player : victims(config.arenaRadius(), null)) {
							Location at = player.getLocation();
							if (Math.hypot(at.getX() - spot.getX(), at.getZ() - spot.getZ()) <= 1.5 && Math.abs(at.getY() - spot.getY()) < 2.5
								&& struck.add(player.getUniqueId())) {
								strike(player, config.spikeDamage(), new Vector(0, 1.1, 0));
							}
						}
					}
				}
				yield t >= animations.cast.duration();
			}
			case CHARGE -> {
				if (t < 12 && t % 3 == 0) {
					BossFx.chargeTelegraph(feet, yaw, CHARGE_LENGTH);
				}
				if (t == 12) {
					BloodFx.play(feet, BossFx.CHARGE, 1.3F, 0.7F);
					BloodFx.play(feet, BossFx.STEP, 1.2F, 0.6F);
				}
				if (t >= Animations.CHARGE_FROM && t <= Animations.CHARGE_TO) {
					Vector velocity = brain.getVelocity();
					if (t > Animations.CHARGE_FROM + 3 && Math.hypot(velocity.getX(), velocity.getZ()) < 0.15) {
						// Hit a wall: it reels.
						BossFx.hit(feet.clone().add(dx * 1.4, 1.2, dz * 1.4));
						BloodFx.play(feet, BossFx.HEAVY_HIT, 1.3F, 0.5F);
						play(animations.stagger, now);
						nextAttack = now + 30;
						yield true;
					}
					brain.setVelocity(new Vector(dx * 1.05, Math.min(velocity.getY(), 0.0), dz * 1.05));
					BossFx.chargeTrail(feet);
					for (Player player : victims(1.6 * scale + 0.8, chest())) {
						if (struck.add(player.getUniqueId())) {
							strike(player, config.chargeDamage(), new Vector(dx * 1.4, 0.6, dz * 1.4));
						}
					}
				}
				yield t >= animations.charge.duration();
			}
			case LEAP -> {
				double radius = LEAP_RADIUS * scale;
				if (t <= 8 && target != null && fightable(target)) {
					leapSpot = target.getLocation(); // it tracks you right up until it jumps
				}
				if (!landed && t % 2 == 0) {
					BossFx.leapTelegraph(leapSpot, radius, Math.min(1.0F, t / 24.0F));
				}
				if (t == Animations.LEAP_LAUNCH) {
					Vector toSpot = leapSpot.toVector().subtract(feet.toVector()).setY(0);
					double distance = Math.min(22.0, toSpot.length());
					Vector flat = toSpot.lengthSquared() < 1.0E-4 ? new Vector() : toSpot.normalize().multiply(distance / 9.5);
					brain.setVelocity(new Vector(flat.getX(), 0.85 + Math.max(0.0, leapSpot.getY() - feet.getY()) * 0.08, flat.getZ()));
					BossFx.leapLaunch(feet);
				}
				if (!landed && t > Animations.LEAP_LAUNCH + 3 && (brain.isOnGround() || t > Animations.LEAP_LAUNCH + 40)) {
					landed = true;
					landedAt = t;
					play(animations.land, now);
					BossFx.leapImpact(feet, radius);
					for (Player player : victims(radius, feet.clone().add(0, 1.0, 0))) {
						Vector away = player.getLocation().toVector().subtract(feet.toVector());
						strike(player, config.leapDamage(), knockback(away, 1.1, 0.6));
					}
				}
				yield landed && t >= landedAt + 20;
			}
			case GRASP -> {
				Location blade = chest().add(dx * 1.8, 0.2, dz * 1.8);
				boolean holding = target != null && fightable(target) && clearPath(chest(), target.getEyeLocation())
					&& target.getLocation().distance(feet) <= GRASP_RANGE + 4.0;
				if (t < Animations.GRASP_PULL && t % 2 == 0 && holding) {
					BossFx.graspTelegraph(blade, BloodFx.chest(target), t / (float) Animations.GRASP_PULL);
				}
				if (t == 2) {
					BloodFx.play(feet, BossFx.TETHER, 1.3F, 0.6F);
				}
				if (t == Animations.GRASP_PULL) {
					if (!holding) {
						yield true; // line of sight broken: the tether snaps
					}
					grasped = true;
					Vector pull = feet.toVector().subtract(target.getLocation().toVector());
					double flat = Math.hypot(pull.getX(), pull.getZ());
					Vector velocity = pull.setY(0).normalize().multiply(Math.min(2.4, 0.4 + flat * 0.13)).setY(0.45);
					BossFx.graspPull(blade, BloodFx.chest(target));
					strike(target, config.graspDamage(), new Vector());
					target.setVelocity(velocity);
				}
				if (t == Animations.GRASP_SMASH && grasped) {
					BossFx.slamImpact(feet.clone().add(dx * 2.0, 0, dz * 2.0));
					for (Player player : victims(CLEAVE_RADIUS * scale, chest())) {
						Vector to = player.getLocation().toVector().subtract(feet.toVector()).setY(0);
						double angle = Math.atan2(to.getZ(), to.getX());
						if (Math.abs(wrap(angle - facing)) <= Math.toRadians(60) || to.lengthSquared() < 3.0) {
							strike(player, config.cleaveDamage() * 0.8, knockback(to, 0.8, 0.9));
						}
					}
				}
				yield t >= animations.grasp.duration();
			}
			case RAIN -> {
				if (t == 0) {
					ThreadLocalRandom random = ThreadLocalRandom.current();
					for (Player player : victims(config.arenaRadius(), null)) {
						spots.add(player.getLocation());
						for (int i = 0; i < 2 && spots.size() < 14; i++) {
							double angle = random.nextDouble() * Math.PI * 2;
							double r = 1.5 + random.nextDouble() * 2.5;
							spots.add(player.getLocation().add(Math.cos(angle) * r, 0, Math.sin(angle) * r));
						}
					}
					BloodFx.play(feet, BossFx.RAIN_CALL, 1.4F, 0.6F);
				}
				if (t < Animations.RAIN_FALL && t % 3 == 0) {
					for (Location spot : spots) {
						BossFx.rainTelegraph(spot, RAIN_RADIUS, t / (float) Animations.RAIN_FALL);
					}
				}
				if (t == Animations.RAIN_FALL) {
					for (Location spot : spots) {
						Location roof = roofOver(spot, 7);
						BossFx.rainSplash(spot, roof);
						if (roof != null) {
							continue; // sheltered
						}
						for (Player player : victims(config.arenaRadius(), null)) {
							Location at = player.getLocation();
							if (Math.hypot(at.getX() - spot.getX(), at.getZ() - spot.getZ()) <= RAIN_RADIUS && Math.abs(at.getY() - spot.getY()) < 2.5
								&& roofOver(at, 7) == null && struck.add(player.getUniqueId())) {
								strike(player, config.rainDamage(), new Vector(0, -0.3, 0));
							}
						}
					}
				}
				yield t >= animations.rain.duration();
			}
			case WHIRLWIND -> {
				double radius = WHIRL_RADIUS * scale;
				if (t >= Animations.WHIRL_FROM && t <= Animations.WHIRL_TO) {
					if (target != null && fightable(target)) {
						Vector toward = target.getLocation().toVector().subtract(feet.toVector()).setY(0);
						if (toward.lengthSquared() > 1.0) {
							toward.normalize().multiply(0.17);
							brain.setVelocity(new Vector(toward.getX(), Math.min(brain.getVelocity().getY(), 0.0), toward.getZ()));
						}
					}
					if (t % 2 == 0) {
						BossFx.whirlBeat(feet, radius, t);
					}
					if ((t - Animations.WHIRL_FROM) % 8 == 0) {
						for (Player player : victims(radius, chest())) {
							Vector away = player.getLocation().toVector().subtract(feet.toVector());
							strike(player, config.whirlwindDamage(), knockback(away, 0.8, 0.3));
						}
					}
				}
				yield t >= animations.whirl.duration();
			}
		};
	}

	/** Players an attack from {@code from} can hurt: in the arena, fighting, and (if from isn't null) in its line of sight. */
	private List<Player> victims(double radius, Location from) {
		List<Player> out = new ArrayList<>();
		for (Player player : world.getPlayers()) {
			if (fightable(player) && player.getLocation().distanceSquared(feet) <= radius * radius
				&& (from == null || clearPath(from, BloodFx.chest(player)))) {
				out.add(player);
			}
		}
		return out;
	}

	/** Nothing solid on the straight line between two points (walls stop the sword and the shockwaves). */
	private boolean clearPath(Location from, Location to) {
		double x = from.getX();
		double y = from.getY();
		double z = from.getZ();
		double ddx = to.getX() - x;
		double ddy = to.getY() - y;
		double ddz = to.getZ() - z;
		double length = Math.sqrt(ddx * ddx + ddy * ddy + ddz * ddz);
		if (length < 0.5) {
			return true;
		}
		int steps = (int) Math.ceil(length / 0.4);
		for (int i = 1; i < steps; i++) {
			double f = i / (double) steps;
			Block block = world.getBlockAt((int) Math.floor(x + ddx * f), (int) Math.floor(y + ddy * f), (int) Math.floor(z + ddz * f));
			if (block.getType().isSolid()) {
				return false;
			}
		}
		return true;
	}

	/** The first solid block above a spot, within {@code height}, or null: what shelters it from the rain. */
	private Location roofOver(Location spot, int height) {
		int x = spot.getBlockX();
		int z = spot.getBlockZ();
		for (int y = spot.getBlockY() + 2; y <= spot.getBlockY() + height; y++) {
			if (world.getBlockAt(x, y, z).getType().isSolid()) {
				return new Location(world, x + 0.5, y, z + 0.5);
			}
		}
		return null;
	}

	private void strike(Player player, double amount, Vector push) {
		double damage = amount * (phase >= 3 ? 1.35 : phase == 2 ? 1.2 : 1.0) * (berserk ? 1.5 : 1.0);
		double before = player.getHealth();
		if (brain.isValid()) {
			player.damage(damage, DamageSource.builder(DamageType.MOB_ATTACK).withCausingEntity(brain).withDirectEntity(brain).build());
		} else {
			player.damage(damage);
		}
		player.setVelocity(player.getVelocity().add(push));
		BossFx.hit(BloodFx.chest(player));
		participants.add(player.getUniqueId());
		if (config.bleedStacks() > 0) {
			addBleed(player, config.bleedStacks());
		}
		if (phase >= 3 && config.lifesteal() > 0) {
			double dealt = before - Math.max(0.0, player.getHealth());
			if (dealt > 0) {
				heal(dealt * config.lifesteal());
			}
		}
	}

	private static Vector knockback(Vector away, double strength, double up) {
		Vector flat = away.clone().setY(0);
		if (flat.lengthSquared() < 1.0E-4) {
			flat = new Vector(1, 0, 0);
		}
		return flat.normalize().multiply(strength).setY(up);
	}

	private Location chest() {
		return feet.clone().add(0, 1.6 * scale, 0);
	}

	// ---- bleeding ---------------------------------------------------------------------------------

	private void addBleed(Player player, int stacks) {
		int[] bleed = bleeding.computeIfAbsent(player.getUniqueId(), id -> new int[2]);
		bleed[0] = Math.min(MAX_BLEED_STACKS, bleed[0] + stacks);
		bleed[1] = (int) (manager.now() + BLEED_SECONDS * 20L);
	}

	/** Once a second: every bleeding player loses stacks x bleed damage, straight past their armour. */
	private void bleedTick(long now) {
		Iterator<Map.Entry<UUID, int[]>> it = bleeding.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<UUID, int[]> entry = it.next();
			Player player = Bukkit.getPlayer(entry.getKey());
			if (player == null || !player.isValid() || player.isDead() || player.getWorld() != world || now > entry.getValue()[1]) {
				it.remove();
				continue;
			}
			bleedHit(player, entry.getValue()[0] * config.bleedDamage());
			BossFx.bleed(BloodFx.chest(player));
		}
	}

	/** Magic damage that doesn't hand out invulnerability frames (it would swallow the next real hit). */
	private void bleedHit(Player player, double amount) {
		if (amount <= 0) {
			return;
		}
		int frames = player.getNoDamageTicks();
		double last = player.getLastDamage();
		player.setNoDamageTicks(0);
		DamageSource.Builder source = DamageSource.builder(DamageType.MAGIC);
		if (brain.isValid()) {
			source.withCausingEntity(brain);
		}
		player.damage(amount, source.build());
		player.setNoDamageTicks(frames);
		player.setLastDamage(last);
	}

	private void heal(double amount) {
		if (!brain.isValid() || brain.isDead()) {
			return;
		}
		brain.setHealth(Math.min(maxHealth(), brain.getHealth() + amount));
		float progress = (float) Math.max(0.0, Math.min(1.0, healthFraction()));
		packBar.progress(progress);
		plainBar.progress(progress);
	}

	// ---- incoming damage ----------------------------------------------------------------------------

	/**
	 * Shapes every hit it takes: arrows and tridents do less, thralls halve everything while they
	 * stand, and no single hit takes off more than the cap.
	 */
	void shapeIncoming(EntityDamageEvent event) {
		double damage = event.getDamage();
		if (event instanceof EntityDamageByEntityEvent byEntity && byEntity.getDamager() instanceof Projectile) {
			damage *= config.projectileDamage();
		}
		if (shielded()) {
			damage *= 0.5;
		}
		if (config.damageCap() > 0) {
			damage = Math.min(damage, config.damageCap());
		}
		event.setDamage(damage);
	}

	/** A player without the pack hit the stand-in: that's a hit on the Knight. */
	void hitThroughStandIn(Player player, double damage) {
		if (!vulnerable() || !brain.isValid() || brain.isDead() || damage <= 0) {
			return;
		}
		brain.damage(damage, DamageSource.builder(DamageType.PLAYER_ATTACK).withCausingEntity(player).withDirectEntity(player).build());
	}

	// ---- phase two ----------------------------------------------------------------------------

	private void enrage(long now) {
		phase = 2;
		enter(State.ENRAGING, now);
		brain.setAware(false);
		brain.setInvulnerable(true);
		play(animations.roar, now);
		base(brain, Attribute.MOVEMENT_SPEED, 0.34);
		renameBars(Component.text("· Bloodied ☠", NamedTextColor.RED));
		plainBar.color(BossBar.Color.PURPLE);
	}

	private void enrageTick(long now) {
		long t = now - stateStart;
		if (t == 8) {
			BossFx.roar(feet);
			for (Player player : victims(config.arenaRadius(), null)) {
				if (player.getLocation().distanceSquared(feet) < 49) {
					Vector away = player.getLocation().toVector().subtract(feet.toVector());
					player.setVelocity(player.getVelocity().add(knockback(away, 1.4, 0.5)));
				}
				player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 70, 0, false, false, false));
				player.showTitle(Title.title(Component.empty(), Component.text("The Blood Knight is bloodied", NamedTextColor.DARK_RED),
					Title.Times.times(Duration.ofMillis(150), Duration.ofMillis(1400), Duration.ofMillis(500))));
			}
		}
		if (t >= ENRAGE_TICKS) {
			enter(State.FIGHTING, now);
			brain.setInvulnerable(false);
			brain.setAware(true);
			nextAttack = now + 20;
		}
	}

	// ---- phase three: the last stand ------------------------------------------------------------

	private void beginLastStand(long now) {
		phase = 3;
		enter(State.LAST_STAND, now);
		action = null;
		brain.setAware(false);
		brain.setInvulnerable(true);
		brain.setVelocity(new Vector());
		play(animations.lastStand, now);
		renameBars(Component.text("· Last Stand ☠", NamedTextColor.RED));
		plainBar.color(BossBar.Color.WHITE);
	}

	private void lastStandTick(long now) {
		int t = (int) (now - stateStart);
		BossFx.lastStand(feet, t, Animations.LAST_STAND_ROAR);
		if (t == Animations.LAST_STAND_ROAR) {
			for (Player player : victims(config.arenaRadius(), null)) {
				if (player.getLocation().distanceSquared(feet) < 81) {
					Vector away = player.getLocation().toVector().subtract(feet.toVector());
					player.setVelocity(player.getVelocity().add(knockback(away, 1.7, 0.6)));
				}
				player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 90, 0, false, false, false));
				player.showTitle(Title.title(Component.text("LAST STAND", NamedTextColor.DARK_RED, TextDecoration.BOLD),
					Component.text("Its thralls shield it. Cut them down.", NamedTextColor.GRAY),
					Title.Times.times(Duration.ofMillis(150), Duration.ofMillis(2000), Duration.ofMillis(600))));
			}
			raiseThralls();
		}
		if (t >= animations.lastStand.duration()) {
			enter(State.FIGHTING, now);
			brain.setInvulnerable(false);
			brain.setAware(true);
			base(brain, Attribute.MOVEMENT_SPEED, 0.37);
			nextAttack = now + 10;
		}
	}

	private void raiseThralls() {
		int fighters = 0;
		for (Player player : world.getPlayers()) {
			if (fightable(player)) {
				fighters++;
			}
		}
		int count = config.thralls() <= 0 ? 0 : config.thralls() + Math.max(0, fighters - 1);
		ThreadLocalRandom random = ThreadLocalRandom.current();
		for (int i = 0; i < count; i++) {
			double angle = (Math.PI * 2 * i) / count + random.nextDouble() * 0.4;
			Location at = feet.clone().add(Math.cos(angle) * 4.0, 0, Math.sin(angle) * 4.0);
			at.setYaw((float) Math.toDegrees(Math.atan2(-Math.cos(angle), -Math.sin(angle))));
			WitherSkeleton thrall = world.spawn(at, WitherSkeleton.class, this::configureThrall);
			thralls.add(thrall);
			BossFx.thrallRise(at);
			Player nearest = null;
			double best = Double.MAX_VALUE;
			for (Player player : world.getPlayers()) {
				if (fightable(player) && player.getLocation().distanceSquared(at) < best) {
					best = player.getLocation().distanceSquared(at);
					nearest = player;
				}
			}
			if (nearest != null) {
				thrall.setTarget(nearest);
			}
		}
	}

	private void configureThrall(WitherSkeleton thrall) {
		thrall.setPersistent(false);
		thrall.setRemoveWhenFarAway(false);
		thrall.setCanPickupItems(false);
		thrall.customName(Component.text("Blood Thrall", NamedTextColor.DARK_RED));
		thrall.setCustomNameVisible(true);
		thrall.getPersistentDataContainer().set(Keys.THRALL, PersistentDataType.BYTE, (byte) 1);
		base(thrall, Attribute.MAX_HEALTH, 40.0);
		thrall.setHealth(40.0);
		EntityEquipment gear = thrall.getEquipment();
		ItemStack chest = new ItemStack(Material.LEATHER_CHESTPLATE);
		chest.editMeta(LeatherArmorMeta.class, meta -> meta.setColor(Color.fromRGB(0x8C0F18)));
		gear.setChestplate(chest, true);
		gear.setItemInMainHand(new ItemStack(Material.IRON_SWORD), true);
		for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HAND, EquipmentSlot.CHEST}) {
			gear.setDropChance(slot, 0.0F);
		}
	}

	private void goBerserk() {
		berserk = true;
		BossFx.berserk(feet);
		renameBars(Component.text("· Berserk ☠", NamedTextColor.RED));
		for (Player player : victims(config.arenaRadius(), null)) {
			player.showTitle(Title.title(Component.empty(), Component.text("The Blood Knight goes berserk", NamedTextColor.DARK_RED),
				Title.Times.times(Duration.ofMillis(150), Duration.ofMillis(1600), Duration.ofMillis(500))));
		}
	}

	private void renameBars(Component suffix) {
		Component name = Component.text("☠ The Blood Knight ", NamedTextColor.DARK_RED).append(suffix);
		packBar.name(name);
		plainBar.name(name);
	}

	// ---- the end ------------------------------------------------------------------------------

	/** The body died: the model plays out its fall and the loot drops when it hits the ground. */
	void onDeath(Player killedBy, long now) {
		if (state == State.DYING || state == State.DONE) {
			return;
		}
		killer = killedBy == null ? null : killedBy.getName();
		enter(State.DYING, now);
		action = null;
		play(animations.death, now);
		packBar.progress(0.0F);
		plainBar.progress(0.0F);
		removeThralls();
		bleeding.clear();
	}

	private void dyingTick(long now) {
		int t = (int) (now - stateStart);
		BossFx.victoryBeat(feet, t);
		if (t == 22 && puppet.isValid()) {
			BloodFx.burst(feet.clone().add(0, 1.2, 0), BloodFx.SPLATTER, 30, 0.6, 0.3);
			puppet.remove(); // the stand-in falls apart as the model drops to its knees
		}
		if (t == 50) {
			dropLoot();
			announceVictory();
		}
		if (t > 100) {
			sink -= 0.035F; // the body sinks into its own blood
		}
		if (t >= DEATH_TICKS) {
			end();
		}
	}

	private void dropLoot() {
		ThreadLocalRandom random = ThreadLocalRandom.current();
		Location at = feet.clone().add(0, 0.8, 0);
		int cores = config.coresMin() + (config.coresMax() > config.coresMin() ? random.nextInt(config.coresMax() - config.coresMin() + 1) : 0);
		for (int i = 0; i < cores; i++) {
			drop(at, BloodCore.create(1));
		}
		// And the rarest thing it carries: blood that never dries, for the Blood Anvil.
		var blood = Settings.get().blood.drops();
		int drops = blood.bossMin() + (blood.bossMax() > blood.bossMin() ? random.nextInt(blood.bossMax() - blood.bossMin() + 1) : 0);
		for (int i = 0; i < drops; i++) {
			drop(at, BloodDrop.create(1));
		}
		if (random.nextDouble() < config.armorChance() && Settings.get().armorEnabled) {
			ArmorPiece[] pieces = ArmorPiece.values();
			drop(at, BloodArmor.create(pieces[random.nextInt(pieces.length)]));
		}
		int experience = config.experience();
		for (int orbs = 0; orbs < 12 && experience > 0; orbs++) {
			int amount = orbs == 11 ? experience : Math.max(1, config.experience() / 12);
			experience -= amount;
			world.spawn(at, ExperienceOrb.class, orb -> orb.setExperience(amount));
		}
	}

	private void drop(Location at, ItemStack stack) {
		ThreadLocalRandom random = ThreadLocalRandom.current();
		world.dropItem(at, stack, item -> {
			item.setVelocity(new Vector(random.nextGaussian() * 0.12, 0.35 + random.nextDouble() * 0.15, random.nextGaussian() * 0.12));
			item.setGlowing(true);
			item.setUnlimitedLifetime(true);
			item.setInvulnerable(true);
		});
	}

	private void announceVictory() {
		Title title = Title.title(Component.text("THE BLOOD KNIGHT IS SLAIN", NamedTextColor.DARK_RED, TextDecoration.BOLD),
			Component.text(killer == null ? "The blood runs still." : killer + " struck the last blow.", NamedTextColor.GRAY),
			Title.Times.times(Duration.ofMillis(300), Duration.ofMillis(3500), Duration.ofMillis(1000)));
		for (UUID id : participants) {
			Player player = Bukkit.getPlayer(id);
			if (player != null && player.getWorld() == world) {
				player.showTitle(title);
				BloodFx.playTo(player, BossFx.TRIUMPH, 0.9F, 0.8F);
			}
		}
		if (config.announce()) {
			manager.broadcastNear(center, Component.text("☠ The Blood Knight has fallen", NamedTextColor.RED)
				.append(Component.text(killer == null ? "." : " to " + killer + ".", NamedTextColor.GRAY)), 128);
		}
	}

	/** Sinks back into the ground without a fight (nobody left, or an admin stopped it). */
	void retreat(long now, String why) {
		if (state == State.DYING || state == State.RETREATING || state == State.DONE) {
			return;
		}
		enter(State.RETREATING, now);
		action = null;
		play(animations.retreat, now);
		if (brain.isValid()) {
			brain.remove();
		}
		if (puppet.isValid()) {
			puppet.remove();
		}
		removeThralls();
		manager.broadcastNear(center, Component.text("☠ The Blood Knight " + why, NamedTextColor.DARK_RED), config.arenaRadius() + 16);
	}

	private void removeThralls() {
		for (WitherSkeleton thrall : thralls) {
			if (thrall.isValid()) {
				BossFx.hit(thrall.getLocation().add(0, 1.0, 0));
				thrall.remove();
			}
		}
		thralls.clear();
	}

	/** Removes everything, now. Safe to call more than once. */
	void end() {
		if (state == State.DONE) {
			return;
		}
		state = State.DONE;
		for (UUID id : barViewers) {
			Player player = Bukkit.getPlayer(id);
			if (player != null) {
				player.hideBossBar(packBar);
				player.hideBossBar(plainBar);
			}
		}
		barViewers.clear();
		modelViewers.clear();
		puppetViewers.clear();
		bleeding.clear();
		model.remove();
		removeThralls();
		if (brain.isValid()) {
			brain.remove();
		}
		if (puppet.isValid()) {
			puppet.remove();
		}
	}

	// ---- reacting to the world ----------------------------------------------------------------

	/** Something hit the Knight and the damage went through. */
	void onHurt(Entity attacker, long now) {
		if (attacker instanceof Player player) {
			participants.add(player.getUniqueId());
		}
		lastHurt = now;
		if (action == null && now - lastFlinch > 16) {
			play(animations.flinch, now);
			lastFlinch = now;
		}
		BossFx.hit(feet.clone().add(0, 1.6 * scale, 0));
		float progress = (float) Math.max(0.0, Math.min(1.0, healthFraction()));
		packBar.progress(progress);
		plainBar.progress(progress);
	}

	/** Its ordinary melee hit landed: swing the sword to match, and it makes them bleed. */
	void onMelee(Entity victim, long now) {
		if (action == null && state == State.FIGHTING) {
			play(animations.swipe, now);
		}
		BloodFx.play(feet, BossFx.SWING, 1.1F, 0.6F);
		if (puppet.isValid()) {
			puppet.swingMainHand();
		}
		if (victim instanceof Player player && config.bleedStacks() > 0) {
			addBleed(player, 1);
		}
	}

	/** A player died in its arena: it drinks, and taunts if it's free to. */
	void onPlayerKilled(long now) {
		if (config.killHeal() > 0 && (state == State.FIGHTING || state == State.ATTACKING)) {
			heal(maxHealth() * config.killHeal());
			BossFx.drink(feet);
		}
		if (state == State.FIGHTING && action == null) {
			play(animations.roar, now);
			BloodFx.play(feet, BossFx.GROWL, 1.1F, 0.6F);
		}
	}

	// ---- viewers ------------------------------------------------------------------------------

	/** Boss bars, and which body each nearby player sees (the model with the pack, the stand-in without). */
	void updateViewers(long now) {
		double range = config.arenaRadius() + 16;
		Set<UUID> present = new HashSet<>();
		for (Player player : world.getPlayers()) {
			if (player.getLocation().distanceSquared(center) > range * range) {
				continue;
			}
			UUID id = player.getUniqueId();
			present.add(id);
			if (fightable(player)) {
				participants.add(id);
			}
			boolean pack = PackState.hasPack(player);
			player.showBossBar(pack ? packBar : plainBar);
			player.hideBossBar(pack ? plainBar : packBar);
			barViewers.add(id);
			if (pack && modelViewers.add(id)) {
				model.showTo(player);
			} else if (!pack && modelViewers.remove(id)) {
				model.hideFrom(player);
			}
			if (puppet.isValid() && !Boolean.valueOf(!pack).equals(puppetViewers.get(id))) {
				puppetViewers.put(id, !pack);
				if (pack) {
					player.hideEntity(plugin, puppet);
				} else {
					player.showEntity(plugin, puppet);
				}
			}
		}
		barViewers.removeIf(id -> {
			if (present.contains(id)) {
				return false;
			}
			Player player = Bukkit.getPlayer(id);
			if (player != null) {
				player.hideBossBar(packBar);
				player.hideBossBar(plainBar);
			}
			return true;
		});
	}

	void forgetViewer(UUID id) {
		barViewers.remove(id);
		modelViewers.remove(id);
		puppetViewers.remove(id);
		bleeding.remove(id);
	}

	/** The player loaded (or dropped) the resource pack mid-fight. */
	void packChanged(Player player) {
		modelViewers.remove(player.getUniqueId());
		model.hideFrom(player);
		puppetViewers.remove(player.getUniqueId());
		updateViewers(manager.now());
	}

	// ---- animation ----------------------------------------------------------------------------

	private void play(Clip clip, long now) {
		action = clip;
		actionStart = now;
	}

	private void animate(long now) {
		int interval = config.animationInterval();
		// Walking: how fast the body actually moved since the last frame.
		double moved = 0.0;
		if (brain.isValid()) {
			Vector velocity = brain.getVelocity();
			moved = Math.hypot(velocity.getX(), velocity.getZ());
		}
		float targetWalk = state == State.FIGHTING ? (float) Math.min(1.0, moved / 0.1) : 0.0F;
		walkWeight += (targetWalk - walkWeight) * Math.min(1.0F, 0.25F * interval);
		walkPhase += (float) (moved * 3.6 * interval / scale) + 0.02F * interval * walkWeight;
		// Facing: follow the body, or the attack's direction, turning at most 14 degrees a tick.
		if (state == State.ATTACKING && attack == Attack.WHIRLWIND && target != null && target.isValid()) {
			attackYaw = yawTowards(feet, target.getLocation());
		}
		float goal = state == State.ATTACKING ? attackYaw : brain.isValid() ? brain.getBodyYaw() : modelYaw;
		float turn = wrapDegrees(goal - modelYaw);
		modelYaw += Math.max(-14.0F * interval, Math.min(14.0F * interval, turn));

		pose.reset();
		animations.idle(pose, now, state == State.DYING ? 0.0F : 1.0F);
		if (walkWeight > 0.02F) {
			animations.walk(pose, walkPhase, walkWeight);
		}
		if (action != null) {
			long t = now - actionStart;
			if (t > action.duration() && !action.holds()) {
				action = null;
			} else {
				action.apply(pose, t, 1.0F);
			}
		}
		look(now);
		pose.addOffset(0, sink, 0, 1.0F);
		model.update(pose, modelYaw, scale, feet);
		footsteps();
		swordTrail(now);
	}

	/** Its head follows its prey: turned toward them (within reason) and tilted up or down to them. */
	private void look(long now) {
		boolean alert = state == State.FIGHTING || state == State.ATTACKING || state == State.ENRAGING || state == State.LAST_STAND;
		Player prey = target != null && target.isValid() ? target : brain.isValid() && brain.getTarget() instanceof Player p ? p : null;
		float wantYaw = 0.0F;
		float wantPitch = 0.0F;
		if (alert && prey != null && prey.getWorld() == world) {
			Location eyes = prey.getEyeLocation();
			float delta = wrapDegrees(yawTowards(feet, eyes) - modelYaw);
			wantYaw = (float) Math.max(-0.9, Math.min(0.9, -Math.toRadians(delta)));
			double flat = Math.hypot(eyes.getX() - feet.getX(), eyes.getZ() - feet.getZ());
			wantPitch = (float) Math.max(-0.6, Math.min(0.6, Math.atan2(eyes.getY() - (feet.getY() + 2.6 * scale), Math.max(0.5, flat))));
		}
		lookYaw += (wantYaw - lookYaw) * 0.35F;
		lookPitch += (wantPitch - lookPitch) * 0.35F;
		animations.lookAt(pose, lookYaw, lookPitch, action == null ? 1.0F : 0.45F);
	}

	/** Each heavy foot coming down while it walks: a thud and a puff of the ground. */
	private void footsteps() {
		float sign = Math.sin(walkPhase) >= 0 ? 1.0F : -1.0F;
		if (walkWeight > 0.45F && sign != lastStepSign) {
			double yaw = Math.toRadians(modelYaw);
			double side = 0.55 * scale * sign;
			Location foot = feet.clone().add(-Math.cos(yaw) * side, 0, -Math.sin(yaw) * side);
			Block ground = world.getBlockAt(foot.getBlockX(), foot.getBlockY() - 1, foot.getBlockZ());
			if (ground.getType().isSolid()) {
				BossFx.footstep(foot, ground.getBlockData());
			}
		}
		lastStepSign = sign;
	}

	/**
	 * During the part of a swing where the blade moves fast, the sword tip's path through the air
	 * is traced in blood: sampled several times between frames, so an arc reads as an arc.
	 */
	private void swordTrail(long now) {
		float t = action == null ? -1 : now - actionStart;
		if (action == null || !swinging(action, t)) {
			lastTip = null;
			return;
		}
		if (trailClip != action) {
			trailClip = action;
			lastTip = null;
		}
		float from = lastTip == null ? t : lastTipTime;
		int steps = lastTip == null ? 1 : 4;
		for (int k = 1; k <= steps; k++) {
			float at = from + (t - from) * k / steps;
			Location tip = tipAt(at, now);
			if (lastTip != null) {
				BossFx.swordArc(lastTip, tip, k == steps && ThreadLocalRandom.current().nextInt(3) == 0);
			}
			lastTip = tip;
		}
		lastTipTime = t;
	}

	private static boolean swinging(Clip clip, float t) {
		return switch (clip.name()) {
			case "swipe" -> t >= 4 && t <= 9;
			case "cleave" -> t >= Animations.CLEAVE_HIT - 4 && t <= Animations.CLEAVE_HIT + 5;
			case "slam" -> t >= Animations.SLAM_HIT - 5 && t <= Animations.SLAM_HIT + 1;
			case "grasp" -> t >= Animations.GRASP_PULL && t <= Animations.GRASP_PULL + 4
				|| t >= Animations.GRASP_SMASH - 4 && t <= Animations.GRASP_SMASH + 1;
			case "whirl" -> t >= Animations.WHIRL_FROM && t <= Animations.WHIRL_TO;
			case "land" -> t <= 4;
			case "cast" -> t >= Animations.CAST_HIT - 4 && t <= Animations.CAST_HIT + 1;
			default -> false;
		};
	}

	/** Where the sword tip is {@code t} ticks into the current clip. */
	private Location tipAt(float t, long now) {
		trailPose.reset();
		animations.idle(trailPose, now, 1.0F);
		action.apply(trailPose, t, 1.0F);
		trailPose.addOffset(0, sink, 0, 1.0F);
		BossModel.boneMatrices(rig, trailPose, modelYaw, scale, trailBones, trailRoot);
		trailBones[swordHand].transformPosition(SWORD_TIP, scratch);
		return new Location(world, feet.getX() + scratch.x, feet.getY() + scratch.y, feet.getZ() + scratch.z);
	}

	// ---- helpers ------------------------------------------------------------------------------

	private Component title() {
		return Component.text("☠ The Blood Knight ☠", NamedTextColor.DARK_RED);
	}

	private static float yawTowards(Location from, Location to) {
		double dx = to.getX() - from.getX();
		double dz = to.getZ() - from.getZ();
		return (float) Math.toDegrees(Math.atan2(-dx, dz));
	}

	private static double wrap(double radians) {
		double r = radians % (Math.PI * 2);
		if (r > Math.PI) {
			r -= Math.PI * 2;
		} else if (r < -Math.PI) {
			r += Math.PI * 2;
		}
		return r;
	}

	private static float wrapDegrees(float degrees) {
		float d = degrees % 360.0F;
		if (d >= 180.0F) {
			d -= 360.0F;
		} else if (d < -180.0F) {
			d += 360.0F;
		}
		return d;
	}

	private static double sq(double value) {
		return value * value;
	}

	/** Standing on something (the entity's view of it, not the client-reported player flag). */
	private static boolean onGround(Entity entity) {
		return entity.isOnGround();
	}

	Set<UUID> participants() {
		return participants;
	}

	List<? extends Entity> modelEntities() {
		return model.entities();
	}
}
