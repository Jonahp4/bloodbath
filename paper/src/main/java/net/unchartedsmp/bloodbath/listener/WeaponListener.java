package net.unchartedsmp.bloodbath.listener;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import net.unchartedsmp.bloodbath.Keys;
import net.unchartedsmp.bloodbath.ability.NullField;
import net.unchartedsmp.bloodbath.ability.ServerClock;
import net.unchartedsmp.bloodbath.armor.BloodArmor;
import net.unchartedsmp.bloodbath.config.Settings;
import net.unchartedsmp.bloodbath.core.BloodCore;
import net.unchartedsmp.bloodbath.fx.BloodFx;
import net.unchartedsmp.bloodbath.util.Damage;
import net.unchartedsmp.bloodbath.util.Targeting;
import net.unchartedsmp.bloodbath.weapon.Behaviors;
import net.unchartedsmp.bloodbath.weapon.Gate;
import net.unchartedsmp.bloodbath.weapon.KillTracker;
import net.unchartedsmp.bloodbath.weapon.WeaponBehavior;
import net.unchartedsmp.bloodbath.weapon.WeaponType;
import net.unchartedsmp.bloodbath.weapon.Weapons;
import net.unchartedsmp.bloodbath.weapon.behavior.Mirrorfang;
import org.bukkit.Keyed;
import org.bukkit.block.Block;
import org.bukkit.damage.DamageSource;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataType;

/** Turns clicks, hits, shots and kills with Bloodbath weapons into their powers. */
public final class WeaponListener implements Listener {
	/** Last tick each player fired an ability, so one click can never fire two. */
	private final Map<UUID, Long> lastUse = new HashMap<>();

	// ---- right-click abilities and the gauntlet's punch -------------------------------------

	@EventHandler(priority = EventPriority.HIGH)
	public void onInteract(PlayerInteractEvent event) {
		Action action = event.getAction();
		if (action == Action.PHYSICAL || action == Action.LEFT_CLICK_AIR || event.getHand() == null) {
			return;
		}
		// Right-clicks on air arrive "cancelled"; protection plugins veto item use through this instead.
		if (event.useItemInHand() == Event.Result.DENY) {
			return;
		}
		WeaponType type = Weapons.typeOf(event.getItem());
		if (type == null) {
			return;
		}
		Player player = event.getPlayer();
		if (action == Action.LEFT_CLICK_BLOCK) {
			Block block = event.getClickedBlock();
			if (type == WeaponType.METEOR_GAUNTLET && block != null && ready(player, type)) {
				Behaviors.METEOR_GAUNTLET.punchBlock(player, block);
			}
			return;
		}
		if (action == Action.RIGHT_CLICK_BLOCK && !player.isSneaking() && opensSomething(event.getClickedBlock())
			&& event.useInteractedBlock() != Event.Result.DENY) {
			return; // doors, chests, buttons... work as usual
		}
		if (type == WeaponType.PARADOX_BOW) {
			// A real bow: vanilla handles the draw; its echo is checked when the arrow leaves.
			Behaviors.PARADOX_BOW.startDrawing(player);
			return;
		}
		if (ready(player, type)) {
			Behaviors.of(type).use(player);
		}
	}

	@SuppressWarnings("deprecation") // Material#isInteractable: good enough to spot doors, chests and buttons
	private static boolean opensSomething(Block block) {
		return block != null && block.getType().isInteractable();
	}

	/** Permission, world, weapon enabled, not clotted, and not already fired this tick. */
	private boolean ready(Player player, WeaponType type) {
		if (!Gate.allows(player, type, false)) {
			return false;
		}
		WeaponBehavior behavior = Behaviors.of(type);
		if (behavior.canBeNullified() && NullField.isNullified(player)) {
			NullField.notifyNullified(player);
			return false;
		}
		long now = ServerClock.now();
		Long last = lastUse.put(player.getUniqueId(), now);
		return last == null || last != now;
	}

	// ---- melee passives ---------------------------------------------------------------------

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onMelee(EntityDamageByEntityEvent event) {
		if (Damage.isAbilityDamage() || event.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK) {
			return;
		}
		if (!(event.getDamager() instanceof Player player) || !(event.getEntity() instanceof LivingEntity target)) {
			return;
		}
		WeaponType type = Weapons.typeOf(player.getInventory().getItemInMainHand());
		if (type == null || !Targeting.validTarget(player, target) || !Gate.allows(player, type, true)) {
			return;
		}
		BloodFx.splash(BloodFx.chest(target), 3);
		Behaviors.of(type).melee(player, target, event.getFinalDamage());
	}

	/** Tells {@link Damage} what happened to its own hits, for /bloodbath debug. */
	@EventHandler(priority = EventPriority.MONITOR)
	public void onAbilityDamage(EntityDamageEvent event) {
		if (Damage.isAbilityDamage()) {
			Damage.observe(event.isCancelled());
		}
	}

