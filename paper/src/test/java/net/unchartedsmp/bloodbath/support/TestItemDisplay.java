package net.unchartedsmp.bloodbath.support;

import java.util.UUID;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.ItemDisplayMock;

/** An item display that can be hidden by default (MockBukkit's can't). */
public final class TestItemDisplay extends ItemDisplayMock {
	private boolean visibleByDefault = true;

	public TestItemDisplay(ServerMock server) {
		super(server, UUID.randomUUID());
	}

	@Override
	public void setVisibleByDefault(boolean visible) {
		visibleByDefault = visible;
	}

	@Override
	public boolean isVisibleByDefault() {
		return visibleByDefault;
	}
}
