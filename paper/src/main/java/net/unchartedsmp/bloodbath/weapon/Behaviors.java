package net.unchartedsmp.bloodbath.weapon;

import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import net.unchartedsmp.bloodbath.weapon.behavior.BloodGrimoire;
import net.unchartedsmp.bloodbath.weapon.behavior.Bloodhook;
import net.unchartedsmp.bloodbath.weapon.behavior.Chronos;
import net.unchartedsmp.bloodbath.weapon.behavior.Clotblade;
import net.unchartedsmp.bloodbath.weapon.behavior.Gravestone;
import net.unchartedsmp.bloodbath.weapon.behavior.HemorrhageScythe;
import net.unchartedsmp.bloodbath.weapon.behavior.MeteorGauntlet;
import net.unchartedsmp.bloodbath.weapon.behavior.Mirrorfang;
import net.unchartedsmp.bloodbath.weapon.behavior.ParadoxBow;
import net.unchartedsmp.bloodbath.weapon.behavior.Riftblade;
import net.unchartedsmp.bloodbath.weapon.behavior.ThunderPike;
import net.unchartedsmp.bloodbath.weapon.behavior.VampireFang;

/** One behavior instance per weapon type. */
public final class Behaviors {
	private static final Map<WeaponType, WeaponBehavior> BY_TYPE = new EnumMap<>(WeaponType.class);

	public static final Riftblade RIFTBLADE = register(new Riftblade());
	public static final Bloodhook BLOODHOOK = register(new Bloodhook());
	public static final Clotblade CLOTBLADE = register(new Clotblade());
	public static final MeteorGauntlet METEOR_GAUNTLET = register(new MeteorGauntlet());
	public static final Gravestone GRAVESTONE = register(new Gravestone());
	public static final Chronos CHRONOS = register(new Chronos());
	public static final ThunderPike THUNDER_PIKE = register(new ThunderPike());
	public static final Mirrorfang MIRRORFANG = register(new Mirrorfang());
	public static final HemorrhageScythe HEMORRHAGE_SCYTHE = register(new HemorrhageScythe());
	public static final ParadoxBow PARADOX_BOW = register(new ParadoxBow());
	public static final VampireFang VAMPIRE_FANG = register(new VampireFang());
	public static final BloodGrimoire BLOOD_GRIMOIRE = register(new BloodGrimoire());

	private Behaviors() {
	}

	private static <T extends WeaponBehavior> T register(T behavior) {
		if (BY_TYPE.put(behavior.type(), behavior) != null) {
			throw new IllegalStateException("Two behaviors for " + behavior.type());
		}
		return behavior;
	}

	public static WeaponBehavior of(WeaponType type) {
		return BY_TYPE.get(type);
	}

	public static Collection<WeaponBehavior> all() {
		return BY_TYPE.values();
	}

	public static void tick(long now) {
		for (WeaponBehavior behavior : BY_TYPE.values()) {
			behavior.tick(now);
		}
	}

	public static void forget(UUID playerId) {
		for (WeaponBehavior behavior : BY_TYPE.values()) {
			behavior.forget(playerId);
		}
	}

	public static void prune() {
		for (WeaponBehavior behavior : BY_TYPE.values()) {
			behavior.prune();
		}
	}

	public static void shutdown() {
		for (WeaponBehavior behavior : BY_TYPE.values()) {
			behavior.shutdown();
		}
	}
}
