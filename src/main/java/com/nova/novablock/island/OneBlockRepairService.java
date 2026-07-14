package com.nova.novablock.island;

import com.nova.novablock.NovaBlock;
import com.nova.novablock.phase.Phase;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayDeque;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
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

    /** How often the rolling scan task fires (ticks). Each run is bounded by {@link #SCAN_BUDGET_NANOS}. */
    private static final long SCAN_PERIOD_TICKS = 10L;
    /** Hard per-run time budget. We stop scanning once this is exceeded so a big roster never stalls a tick. */
    private static final long SCAN_BUDGET_NANOS = 2_000_000L; // ~2ms

    private final NovaBlock plugin;
    private int taskId = -1;

    /**
     * Rolling work queue of island ids still to scan this pass. Refilled from the
     * live roster whenever it drains, so the scan continuously sweeps every island
     * a few at a time instead of all at once.
     */
    private final ArrayDeque<UUID> scanQueue = new ArrayDeque<>();

    public OneBlockRepairService(@NotNull NovaBlock plugin) {
        this.plugin = plugin;
    }

    public void start() {
        shutdown();
        taskId = plugin.getServer().getScheduler().scheduleSyncRepeatingTask(plugin,
                this::tickScan, SCAN_PERIOD_TICKS, SCAN_PERIOD_TICKS);
    }

    public void shutdown() {
        if (taskId != -1) {
            plugin.getServer().getScheduler().cancelTask(taskId);
            taskId = -1;
        }
        scanQueue.clear();
    }

    /**
     * Incremental rolling scan. Processes islands off {@link #scanQueue} until the
     * per-run time budget is spent or the queue drains, then refills the queue for
     * the next sweep. Unlike a full synchronous sweep this never forces chunk loads
     * and never touches islands nobody is on — see {@link #scanIsland}.
     */
    private void tickScan() {
        if (scanQueue.isEmpty()) scanQueue.addAll(plugin.islands().all().keySet());
        long deadline = System.nanoTime() + SCAN_BUDGET_NANOS;
        while (!scanQueue.isEmpty()) {
            UUID id = scanQueue.poll();
            Island island = plugin.islands().get(id);
            if (island != null) scanIsland(island);
            if (System.nanoTime() >= deadline) break;
        }
    }

    /**
     * Passive repair check for one island. Skips islands with nobody online and any
     * center whose chunk isn't already loaded, so the scan stays cheap and never
     * pulls chunks into memory just to inspect them. Only writes a block when the
     * center is actually broken.
     */
    private void scanIsland(@NotNull Island island) {
        if (!hasOnlineMember(island)) return;
        scanCenter(island, island.centerBlock(), Dimension.OVERWORLD);
        if (island.isNetherUnlocked()) scanCenter(island, island.netherCenterBlock(), Dimension.NETHER);
        if (island.isEndUnlocked()) scanCenter(island, island.endCenterBlock(), Dimension.END);
    }

    private void scanCenter(@NotNull Island island, @NotNull Location center, Dimension dim) {
        World world = center.getWorld();
        if (world == null) return;
        if (!world.isChunkLoaded(center.getBlockX() >> 4, center.getBlockZ() >> 4)) return; // don't force-load
        if (!needsRepair(center.getBlock().getType())) return;
        repairAt(island, center, dim, false);
    }

    private boolean hasOnlineMember(@NotNull Island island) {
        for (UUID member : island.data().getMembers()) {
            if (Bukkit.getPlayer(member) != null) return true;
        }
        return false;
    }

    /**
     * Eagerly repair every loaded island in one pass. Retained for the admin
     * {@code /obadmin repair} command — it's explicit and on-demand, so the
     * synchronous full sweep is acceptable there; the periodic safety net uses
     * {@link #tickScan} instead.
     */
    public int repairLoadedIslands() {
        int repaired = 0;
        for (Island island : plugin.islands().all().values()) {
            if (repair(island, false)) repaired++;
            if (island.isNetherUnlocked() && repairNether(island, false)) repaired++;
            if (island.isEndUnlocked() && repairEnd(island, false)) repaired++;
        }
        return repaired;
    }

    public boolean repair(@NotNull Island island, boolean force) {
        return repairAt(island, island.centerBlock(), Dimension.OVERWORLD, force);
    }

    /**
     * Mirror of {@link #repair(Island, boolean)} but for the Nether center.
     * Picks replacements from the Nether phase table (added in Slice 2);
     * until then it falls back to NETHERRACK via {@link #netherReplacementFor}.
     */
    public boolean repairNether(@NotNull Island island, boolean force) {
        if (!island.isNetherUnlocked()) return false;
        Location center = island.netherCenterBlock();
        if (center.getWorld() == null) return false;
        return repairAt(island, center, Dimension.NETHER, force);
    }

    /** Mirror of {@link #repairNether(Island, boolean)} for the End center. */
    public boolean repairEnd(@NotNull Island island, boolean force) {
        if (!island.isEndUnlocked()) return false;
        Location center = island.endCenterBlock();
        if (center.getWorld() == null) return false;
        return repairAt(island, center, Dimension.END, force);
    }

    private boolean repairAt(@NotNull Island island, @NotNull Location center, Dimension dim, boolean force) {
        if (center.getWorld() == null) return false;
        center.getChunk().load();

        Location anchor = center.clone().add(0, -1, 0);
        if (anchor.getBlock().getType() != Material.BEDROCK) {
            anchor.getBlock().setType(Material.BEDROCK, false);
        }

        Block block = center.getBlock();
        if (!force && !needsRepair(block.getType())) return false;
        Material replacement = switch (dim) {
            case OVERWORLD -> replacementFor(island);
            case NETHER -> netherReplacementFor(island);
            case END -> endReplacementFor(island);
        };
        block.setType(replacement, false);
        if (dim.isOverworld()) refillPreview(island);
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

    /**
     * Slice 1 placeholder: until Nether phases land in Slice 2, the Nether
     * center repairs to NETHERRACK. Slice 2 swaps this to read from
     * {@code plugin.phases().getNetherOrLast(...)}.
     */
    private @NotNull Material netherReplacementFor(@NotNull Island island) {
        Phase phase = plugin.phases().getNetherOrLast(island.data().getNetherPhaseIndex());
        if (phase != null) {
            for (int i = 0; i < 12; i++) {
                Material rolled = phase.rollBlock(ThreadLocalRandom.current());
                if (!needsRepair(rolled)) return rolled;
            }
            for (var phaseBlock : phase.getBlocks()) {
                Material candidate = phaseBlock.material();
                if (!needsRepair(candidate)) return candidate;
            }
        }
        return Material.NETHERRACK;
    }

    private @NotNull Material endReplacementFor(@NotNull Island island) {
        Phase phase = plugin.phases().getEndOrLast(island.data().getEndPhaseIndex());
        if (phase != null) {
            for (int i = 0; i < 12; i++) {
                Material rolled = phase.rollBlock(ThreadLocalRandom.current());
                if (!needsRepair(rolled)) return rolled;
            }
            for (var phaseBlock : phase.getBlocks()) {
                Material candidate = phaseBlock.material();
                if (!needsRepair(candidate)) return candidate;
            }
        }
        return Material.END_STONE;
    }

    private void refillPreview(@NotNull Island island) {
        Phase phase = plugin.phases().getOrLast(island.data().getPhaseIndex());
        if (phase != null) {
            island.refillUpcoming(phase, com.nova.novablock.prophecy.ProphecyManager.QUEUE_SIZE);
        }
    }
}
