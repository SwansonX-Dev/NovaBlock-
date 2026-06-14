package com.nova.novablock.listener;

import com.nova.novablock.NovaBlock;
import com.nova.novablock.island.Island;
import com.nova.novablock.island.IslandUpgrade;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockGrowEvent;

import java.util.concurrent.ThreadLocalRandom;

/**
 * CROP_GROWTH island upgrade. On each natural grow tick of an {@link Ageable}
 * crop located on an island, advances the crop extra stages — {@code +50%}
 * growth per level (level 1 = +50%, level 2 = doubled, level 3 = 2.5×). The
 * bonus is applied as a guaranteed whole-stage part plus a fractional chance, so
 * higher levels are clearly faster, not just statistically. Each boosted tick
 * shows bonemeal-style green sparkles so it's visibly working. Crops off an
 * island, or on one without the upgrade, grow at vanilla speed.
 */
public class CropGrowthListener implements Listener {

    private static final double BONUS_PER_LEVEL = 0.50;

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

        // Extra stages on top of this natural tick: floor(bonus) guaranteed,
        // plus one more with the fractional probability.
        double bonus = BONUS_PER_LEVEL * level;
        int extra = (int) Math.floor(bonus);
        if (ThreadLocalRandom.current().nextDouble() < (bonus - extra)) extra++;
        if (extra <= 0) return;

        int newAge = Math.min(age.getMaximumAge(), age.getAge() + extra);
        if (newAge == age.getAge()) return;
        age.setAge(newAge);
        newState.setBlockData(age);

        Location loc = event.getBlock().getLocation();
        if (loc.getWorld() != null) {
            loc.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, loc.add(0.5, 0.4, 0.5), 5, 0.25, 0.25, 0.25, 0.0);
        }
    }
}
