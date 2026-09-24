package net.unchartedsmp.item;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.component.ComponentType;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.unchartedsmp.UnchartedSMP;
import net.unchartedsmp.ability.Ability;

/**
 * Item registration. Item ids are unchanged from the original release so existing weapons in
 * player inventories and chests keep working - only names, models and effects were re-themed.
 *
 * <p>Every weapon carries its name (in red) and a short ability tooltip as item components, so
 * players see what it does straight from the inventory, with or without the resource pack.
 */
public final class ModItems {
	public static final RegistryKey<ItemGroup> GROUP_KEY = RegistryKey.of(RegistryKeys.ITEM_GROUP, UnchartedSMP.id("uncharted_smp"));
	private static final List<Item> ALL = new ArrayList<>();

	public static Item RIFTBLADE;
	public static Item BLOODHOOK;
	public static Item NULLBLADE;
	public static Item METEOR_GAUNTLET;
	public static Item GRAVESTONE;
	public static Item CHRONOS;
	public static Item THUNDER_PIKE;
	public static Item MIRRORFANG;
	public static Item VOID_SCYTHE;
	public static Item PARADOX_BOW;

	private ModItems() {
	}

	public static void registerAll() {
		RIFTBLADE = register("riftblade", RiftbladeItem::new, UnaryOperator.identity(), "Bloodrift Blade", Ability.RIFTBLADE,
			"Right-click: tear a bleeding rift ahead", "that drags enemies in. Use again to", "step through it.");
		BLOODHOOK = register("bloodhook", BloodhookItem::new, UnaryOperator.identity(), "Bloodhook", Ability.BLOODHOOK,
			"Right-click: chain the player you're", "looking at and reel yourself in.", "Repeat hooks reach further.");
		NULLBLADE = register("nullblade", NullbladeItem::new, UnaryOperator.identity(), "Clotblade", Ability.NULLBLADE_ZONE,
			"Hits clot a player's blood, suppressing", "their abilities for 4s.", "Right-click: throw down a clot field.");
		METEOR_GAUNTLET = register("meteor_gauntlet", MeteorGauntletItem::new, UnaryOperator.identity(), "Blood Meteor Gauntlet",
			Ability.METEOR_GAUNTLET, "Punch a block or right-click the ground:", "a blood meteor lands 2s later and", "launches everything nearby.");
		GRAVESTONE = register("gravestone", GravestoneItem::new, UnaryOperator.identity(), "Crimson Gravestone", Ability.GRAVESTONE,
			"Right-click: a blood pool drags everything", "within 6 blocks in for 3s, then erupts.");
		CHRONOS = register("chronos", ChronosItem::new, UnaryOperator.identity(), "Bleeding Chronos", Ability.CHRONOS,
			"Right-click: leave a blood-mark.", "Use again within 8s to snap back to it.");
		THUNDER_PIKE = register("thunder_pike", ThunderPikeItem::new, UnaryOperator.identity(), "Crimson Thunder Pike", Ability.THUNDER_PIKE,
			"Right-click: hurl charged blood up to", "22 blocks, call crimson lightning", "and ride the bolt there.");
		MIRRORFANG = register("mirrorfang", MirrorfangItem::new, UnaryOperator.identity(), "Blood Mirrorfang", Ability.MIRRORFANG,
			"Right-click: a blood mirror of you", "fights beside you for 7s.");
		VOID_SCYTHE = register("void_scythe", VoidScytheItem::new, UnaryOperator.identity(), "Hemorrhage Scythe", Ability.VOID_SCYTHE,
			"Every hit makes the target bleed.", "The 5th hemorrhages them and everything", "within 4 blocks.");
		PARADOX_BOW = register("paradox_bow", ParadoxBowItem::new, settings -> settings.maxDamage(384).enchantable(1),
			"Sanguine Paradox Bow", Ability.PARADOX_BOW,
			"A real bow. Fully drawn shots leave", "a Paradox Echo that tears back along", "the arrow's path 3s later.");

		ItemGroup group = FabricItemGroup.builder()
			.icon(() -> new ItemStack(VOID_SCYTHE))
			.displayName(Text.translatable("itemGroup.unchartedsmp.uncharted_smp"))
			.entries((context, entries) -> ALL.forEach(entries::add))
			.build();
		Registry.register(Registries.ITEM_GROUP, GROUP_KEY, group);
	}

	private static Item register(String path, Function<Item.Settings, Item> factory, UnaryOperator<Item.Settings> extra,
		String name, Ability ability, String... lore) {
		RegistryKey<Item> key = RegistryKey.of(RegistryKeys.ITEM, UnchartedSMP.id(path));
		Item.Settings settings = extra.apply(new Item.Settings().registryKey(key).maxCount(1));
		Item item = Registry.register(Registries.ITEM, key, factory.apply(withTooltip(settings, name, ability, lore)));
		ALL.add(item);
		return item;
	}

	/**
	 * Red item name plus lore lines. The component types are looked up by id (Yarn doesn't name
	 * the {@code DataComponentTypes} constants); if either is missing the item simply goes
	 * without it rather than failing to load.
	 */
	private static Item.Settings withTooltip(Item.Settings settings, String name, Ability ability, String[] lore) {
		ComponentType<Text> itemName = componentType("minecraft:item_name");
		ComponentType<LoreComponent> loreType = componentType("minecraft:lore");
		if (itemName != null) {
			settings = settings.component(itemName, Text.literal(name).formatted(Formatting.RED));
		}
		if (loreType != null) {
			List<Text> lines = new ArrayList<>();
			for (String line : lore) {
				lines.add(Text.literal(line).formatted(Formatting.GRAY));
			}
			lines.add(Text.literal(ability.displayName() + " · cooldown " + ability.cooldownLabel()).formatted(Formatting.DARK_RED));
			settings = settings.component(loreType, new LoreComponent(lines));
		}
		return settings;
	}

	@SuppressWarnings("unchecked")
	private static <T> ComponentType<T> componentType(String id) {
		return (ComponentType<T>) Registries.DATA_COMPONENT_TYPE.get(Identifier.of(id));
	}
}
