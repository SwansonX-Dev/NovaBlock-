package com.nova.novablock.scoreboard;

import com.nova.novablock.NovaBlock;
import com.nova.novablock.island.Island;
import com.nova.novablock.phase.Phase;
import com.nova.novablock.progression.PlayerProgression;
import com.nova.novablock.progression.SkillType;
import com.nova.novablock.util.Msg;
import io.papermc.paper.scoreboard.numbers.NumberFormat;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Sidebar scoreboard, rendered via the classic team-prefix trick so it translates
 * cleanly to Bedrock clients via Geyser. Lines are kept short to fit narrow mobile UIs.
 */
public class ScoreboardManager {

    private final NovaBlock plugin;
    private final Map<UUID, Scoreboard> boards = new HashMap<>();
    private BukkitTask ticker;

    /** Scoreboard sidebars are capped at 15 score lines by the vanilla client. */
    private static final int MAX_LINES = 15;

    // Config-driven overrides, cached and refreshed by reload(). Empty = use the
    // built-in dynamic board, so an unconfigured server is unchanged.
    private String customTitle = "";
    private String customCommunityTitle = "";
    private List<String> customLines = List.of();
    private List<String> customCommunityLines = List.of();
    private boolean papiPresent;

    public ScoreboardManager(NovaBlock plugin) { this.plugin = plugin; }

    public void startTicker() {
        reload();
        if (ticker != null) ticker.cancel();
        ticker = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    /** Re-read the configurable scoreboard title/lines from config. Called on /obadmin reload. */
    public void reload() {
        var cfg = plugin.getConfig();
        customTitle = cfg.getString("scoreboard.title", "");
        customCommunityTitle = cfg.getString("scoreboard.community-title", "");
        customLines = cfg.getStringList("scoreboard.lines");
        customCommunityLines = cfg.getStringList("scoreboard.community-lines");
        papiPresent = Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI");
    }

    /** Resolve PlaceholderAPI placeholders (if installed) before MiniMessage parsing. */
    private String resolve(Player p, String text) {
        if (text == null) return "";
        if (papiPresent) {
            try {
                text = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(p, text);
            } catch (Throwable ignored) {
                // A misbehaving expansion shouldn't blank the whole board.
            }
        }
        return text;
    }

    /**
     * The lines to render for this player: a configured override (placeholders
     * resolved, fully-empty lines dropped so they can act as conditionals) when
     * set, otherwise the built-in dynamic board.
     */
    private List<String> resolveLines(Player p, boolean inCommunity) {
        List<String> custom = inCommunity ? customCommunityLines : customLines;
        if (custom == null || custom.isEmpty()) {
            return inCommunity ? buildCommunityLines(p) : buildLines(p);
        }
        List<String> out = new ArrayList<>();
        for (String raw : custom) {
            String resolved = resolve(p, raw);
            if (resolved.isEmpty()) continue; // empty after resolve = hide (conditional line)
            out.add(resolved);
            if (out.size() >= MAX_LINES) break;
        }
        return out;
    }

    private void tick() {
        for (Player p : Bukkit.getOnlinePlayers()) update(p);
    }

    /** Toggle visibility; persisted per-player. */
    public void toggle(Player p) {
        var prog = plugin.progression().get(p);
        boolean enabled = !prog.isScoreboardEnabled();
        prog.setScoreboardEnabled(enabled);
        plugin.progression().save(p.getUniqueId());
        if (enabled) update(p);
        else clear(p);
        com.nova.novablock.util.Msg.actionBar(p, enabled
                ? "<green>Scoreboard shown."
                : "<gray>Scoreboard hidden. <yellow>/sb<gray> to toggle.");
    }

    public void update(Player p) {
        if (!plugin.progression().get(p).isScoreboardEnabled()) {
            clear(p);
            return;
        }
        // Yield to OG OneBlock's own scoreboard whenever the player is in its world.
        var ogPlugin = Bukkit.getPluginManager().getPlugin("OGOneBlock");
        if (ogPlugin != null && ogPlugin.isEnabled()) {
            String ogWorld = ogPlugin.getConfig().getString("world.name", "OGOBworld");
            if (p.getWorld().getName().equals(ogWorld)) {
                if (boards.containsKey(p.getUniqueId())) clear(p);
                return;
            }
        }
        // In the shared community OneBlock world, render a community-themed board
        // (shared phase/pool/weekly goal) instead of the player's personal-island stats.
        boolean inCommunity = plugin.community() != null
                && plugin.community().isEnabled()
                && p.getWorld().getName().equals(plugin.community().communityWorldName());

        Scoreboard board = boards.computeIfAbsent(p.getUniqueId(),
                u -> Bukkit.getScoreboardManager().getNewScoreboard());
        Objective obj = board.getObjective("nb");
        if (obj != null) obj.unregister();
        String titleOverride = inCommunity ? customCommunityTitle : customTitle;
        var title = (titleOverride != null && !titleOverride.isBlank())
                ? Msg.mm(resolve(p, titleOverride))
                : Msg.mm(inCommunity
                        ? "<gradient:#FFB347:#FFD700><bold>Community"
                        : "<gradient:#7B61FF:#4FC3F7><bold>NovaBlock");
        obj = board.registerNewObjective("nb", Criteria.DUMMY, title);
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);
        // Hide the red side scores on every line (Paper number-format API).
        obj.numberFormat(NumberFormat.blank());

        List<String> lines = resolveLines(p, inCommunity);
        // Render top-to-bottom: highest score on top
        int score = lines.size();
        // Reuse a stable set of team names so we don't churn entities
        for (int i = 0; i < lines.size(); i++) {
            String text = lines.get(i);
            String teamName = "nb_" + i;
            String entry = uniqueEntry(i);
            Team team = board.getTeam(teamName);
            if (team == null) team = board.registerNewTeam(teamName);
            team.prefix(Msg.mm(text));
            if (!team.hasEntry(entry)) {
                // Clear previous and add the entry
                for (String e : new ArrayList<>(team.getEntries())) team.removeEntry(e);
                team.addEntry(entry);
            }
            obj.getScore(entry).setScore(score - i);
        }
        p.setScoreboard(board);
        plugin.rankNameplates().refreshViewer(p);
    }

