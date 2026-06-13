package com.nova.novablock.island;

import org.bukkit.Material;

/**
 * Permanent, coin-purchased per-island upgrades. Survive prestige resets.
 *
 * <p>Each upgrade has discrete levels with rising cost. The effect lookup is
 * done by {@link IslandData#getUpgradeLevel} so all gameplay code just asks
 * "what level of X does this island have." Each upgrade exposes
 * {@link #currentEffect} / {@link #nextEffect} so the UI can render the
 * concrete numeric effect a player has now and what they'd get from the
 * next purchase, instead of an abstract "+N per level" rule.
 */
public enum IslandUpgrade {

    XP_BOOST(
            3, new long[]{2_500L, 7_500L, 20_000L},
            Material.EXPERIENCE_BOTTLE,
            "XP Boost",
            "More Mining XP from every OneBlock break."),

    PROPHECY_SLOTS(
            3, new long[]{4_000L, 12_000L, 30_000L},
            Material.AMETHYST_SHARD,
            "Prophecy Slots",
            "More prophecy picks members can lock at once."),

    CROP_GROWTH(
            3, new long[]{6_000L, 18_000L, 45_000L},
            Material.WHEAT,
            "Crop Growth",
            "Crops planted on your island ripen faster."),

    LOOT_ROOM_RATE(
            3, new long[]{6_000L, 18_000L, 45_000L},
            Material.END_PORTAL_FRAME,
            "Loot Room Rate",
            "Loot-room rifts appear more often while mining.");

    public final int maxLevel;
    public final long[] cost; // length == maxLevel
    public final Material icon;
    public final String displayName;
    public final String description;

    IslandUpgrade(int maxLevel, long[] cost, Material icon, String displayName, String description) {
        this.maxLevel = maxLevel;
        this.cost = cost;
        this.icon = icon;
        this.displayName = displayName;
        this.description = description;
    }

    public String storageKey() { return name().toLowerCase(); }

    /** Cost to advance from {@code currentLevel} to {@code currentLevel+1}. -1 if already max. */
    public long costFor(int currentLevel) {
        if (currentLevel >= maxLevel) return -1;
        return cost[currentLevel];
    }

    /** Human-readable description of the effect at {@code level}. */
    public String currentEffect(int level) {
        return switch (this) {
            case XP_BOOST -> level == 0
                    ? "Base Mining XP per break"
                    : "+" + (15 * level) + "% Mining XP from breaks";
            case PROPHECY_SLOTS -> "+" + level + " prophecy slot" + (level == 1 ? "" : "s");
            case CROP_GROWTH -> level == 0
                    ? "Normal crop growth"
                    : "+" + (25 * level) + "% crop growth speed";
            case LOOT_ROOM_RATE -> level == 0
                    ? "Base loot-room rate"
                    : "+" + (10 * level) + "% loot-room rate";
        };
    }

    /** Human-readable description of what advancing to {@code currentLevel + 1} grants. */
    public String nextEffect(int currentLevel) {
        return currentEffect(currentLevel + 1);
    }

    public static IslandUpgrade byKey(String key) {
        if (key == null) return null;
        try { return valueOf(key.toUpperCase()); }
        catch (IllegalArgumentException ex) { return null; }
    }
}
