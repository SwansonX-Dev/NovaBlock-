package com.nova.novablock.minion;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class MinionData {
    private final UUID id;
    /** Owning island, or null for a community-claim (owner-based) minion. */
    private final UUID islandId;
    /** Owning player for community minions (null for island minions). */
    private UUID ownerId;
    private final MinionType type;
    private String worldName;
    private double x;
    private double y;
    private double z;
    private String linkedWorldName;
    private int linkedX;
    private int linkedY;
    private int linkedZ;
    private final EnumMap<MinionUpgrade, Integer> upgrades = new EnumMap<>(MinionUpgrade.class);
    private String skin = "default";
    private long fuelTicksRemaining;
    private long accumulatedTicks;
    private MinionStatus status = MinionStatus.UNLINKED;
    private final List<String> productionLog = new ArrayList<>();

    public MinionData(UUID id, UUID islandId, MinionType type, Location location) {
        this.id = id;
        this.islandId = islandId;
        this.type = type;
        setLocation(location);
        for (MinionUpgrade upgrade : MinionUpgrade.values()) upgrades.put(upgrade, 0);
    }

    public UUID id() { return id; }
    public UUID islandId() { return islandId; }
    public UUID ownerId() { return ownerId; }
    public void setOwnerId(UUID ownerId) { this.ownerId = ownerId; }
    /** True for a community-claim minion (owner-based, not tied to an island). */
    public boolean isCommunity() { return islandId == null; }
    public MinionType type() { return type; }
    public String worldName() { return worldName; }
    public double x() { return x; }
    public double y() { return y; }
    public double z() { return z; }
    public String linkedWorldName() { return linkedWorldName; }
    public int linkedX() { return linkedX; }
    public int linkedY() { return linkedY; }
    public int linkedZ() { return linkedZ; }
    public String skin() { return skin; }
    public long fuelTicksRemaining() { return fuelTicksRemaining; }
    public long accumulatedTicks() { return accumulatedTicks; }
    public MinionStatus status() { return status; }
    public List<String> productionLog() { return Collections.unmodifiableList(productionLog); }
    public Map<MinionUpgrade, Integer> upgrades() { return upgrades; }

    public void setLocation(Location location) {
        this.worldName = location.getWorld() == null ? "" : location.getWorld().getName();
        this.x = location.getX();
        this.y = location.getY();
        this.z = location.getZ();
    }

    public Location location() {
        World world = Bukkit.getWorld(worldName);
        return world == null ? null : new Location(world, x, y, z);
    }

    public void setLinked(Location location) {
        linkedWorldName = location.getWorld() == null ? null : location.getWorld().getName();
        linkedX = location.getBlockX();
        linkedY = location.getBlockY();
        linkedZ = location.getBlockZ();
    }

    public boolean hasLinkedChest() { return linkedWorldName != null && !linkedWorldName.isBlank(); }

    public Location linkedLocation() {
        if (!hasLinkedChest()) return null;
        World world = Bukkit.getWorld(linkedWorldName);
        return world == null ? null : new Location(world, linkedX, linkedY, linkedZ);
    }

    public int upgrade(MinionUpgrade upgrade) { return upgrades.getOrDefault(upgrade, 0); }
    public void setUpgrade(MinionUpgrade upgrade, int level) { upgrades.put(upgrade, Math.max(0, Math.min(level, upgrade.maxLevel()))); }
    public void setSkin(String skin) { this.skin = skin == null || skin.isBlank() ? "default" : skin; }
    public void addFuelTicks(long ticks) { fuelTicksRemaining = Math.max(0L, fuelTicksRemaining + ticks); }
    public void consumeFuelTicks(long ticks) { fuelTicksRemaining = Math.max(0L, fuelTicksRemaining - Math.max(0L, ticks)); }
    public void setFuelTicksRemaining(long ticks) { fuelTicksRemaining = Math.max(0L, ticks); }
    public void addAccumulatedTicks(long ticks) { accumulatedTicks = Math.max(0L, accumulatedTicks + ticks); }
    public void consumeAccumulatedTicks(long ticks) { accumulatedTicks = Math.max(0L, accumulatedTicks - Math.max(0L, ticks)); }
    public void setAccumulatedTicks(long ticks) { accumulatedTicks = Math.max(0L, ticks); }
    public void setStatus(MinionStatus status) { this.status = status == null ? MinionStatus.READY : status; }

    public void addProductionLog(String entry) {
        if (entry == null || entry.isBlank()) return;
        productionLog.add(0, entry);
        while (productionLog.size() > 5) productionLog.remove(productionLog.size() - 1);
    }

    public void loadProductionLog(List<String> entries) {
        productionLog.clear();
        if (entries == null) return;
        for (String entry : entries) {
            if (entry == null || entry.isBlank()) continue;
            productionLog.add(entry);
            if (productionLog.size() >= 5) break;
        }
    }
}
