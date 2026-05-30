package com.nova.novablock.minion;

import org.bukkit.Material;

public enum MinionFuel {
    COAL(Material.COAL, 20 * 60 * 10),
    CHARCOAL(Material.CHARCOAL, 20 * 60 * 10),
    BLAZE_POWDER(Material.BLAZE_POWDER, 20 * 60 * 8),
    BLAZE_ROD(Material.BLAZE_ROD, 20 * 60 * 20),
    LAVA_BUCKET(Material.LAVA_BUCKET, 20 * 60 * 30),
    DRIED_KELP_BLOCK(Material.DRIED_KELP_BLOCK, 20 * 60 * 15);

    private final Material material;
    private final long durationTicks;

    MinionFuel(Material material, long durationTicks) {
        this.material = material;
        this.durationTicks = durationTicks;
    }

    public Material material() { return material; }
    public long durationTicks() { return durationTicks; }

    public static MinionFuel of(Material material) {
        for (MinionFuel fuel : values()) if (fuel.material == material) return fuel;
        return null;
    }
}
