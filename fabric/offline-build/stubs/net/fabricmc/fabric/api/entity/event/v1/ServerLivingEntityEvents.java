package net.fabricmc.fabric.api.entity.event.v1;
import net.fabricmc.fabric.api.event.Event;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
public final class ServerLivingEntityEvents {
	public static final Event<AfterDeath> AFTER_DEATH = null;
	@FunctionalInterface public interface AfterDeath { void afterDeath(LivingEntity entity, DamageSource damageSource); }
}