    private List<String> buildLines(Player p) {
        Island island = plugin.islands().ofPlayer(p);
        PlayerProgression prog = plugin.progression().get(p);
        List<String> lines = new ArrayList<>();
        if (island != null) {
            boolean inNether = p.getWorld() != null
                    && p.getWorld().getName().equals(plugin.worlds().netherWorldName());
            int pLvl = island.data().getPrestigeLevel();
            if (pLvl > 0) {
                lines.add(plugin.prestige().title(pLvl) + " <gray>(<white>" + pLvl + "<gray>)");
                int coinPct = (int) Math.round(plugin.prestige().coinMultiplierAtLevel(pLvl) * 100) - 100;
                int xpPct = (int) Math.round(plugin.prestige().xpMultiplierAtLevel(pLvl) * 100) - 100;
                if (coinPct > 0 || xpPct > 0) {
                    lines.add("<aqua>Bonus: <yellow>+" + coinPct + "%<gray> coins <yellow>+" + xpPct + "%<gray> xp");
                }
            }
            Phase phase = inNether
                    ? plugin.phases().getNetherOrLast(island.data().getNetherPhaseIndex())
                    : plugin.phases().getOrLast(island.data().getPhaseIndex());
            String color = phase == null ? "white" : phase.getThemeColor();
            String name = phase == null ? "?" : phase.getDisplayName();
            String label = inNether ? "Nether" : "Phase";
            lines.add("<gray>" + label + ": <" + color + ">" + name);
            int prog2 = inNether ? island.data().getNetherPhaseProgress() : island.data().getPhaseProgress();
            int req = phase == null ? 1 : phase.getRequiredBlocks();
            int remaining = Math.max(0, req - prog2);
            if (remaining > 0 && remaining <= 50) {
                lines.add("<gold><bold>" + remaining + "</bold> blocks to next phase!");
            } else {
                lines.add("<gray>" + prog2 + " / " + req + " blocks");
            }
            long totalBlocks = inNether ? island.data().getNetherBlocksBroken() : island.data().getBlocksBroken();
            lines.add("<gray>Total: <white>" + totalBlocks);
            String riftLine = nextRiftLine(island, inNether);
            if (riftLine != null) lines.add(riftLine);
            lines.add("<gold>Coins: <yellow>" + plugin.economy().balance(island));
            var path = plugin.seasonalPaths().activePath();
            int pathTier = plugin.seasonalPaths().tierFor(prog.getSeasonalPathPoints());
            lines.add("<" + path.color() + ">Path: <white>" + pathTier + "/" + com.nova.novablock.season.SeasonalPathManager.TIER_COUNT);
            int streak = prog.getLoginStreak();
            if (streak >= 2) {
                lines.add("<light_purple>Streak: <white>" + streak + "d");
            }
            lines.add(" ");
        }
        for (SkillType t : SkillType.values()) {
            lines.add("<" + t.color() + ">" + t.displayName() + " <white>Lv " + prog.getLevel(t));
        }
        lines.add("  ");
        lines.add("<gray>Online: <green>" + Bukkit.getOnlinePlayers().size());
        if (plugin.seasons().active() != null) {
            lines.add("   ");
            var ev = plugin.seasons().active();
            long secs = Math.max(0, (plugin.seasons().activeUntil() - System.currentTimeMillis()) / 1000);
            lines.add(ev.color + "★ " + ev.displayName + " <gray>(" + formatTime(secs) + ")");
        }
        // Community weekly goal — only show in the final 24h to keep the sidebar tight.
        if (plugin.community() != null
                && plugin.getConfig().getBoolean("community.weekly-goal.enabled", true)) {
            var goal = plugin.community().goal();
            long remaining = goal.windowEnd() - System.currentTimeMillis();
            if (remaining > 0 && remaining <= 24L * 60 * 60 * 1000
                    && goal.progress() < goal.target()) {
                lines.add("<gold>Goal: <white>" + goal.progress() + "/" + goal.target());
                lines.add("<gray>Ends: <yellow>" + formatGoalTime(remaining / 1000));
            }
        }
        return lines;
    }

