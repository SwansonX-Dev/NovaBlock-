package com.nova.novablock.storage;

import com.nova.novablock.NovaBlock;
import com.nova.novablock.island.IslandData;
import com.nova.novablock.progression.PlayerProgression;
import com.nova.novablock.progression.SkillType;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class YamlStorage implements DataStorage {

    /** Bump when on-disk format changes — old files are read with backward-compat code. */
    public static final int SCHEMA_VERSION = 1;

    private final NovaBlock plugin;
    private File islandDir;
    private File playerDir;

    public YamlStorage(NovaBlock plugin) {
        this.plugin = plugin;
    }

    @Override
    public void init() {
        islandDir = new File(plugin.getDataFolder(), "islands");
        playerDir = new File(plugin.getDataFolder(), "players");
        if (!islandDir.exists()) islandDir.mkdirs();
        if (!playerDir.exists()) playerDir.mkdirs();
    }

    /** Atomic YAML save: write to .tmp then move into place so a crash can't truncate the real file. */
    private void atomicSave(YamlConfiguration y, File target) throws IOException {
        File tmp = new File(target.getParentFile(), target.getName() + ".tmp");
        y.save(tmp);
        Files.move(tmp.toPath(), target.toPath(),
                StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    @Override
    public void shutdown() {}

    @Override
    public Collection<IslandData> loadAllIslands() {
        List<IslandData> result = new ArrayList<>();
        File[] files = islandDir.listFiles((d, n) -> n.endsWith(".yml"));
        if (files == null) return result;
        for (File f : files) {
            try {
                YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
                String idStr = y.getString("id");
                String ownerStr = y.getString("owner");
                if (idStr == null || ownerStr == null) {
                    plugin.getLogger().warning("Island file " + f.getName() + " missing id/owner; skipping.");
                    continue;
                }
                UUID id = UUID.fromString(idStr);
                UUID owner = UUID.fromString(ownerStr);
                String worldName = y.getString("world", com.nova.novablock.island.IslandWorldManager.WORLD_NAME);
                int slotX = y.getInt("slot.x");
                int slotZ = y.getInt("slot.z");
                long blocksBroken = y.getLong("blocksBroken", 0);
                int phaseIndex = y.getInt("phaseIndex", 0);
                int phaseProgress = y.getInt("phaseProgress", 0);
                int level = y.getInt("level", 1);
                long lastBossAt = y.getLong("lastBossAt", 0);
                long lastLootRoomAt = y.getLong("lastLootRoomAt", 0);
                List<String> members = y.getStringList("members");
                List<UUID> memberIds = new ArrayList<>();
                for (String m : members) memberIds.add(UUID.fromString(m));

                IslandData data = new IslandData(id, owner, worldName, slotX, slotZ);
                data.setBlocksBroken(blocksBroken);
                data.setPhaseIndex(phaseIndex);
                data.setPhaseProgress(phaseProgress);
                data.setLevel(level);
                data.setLastBossAt(lastBossAt);
                data.setLastLootRoomAt(lastLootRoomAt);
                data.getMembers().addAll(memberIds);
                result.add(data);
            } catch (Exception ex) {
                plugin.getLogger().warning("Failed to load island " + f.getName() + ": " + ex.getMessage());
            }
        }
        return result;
    }

    @Override
    public void saveIsland(IslandData data) {
        File f = new File(islandDir, data.getId() + ".yml");
        YamlConfiguration y = new YamlConfiguration();
        y.set("schemaVersion", SCHEMA_VERSION);
        y.set("id", data.getId().toString());
        y.set("owner", data.getOwner().toString());
        y.set("world", data.getWorldName());
        y.set("slot.x", data.getSlotX());
        y.set("slot.z", data.getSlotZ());
        y.set("blocksBroken", data.getBlocksBroken());
        y.set("phaseIndex", data.getPhaseIndex());
        y.set("phaseProgress", data.getPhaseProgress());
        y.set("level", data.getLevel());
        y.set("lastBossAt", data.getLastBossAt());
        y.set("lastLootRoomAt", data.getLastLootRoomAt());
        List<String> mem = new ArrayList<>();
        for (UUID u : data.getMembers()) mem.add(u.toString());
        y.set("members", mem);
        try { atomicSave(y, f); }
        catch (IOException ex) { plugin.getLogger().warning("Failed to save island " + data.getId() + ": " + ex.getMessage()); }
    }

    @Override
    public void deleteIsland(UUID islandId) {
        File f = new File(islandDir, islandId + ".yml");
        if (f.exists()) f.delete();
    }

    @Override
    public PlayerProgression loadProgression(UUID playerId) {
        File f = new File(playerDir, playerId + ".yml");
        PlayerProgression p = new PlayerProgression(playerId);
        if (!f.exists()) return p;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
        ConfigurationSection skills = y.getConfigurationSection("skills");
        if (skills != null) {
            for (String key : skills.getKeys(false)) {
                try {
                    SkillType type = SkillType.valueOf(key.toUpperCase());
                    p.setXp(type, skills.getLong(key + ".xp"));
                    p.setLevel(type, skills.getInt(key + ".level"));
                } catch (IllegalArgumentException ignored) {}
            }
        }
        p.setQuestProgress(y.getInt("quest.progress", 0));
        p.setQuestDayStamp(y.getLong("quest.day", 0));
        p.setMenuItemEnabled(y.getBoolean("ui.menuItem", true));
        p.setScoreboardEnabled(y.getBoolean("ui.scoreboard", true));
        return p;
    }

    @Override
    public void saveProgression(PlayerProgression p) {
        File f = new File(playerDir, p.getPlayerId() + ".yml");
        YamlConfiguration y = new YamlConfiguration();
        y.set("schemaVersion", SCHEMA_VERSION);
        for (SkillType type : SkillType.values()) {
            String key = "skills." + type.name().toLowerCase();
            y.set(key + ".xp", p.getXp(type));
            y.set(key + ".level", p.getLevel(type));
        }
        y.set("quest.progress", p.getQuestProgress());
        y.set("quest.day", p.getQuestDayStamp());
        y.set("ui.menuItem", p.isMenuItemEnabled());
        y.set("ui.scoreboard", p.isScoreboardEnabled());
        try { atomicSave(y, f); }
        catch (IOException ex) { plugin.getLogger().warning("Failed to save progression: " + ex.getMessage()); }
    }

    public static Location parseLoc(YamlConfiguration y, String path) {
        if (!y.contains(path)) return null;
        String world = y.getString(path + ".world");
        return new Location(Bukkit.getWorld(world),
                y.getDouble(path + ".x"), y.getDouble(path + ".y"), y.getDouble(path + ".z"),
                (float) y.getDouble(path + ".yaw"), (float) y.getDouble(path + ".pitch"));
    }
}
