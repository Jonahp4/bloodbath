package net.unchartedsmp.bloodbath.support;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.resource.ResourcePackRequest;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.damage.DamageSource;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/** A player who joined through "play.example.com" and remembers the resource packs sent to them. */
public final class TestPlayer extends PlayerMock {
	public final List<ResourcePackRequest> packs = new ArrayList<>();

	public TestPlayer(ServerMock server, String name) {
		super(server, name);
	}

	@Override
	public InetSocketAddress getVirtualHost() {
		return InetSocketAddress.createUnresolved("play.example.com", 25565);
	}

	@Override
	public void sendResourcePacks(ResourcePackRequest request) {
		packs.add(request);
	}

	@Override
	public void playHurtAnimation(float yaw) {
	}

	@Override
	public void heal(double amount, EntityRegainHealthEvent.RegainReason reason) {
		EntityRegainHealthEvent event = new EntityRegainHealthEvent(this, amount, reason);
		Bukkit.getPluginManager().callEvent(event);
		if (!event.isCancelled()) {
			setHealth(Math.min(getAttribute(Attribute.MAX_HEALTH).getValue(), getHealth() + event.getAmount()));
		}
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
