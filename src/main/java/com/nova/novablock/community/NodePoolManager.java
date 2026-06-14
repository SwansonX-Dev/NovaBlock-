package com.nova.novablock.community;

import com.nova.novablock.NovaBlock;
import com.nova.novablock.community.CommunityNodeType.Weighted;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Editable, persisted block pools for the Personal OneBlock node types. Each
 * {@link CommunityNodeType} ships a code-default pool ({@link CommunityNodeType#defaultPool()});
 * this manager lets admins add/remove materials at runtime (via the in-game
 * editor GUI) and stores only the <em>customised</em> pools in {@code node-pools.yml}.
 *
 * <p>A type with no override rolls from its code default, so updating the
 * defaults in a new build still reaches every server that never touched that
 * type. Once edited, a type's full pool is frozen into the yaml.
 */
public final class NodePoolManager {

    /** Default weight applied when an admin adds a block via the editor. */
    public static final int DEFAULT_WEIGHT = 10;

    private final NovaBlock plugin;
    private final File file;
    /** Only customised types live here; absent types use the code default. */
    private final Map<CommunityNodeType, List<Weighted>> overrides = new EnumMap<>(CommunityNodeType.class);

    public NodePoolManager(NovaBlock plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "node-pools.yml");
        load();
    }

    /** Effective pool for a type: the override if customised, else the code default. Read-only. */
    public synchronized List<Weighted> pool(CommunityNodeType type) {
        List<Weighted> override = overrides.get(type);
        return override != null ? override : type.defaultPool();
    }

    /** Weighted-random material from the effective pool (falls back to the type icon if empty). */
    public synchronized Material roll(CommunityNodeType type, Random rng) {
        List<Weighted> pool = pool(type);
        int total = 0;
        for (Weighted w : pool) total += Math.max(0, w.weight());
        if (total <= 0) return type.icon();
        int roll = rng.nextInt(total);
        int acc = 0;
        for (Weighted w : pool) {
            acc += Math.max(0, w.weight());
            if (roll < acc) return w.material();
        }
        return pool.get(0).material();
    }

    /** Mutable copy of the effective pool, promoted into the overrides map for editing. */
    private List<Weighted> editable(CommunityNodeType type) {
        return overrides.computeIfAbsent(type, t -> new ArrayList<>(t.defaultPool()));
    }

    /**
     * Add a material to a type's pool, or reset its weight if already present.
     * @return true if newly added, false if it was already in the pool (weight updated)
     */
    public synchronized boolean addMaterial(CommunityNodeType type, Material material, int weight) {
        List<Weighted> pool = editable(type);
        int w = Math.max(1, weight);
        for (int i = 0; i < pool.size(); i++) {
            if (pool.get(i).material() == material) {
                pool.set(i, new Weighted(material, w));
                save();
                return false;
            }
        }
        pool.add(new Weighted(material, w));
        save();
        return true;
    }

    /** Remove a material from a type's pool. @return true if it was present. */
    public synchronized boolean removeMaterial(CommunityNodeType type, Material material) {
        List<Weighted> pool = editable(type);
        boolean removed = pool.removeIf(w -> w.material() == material);
        if (removed) save();
        return removed;
    }

    /** Drop any customisation and revert this type to its code-default pool. */
    public synchronized void resetToDefault(CommunityNodeType type) {
        overrides.remove(type);
        save();
    }

    /** True if this type has been customised away from its code default. */
    public synchronized boolean isCustomised(CommunityNodeType type) {
        return overrides.containsKey(type);
    }

    private void load() {
        if (!file.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = y.getConfigurationSection("pools");
        if (root == null) return;
        for (String key : root.getKeys(false)) {
            CommunityNodeType type = CommunityNodeType.byId(key);
            if (type == null) continue;
            List<Weighted> list = new ArrayList<>();
            for (String entry : root.getStringList(key)) {
                // Format: "MATERIAL:weight"
                String[] parts = entry.split(":", 2);
                Material m = Material.matchMaterial(parts[0].trim());
                if (m == null) continue;
                int weight = DEFAULT_WEIGHT;
                if (parts.length == 2) {
                    try { weight = Math.max(1, Integer.parseInt(parts[1].trim())); }
                    catch (NumberFormatException ignored) {}
                }
                list.add(new Weighted(m, weight));
            }
            if (!list.isEmpty()) overrides.put(type, list);
        }
    }

    public synchronized void save() {
        YamlConfiguration y = new YamlConfiguration();
        for (Map.Entry<CommunityNodeType, List<Weighted>> e : overrides.entrySet()) {
            List<String> list = new ArrayList<>(e.getValue().size());
            for (Weighted w : e.getValue()) list.add(w.material().name() + ":" + w.weight());
            y.set("pools." + e.getKey().id(), list);
        }
        try { y.save(file); }
        catch (IOException ex) { plugin.getLogger().warning("Failed to save node-pools.yml: " + ex.getMessage()); }
    }
}
