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
    /**
     * Not final: an island can change hands via the island market
     * ({@link com.nova.novablock.island.IslandMarketService}). Mutate only through
     * {@link IslandManager#transferOwnership}, which also fixes the owner→island index,
     * the member set and the role map — setting this field alone leaves those stale.
     */
    private UUID owner;
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

    /**
     * Epoch millis when a member of this island was last online. Refreshed on join and
     * quit, so it survives restarts and reflects the island rather than any one player —
     * a co-op island stays "active" as long as anybody on it plays. Drives the inactivity
     * purge ({@link com.nova.novablock.island.IslandPurgeService}). 0 means "never seen",
     * which is treated as active so pre-existing islands aren't purged for lacking data.
     */
    private long lastActiveMillis;

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
    private int netherPrestigeLevel;
    private long netherLastBossAt;
    private long netherLastLootRoomAt;
    /** Set the first time the Overworld crosses into Phase 7 (phaseIndex >= 6). */
    private boolean netherUnlocked;
    /** True until the owner has visited their own Nether for the first time. */
    private boolean firstNetherVisit = true;

    // --- End dimension (added in v0.35.0) ------------------------------------
    private long endBlocksBroken;
    private int endPhaseIndex;
    private int endPhaseProgress;
    private int endPrestigeLevel;
    private long endLastBossAt;
    private long endLastLootRoomAt;
    /** Set the first time the island prestiges (End is a post-prestige dimension). */
    private boolean endUnlocked;
    /** True until the owner has visited their own End for the first time. */
    private boolean firstEndVisit = true;

    /**
     * Set whenever persisted state changes; cleared once written to disk. The
     * autosave only rewrites dirty islands. Starts {@code false}: a freshly
     * loaded island already matches its file, and {@code create()} saves once
     * immediately. Mutated only from the main thread.
     */
    private transient boolean dirty;

    /** Mark this island as having unsaved changes. */
    public void markDirty() { this.dirty = true; }
    public boolean isDirty() { return dirty; }
    /** Clear the dirty flag — call right after the data has been (or is being) persisted. */
    public void clearDirty() { this.dirty = false; }

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
    /** @see #owner — go through {@link IslandManager#transferOwnership} instead. */
    void setOwner(UUID newOwner) { this.owner = newOwner; markDirty(); }
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
        markDirty();
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
        if (!trusted.add(playerId)) return false;
        markDirty();
        return true;
    }

    /** Revoke a player's trust. Returns false if they weren't trusted. */
    public boolean removeTrusted(UUID playerId) {
        if (!trusted.remove(playerId)) return false;
        markDirty();
        return true;
    }

    // --- island bank ---------------------------------------------------------

    public long getBankBalance() { return bankBalance; }
    public void setBankBalance(long v) { this.bankBalance = Math.max(0, v); markDirty(); }

    /** Live flag map. Missing entries fall back to {@link IslandFlag#defaultValue}. */
    public Map<IslandFlag, Boolean> getFlags() { return flags; }

    public boolean isFlag(IslandFlag f) {
        Boolean v = flags.get(f);
        return v == null ? f.defaultValue : v;
    }

    public void setFlag(IslandFlag f, boolean v) { flags.put(f, v); markDirty(); }

    public Map<IslandUpgrade, Integer> getUpgrades() { return upgrades; }

    public int getUpgradeLevel(IslandUpgrade u) {
        Integer v = upgrades.get(u);
        return v == null ? 0 : v;
    }

    public void setUpgradeLevel(IslandUpgrade u, int level) {
        upgrades.put(u, Math.max(0, Math.min(u.maxLevel, level)));
        markDirty();
    }

    public String getStorageBase64() { return storageBase64; }
    public void setStorageBase64(String s) { this.storageBase64 = s == null ? "" : s; markDirty(); }

    public Set<String> getReceivedPrestigeTemplates() { return receivedPrestigeTemplates; }

    public long getBlocksBroken() { return blocksBroken; }
    public void setBlocksBroken(long v) { this.blocksBroken = v; markDirty(); }

    public long getLastActiveMillis() { return lastActiveMillis; }
    public void setLastActiveMillis(long v) { this.lastActiveMillis = v; markDirty(); }
    /** Stamps this island as active now. Called when any member joins or quits. */
    public void touchActivity() { setLastActiveMillis(System.currentTimeMillis()); }
    /**
     * Days since a member was last online, or 0 if the island has never been stamped
     * (treated as active so islands predating this field are never auto-purged).
     */
    public long daysInactive() {
        if (lastActiveMillis <= 0L) return 0L;
        return java.time.Duration.ofMillis(System.currentTimeMillis() - lastActiveMillis).toDays();
    }
    public void incrementBlocksBroken() { this.blocksBroken++; markDirty(); }

    public int getPhaseIndex() { return phaseIndex; }
    public void setPhaseIndex(int v) { this.phaseIndex = v; markDirty(); }

    public int getPhaseProgress() { return phaseProgress; }
    public void setPhaseProgress(int v) { this.phaseProgress = v; markDirty(); }
    public void incrementPhaseProgress() { this.phaseProgress++; markDirty(); }

    public int getPrestigeLevel() { return prestigeLevel; }
    public void setPrestigeLevel(int v) { this.prestigeLevel = v; markDirty(); }

    public int getLevel() { return level; }
    public void setLevel(int v) { this.level = v; markDirty(); }

    public int getQuestlineStage() { return questlineStage; }
    public void setQuestlineStage(int v) { this.questlineStage = Math.max(1, v); markDirty(); }

    public int getQuestlineProgress() { return questlineProgress; }
    public void setQuestlineProgress(int v) { this.questlineProgress = Math.max(0, v); markDirty(); }

    public long getLastBossAt() { return lastBossAt; }
    public void setLastBossAt(long v) { this.lastBossAt = v; markDirty(); }

    public long getLastLootRoomAt() { return lastLootRoomAt; }
    public void setLastLootRoomAt(long v) { this.lastLootRoomAt = v; markDirty(); }

    // --- Nether accessors ----------------------------------------------------

    public long getNetherBlocksBroken() { return netherBlocksBroken; }
    public void setNetherBlocksBroken(long v) { this.netherBlocksBroken = v; markDirty(); }
    public void incrementNetherBlocksBroken() { this.netherBlocksBroken++; markDirty(); }

    public int getNetherPhaseIndex() { return netherPhaseIndex; }
    public void setNetherPhaseIndex(int v) { this.netherPhaseIndex = v; markDirty(); }

    public int getNetherPhaseProgress() { return netherPhaseProgress; }
    public void setNetherPhaseProgress(int v) { this.netherPhaseProgress = v; markDirty(); }
    public void incrementNetherPhaseProgress() { this.netherPhaseProgress++; markDirty(); }

    public int getNetherPrestigeLevel() { return netherPrestigeLevel; }
    public void setNetherPrestigeLevel(int v) { this.netherPrestigeLevel = v; markDirty(); }

    public long getNetherLastBossAt() { return netherLastBossAt; }
    public void setNetherLastBossAt(long v) { this.netherLastBossAt = v; markDirty(); }

    public long getNetherLastLootRoomAt() { return netherLastLootRoomAt; }
    public void setNetherLastLootRoomAt(long v) { this.netherLastLootRoomAt = v; markDirty(); }

    public boolean isNetherUnlocked() { return netherUnlocked; }
    public void setNetherUnlocked(boolean v) { this.netherUnlocked = v; markDirty(); }

    public boolean isFirstNetherVisit() { return firstNetherVisit; }
    public void setFirstNetherVisit(boolean v) { this.firstNetherVisit = v; markDirty(); }

    // --- End accessors -------------------------------------------------------

    public long getEndBlocksBroken() { return endBlocksBroken; }
    public void setEndBlocksBroken(long v) { this.endBlocksBroken = v; markDirty(); }
    public void incrementEndBlocksBroken() { this.endBlocksBroken++; markDirty(); }

    public int getEndPhaseIndex() { return endPhaseIndex; }
    public void setEndPhaseIndex(int v) { this.endPhaseIndex = v; markDirty(); }

    public int getEndPhaseProgress() { return endPhaseProgress; }
    public void setEndPhaseProgress(int v) { this.endPhaseProgress = v; markDirty(); }
    public void incrementEndPhaseProgress() { this.endPhaseProgress++; markDirty(); }

    public int getEndPrestigeLevel() { return endPrestigeLevel; }
    public void setEndPrestigeLevel(int v) { this.endPrestigeLevel = v; markDirty(); }

    public long getEndLastBossAt() { return endLastBossAt; }
    public void setEndLastBossAt(long v) { this.endLastBossAt = v; markDirty(); }

    public long getEndLastLootRoomAt() { return endLastLootRoomAt; }
    public void setEndLastLootRoomAt(long v) { this.endLastLootRoomAt = v; markDirty(); }

    public boolean isEndUnlocked() { return endUnlocked; }
    public void setEndUnlocked(boolean v) { this.endUnlocked = v; markDirty(); }

    public boolean isFirstEndVisit() { return firstEndVisit; }
    public void setFirstEndVisit(boolean v) { this.firstEndVisit = v; markDirty(); }

    // --- Dimension-parameterized convenience accessors -----------------------
    // Centralize the three-way switching so listeners/services stay clean.

    public long getBlocksBroken(Dimension d) {
        return switch (d) {
            case OVERWORLD -> blocksBroken;
            case NETHER -> netherBlocksBroken;
            case END -> endBlocksBroken;
        };
    }

    public void incrementBlocksBroken(Dimension d) {
        switch (d) {
            case OVERWORLD -> incrementBlocksBroken();
            case NETHER -> incrementNetherBlocksBroken();
            case END -> incrementEndBlocksBroken();
        }
    }

    public int getPhaseIndex(Dimension d) {
        return switch (d) {
            case OVERWORLD -> phaseIndex;
            case NETHER -> netherPhaseIndex;
            case END -> endPhaseIndex;
        };
    }

    public void setPhaseIndex(Dimension d, int v) {
        switch (d) {
            case OVERWORLD -> setPhaseIndex(v);
            case NETHER -> setNetherPhaseIndex(v);
            case END -> setEndPhaseIndex(v);
        }
    }

    public int getPhaseProgress(Dimension d) {
        return switch (d) {
            case OVERWORLD -> phaseProgress;
            case NETHER -> netherPhaseProgress;
            case END -> endPhaseProgress;
        };
    }

    public void setPhaseProgress(Dimension d, int v) {
        switch (d) {
            case OVERWORLD -> setPhaseProgress(v);
            case NETHER -> setNetherPhaseProgress(v);
            case END -> setEndPhaseProgress(v);
        }
    }

    public void incrementPhaseProgress(Dimension d) {
        switch (d) {
            case OVERWORLD -> incrementPhaseProgress();
            case NETHER -> incrementNetherPhaseProgress();
            case END -> incrementEndPhaseProgress();
        }
    }

    public long getLastBossAt(Dimension d) {
        return switch (d) {
            case OVERWORLD -> lastBossAt;
            case NETHER -> netherLastBossAt;
            case END -> endLastBossAt;
        };
    }

    public void setLastBossAt(Dimension d, long v) {
        switch (d) {
            case OVERWORLD -> setLastBossAt(v);
            case NETHER -> setNetherLastBossAt(v);
            case END -> setEndLastBossAt(v);
        }
    }

    public long getLastLootRoomAt(Dimension d) {
        return switch (d) {
            case OVERWORLD -> lastLootRoomAt;
            case NETHER -> netherLastLootRoomAt;
            case END -> endLastLootRoomAt;
        };
    }

    public void setLastLootRoomAt(Dimension d, long v) {
        switch (d) {
            case OVERWORLD -> setLastLootRoomAt(v);
            case NETHER -> setNetherLastLootRoomAt(v);
            case END -> setEndLastLootRoomAt(v);
        }
    }

    /** Each dimension has its own independent prestige level (Overworld = the legacy {@code prestigeLevel}). */
    public int getPrestigeLevel(Dimension d) {
        return switch (d) {
            case OVERWORLD -> prestigeLevel;
            case NETHER -> netherPrestigeLevel;
            case END -> endPrestigeLevel;
        };
    }

    public void setPrestigeLevel(Dimension d, int v) {
        switch (d) {
            case OVERWORLD -> setPrestigeLevel(v);
            case NETHER -> setNetherPrestigeLevel(v);
            case END -> setEndPrestigeLevel(v);
        }
    }

    /** Sum of all three dimensions' prestige levels (uncapped). */
    public int getTotalPrestigeLevel() {
        return prestigeLevel + netherPrestigeLevel + endPrestigeLevel;
    }

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

    // --- visit spot ----------------------------------------------------------

    /**
     * Where {@code /ob visit} drops a visitor. Unset by default, in which case
     * visitors land on {@link #spawnLocation()} next to the centre block, which
     * is what happened before this existed. Set with {@code /ob setvisit}.
     *
     * <p>Stored as a world name plus coords rather than a Location so it can be
     * persisted and so an unloaded world degrades to "unset" instead of throwing.
     */
    private String visitWorld;
    private double visitX;
    private double visitY;
    private double visitZ;
    private float visitYaw;
    private float visitPitch;

    /** True if the owner has set a custom visit spot. */
    public boolean hasVisitSpot() { return visitWorld != null; }

    public void setVisitSpot(Location loc) {
        this.visitWorld = loc.getWorld() == null ? null : loc.getWorld().getName();
        this.visitX = loc.getX();
        this.visitY = loc.getY();
        this.visitZ = loc.getZ();
        this.visitYaw = loc.getYaw();
        this.visitPitch = loc.getPitch();
        markDirty();
    }

    public void clearVisitSpot() {
        this.visitWorld = null;
        markDirty();
    }

    /**
     * The stored visit spot, or {@code null} if unset or its world isn't loaded.
     * Callers should fall back to {@link #spawnLocation()}.
     */
    public Location visitLocation() {
        if (visitWorld == null) return null;
        World w = Bukkit.getWorld(visitWorld);
        if (w == null) return null;
        return new Location(w, visitX, visitY, visitZ, visitYaw, visitPitch);
    }

    /** Raw accessors for persistence — {@link #visitLocation()} for everything else. */
    public String getVisitWorld() { return visitWorld; }
    public double getVisitX() { return visitX; }
    public double getVisitY() { return visitY; }
    public double getVisitZ() { return visitZ; }
    public float getVisitYaw() { return visitYaw; }
    public float getVisitPitch() { return visitPitch; }

    /** Restores a persisted visit spot without marking dirty — load path only. */
    public void restoreVisitSpot(String world, double x, double y, double z, float yaw, float pitch) {
        this.visitWorld = world;
        this.visitX = x;
        this.visitY = y;
        this.visitZ = z;
        this.visitYaw = yaw;
        this.visitPitch = pitch;
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

    /**
     * Same slot coords as {@link #centerBlock()} but resolved against the End
     * world. Returns a Location with a null world if the End world isn't loaded
     * — callers should gate on {@link #isEndUnlocked()} and {@code getWorld() != null}.
     */
    public Location endCenterBlock() {
        World w = Bukkit.getWorld(NovaBlock.get().worlds().endWorldName());
        int x = slotX * SLOT_SIZE;
        int z = slotZ * SLOT_SIZE;
        return new Location(w, x + 0.5, 80, z + 0.5);
    }

    public Location endSpawnLocation() {
        Location c = endCenterBlock();
        return new Location(c.getWorld(), c.getX(), c.getY() + 1, c.getZ() + 1.5, 180f, 0f);
    }

    /** Centre block resolved against whichever dimension's world. */
    public Location centerBlock(Dimension d) {
        return switch (d) {
            case OVERWORLD -> centerBlock();
            case NETHER -> netherCenterBlock();
            case END -> endCenterBlock();
        };
    }
}
