package net.unchartedsmp.item;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.unchartedsmp.UnchartedSMP;

/**
 * Item registration. Item ids are unchanged from the original release so existing weapons in
 * player inventories and chests keep working - only names, models and effects were re-themed.
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
		RIFTBLADE = register("riftblade", RiftbladeItem::new);
		BLOODHOOK = register("bloodhook", BloodhookItem::new);
		NULLBLADE = register("nullblade", NullbladeItem::new);
		METEOR_GAUNTLET = register("meteor_gauntlet", MeteorGauntletItem::new);
		GRAVESTONE = register("gravestone", GravestoneItem::new);
		CHRONOS = register("chronos", ChronosItem::new);
		THUNDER_PIKE = register("thunder_pike", ThunderPikeItem::new);
		MIRRORFANG = register("mirrorfang", MirrorfangItem::new);
		VOID_SCYTHE = register("void_scythe", VoidScytheItem::new);
		PARADOX_BOW = register("paradox_bow", ParadoxBowItem::new);

		ItemGroup group = FabricItemGroup.builder()
			.icon(() -> new ItemStack(VOID_SCYTHE))
			.displayName(Text.translatable("itemGroup.unchartedsmp.uncharted_smp"))
			.entries((context, entries) -> ALL.forEach(entries::add))
			.build();
		Registry.register(Registries.ITEM_GROUP, GROUP_KEY, group);
	}

	private static Item register(String path, Function<Item.Settings, Item> factory) {
		RegistryKey<Item> key = RegistryKey.of(RegistryKeys.ITEM, UnchartedSMP.id(path));
		Item item = Registry.register(Registries.ITEM, key, factory.apply(new Item.Settings().registryKey(key).maxCount(1)));
		ALL.add(item);
		return item;
	}
}
