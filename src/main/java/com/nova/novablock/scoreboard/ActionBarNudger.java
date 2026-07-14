package com.nova.novablock.scoreboard;

import com.nova.novablock.NovaBlock;
import com.nova.novablock.island.Island;
import com.nova.novablock.phase.Phase;
import com.nova.novablock.util.Msg;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Pushes one-shot action-bar prompts that the sidebar can't capture.
 * Currently only the "you're almost at the next phase" nudge — fires once per
 * (player, dimension, phase index) so we don't spam the player while they mine
 * the final 50 blocks, and so the Overworld and Nether nudges are independent.
 */
public class ActionBarNudger implements Listener {

    private static final int APPROACH_THRESHOLD = 50;

    private final NovaBlock plugin;
    private final ConcurrentMap<NudgeKey, Boolean> nudged = new ConcurrentHashMap<>();

    public ActionBarNudger(NovaBlock plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Island island = plugin.islands().ofPlayer(player);
        if (island == null) return;
        com.nova.novablock.island.Dimension dim = com.nova.novablock.island.Dimension.OVERWORLD;
        if (player.getWorld() != null) {
            String wn = player.getWorld().getName();
            if (wn.equals(plugin.worlds().netherWorldName())) dim = com.nova.novablock.island.Dimension.NETHER;
            else if (wn.equals(plugin.worlds().endWorldName())) dim = com.nova.novablock.island.Dimension.END;
        }
        Phase phase = plugin.phases().getOrLast(dim, island.data().getPhaseIndex(dim));
        if (phase == null) return;
        int progress = island.data().getPhaseProgress(dim);
        int remaining = phase.getRequiredBlocks() - progress;
        if (remaining <= 0 || remaining > APPROACH_THRESHOLD) return;

        int phaseIdx = island.data().getPhaseIndex(dim);
        NudgeKey key = new NudgeKey(player.getUniqueId(), dim, phaseIdx);
        if (nudged.putIfAbsent(key, Boolean.TRUE) != null) return;

        Phase next = plugin.phases().get(dim, phaseIdx + 1);
        String label;
        String color;
        if (next == null) {
            // Every track has its own prestige now, so the final-phase nudge is
            // always "Prestige" (of that dimension).
            label = switch (dim) {
                case OVERWORLD -> "Prestige";
                case NETHER -> "Nether Prestige";
                case END -> "End Prestige";
            };
            color = "<gradient:#7B61FF:#4FC3F7>";
        } else {
            String prefix = switch (dim) {
                case OVERWORLD -> "Phase ";
                case NETHER -> "Nether Phase ";
                case END -> "End Phase ";
            };
            label = prefix + (phaseIdx + 2) + ": " + next.getDisplayName();
            color = "<" + next.getThemeColor() + ">";
        }
        Msg.actionBar(player,
                "<gold><bold>" + remaining + "</bold> blocks to " + color + label);
    }

    private record NudgeKey(UUID uuid, com.nova.novablock.island.Dimension dim, int phaseIndex) {}
}
