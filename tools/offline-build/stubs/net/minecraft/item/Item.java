package net.minecraft.item;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.world.World;
public class Item implements ItemConvertible {
	public Item(Settings settings) { throw new UnsupportedOperationException(); }
	@Override public Item asItem() { throw new UnsupportedOperationException(); }
	public ActionResult use(World world, PlayerEntity user, Hand hand) { throw new UnsupportedOperationException(); }
	public void postHit(ItemStack stack, LivingEntity target, LivingEntity attacker) { throw new UnsupportedOperationException(); }
	public static class Settings {
		public Settings() { throw new UnsupportedOperationException(); }
		public Settings registryKey(RegistryKey<Item> key) { throw new UnsupportedOperationException(); }
		public Settings maxCount(int maxCount) { throw new UnsupportedOperationException(); }
		public Settings sword(ToolMaterial material, float attackDamage, float attackSpeed) { throw new UnsupportedOperationException(); }
	}
}
