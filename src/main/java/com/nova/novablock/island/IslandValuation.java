package com.nova.novablock.island;

import com.nova.novablock.NovaBlock;
import org.bukkit.configuration.file.FileConfiguration;
import org.jetbrains.annotations.NotNull;

/**
 * Computes the floor price of an island for the market.
 *
 * <p>The floor exists to stop a maxed island being handed to an alt for one coin — it is a
 * minimum, not a valuation the seller has to accept. Owners list at or above it.
 *
 * <p>Everything the formula reads is permanent, earned progress: island level, phase depth
 * across all three dimensions, total prestige, purchased upgrades, and lifetime blocks
 * broken. Deliberately excluded are the things a seller can drain right before listing —
 * the island bank and the shared storage vault — so emptying them can't crash the floor.
 */
public final class IslandValuation {

    private final NovaBlock plugin;

    public IslandValuation(NovaBlock plugin) { this.plugin = plugin; }

    /** Itemised so the sell UI can show the owner where the number came from. */
    public record Breakdown(long base, long fromLevel, long fromPhases,
                            long fromPrestige, long fromUpgrades, long fromBlocks) {
        public long total() {
            return base + fromLevel + fromPhases + fromPrestige + fromUpgrades + fromBlocks;
        }
    }

    public long floorPrice(@NotNull IslandData data) {
        return breakdown(data).total();
    }

    public Breakdown breakdown(@NotNull IslandData data) {
        FileConfiguration c = plugin.getConfig();
        String p = "islands.market.valuation.";
        long base = c.getLong(p + "base", 10_000L);
        long perLevel = c.getLong(p + "per-level", 2_500L);
        long perPhase = c.getLong(p + "per-phase", 1_500L);
        long perPrestige = c.getLong(p + "per-prestige", 15_000L);
        long perUpgrade = c.getLong(p + "per-upgrade-level", 5_000L);
        long perKBlocks = c.getLong(p + "per-1000-blocks", 250L);

        int phases = data.getPhaseIndex()
                + data.getPhaseIndex(Dimension.NETHER)
                + data.getPhaseIndex(Dimension.END);

        int upgradeLevels = 0;
        for (IslandUpgrade u : IslandUpgrade.values()) upgradeLevels += data.getUpgradeLevel(u);

        long totalBlocks = data.getBlocksBroken()
                + data.getBlocksBroken(Dimension.NETHER)
                + data.getBlocksBroken(Dimension.END);

        return new Breakdown(
                base,
                (long) Math.max(0, data.getLevel() - 1) * perLevel,
                (long) phases * perPhase,
                (long) data.getTotalPrestigeLevel() * perPrestige,
                (long) upgradeLevels * perUpgrade,
                totalBlocks / 1000L * perKBlocks);
    }
}
