package com.nova.novablock.community;

import com.nova.novablock.NovaBlock;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Top-level coordinator for community features. Owns {@link CommunityBlock},
 * {@link WeeklyGoal}, and {@link RaidScheduler}; loads/saves state via
 * {@link HubStorage}; provides the single integration surface used by
 * BlockListener / EventManager / NovaBlock.
 */
public class CommunityHubManager {

    private final NovaBlock plugin;
    private final CommunityBlock block;
    private final WeeklyGoal goal;
    private final RaidScheduler raids;
    private final CommunityLeaderboardDisplay leaderboard;
    private final HubStorage storage;
    private final AtomicBoolean dirty = new AtomicBoolean(false);
    private static final long SAVE_DEBOUNCE_TICKS = 100L; // ~5s

    public CommunityHubManager(NovaBlock plugin) {
        this.plugin = plugin;
        this.block = new CommunityBlock(plugin);
        this.goal = new WeeklyGoal(plugin);
        this.raids = new RaidScheduler(plugin);
        this.leaderboard = new CommunityLeaderboardDisplay(plugin, this);
        this.storage = new HubStorage(plugin);
        storage.load(block, goal, raids);
    }

    public CommunityBlock block() { return block; }
    public WeeklyGoal goal() { return goal; }
    public RaidScheduler raids() { return raids; }
    public CommunityLeaderboardDisplay leaderboard() { return leaderboard; }

    /** Place the bedrock anchor + starter material if missing. Called from NovaBlock after worlds load. */
    public void placeIfNeeded() {
        Location at = plugin.spawn().communityBlockLocation();
        if (at == null) {
            plugin.getLogger().warning("Community block can't be placed yet — spawn location not set.");
            return;
        }
        // Defer one tick so the world is fully ready when called from onEnable.
        Bukkit.getScheduler().runTask(plugin, () -> block.placeInitial(at));
    }

    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("community.enabled", true);
    }

    /** True if the broken location matches the community block coordinates. */
    public boolean isCommunityBlock(Location loc) {
        if (!isEnabled() || loc == null) return false;
        Location at = plugin.spawn().communityBlockLocation();
        if (at == null || at.getWorld() == null) return false;
        if (!at.getWorld().equals(loc.getWorld())) return false;
        return at.getBlockX() == loc.getBlockX()
                && at.getBlockY() == loc.getBlockY()
                && at.getBlockZ() == loc.getBlockZ();
    }

    /** True if loc is the bedrock anchor directly below the community block. */
    public boolean isAnchorBlock(Location loc) {
        if (!isEnabled() || loc == null) return false;
        Location at = plugin.spawn().communityBlockLocation();
        if (at == null || at.getWorld() == null) return false;
        if (!at.getWorld().equals(loc.getWorld())) return false;
        return at.getBlockX() == loc.getBlockX()
                && at.getBlockY() - 1 == loc.getBlockY()
                && at.getBlockZ() == loc.getBlockZ();
    }

    /** True if loc is in the regen column (block ± 1 vertical, same x/z). */
    public boolean isInRegenColumn(Location loc) {
        if (!isEnabled() || loc == null) return false;
        Location at = plugin.spawn().communityBlockLocation();
        if (at == null || at.getWorld() == null) return false;
        if (!at.getWorld().equals(loc.getWorld())) return false;
        return at.getBlockX() == loc.getBlockX()
                && at.getBlockZ() == loc.getBlockZ()
                && loc.getBlockY() >= at.getBlockY() - 1
                && loc.getBlockY() <= at.getBlockY() + 1;
    }

    /** Routes a break through CommunityBlock, then counts it toward the weekly goal. */
    public void handleBreak(Player player, BlockBreakEvent event) {
        if (!isEnabled()) return;
        Location at = plugin.spawn().communityBlockLocation();
        if (at == null) return;
        block.onBreak(player, event.getBlock().getType(), event, at);
        goal.recordBreak(player, 1L);
        block.tickPayoutIfDue();
        markDirty();
    }

    /** Counts a non-community block break toward the weekly goal only. */
    public void recordIslandBreak(Player p) {
        if (!isEnabled()) return;
        goal.recordBreak(p, 1L);
        markDirty();
    }

    public void markDirty() {
        if (!dirty.compareAndSet(false, true)) return;
        Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, this::flush, SAVE_DEBOUNCE_TICKS);
    }

    public void flushNow() {
        dirty.set(false);
        storage.save(block, goal, raids);
    }

    private void flush() {
        if (!dirty.compareAndSet(true, false)) return;
        storage.save(block, goal, raids);
    }

    public void shutdown() {
        leaderboard.shutdown();
        raids.shutdown();
        // Force a final sync save with current state.
        dirty.set(false);
        storage.save(block, goal, raids);
    }
}
