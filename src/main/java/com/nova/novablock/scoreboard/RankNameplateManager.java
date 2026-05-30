package com.nova.novablock.scoreboard;

import com.nova.novablock.NovaBlock;
import com.nova.novablock.compat.LuckPermsRanks;
import com.nova.novablock.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.ArrayList;

public class RankNameplateManager {
    private static final String TEAM_PREFIX = "nbnp_";

    private final NovaBlock plugin;
    private BukkitTask ticker;
    private boolean active;

    public RankNameplateManager(NovaBlock plugin) {
        this.plugin = plugin;
    }

    public void startTicker() {
        active = true;
        if (ticker != null) ticker.cancel();
        ticker = Bukkit.getScheduler().runTaskTimer(plugin, this::refreshAll, 40L, 20L * 15);
    }

    public void refreshAll() {
        if (!active) return;
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            refreshViewer(viewer);
        }
    }

    public void refreshViewer(Player viewer) {
        if (!active) return;
        if (viewer == null || !viewer.isOnline()) return;
        Scoreboard board = viewer.getScoreboard();
        clearStaleTeams(board);
        for (Player subject : Bukkit.getOnlinePlayers()) {
            apply(board, subject);
        }
    }

    public void remove(Player player) {
        if (!active) return;
        if (player == null) return;
        String entry = player.getName();
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            Scoreboard board = viewer.getScoreboard();
            for (Team team : new ArrayList<>(board.getTeams())) {
                if (team.getName().startsWith(TEAM_PREFIX)) team.removeEntry(entry);
            }
        }
    }

    public void shutdown() {
        active = false;
        if (ticker != null) { ticker.cancel(); ticker = null; }
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            clearStaleTeams(viewer.getScoreboard());
        }
    }

    private void apply(Scoreboard board, Player subject) {
        Team team = board.getTeam(teamName(subject));
        if (team == null) team = board.registerNewTeam(teamName(subject));
        String rank = LuckPermsRanks.rank(subject);
        team.prefix(rank.isBlank() ? Msg.mm("") : Msg.mm("<gray>[<#FFC940>" + rank + "<gray>] "));
        team.suffix(Msg.mm(""));
        if (!team.hasEntry(subject.getName())) {
            for (String entry : new ArrayList<>(team.getEntries())) team.removeEntry(entry);
            team.addEntry(subject.getName());
        }
    }

    private void clearStaleTeams(Scoreboard board) {
        for (Team team : new ArrayList<>(board.getTeams())) {
            if (!team.getName().startsWith(TEAM_PREFIX)) continue;
            String expectedEntry = null;
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (team.getName().equals(teamName(player))) {
                    expectedEntry = player.getName();
                    break;
                }
            }
            if (expectedEntry == null) {
                team.unregister();
                continue;
            }
            for (String entry : new ArrayList<>(team.getEntries())) {
                if (!entry.equals(expectedEntry)) team.removeEntry(entry);
            }
        }
    }

    private static String teamName(Player player) {
        return TEAM_PREFIX + player.getUniqueId().toString().substring(0, 10);
    }
}
