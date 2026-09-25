package net.unchartedsmp.bloodbath.weapon.behavior;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.unchartedsmp.bloodbath.ability.Cooldowns;
import net.unchartedsmp.bloodbath.ability.NullField;
import net.unchartedsmp.bloodbath.ability.ServerClock;
import net.unchartedsmp.bloodbath.ability.TickScheduler;
import net.unchartedsmp.bloodbath.fx.BloodFx;
import net.unchartedsmp.bloodbath.fx.Shapes;
import net.unchartedsmp.bloodbath.hud.Hud;
import net.unchartedsmp.bloodbath.util.Damage;
import net.unchartedsmp.bloodbath.util.Targeting;
import net.unchartedsmp.bloodbath.weapon.WeaponBehavior;
import net.unchartedsmp.bloodbath.weapon.WeaponType;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

/**
 * Sanguine Paradox Bow: a real bow (the client animates the draw, it shoots your arrows and takes
 * bow enchantments). A <b>fully drawn</b> shot, when the echo is ready, is fired twice: once now,
 * and once more from the past.
 * <ul>
 *   <li>A blood rift opens where you shot from (above your head, out of your way).</li>
 *   <li>If the arrow hits a creature, it's <b>marked</b>. When the echo comes due (1.5s) a phantom
 *       arrow tears out of the rift and homes into it wherever it has run, piercing anything
 *       in between.</li>
 *   <li>If the arrow hits nothing living, the echo retraces the arrow's real flight path back to
 *       the rift instead, cutting everything along it: a trap for whoever walks into the line.</li>
 * </ul>
 * Every creature takes the echo's damage once.
 *
 * <p>Nothing here spawns in front of the shooter's own camera: the draw effects and the first
 * ticks of the arrow's trail are for everyone else, the shooter gets sounds and the HUD.
 */
public final class ParadoxBow implements WeaponBehavior {
	private static final int FULL_DRAW_TICKS = 20;
	private static final int TRAIL_MAX_TICKS = 80;
	private static final int PATH_MAX_POINTS = 100;
	/** The shooter doesn't see their own trail for this long: it would start at their eyes. */
	private static final int TRAIL_SELF_HIDDEN_TICKS = 3;
	private static final int RETRACE_TICKS = 12;
	private static final int PHANTOM_TICKS = 6;
	private static final double HIT_SIZE = 1.3;
	private static final double RIFT_ABOVE_EYES = 1.2;

	/** An arrow from the bow in flight; paradox arrows also carry their echo. */
	private record Flight(AbstractArrow arrow, Player shooter, long firedAt, Echo echo) {
	}

	/** A pending echo: the rift, the arrow's recorded path, and whatever the arrow marked. */
	private static final class Echo {
		final Player shooter;
		final Location rift;
		final long due;
		final List<Location> path = new ArrayList<>();
		LivingEntity marked;

		Echo(Player shooter, Location rift, long due) {
			this.shooter = shooter;
			this.rift = rift;
			this.due = due;
		}
	}

	private record Draw(Player player, long clickedAt) {
	}

	/** Players drawing this bow, from their right-click until the arrow leaves (or they stop). */
	private final Map<UUID, Draw> drawing = new HashMap<>();
	private final Map<UUID, Flight> flights = new HashMap<>();
	/** Each shooter's pending echo, for the HUD. */
	private final Map<UUID, Echo> pending = new HashMap<>();

	@Override
	public WeaponType type() {
		return WeaponType.PARADOX_BOW;
	}

	/** Right-click with the bow: vanilla starts the draw, we start watching it. */
	public void startDrawing(Player player) {
		drawing.put(player.getUniqueId(), new Draw(player, ServerClock.now()));
	}

	@Override
	public void tick(long now) {
		if (!drawing.isEmpty()) {
			Iterator<Draw> it = drawing.values().iterator();
			while (it.hasNext()) {
				Draw draw = it.next();
				Player player = draw.player();
				if (!isDrawing(player)) {
					// The server starts using the bow just after the click; give it a moment.
					if (!player.isOnline() || now - draw.clickedAt() > 2) {
						it.remove();
					}
					continue;
				}
				drawEffects(player, player.getActiveItemUsedTime());
			}
		}
		if (!flights.isEmpty()) {
			Iterator<Flight> it = flights.values().iterator();
			while (it.hasNext()) {
				Flight flight = it.next();
				AbstractArrow arrow = flight.arrow();
				boolean landed = !arrow.isValid() || arrow.isInBlock();
				Echo echo = flight.echo();
				if (echo != null && echo.path.size() < PATH_MAX_POINTS && arrow.getWorld() == echo.rift.getWorld()) {
					echo.path.add(arrow.getLocation());
				}
				if (landed || now > flight.firedAt() + TRAIL_MAX_TICKS) {
					it.remove();
					continue;
				}
				trail(flight, now);
			}
		}
		if (!pending.isEmpty() && now % 4 == 0) {
			for (Echo echo : pending.values()) {
				riftEffects(echo, now);
			}
		}
	}

