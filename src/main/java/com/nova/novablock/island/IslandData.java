package com.nova.novablock.island;

import com.nova.novablock.NovaBlock;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.nova.novablock.island.IslandWorldManager.DEFAULT_WORLD_NAME;
import static com.nova.novablock.island.IslandWorldManager.SLOT_SIZE;

public class IslandData {

    private final UUID id;
    private final UUID owner;
    private final String worldName;
    private final int slotX;
    private final int slotZ;
    private final Set<UUID> members = new HashSet<>();
    private final Map<IslandFlag, Boolean> flags = new EnumMap<>(IslandFlag.class);
    private final Map<IslandUpgrade, Integer> upgrades = new EnumMap<>(IslandUpgrade.class);
    /** Material names of smithing templates this island has already earned from prestige. */
    private final Set<String> receivedPrestigeTemplates = new HashSet<>();

    private long blocksBroken;
    private int phaseIndex;
    private int phaseProgress;
    private int prestigeLevel;
    private int level;
    private long lastBossAt;
    private long lastLootRoomAt;
    /** Base64-encoded ItemStack[] for the island's shared virtual storage. */
    private String storageBase64 = "";

    // --- Nether dimension (added in v0.5.0) ----------------------------------
    private long netherBlocksBroken;
    private int netherPhaseIndex;
    private int netherPhaseProgress;
    private long netherLastBossAt;
    private long netherLastLootRoomAt;
    /** Set the first time the Overworld crosses into Phase 7 (phaseIndex >= 6). */
    private boolean netherUnlocked;

    public IslandData(UUID id, UUID owner, String worldName, int slotX, int slotZ) {
        this.id = id;
        this.owner = owner;
        this.worldName = worldName;
        this.slotX = slotX;
        this.slotZ = slotZ;
        this.members.add(owner);
    }

    public UUID getId() { return id; }
    public UUID getOwner() { return owner; }
    public String getWorldName() { return worldName; }
    public int getSlotX() { return slotX; }
    public int getSlotZ() { return slotZ; }
    public Set<UUID> getMembers() { return members; }

    /** Live flag map. Missing entries fall back to {@link IslandFlag#defaultValue}. */
    public Map<IslandFlag, Boolean> getFlags() { return flags; }

    public boolean isFlag(IslandFlag f) {
        Boolean v = flags.get(f);
        return v == null ? f.defaultValue : v;
    }

    public void setFlag(IslandFlag f, boolean v) { flags.put(f, v); }

    public Map<IslandUpgrade, Integer> getUpgrades() { return upgrades; }

    public int getUpgradeLevel(IslandUpgrade u) {
        Integer v = upgrades.get(u);
        return v == null ? 0 : v;
    }

    public void setUpgradeLevel(IslandUpgrade u, int level) {
        upgrades.put(u, Math.max(0, Math.min(u.maxLevel, level)));
    }

    public String getStorageBase64() { return storageBase64; }
    public void setStorageBase64(String s) { this.storageBase64 = s == null ? "" : s; }

    public Set<String> getReceivedPrestigeTemplates() { return receivedPrestigeTemplates; }

    public long getBlocksBroken() { return blocksBroken; }
    public void setBlocksBroken(long v) { this.blocksBroken = v; }
    public void incrementBlocksBroken() { this.blocksBroken++; }

    public int getPhaseIndex() { return phaseIndex; }
    public void setPhaseIndex(int v) { this.phaseIndex = v; }

    public int getPhaseProgress() { return phaseProgress; }
    public void setPhaseProgress(int v) { this.phaseProgress = v; }
    public void incrementPhaseProgress() { this.phaseProgress++; }

    public int getPrestigeLevel() { return prestigeLevel; }
    public void setPrestigeLevel(int v) { this.prestigeLevel = v; }

    public int getLevel() { return level; }
    public void setLevel(int v) { this.level = v; }

    public long getLastBossAt() { return lastBossAt; }
    public void setLastBossAt(long v) { this.lastBossAt = v; }

    public long getLastLootRoomAt() { return lastLootRoomAt; }
    public void setLastLootRoomAt(long v) { this.lastLootRoomAt = v; }

    // --- Nether accessors ----------------------------------------------------

    public long getNetherBlocksBroken() { return netherBlocksBroken; }
    public void setNetherBlocksBroken(long v) { this.netherBlocksBroken = v; }
    public void incrementNetherBlocksBroken() { this.netherBlocksBroken++; }

    public int getNetherPhaseIndex() { return netherPhaseIndex; }
    public void setNetherPhaseIndex(int v) { this.netherPhaseIndex = v; }

    public int getNetherPhaseProgress() { return netherPhaseProgress; }
    public void setNetherPhaseProgress(int v) { this.netherPhaseProgress = v; }
    public void incrementNetherPhaseProgress() { this.netherPhaseProgress++; }

    public long getNetherLastBossAt() { return netherLastBossAt; }
    public void setNetherLastBossAt(long v) { this.netherLastBossAt = v; }

    public long getNetherLastLootRoomAt() { return netherLastLootRoomAt; }
    public void setNetherLastLootRoomAt(long v) { this.netherLastLootRoomAt = v; }

    public boolean isNetherUnlocked() { return netherUnlocked; }
    public void setNetherUnlocked(boolean v) { this.netherUnlocked = v; }

    /** Centre block (the one that regenerates). */
    public Location centerBlock() {
        World w = Bukkit.getWorld(worldName == null ? DEFAULT_WORLD_NAME : worldName);
        int x = slotX * SLOT_SIZE;
        int z = slotZ * SLOT_SIZE;
        return new Location(w, x + 0.5, 80, z + 0.5);
    }

    public Location spawnLocation() {
        Location c = centerBlock();
        return new Location(c.getWorld(), c.getX(), c.getY() + 1, c.getZ() + 1.5, 180f, 0f);
    }

    /**
     * Same slot coords as {@link #centerBlock()} but resolved against the Nether
     * world. Returns null if the Nether world isn't loaded — callers should
     * gate on {@link #isNetherUnlocked()} and {@code getWorld() != null}.
     */
    public Location netherCenterBlock() {
        World w = Bukkit.getWorld(NovaBlock.get().worlds().netherWorldName());
        int x = slotX * SLOT_SIZE;
        int z = slotZ * SLOT_SIZE;
        return new Location(w, x + 0.5, 80, z + 0.5);
    }

    public Location netherSpawnLocation() {
        Location c = netherCenterBlock();
        return new Location(c.getWorld(), c.getX(), c.getY() + 1, c.getZ() + 1.5, 180f, 0f);
    }
}
