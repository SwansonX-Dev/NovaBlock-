package com.nova.novablock.progression;

import org.bukkit.Material;

public enum SkillType {
    MINING("Mining", Material.DIAMOND_PICKAXE, "#7CFC00"),
    COMBAT("Combat", Material.NETHERITE_SWORD, "#FF6B6B"),
    MAGIC("Magic", Material.ENCHANTED_BOOK, "#9C66FF"),
    LUCK("Luck", Material.RABBIT_FOOT, "#FFD24D");

    private final String displayName;
    private final Material icon;
    private final String color;

    SkillType(String displayName, Material icon, String color) {
        this.displayName = displayName;
        this.icon = icon;
        this.color = color;
    }

    public String displayName() { return displayName; }
    public Material icon() { return icon; }
    public String color() { return color; }
}
