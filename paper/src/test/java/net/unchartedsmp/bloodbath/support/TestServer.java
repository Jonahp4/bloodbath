package net.unchartedsmp.bloodbath.support;

import java.util.function.UnaryOperator;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFactory;
import org.bukkit.inventory.ItemStack;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.inventory.ChestInventoryMock;
import org.mockbukkit.mockbukkit.inventory.InventoryMock;
import org.mockbukkit.mockbukkit.inventory.ItemFactoryMock;

/**
 * MockBukkit's server plus what it leaves unimplemented: chest menus that report their holder
 * without a snapshot, and item hover events in chat.
 */
@SuppressWarnings("unchecked") // inherited from ServerMock#getBanList
public final class TestServer extends ServerMock {
	private final ItemFactory items = new ItemFactoryMock() {
		@Override
		public HoverEvent<HoverEvent.ShowItem> asHoverEvent(ItemStack item, UnaryOperator<HoverEvent.ShowItem> op) {
			return HoverEvent.showItem(op.apply(HoverEvent.ShowItem.showItem(item.getType().getKey(), item.getAmount())));
		}
	};

	@Override
	public ItemFactory getItemFactory() {
		return items;
	}

	public TestWorld addTestWorld(String name) {
		TestWorld world = new TestWorld(name);
		addWorld(world);
		return world;
	}

	public TestPlayer addTestPlayer(String name) {
		TestPlayer player = new TestPlayer(this, name);
		addPlayer(player);
		return player;
	}

	@Override
	public InventoryMock createInventory(InventoryHolder owner, int size, Component title) {
		return new ChestInventoryMock(owner, size) {
			@Override
			public InventoryHolder getHolder(boolean useSnapshot) {
				return getHolder();
			}
		};
	}
}
