package net.unchartedsmp.bloodbath.support;

import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.damage.DamageSource;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.ZombieMock;

/** A zombie that takes damage from a {@link DamageSource} (MockBukkit doesn't). */
public final class TestZombie extends ZombieMock {
	public TestZombie(ServerMock server, Location at) {
		super(server, UUID.randomUUID());
		setLocation(at);
		server.registerEntity(this);
	}

	@Override
	public void damage(double amount, DamageSource source) {
		if (isDead()) {
			return;
		}
		double left = Hits.apply(this, getHealth(), amount, source);
		if (left >= 0.0) {
			setHealth(left);
		}
	}
}
