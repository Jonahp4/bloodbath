package net.minecraft.text;
public interface Text {
	static MutableText literal(String string) { throw new UnsupportedOperationException(); }
	static MutableText translatable(String key) { throw new UnsupportedOperationException(); }
	String getString();
}
