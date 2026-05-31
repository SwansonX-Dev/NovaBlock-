package com.nova.novablock.spawn;

import com.nova.novablock.NovaBlock;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.jetbrains.annotations.Nullable;

public class SpawnManager {

    private final NovaBlock plugin;

    public SpawnManager(NovaBlock plugin) {
        this.plugin = plugin;
    }

    public @Nullable Location location() {
        FileConfiguration cfg = plugin.getConfig();
        if (!cfg.isSet("spawn.world")) return fallback();
        String worldName = cfg.getString("spawn.world");
        if (worldName == null || worldName.isEmpty()) return fallback();
        World world = Bukkit.getWorld(worldName);
        if (world == null) return fallback();
        double x = cfg.getDouble("spawn.x");
        double y = cfg.getDouble("spawn.y");
        double z = cfg.getDouble("spawn.z");
        float yaw = (float) cfg.getDouble("spawn.yaw", 0.0);
        float pitch = (float) cfg.getDouble("spawn.pitch", 0.0);
        return new Location(world, x, y, z, yaw, pitch);
    }

    /**
     * Resolved location of the community OneBlock: {@link #location()} plus
     * {@code community.hub.offset.{x,y,z}}. Returns null if spawn isn't set.
     */
    public @Nullable Location communityBlockLocation() {
        Location base = location();
        if (base == null) return null;
        FileConfiguration cfg = plugin.getConfig();
        double dx = cfg.getDouble("community.hub.offset.x", 0.0);
        double dy = cfg.getDouble("community.hub.offset.y", 0.0);
        double dz = cfg.getDouble("community.hub.offset.z", 5.0);
        return new Location(base.getWorld(),
                base.getBlockX() + dx + 0.0,
                base.getBlockY() + dy + 0.0,
                base.getBlockZ() + dz + 0.0);
    }

    public void setLocation(Location loc) {
        FileConfiguration cfg = plugin.getConfig();
        World world = loc.getWorld();
        cfg.set("spawn.world", world == null ? null : world.getName());
        cfg.set("spawn.x", loc.getX());
        cfg.set("spawn.y", loc.getY());
        cfg.set("spawn.z", loc.getZ());
        cfg.set("spawn.yaw", (double) loc.getYaw());
        cfg.set("spawn.pitch", (double) loc.getPitch());
        plugin.saveConfig();
    }

    private @Nullable Location fallback() {
        World primary = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
        return primary == null ? null : primary.getSpawnLocation();
    }
}
