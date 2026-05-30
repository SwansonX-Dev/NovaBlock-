package com.nova.novablock.gui.minion;

import com.nova.novablock.NovaBlock;
import com.nova.novablock.gui.ChestGui;
import com.nova.novablock.minion.MinionDrop;
import com.nova.novablock.minion.MinionType;
import com.nova.novablock.util.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class MinionTypesGui extends ChestGui {
    private final NovaBlock plugin;

    public MinionTypesGui(NovaBlock plugin) {
        super("<dark_gray>Minion Types", 5);
        this.plugin = plugin;
    }

    @Override protected void build(Player player) {
        fill(Material.GRAY_STAINED_GLASS_PANE, " ");
        set(4, ItemBuilder.of(Material.BOOK).name("<gold>Minion Types").lore("<gray>Unlocks, prices, and configured drops.").build(), null);
        int[] slots = {10,11,12,13,14,15,16,19,20,21,22,23};
        MinionType[] types = MinionType.values();
        for (int i = 0; i < types.length; i++) {
            MinionType type = types[i];
            List<String> lore = new ArrayList<>();
            lore.add("<gray>Unlock: <yellow>" + type.phaseName(plugin));
            lore.add("<gray>Price: <yellow>" + type.shopPrice(plugin) + " coins");
            lore.add("<gray>Shop: " + (plugin.minions().isShopEnabled(type) ? "<green>Enabled" : "<red>Disabled"));
            lore.add("<gray>Interval: <white>" + type.baseIntervalSeconds() + "s");
            lore.add("");
            int shown = 0;
            for (MinionDrop drop : plugin.minions().drops(type)) {
                if (shown++ >= 5) { lore.add("<dark_gray>..."); break; }
                lore.add("<dark_gray>- <white>" + plugin.minions().pretty(drop.material()) + " <gray>w" + drop.weight());
            }
            set(slots[i], ItemBuilder.of(type.displayMaterial()).name("<yellow>" + type.displayName()).lore(lore).hideFlags().build(), null);
        }
        set(40, ItemBuilder.of(Material.ARROW).name("<yellow>Overview").build(), e -> new MinionOverviewGui(plugin).open(player));
    }
}
