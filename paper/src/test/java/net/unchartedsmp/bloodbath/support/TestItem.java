package net.unchartedsmp.bloodbath.support;

import java.util.UUID;
import org.bukkit.inventory.ItemStack;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.ItemMock;

/** A dropped item that can be told never to despawn (MockBukkit's can't). */
public final class TestItem extends ItemMock {
	private boolean unlimited;

	public TestItem(ServerMock server, ItemStack stack) {
		super(server, UUID.randomUUID(), stack);
	}

	@Override
	public void setUnlimitedLifetime(boolean unlimited) {
		this.unlimited = unlimited;
	}

	@Override
	public boolean isUnlimitedLifetime() {
		return unlimited;
	}
}
