package com.nova.novablock.spawn;

import com.nova.novablock.NovaBlock;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class PlayerSpawnManager {

    private final NovaBlock plugin;
    private final File file;
    private FileConfiguration data;
    private final ConcurrentMap<UUID, Location> cache = new ConcurrentHashMap<>();
    private final AtomicBoolean dirty = new AtomicBoolean(false);
    private static final long SAVE_DEBOUNCE_TICKS = 60L;

    public PlayerSpawnManager(NovaBlock plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "player_spawns.yml");
        load();
    }

    private void load() {
        if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
        if (!file.exists()) {
            try { file.createNewFile(); }
            catch (IOException e) { plugin.getLogger().severe("Could not create player_spawns.yml: " + e.getMessage()); }
        }
        data = YamlConfiguration.loadConfiguration(file);
        cache.clear();
        for (String key : data.getKeys(false)) {
            UUID uuid;
            try { uuid = UUID.fromString(key); } catch (IllegalArgumentException e) { continue; }
            Location loc = readLocation(data.getConfigurationSection(key));
            if (loc != null) cache.put(uuid, loc);
        }
    }

    public @Nullable Location get(UUID uuid) {
        Location cached = cache.get(uuid);
        if (cached == null) return null;
        if (cached.getWorld() == null) {
            // World was unloaded after caching — fall through to a re-read in case it's back.
            Location reread = readLocation(data.getConfigurationSection(uuid.toString()));
            if (reread == null) return null;
            cache.put(uuid, reread);
            return reread;
        }
        return cached;
    }

    public void set(UUID uuid, Location loc) {
        cache.put(uuid, loc);
        ConfigurationSection section = data.createSection(uuid.toString());
        World world = loc.getWorld();
        section.set("world", world == null ? null : world.getName());
        section.set("x", loc.getX());
        section.set("y", loc.getY());
        section.set("z", loc.getZ());
        section.set("yaw", (double) loc.getYaw());
        section.set("pitch", (double) loc.getPitch());
        scheduleSave();
    }

    public void clear(UUID uuid) {
        cache.remove(uuid);
        data.set(uuid.toString(), null);
        scheduleSave();
    }

    public boolean has(UUID uuid) {
        return cache.containsKey(uuid);
    }

    private @Nullable Location readLocation(@Nullable ConfigurationSection section) {
        if (section == null) return null;
        String worldName = section.getString("world");
        if (worldName == null) return null;
        World world = Bukkit.getWorld(worldName);
        if (world == null) return null;
        return new Location(world,
                section.getDouble("x"),
                section.getDouble("y"),
                section.getDouble("z"),
                (float) section.getDouble("yaw", 0.0),
                (float) section.getDouble("pitch", 0.0));
    }

    private void scheduleSave() {
        if (!dirty.compareAndSet(false, true)) return;
        Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, this::flush, SAVE_DEBOUNCE_TICKS);
    }

    private synchronized void flush() {
        if (!dirty.compareAndSet(true, false)) return;
        try { data.save(file); }
        catch (IOException e) { plugin.getLogger().severe("Could not save player_spawns.yml: " + e.getMessage()); }
    }

    public synchronized void saveNow() {
        if (data == null) return;
        dirty.set(false);
        try { data.save(file); }
        catch (IOException e) { plugin.getLogger().severe("Could not save player_spawns.yml: " + e.getMessage()); }
    }
}
