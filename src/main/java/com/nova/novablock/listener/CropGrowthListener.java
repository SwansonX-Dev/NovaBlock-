package com.nova.novablock.listener;

import com.nova.novablock.NovaBlock;
import com.nova.novablock.island.Island;
import com.nova.novablock.island.IslandUpgrade;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockGrowEvent;

import java.util.concurrent.ThreadLocalRandom;

/**
 * CROP_GROWTH island upgrade. On each natural grow tick of an {@link Ageable}
 * crop located on an island, rolls a per-level chance ({@code 25% × level}) to
 * advance the crop one extra stage — roughly +25% effective growth speed per
 * level. Anything not on a claimed island, or an island without the upgrade,
 * grows at vanilla speed.
 */
public class CropGrowthListener implements Listener {

    private static final double CHANCE_PER_LEVEL = 0.25;

    private final NovaBlock plugin;

    public CropGrowthListener(NovaBlock plugin) { this.plugin = plugin; }

    @EventHandler(ignoreCancelled = true)
    public void onGrow(BlockGrowEvent event) {
        BlockState newState = event.getNewState();
        BlockData data = newState.getBlockData();
        if (!(data instanceof Ageable age)) return;
        if (age.getAge() >= age.getMaximumAge()) return;

        Island island = plugin.islands().atLocation(event.getBlock().getLocation());
        if (island == null) return;
        int level = island.data().getUpgradeLevel(IslandUpgrade.CROP_GROWTH);
        if (level <= 0) return;

        if (ThreadLocalRandom.current().nextDouble() >= CHANCE_PER_LEVEL * level) return;
        age.setAge(Math.min(age.getMaximumAge(), age.getAge() + 1));
        newState.setBlockData(age);
    }
}
