package com.nova.novablock.event;

import com.nova.novablock.NovaBlock;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

/**
 * Hosts long-running maintenance timers: autosave, daily quest rollover, etc.
 */
public class EventManager {

    private final NovaBlock plugin;
    private BukkitTask autosave;
    private BukkitTask dailyRoll;
    private BukkitTask communityTick;

    public EventManager(NovaBlock plugin) { this.plugin = plugin; }

    public void startTimers() {
        autosave = Bukkit.getScheduler().runTaskTimer(plugin, this::autosave, 20L * 60 * 5, 20L * 60 * 5);
        dailyRoll = Bukkit.getScheduler().runTaskTimer(plugin, () -> plugin.quests().rollDaily(), 20L * 60, 20L * 60);
        communityTick = Bukkit.getScheduler().runTaskTimer(plugin, this::tickCommunity, 20L * 30, 20L * 60);
    }

    private void autosave() {
        plugin.islands().saveDirty();
        plugin.progression().saveAll();
    }

    private void tickCommunity() {
        if (plugin.community() == null) return;
        if (!plugin.community().isEnabled()) return;
        plugin.community().repairIfNeeded();
        plugin.community().goal().tick();
        plugin.community().raids().tick();
        plugin.community().block().tickPayoutIfDue();
        plugin.community().markDirty();
    }

    public void shutdown() {
        if (autosave != null) { autosave.cancel(); autosave = null; }
        if (dailyRoll != null) { dailyRoll.cancel(); dailyRoll = null; }
        if (communityTick != null) { communityTick.cancel(); communityTick = null; }
    }
}
