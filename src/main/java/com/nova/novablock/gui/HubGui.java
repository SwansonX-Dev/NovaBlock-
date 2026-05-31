package com.nova.novablock.gui;

import com.nova.novablock.NovaBlock;
import com.nova.novablock.community.CommunityHubManager;
import com.nova.novablock.community.WeeklyGoal;
import com.nova.novablock.phase.Phase;
import com.nova.novablock.util.ItemBuilder;
import com.nova.novablock.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Single-page hub overview: community block phase + pool, weekly goal progress,
 * next raid countdown, top contributors, friends online. Opened via {@code /ob hub}.
 */
public class HubGui extends ChestGui {

    private final NovaBlock plugin;

    public HubGui(NovaBlock plugin) {
        super("<gradient:#FF6B6B:#FFC940><bold>Community Hub", 5);
        this.plugin = plugin;
    }

    @Override
    protected void build(Player viewer) {
        CommunityHubManager hub = plugin.community();
        if (hub == null || !plugin.getConfig().getBoolean("community.enabled", true)) {
            set(22, ItemBuilder.of(Material.BARRIER)
                    .name("<red>Community features disabled")
                    .lore("<gray>Ask an operator to enable <yellow>community.enabled</yellow> in config.yml.")
                    .build(), null);
            fill(Material.GRAY_STAINED_GLASS_PANE, " ");
            return;
        }

        // --- Tile: community block status ---
        Phase phase = plugin.phases().getOrLast(hub.block().phaseIndex());
        String phaseColor = phase == null ? "white" : phase.getThemeColor();
        String phaseName = phase == null ? "?" : phase.getDisplayName();
        int phaseProg = hub.block().phaseProgress();
        int phaseReq = phase == null ? 1 : phase.getRequiredBlocks();
        long pool = hub.block().pool();
        set(11, ItemBuilder.of(Material.NETHER_STAR)
                .name("<gold><bold>Community OneBlock")
                .lore(
                        "<gray>Phase: <" + phaseColor + ">" + phaseName,
                        "<gray>Progress: <white>" + phaseProg + " / " + phaseReq,
                        "<gray>Total mined: <white>" + hub.block().blocksBroken(),
                        "",
                        "<gold>Pool: <yellow>" + pool + "<gold> coins",
                        "<dark_gray>Pays out every "
                                + plugin.getConfig().getLong("community.block.payout.interval-minutes", 60) + "m"
                                + " or " + plugin.getConfig().getLong("community.block.payout.block-threshold", 500) + " blocks.")
                .glow().build(), null);

        // --- Tile: weekly goal ---
        WeeklyGoal goal = hub.goal();
        long progress = goal.progress();
        long target = goal.target();
        int pct = (int) Math.min(100, (progress * 100L) / Math.max(1, target));
        long remainingMs = Math.max(0, goal.windowEnd() - System.currentTimeMillis());
        long myContrib = goal.contributionByPlayer().getOrDefault(viewer.getUniqueId(), 0L);
        set(13, ItemBuilder.of(Material.MAP)
                .name("<aqua><bold>Weekly Goal")
                .lore(
                        "<gray>Mine <yellow>" + target + "<gray> blocks together this week",
                        "",
                        progressBar(pct, 20) + " <white>" + pct + "%",
                        "<gray>" + progress + " / " + target,
                        "",
                        "<gray>You: <white>" + myContrib + " <gray>blocks",
                        "<dark_gray>Resets in " + formatDuration(remainingMs))
                .build(), null);

        // --- Tile: next raid ---
        long nextRaid = Math.max(0, hub.raids().nextRaidAt() - System.currentTimeMillis());
        boolean raidsEnabled = plugin.getConfig().getBoolean("community.raids.enabled", true);
        set(15, ItemBuilder.of(Material.NETHERITE_SWORD)
                .name("<red><bold>Next Raid")
                .lore(raidsEnabled
                        ? new String[]{
                            "<gray>A scaled boss spawns at the hub",
                            "<gray>every " + plugin.getConfig().getLong("community.raids.interval-minutes", 120) + "m",
                            "",
                            "<yellow>Next: <white>" + (nextRaid == 0 ? "any moment" : formatDuration(nextRaid)),
                            "<dark_gray>Damage = your share of the loot."
                          }
                        : new String[]{"<dark_gray>Raids are disabled in config."})
                .build(), null);

        // --- Tile: top contributors (weekly) ---
        var top = goal.topContributors(5);
        List<String> lore = new ArrayList<>();
        if (top.isEmpty()) {
            lore.add("<dark_gray>No contributors yet this week.");
        } else {
            int rank = 1;
            for (Map.Entry<UUID, Long> e : top) {
                OfflinePlayer op = Bukkit.getOfflinePlayer(e.getKey());
                String name = op.getName() == null ? "Unknown" : op.getName();
                lore.add("<gray>" + rank + ". <yellow>" + name + " <gray>– <white>" + e.getValue());
                rank++;
            }
        }
        set(29, ItemBuilder.of(Material.GOLDEN_HELMET)
                .name("<gold><bold>Top Contributors")
                .lore(lore.toArray(new String[0]))
                .hideFlags().build(), null);

        // --- Tile: online friends ---
        var onlineFriends = plugin.friends().onlineFriends(viewer.getUniqueId());
        ItemStack friendsItem;
        if (onlineFriends.isEmpty()) {
            friendsItem = ItemBuilder.of(Material.PLAYER_HEAD)
                    .name("<aqua><bold>Friends Online")
                    .lore("<dark_gray>No friends online right now.").build();
        } else {
            List<String> friendLore = new ArrayList<>();
            for (Player f : onlineFriends) friendLore.add("<green>● <white>" + f.getName());
            friendLore.add("");
            friendLore.add("<yellow>Click to open <white>/ob friends");
            friendsItem = ItemBuilder.of(Material.PLAYER_HEAD)
                    .name("<aqua><bold>Friends Online <gray>(" + onlineFriends.size() + ")")
                    .lore(friendLore.toArray(new String[0])).build();
            if (friendsItem.getItemMeta() instanceof SkullMeta sm) {
                sm.setOwningPlayer(onlineFriends.get(0));
                friendsItem.setItemMeta(sm);
            }
        }
        set(33, friendsItem, e -> {
            viewer.closeInventory();
            viewer.performCommand("ob friends");
        });

        // --- Teleport to hub tile ---
        set(40, ItemBuilder.of(Material.ENDER_PEARL)
                .name("<gold>Teleport to Hub")
                .lore("<gray>Take me to <yellow>/warp community<gray>.").build(),
                e -> { viewer.closeInventory(); viewer.performCommand("warp community"); });

        fill(Material.BLACK_STAINED_GLASS_PANE, " ");
    }

    private static String progressBar(int percent, int width) {
        int filled = Math.max(0, Math.min(width, percent * width / 100));
        StringBuilder sb = new StringBuilder("<dark_gray>[<green>");
        for (int i = 0; i < filled; i++) sb.append("|");
        sb.append("<dark_gray>");
        for (int i = filled; i < width; i++) sb.append("|");
        sb.append("<dark_gray>]");
        return sb.toString();
    }

    private static String formatDuration(long ms) {
        long s = ms / 1000;
        long d = s / 86400;
        long h = (s % 86400) / 3600;
        long m = (s % 3600) / 60;
        if (d > 0) return d + "d " + h + "h";
        if (h > 0) return h + "h " + m + "m";
        return m + "m";
    }
}
