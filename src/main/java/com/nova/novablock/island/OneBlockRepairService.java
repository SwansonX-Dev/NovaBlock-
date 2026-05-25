package com.nova.novablock.island;

import com.nova.novablock.NovaBlock;
import com.nova.novablock.phase.Phase;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.jetbrains.annotations.NotNull;

import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/** Repairs missing or invalid OneBlock center blocks without resetting island progress. */
public final class OneBlockRepairService {

    private static final Set<Material> INVALID_CENTER_MATERIALS = EnumSet.of(
            Material.AIR,
            Material.CAVE_AIR,
            Material.VOID_AIR,
            Material.BEDROCK,
            Material.BARRIER,
            Material.COMMAND_BLOCK,
            Material.CHAIN_COMMAND_BLOCK,
            Material.REPEATING_COMMAND_BLOCK,
            Material.STRUCTURE_BLOCK,
            Material.STRUCTURE_VOID,
            Material.JIGSAW,
            Material.LIGHT,
            Material.END_PORTAL,
            Material.END_PORTAL_FRAME,
            Material.NETHER_PORTAL,
            Material.REINFORCED_DEEPSLATE,
            Material.MOVING_PISTON,
            Material.PISTON_HEAD
    );

    private final NovaBlock plugin;
    private int taskId = -1;

    public OneBlockRepairService(@NotNull NovaBlock plugin) {
        this.plugin = plugin;
    }

    public void start() {
        shutdown();
        taskId = plugin.getServer().getScheduler().scheduleSyncRepeatingTask(plugin,
                this::repairLoadedIslands, 20L * 30L, 20L * 30L);
    }

    public void shutdown() {
        if (taskId != -1) {
            plugin.getServer().getScheduler().cancelTask(taskId);
            taskId = -1;
        }
    }

    public int repairLoadedIslands() {
        int repaired = 0;
        for (Island island : plugin.islands().all().values()) {
            if (repair(island, false)) repaired++;
        }
        return repaired;
    }

    public boolean repair(@NotNull Island island, boolean force) {
        Location center = island.centerBlock();
        if (center.getWorld() == null) return false;
        center.getChunk().load();

        Location anchor = center.clone().add(0, -1, 0);
        if (anchor.getBlock().getType() != Material.BEDROCK) {
            anchor.getBlock().setType(Material.BEDROCK, false);
        }

        Block block = center.getBlock();
        if (!force && !needsRepair(block.getType())) return false;
        Material replacement = replacementFor(island);
        block.setType(replacement, false);
        refillPreview(island);
        return true;
    }

    public boolean needsRepair(@NotNull Island island) {
        Location center = island.centerBlock();
        return center.getWorld() != null && needsRepair(center.getBlock().getType());
    }

    public boolean needsRepair(@NotNull Material material) {
        return INVALID_CENTER_MATERIALS.contains(material);
    }

    private @NotNull Material replacementFor(@NotNull Island island) {
        Phase phase = plugin.phases().getOrLast(island.data().getPhaseIndex());
        if (phase != null) {
            Material planned = island.pollNext();
            if (planned != null && !needsRepair(planned)) return planned;
            for (int i = 0; i < 12; i++) {
                Material rolled = phase.rollBlock(ThreadLocalRandom.current());
                if (!needsRepair(rolled)) return rolled;
            }
            for (var phaseBlock : phase.getBlocks()) {
                Material candidate = phaseBlock.material();
                if (!needsRepair(candidate)) return candidate;
            }
        }
        return Material.GRASS_BLOCK;
    }

    private void refillPreview(@NotNull Island island) {
        Phase phase = plugin.phases().getOrLast(island.data().getPhaseIndex());
        if (phase != null) {
            island.refillUpcoming(phase, com.nova.novablock.prophecy.ProphecyManager.QUEUE_SIZE);
        }
    }
}
