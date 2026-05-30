package com.nova.novablock.gui.minion;

import com.nova.novablock.NovaBlock;
import com.nova.novablock.gui.ChestGui;
import com.nova.novablock.island.Island;
import com.nova.novablock.minion.MinionData;
import com.nova.novablock.util.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class MinionDebugGui extends ChestGui {
    private final NovaBlock plugin;
    private final java.util.UUID playerId;

    public MinionDebugGui(NovaBlock plugin, java.util.UUID playerId) {
        super("<dark_gray>Minion Debug", 6);
        this.plugin = plugin;
        this.playerId = playerId;
    }

    @Override protected void build(Player viewer) {
        fill(Material.GRAY_STAINED_GLASS_PANE, " ");
        OfflinePlayer target = Bukkit.getOfflinePlayer(playerId);
        Island island = plugin.islands().ofPlayer(playerId);
        if (island == null) {
            set(22, ItemBuilder.of(Material.BARRIER).name("<red>No Island").lore("<gray>" + (target.getName() == null ? playerId.toString() : target.getName())).build(), null);
            return;
        }
        List<MinionData> minions = plugin.minions().ofIsland(island.data().getId());
        set(4, ItemBuilder.of(Material.SPYGLASS)
                .name("<gold>Debug: " + (target.getName() == null ? playerId.toString().substring(0, 8) : target.getName()))
                .lore("<gray>Island: <white>" + island.data().getId(),
                        "<gray>Count: <white>" + minions.size(),
                        "<gray>Viewer limit: <white>" + plugin.minions().limit(viewer, island))
                .build(), null);
        for (int i = 0; i < Math.min(36, minions.size()); i++) {
            MinionData data = minions.get(i);
            List<String> lore = new ArrayList<>();
            lore.add("<gray>ID: <white>" + data.id());
            lore.add("<gray>Status: " + data.status().color() + data.status().displayName());
            lore.add("<gray>Loc: <white>" + blockText(data.worldName(), data.x(), data.y(), data.z()));
            lore.add("<gray>Chest: <white>" + (data.hasLinkedChest() ? blockText(data.linkedWorldName(), data.linkedX(), data.linkedY(), data.linkedZ()) : "None"));
            lore.add("<gray>Fuel: <white>" + data.fuelTicksRemaining() / 20 + "s");
            lore.add("<gray>Skin: <white>" + data.skin());
            set(i + 9, ItemBuilder.of(data.type().displayMaterial()).name("<yellow>" + data.type().displayName()).lore(lore).hideFlags().build(), e -> new MinionControlGui(plugin, data.id()).open(viewer));
        }
        set(49, ItemBuilder.of(Material.ARROW).name("<yellow>Back").build(), e -> new MinionOverviewGui(plugin).open(viewer));
    }

    private String blockText(String world, double x, double y, double z) {
        return world + " " + (int) Math.floor(x) + "," + (int) Math.floor(y) + "," + (int) Math.floor(z);
    }
}
