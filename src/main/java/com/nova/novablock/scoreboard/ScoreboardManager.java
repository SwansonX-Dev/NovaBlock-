package com.nova.novablock.scoreboard;

import com.nova.novablock.NovaBlock;
import com.nova.novablock.island.Island;
import com.nova.novablock.phase.Phase;
import com.nova.novablock.progression.PlayerProgression;
import com.nova.novablock.progression.SkillType;
import com.nova.novablock.util.Msg;
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

    public ScoreboardManager(NovaBlock plugin) { this.plugin = plugin; }

    public void startTicker() {
        if (ticker != null) ticker.cancel();
        ticker = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    private void tick() {
        for (Player p : Bukkit.getOnlinePlayers()) update(p);
    }

    public void update(Player p) {
        Scoreboard board = boards.computeIfAbsent(p.getUniqueId(),
                u -> Bukkit.getScoreboardManager().getNewScoreboard());
        Objective obj = board.getObjective("nb");
        if (obj != null) obj.unregister();
        obj = board.registerNewObjective("nb", Criteria.DUMMY,
                Msg.mm("<gradient:#7B61FF:#4FC3F7><bold>NovaBlock"));
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);

        List<String> lines = buildLines(p);
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
    }

    private List<String> buildLines(Player p) {
        Island island = plugin.islands().ofPlayer(p);
        PlayerProgression prog = plugin.progression().get(p);
        List<String> lines = new ArrayList<>();
        if (island != null) {
            Phase phase = plugin.phases().getOrLast(island.data().getPhaseIndex());
            String color = phase == null ? "white" : phase.getThemeColor();
            String name = phase == null ? "?" : phase.getDisplayName();
            lines.add("<gray>Phase: <" + color + ">" + name);
            int prog2 = island.data().getPhaseProgress();
            int req = phase == null ? 1 : phase.getRequiredBlocks();
            lines.add("<gray>" + prog2 + " / " + req + " blocks");
            lines.add("<gray>Total: <white>" + island.data().getBlocksBroken());
            lines.add("<gold>Coins: <yellow>" + island.data().getCoins());
            lines.add(" ");
        }
        for (SkillType t : SkillType.values()) {
            lines.add("<" + t.color() + ">" + t.displayName() + " <white>Lv " + prog.getLevel(t));
        }
        if (plugin.seasons().active() != null) {
            lines.add(" ");
            var ev = plugin.seasons().active();
            long secs = Math.max(0, (plugin.seasons().activeUntil() - System.currentTimeMillis()) / 1000);
            lines.add(ev.color + "★ " + ev.displayName + " <gray>(" + formatTime(secs) + ")");
        }
        return lines;
    }

    private String formatTime(long seconds) {
        long m = seconds / 60;
        long s = seconds % 60;
        return m + ":" + (s < 10 ? "0" : "") + s;
    }

    /** Builds a 1–2 char unique entry string per line (invisible-ish). */
    private String uniqueEntry(int i) {
        // Use color codes which render as empty for unique entries
        char[] hex = "0123456789abcdef".toCharArray();
        return "§" + hex[i % 16] + "§r" + (i / 16 == 0 ? "" : Character.toString((char) ('a' + (i / 16))));
    }

    public void clear(Player p) {
        Scoreboard b = boards.remove(p.getUniqueId());
        if (b != null) p.setScoreboard(Bukkit.getScoreboardManager().getNewScoreboard());
    }

    public void shutdown() {
        if (ticker != null) { ticker.cancel(); ticker = null; }
        for (Player p : Bukkit.getOnlinePlayers()) clear(p);
        boards.clear();
    }
}
