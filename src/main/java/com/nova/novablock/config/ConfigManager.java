package com.nova.novablock.config;

import com.nova.novablock.NovaBlock;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class ConfigManager {

    private final NovaBlock plugin;
    private FileConfiguration messages;
    private FileConfiguration bosses;
    private FileConfiguration lootRooms;
    private FileConfiguration skills;

    public ConfigManager(NovaBlock plugin) {
        this.plugin = plugin;
    }

    public void loadAll() {
        plugin.reloadConfig();
        this.messages = loadOrCreate("messages.yml");
        this.bosses = loadOrCreate("bosses.yml");
        this.lootRooms = loadOrCreate("lootrooms.yml");
        this.skills = loadOrCreate("skills.yml");
    }

    private FileConfiguration loadOrCreate(String name) {
        File file = new File(plugin.getDataFolder(), name);
        if (!file.exists()) {
            plugin.saveResource(name, false);
        }
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        InputStream defaults = plugin.getResource(name);
        if (defaults != null) {
            cfg.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(defaults, StandardCharsets.UTF_8)));
        }
        return cfg;
    }

    public FileConfiguration main() { return plugin.getConfig(); }
    public FileConfiguration messages() { return messages; }
    public FileConfiguration bosses() { return bosses; }
    public FileConfiguration lootRooms() { return lootRooms; }
    public FileConfiguration skills() { return skills; }

    public void save() {
        try {
            messages.save(new File(plugin.getDataFolder(), "messages.yml"));
            bosses.save(new File(plugin.getDataFolder(), "bosses.yml"));
            lootRooms.save(new File(plugin.getDataFolder(), "lootrooms.yml"));
            skills.save(new File(plugin.getDataFolder(), "skills.yml"));
        } catch (IOException ex) {
            plugin.getLogger().warning("Failed to save configs: " + ex.getMessage());
        }
    }
}