	private static void trail(Flight flight, long now) {
		Location at = flight.arrow().getLocation();
		BloodFx.Fx fx = flight.echo() != null ? BloodFx.BLOOD_FADE : BloodFx.MOTE;
		if (now - flight.firedAt() < TRAIL_SELF_HIDDEN_TICKS) {
			BloodFx.burstForOthers(flight.shooter(), at, fx, 2, 0.02);
			return;
		}
		BloodFx.burst(at, fx, flight.echo() != null ? 2 : 1, 0.02, 0.0);
		if (now % 3 == 0) {
			BloodFx.burst(at, BloodFx.DRIP, 1, 0.02, 0.0);
		}
	}

	/** Blood gathering into the nocked arrow; everyone else sees it, the shooter hears it. */
	private void drawEffects(Player player, int drawn) {
		Location nock = nockPos(player);
		boolean primed = echoAvailable(player);
		if (drawn < FULL_DRAW_TICKS) {
			if (drawn % 3 == 0) {
				BloodFx.burstForOthers(player, nock, BloodFx.MOTE, 3, 0.35 - drawn * 0.012);
			}
		} else if (drawn == FULL_DRAW_TICKS) {
			BloodFx.play(player, BloodFx.BOW_DRAWN, 0.8F, primed ? 0.6F : 1.3F);
			if (primed) {
				BloodFx.play(player, BloodFx.HEARTBEAT, 0.7F, 1.4F);
				BloodFx.burstForOthers(player, nock, BloodFx.BLOOD_FADE, 10, 0.2);
			}
		} else if (primed && drawn % 5 == 0) {
			BloodFx.burstForOthers(player, nock, BloodFx.BLOOD, 2, 0.1);
		}
	}

	private static boolean isDrawing(Player player) {
		return player.isOnline() && player.hasActiveItem() && player.getActiveItem().getType() == Material.BOW;
	}

	private boolean echoAvailable(Player player) {
		return Cooldowns.isReady(player, ability()) && !NullField.isNullified(player) && !pending.containsKey(player.getUniqueId());
	}

	private static Location nockPos(Player player) {
		Location eye = player.getEyeLocation();
		return eye.add(eye.getDirection().multiply(0.9)).add(0.0, -0.2, 0.0);
	}

	/** Vanilla's bow power curve: 0..1 over 20 ticks. */
	private static float pull(int ticks) {
		float t = ticks / 20.0F;
		return Math.min(1.0F, (t * t + t * 2.0F) / 3.0F);
	}

	/**
	 * Any arrow shot from the bow. {@code fullDraw}: vanilla crits it and the caller has checked
	 * permission, world and that the bow is enabled; the echo goes with it if it's ready.
	 */
	public void shot(Player player, AbstractArrow arrow, boolean fullDraw) {
		drawing.remove(player.getUniqueId());
		Echo echo = fullDraw ? release(player) : null;
		flights.put(arrow.getUniqueId(), new Flight(arrow, player, ServerClock.now(), echo));
	}

	private Echo release(Player player) {
		if (NullField.isNullified(player)) {
			NullField.notifyNullified(player);
			return null;
		}
		if (!Cooldowns.isReady(player, ability()) || pending.containsKey(player.getUniqueId())) {
			return null; // a normal shot: the bow is still a bow while the echo recharges
		}
		Cooldowns.start(player, ability());
		int delay = Math.max(10, ticksSetting("echo-delay", 30));
		Location rift = player.getEyeLocation().add(0.0, RIFT_ABOVE_EYES, 0.0);
		Echo echo = new Echo(player, rift, ServerClock.now() + delay);
		pending.put(player.getUniqueId(), echo);
		BloodFx.play(player, BloodFx.BOW_RELEASE, 1.0F, 0.6F);
		BloodFx.play(rift, BloodFx.RIFT_OPEN, 0.6F, 1.6F);
		BloodFx.burst(rift, BloodFx.BLOOD_FADE, 16, 0.3);
		BloodFx.ring(rift, BloodFx.BLOOD_FADE, 0.7, 14);
		TickScheduler.schedule(delay, () -> fire(echo));
		return echo;
	}

