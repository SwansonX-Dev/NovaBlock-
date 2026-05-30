package com.nova.novablock.gui;

import com.nova.novablock.NovaBlock;
import com.nova.novablock.sprint.WeeklySprintManager;
import com.nova.novablock.util.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class SprintHistoryGui extends ChestGui {
    private final NovaBlock plugin;

    public SprintHistoryGui(NovaBlock plugin) {
        super("<gold><bold>Last Sprint Winners", 4);
        this.plugin = plugin;
    }

    @Override
    protected void build(Player player) {
        fill(Material.GRAY_STAINED_GLASS_PANE, " ");

        List<WeeklySprintManager.WinnerRow> winners = plugin.sprint().lastWinners();
        if (winners.isEmpty()) {
            set(13, ItemBuilder.of(Material.PAPER)
                    .name("<gray>No completed sprint yet")
                    .lore("<gray>Winners appear after a podium broadcast.")
                    .build(), null);
        } else {
            int[] slots = {10, 11, 12, 14, 15, 16};
            for (int i = 0; i < winners.size() && i < slots.length; i++) {
                WeeklySprintManager.WinnerRow row = winners.get(i);
                Material icon = switch (row.place()) {
                    case 1 -> Material.NETHER_STAR;
                    case 2 -> Material.DIAMOND_BLOCK;
                    case 3 -> Material.GOLD_BLOCK;
                    default -> Material.IRON_BLOCK;
                };
                List<String> lore = new ArrayList<>();
                lore.add("<gray>Board: <white>" + SprintGui.prettyBoard(row.board()));
                lore.add("<gray>Score: <white>" + SprintGui.formatNumber(row.score()));
                if (row.coins() > 0) lore.add("<gray>Coins: <gold>" + SprintGui.formatNumber(row.coins()));
                set(slots[i], ItemBuilder.of(icon)
                        .name("<gold>#" + row.place() + " <yellow>" + row.name())
                        .lore(lore)
                        .build(), null);
            }
        }

        set(31, ItemBuilder.of(Material.ARROW).name("<gray>Back").build(),
                e -> new SprintGui(plugin).open(player));
    }
}
