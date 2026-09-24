package net.unchartedsmp.bloodbath.boss;

import com.google.common.collect.ImmutableMultimap;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
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
import net.unchartedsmp.bloodbath.config.Settings;
import net.unchartedsmp.bloodbath.core.BloodCore;
import net.unchartedsmp.bloodbath.fx.BloodFx;
import net.unchartedsmp.bloodbath.fx.Particles;
import net.unchartedsmp.bloodbath.pack.PackState;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Player;
import org.bukkit.entity.WitherSkeleton;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

/**
 * One Blood Knight fight, from the moment it breaks the ground to its last bell.
 *
 * <p>The Knight is two things: an invisible wither skeleton (its hitbox, health and pathfinding,
 * scaled to the model's size) and the {@link BossModel} drawn over it for players with the
 * resource pack. Players without the pack see the skeleton's netherite armour and sword floating
 * with nothing inside: an empty suit of armour that fights.
 *
 * <p>Everything runs from {@link #tick}, called by the {@link BossManager} once a tick, so there
 * are no tasks of its own to leak: when the fight ends, the manager simply stops calling it.
 */
final class BloodKnightBoss {
	enum State {
		RISING, FIGHTING, ATTACKING, ENRAGING, DYING, RETREATING, DONE
	}

	enum Attack {
		CLEAVE, SLAM, SPIKES, CHARGE
	}

	private static final int RISE_TICKS = 60;
	private static final int ENRAGE_TICKS = 42;
	private static final int DEATH_TICKS = 150;
	private static final int RETREAT_TICKS = 52;
	private static final double CLEAVE_RADIUS = 5.5;
	private static final double CLEAVE_HALF_ANGLE = Math.toRadians(65);
	private static final double SLAM_RADIUS = 9.5;
	private static final double CHARGE_LENGTH = 13.0;
	private static final double BRAIN_HEIGHT = 2.4;
	/** The model is 3 blocks tall at scale 1; the skeleton's hitbox is scaled to match. */
	private static final double MODEL_HEIGHT = 3.0;

	private final BossManager manager;
	private final Plugin plugin;
	private final Settings.BossSettings config;
	private final Animations animations;
	private final Location center;
	private final World world;
	private final WitherSkeleton brain;
	private final BossModel model;
	private final Pose pose;
	private final float scale;
	private final BossBar packBar;
	private final BossBar plainBar;
	private final Set<UUID> barViewers = new HashSet<>();
	private final Set<UUID> modelViewers = new HashSet<>();
	private final Set<UUID> participants = new HashSet<>();
	private final Set<UUID> struck = new HashSet<>();
	private final List<Location> spikes = new ArrayList<>();

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
	private float sink;
	private Location feet;
	private boolean bloodied;
	private long emptySince = -1;
	private String killer;

	BloodKnightBoss(BossManager manager, Plugin plugin, Rig rig, Animations animations, Location at) {
		this.manager = manager;
		this.plugin = plugin;
		this.config = Settings.get().boss;
		this.animations = animations;
		this.center = at.clone();
		this.world = at.getWorld();
		this.scale = (float) config.scale();
		this.pose = new Pose(rig.bones().size());
		long now = manager.now();
		this.stateStart = now;
		this.modelYaw = at.getYaw();
		this.feet = at.clone();
		this.brain = world.spawn(at, WitherSkeleton.class, this::configure);
		this.model = new BossModel(rig, plugin, at, scale, config.animationInterval());
		this.packBar = BossBar.bossBar(title(), 1.0F, BossBar.Color.YELLOW, BossBar.Overlay.PROGRESS);
		this.plainBar = BossBar.bossBar(title(), 1.0F, BossBar.Color.RED, BossBar.Overlay.NOTCHED_10);
		play(animations.rise, now);
		BossFx.emerge(at);
		updateViewers(now);
	}

