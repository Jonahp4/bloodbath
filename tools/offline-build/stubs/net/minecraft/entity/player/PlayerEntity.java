package net.minecraft.entity.player;
import net.minecraft.entity.LivingEntity;
import net.minecraft.text.Text;
public abstract class PlayerEntity extends LivingEntity {
	public void sendMessage(Text message, boolean overlay) { throw new UnsupportedOperationException(); }
}
