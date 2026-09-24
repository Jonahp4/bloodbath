package net.unchartedsmp.ability;

/**
 * Every cooldown-gated ability in the mod. Cooldowns are stored per player as a flat
 * {@code long[]} indexed by {@link #ordinal()}, so adding an ability here is all that's needed.
 */
public enum Ability {
	RIFTBLADE("Bloodrift", 300),
	BLOODHOOK("Bloodhook", 120),
	NULLBLADE_ZONE("Clot Field", 400),
	METEOR_GAUNTLET("Blood Meteor", 280),
	GRAVESTONE("Grave Pull", 400),
	CHRONOS("Bleeding Recall", 360),
	THUNDER_PIKE("Crimson Bolt", 240),
	MIRRORFANG("Blood Mirror", 400),
	VOID_SCYTHE("Harvest", 60),
	PARADOX_BOW("Paradox Echo", 200);

	public static final int COUNT = values().length;

	private final String displayName;
	private final int cooldownTicks;

	Ability(String displayName, int cooldownTicks) {
		this.displayName = displayName;
		this.cooldownTicks = cooldownTicks;
	}

	public String displayName() {
		return displayName;
	}

	public int cooldownTicks() {
		return cooldownTicks;
	}

	public String cooldownLabel() {
		return (cooldownTicks % 20 == 0 ? String.valueOf(cooldownTicks / 20) : String.valueOf(cooldownTicks / 20.0)) + "s";
	}
}
