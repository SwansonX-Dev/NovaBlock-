package com.nova.novablock.gui.minion;

import com.nova.novablock.NovaBlock;
import com.nova.novablock.gui.ChestGui;
import com.nova.novablock.island.Island;
import com.nova.novablock.minion.MinionData;
import com.nova.novablock.util.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import java.util.ArrayList;
import java.util.List;

public class MinionOverviewGui extends ChestGui {
    private final NovaBlock plugin;
    public MinionOverviewGui(NovaBlock plugin) { super("<dark_gray>Island Minions", 6); this.plugin = plugin; }
    @Override protected void build(Player player) {
        fill(Material.GRAY_STAINED_GLASS_PANE, " ");
        Island island = plugin.islands().ofPlayer(player);
        if (island == null) { set(22, ItemBuilder.of(Material.BARRIER).name("<red>No Island").build(), null); return; }
        List<MinionData> minions = plugin.minions().ofIsland(island.data().getId());
        int limit = plugin.minions().limit(player, island);
        set(4, ItemBuilder.of(Material.NETHER_STAR).name("<gold>Island Minions").lore("<gray>Placed: <white>" + minions.size() + "/" + (limit == Integer.MAX_VALUE ? "∞" : limit)).build(), null);
        for (int i = 0; i < Math.min(36, minions.size()); i++) {
            MinionData data = minions.get(i);
            List<String> lore = new ArrayList<>();
            lore.add("<gray>Status: " + data.status().color() + data.status().displayName());
            lore.add("<gray>Fuel: <white>" + (data.fuelTicksRemaining() / 20 / 60) + "m");
            if (!data.productionLog().isEmpty()) { lore.add("<gray>Recent:"); for (String log : data.productionLog()) lore.add("<dark_gray>- <white>" + log); }
            set(i + 9, ItemBuilder.of(data.type().displayMaterial()).name("<yellow>" + data.type().displayName()).lore(lore).hideFlags().build(), e -> new MinionControlGui(plugin, data.id()).open(player));
        }
        set(45, ItemBuilder.of(Material.EMERALD).name("<green>Shop").build(), e -> new MinionShopGui(plugin).open(player));
        set(49, ItemBuilder.of(Material.BOOK).name("<aqua>Help").build(), e -> new MinionHelpGui(plugin).open(player));
        if (player.hasPermission("novablock.minions.admin") || player.hasPermission("novablock.admin.minions")) set(53, ItemBuilder.of(Material.WRITABLE_BOOK).name("<aqua>Output Admin").build(), e -> new MinionOutputAdminGui(plugin).open(player));
    }
}
