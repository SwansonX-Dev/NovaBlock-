package com.nova.novablock.minion;

public enum MinionStatus {
    READY("Ready", "<green>"),
    NO_ONLINE_MEMBER("Island offline", "<dark_gray>"),
    UNLINKED("No linked chest", "<yellow>"),
    MISSING_CHEST("Chest missing", "<red>"),
    CHEST_OUTSIDE_ISLAND("Chest outside island", "<red>"),
    CHUNK_UNLOADED("Chest unloaded", "<gray>"),
    CHEST_FULL("Chest full", "<gold>");

    private final String displayName;
    private final String color;

    MinionStatus(String displayName, String color) {
        this.displayName = displayName;
        this.color = color;
    }

    public String displayName() { return displayName; }
    public String color() { return color; }
}
