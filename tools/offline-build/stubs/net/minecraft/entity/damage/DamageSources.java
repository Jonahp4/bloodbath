package net.minecraft.entity.damage;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
public class DamageSources {
	public DamageSource playerAttack(PlayerEntity attacker) { throw new UnsupportedOperationException(); }
	public DamageSource mobAttack(LivingEntity attacker) { throw new UnsupportedOperationException(); }
	public DamageSource generic() { throw new UnsupportedOperationException(); }
	public DamageSource lightningBolt() { throw new UnsupportedOperationException(); }
}
