package net.minecraft.entity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;
public abstract class LivingEntity extends Entity {
	public ItemStack getEquippedStack(EquipmentSlot slot) { throw new UnsupportedOperationException(); }
	public void equipStack(EquipmentSlot slot, ItemStack stack) { throw new UnsupportedOperationException(); }
	public ItemStack getStackInHand(Hand hand) { throw new UnsupportedOperationException(); }
	public ItemStack getMainHandStack() { throw new UnsupportedOperationException(); }
	public boolean isUsingItem() { throw new UnsupportedOperationException(); }
	public ItemStack getActiveItem() { throw new UnsupportedOperationException(); }
	public int getItemUseTime() { throw new UnsupportedOperationException(); }
	@Override public boolean damage(ServerWorld world, DamageSource source, float amount) { throw new UnsupportedOperationException(); }
}
