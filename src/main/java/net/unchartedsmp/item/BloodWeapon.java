package net.unchartedsmp.item;

import net.minecraft.particle.ParticleEffect;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.unchartedsmp.ability.Ability;
import net.unchartedsmp.fx.BloodFx;
import net.unchartedsmp.hud.Hud;

/** Anything from the Bloodbath armory: gets the held-weapon aura, status line and kill effects. */
public interface BloodWeapon {
	Ability ability();

	/** Action-bar line shown while this weapon is held. */
	default Text hudStatus(ServerPlayerEntity player) {
		return Hud.cooldownBar(player, ability());
	}

	/** Accent particle mixed into the dripping aura while held. */
	default ParticleEffect auraAccent() {
		return BloodFx.BLOOD;
	}
}
