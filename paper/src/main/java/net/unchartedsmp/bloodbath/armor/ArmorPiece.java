package net.unchartedsmp.bloodbath.armor;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.EquipmentSlotGroup;

/**
 * The Blood Knight's armour, worn by the boss and now by players. Each piece is a netherite piece
 * underneath (fire-proof, same durability) with more protection than netherite, plus a heart.
 */
public enum ArmorPiece {
	HELM("blood_knight_helm", "Blood Knight Helm", "helmet", Material.NETHERITE_HELMET, EquipmentSlot.HEAD, EquipmentSlotGroup.HEAD, 4,
		"The Knight's closed helm. Its eyes", "still burn behind the visor."),
	CUIRASS("blood_knight_cuirass", "Blood Knight Cuirass", "chestplate", Material.NETHERITE_CHESTPLATE, EquipmentSlot.CHEST, EquipmentSlotGroup.CHEST, 9,
		"Its core still glows with the", "Knight's blood."),
	GREAVES("blood_knight_greaves", "Blood Knight Greaves", "leggings", Material.NETHERITE_LEGGINGS, EquipmentSlot.LEGS, EquipmentSlotGroup.LEGS, 7,
		"Plated legs with a glow in each knee."),
	SABATONS("blood_knight_sabatons", "Blood Knight Sabatons", "boots", Material.NETHERITE_BOOTS, EquipmentSlot.FEET, EquipmentSlotGroup.FEET, 4,
		"Iron-shod boots that leave red", "footprints.");

	/**
	 * Every piece beats netherite outright: one more armour point than the netherite piece
	 * (4/9/7/4 against 3/8/6/3), more toughness (3.5 against 3) and more knockback resistance.
	 */
	public static final double TOUGHNESS = 3.5;
	public static final double KNOCKBACK_RESISTANCE = 0.15;
	/** Extra max health per piece, in half-hearts: two hearts for the whole set. */
	public static final double HEALTH = 1.0;

	private static final Map<String, ArmorPiece> BY_NAME = new HashMap<>();

	static {
		for (ArmorPiece piece : values()) {
			BY_NAME.put(piece.id, piece);
			String name = normalize(piece.displayName);
			BY_NAME.put(name, piece);
			BY_NAME.putIfAbsent(name.substring(name.lastIndexOf('_') + 1), piece); // "helm", "greaves", ...
		}
	}

	private final String id;
	private final String displayName;
	private final String kind;
	private final Material base;
	private final EquipmentSlot slot;
	private final EquipmentSlotGroup group;
	private final double armor;
	private final List<String> description;

	ArmorPiece(String id, String displayName, String kind, Material base, EquipmentSlot slot, EquipmentSlotGroup group, double armor, String... description) {
		this.id = id;
		this.displayName = displayName;
		this.kind = kind;
		this.base = base;
		this.slot = slot;
		this.group = group;
		this.armor = armor;
		this.description = List.of(description);
	}

	/** An id, a display name or its last word ("helm"); null if nothing matches. */
	public static ArmorPiece find(String input) {
		return input == null ? null : BY_NAME.get(normalize(input));
	}

	private static String normalize(String text) {
		return text.trim().toLowerCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
	}

	public String id() {
		return id;
	}

	public String displayName() {
		return displayName;
	}

	/** Vanilla's word for the piece: helmet, chestplate, leggings or boots. */
	public String kind() {
		return kind;
	}

	public Material base() {
		return base;
	}

	public EquipmentSlot slot() {
		return slot;
	}

	public EquipmentSlotGroup group() {
		return group;
	}

	public double armor() {
		return armor;
	}

	public List<String> description() {
		return description;
	}
}
