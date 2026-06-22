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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class YamlStorage implements DataStorage {

    /** Bump when on-disk format changes — old files are read with backward-compat code. */
    public static final int SCHEMA_VERSION = 1;

    private final NovaBlock plugin;
    private File islandDir;
    private File playerDir;

    /**
     * Single background thread that performs island file I/O (the atomic write +
     * move). Island YAML is built on the main thread — a cheap in-memory snapshot —
     * then handed here so the disk write never stalls a tick. A single thread keeps
     * writes for the same island strictly ordered, so an autosave can't clobber a
     * later immediate save. Drained on {@link #shutdown()}.
     */
    private ExecutorService ioExecutor;

    public YamlStorage(NovaBlock plugin) {
        this.plugin = plugin;
    }

    @Override
    public void init() {
        islandDir = new File(plugin.getDataFolder(), "islands");
        playerDir = new File(plugin.getDataFolder(), "players");
        if (!islandDir.exists()) islandDir.mkdirs();
        if (!playerDir.exists()) playerDir.mkdirs();
        ioExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "NovaBlock-IslandIO");
            t.setDaemon(true);
            return t;
        });
    }

    /** Atomic YAML save: write to .tmp then move into place so a crash can't truncate the real file. */
    private void atomicSave(YamlConfiguration y, File target) throws IOException {
        File tmp = new File(target.getParentFile(), target.getName() + ".tmp");
        y.save(tmp);
        Files.move(tmp.toPath(), target.toPath(),
                StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    @Override
    public void shutdown() {
        // Drain queued island writes so nothing is lost when the server stops.
        if (ioExecutor != null) {
            ioExecutor.shutdown();
            try {
                if (!ioExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                    plugin.getLogger().warning("Island IO did not finish within 30s during shutdown.");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            ioExecutor = null;
        }
    }

    /** Submit an island file write to the IO thread, or run it inline if the executor is gone (e.g. mid-shutdown). */
    private void submitIslandWrite(YamlConfiguration y, File f, UUID id) {
        Runnable write = () -> {
            try { atomicSave(y, f); }
            catch (IOException ex) { plugin.getLogger().warning("Failed to save island " + id + ": " + ex.getMessage()); }
        };
        ExecutorService ex = ioExecutor;
        if (ex == null || ex.isShutdown()) write.run();
        else ex.execute(write);
    }

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
                String worldName = y.getString("world", plugin.worlds().worldName());
                int slotX = y.getInt("slot.x");
                int slotZ = y.getInt("slot.z");
                long blocksBroken = y.getLong("blocksBroken", 0);
                int phaseIndex = y.getInt("phaseIndex", 0);
                int phaseProgress = y.getInt("phaseProgress", 0);
                int prestigeLevel = y.getInt("prestigeLevel", 0);
                int level = y.getInt("level", 1);
                long lastBossAt = y.getLong("lastBossAt", 0);
                long lastLootRoomAt = y.getLong("lastLootRoomAt", 0);
                long netherBlocksBroken = y.getLong("nether.blocksBroken", 0);
                int netherPhaseIndex = y.getInt("nether.phaseIndex", 0);
                int netherPhaseProgress = y.getInt("nether.phaseProgress", 0);
                long netherLastBossAt = y.getLong("nether.lastBossAt", 0);
                long netherLastLootRoomAt = y.getLong("nether.lastLootRoomAt", 0);
                boolean netherUnlocked = y.getBoolean("nether.unlocked", false);
                boolean firstNetherVisit = y.getBoolean("nether.firstVisit", true);
                List<String> members = y.getStringList("members");
                List<UUID> memberIds = new ArrayList<>();
                for (String m : members) memberIds.add(UUID.fromString(m));

                IslandData data = new IslandData(id, owner, worldName, slotX, slotZ);
                data.setBlocksBroken(blocksBroken);
                data.setPhaseIndex(phaseIndex);
                data.setPhaseProgress(phaseProgress);
                data.setPrestigeLevel(prestigeLevel);
                data.setLevel(level);
                data.setLastBossAt(lastBossAt);
                data.setLastLootRoomAt(lastLootRoomAt);
                data.setNetherBlocksBroken(netherBlocksBroken);
                data.setNetherPhaseIndex(netherPhaseIndex);
                data.setNetherPhaseProgress(netherPhaseProgress);
                data.setNetherLastBossAt(netherLastBossAt);
                data.setNetherLastLootRoomAt(netherLastLootRoomAt);
                data.setNetherUnlocked(netherUnlocked);
                data.setFirstNetherVisit(firstNetherVisit);
                data.getMembers().addAll(memberIds);
                for (String t : y.getStringList("trusted")) {
                    try { data.getTrusted().add(UUID.fromString(t)); }
                    catch (IllegalArgumentException ignored) { /* skip malformed UUID */ }
                }
                data.setBankBalance(y.getLong("bankBalance", 0));
                ConfigurationSection rolesSec = y.getConfigurationSection("roles");
                if (rolesSec != null) {
                    for (String key : rolesSec.getKeys(false)) {
                        try {
                            UUID memberId = UUID.fromString(key);
                            data.setRole(memberId,
                                    com.nova.novablock.island.IslandRole.byKey(rolesSec.getString(key)));
                        } catch (IllegalArgumentException ignored) {
                            // Malformed UUID key — skip rather than fail the whole island load.
                        }
                    }
                }
                ConfigurationSection flagsSec = y.getConfigurationSection("flags");
                if (flagsSec != null) {
                    for (String key : flagsSec.getKeys(false)) {
                        com.nova.novablock.island.IslandFlag flag = com.nova.novablock.island.IslandFlag.byKey(key);
                        if (flag != null) data.setFlag(flag, flagsSec.getBoolean(key));
                    }
                }
                ConfigurationSection upgradesSec = y.getConfigurationSection("upgrades");
                if (upgradesSec != null) {
                    for (String key : upgradesSec.getKeys(false)) {
                        com.nova.novablock.island.IslandUpgrade up = com.nova.novablock.island.IslandUpgrade.byKey(key);
                        if (up != null) data.setUpgradeLevel(up, upgradesSec.getInt(key));
                    }
                }
                data.setStorageBase64(y.getString("storage", ""));
                data.setQuestlineStage(y.getInt("questline.stage", 1));
                data.setQuestlineProgress(y.getInt("questline.progress", 0));
                data.getReceivedPrestigeTemplates().addAll(y.getStringList("prestige.receivedTemplates"));
                result.add(data);
            } catch (Exception ex) {
                plugin.getLogger().warning("Failed to load island " + f.getName() + ": " + ex.getMessage());
            }
        }
        return result;
    }

    @Override
    public void saveIsland(IslandData data) {
        // Build the YAML snapshot on the calling (main) thread so it reflects a
        // consistent view of the live data, then hand the disk write to the IO
        // thread. Clearing dirty here — at snapshot time — means any mutation that
        // lands afterwards re-marks the island for the next autosave.
        File f = new File(islandDir, data.getId() + ".yml");
        YamlConfiguration y = buildIslandYaml(data);
        data.clearDirty();
        submitIslandWrite(y, f, data.getId());
    }

    private YamlConfiguration buildIslandYaml(IslandData data) {
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
        y.set("prestigeLevel", data.getPrestigeLevel());
        y.set("level", data.getLevel());
        y.set("lastBossAt", data.getLastBossAt());
        y.set("lastLootRoomAt", data.getLastLootRoomAt());
        y.set("nether.blocksBroken", data.getNetherBlocksBroken());
        y.set("nether.phaseIndex", data.getNetherPhaseIndex());
        y.set("nether.phaseProgress", data.getNetherPhaseProgress());
        y.set("nether.lastBossAt", data.getNetherLastBossAt());
        y.set("nether.lastLootRoomAt", data.getNetherLastLootRoomAt());
        y.set("nether.unlocked", data.isNetherUnlocked());
        y.set("nether.firstVisit", data.isFirstNetherVisit());
        List<String> mem = new ArrayList<>();
        for (UUID u : data.getMembers()) mem.add(u.toString());
        y.set("members", mem);
        if (!data.getTrusted().isEmpty()) {
            List<String> tr = new ArrayList<>();
            for (UUID u : data.getTrusted()) tr.add(u.toString());
            y.set("trusted", tr);
        }
        if (data.getBankBalance() > 0) y.set("bankBalance", data.getBankBalance());
        // Only non-default (non-MEMBER) roles are persisted; owner role is implicit.
        for (var e : data.getRoles().entrySet()) {
            if (e.getValue() != null && e.getValue() != com.nova.novablock.island.IslandRole.MEMBER) {
                y.set("roles." + e.getKey(), e.getValue().name());
            }
        }
        for (var e : data.getFlags().entrySet()) {
            y.set("flags." + e.getKey().storageKey(), e.getValue());
        }
        for (var e : data.getUpgrades().entrySet()) {
            if (e.getValue() != null && e.getValue() > 0) {
                y.set("upgrades." + e.getKey().storageKey(), e.getValue());
            }
        }
        if (data.getStorageBase64() != null && !data.getStorageBase64().isEmpty()) {
            y.set("storage", data.getStorageBase64());
        }
        y.set("questline.stage", data.getQuestlineStage());
        if (data.getQuestlineProgress() > 0) y.set("questline.progress", data.getQuestlineProgress());
        if (!data.getReceivedPrestigeTemplates().isEmpty()) {
            y.set("prestige.receivedTemplates",
                    data.getReceivedPrestigeTemplates().stream().sorted().toList());
        }
        return y;
    }

    @Override
    public void deleteIsland(UUID islandId) {
        // Route deletes through the same IO thread so they stay ordered against any
        // queued writes for the same island (a save then delete deletes last).
        File f = new File(islandDir, islandId + ".yml");
        Runnable del = () -> { if (f.exists()) f.delete(); };
        ExecutorService ex = ioExecutor;
        if (ex == null || ex.isShutdown()) del.run();
        else ex.execute(del);
    }

    @Override
    public PlayerProgression loadProgression(UUID playerId) {
        File f = new File(playerDir, playerId + ".yml");
        PlayerProgression p = new PlayerProgression(playerId);
        boolean backpackDefault = plugin.getConfig().getBoolean("backpack.default-auto-grab", false);
        p.setBackpackItemEnabled(backpackDefault);
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
        ConfigurationSection questProgress = y.getConfigurationSection("quest.progress");
        if (questProgress != null) {
            java.util.Map<String, Integer> m = new java.util.HashMap<>();
            for (String key : questProgress.getKeys(false)) m.put(key, questProgress.getInt(key));
            p.setQuestProgressMap(m);
        }
        p.setQuestDayStamp(y.getLong("quest.day", 0));
        p.setLastRerollDay(y.getLong("prophecy.lastRerollDay", 0));
        p.setLastLoginDay(y.getLong("login.lastDay", 0));
        p.setLoginStreak(y.getInt("login.streak", 0));
        p.setMenuItemEnabled(y.getBoolean("ui.menuItem", true));
        p.setScoreboardEnabled(y.getBoolean("ui.scoreboard", true));
        p.setAutoSellEnabled(y.getBoolean("ui.autoSell", false));
        p.setBackpackItemEnabled(y.getBoolean("ui.backpackItem", backpackDefault));
        p.setBackpackBase64(y.getString("backpack.data", ""));
        String dcWorld = y.getString("community.depositChest.world", "");
        if (dcWorld != null && !dcWorld.isEmpty()) {
            p.setDepositChest(dcWorld,
                    y.getInt("community.depositChest.x", 0),
                    y.getInt("community.depositChest.y", 0),
                    y.getInt("community.depositChest.z", 0));
        }
        p.setAtlasScore(y.getInt("atlas.score", 0));
        p.setSeasonalPathKey(y.getString("seasonal.pathKey", ""));
        p.setSeasonalPathPoints(y.getInt("seasonal.points", 0));
        p.setClaimedSeasonalTiers(new java.util.HashSet<>(y.getIntegerList("seasonal.claimedTiers")));
        p.setPendingRewardCommands(new java.util.HashSet<>(y.getStringList("seasonal.pendingCommands")));
        p.setClaimRewardBreaks(y.getLong("claimBlocks.oneBlockBreaks", 0));
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
        y.set("quest.day", p.getQuestDayStamp());
        for (var e : p.getQuestProgressMap().entrySet()) {
            if (e.getValue() != null && e.getValue() > 0) {
                y.set("quest.progress." + e.getKey(), e.getValue());
            }
        }
        y.set("prophecy.lastRerollDay", p.getLastRerollDay());
        y.set("login.lastDay", p.getLastLoginDay());
        y.set("login.streak", p.getLoginStreak());
        y.set("ui.menuItem", p.isMenuItemEnabled());
        y.set("ui.scoreboard", p.isScoreboardEnabled());
        y.set("ui.autoSell", p.isAutoSellEnabled());
        y.set("ui.backpackItem", p.isBackpackItemEnabled());
        if (!p.getBackpackBase64().isEmpty()) y.set("backpack.data", p.getBackpackBase64());
        if (p.hasDepositChest()) {
            y.set("community.depositChest.world", p.getDepositChestWorld());
            y.set("community.depositChest.x", p.getDepositChestX());
            y.set("community.depositChest.y", p.getDepositChestY());
            y.set("community.depositChest.z", p.getDepositChestZ());
        }
        y.set("atlas.score", p.getAtlasScore());
        y.set("seasonal.pathKey", p.getSeasonalPathKey());
        y.set("seasonal.points", p.getSeasonalPathPoints());
        y.set("seasonal.claimedTiers", p.getClaimedSeasonalTiers().stream().sorted().toList());
        y.set("seasonal.pendingCommands", p.getPendingRewardCommands().stream().sorted().toList());
        y.set("claimBlocks.oneBlockBreaks", p.getClaimRewardBreaks());
        try { atomicSave(y, f); }
        catch (IOException ex) { plugin.getLogger().warning("Failed to save progression: " + ex.getMessage()); }
    }

    @Override
    public void deleteProgression(UUID playerId) {
        File f = new File(playerDir, playerId + ".yml");
        if (f.exists()) f.delete();
    }

    public static Location parseLoc(YamlConfiguration y, String path) {
        if (!y.contains(path)) return null;
        String world = y.getString(path + ".world");
        return new Location(Bukkit.getWorld(world),
                y.getDouble(path + ".x"), y.getDouble(path + ".y"), y.getDouble(path + ".z"),
                (float) y.getDouble(path + ".yaw"), (float) y.getDouble(path + ".pitch"));
    }
}
