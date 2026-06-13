package com.nova.novablock.questline;

import com.nova.novablock.NovaBlock;
import com.nova.novablock.island.Island;
import com.nova.novablock.island.IslandData;
import com.nova.novablock.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

/**
 * Endless, per-island questline. All members of an island share one stage; any
 * member's activity advances it, and the coin reward is paid into the shared
 * island bank. Stages are generated procedurally from {@link #CYCLE} so the
 * chain never ends, and every objective type is infinitely repeatable so it can
 * never stall.
 *
 * <p>Interim progress lives on {@link IslandData} and is persisted on stage
 * completion (and by the normal island autosave); the stage advance and bank
 * payout are saved immediately.
 */
public class IslandQuestlineManager {

    /**
     * The objective rotation. Weighted toward mining the OneBlock (the core
     * loop) with mob fights, cobble-gen runs, loot rooms and prophecies mixed in
     * for variety. No phase-advance objective: phases are finite and would
     * soft-lock the chain.
     */
    private static final IslandObjective[] CYCLE = {
            IslandObjective.MINE_ONEBLOCKS,
            IslandObjective.KILL_MOBS,
            IslandObjective.MINE_ONEBLOCKS,
            IslandObjective.GENERATE_COBBLE,
            IslandObjective.SMELT_ITEMS,
            IslandObjective.MINE_ONEBLOCKS,
            IslandObjective.HARVEST_CROPS,
            IslandObjective.CLEAR_LOOT_ROOMS,
            IslandObjective.MINE_ONEBLOCKS,
            IslandObjective.CATCH_FISH,
            IslandObjective.MINE_ONEBLOCKS,
            IslandObjective.FULFILL_PROPHECIES,
    };

    private final NovaBlock plugin;

    // Config-tunable reward curve.
    private long coinsBase = 750L;
    private long coinsPerStage = 250L;
    private int milestoneInterval = 10;
    private long milestoneBonusCoins = 5000L;
    private List<String> milestoneCommands = List.of();

    public IslandQuestlineManager(NovaBlock plugin) {
        this.plugin = plugin;
        reloadConfig();
    }

    public void reloadConfig() {
        var c = plugin.getConfig();
        coinsBase = c.getLong("questline.coins-base", 750L);
        coinsPerStage = c.getLong("questline.coins-per-stage", 250L);
        milestoneInterval = Math.max(1, c.getInt("questline.milestone-interval", 10));
        milestoneBonusCoins = c.getLong("questline.milestone-bonus-coins", 5000L);
        milestoneCommands = c.getStringList("questline.milestone-commands");
    }

    // --- Stage maths -----------------------------------------------------------

    public IslandObjective objectiveFor(int stage) {
        return CYCLE[Math.floorMod(stage - 1, CYCLE.length)];
    }

    public int requiredFor(int stage, IslandObjective objective) {
        return switch (objective) {
            case MINE_ONEBLOCKS -> 200 + stage * 40;
            case KILL_MOBS -> 8 + stage * 2;
            case GENERATE_COBBLE -> 64 + stage * 16;
            case SMELT_ITEMS -> 32 + stage * 8;
            case HARVEST_CROPS -> 24 + stage * 6;
            case CATCH_FISH -> 8 + stage * 2;
            case CLEAR_LOOT_ROOMS -> 1 + (stage - 1) / 8;
            case FULFILL_PROPHECIES -> 1 + (stage - 1) / 5;
        };
    }

    public boolean isMilestone(int stage) { return stage % milestoneInterval == 0; }

    public int milestoneInterval() { return milestoneInterval; }

    public long rewardCoinsFor(int stage) {
        long coins = coinsBase + (long) stage * coinsPerStage;
        if (isMilestone(stage)) coins += milestoneBonusCoins;
        return coins;
    }

    public IslandQuestStage stageOf(Island island) {
        int stage = island.data().getQuestlineStage();
        IslandObjective obj = objectiveFor(stage);
        return new IslandQuestStage(stage, obj, requiredFor(stage, obj), rewardCoinsFor(stage), isMilestone(stage));
    }

    public int progressOf(Island island) {
        IslandQuestStage s = stageOf(island);
        return Math.min(s.required(), island.data().getQuestlineProgress());
    }

    // --- Activity hooks --------------------------------------------------------

    /** Credit activity by {@code p} to {@code p}'s own island. */
    public void record(Player p, IslandObjective type, int amount) {
        if (p == null) return;
        Island island = plugin.islands().ofPlayer(p);
        if (island != null) add(island, type, amount, p);
    }

    /**
     * Credit activity to a specific island once (used for shared events like a
     * boss kill, which has several participants but is one island accomplishment).
     */
    public void recordForIsland(Island island, IslandObjective type, int amount, Player completer) {
        if (island != null) add(island, type, amount, completer);
    }

    private void add(Island island, IslandObjective type, int amount, Player completer) {
        if (amount <= 0) return;
        IslandData d = island.data();
        int stage = d.getQuestlineStage();
        if (objectiveFor(stage) != type) return;
        int required = requiredFor(stage, objectiveFor(stage));
        int before = Math.min(required, d.getQuestlineProgress());
        int after = Math.min(required, before + amount);
        if (after == before) return;
        d.setQuestlineProgress(after);
        if (after >= required) complete(island, stage, completer);
    }

    private void complete(Island island, int stage, Player completer) {
        IslandData d = island.data();
        long coins = rewardCoinsFor(stage);
        boolean milestone = isMilestone(stage);

        plugin.economy().award(island, coins);
        d.setQuestlineStage(stage + 1);
        d.setQuestlineProgress(0);
        plugin.storage().saveIsland(d);
        // Clearing a stage adds questline points — may push the island to a new level.
        plugin.islands().checkLevelUp(island);

        String completerName = completer == null ? "An islander" : completer.getName();
        String nextDesc = describeStage(stage + 1);
        for (UUID id : d.getMembers()) {
            Player m = Bukkit.getPlayer(id);
            if (m == null) continue;
            Msg.title(m, (milestone ? "<gold><bold>★ Milestone " + stage + " ★" : "<gold>★ Island Quest " + stage),
                    "<yellow>+" + coins + " coins to the island bank");
            Msg.send(m, "<gold>Island Questline <gray>— stage <yellow>" + stage
                    + "<gray> cleared by <yellow>" + completerName
                    + "<gray>. Next up: <white>" + nextDesc);
            m.playSound(m.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, milestone ? 0.8f : 1.2f);
        }

        if (milestone && !milestoneCommands.isEmpty()) runMilestoneCommands(d, stage);
    }

    /** Run each configured milestone command for every online member ({@code %player%}). */
    private void runMilestoneCommands(IslandData d, int stage) {
        for (UUID id : d.getMembers()) {
            Player m = Bukkit.getPlayer(id);
            if (m == null) continue;
            for (String cmd : milestoneCommands) {
                if (cmd == null || cmd.isBlank()) continue;
                String prepared = cmd.replace("%player%", m.getName())
                        .replace("%stage%", String.valueOf(stage));
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), prepared);
            }
        }
    }

    public String describeStage(int stage) {
        IslandObjective obj = objectiveFor(stage);
        return obj.describe(requiredFor(stage, obj));
    }
}
