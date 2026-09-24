package net.unchartedsmp.bloodbath.support;

import com.destroystokyo.paper.entity.Pathfinder;
import java.lang.reflect.Proxy;
import java.util.UUID;
import org.bukkit.damage.DamageSource;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.RavagerMock;

/** The Blood Knight's body: a ravager with the mob controls MockBukkit leaves out (awareness, pathfinding, body yaw). */
public final class TestKnightRavager extends RavagerMock {
	private boolean aware = true;
	private boolean removeWhenFarAway = true;
	private double lastDamage;

	public TestKnightRavager(ServerMock server) {
		super(server, UUID.randomUUID());
	}

	@Override
	public void setRemoveWhenFarAway(boolean remove) {
		removeWhenFarAway = remove;
	}

	@Override
	public boolean getRemoveWhenFarAway() {
		return removeWhenFarAway;
	}

	private boolean pickup = true;

	@Override
	public void setCanPickupItems(boolean pickup) {
		this.pickup = pickup;
	}

	@Override
	public boolean getCanPickupItems() {
		return pickup;
	}

	@Override
	public void setAware(boolean aware) {
		this.aware = aware;
	}

	@Override
	public boolean isAware() {
		return aware;
	}

	@Override
	public float getBodyYaw() {
		return getLocation().getYaw();
	}

	/** Whether its pathfinder can reach whatever it's asked about (tests set false for a player out of reach). */
	public boolean reachable = true;

	@Override
	public Pathfinder getPathfinder() {
		Pathfinder.PathResult path = (Pathfinder.PathResult) Proxy.newProxyInstance(Pathfinder.class.getClassLoader(),
			new Class<?>[] {Pathfinder.PathResult.class},
			(proxy, method, args) -> method.getName().equals("canReachFinalPoint") ? reachable : method.getReturnType() == boolean.class ? false : null);
		return (Pathfinder) Proxy.newProxyInstance(Pathfinder.class.getClassLoader(), new Class<?>[] {Pathfinder.class},
			(proxy, method, args) -> method.getName().equals("findPath") ? path : method.getReturnType() == boolean.class ? false : null);
	}

	@Override
	public double getLastDamage() {
		return lastDamage;
	}

	@Override
	public void setLastDamage(double damage) {
		lastDamage = damage;
	}

	@Override
	public void damage(double amount) {
		// Like Paper: a hit with no source still goes through the damage event.
		damage(amount, DamageSource.builder(org.bukkit.damage.DamageType.GENERIC).build());
	}

	@Override
	public void damage(double amount, DamageSource source) {
		if (isDead() || isInvulnerable()) {
			return;
		}
		double left = Hits.apply(this, getHealth(), amount, source);
		if (left >= 0.0) {
			setHealth(left);
		}
	}
}
