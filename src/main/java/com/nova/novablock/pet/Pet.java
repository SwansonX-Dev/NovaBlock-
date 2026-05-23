package com.nova.novablock.pet;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.UUID;

public class Pet {

    private final UUID ownerId;
    private final PetType type;
    private final UUID entityId;
    private String customName;
    private int level;
    private long xp;
    private PetTask task;
    private final Inventory storage;
    /** Timestamp (ms) of last support-task tick. Per-pet so multiple Support pets don't clash. */
    private long lastSupportTickMs;

    public Pet(UUID ownerId, PetType type, UUID entityId, int level) {
        this.ownerId = ownerId;
        this.type = type;
        this.entityId = entityId;
        this.level = level;
        this.task = type.defaultTask;
        this.storage = type == PetType.CHEST_PIG
                ? Bukkit.createInventory(null, 27, net.kyori.adventure.text.Component.text("Pack Pig"))
                : null;
    }

    public UUID ownerId() { return ownerId; }
    public PetType type() { return type; }
    public UUID entityId() { return entityId; }
    public LivingEntity entity() {
        var e = Bukkit.getEntity(entityId);
        return e instanceof LivingEntity le ? le : null;
    }

    public Player owner() { return Bukkit.getPlayer(ownerId); }

    public String customName() { return customName; }
    public void setCustomName(String n) { this.customName = n; }

    public int level() { return level; }
    public void setLevel(int l) { this.level = l; }

    public long xp() { return xp; }
    public void addXp(long x) { this.xp = Math.max(0, this.xp + x); }
    public void setXp(long x) { this.xp = x; }

    public PetTask task() { return task; }
    public void setTask(PetTask t) { this.task = t; }

    public Inventory storage() { return storage; }

    public long lastSupportTickMs() { return lastSupportTickMs; }
    public void setLastSupportTickMs(long t) { this.lastSupportTickMs = t; }

    public Location entityLocation() {
        var e = entity();
        return e == null ? null : e.getLocation();
    }

    public static long xpForLevel(int level) { return 50L + 25L * (long) level * level; }
}
