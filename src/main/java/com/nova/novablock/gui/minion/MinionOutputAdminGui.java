package com.nova.novablock.gui.minion;

import com.nova.novablock.NovaBlock;
import com.nova.novablock.gui.ChestGui;
import com.nova.novablock.minion.MinionType;
import com.nova.novablock.util.ItemBuilder;
import com.nova.novablock.util.Msg;
import org.bukkit.Material;
import org.bukkit.entity.Player;

public class MinionOutputAdminGui extends ChestGui {
    private final NovaBlock plugin;
    public MinionOutputAdminGui(NovaBlock plugin) { super("<dark_gray>Minion Output Admin", 4); this.plugin = plugin; }
    @Override protected void build(Player player) {
        fill(Material.GRAY_STAINED_GLASS_PANE, " ");
        set(4, ItemBuilder.of(Material.WRITABLE_BOOK).name("<gold>Minion Output Tables").build(), null);
        int[] slots = {10,11,12,13,14,15,16,19,20,21,22,23};
        MinionType[] types = MinionType.values();
        for (int i = 0; i < types.length; i++) {
            MinionType type = types[i];
            set(slots[i], ItemBuilder.of(type.displayMaterial()).name("<yellow>" + type.displayName()).lore("<gray>Outputs: <white>" + plugin.minions().drops(type).size()).hideFlags().build(), e -> new MinionOutputEditorGui(plugin, type).open(player));
        }
        set(31, ItemBuilder.of(Material.COMPARATOR).name("<aqua>Reload Output Tables").build(), e -> { plugin.minions().reloadOutputTables(); Msg.send(player, "<green>Reloaded minion output tables."); open(player); });
    }
}
