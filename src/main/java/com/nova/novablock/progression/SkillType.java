package com.nova.novablock.progression;

import org.bukkit.Material;

public enum SkillType {
    MINING("Mining", Material.DIAMOND_PICKAXE, "#7CFC00"),
    COMBAT("Combat", Material.NETHERITE_SWORD, "#FF6B6B"),
    MAGIC("Magic", Material.ENCHANTED_BOOK, "#9C66FF"),
    LUCK("Luck", Material.RABBIT_FOOT, "#FFD24D"),
    FARMING("Farming", Material.GOLDEN_HOE, "#A3E635"),
    FISHING("Fishing", Material.FISHING_ROD, "#38BDF8"),
    WOODCUTTING("Woodcutting", Material.GOLDEN_AXE, "#D9A066"),
    EXCAVATION("Excavation", Material.GOLDEN_SHOVEL, "#E0C068");

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
