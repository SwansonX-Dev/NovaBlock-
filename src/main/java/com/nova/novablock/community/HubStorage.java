package com.nova.novablock.community;

import com.nova.novablock.NovaBlock;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/**
 * Atomic load/save for the community hub state. One file
 * {@code <datafolder>/community/hub.yml} holds block + goal + raid sections.
 */
class HubStorage {

    private final NovaBlock plugin;
    private final File file;

    HubStorage(NovaBlock plugin) {
        this.plugin = plugin;
        File dir = new File(plugin.getDataFolder(), "community");
        if (!dir.exists()) dir.mkdirs();
        this.file = new File(dir, "hub.yml");
    }

    void load(CommunityBlock block, WeeklyGoal goal, RaidScheduler raids) {
        if (!file.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);

        ConfigurationSection b = y.getConfigurationSection("block");
        if (b != null) {
            block.setBlocksBroken(b.getLong("blocksBroken", 0));
            block.setPhaseIndex(b.getInt("phaseIndex", 0));
            block.setPhaseProgress(b.getInt("phaseProgress", 0));
            block.setLastPayoutAt(b.getLong("lastPayoutAt", System.currentTimeMillis()));
            ConfigurationSection contrib = b.getConfigurationSection("contributions");
            if (contrib != null) {
                for (String key : contrib.getKeys(false)) {
                    try { block.contributionByPlayer().put(UUID.fromString(key), contrib.getLong(key)); }
                    catch (IllegalArgumentException ignored) {}
                }
            }
            for (String name : b.getStringList("upcoming")) {
                Material m = Material.matchMaterial(name);
                if (m != null) block.upcoming().addLast(m);
            }
        }

        ConfigurationSection g = y.getConfigurationSection("weekly-goal");
        if (g != null) {
            goal.setWindowStart(g.getLong("windowStart", goal.windowStart()));
            goal.setProgress(g.getLong("progress", 0));
            goal.setPayoutDone(g.getBoolean("payoutDone", false));
            for (int pct : g.getIntegerList("milestonesFired")) goal.milestonesFired().add(pct);
            ConfigurationSection contrib = g.getConfigurationSection("contributions");
            if (contrib != null) {
                for (String key : contrib.getKeys(false)) {
                    try { goal.contributionByPlayer().put(UUID.fromString(key), contrib.getLong(key)); }
                    catch (IllegalArgumentException ignored) {}
                }
            }
        }

        ConfigurationSection r = y.getConfigurationSection("raids");
        if (r != null) {
            raids.setLastRaidEndedAt(r.getLong("lastRaidEndedAt", 0));
        }
    }

    void save(CommunityBlock block, WeeklyGoal goal, RaidScheduler raids) {
        YamlConfiguration y = new YamlConfiguration();

        y.set("block.blocksBroken", block.blocksBroken());
        y.set("block.phaseIndex", block.phaseIndex());
        y.set("block.phaseProgress", block.phaseProgress());
        y.set("block.lastPayoutAt", block.lastPayoutAt());
        for (var e : block.contributionByPlayer().entrySet()) {
            y.set("block.contributions." + e.getKey(), e.getValue());
        }
        java.util.List<String> upcoming = new java.util.ArrayList<>();
        for (Material m : block.upcoming()) upcoming.add(m.name());
        y.set("block.upcoming", upcoming);

        y.set("weekly-goal.windowStart", goal.windowStart());
        y.set("weekly-goal.progress", goal.progress());
        y.set("weekly-goal.payoutDone", goal.payoutDone());
        y.set("weekly-goal.milestonesFired", new java.util.ArrayList<>(goal.milestonesFired()));
        for (var e : goal.contributionByPlayer().entrySet()) {
            y.set("weekly-goal.contributions." + e.getKey(), e.getValue());
        }

        y.set("raids.lastRaidEndedAt", raids.lastRaidEndedAt());

        try { atomicSave(y, file); }
        catch (IOException ex) { plugin.getLogger().warning("Failed to save hub.yml: " + ex.getMessage()); }
    }

    private static void atomicSave(YamlConfiguration y, File target) throws IOException {
        File tmp = new File(target.getParentFile(), target.getName() + ".tmp");
        y.save(tmp);
        Files.move(tmp.toPath(), target.toPath(),
                StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }
}
