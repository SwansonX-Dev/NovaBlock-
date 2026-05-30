package com.nova.novablock.minion;

import org.bukkit.Material;

public record MinionDrop(Material material, int weight, int minAmount, int maxAmount, double rareChance) {
    public MinionDrop {
        if (material == null || material.isAir()) throw new IllegalArgumentException("material");
        weight = Math.max(1, weight);
        minAmount = Math.max(1, minAmount);
        maxAmount = Math.max(minAmount, maxAmount);
        rareChance = Math.max(0.0, Math.min(1.0, rareChance));
    }

    public MinionDrop(Material material, int weight) {
        this(material, weight, 1, 1, 0.0);
    }
}
