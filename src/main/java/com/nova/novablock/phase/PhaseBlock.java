package com.nova.novablock.phase;

import org.bukkit.Material;

import java.util.EnumSet;
import java.util.Set;

public record PhaseBlock(Material material, int weight) {
    public PhaseBlock {
        if (material == null) throw new IllegalArgumentException("material");
        if (weight <= 0) throw new IllegalArgumentException("weight");
    }

    /**
     * Materials forced to be far rarer than their nominal weight (already at the
     * integer floor of 1). In a weighted roll a flagged material keeps its raw
     * weight while every other entry is multiplied by {@link #RARE_DILUTION},
     * making the flagged material ~{@code RARE_DILUTION}x less likely without
     * rescaling the whole table. Pools with no flagged material are unaffected
     * (all weights scale equally, so proportions are unchanged).
     *
     * <p>Beacons (worth 2500 coins each) were piling up on the heavily-mined
     * community OneBlock — at ~0.17% per break across thousands of daily breaks
     * players were collecting 10+. The dilution drops them to ~0.004%.
     */
    public static final Set<Material> ULTRA_RARE = EnumSet.of(Material.BEACON);
    public static final int RARE_DILUTION = 40;

    /** Weight to use in a weighted roll: diluted for ultra-rare materials. */
    public int effectiveWeight() {
        return ULTRA_RARE.contains(material) ? weight : weight * RARE_DILUTION;
    }
}
