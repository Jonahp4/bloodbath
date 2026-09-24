package net.unchartedsmp.item;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.world.World;
import net.unchartedsmp.ability.Ability;
import net.unchartedsmp.ability.NullField;

/**
 * Base for every right-click weapon. Handles the boilerplate each item used to copy-paste:
 * server-side only, clot-field suppression, and handing the subclass a {@link ServerWorld} and
 * {@link ServerPlayerEntity}. Subclasses decide when to consult {@code Cooldowns} because some
 * (Bloodrift, Chronos) have a free recast step before the cooldown applies.
 */
public abstract class AbilityWeapon extends Item {
	protected final Ability ability;

	protected AbilityWeapon(Settings settings, Ability ability) {
		super(settings);
		this.ability = ability;
	}

	@Override
	public final ActionResult use(World world, PlayerEntity user, Hand hand) {
		if (!(world instanceof ServerWorld serverWorld) || !(user instanceof ServerPlayerEntity player)) {
			return ActionResult.PASS;
		}
		if (canBeNullified() && NullField.isNullified(player)) {
			NullField.notifyNullified(player);
			return ActionResult.FAIL;
		}
		return useAbility(serverWorld, player);
	}

	/** The Clotblade's own field doesn't stop the Clotblade. */
	protected boolean canBeNullified() {
		return true;
	}

	protected abstract ActionResult useAbility(ServerWorld world, ServerPlayerEntity player);
}
