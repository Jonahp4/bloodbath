package net.minecraft.registry;
import net.minecraft.util.Identifier;
public class RegistryKey<T> {
	public static <T> RegistryKey<T> of(RegistryKey<? extends Registry<T>> registry, Identifier value) { throw new UnsupportedOperationException(); }
}
