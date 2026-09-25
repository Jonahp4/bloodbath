package net.unchartedsmp.bloodbath.anvil;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.unchartedsmp.bloodbath.Keys;
import net.unchartedsmp.bloodbath.ability.ServerClock;
import net.unchartedsmp.bloodbath.blood.BloodDrop;
import net.unchartedsmp.bloodbath.blood.BloodLevels;
import net.unchartedsmp.bloodbath.config.BloodConfig;
import net.unchartedsmp.bloodbath.config.Settings;
import net.unchartedsmp.bloodbath.fx.BloodFx;
import net.unchartedsmp.bloodbath.pack.PackState;
import net.unchartedsmp.bloodbath.weapon.WeaponType;
import net.unchartedsmp.bloodbath.weapon.Weapons;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataType;

/**
 * One player's Blood Anvil screen.
 *
 * <p>The real items (the weapon and the Blood Drops) never sit in the menu: they're held here, in
 * {@link #weapon} and {@link #drops}, and written to the escrow the moment they change. Every slot
 * of the menu shows a picture: a copy of the weapon with its Blood Infusion appended, a copy of the
 * drops, the result preview, the button, the level pips, all tagged as menu items. Every click on
 * the menu is cancelled and carried out here instead (left, right, shift, number keys, the off-hand
 * key, dropping), so no vanilla behaviour can move, duplicate or keep a picture, and a click can only
 * ever move a real item between the player and this session.
 */
public final class AnvilMenu implements InventoryHolder {
	static final int SIZE = 45;
	static final int HEADER = 4;
	static final int WEAPON = 11;
	static final int ARROW = 13;
	static final int RESULT = 15;
	static final int COST = 20;
	static final int DROPS = 22;
	static final int METER = 24;
	static final int[] BUTTON = {29, 30, 31, 32, 33};
	static final int BUTTON_FACE = 31;
	static final int[] PIPS = {38, 39, 40, 41, 42};
	private static final int ANIMATION_TICKS = 32;
	private static final Key GUI_FONT = Key.key(Keys.PACK_NAMESPACE, "gui");
	private static final TextColor BLOOD = BloodLevels.BLOOD;
	private static final TextColor DEEP = BloodLevels.DEEP;
	private static final TextColor PALE = TextColor.color(0xE2B8B3);

	/** What the Bleed Weapon button says, and whether it works. */
	enum State {
		/** No weapon yet. */
		EMPTY("button_idle", "BLEED WEAPON", Material.GRAY_STAINED_GLASS_PANE),
		/** A weapon that can't be bled (disabled on this server). */
		CANNOT("button_cannot", "CANNOT BLEED", Material.BARRIER),
		NEED("button_need", "NEED MORE BLOOD", Material.GLASS_BOTTLE),
		READY("button_ready", "BLEED WEAPON · READY", Material.REDSTONE_BLOCK),
		BLEEDING("button_bleeding", "BLEEDING...", Material.MAGMA_BLOCK),
		MAX("button_max", "MAXIMUM LEVEL", Material.NETHER_STAR);

		final String model;
		final String label;
		final Material fallback;

		State(String model, String label, Material fallback) {
			this.model = model;
			this.label = label;
			this.fallback = fallback;
		}
	}

	private final BloodAnvils service;
	private final UUID owner;
	private final Location anvil;
	private final boolean art;
	private final Inventory inventory;
	/** The real weapon in the anvil, or null. */
	ItemStack weapon;
	/** The real Blood Drops in the anvil, or null. */
	ItemStack drops;
	private boolean processing;
	private int animation = -1;
	/** The level the weapon was at when the running animation started. */
	private int animatedFrom;
	private long lastSound;
	private boolean closed;

	AnvilMenu(BloodAnvils service, Player player, Location anvil) {
		this.service = service;
		this.owner = player.getUniqueId();
		this.anvil = anvil;
		this.art = PackState.hasPack(player);
		Component title = Component.text("Blood Anvil", NamedTextColor.DARK_RED);
		if (art) {
			// With the pack, the title draws the anvil's backdrop behind the slots (a font glyph).
			title = Component.text("", NamedTextColor.WHITE).font(GUI_FONT)
				.append(Component.text("Blood Anvil", TextColor.color(0xE0303C)).font(Key.key("minecraft", "default")));
		}
		this.inventory = Bukkit.createInventory(this, SIZE, title);
		render();
	}

	@Override
	public Inventory getInventory() {
		return inventory;
	}

	UUID owner() {
		return owner;
	}

	Location anvil() {
		return anvil;
	}

	boolean processing() {
		return processing;
	}

	boolean closed() {
		return closed;
	}

	void markClosed() {
		closed = true;
	}

	/** The real items it holds, for the escrow and for giving back. */
	List<ItemStack> contents() {
		List<ItemStack> out = new ArrayList<>(2);
		if (weapon != null && !weapon.isEmpty()) {
			out.add(weapon);
		}
		if (drops != null && !drops.isEmpty()) {
			out.add(drops);
		}
		return out;
	}

