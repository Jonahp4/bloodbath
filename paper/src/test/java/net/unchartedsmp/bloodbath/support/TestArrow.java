package net.unchartedsmp.bloodbath.support;

import java.util.UUID;
import org.bukkit.Location;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.ArrowMock;

/** An arrow in flight (MockBukkit can't say whether an arrow is stuck in a block). */
public final class TestArrow extends ArrowMock {
	public TestArrow(ServerMock server, Location at) {
		super(server, UUID.randomUUID());
		setLocation(at);
		server.registerEntity(this);
	}

	@Override
	public boolean isInBlock() {
		return false;
	}
}
