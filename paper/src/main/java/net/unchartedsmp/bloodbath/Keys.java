package net.unchartedsmp.bloodbath;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

/** Every persistent-data key the plugin writes, plus the resource pack's namespace. */
public final class Keys {
	/** Namespace of the models/textures in the resource pack (shared with the Fabric mod). */
	public static final String PACK_NAMESPACE = "unchartedsmp";

	/** On weapon items: the weapon id. */
	public static NamespacedKey WEAPON;
	/** On Blood Knight armour: the piece id. */
	public static NamespacedKey ARMOR;
	/** On weapon items: which definition revision built the item's name/lore/attributes. */
	public static NamespacedKey REVISION;
	/** On weapon items: kills made with it. */
	public static NamespacedKey KILLS;
	/** On Blood Mirror armor stands. */
	public static NamespacedKey MIRROR;
	/** On players: 1 when they turned the action-bar HUD off. */
	public static NamespacedKey HUD_OFF;
	/** On Blood Cores. */
	public static NamespacedKey CORE;
	/** On every entity that makes up a Blood Knight boss (its body and its model's parts). */
	public static NamespacedKey BOSS;
	/** On the Blood Knight's thralls (raised in its last stand), so they go when it does. */
	public static NamespacedKey THRALL;
	/** On players, for this connection only: the hash of the Bloodbath pack their game loaded. */
	public static NamespacedKey PACK;
	/** On players, kept: "on" or "off" when they chose the pack visuals themselves (/bb visuals). */
	public static NamespacedKey VISUALS;
	/** On weapon items: the Blood Level the Blood Anvil has bled into it (0 when absent). */
	public static NamespacedKey BLOOD_LEVEL;
	/** On Blood Drops. */
	public static NamespacedKey BLOOD_DROP;
	/** On Bloodstone Frame items (the Bloodlands portal frame). */
	public static NamespacedKey FRAME_ITEM;
	/** On Blood Anvil items. */
	public static NamespacedKey ANVIL_ITEM;
	/** On chunks: the frame blocks placed in them (packed positions), so a frame is known by more than its look. */
	public static NamespacedKey FRAMES;
	/** On chunks: the Blood Anvils standing in them (packed positions). */
	public static NamespacedKey ANVILS;
	/** On the display entities Bloodbath shows at portals and anvils (never saved: respawned with their chunk). */
	public static NamespacedKey FIXTURE;
	/** On every item a Bloodbath menu shows: a picture, never a real item. Anything carrying it is destroyed on sight outside a menu. */
	public static NamespacedKey GUI;
	/** On players: where the portal they took into the Bloodlands stands, to send them back there. */
	public static NamespacedKey RETURN;
	/** On Bloodlands elite mobs ("Bloodbound"), which carry Blood Drops. */
	public static NamespacedKey BLOODBOUND;
	/** On arrows shot from a bled bow: the bow's Blood Level. */
	public static NamespacedKey ARROW_BLOOD;

	private Keys() {
	}

	static void init(Plugin plugin) {
		WEAPON = new NamespacedKey(plugin, "weapon");
		ARMOR = new NamespacedKey(plugin, "armor");
		REVISION = new NamespacedKey(plugin, "revision");
		KILLS = new NamespacedKey(plugin, "kills");
		MIRROR = new NamespacedKey(plugin, "mirror");
		HUD_OFF = new NamespacedKey(plugin, "hud_off");
		CORE = new NamespacedKey(plugin, "core");
		BOSS = new NamespacedKey(plugin, "boss");
		THRALL = new NamespacedKey(plugin, "thrall");
		PACK = new NamespacedKey(plugin, "pack_loaded");
		VISUALS = new NamespacedKey(plugin, "visuals");
		BLOOD_LEVEL = new NamespacedKey(plugin, "blood_level");
		BLOOD_DROP = new NamespacedKey(plugin, "blood_drop");
		FRAME_ITEM = new NamespacedKey(plugin, "bloodstone_frame");
		ANVIL_ITEM = new NamespacedKey(plugin, "blood_anvil");
		FRAMES = new NamespacedKey(plugin, "frames");
		ANVILS = new NamespacedKey(plugin, "anvils");
		FIXTURE = new NamespacedKey(plugin, "fixture");
		GUI = new NamespacedKey(plugin, "gui");
		RETURN = new NamespacedKey(plugin, "return");
		BLOODBOUND = new NamespacedKey(plugin, "bloodbound");
		ARROW_BLOOD = new NamespacedKey(plugin, "arrow_blood");
	}

	/** A key in the resource pack's namespace, e.g. {@code unchartedsmp:riftblade}. */
	public static NamespacedKey pack(String path) {
		return NamespacedKey.fromString(PACK_NAMESPACE + ":" + path);
	}
}