	void clearContents() {
		weapon = null;
		drops = null;
	}

	// ---- state ---------------------------------------------------------------------------------

	private int level() {
		return weapon == null ? 0 : BloodLevels.level(weapon);
	}

	private int cost() {
		return weapon == null ? BloodLevels.cost(0) : BloodLevels.cost(level());
	}

	private int dropCount() {
		return drops == null ? 0 : drops.getAmount();
	}

	State state() {
		if (processing) {
			return State.BLEEDING;
		}
		if (weapon == null) {
			return State.EMPTY;
		}
		WeaponType type = Weapons.typeOf(weapon);
		if (type == null || !Settings.get().enabled(type) || !Settings.get().blood.anvil().enabled()) {
			return State.CANNOT;
		}
		if (BloodLevels.isMax(level())) {
			return State.MAX;
		}
		return dropCount() >= cost() ? State.READY : State.NEED;
	}

	// ---- clicks ----------------------------------------------------------------------------------

	/** A click on the menu itself. The event is already cancelled; this is the only thing that happens. */
	void clickTop(Player player, int slot, ClickType click, int hotbar) {
		if (processing) {
			if (slot == WEAPON || slot == DROPS || isButton(slot)) {
				deny(player, "Wait: the anvil is still bleeding.");
			}
			return;
		}
		if (slot == WEAPON) {
			clickWeapon(player, click, hotbar);
		} else if (slot == DROPS) {
			clickDrops(player, click, hotbar);
		} else if (isButton(slot)) {
			bleed(player);
		}
	}

	private static boolean isButton(int slot) {
		for (int b : BUTTON) {
			if (b == slot) {
				return true;
			}
		}
		return false;
	}

	private void clickWeapon(Player player, ClickType click, int hotbar) {
		ItemStack cursor = player.getItemOnCursor();
		switch (click) {
			case LEFT, RIGHT -> {
				if (cursor.isEmpty()) {
					if (weapon != null) {
						player.setItemOnCursor(weapon);
						weapon = null;
						changed(player, false, BloodFx.PAGE, 0.6F);
					}
				} else if (isBleedable(cursor)) {
					ItemStack previous = weapon;
					weapon = cursor.clone();
					player.setItemOnCursor(previous);
					changed(player, true, "item.armor.equip_netherite", 0.8F);
				} else {
					deny(player, cursor.getType() == Material.AIR ? null : notAWeapon(cursor));
				}
			}
			case SHIFT_LEFT, SHIFT_RIGHT -> {
				if (weapon != null) {
					ItemStack left = give(player.getInventory(), weapon);
					if (left == null) {
						weapon = null;
						changed(player, false, BloodFx.PAGE, 0.6F);
					} else {
						deny(player, "Your inventory is full.");
					}
				}
			}
			case NUMBER_KEY -> swapWith(player, hotbar, true);
			case SWAP_OFFHAND -> swapWith(player, 40, true);
			case DROP, CONTROL_DROP -> {
				if (weapon != null) {
					drop(player, weapon);
					weapon = null;
					changed(player, false, "entity.item.pickup", 0.6F);
				}
			}
			default -> {
				// double clicks, middle clicks, creative picks: nothing
			}
		}
	}

	private void clickDrops(Player player, ClickType click, int hotbar) {
		ItemStack cursor = player.getItemOnCursor();
		switch (click) {
			case LEFT -> {
				if (cursor.isEmpty()) {
					if (drops != null) {
						player.setItemOnCursor(drops);
						drops = null;
						changed(player, false, "item.bottle.fill", 0.7F);
					}
				} else if (BloodDrop.isDrop(cursor)) {
					if (drops == null) {
						drops = cursor.clone();
						player.setItemOnCursor(null);
					} else if (drops.isSimilar(cursor)) {
						int room = drops.getMaxStackSize() - drops.getAmount();
						int moved = Math.min(room, cursor.getAmount());
						if (moved <= 0) {
							return;
						}
						drops.setAmount(drops.getAmount() + moved);
						ItemStack rest = cursor.clone();
						rest.setAmount(cursor.getAmount() - moved);
						player.setItemOnCursor(rest.getAmount() > 0 ? rest : null);
					} else {
						ItemStack previous = drops;
						drops = cursor.clone();
						player.setItemOnCursor(previous);
					}
					changed(player, true, BloodFx.SQUELCH, 1.5F);
				} else {
					deny(player, "Only Blood Drops go here.");
				}
			}
			case RIGHT -> {
				if (cursor.isEmpty()) {
					if (drops != null) {
						int half = (drops.getAmount() + 1) / 2;
						ItemStack taken = drops.clone();
						taken.setAmount(half);
						drops.setAmount(drops.getAmount() - half);
						if (drops.getAmount() <= 0) {
							drops = null;
						}
						player.setItemOnCursor(taken);
						changed(player, false, "item.bottle.fill", 0.8F);
					}
				} else if (BloodDrop.isDrop(cursor)) {
					if (drops != null && (!drops.isSimilar(cursor) || drops.getAmount() >= drops.getMaxStackSize())) {
						return;
					}
					if (drops == null) {
						drops = cursor.asOne();
					} else {
						drops.setAmount(drops.getAmount() + 1);
					}
					ItemStack rest = cursor.clone();
					rest.setAmount(cursor.getAmount() - 1);
					player.setItemOnCursor(rest.getAmount() > 0 ? rest : null);
					changed(player, true, BloodFx.SQUELCH, 1.7F);
				} else {
					deny(player, "Only Blood Drops go here.");
				}
			}
			case SHIFT_LEFT, SHIFT_RIGHT -> {
				if (drops != null) {
					ItemStack left = give(player.getInventory(), drops);
					drops = left;
					changed(player, false, "item.bottle.fill", 0.7F);
				}
			}
			case NUMBER_KEY -> swapWith(player, hotbar, false);
			case SWAP_OFFHAND -> swapWith(player, 40, false);
			case DROP -> {
				if (drops != null) {
					drop(player, drops.asOne());
					drops.setAmount(drops.getAmount() - 1);
					if (drops.getAmount() <= 0) {
						drops = null;
					}
					changed(player, false, "entity.item.pickup", 0.6F);
				}
			}
			case CONTROL_DROP -> {
				if (drops != null) {
					drop(player, drops);
					drops = null;
					changed(player, false, "entity.item.pickup", 0.6F);
				}
			}
			default -> {
			}
		}
	}