    /**
     * Lines for the shared community OneBlock world. Everyone in this world sees the same
     * community-wide stats (phase, pool, weekly goal) plus their own stake in the next
     * payout / weekly contribution — not their personal-island progression.
     */
    private List<String> buildCommunityLines(Player p) {
        var hub = plugin.community();
        var block = hub.block();
        var goal = hub.goal();
        UUID id = p.getUniqueId();
        List<String> lines = new ArrayList<>();

        Phase phase = plugin.phases().getOrLast(block.phaseIndex());
        String color = phase == null ? "white" : phase.getThemeColor();
        String name = phase == null ? "?" : phase.getDisplayName();
        lines.add("<gray>Phase: <" + color + ">" + name);

        int prog = block.phaseProgress();
        int req = block.requiredForCurrentPhase();
        int remaining = Math.max(0, req - prog);
        if (remaining > 0 && remaining <= 200) {
            lines.add("<gold><bold>" + remaining + "</bold> to next phase!");
        } else {
            lines.add("<gray>" + prog + " / " + req + " blocks");
        }
        lines.add("<gray>Total: <white>" + block.blocksBroken());
        lines.add(" ");

        // Shared coin pool + your stake in the next payout.
        lines.add("<gold>Pool: <yellow>" + block.pool());
        long yourPool = block.contributionByPlayer().getOrDefault(id, 0L);
        if (yourPool > 0) lines.add("<gray>Your share: <yellow>" + yourPool);
        lines.add("  ");

        // Weekly community goal — always shown here; it's the point of the place.
        if (plugin.getConfig().getBoolean("community.weekly-goal.enabled", true)) {
            long gp = goal.progress();
            long gt = goal.target();
            int pct = (int) Math.min(100, gp * 100 / Math.max(1, gt));
            lines.add("<aqua>Weekly: <white>" + gp + "/" + gt + " <gray>(" + pct + "%)");
            long yourWeek = goal.contributionByPlayer().getOrDefault(id, 0L);
            if (yourWeek > 0) lines.add("<gray>You: <white>" + yourWeek + " blocks");
            long endsIn = goal.windowEnd() - System.currentTimeMillis();
            if (endsIn > 0) lines.add("<gray>Resets: <yellow>" + formatGoalTime(endsIn / 1000));
            lines.add("   ");
        }

        // Top weekly contributor.
        var top = goal.topContributors(1);
        if (!top.isEmpty()) {
            var e = top.get(0);
            String n = Bukkit.getOfflinePlayer(e.getKey()).getName();
            lines.add("<gold>Top: <white>" + (n == null ? "?" : n) + " <yellow>" + e.getValue());
        }
        lines.add("<gray>Online: <green>" + Bukkit.getOnlinePlayers().size());
        return lines;
    }

