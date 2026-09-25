package net.unchartedsmp.bloodbath.ability;

/**
 * Every cooldown-gated ability. Cooldowns are stored per player as a flat {@code long[]} indexed
 * by {@link #ordinal()}. The default cooldown can be overridden per weapon in config.yml.
 */
public enum Ability {
	RIFTBLADE("Bloodrift", 280),
	BLOODHOOK("Bloodhook", 140),
	NULLBLADE_ZONE("Clot Field", 400),
	METEOR_GAUNTLET("Blood Meteor", 300),
	GRAVESTONE("Grave Pull", 360),
	CHRONOS("Bleeding Recall", 360),
	THUNDER_PIKE("Crimson Bolt", 260),
	MIRRORFANG("Blood Mirror", 400),
	VOID_SCYTHE("Harvest", 100),
	PARADOX_BOW("Paradox Echo", 200),
	VAMPIRE_FANG("Blood Dash", 180),
	BLOOD_GRIMOIRE("Transfusion", 320),
	/** The Blood Knight set's bonus, not a weapon's. */
	BLOOD_RAGE("Blood Rage", 1200);

	public static final int COUNT = values().length;

	private final String displayName;
	private final int defaultCooldownTicks;

	Ability(String displayName, int defaultCooldownTicks) {
		this.displayName = displayName;
		this.defaultCooldownTicks = defaultCooldownTicks;
	}

	public String displayName() {
		return displayName;
	}

	public int defaultCooldownTicks() {
		return defaultCooldownTicks;
	}
}