	/** The number keys and the off-hand key: swap the slot with that inventory slot. */
	private void swapWith(Player player, int inventorySlot, boolean weaponSlot) {
		if (inventorySlot < 0) {
			return;
		}
		PlayerInventory inv = player.getInventory();
		ItemStack other = inv.getItem(inventorySlot);
		boolean otherEmpty = other == null || other.isEmpty();
		ItemStack mine = weaponSlot ? weapon : drops;
		if (!otherEmpty && !(weaponSlot ? isBleedable(other) : BloodDrop.isDrop(other))) {
			deny(player, weaponSlot ? notAWeapon(other) : "Only Blood Drops go here.");
			return;
		}
		if (otherEmpty && mine == null) {
			return;
		}
		ItemStack incoming = otherEmpty ? null : other.clone();
		inv.setItem(inventorySlot, mine);
		if (weaponSlot) {
			weapon = incoming;
		} else {
			drops = incoming;
		}
		changed(player, incoming != null, weaponSlot ? "item.armor.equip_netherite" : BloodFx.SQUELCH, weaponSlot ? 0.8F : 1.5F);
	}

	/** Shift-click from the player's inventory: a weapon or drops go into their slot, anything else is refused. */
	void shiftIn(Player player, int inventorySlot) {
		if (processing) {
			deny(player, "Wait: the anvil is still bleeding.");
			return;
		}
		PlayerInventory inv = player.getInventory();
		ItemStack item = inv.getItem(inventorySlot);
		if (item == null || item.isEmpty()) {
			return;
		}
		if (isBleedable(item)) {
			if (weapon != null) {
				deny(player, "There's already a weapon on the anvil.");
				return;
			}
			weapon = item.clone();
			inv.setItem(inventorySlot, null);
			changed(player, true, "item.armor.equip_netherite", 0.8F);
		} else if (BloodDrop.isDrop(item)) {
			if (drops == null) {
				drops = item.clone();
				inv.setItem(inventorySlot, null);
			} else if (drops.isSimilar(item)) {
				int moved = Math.min(drops.getMaxStackSize() - drops.getAmount(), item.getAmount());
				if (moved <= 0) {
					deny(player, "The Blood Drop slot is full.");
					return;
				}
				drops.setAmount(drops.getAmount() + moved);
				ItemStack rest = item.clone();
				rest.setAmount(item.getAmount() - moved);
				inv.setItem(inventorySlot, rest.getAmount() > 0 ? rest : null);
			} else {
				deny(player, "The Blood Drop slot is full.");
				return;
			}
			changed(player, true, BloodFx.SQUELCH, 1.5F);
		} else {
			deny(player, notAWeapon(item));
		}
	}

	private static boolean isBleedable(ItemStack item) {
		return item != null && item.getAmount() == 1 && Weapons.isWeapon(item)
			&& !item.getPersistentDataContainer().has(Keys.GUI, PersistentDataType.BYTE);
	}

	private static String notAWeapon(ItemStack item) {
		if (BloodDrop.isDrop(item)) {
			return "Blood Drops go in the slot below.";
		}
		return Weapons.isWeapon(item) ? "One weapon at a time." : "Only Bloodbath weapons can be bled. This is a "
			+ item.getType().getKey().getKey().replace('_', ' ') + ".";
	}

	/** Adds to the inventory; returns what didn't fit (or null). */
	private static ItemStack give(PlayerInventory inventory, ItemStack stack) {
		var left = inventory.addItem(stack.clone());
		return left.isEmpty() ? null : left.values().iterator().next();
	}

