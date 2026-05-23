package com.nova.novablock.gui;

import com.nova.novablock.NovaBlock;
import com.nova.novablock.island.Island;
import com.nova.novablock.util.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class LeaderboardGui extends ChestGui {

    private final NovaBlock plugin;

    public LeaderboardGui(NovaBlock plugin) {
        super("<white><bold>Leaderboard", 6);
        this.plugin = plugin;
    }

    @Override
    protected void build(Player viewer) {
        List<Island> sorted = plugin.islands().all().values().stream()
                .sorted(Comparator.comparingLong((Island i) -> i.data().getBlocksBroken()).reversed())
                .limit(45)
                .collect(Collectors.toList());

        Material[] medals = {Material.NETHER_STAR, Material.DIAMOND_BLOCK, Material.GOLD_BLOCK};
        for (int i = 0; i < sorted.size(); i++) {
            Island island = sorted.get(i);
            Material icon = i < medals.length ? medals[i] : Material.IRON_BLOCK;
            String owner = ownerName(island);
            set(i, ItemBuilder.of(icon)
                    .name("<gold>#" + (i + 1) + " <yellow>" + owner)
                    .lore("<gray>Blocks broken: <white>" + island.data().getBlocksBroken(),
                            "<gray>Phase: <white>" + (island.data().getPhaseIndex() + 1),
                            "<gray>Coins: <yellow>" + plugin.economy().balance(island))
                    .build(), null);
        }
        set(53, ItemBuilder.of(Material.ARROW).name("<gray>Back").build(),
                e -> new MainMenuGui(plugin).open(viewer));
    }

    private String ownerName(Island island) {
        var op = Bukkit.getOfflinePlayer(island.data().getOwner());
        return op.getName() == null ? "Unknown" : op.getName();
    }
}
