package net.minecraft.registry;
import net.minecraft.util.Identifier;
public interface Registry<T> {
	T get(Identifier id);
	static <V, T extends V> T register(Registry<V> registry, RegistryKey<V> key, T entry) { throw new UnsupportedOperationException(); }
}