	private static void drop(Player player, ItemStack stack) {
		Location eye = player.getEyeLocation();
		player.getWorld().dropItem(eye.clone().subtract(0, 0.3, 0), stack.clone(), item -> {
			item.setThrower(player.getUniqueId());
			item.setVelocity(eye.getDirection().multiply(0.3));
			item.setPickupDelay(40);
		});
	}

	// ---- bleeding --------------------------------------------------------------------------------------

	/** The button. Checks everything again, then takes the drops and raises the level in one go. */
	private void bleed(Player player) {
		State state = state();
		switch (state) {
			case EMPTY -> {
				deny(player, "Put a Bloodbath weapon on the anvil first.");
				return;
			}
			case CANNOT -> {
				deny(player, "This weapon can't be bled on this server.");
				return;
			}
			case MAX -> {
				deny(player, "This weapon can't take any more blood.");
				return;
			}
			case NEED -> {
				deny(player, "It needs " + cost() + " Blood Drops; there " + (dropCount() == 1 ? "is " : "are ") + dropCount() + ".");
				return;
			}
			default -> {
			}
		}
		if (!player.hasPermission("bloodbath.anvil")) {
			deny(player, "You don't have permission to use the Blood Anvil.");
			return;
		}
		// Locked from here until the animation ends: a second click (or a double click) does nothing.
		processing = true;
		int from = level();
		int cost = cost();
		if (weapon == null || Weapons.typeOf(weapon) == null || BloodLevels.isMax(from) || drops == null
			|| !BloodDrop.isDrop(drops) || drops.getAmount() < cost) {
			processing = false;
			fail(player);
			return;
		}
		drops.setAmount(drops.getAmount() - cost);
		if (drops.getAmount() <= 0) {
			drops = null;
		}
		BloodLevels.set(weapon, from + 1);
		service.persist(this, player, false);
		animatedFrom = from;
		animation = 0;
		play(player, "block.anvil.use", 0.7F, 0.55F, true);
		play(player, BloodFx.HEARTBEAT, 1.0F, 0.9F, true);
		render();
	}

	private void fail(Player player) {
		play(player, "block.fire.extinguish", 0.7F, 0.6F, true);
		play(player, "block.anvil.land", 0.3F, 0.5F, true);
		render();
	}

	/** Called every tick while the menu is open. */
	void tick(Player player) {
		if (animation < 0) {
			return;
		}
		int t = animation++;
		BloodFx.Fx fx = t % 2 == 0 ? BloodFx.BLOOD_FADE : BloodFx.MOTE;
		Location top = anvil.clone().add(0.5, 1.1, 0.5);
		if (t % 3 == 0) {
			double angle = t * 0.5;
			BloodFx.burst(top.clone().add(Math.cos(angle) * 0.5, t * 0.02, Math.sin(angle) * 0.5), fx, 3, 0.05, 0.01);
			BloodFx.burst(top, BloodFx.DRIP, 1, 0.25, 0.0);
		}
		if (t == 10) {
			play(player, BloodFx.SQUELCH, 0.8F, 0.7F, true);
		}
		if (t == 20) {
			play(player, BloodFx.HEARTBEAT, 1.0F, 1.1F, true);
		}
		if (t % 4 == 0) {
			render();
		}
		if (t >= ANIMATION_TICKS) {
			animation = -1;
			processing = false;
			int now = level();
			BloodFx.burst(top, BloodFx.BLOOD_LARGE, 26, 0.4);
			BloodFx.burst(top, BloodFx.RING, 8, 0.3);
			play(player, "block.anvil.use", 0.6F, 0.8F, true);
			play(player, "entity.player.levelup", 0.6F, 0.6F, true);
			WeaponType type = weapon == null ? null : Weapons.typeOf(weapon);
			if (type != null) {
				player.sendMessage(Settings.get().prefix.append(Component.text("Your ", NamedTextColor.GRAY))
					.append(Component.text(type.displayName(), NamedTextColor.RED))
					.append(Component.text(" drinks deep. Blood Level ", NamedTextColor.GRAY))
					.append(Component.text(BloodLevels.numeral(now), BLOOD))
					.append(Component.text(".", NamedTextColor.GRAY)));
			}
			render();
		}
	}

	boolean animating() {
		return animation >= 0;
	}

	/** After a real item moved: redraw, save the escrow (and the player, if items left them), and a sound. */
	private void changed(Player player, boolean tookFromPlayer, String sound, float pitch) {
		service.persist(this, player, tookFromPlayer);
		play(player, sound, 0.6F, pitch, false);
		render();
	}

	private void deny(Player player, String message) {
		if (message != null) {
			player.sendActionBar(Component.text(message, NamedTextColor.RED));
		}
		play(player, "block.note_block.bass", 0.6F, 0.6F, false);
	}