    /**
     * "Next rift" is block-based, not time-based: a loot-room or boss roll happens
     * only after the configured min-blocks gate elapses since the last drop. Surface
     * it once the player is within 50 blocks of either gate so they feel something
     * coming, instead of crunching aimlessly.
     */
    private String nextRiftLine(Island island, boolean inNether) {
        var cfg = plugin.getConfig();
        int lootCd = Math.max(1, cfg.getInt(
                inNether ? "cooldowns.netherLootRoomMinBlocks" : "cooldowns.lootRoomMinBlocks",
                inNether ? 400 : 150));
        int bossCd = Math.max(1, cfg.getInt(
                inNether ? "cooldowns.netherBossMinBlocks" : "cooldowns.bossMinBlocks", 300));
        long broken = inNether ? island.data().getNetherBlocksBroken() : island.data().getBlocksBroken();
        long lastLoot = inNether ? island.data().getNetherLastLootRoomAt() : island.data().getLastLootRoomAt();
        long lastBoss = inNether ? island.data().getNetherLastBossAt() : island.data().getLastBossAt();
        long lootSince = broken - lastLoot;
        long bossSince = broken - lastBoss;
        long lootLeft = lootCd - lootSince;
        long bossLeft = bossCd - bossSince;
        if (lootLeft > 0 && lootLeft <= 50) {
            return "<dark_purple>Next rift: <white>" + lootLeft + " blocks";
        }
        if (bossLeft > 0 && bossLeft <= 50) {
            return "<red>Next boss: <white>" + bossLeft + " blocks";
        }
        return null;
    }

    private String formatTime(long seconds) {
        long m = seconds / 60;
        long s = seconds % 60;
        return m + ":" + (s < 10 ? "0" : "") + s;
    }

    private String formatGoalTime(long seconds) {
        long d = seconds / 86400;
        long h = (seconds % 86400) / 3600;
        long m = (seconds % 3600) / 60;
        if (d > 0) return d + "d " + h + "h";
        if (h > 0) return h + "h " + m + "m";
        return Math.max(0, m) + "m";
    }

    /** Builds a 1–2 char unique entry string per line (invisible-ish). */
    private String uniqueEntry(int i) {
        // Use color codes which render as empty for unique entries
        char[] hex = "0123456789abcdef".toCharArray();
        return "§" + hex[i % 16] + "§r" + (i / 16 == 0 ? "" : Character.toString((char) ('a' + (i / 16))));
    }

    public void clear(Player p) {
        Scoreboard b = boards.remove(p.getUniqueId());
        if (b != null) {
            p.setScoreboard(Bukkit.getScoreboardManager().getNewScoreboard());
            plugin.rankNameplates().refreshViewer(p);
        }
    }

    public void shutdown() {
        if (ticker != null) { ticker.cancel(); ticker = null; }
        for (Player p : Bukkit.getOnlinePlayers()) clear(p);
        boards.clear();
    }
}
