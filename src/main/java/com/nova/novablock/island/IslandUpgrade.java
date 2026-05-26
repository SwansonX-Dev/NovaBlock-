package com.nova.novablock.island;

import org.bukkit.Material;

/**
 * Permanent, coin-purchased per-island upgrades. Survive prestige resets.
 *
 * <p>Each upgrade has discrete levels with rising cost. The effect lookup is
 * done by {@link IslandData#getUpgradeLevel} so all gameplay code just asks
 * "what level of X does this island have."
 */
public enum IslandUpgrade {

    XP_BOOST(
            3, new long[]{2_500L, 7_500L, 20_000L},
            Material.EXPERIENCE_BOTTLE,
            "XP Boost",
            "Each level adds +1 Mining XP per OneBlock break.",
            "Stacks additively with combo XP and ARCANE_LURE."),

    STORAGE_AUTOSELL(
            3, new long[]{5_000L, 15_000L, 40_000L},
            Material.CHEST,
            "Auto-Sell Bonus",
            "Each level adds +25% coin to the storage auto-sell slot.",
            "Stacks with island JACKPOT/QUARRY perks if active."),

    PROPHECY_SLOTS(
            3, new long[]{4_000L, 12_000L, 30_000L},
            Material.AMETHYST_SHARD,
            "Prophecy Slots",
            "Each level adds one extra prophecy pick slot for island members.",
            "Stacks with PROPHET (Mining 30) for up to 5 picks at max."),

    BOSS_LOOT(
            3, new long[]{6_000L, 18_000L, 45_000L},
            Material.NETHERITE_INGOT,
            "Boss Loot",
            "Each level adds +20% coin reward when a boss is defeated.",
            "Stacks multiplicatively with prestige and event bonuses."),

    LOOT_ROOM_RATE(
            3, new long[]{6_000L, 18_000L, 45_000L},
            Material.END_PORTAL_FRAME,
            "Loot Room Rate",
            "Each level boosts loot-room appearance rate by 10%.",
            "Stacks with the Magic 10 perk (RIFTWALKER).");

    public final int maxLevel;
    public final long[] cost; // length == maxLevel
    public final Material icon;
    public final String displayName;
    public final String[] description;

    IslandUpgrade(int maxLevel, long[] cost, Material icon, String displayName, String... description) {
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

    public static IslandUpgrade byKey(String key) {
        if (key == null) return null;
        try { return valueOf(key.toUpperCase()); }
        catch (IllegalArgumentException ex) { return null; }
    }
}