	/** Menu sounds, to this player only and never more than one every two ticks (unless it matters). */
	private void play(Player player, String sound, float volume, float pitch, boolean always) {
		long now = ServerClock.now();
		if (!always && now - lastSound < 2) {
			return;
		}
		lastSound = now;
		BloodFx.playTo(player, sound, volume, pitch);
	}

	// ---- drawing ------------------------------------------------------------------------------------

	/** Redraws every slot from the real state. */
	void render() {
		State state = state();
		int level = level();
		int max = Settings.get().blood.maxLevel();
		WeaponType type = weapon == null ? null : Weapons.typeOf(weapon);
		int cost = cost();

		set(HEADER, header());
		set(WEAPON, weapon == null ? weaponPlaceholder() : weaponCopy(type, level));
		set(ARROW, arrow(state, type, level));
		set(RESULT, result(state, type, level));
		set(COST, costItem(state, cost));
		set(DROPS, drops == null ? dropsPlaceholder(cost) : dropsCopy(cost));
		set(METER, meter(state, cost));
		ItemStack face = button(state, true, level, cost);
		ItemStack side = button(state, false, level, cost);
		for (int slot : BUTTON) {
			set(slot, slot == BUTTON_FACE ? face : side);
		}
		for (int i = 0; i < PIPS.length; i++) {
			set(PIPS[i], i < max && max <= PIPS.length ? pip(i + 1, level) : i == 2 && max > PIPS.length ? levelBadge(level, max) : null);
		}
		if (!art) {
			ItemStack pane = pane();
			for (int slot = 0; slot < SIZE; slot++) {
				if (inventory.getItem(slot) == null) {
					inventory.setItem(slot, pane);
				}
			}
		}
	}

	private void set(int slot, ItemStack item) {
		ItemStack current = inventory.getItem(slot);
		if (item == null ? current != null : !item.equals(current)) {
			inventory.setItem(slot, item);
		}
	}

	private ItemStack header() {
		BloodConfig config = Settings.get().blood;
		List<Component> lore = new ArrayList<>();
		lore.add(t("Place a Bloodbath weapon and", NamedTextColor.GRAY));
		lore.add(t("Blood Drops, then bleed the weapon.", NamedTextColor.GRAY));
		lore.add(Component.empty());
		lore.add(t("Each Blood Level adds to the weapon", NamedTextColor.GRAY));
		lore.add(t("itself: harder hits, stronger ability,", NamedTextColor.GRAY));
		lore.add(t("a chance to open wounds.", NamedTextColor.GRAY));
		lore.add(Component.empty());
		int total = 0;
		for (int l = 1; l <= config.maxLevel(); l++) {
			total += config.level(l).cost();
		}
		lore.add(t(config.maxLevel() + " levels · " + total + " Blood Drops from nothing to max", DEEP));
		return gui("header", Material.ANVIL, t("BLOOD ANVIL", BLOOD).decorate(TextDecoration.BOLD), lore, 1);
	}

	private ItemStack weaponPlaceholder() {
		List<Component> lore = new ArrayList<>();
		lore.add(t("Place an existing Bloodbath", NamedTextColor.GRAY));
		lore.add(t("weapon here.", NamedTextColor.GRAY));
		lore.add(Component.empty());
		lore.add(t("Its stats, ability, kills and", NamedTextColor.DARK_GRAY));
		lore.add(t("enchantments are all kept.", NamedTextColor.DARK_GRAY));
		return gui("weapon_ghost", Material.LIGHT_GRAY_STAINED_GLASS_PANE, t("BLOOD WEAPON", BLOOD).decorate(TextDecoration.BOLD), lore, 1);
	}

	/** The weapon exactly as it is (its own tooltip), with its Blood Infusion written underneath. */
	private ItemStack weaponCopy(WeaponType type, int level) {
		ItemStack copy = weapon.clone();
		copy.editMeta(meta -> {
			List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
			lore.add(t("──────────────", NamedTextColor.DARK_GRAY));
			lore.add(t("Blood Infusion", BLOOD).decorate(TextDecoration.BOLD));
			lore.add(t("Blood Level: ", NamedTextColor.GRAY).append(t(BloodLevels.numeral(level) + "  ", BLOOD))
				.append(t(BloodLevels.pips(level), DEEP)));
			if (type != null) {
				if (BloodLevels.isMax(level)) {
					lore.add(t("Next Level: ", NamedTextColor.GRAY).append(t("none (maximum)", NamedTextColor.DARK_GRAY)));
				} else {
					lore.add(t("Next Level: ", NamedTextColor.GRAY).append(t(BloodLevels.numeral(level + 1), PALE)));
				}
				lore.add(t("Blood Bonus: ", NamedTextColor.GRAY).append(t(level == 0 ? "none yet" : BloodLevels.effects(type, level), PALE)));
				lore.add(t(damageLabel(type) + ": ", NamedTextColor.GRAY).append(t(BloodLevels.fmt(BloodLevels.damageAt(type, level)), PALE)));
				lore.add(t("Bleeding: ", NamedTextColor.GRAY).append(t(BloodLevels.percent(BloodLevels.bleedChance(level)) + " on hit", PALE)));
				if (!BloodLevels.isMax(level)) {
					int cost = BloodLevels.cost(level);
					lore.add(t("Upgrade Cost: ", NamedTextColor.GRAY).append(t(cost + (cost == 1 ? " Blood Drop" : " Blood Drops"), BLOOD)));
				}
			}
			lore.add(Component.empty());
			lore.add(t("Click to take it back", NamedTextColor.DARK_GRAY));
			meta.lore(lore);
			mark(meta);
		});
		return copy;
	}

