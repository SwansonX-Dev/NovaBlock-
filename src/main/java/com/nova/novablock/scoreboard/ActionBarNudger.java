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
 * (player, phase index) so we don't spam the player while they mine the final
 * 50 blocks.
 */
public class ActionBarNudger implements Listener {

    private static final int APPROACH_THRESHOLD = 50;

    private final NovaBlock plugin;
    private final ConcurrentMap<UUID, Integer> nudgedPhaseByPlayer = new ConcurrentHashMap<>();

    public ActionBarNudger(NovaBlock plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Island island = plugin.islands().ofPlayer(player);
        if (island == null) return;
        Phase phase = plugin.phases().getOrLast(island.data().getPhaseIndex());
        if (phase == null) return;
        int remaining = phase.getRequiredBlocks() - island.data().getPhaseProgress();
        if (remaining <= 0 || remaining > APPROACH_THRESHOLD) return;

        int phaseIdx = island.data().getPhaseIndex();
        Integer last = nudgedPhaseByPlayer.get(player.getUniqueId());
        if (last != null && last == phaseIdx) return;
        nudgedPhaseByPlayer.put(player.getUniqueId(), phaseIdx);

        Phase next = plugin.phases().get(phaseIdx + 1);
        String label = next == null ? "Prestige" : ("Phase " + (phaseIdx + 2) + ": " + next.getDisplayName());
        String color = next == null ? "<gradient:#7B61FF:#4FC3F7>" : ("<" + next.getThemeColor() + ">");
        Msg.actionBar(player,
                "<gold><bold>" + remaining + "</bold> blocks to " + color + label);
    }
}
