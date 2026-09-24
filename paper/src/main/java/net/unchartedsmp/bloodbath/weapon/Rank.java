package net.unchartedsmp.bloodbath.weapon;

import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;

/** Kill ranks shown on each weapon's tooltip. */
public enum Rank {
	UNBLOODED(0, "Unblooded", NamedTextColor.GRAY),
	BLOODED(5, "Blooded", NamedTextColor.RED),
	CRIMSON(25, "Crimson", NamedTextColor.DARK_RED),
	SANGUINE(100, "Sanguine", TextColor.color(0xFF2A35)),
	BLOODBATH(250, "Bloodbath", TextColor.color(0xFFD27A));

	private final int kills;
	private final String title;
	private final TextColor color;

	Rank(int kills, String title, TextColor color) {
		this.kills = kills;
		this.title = title;
		this.color = color;
	}

	public static Rank of(int kills) {
		Rank best = UNBLOODED;
		for (Rank rank : values()) {
			if (kills >= rank.kills) {
				best = rank;
			}
		}
		return best;
	}

	public String title() {
		return title;
	}

	public TextColor color() {
		return color;
	}
}