	private static String damageLabel(WeaponType type) {
		return type == WeaponType.PARADOX_BOW ? "Arrow bonus" : "Melee damage";
	}

	private ItemStack arrow(State state, WeaponType type, int level) {
		List<Component> lore = new ArrayList<>();
		if (type == null) {
			lore.add(t("Place a weapon to see what", NamedTextColor.GRAY));
			lore.add(t("the blood would make of it.", NamedTextColor.GRAY));
		} else if (BloodLevels.isMax(level)) {
			lore.add(t("Blood Level " + BloodLevels.numeral(level) + ": nothing left to give.", NamedTextColor.GRAY));
		} else {
			lore.add(t("Blood Level ", NamedTextColor.GRAY).append(t(BloodLevels.numeral(level), PALE))
				.append(t("  →  ", NamedTextColor.DARK_GRAY)).append(t(BloodLevels.numeral(level + 1), BLOOD)));
		}
		boolean lit = state == State.READY || state == State.BLEEDING;
		return gui(lit ? "arrow_ready" : "arrow_idle", Material.ARROW, t("→", lit ? BLOOD : NamedTextColor.DARK_GRAY), lore, 1);
	}

	/** The preview: what the weapon becomes, built from the real weapon but never the real thing. */
	private ItemStack result(State state, WeaponType type, int level) {
		if (type == null) {
			List<Component> lore = List.of(t("The bled weapon appears here", NamedTextColor.GRAY),
				t("once you place one.", NamedTextColor.GRAY));
			return gui("result_ghost", Material.LIGHT_GRAY_STAINED_GLASS_PANE, t("RESULT", BLOOD).decorate(TextDecoration.BOLD), lore, 1);
		}
		boolean max = BloodLevels.isMax(level);
		int next = max ? level : level + 1;
		ItemStack preview = weapon.clone();
		if (!max) {
			BloodLevels.set(preview, next);
		}
		preview.editMeta(meta -> {
			List<Component> lore = new ArrayList<>();
			lore.add(t(max ? "ALREADY AT THE MAXIMUM" : "RESULT", BLOOD).decorate(TextDecoration.BOLD));
			lore.add(t(type.displayName(), NamedTextColor.RED));
			lore.add(t("Blood Level: ", NamedTextColor.GRAY).append(t(BloodLevels.numeral(next) + "  ", BLOOD)).append(t(BloodLevels.pips(next), DEEP)));
			lore.add(Component.empty());
			lore.add(compare(damageLabel(type), BloodLevels.fmt(BloodLevels.damageAt(type, level)), BloodLevels.fmt(BloodLevels.damageAt(type, next)), max));
			lore.add(compare("Ability damage", "+" + BloodLevels.percent(BloodLevels.abilityPower(level)),
				"+" + BloodLevels.percent(BloodLevels.abilityPower(next)), max));
			lore.add(compare("Bleeding chance", BloodLevels.percent(BloodLevels.bleedChance(level)),
				BloodLevels.percent(BloodLevels.bleedChance(next)), max));
			lore.add(Component.empty());
			lore.add(t("Everything else about it stays.", NamedTextColor.DARK_GRAY));
			if (state == State.READY) {
				lore.add(t("Press BLEED WEAPON to make it so.", DEEP));
			}
			meta.lore(lore);
			meta.setEnchantmentGlintOverride(state == State.READY);
			mark(meta);
		});
		return preview;
	}

	private static Component compare(String label, String before, String after, boolean same) {
		Component line = t(label + ": ", NamedTextColor.GRAY).append(t(before, PALE));
		return same || before.equals(after) ? line : line.append(t("  →  ", NamedTextColor.DARK_GRAY)).append(t(after, BLOOD));
	}

	private ItemStack costItem(State state, int cost) {
		if (weapon == null || cost < 0) {
			List<Component> lore = List.of(t(weapon == null ? "Place a weapon to see its cost." : "Nothing more to pay.", NamedTextColor.GRAY));
			return gui("cost_none", Material.GRAY_DYE, t("COST", BLOOD).decorate(TextDecoration.BOLD), lore, 1);
		}
		List<Component> lore = new ArrayList<>();
		lore.add(t("Bleeding it to Blood Level " + BloodLevels.numeral(level() + 1), NamedTextColor.GRAY));
		lore.add(t("takes " + cost + (cost == 1 ? " Blood Drop." : " Blood Drops."), NamedTextColor.GRAY));
		ItemStack icon = art ? gui("cost", Material.FIREWORK_STAR, null, lore, cost) : BloodDrop.icon(cost, lore);
		icon.editMeta(meta -> {
			meta.itemName(t("COST: " + cost + (cost == 1 ? " BLOOD DROP" : " BLOOD DROPS"), BLOOD).decorate(TextDecoration.BOLD));
			mark(meta);
		});
		return icon;
	}