	private void configure(WitherSkeleton skeleton) {
		skeleton.setPersistent(false);
		skeleton.setRemoveWhenFarAway(false);
		skeleton.setCanPickupItems(false);
		skeleton.setSilent(true);
		skeleton.setInvisible(true);
		skeleton.setInvulnerable(true);
		skeleton.setAware(false);
		skeleton.customName(Component.text("Blood Knight", NamedTextColor.DARK_RED));
		skeleton.setCustomNameVisible(false);
		skeleton.getPersistentDataContainer().set(Keys.BOSS, PersistentDataType.BYTE, (byte) 1);
		base(skeleton, Attribute.MAX_HEALTH, config.health());
		skeleton.setHealth(config.health());
		base(skeleton, Attribute.ARMOR, config.armor());
		base(skeleton, Attribute.ARMOR_TOUGHNESS, 4.0);
		base(skeleton, Attribute.ATTACK_DAMAGE, config.meleeDamage());
		base(skeleton, Attribute.KNOCKBACK_RESISTANCE, 1.0);
		base(skeleton, Attribute.MOVEMENT_SPEED, 0.27);
		base(skeleton, Attribute.FOLLOW_RANGE, config.arenaRadius() + 16);
		base(skeleton, Attribute.STEP_HEIGHT, 1.1);
		base(skeleton, Attribute.SCALE, MODEL_HEIGHT * scale / BRAIN_HEIGHT);
		// What players without the resource pack see: the armour, the sword and the shield.
		EntityEquipment gear = skeleton.getEquipment();
		gear.setHelmet(new ItemStack(Material.NETHERITE_HELMET), true);
		gear.setChestplate(new ItemStack(Material.NETHERITE_CHESTPLATE), true);
		gear.setLeggings(new ItemStack(Material.NETHERITE_LEGGINGS), true);
		gear.setBoots(new ItemStack(Material.NETHERITE_BOOTS), true);
		ItemStack sword = new ItemStack(Material.NETHERITE_SWORD);
		sword.editMeta(meta -> meta.setAttributeModifiers(ImmutableMultimap.of())); // its damage is the attribute above
		gear.setItemInMainHand(sword, true);
		gear.setItemInOffHand(new ItemStack(Material.SHIELD), true);
		for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HAND, EquipmentSlot.OFF_HAND, EquipmentSlot.HEAD, EquipmentSlot.CHEST,
			EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
			gear.setDropChance(slot, 0.0F);
		}
	}

	private static void base(WitherSkeleton skeleton, Attribute attribute, double value) {
		AttributeInstance instance = skeleton.getAttribute(attribute);
		if (instance != null) {
			instance.setBaseValue(value);
		}
	}

	// ---- queries ------------------------------------------------------------------------------

	WitherSkeleton brain() {
		return brain;
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

	boolean done() {
		return state == State.DONE;
	}

	boolean inArena(Location location) {
		return location.getWorld() == world && location.distanceSquared(center) <= config.arenaRadius() * config.arenaRadius();
	}

	boolean vulnerable() {
		return state == State.FIGHTING || state == State.ATTACKING;
	}

	double healthFraction() {
		AttributeInstance max = brain.getAttribute(Attribute.MAX_HEALTH);
		return brain.isDead() ? 0.0 : brain.getHealth() / (max == null ? config.health() : max.getValue());
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
		if (now % 10 == 0) {
			updateViewers(now);
		}
		switch (state) {
			case RISING -> {
				long t = now - stateStart;
				if (t == 44) {
					BossFx.roar(feet);
				}
				if (t >= RISE_TICKS) {
					enter(State.FIGHTING, now);
					brain.setInvulnerable(false);
					brain.setAware(true);
					nextAttack = now + 40;
				}
			}
			case FIGHTING -> fight(now);
			case ATTACKING -> {
				if (attackTick((int) (now - stateStart))) {
					enter(State.FIGHTING, now);
					brain.setAware(true);
					int cooldown = (int) (config.attackCooldownTicks() * (bloodied ? 0.7 : 1.0));
					nextAttack = now + cooldown + ThreadLocalRandom.current().nextInt(Math.max(1, cooldown / 3));
				}
			}
			case ENRAGING -> enrageTick(now);
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

	private void fight(long now) {
		if (now % 10 == 0) {
			BossFx.aura(feet, bloodied);
		}
		if (config.atmosphere() && now % 10 == 5) {
			for (UUID id : participants) {
				Player player = Bukkit.getPlayer(id);
				if (player != null && inArena(player.getLocation())) {
					BossFx.atmosphere(player.getLocation(), now);
				}
			}
		}
		if (!bloodied && healthFraction() <= config.phaseTwoAt()) {
			enrage(now);
			return;
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
		if (now >= nextAttack && action == null) {
			startAttack(hunted, now);
		}
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

	// ---- attacks ------------------------------------------------------------------------------

	private void startAttack(Player hunted, long now) {
		double distance = Math.sqrt(hunted.getLocation().distanceSquared(feet));
		ThreadLocalRandom random = ThreadLocalRandom.current();
		Attack choice;
		if (distance <= CLEAVE_RADIUS) {
			choice = random.nextDouble() < (crowdNear(8.0) >= 2 ? 0.55 : 0.3) ? Attack.SLAM : Attack.CLEAVE;
		} else if (distance <= 15.0) {
			choice = random.nextBoolean() ? Attack.CHARGE : Attack.SPIKES;
		} else {
			choice = Attack.SPIKES;
		}
		if (choice == lastAttack && random.nextBoolean()) {
			choice = Attack.values()[(choice.ordinal() + 1 + random.nextInt(3)) % 4];
		}
		attack = choice;
		lastAttack = choice;
		target = hunted;
		struck.clear();
		spikes.clear();
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
		}, now);
		BloodFx.play(feet, BossFx.GROWL, 1.0F, choice == Attack.SLAM ? 0.5F : 0.8F);
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

	/** One tick of the current attack; true when it's over. */
	private boolean attackTick(int t) {
		double yaw = Math.toRadians(attackYaw);
		// Minecraft yaw 0 faces +Z; the arc helper measures angles from +X.
		double facing = Math.atan2(Math.cos(yaw), -Math.sin(yaw));
		return switch (attack) {
			case CLEAVE -> {
				if (t <= 13 && t % 2 == 0) {
					BossFx.cleaveTelegraph(feet, facing, CLEAVE_RADIUS, CLEAVE_HALF_ANGLE, t / 13.0F);
				}
				if (t == 15) {
					BossFx.cleaveStrike(feet, facing, CLEAVE_RADIUS, CLEAVE_HALF_ANGLE);
					for (Player player : victims(CLEAVE_RADIUS + 0.5)) {
						Vector to = player.getLocation().toVector().subtract(feet.toVector()).setY(0);
						double angle = Math.atan2(to.getZ(), to.getX());
						if (Math.abs(wrap(angle - facing)) <= CLEAVE_HALF_ANGLE + 0.15 || to.lengthSquared() < 2.0) {
							strike(player, config.cleaveDamage(), knockback(to, 1.15, 0.4));
						}
					}
				}
				yield t >= 30;
			}
			case SLAM -> {
				if (t < 21) {
					BossFx.slamWindup(feet, SLAM_RADIUS, t);
				}
				if (t == 21) {
					BossFx.slamImpact(feet);
				}
				if (t >= 21 && t <= 33) {
					double radius = 1.0 + (t - 21) / 12.0 * (SLAM_RADIUS - 1.0);
					BossFx.slamWave(feet, radius);
					for (Player player : victims(radius + 1.0)) {
						Vector to = player.getLocation().toVector().subtract(feet.toVector());
						double flat = Math.hypot(to.getX(), to.getZ());
						boolean grounded = player.getLocation().getY() - feet.getY() < 0.7 && onGround(player);
						if (flat >= radius - 1.1 && grounded && struck.add(player.getUniqueId())) {
							strike(player, config.slamDamage(), knockback(to.setY(0), 0.8, 0.95));
						}
					}
				}
				yield t >= 40;
			}
			case SPIKES -> {
				if (t == 0) {
					List<Player> marked = new ArrayList<>(victims(config.arenaRadius()));
					Collections.shuffle(marked);
					for (Player player : marked.subList(0, Math.min(marked.size(), bloodied ? 4 : 3))) {
						spikes.add(player.getLocation());
					}
				}
				if (t <= 30 && t % 2 == 0) {
					for (Location spot : spikes) {
						BossFx.spikeTelegraph(spot, t / 30.0F);
					}
				}
				if (t == 16) {
					BloodFx.play(feet, BossFx.SPIKE, 1.2F, 0.5F);
					BloodFx.burst(feet, BloodFx.SPLATTER, 16, 0.6, 0.3);
				}
				if (t == 30) {
					for (Location spot : spikes) {
						BossFx.spikeErupt(spot);
						for (Player player : victims(config.arenaRadius())) {
							Location at = player.getLocation();
							if (Math.hypot(at.getX() - spot.getX(), at.getZ() - spot.getZ()) <= 1.5 && Math.abs(at.getY() - spot.getY()) < 2.5
								&& struck.add(player.getUniqueId())) {
								strike(player, config.spikeDamage(), new Vector(0, 1.1, 0));
							}
						}
					}
				}
				yield t >= 46;
			}
			case CHARGE -> {
				double dx = -Math.sin(yaw);
				double dz = Math.cos(yaw);
				if (t < 12 && t % 3 == 0) {
					BossFx.chargeTelegraph(feet, yaw, CHARGE_LENGTH);
				}
				if (t == 12) {
					BloodFx.play(feet, BossFx.CHARGE, 1.3F, 0.7F);
					BloodFx.play(feet, BossFx.STEP, 1.2F, 0.6F);
				}
				if (t >= 14 && t <= 26) {
					Vector velocity = brain.getVelocity();
					if (t > 17 && Math.hypot(velocity.getX(), velocity.getZ()) < 0.15) {
						// Hit a wall: it staggers.
						BossFx.hit(feet.clone().add(dx, 1.2, dz));
						BloodFx.play(feet, BossFx.HEAVY_HIT, 1.2F, 0.5F);
						yield true;
					}
					brain.setVelocity(new Vector(dx * 0.95, Math.min(velocity.getY(), 0.0), dz * 0.95));
					BossFx.chargeTrail(feet);
					for (Player player : victims(2.2)) {
						if (struck.add(player.getUniqueId())) {
							strike(player, config.chargeDamage(), new Vector(dx * 1.3, 0.55, dz * 1.3));
						}
					}
				}
				yield t >= 34;
			}
		};
	}

	/** Players this attack may hurt: in the arena, alive, not in creative or spectator. */
	private List<Player> victims(double radius) {
		List<Player> out = new ArrayList<>();
		for (Player player : world.getPlayers()) {
			if (fightable(player) && player.getLocation().distanceSquared(feet) <= radius * radius) {
				out.add(player);
			}
		}
		return out;
	}

	private void strike(Player player, double amount, Vector push) {
		double damage = amount * (bloodied ? 1.2 : 1.0);
		if (brain.isValid()) {
			player.damage(damage, DamageSource.builder(DamageType.MOB_ATTACK).withCausingEntity(brain).withDirectEntity(brain).build());
		} else {
			player.damage(damage);
		}
		player.setVelocity(player.getVelocity().add(push));
		BossFx.hit(BloodFx.chest(player));
		participants.add(player.getUniqueId());
	}

	private static Vector knockback(Vector away, double strength, double up) {
		Vector flat = away.clone().setY(0);
		if (flat.lengthSquared() < 1.0E-4) {
			flat = new Vector(1, 0, 0);
		}
		return flat.normalize().multiply(strength).setY(up);
	}

	// ---- phase two ----------------------------------------------------------------------------

	private void enrage(long now) {
		bloodied = true;
		enter(State.ENRAGING, now);
		brain.setAware(false);
		brain.setInvulnerable(true);
		play(animations.roar, now);
		base(brain, Attribute.MOVEMENT_SPEED, 0.31);
		Component name = Component.text("☠ The Blood Knight ", NamedTextColor.DARK_RED)
			.append(Component.text("· Bloodied ☠", NamedTextColor.RED));
		packBar.name(name);
		plainBar.name(name);
		plainBar.color(BossBar.Color.PURPLE);
	}

	private void enrageTick(long now) {
		long t = now - stateStart;
		if (t == 8) {
			BossFx.roar(feet);
			for (Player player : victims(config.arenaRadius())) {
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

	// ---- the end ------------------------------------------------------------------------------

	/** The skeleton died: the model plays out its fall and the loot drops when it hits the ground. */
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
	}

	private void dyingTick(long now) {
		int t = (int) (now - stateStart);
		BossFx.victoryBeat(feet, t);
		if (t == 54) {
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
		manager.broadcastNear(center, Component.text("☠ The Blood Knight " + why, NamedTextColor.DARK_RED), config.arenaRadius() + 16);
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
		model.remove();
		if (brain.isValid()) {
			brain.remove();
		}
	}

	// ---- reacting to the world ----------------------------------------------------------------

	/** Something hit the Knight and the damage went through. */
	void onHurt(Entity attacker, long now) {
		if (attacker instanceof Player player) {
			participants.add(player.getUniqueId());
		}
		if (action == null) {
			play(animations.flinch, now);
		}
		BossFx.hit(feet.clone().add(0, 1.6 * scale, 0));
		float progress = (float) Math.max(0.0, Math.min(1.0, healthFraction()));
		packBar.progress(progress);
		plainBar.progress(progress);
	}

	/** Its ordinary melee hit landed: swing the sword to match. */
	void onMelee(long now) {
		if (action == null && state == State.FIGHTING) {
			play(animations.swipe, now);
		}
	}

	/** A player died in its arena: a taunt, if it's free to. */
	void onPlayerKilled(long now) {
		if (state == State.FIGHTING && action == null) {
			play(animations.roar, now);
			BloodFx.play(feet, BossFx.GROWL, 1.1F, 0.6F);
		}
	}

	// ---- viewers ------------------------------------------------------------------------------

	/** Boss bars, the model's visibility and the pack users' fake-empty equipment, every half second. */
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
				hideGearFrom(player);
			} else if (!pack && modelViewers.remove(id)) {
				model.hideFrom(player);
			} else if (pack && now % 40 == 0) {
				hideGearFrom(player); // re-sent now and then: tracking can reset it
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

	/** Pack users see the model, so the skeleton's fallback armour and weapons are blanked out for them. */
	void hideGearFrom(Player player) {
		if (!brain.isValid()) {
			return;
		}
		Map<EquipmentSlot, ItemStack> empty = new EnumMap<>(EquipmentSlot.class);
		for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET,
			EquipmentSlot.HAND, EquipmentSlot.OFF_HAND}) {
			empty.put(slot, ItemStack.empty());
		}
		player.sendEquipmentChange(brain, empty);
	}

	void forgetViewer(UUID id) {
		barViewers.remove(id);
		modelViewers.remove(id);
	}

	/** The player loaded (or dropped) the resource pack mid-fight. */
	void packChanged(Player player) {
		modelViewers.remove(player.getUniqueId());
		model.hideFrom(player);
		updateViewers(manager.now());
	}

	// ---- animation ----------------------------------------------------------------------------

	private void play(Clip clip, long now) {
		action = clip;
		actionStart = now;
	}

	private void animate(long now) {
		int interval = config.animationInterval();
		// Walking: how fast the skeleton actually moved since the last frame.
		double moved = 0.0;
		if (brain.isValid()) {
			Vector velocity = brain.getVelocity();
			moved = Math.hypot(velocity.getX(), velocity.getZ());
		}
		float targetWalk = state == State.FIGHTING ? (float) Math.min(1.0, moved / 0.1) : 0.0F;
		walkWeight += (targetWalk - walkWeight) * Math.min(1.0F, 0.25F * interval);
		walkPhase += (float) (moved * 4.2 * interval) + 0.02F * interval * walkWeight;
		// Facing: follow the body, or the attack's direction, turning at most 14 degrees a tick.
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
		pose.addOffset(0, sink, 0, 1.0F);
		model.update(pose, modelYaw, scale, feet);
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
