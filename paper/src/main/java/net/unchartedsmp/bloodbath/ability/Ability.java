package net.unchartedsmp.bloodbath.ability;

/**
 * Every cooldown-gated ability. Cooldowns are stored per player as a flat {@code long[]} indexed
 * by {@link #ordinal()}. The default cooldown can be overridden per weapon in config.yml.
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
	PARADOX_BOW("Paradox Echo", 200),
	VAMPIRE_FANG("Blood Dash", 160),
	BLOOD_GRIMOIRE("Transfusion", 280);

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