	private ItemStack dropsPlaceholder(int cost) {
		List<Component> lore = new ArrayList<>();
		lore.add(t("A rare substance found", NamedTextColor.GRAY));
		lore.add(t("within the Bloodlands.", NamedTextColor.GRAY));
		lore.add(Component.empty());
		if (weapon != null && cost > 0) {
			lore.add(t("Required: ", NamedTextColor.GRAY).append(t(String.valueOf(cost), BLOOD)));
		}
		lore.add(t("Inserted: ", NamedTextColor.GRAY).append(t("0", PALE)));
		return gui("drop_ghost", Material.LIGHT_GRAY_STAINED_GLASS_PANE, t("BLOOD DROP", BLOOD).decorate(TextDecoration.BOLD), lore, 1);
	}

	private ItemStack dropsCopy(int cost) {
		ItemStack copy = drops.clone();
		copy.editMeta(meta -> {
			meta.itemName(t("BLOOD DROP", BLOOD).decorate(TextDecoration.BOLD));
			List<Component> lore = new ArrayList<>();
			lore.add(t("A rare substance found", NamedTextColor.GRAY));
			lore.add(t("within the Bloodlands.", NamedTextColor.GRAY));
			lore.add(Component.empty());
			if (weapon != null && cost > 0) {
				lore.add(t("Required: ", NamedTextColor.GRAY).append(t(String.valueOf(cost), BLOOD)));
			}
			lore.add(t("Inserted: ", NamedTextColor.GRAY).append(t(String.valueOf(drops.getAmount()), PALE)));
			if (weapon != null && cost > 0 && drops.getAmount() > cost) {
				lore.add(t((drops.getAmount() - cost) + " left over stay in the slot.", NamedTextColor.DARK_GRAY));
			}
			lore.add(Component.empty());
			lore.add(t("Click to take them back", NamedTextColor.DARK_GRAY));
			meta.lore(lore);
			mark(meta);
		});
		return copy;
	}

	/** A vial filling with blood: drops in over drops needed. */
	private ItemStack meter(State state, int cost) {
		int have = dropCount();
		int fill;
		if (state == State.MAX || state == State.READY || state == State.BLEEDING) {
			fill = 8;
		} else if (weapon == null || cost <= 0) {
			fill = 0;
		} else {
			fill = Math.min(8, have * 8 / cost);
		}
		List<Component> lore = new ArrayList<>();
		if (weapon == null) {
			lore.add(t("Fills as you add Blood Drops.", NamedTextColor.GRAY));
		} else if (cost < 0) {
			lore.add(t("The weapon is full of blood.", NamedTextColor.GRAY));
		} else {
			lore.add(t("Status: ", NamedTextColor.GRAY).append(t(Math.min(have, cost) + " / " + cost + " Blood Drops", have >= cost ? BLOOD : PALE)));
		}
		return gui("meter_" + fill, fill >= 8 ? Material.RED_STAINED_GLASS : Material.GLASS, t("BLOOD", BLOOD).decorate(TextDecoration.BOLD), lore, 1);
	}

