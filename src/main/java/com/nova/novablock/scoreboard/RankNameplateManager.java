package com.nova.novablock.scoreboard;

import com.nova.novablock.NovaBlock;
import com.nova.novablock.compat.LuckPermsRanks;
import com.nova.novablock.island.Island;
import com.nova.novablock.util.Msg;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.ArrayList;
import java.util.UUID;

public class RankNameplateManager {
    private static final String TEAM_PREFIX = "nbnp_";
    /**
     * Refresh cadence. Five seconds so the Owner/Member/Visitor badge keeps up
     * with players crossing onto and off islands without a per-move listener.
     */
    private static final long REFRESH_TICKS = 20L * 5;
    private static final LegacyComponentSerializer LEGACY =
            LegacyComponentSerializer.legacyAmpersand();

    private final NovaBlock plugin;
    private BukkitTask ticker;
    private boolean active;

    public RankNameplateManager(NovaBlock plugin) {
        this.plugin = plugin;
    }

    public void startTicker() {
        active = true;
        if (ticker != null) ticker.cancel();
        ticker = Bukkit.getScheduler().runTaskTimer(plugin, this::refreshAll, 40L, REFRESH_TICKS);
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
        team.prefix(buildPrefix(subject));
        team.suffix(Component.empty());
        if (!team.hasEntry(subject.getName())) {
            for (String entry : new ArrayList<>(team.getEntries())) team.removeEntry(entry);
            team.addEntry(subject.getName());
        }
    }

    /**
     * Combined nameplate prefix: LuckPerms cached-meta prefix (legacy-format
     * string parsed via Adventure's legacy serializer) followed by an island
     * role badge. Either piece is omitted if missing — players with no LP
     * prefix and not on any island get a vanilla nameplate.
     */
    private Component buildPrefix(Player subject) {
        Component out = Component.empty();
        String lpPrefix = LuckPermsRanks.prefix(subject);
        if (lpPrefix != null && !lpPrefix.isBlank()) {
            String normalized = lpPrefix.replace('§', '&');
            out = out.append(LEGACY.deserialize(normalized));
            if (!normalized.endsWith(" ")) out = out.append(Component.text(" "));
        }
        String islandBadge = islandRoleBadgeFor(subject);
        if (!islandBadge.isBlank()) {
            // Trailing space baked into the same component so IslandBadgeMessageFilter
            // can strip the whole "[Owner] " token from broadcasts without leaving a gap.
            out = out.append(Msg.mm(islandBadge + " "));
        }
        return out;
    }

    /**
     * Subject's role on the island they're currently standing on. Empty when
     * they're not on any island — that's the spatial signal ("nobody owns
     * this place"). Owner gold, member aqua, visitor gray.
     */
    private String islandRoleBadgeFor(Player subject) {
        if (subject.getLocation() == null || subject.getWorld() == null) return "";
        Island island = plugin.islands().atLocation(subject.getLocation());
        if (island == null) return "";
        UUID uuid = subject.getUniqueId();
        if (uuid.equals(island.data().getOwner())) return "<gold>[Owner]";
        if (island.data().getMembers().contains(uuid)) return "<aqua>[Member]";
        return "<gray>[Visitor]";
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
