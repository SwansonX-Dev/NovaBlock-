package com.nova.novablock.island;

import com.nova.novablock.NovaBlock;
import com.nova.novablock.phase.Phase;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Runtime wrapper around IslandData. Holds per-island state that doesn't need to be persisted
 * every tick (e.g. queued "next blocks" used for the prophecy preview).
 */
public class Island {

    private final NovaBlock plugin;
    private final IslandData data;
    private final java.util.Deque<Material> upcomingBlocks = new java.util.ArrayDeque<>();
    private long lastBreakTick;
    private Material lastBrokenBlock;
    private int comboCount;

    public Island(NovaBlock plugin, IslandData data) {
        this.plugin = plugin;
        this.data = data;
    }

    public IslandData data() { return data; }

    public Location centerBlock() { return data.centerBlock(); }

    public void ensureSpawnPlatform() {
        Location c = centerBlock();
        if (c.getWorld() == null) return;
        c.getBlock().setType(Material.GRASS_BLOCK, false);
        // Permanent bedrock directly under the centre so the OneBlock can never fall into the void
        // even if the player breaks faster than the regen, or if a chunk reload skips a tick.
        c.clone().add(0, -1, 0).getBlock().setType(Material.BEDROCK, false);
        // Small bedrock skirt around the underside so first-join players don't free-fall
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue;
                Location l = c.clone().add(dx, -1, dz);
                if (l.getBlock().getType() == Material.AIR) l.getBlock().setType(Material.BEDROCK, false);
            }
        }
    }

    public java.util.Deque<Material> upcomingBlocks() { return upcomingBlocks; }

    public void refillUpcoming(Phase phase, int target) {
        while (upcomingBlocks.size() < target) {
            upcomingBlocks.addLast(phase.rollBlock(ThreadLocalRandom.current()));
        }
    }

    public Material pollNext() { return upcomingBlocks.pollFirst(); }

    public long getLastBreakTick() { return lastBreakTick; }
    public void setLastBreakTick(long t) { this.lastBreakTick = t; }

    public Material getLastBrokenBlock() { return lastBrokenBlock; }
    public int getComboCount() { return comboCount; }
    public void recordBreak(Material m) {
        if (m == lastBrokenBlock) comboCount++;
        else { lastBrokenBlock = m; comboCount = 1; }
    }
    public void breakCombo() { comboCount = 0; lastBrokenBlock = null; }

    public boolean isMember(UUID id) { return data.getMembers().contains(id); }
    public boolean isMember(Player p) { return isMember(p.getUniqueId()); }

    public void teleportHome(Player p) {
        Location loc = data.spawnLocation();
        if (loc.getWorld() == null) {
            p.sendMessage("Your island world isn't loaded.");
            return;
        }
        loc.getChunk().load();
        p.teleportAsync(loc);
    }

    public static UUID newId() { return UUID.randomUUID(); }
    public static String shortId(UUID id) { return id.toString().substring(0, 8); }
    public static long now() { return Bukkit.getServer().getCurrentTick(); }
}
