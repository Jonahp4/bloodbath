package net.minecraft.item;
import java.util.function.Supplier;
import net.minecraft.text.Text;
public class ItemGroup {
	public static class Builder {
		public Builder icon(Supplier<ItemStack> iconSupplier) { throw new UnsupportedOperationException(); }
		public Builder displayName(Text displayName) { throw new UnsupportedOperationException(); }
		public Builder entries(EntryCollector entryCollector) { throw new UnsupportedOperationException(); }
		public ItemGroup build() { throw new UnsupportedOperationException(); }
	}
	public record DisplayContext() {}
	@FunctionalInterface public interface EntryCollector { void accept(DisplayContext displayContext, Entries entries); }
	public interface Entries { default void add(ItemConvertible item) { throw new UnsupportedOperationException(); } }
}
