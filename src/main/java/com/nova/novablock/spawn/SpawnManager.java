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
