package com.nova.novablock.phase;

import org.bukkit.Material;

public record PhaseBlock(Material material, int weight) {
    public PhaseBlock {
        if (material == null) throw new IllegalArgumentException("material");
        if (weight <= 0) throw new IllegalArgumentException("weight");
    }
}