	// ---- the Paradox Bow ----------------------------------------------------------------------

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onShoot(EntityShootBowEvent event) {
		if (!(event.getEntity() instanceof Player player) || !(event.getProjectile() instanceof AbstractArrow arrow)
			|| Weapons.typeOf(event.getBow()) != WeaponType.PARADOX_BOW) {
			return;
		}
		arrow.getPersistentDataContainer().set(Keys.WEAPON, PersistentDataType.STRING, WeaponType.PARADOX_BOW.id());
		// Vanilla only crits fully drawn shots, on every version.
		boolean fullDraw = arrow.isCritical() && Gate.allows(player, WeaponType.PARADOX_BOW, true);
		Behaviors.PARADOX_BOW.shot(player, arrow, fullDraw);
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onArrowHit(ProjectileHitEvent event) {
		if (event.getHitEntity() != null && event.getEntity() instanceof AbstractArrow arrow
			&& WeaponType.PARADOX_BOW.id().equals(arrow.getPersistentDataContainer().get(Keys.WEAPON, PersistentDataType.STRING))) {
			Behaviors.PARADOX_BOW.arrowHit(arrow, event.getHitEntity());
		}
	}

	// ---- kills ------------------------------------------------------------------------------

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onDeath(EntityDeathEvent event) {
		LivingEntity victim = event.getEntity();
		DamageSource source = event.getDamageSource();
		if (!(source.getCausingEntity() instanceof Player killer) || killer == victim || Mirrorfang.isMirror(victim)) {
			return;
		}
		WeaponType type = Damage.currentWeapon();
		if (type == null) {
			Entity direct = source.getDirectEntity();
			if (direct instanceof Projectile projectile) {
				type = WeaponType.byId(projectile.getPersistentDataContainer().get(Keys.WEAPON, PersistentDataType.STRING));
			} else if (direct == killer) {
				type = Weapons.typeOf(killer.getInventory().getItemInMainHand());
			}
		}
		if (type == null) {
			return;
		}
		Settings settings = Settings.get();
		if (settings.killEffects) {
			BloodFx.killBurst(victim, killer);
		}
		if (settings.hitMarkers) {
			// A skull under the crosshair, for the killer only.
			killer.showTitle(Title.title(Component.empty(), Component.text("☠", NamedTextColor.RED),
				Title.Times.times(Duration.ZERO, Duration.ofMillis(350), Duration.ofMillis(250))));
		}
		if (settings.killTracking && (victim instanceof Player || settings.countMobKills)) {
			ItemStack weapon = held(killer, type);
			if (weapon != null) {
				KillTracker.record(killer, weapon, type);
			}
		}
	}

	/** The live stack of this weapon type in either hand, or null. */
	private static ItemStack held(Player player, WeaponType type) {
		PlayerInventory inventory = player.getInventory();
		ItemStack main = inventory.getItemInMainHand();
		if (Weapons.typeOf(main) == type) {
			return main;
		}
		ItemStack off = inventory.getItemInOffHand();
		return Weapons.typeOf(off) == type ? off : null;
	}

	// ---- keeping items current and safe -----------------------------------------------------

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onHeld(PlayerItemHeldEvent event) {
		ItemStack next = event.getPlayer().getInventory().getItem(event.getNewSlot());
		if (next != null) {
			Weapons.refresh(next);
		}
	}

	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onPickup(EntityPickupItemEvent event) {
		Item item = event.getItem();
		ItemStack stack = item.getItemStack();
		if (Weapons.refresh(stack) || BloodArmor.refresh(stack)) {
			item.setItemStack(stack);
		}
	}

	/** Dropped weapons never despawn and can't be destroyed by cactus or explosions. */
	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onItemSpawn(ItemSpawnEvent event) {
		Item item = event.getEntity();
		ItemStack stack = item.getItemStack();
		if (Settings.get().neverDespawn && (Weapons.isWeapon(stack) || BloodArmor.isArmor(stack) || BloodCore.isCore(stack))) {
			item.setUnlimitedLifetime(true);
			item.setInvulnerable(true);
		}
	}

	/**
	 * Weapons are netherite swords and bows underneath, so without this they'd be accepted by
	 * crafting recipes (including our own) and the grid's repair recipe, turning two weapons into
	 * one plain sword.
	 */
	@EventHandler(priority = EventPriority.HIGHEST)
	public void onPrepareCraft(PrepareItemCraftEvent event) {
		boolean ours = event.getRecipe() instanceof Keyed keyed && keyed.getKey().getNamespace().equals(Keys.WEAPON.getNamespace());
		for (ItemStack ingredient : event.getInventory().getMatrix()) {
			// A Blood Core is a nether star underneath: keep it out of beacons and every other
			// vanilla recipe, it only works in ours.
			if (Weapons.isWeapon(ingredient) || BloodArmor.isArmor(ingredient) || !ours && BloodCore.isCore(ingredient)) {
				event.getInventory().setResult(null);
				return;
			}
		}
		ItemStack result = event.getInventory().getResult();
		if ((Weapons.isWeapon(result) || BloodArmor.isArmor(result)) && !event.getView().getPlayer().hasPermission("bloodbath.craft")) {
			event.getInventory().setResult(null);
		}
	}

	public void forget(UUID playerId) {
		lastUse.remove(playerId);
	}

	public void prune() {
		long now = ServerClock.now();
		lastUse.values().removeIf(tick -> tick < now - 20);
	}
}
