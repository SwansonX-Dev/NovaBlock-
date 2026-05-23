package com.nova.novablock.gui;

import com.nova.novablock.NovaBlock;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Admin-editable custom buttons that overlay the main menu. Persists to
 * {@code menu.yml} in the plugin data folder. Each entry is keyed by slot.
 *
 * <p>Schema:
 * <pre>
 * items:
 *   29:
 *     material: DIAMOND
 *     name: "&aCrates"
 *     command: "crates open"
 *     lore:
 *       - "&7Open the crate menu"
 * </pre>
 */
public final class MainMenuConfig {

    public static final class Entry {
        public final int slot;
        public final Material material;
        public String name;
        public String command;
        public final List<String> lore;

        public Entry(int slot, Material material, String name, String command, List<String> lore) {
            this.slot = slot;
            this.material = material;
            this.name = name;
            this.command = command;
            this.lore = new ArrayList<>(lore);
        }
    }

    private final NovaBlock plugin;
    private final File file;
    private final Map<Integer, Entry> items = new LinkedHashMap<>();

    public MainMenuConfig(NovaBlock plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "menu.yml");
        load();
    }

    public void load() {
        items.clear();
        if (!file.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = y.getConfigurationSection("items");
        if (root == null) return;
        for (String key : root.getKeys(false)) {
            try {
                int slot = Integer.parseInt(key);
                ConfigurationSection s = root.getConfigurationSection(key);
                if (s == null) continue;
                Material mat = Material.matchMaterial(s.getString("material", "PAPER"));
                if (mat == null) mat = Material.PAPER;
                String name = s.getString("name", mat.name());
                String command = s.getString("command", "");
                List<String> lore = s.getStringList("lore");
                items.put(slot, new Entry(slot, mat, name, command, lore));
            } catch (NumberFormatException ignored) {}
        }
    }

    public void save() {
        YamlConfiguration y = new YamlConfiguration();
        for (Entry e : items.values()) {
            String base = "items." + e.slot + ".";
            y.set(base + "material", e.material.name());
            y.set(base + "name", e.name);
            y.set(base + "command", e.command);
            y.set(base + "lore", e.lore);
        }
        try {
            file.getParentFile().mkdirs();
            y.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("Failed to save menu.yml: " + ex.getMessage());
        }
    }

    public Map<Integer, Entry> all() { return items; }

    public Entry get(int slot) { return items.get(slot); }

    public Entry put(int slot, Material material, String name, String command) {
        Entry e = new Entry(slot, material, name, command, new ArrayList<>());
        items.put(slot, e);
        save();
        return e;
    }

    public boolean remove(int slot) {
        boolean had = items.remove(slot) != null;
        if (had) save();
        return had;
    }
}