	private ItemStack button(State state, boolean face, int level, int cost) {
		List<Component> lore = new ArrayList<>();
		if (state != State.EMPTY) {
			lore.add(t("Infuse the weapon with blood.", NamedTextColor.GRAY));
			lore.add(Component.empty());
		}
		switch (state) {
			case EMPTY -> {
				lore.add(t("Infuse the weapon with blood.", NamedTextColor.GRAY));
				lore.add(Component.empty());
				lore.add(t("Place a Bloodbath weapon", NamedTextColor.DARK_GRAY));
				lore.add(t("on the anvil to begin.", NamedTextColor.DARK_GRAY));
			}
			case CANNOT -> lore.add(t("This weapon is disabled on this server.", NamedTextColor.RED));
			case MAX -> {
				lore.add(t("Current Level: ", NamedTextColor.GRAY).append(t(BloodLevels.numeral(level), BLOOD)));
				lore.add(Component.empty());
				lore.add(t("It can't take any more blood.", NamedTextColor.DARK_GRAY));
			}
			case NEED, READY, BLEEDING -> {
				int shownLevel = state == State.BLEEDING ? animatedFrom : level;
				lore.add(t("Current Level: ", NamedTextColor.GRAY).append(t(BloodLevels.numeral(shownLevel), PALE)));
				lore.add(t("Next Level: ", NamedTextColor.GRAY).append(t(BloodLevels.numeral(shownLevel + 1), BLOOD)));
				lore.add(Component.empty());
				int showCost = state == State.BLEEDING ? BloodLevels.cost(animatedFrom) : cost;
				lore.add(t("Cost: ", NamedTextColor.GRAY).append(t(showCost + (showCost == 1 ? " Blood Drop" : " Blood Drops"), BLOOD)));
				if (state == State.NEED) {
					lore.add(Component.empty());
					lore.add(t("Status: ", NamedTextColor.GRAY).append(t(dropCount() + " / " + cost + " Blood Drops", PALE)));
					lore.add(Component.empty());
					lore.add(t("Cannot upgrade yet.", NamedTextColor.RED));
				} else if (state == State.READY) {
					lore.add(Component.empty());
					lore.add(t("READY", BLOOD).decorate(TextDecoration.BOLD));
					lore.add(Component.empty());
					lore.add(t("Click to bleed weapon.", NamedTextColor.WHITE));
				} else {
					lore.add(Component.empty());
					lore.add(t("The anvil drinks...", DEEP));
				}
			}
		}
		Component name = t(state.label, state == State.READY || state == State.BLEEDING ? BLOOD
			: state == State.EMPTY ? PALE : NamedTextColor.GRAY).decorate(TextDecoration.BOLD);
		if (!face) {
			// The other four slots of the button: invisible with the pack, the same tooltip everywhere.
			return gui("blank", state == State.READY ? Material.RED_STAINED_GLASS_PANE : Material.BLACK_STAINED_GLASS_PANE, name, lore, 1);
		}
		return gui(state.model, state.fallback, name, lore, 1);
	}

	/** One of the level pips along the bottom, with what that level gives. */
	private ItemStack pip(int pipLevel, int level) {
		BloodConfig.Level config = Settings.get().blood.level(pipLevel);
		boolean reached = pipLevel <= level;
		boolean flashing = animation >= 0 && pipLevel == level && (animation / 3) % 2 == 0;
		boolean next = pipLevel == level + 1 && weapon != null;
		String model = flashing ? "pip_next" : reached ? "pip_full" : next ? "pip_next" : "pip_empty";
		List<Component> lore = new ArrayList<>();
		lore.add(t(reached ? "Reached" : next ? "Next" : "Locked", reached ? BLOOD : next ? PALE : NamedTextColor.DARK_GRAY));
		lore.add(Component.empty());
		lore.add(t("+" + BloodLevels.fmt(config.damageBonus()) + " melee (or arrow) damage", NamedTextColor.GRAY));
		lore.add(t("+" + BloodLevels.percent(config.abilityPower()) + " ability damage", NamedTextColor.GRAY));
		lore.add(t(BloodLevels.percent(config.bleedChance()) + " chance to bleed on hit", NamedTextColor.GRAY));
		lore.add(Component.empty());
		lore.add(t("Costs " + config.cost() + (config.cost() == 1 ? " Blood Drop" : " Blood Drops"), DEEP));
		Material fallback = reached ? Material.RED_DYE : next ? Material.PINK_DYE : Material.GRAY_DYE;
		return gui(model, fallback, t("BLOOD LEVEL " + BloodLevels.numeral(pipLevel), reached ? BLOOD : PALE).decorate(TextDecoration.BOLD), lore, 1);
	}

	/** For servers with more levels than pips: one badge with the level as its count. */
	private ItemStack levelBadge(int level, int max) {
		List<Component> lore = List.of(t("Level " + level + " of " + max, NamedTextColor.GRAY));
		return gui("pip_full", Material.RED_DYE, t("BLOOD LEVEL " + BloodLevels.numeral(level), BLOOD), lore, Math.max(1, level));
	}

	private ItemStack pane() {
		ItemStack pane = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
		pane.editMeta(meta -> {
			meta.setHideTooltip(true);
			mark(meta);
		});
		return pane;
	}

	/**
	 * A picture for the menu: with the pack, a sheet of paper wearing one of the anvil's own models
	 * and the blood tooltip frame; without it, a vanilla item that reads the same. Always tagged, so it
	 * can never pass for anything real.
	 */
	private ItemStack gui(String model, Material fallback, Component name, List<Component> lore, int amount) {
		ItemStack item = new ItemStack(art ? Material.PAPER : fallback, Math.max(1, Math.min(99, amount)));
		item.editMeta(meta -> {
			if (name != null) {
				meta.itemName(name);
			}
			meta.lore(lore);
			meta.setMaxStackSize(99);
			if (art) {
				meta.setItemModel(Keys.pack("gui/" + model));
				meta.setTooltipStyle(Weapons.TOOLTIP_STYLE);
			}
			mark(meta);
		});
		return item;
	}

	private static void mark(org.bukkit.inventory.meta.ItemMeta meta) {
		meta.getPersistentDataContainer().set(Keys.GUI, PersistentDataType.BYTE, (byte) 1);
	}

	static Component t(String text, TextColor color) {
		return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
	}
}
