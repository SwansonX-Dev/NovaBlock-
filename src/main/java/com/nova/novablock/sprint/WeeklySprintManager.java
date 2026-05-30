package com.nova.novablock.sprint;

import com.nova.novablock.NovaBlock;
import com.nova.novablock.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Two weekly sprint boards layered on top of existing progress — never touches
 * island data, only parallel counters that reset on Monday 00:00 server-local.
 *
 * <p><b>Hardcore</b> ranks islands by blocks broken this week.
 * <br><b>Casual</b> ranks players by daily quests completed this week (0–7),
 * tie-broken by the timestamp they first reached 7/7 so consistent players win
 * over Sunday-night binge clearers.
 *
 * <p>The Sunday 20:00 podium broadcast and the Monday 00:00 reset are checked
 * by a 1-minute ticker; the {@code lastPodiumWeekStart} field dedups the
 * broadcast across restarts.
 */
public class WeeklySprintManager {

    private static final int CASUAL_MAX = 7;
    /** Sunday 20:00 = day 6 + 20h after the weekStart marker. */
    private static final long PODIUM_OFFSET_MILLIS = (6L * 24 + 20) * 60 * 60 * 1000;
    private static final long WEEK_MILLIS = 7L * 24 * 60 * 60 * 1000;
    private static final long SAVE_DEBOUNCE_TICKS = 60L;
    private static final long TICK_PERIOD_TICKS = 20L * 60; // 1 min

    private final NovaBlock plugin;
    private final File file;
    private FileConfiguration data;

    private final ConcurrentMap<UUID, Long> hardcore = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, CasualEntry> casual = new ConcurrentHashMap<>();
    private final List<WinnerRow> lastWinners = new ArrayList<>();
    private long weekStart;
    private long lastPodiumWeekStart;

    private BukkitTask ticker;
    private final AtomicBoolean dirty = new AtomicBoolean(false);

