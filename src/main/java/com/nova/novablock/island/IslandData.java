package com.nova.novablock.island;

import com.nova.novablock.NovaBlock;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.EnumMap;
import java.util.HashMap;
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
    /** Per-member rank. The owner is implicitly OWNER; non-owner members default to MEMBER. */
    private final Map<UUID, IslandRole> roles = new HashMap<>();
    /**
     * Non-member players granted build/break + container access on this island
     * via /ob trust. Distinct from {@link #members} — trusted players keep their own
     * island and have no roster/bank rights here, only build access.
     */
    private final Set<UUID> trusted = new HashSet<>();
    private final Map<IslandFlag, Boolean> flags = new EnumMap<>(IslandFlag.class);
    private final Map<IslandUpgrade, Integer> upgrades = new EnumMap<>(IslandUpgrade.class);
    /** Shared island bank, in whole coins. Funds upgrades; members deposit, owner withdraws. */
    private long bankBalance;
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

    // --- Endless island questline (shared across all members) ----------------
    /** Current questline stage (1-based, endless). */
    private int questlineStage = 1;
    /** Progress toward the current stage's objective. */
    private int questlineProgress;

    // --- Nether dimension (added in v0.5.0) ----------------------------------
    private long netherBlocksBroken;
    private int netherPhaseIndex;
    private int netherPhaseProgress;
    private long netherLastBossAt;
    private long netherLastLootRoomAt;
    /** Set the first time the Overworld crosses into Phase 7 (phaseIndex >= 6). */
    private boolean netherUnlocked;
    /** True until the owner has visited their own Nether for the first time. */
    private boolean firstNetherVisit = true;

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

    // --- roles ---------------------------------------------------------------

    /** Live role map (non-owner members only; owner is always OWNER via {@link #getRole}). */
    public Map<UUID, IslandRole> getRoles() { return roles; }

    /** Effective role: OWNER for the owner, the stored role for members, MEMBER otherwise. */
    public IslandRole getRole(UUID playerId) {
        if (owner.equals(playerId)) return IslandRole.OWNER;
        if (!members.contains(playerId)) return IslandRole.MEMBER;
        return roles.getOrDefault(playerId, IslandRole.MEMBER);
    }

    /** Set a member's role. No-op for the owner (always OWNER) or non-members. */
    public void setRole(UUID playerId, IslandRole role) {
        if (owner.equals(playerId) || !members.contains(playerId)) return;
        if (role == null || role == IslandRole.MEMBER) roles.remove(playerId);
        else roles.put(playerId, role);
    }

    // --- trusted players -----------------------------------------------------

    /** Live trusted-player set. */
    public Set<UUID> getTrusted() { return trusted; }

    public boolean isTrusted(UUID playerId) { return trusted.contains(playerId); }

    /**
     * Grant build access to a non-member. Returns false (changing nothing) if the
     * player is the owner or an existing member — they already have access.
     */
    public boolean addTrusted(UUID playerId) {
        if (owner.equals(playerId) || members.contains(playerId)) return false;
        return trusted.add(playerId);
    }

    /** Revoke a player's trust. Returns false if they weren't trusted. */
    public boolean removeTrusted(UUID playerId) { return trusted.remove(playerId); }

    // --- island bank ---------------------------------------------------------

    public long getBankBalance() { return bankBalance; }
    public void setBankBalance(long v) { this.bankBalance = Math.max(0, v); }

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

    public int getQuestlineStage() { return questlineStage; }
    public void setQuestlineStage(int v) { this.questlineStage = Math.max(1, v); }

    public int getQuestlineProgress() { return questlineProgress; }
    public void setQuestlineProgress(int v) { this.questlineProgress = Math.max(0, v); }

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

    public boolean isFirstNetherVisit() { return firstNetherVisit; }
    public void setFirstNetherVisit(boolean v) { this.firstNetherVisit = v; }

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
