package net.minecraft.item;
import net.minecraft.entity.LivingEntity;
import net.minecraft.world.World;
public class BowItem extends RangedWeaponItem {
	public BowItem(Settings settings) { super(settings); }
	public static float getPullProgress(int useTicks) { throw new UnsupportedOperationException(); }
	@Override public boolean onStoppedUsing(ItemStack stack, World world, LivingEntity user, int remainingUseTicks) { throw new UnsupportedOperationException(); }
}
