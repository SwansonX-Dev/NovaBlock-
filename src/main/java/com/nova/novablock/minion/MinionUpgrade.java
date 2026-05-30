package com.nova.novablock.minion;

public enum MinionUpgrade {
    SPEED("Speed", 5),
    YIELD("Yield", 5),
    FUEL_EFFICIENCY("Fuel Efficiency", 5),
    COMPACTOR("Compactor", 3);

    private final String displayName;
    private final int maxLevel;

    MinionUpgrade(String displayName, int maxLevel) {
        this.displayName = displayName;
        this.maxLevel = maxLevel;
    }

    public String displayName() { return displayName; }
    public int maxLevel() { return maxLevel; }
}
