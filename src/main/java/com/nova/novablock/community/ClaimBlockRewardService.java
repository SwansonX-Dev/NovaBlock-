package com.nova.novablock.community;

import com.nova.novablock.NovaBlock;
import com.nova.novablock.progression.PlayerProgression;
import com.nova.novablock.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public class ClaimBlockRewardService {

    private final NovaBlock plugin;

    public ClaimBlockRewardService(NovaBlock plugin) {
        this.plugin = plugin;
    }

    public void recordPersonalBreak(Player player) {
        if (!plugin.getConfig().getBoolean("claim-block-rewards.include-personal-oneblock", true)) return;
        record(player);
    }

    public void recordCommunityBreak(Player player) {
        if (!plugin.getConfig().getBoolean("claim-block-rewards.include-community-oneblock", true)) return;
        record(player);
    }

    private void record(Player player) {
        if (!plugin.getConfig().getBoolean("claim-block-rewards.enabled", true)) return;
        int every = Math.max(1, plugin.getConfig().getInt("claim-block-rewards.every-oneblock-breaks", 100));
        long amount = Math.max(0L, plugin.getConfig().getLong("claim-block-rewards.claim-blocks-per-reward", every));
        if (amount <= 0L) return;

        PlayerProgression progress = plugin.progression().get(player);
        long total = progress.incrementClaimRewardBreaks();
        if (total % every != 0L) return;

        String command = plugin.getConfig().getString("claim-block-rewards.command",
                "claimblocks give %player% %amount%");
        if (command == null || command.isBlank()) return;
        command = command
                .replace("%player%", player.getName())
                .replace("%uuid%", player.getUniqueId().toString())
                .replace("%amount%", String.valueOf(amount))
                .replace("%breaks%", String.valueOf(total));
        try {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
            plugin.progression().save(player.getUniqueId());
            if (plugin.getConfig().getBoolean("claim-block-rewards.actionbar", true)) {
                Msg.actionBar(player, "<green>+" + amount + " claim blocks <gray>(" + total + " OneBlocks mined)");
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("Claim-block reward command failed: " + command);
        }
    }
}
