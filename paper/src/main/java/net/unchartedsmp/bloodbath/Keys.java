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
	}

	/** A key in the resource pack's namespace, e.g. {@code unchartedsmp:riftblade}. */
	public static NamespacedKey pack(String path) {
		return NamespacedKey.fromString(PACK_NAMESPACE + ":" + path);
	}
}