	/** A Paradox Bow arrow hit something: a paradox arrow marks the first creature it hits. */
	public void arrowHit(AbstractArrow arrow, Entity hit) {
		Flight flight = flights.get(arrow.getUniqueId());
		if (flight == null || flight.echo() == null || flight.echo().marked != null || !(hit instanceof LivingEntity target)
			|| !Targeting.validTarget(flight.shooter(), target)) {
			return;
		}
		Echo echo = flight.echo();
		echo.marked = target;
		// Marked prey lights up through walls until the echo arrives: nowhere to hide from it.
		target.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, ticksSetting("mark-glow", 60), 0, false, false, true));
		Location chest = BloodFx.chest(target);
		BloodFx.ring(target.getLocation().add(0.0, 0.1, 0.0), BloodFx.BLOOD_FADE, 0.9, 16);
		BloodFx.burst(chest, BloodFx.GLYPH, 12, 0.4);
		BloodFx.play(echo.shooter, BloodFx.MARKED, 0.9F, 1.4F);
		Hud.flash(echo.shooter, Component.text("✦ Marked: the echo will find it", NamedTextColor.RED));
	}

	private void riftEffects(Echo echo, long now) {
		long left = echo.due - now;
		double radius = 0.25 + 0.45 * Math.max(0.0, Math.min(1.0, left / 30.0));
		BloodFx.ring(echo.rift, BloodFx.BLOOD_FADE, radius, 10);
		BloodFx.burst(echo.rift, BloodFx.DRIP, 1, 0.15, 0.0);
		if (now % 8 == 0) {
			BloodFx.play(echo.rift, BloodFx.CLOCK_TICK, 0.7F, 0.9F + (float) Math.max(0, 30 - left) * 0.02F);
		}
		if (markedAndValid(echo)) {
			BloodFx.ring(echo.marked.getLocation().add(0.0, 0.1, 0.0), BloodFx.BLOOD_FADE, 0.6 + 0.2 * Math.sin(now * 0.5), 10);
		}
	}

	private boolean markedAndValid(Echo echo) {
		LivingEntity target = echo.marked;
		return target != null && target.isValid() && !target.isDead() && target.getWorld() == echo.rift.getWorld()
			&& target.getLocation().distanceSquared(echo.rift) <= square(setting("range", 40.0));
	}

	private void fire(Echo echo) {
		pending.remove(echo.shooter.getUniqueId(), echo);
		if (!echo.shooter.isOnline()) {
			return;
		}
		BloodFx.play(echo.rift, BloodFx.BOW_ECHO, 0.5F, 1.6F);
		BloodFx.burst(echo.rift, BloodFx.BLOOD_FADE, 20, 0.35);
		Set<UUID> hit = new HashSet<>();
		if (markedAndValid(echo)) {
			phantom(echo, hit);
		} else {
			retrace(echo, hit);
		}
	}

	/** A phantom arrow tears out of the rift and homes into the marked creature. */
	private void phantom(Echo echo, Set<UUID> hit) {
		double damage = setting("damage", 6.0);
		Location[] head = {echo.rift.clone()};
		TickScheduler.repeat(1, 1, PHANTOM_TICKS, step -> {
			LivingEntity target = echo.marked;
			if (!target.isValid() || target.getWorld() != head[0].getWorld()) {
				return false;
			}
			Location aim = BloodFx.chest(target);
			// Close the remaining distance evenly over the ticks left: it always arrives.
			double t = 1.0 / (PHANTOM_TICKS - step);
			Location next = head[0].clone().add(aim.toVector().subtract(head[0].toVector()).multiply(t));
			BloodFx.line(head[0], next, BloodFx.BLOOD, 3.0);
			Shapes.helix(head[0], next, BloodFx.EMBER, 0.15, 1.5, 4.0, step * 1.2);
			BloodFx.burst(next, BloodFx.BLOOD_FADE, 3, 0.08);
			cut(echo, head[0], next, damage, hit);
			head[0] = next;
			if (step == PHANTOM_TICKS - 1) {
				if (hit.add(target.getUniqueId()) && Targeting.validTarget(echo.shooter, target)) {
					Damage.deal(target, damage, echo.shooter, type(), next);
				}
				BloodFx.splash(aim, 6);
				BloodFx.play(aim, BloodFx.ECHO_HIT, 1.0F, 0.7F);
			}
			return true;
		});
	}

	/** The echo runs the arrow's flight backwards, from where it landed to the rift. */
	private void retrace(Echo echo, Set<UUID> hit) {
		List<Location> path = new ArrayList<>(echo.path);
		if (path.isEmpty()) {
			return;
		}
		path.add(0, echo.rift);
		double damage = setting("damage", 7.0);
		int segments = path.size() - 1;
		BloodFx.flow(path.get(segments), echo.rift, 8, 0.1, BloodFx.BRIGHT_RED, RETRACE_TICKS);
		TickScheduler.repeat(0, 1, RETRACE_TICKS, step -> {
			// Walk back from the landing point; each tick covers an equal share of the path.
			int from = segments - (int) Math.floor(step * segments / (double) RETRACE_TICKS);
			int to = segments - (int) Math.floor((step + 1) * segments / (double) RETRACE_TICKS);
			for (int i = from; i > to; i--) {
				Location a = path.get(i);
				Location b = path.get(i - 1);
				BloodFx.burst(a, BloodFx.BLOOD_FADE, 3, 0.1);
				BloodFx.burst(a, BloodFx.SPLATTER, 1, 0.05, 0.1);
				cut(echo, a, b, damage, hit);
			}
			return true;
		});
	}

	/** Damages every valid creature along a to b once (sampled every half block). */
	private void cut(Echo echo, Location a, Location b, double damage, Set<UUID> hit) {
		World world = a.getWorld();
		if (world != b.getWorld()) {
			return;
		}
		Vector step = b.toVector().subtract(a.toVector());
		int samples = Math.max(1, (int) Math.ceil(step.length() * 2.0));
		for (int i = 0; i <= samples; i++) {
			Location p = a.clone().add(step.clone().multiply(i / (double) samples));
			BoundingBox box = BoundingBox.of(p, HIT_SIZE / 2, HIT_SIZE / 2, HIT_SIZE / 2);
			for (Entity entity : world.getNearbyEntities(box, e -> e instanceof LivingEntity && e != echo.shooter)) {
				LivingEntity target = (LivingEntity) entity;
				if (target != echo.marked && Targeting.validTarget(echo.shooter, target) && hit.add(target.getUniqueId())) {
					Damage.deal(target, damage, echo.shooter, type(), p);
					BloodFx.splash(BloodFx.chest(target), 4);
				}
			}
		}
	}

	private static double square(double value) {
		return value * value;
	}

	@Override
	public Component hud(Player player) {
		Echo echo = pending.get(player.getUniqueId());
		if (echo != null) {
			long left = Math.max(0, echo.due - ServerClock.now());
			int delay = Math.max(10, ticksSetting("echo-delay", 30));
			Component line = Component.text("⧖ Echo  ", NamedTextColor.DARK_RED).append(Hud.bar(player, 1.0F - left / (float) delay))
				.append(Component.text(Hud.seconds(left), NamedTextColor.GRAY));
			return line.append(echo.marked != null
				? Component.text("  ✦ locked on", NamedTextColor.RED)
				: Component.text("  ⟲ retrace", NamedTextColor.GRAY));
		}
		if (drawing.containsKey(player.getUniqueId()) && isDrawing(player)) {
			float pull = pull(player.getActiveItemUsedTime());
			Component line = Component.text("Draw  ", NamedTextColor.DARK_RED).append(Hud.bar(player, pull));
			if (pull >= 1.0F) {
				line = line.append(echoAvailable(player)
					? Component.text("  ● ECHO PRIMED", NamedTextColor.RED)
					: Component.text("  full draw", NamedTextColor.GRAY));
			}
			return line;
		}
		Component line = Hud.cooldownBar(player, ability());
		return Cooldowns.isReady(player, ability())
			? line.append(Component.text("  full draw releases it", NamedTextColor.GRAY))
			: line;
	}

	@Override
	public BloodFx.Fx auraAccent() {
		return BloodFx.BLOOD_FADE;
	}

	@Override
	public void forget(UUID playerId) {
		drawing.remove(playerId);
	}

	@Override
	public void shutdown() {
		drawing.clear();
		flights.clear();
		pending.clear();
	}
}
