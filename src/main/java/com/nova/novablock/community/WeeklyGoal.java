package com.nova.novablock.community;

import com.nova.novablock.NovaBlock;
import com.nova.novablock.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Server-wide weekly community goal. Every block break (personal-island or
 * community) calls {@link #recordBreak(Player, long)}; on hitting milestones
 * we broadcast, and on the goal being met we distribute tiered coin rewards.
 *
 * <p>Resets at Monday 00:00 server-local — matches WeeklySprintManager's
 * convention so the two reset on the same heartbeat.
 */
public class WeeklyGoal {

    private static final long WEEK_MILLIS = 7L * 24 * 60 * 60 * 1000;

    private final NovaBlock plugin;

    // ---- persisted state ----
    private long windowStart;
    private long progress;
    private final Map<UUID, Long> contributionByPlayer = new HashMap<>();
    private final Set<Integer> milestonesFired = new HashSet<>();
    private boolean payoutDone;

    public WeeklyGoal(NovaBlock plugin) {
        this.plugin = plugin;
        this.windowStart = mondayMidnightOf(System.currentTimeMillis());
    }

    public long windowStart() { return windowStart; }
    public long windowEnd() { return windowStart + WEEK_MILLIS; }
    public long progress() { return progress; }
    public long target() { return Math.max(1, plugin.getConfig().getLong("community.weekly-goal.target-blocks", 100_000)); }
    public Map<UUID, Long> contributionByPlayer() { return contributionByPlayer; }
    public Set<Integer> milestonesFired() { return milestonesFired; }
    public boolean payoutDone() { return payoutDone; }

    public void setWindowStart(long v) { this.windowStart = v; }
    public void setProgress(long v) { this.progress = v; }
    public void setPayoutDone(boolean v) { this.payoutDone = v; }

    /** Bump progress + the per-player contribution map; called from BlockListener. */
    public void recordBreak(Player p, long amount) {
        if (!isEnabled() || amount <= 0) return;
        rolloverIfNeeded();
        progress += amount;
        contributionByPlayer.merge(p.getUniqueId(), amount, Long::sum);
        checkMilestones();
    }

    /** Wall-clock tick called from EventManager (1 min). Handles rollover + payout. */
    public void tick() {
        if (!isEnabled()) return;
        rolloverIfNeeded();
        if (progress >= target() && !payoutDone) {
            distributeRewards();
            payoutDone = true;
        }
    }

    private void checkMilestones() {
        long tgt = target();
        for (int pct : plugin.getConfig().getIntegerList("community.weekly-goal.broadcast.milestones-percent")) {
            if (milestonesFired.contains(pct)) continue;
            if (progress * 100L >= tgt * (long) pct) {
                milestonesFired.add(pct);
                broadcastMilestone(pct);
                if (pct >= 100 && !payoutDone) {
                    distributeRewards();
                    payoutDone = true;
                }
            }
        }
    }

    private void broadcastMilestone(int pct) {
        if (pct >= 100) {
            Bukkit.broadcast(Msg.mm("<gold>✦ <yellow>Weekly community goal complete! <gray>"
                    + progress + " / " + target() + " blocks."));
            return;
        }
        Bukkit.broadcast(Msg.mm("<gold>✦ <yellow>Weekly goal: <white>" + pct
                + "% <gray>(" + progress + " / " + target() + ")"));
    }

    private void distributeRewards() {
        var cfg = plugin.getConfig();
        long baselineCoins = cfg.getLong("community.weekly-goal.rewards.baseline-coins", 1000);
        long baselineMin = cfg.getLong("community.weekly-goal.rewards.baseline-min-contribution", 250);
        int topPct = Math.max(1, cfg.getInt("community.weekly-goal.rewards.top-percent", 10));
        long topBonus = cfg.getLong("community.weekly-goal.rewards.top-percent-bonus-coins", 10_000);
        // Fixed coin payout for the top placed contributors (1st, 2nd, 3rd, …). Stacks on
        // top of the baseline. Absent from config -> default podium; set to [] to disable.
        List<Long> podium = cfg.contains("community.weekly-goal.rewards.podium-coins")
                ? cfg.getLongList("community.weekly-goal.rewards.podium-coins")
                : List.of(100_000L, 50_000L, 25_000L);

        List<Map.Entry<UUID, Long>> ranked = new ArrayList<>(contributionByPlayer.entrySet());
        ranked.sort(Comparator.comparingLong((Map.Entry<UUID, Long> e) -> e.getValue()).reversed());

        long qualifyingPlayers = ranked.stream().filter(e -> e.getValue() >= baselineMin).count();
        long topN = Math.max(1, Math.round(qualifyingPlayers * topPct / 100.0));

        List<String> podiumLines = new ArrayList<>();
        int rank = 0;
        for (var e : ranked) {
            if (e.getValue() < baselineMin) continue;
            long coins = baselineCoins;
            if (rank < topN) coins += topBonus;
            long podiumBonus = rank < podium.size() ? Math.max(0L, podium.get(rank)) : 0L;
            coins += podiumBonus;
            OfflinePlayer op = Bukkit.getOfflinePlayer(e.getKey());
            plugin.economy().deposit(op, coins);
            Player online = op.getPlayer();
            if (online != null) {
                Msg.title(online, "<gold>★ Weekly Goal Reward",
                        "<yellow>+" + coins + " coins <gray>(rank #" + (rank + 1) + ")");
            }
            if (podiumBonus > 0) {
                String name = op.getName() != null ? op.getName() : "Unknown";
                podiumLines.add("<gray>  #" + (rank + 1) + " <yellow>" + name
                        + " <gray>— <gold>" + podiumBonus + " coins");
            }
            rank++;
        }
        Bukkit.broadcast(Msg.mm("<gold>✦ <yellow>Goal rewards distributed to <white>"
                + Math.min(qualifyingPlayers, ranked.size()) + "<yellow> contributors."));
        if (!podiumLines.isEmpty()) {
            Bukkit.broadcast(Msg.mm("<gold>✦ <yellow>Weekly community podium:"));
            for (String line : podiumLines) Bukkit.broadcast(Msg.mm(line));
        }
    }

    private void rolloverIfNeeded() {
        long currentWeek = mondayMidnightOf(System.currentTimeMillis());
        if (currentWeek <= windowStart) return;
        windowStart = currentWeek;
        progress = 0;
        contributionByPlayer.clear();
        milestonesFired.clear();
        payoutDone = false;
    }

    public List<Map.Entry<UUID, Long>> topContributors(int limit) {
        List<Map.Entry<UUID, Long>> out = new ArrayList<>(contributionByPlayer.entrySet());
        out.sort(Comparator.comparingLong((Map.Entry<UUID, Long> e) -> e.getValue()).reversed());
        if (out.size() > limit) return out.subList(0, limit);
        return out;
    }

    public void resetCurrentWeek() {
        windowStart = mondayMidnightOf(System.currentTimeMillis());
        progress = 0;
        contributionByPlayer.clear();
        milestonesFired.clear();
        payoutDone = false;
    }

    public boolean forcePayout() {
        if (payoutDone || progress < target()) return false;
        distributeRewards();
        payoutDone = true;
        return true;
    }

    private boolean isEnabled() {
        return plugin.getConfig().getBoolean("community.weekly-goal.enabled", true);
    }

    private static long mondayMidnightOf(long epochMillis) {
        ZonedDateTime z = Instant.ofEpochMilli(epochMillis)
                .atZone(ZoneId.systemDefault())
                .withHour(0).withMinute(0).withSecond(0).withNano(0)
                .with(DayOfWeek.MONDAY);
        return z.toInstant().toEpochMilli();
    }
}