    public WeeklySprintManager(NovaBlock plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "weekly_sprint.yml");
        load();
    }

    public void startTicker() {
        if (ticker != null) ticker.cancel();
        ticker = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, TICK_PERIOD_TICKS);
    }

    public void shutdown() {
        if (ticker != null) { ticker.cancel(); ticker = null; }
        saveNow();
    }

    // ---- Public API consumed by the listeners / GUI ----

    public void recordBlocksBroken(UUID islandUuid, long amount) {
        if (amount <= 0) return;
        rolloverIfNeeded();
        hardcore.merge(islandUuid, amount, Long::sum);
        scheduleSave();
    }

    public void recordQuestCompleted(UUID playerUuid) {
        rolloverIfNeeded();
        casual.compute(playerUuid, (id, existing) -> {
            CasualEntry e = existing == null ? new CasualEntry() : existing;
            if (e.quests >= CASUAL_MAX) return e;
            e.quests += 1;
            if (e.quests == CASUAL_MAX && e.firstSevenAt == 0L) e.firstSevenAt = System.currentTimeMillis();
            return e;
        });
        scheduleSave();
    }

    public long hardcoreScore(UUID islandUuid) {
        return hardcore.getOrDefault(islandUuid, 0L);
    }

    public int casualQuests(UUID playerUuid) {
        CasualEntry e = casual.get(playerUuid);
        return e == null ? 0 : e.quests;
    }

    public long casualFirstSevenAt(UUID playerUuid) {
        CasualEntry e = casual.get(playerUuid);
        return e == null ? 0L : e.firstSevenAt;
    }

    public long weekStart() { return weekStart; }
    public long weekEnd() { return weekStart + WEEK_MILLIS; }

    public List<HardcoreRow> topHardcore(int limit) {
        return hardcore.entrySet().stream()
                .filter(e -> e.getValue() > 0)
                .sorted(Comparator.<java.util.Map.Entry<UUID, Long>>comparingLong(java.util.Map.Entry::getValue).reversed())
                .limit(limit)
                .map(e -> new HardcoreRow(e.getKey(), e.getValue()))
                .toList();
    }

    public List<CasualRow> topCasual(int limit) {
        return casual.entrySet().stream()
                .filter(e -> e.getValue().quests > 0)
                .sorted(Comparator
                        .comparingInt((java.util.Map.Entry<UUID, CasualEntry> e) -> e.getValue().quests).reversed()
                        .thenComparingLong(e -> {
                            // Players who hit 7 first win ties; 0 (didn't hit 7) sorts to the bottom.
                            long t = e.getValue().firstSevenAt;
                            return t == 0L ? Long.MAX_VALUE : t;
                        }))
                .limit(limit)
                .map(e -> new CasualRow(e.getKey(), e.getValue().quests, e.getValue().firstSevenAt))
                .toList();
    }

    public int hardcoreRank(UUID islandUuid) {
        if (islandUuid == null || hardcoreScore(islandUuid) <= 0) return 0;
        List<HardcoreRow> rows = topHardcore(Integer.MAX_VALUE);
        for (int i = 0; i < rows.size(); i++) if (rows.get(i).islandUuid().equals(islandUuid)) return i + 1;
        return 0;
    }

    public int casualRank(UUID playerUuid) {
        if (playerUuid == null || casualQuests(playerUuid) <= 0) return 0;
        List<CasualRow> rows = topCasual(Integer.MAX_VALUE);
        for (int i = 0; i < rows.size(); i++) if (rows.get(i).playerUuid().equals(playerUuid)) return i + 1;
        return 0;
    }

    public List<Long> hardcoreCoinRewards() {
        return readCoinRewards(plugin.getConfig(), "sprint.hardcore.coin-rewards", List.of(100_000L, 50_000L, 25_000L));
    }

    public List<Long> casualCoinRewards() {
        return readCoinRewards(plugin.getConfig(), "sprint.casual.coin-rewards", List.of(25_000L, 10_000L, 5_000L));
    }

    public List<WinnerRow> lastWinners() { return List.copyOf(lastWinners); }

    public void addHardcoreScore(UUID islandUuid, long amount) {
        if (islandUuid == null || amount == 0) return;
        rolloverIfNeeded();
        hardcore.compute(islandUuid, (id, existing) -> Math.max(0L, (existing == null ? 0L : existing) + amount));
        if (hardcore.getOrDefault(islandUuid, 0L) == 0L) hardcore.remove(islandUuid);
        scheduleSave();
    }

    public void addCasualScore(UUID playerUuid, int amount) {
        if (playerUuid == null || amount == 0) return;
        rolloverIfNeeded();
        casual.compute(playerUuid, (id, existing) -> {
            CasualEntry entry = existing == null ? new CasualEntry() : existing;
            int before = entry.quests;
            entry.quests = Math.max(0, Math.min(CASUAL_MAX, entry.quests + amount));
            if (entry.quests == 0) return null;
            if (before < CASUAL_MAX && entry.quests == CASUAL_MAX && entry.firstSevenAt == 0L) {
                entry.firstSevenAt = System.currentTimeMillis();
            }
            if (entry.quests < CASUAL_MAX) entry.firstSevenAt = 0L;
            return entry;
        });
        scheduleSave();
    }

    public void resetCurrentWeek() {
        weekStart = mondayMidnightOf(System.currentTimeMillis());
        hardcore.clear();
        casual.clear();
        lastPodiumWeekStart = 0L;
        scheduleSave();
    }

    public void broadcastPodiumNow(boolean reward) {
        broadcastPodium(reward, true);
        if (reward) {
            lastPodiumWeekStart = weekStart;
            scheduleSave();
        }
    }

    // ---- Internals ----

    private void tick() {
        rolloverIfNeeded();
        long now = System.currentTimeMillis();
        if (now >= weekStart + PODIUM_OFFSET_MILLIS && lastPodiumWeekStart != weekStart) {
            broadcastPodium(true, true);
            lastPodiumWeekStart = weekStart;
            scheduleSave();
        }
    }

    /**
     * Advance {@link #weekStart} forward to whatever Monday-00:00 the current
     * server clock is in, clearing counters once on the first rollover. If the
     * server was down for multiple weeks we skip past them silently — no point
     * broadcasting podiums for a week with no players online to win them.
     */
    private synchronized void rolloverIfNeeded() {
        long currentWeek = mondayMidnightOf(System.currentTimeMillis());
        if (currentWeek <= weekStart) return;
        weekStart = currentWeek;
        hardcore.clear();
        casual.clear();
        scheduleSave();
    }

    private void broadcastPodium(boolean reward, boolean rememberWinners) {
        List<HardcoreRow> hcTop = topHardcore(3);
        List<CasualRow> caTop = topCasual(3);
        if (hcTop.isEmpty() && caTop.isEmpty()) return;

        List<Long> hcCoins = hardcoreCoinRewards();
        List<Long> caCoins = casualCoinRewards();
        List<WinnerRow> winners = new ArrayList<>();

        Bukkit.broadcast(Msg.mm("<gradient:#7B61FF:#4FC3F7><bold>Weekly Sprint Podium"));
        if (!hcTop.isEmpty()) {
            Bukkit.broadcast(Msg.mm("<red>Hardcore <gray>(blocks broken)"));
            for (int i = 0; i < hcTop.size(); i++) {
                HardcoreRow row = hcTop.get(i);
                var owner = ownerOfIsland(row.islandUuid());
                String name = owner == null ? "Unknown" : (owner.getName() == null ? "Unknown" : owner.getName());
                long coins = at(hcCoins, i);
                Bukkit.broadcast(Msg.mm("  " + medal(i) + " <yellow>" + name
                        + " <gray>– <white>" + row.blocks() + " blocks"
                        + (coins > 0 ? " <dark_gray>(<gold>+" + coins + "<dark_gray>)" : "")));
                winners.add(new WinnerRow("hardcore", i + 1, name, row.blocks(), coins));
                if (reward) dispatchHardcoreReward(row.islandUuid(), name, i + 1, coins);
            }
        }
        if (!caTop.isEmpty()) {
            Bukkit.broadcast(Msg.mm("<aqua>Casual <gray>(daily quests)"));
            for (int i = 0; i < caTop.size(); i++) {
                CasualRow row = caTop.get(i);
                OfflinePlayer op = Bukkit.getOfflinePlayer(row.playerUuid());
                String name = op.getName() == null ? "Unknown" : op.getName();
                long coins = at(caCoins, i);
                Bukkit.broadcast(Msg.mm("  " + medal(i) + " <yellow>" + name
                        + " <gray>– <white>" + row.quests() + "/7"
                        + (coins > 0 ? " <dark_gray>(<gold>+" + coins + "<dark_gray>)" : "")));
                winners.add(new WinnerRow("casual", i + 1, name, row.quests(), coins));
                if (reward) dispatchCasualReward(row.playerUuid(), name, i + 1, coins);
            }
        }
        if (rememberWinners) {
            lastWinners.clear();
            lastWinners.addAll(winners);
            scheduleSave();
        }
    }

    /**
     * Coins land on the island (xEconomy splits among online members per its
     * existing rules), and {@code sprint.hardcore.reward-commands-place-N} are
     * dispatched from console with {@code %owner%} and {@code %place%}
     * placeholders so server owners can stack extra perks (pets, ranks, etc.).
     */
    private void dispatchHardcoreReward(UUID islandUuid, String ownerName, int place, long coins) {
        var island = plugin.islands().all().get(islandUuid);
        if (island != null && coins > 0) plugin.economy().award(island, coins);

        if (island != null) {
            var ownerPlayer = Bukkit.getPlayer(island.data().getOwner());
            if (ownerPlayer != null) {
                Msg.title(ownerPlayer,
                        "<gradient:#FF6B6B:#FFC940><bold>Hardcore #" + place + "!",
                        coins > 0 ? "<yellow>+" + coins + " coins to your island" : "<yellow>Podium claimed");
            }
        }

        for (String raw : plugin.getConfig().getStringList("sprint.hardcore.reward-commands-place-" + place)) {
            runCommand(raw.replace("%owner%", ownerName).replace("%place%", String.valueOf(place)));
        }
    }

    /**
     * Casual rewards go to the individual player (their island for coins,
     * placeholders {@code %player%} / {@code %place%} for commands).
     */
    private void dispatchCasualReward(UUID playerUuid, String playerName, int place, long coins) {
        var island = plugin.islands().ofPlayer(playerUuid);
        if (island != null && coins > 0) plugin.economy().award(island, coins);

        var online = Bukkit.getPlayer(playerUuid);
        if (online != null) {
            Msg.title(online,
                    "<gradient:#7B61FF:#4FC3F7><bold>Casual #" + place + "!",
                    coins > 0 ? "<yellow>+" + coins + " coins" : "<yellow>Podium claimed");
        }

        for (String raw : plugin.getConfig().getStringList("sprint.casual.reward-commands-place-" + place)) {
            runCommand(raw.replace("%player%", playerName).replace("%place%", String.valueOf(place)));
        }
    }

    private void runCommand(String command) {
        try { Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command); }
        catch (Throwable t) { plugin.getLogger().warning("Sprint reward command failed: " + command); }
    }

    private static List<Long> readCoinRewards(org.bukkit.configuration.file.FileConfiguration cfg,
                                              String path, List<Long> fallback) {
        if (!cfg.isList(path)) return fallback;
        List<Long> out = new ArrayList<>(3);
        for (Object raw : cfg.getList(path)) {
            if (raw instanceof Number n) out.add(n.longValue());
        }
        return out.isEmpty() ? fallback : out;
    }

    private static long at(List<Long> list, int index) {
        return index < list.size() ? Math.max(0L, list.get(index)) : 0L;
    }

    private OfflinePlayer ownerOfIsland(UUID islandUuid) {
        var island = plugin.islands().all().get(islandUuid);
        if (island == null) return null;
        return Bukkit.getOfflinePlayer(island.data().getOwner());
    }

    private static String medal(int index) {
        return switch (index) {
            case 0 -> "<gold>1.";
            case 1 -> "<white>2.";
            case 2 -> "<#CD7F32>3.";
            default -> "<gray>" + (index + 1) + ".";
        };
    }

    private static long mondayMidnightOf(long epochMillis) {
        ZonedDateTime z = Instant.ofEpochMilli(epochMillis)
                .atZone(ZoneId.systemDefault())
                .withHour(0).withMinute(0).withSecond(0).withNano(0)
                .with(DayOfWeek.MONDAY);
        return z.toInstant().toEpochMilli();
    }

    // ---- Persistence ----

    private void load() {
        if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
        if (!file.exists()) {
            try { file.createNewFile(); }
            catch (IOException e) { plugin.getLogger().severe("Could not create weekly_sprint.yml: " + e.getMessage()); }
        }
        data = YamlConfiguration.loadConfiguration(file);
        weekStart = data.getLong("week-start", 0L);
        lastPodiumWeekStart = data.getLong("last-podium-week", 0L);
        if (weekStart == 0L) weekStart = mondayMidnightOf(System.currentTimeMillis());

        hardcore.clear();
        ConfigurationSection hc = data.getConfigurationSection("hardcore");
        if (hc != null) {
            for (String key : hc.getKeys(false)) {
                UUID uuid; try { uuid = UUID.fromString(key); } catch (IllegalArgumentException e) { continue; }
                long blocks = hc.getLong(key);
                if (blocks > 0) hardcore.put(uuid, blocks);
            }
        }
        casual.clear();
        ConfigurationSection ca = data.getConfigurationSection("casual");
        if (ca != null) {
            for (String key : ca.getKeys(false)) {
                UUID uuid; try { uuid = UUID.fromString(key); } catch (IllegalArgumentException e) { continue; }
                CasualEntry entry = new CasualEntry();
                entry.quests = ca.getInt(key + ".quests", 0);
                entry.firstSevenAt = ca.getLong(key + ".first-seven-at", 0L);
                if (entry.quests > 0) casual.put(uuid, entry);
            }
        }
        lastWinners.clear();
        ConfigurationSection winners = data.getConfigurationSection("last-winners");
        if (winners != null) {
            for (String key : winners.getKeys(false)) {
                ConfigurationSection sec = winners.getConfigurationSection(key);
                if (sec == null) continue;
                lastWinners.add(new WinnerRow(
                        sec.getString("board", "unknown"),
                        sec.getInt("place", 0),
                        sec.getString("name", "Unknown"),
                        sec.getLong("score", 0L),
                        sec.getLong("coins", 0L)));
            }
            lastWinners.sort(Comparator.comparingInt((WinnerRow row) -> boardOrder(row.board())).thenComparingInt(WinnerRow::place));
        }
        rolloverIfNeeded();
    }

    private void scheduleSave() {
        if (!dirty.compareAndSet(false, true)) return;
        Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, this::flush, SAVE_DEBOUNCE_TICKS);
    }

    private synchronized void flush() {
        if (!dirty.compareAndSet(true, false)) return;
        writeSnapshot();
        try { data.save(file); }
        catch (IOException e) { plugin.getLogger().severe("Could not save weekly_sprint.yml: " + e.getMessage()); }
    }

    public synchronized void saveNow() {
        if (data == null) return;
        dirty.set(false);
        writeSnapshot();
        try { data.save(file); }
        catch (IOException e) { plugin.getLogger().severe("Could not save weekly_sprint.yml: " + e.getMessage()); }
    }

    private void writeSnapshot() {
        data.set("week-start", weekStart);
        data.set("last-podium-week", lastPodiumWeekStart);
        data.set("hardcore", null);
        ConfigurationSection hc = data.createSection("hardcore");
        for (var e : hardcore.entrySet()) hc.set(e.getKey().toString(), e.getValue());
        data.set("casual", null);
        ConfigurationSection ca = data.createSection("casual");
        for (var e : casual.entrySet()) {
            ca.set(e.getKey() + ".quests", e.getValue().quests);
            ca.set(e.getKey() + ".first-seven-at", e.getValue().firstSevenAt);
        }
        data.set("last-winners", null);
        ConfigurationSection winners = data.createSection("last-winners");
        for (int i = 0; i < lastWinners.size(); i++) {
            WinnerRow row = lastWinners.get(i);
            String path = String.valueOf(i);
            winners.set(path + ".board", row.board());
            winners.set(path + ".place", row.place());
            winners.set(path + ".name", row.name());
            winners.set(path + ".score", row.score());
            winners.set(path + ".coins", row.coins());
        }
    }

    // ---- Records ----

    public record HardcoreRow(UUID islandUuid, long blocks) {}
    public record CasualRow(UUID playerUuid, int quests, long firstSevenAt) {}
    public record WinnerRow(String board, int place, String name, long score, long coins) {}

    private static final class CasualEntry {
        int quests;
        long firstSevenAt;
    }

    private static int boardOrder(String board) {
        return "casual".equalsIgnoreCase(board) ? 1 : 0;
    }
}
