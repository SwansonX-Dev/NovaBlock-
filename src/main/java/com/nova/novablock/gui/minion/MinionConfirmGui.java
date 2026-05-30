package com.nova.novablock.gui.minion;

import com.nova.novablock.NovaBlock;
import com.nova.novablock.gui.ChestGui;
import com.nova.novablock.minion.MinionData;
import com.nova.novablock.util.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.entity.Player;

public class MinionConfirmGui extends ChestGui {
    public enum Action { PICKUP, DELETE }
    private final NovaBlock plugin;
    private final java.util.UUID minionId;
    private final Action action;
    public MinionConfirmGui(NovaBlock plugin, java.util.UUID minionId, Action action) {
        super("<dark_gray>Confirm", 3); this.plugin = plugin; this.minionId = minionId; this.action = action;
    }
    @Override protected void build(Player player) {
        fill(Material.BLACK_STAINED_GLASS_PANE, " ");
        MinionData data = plugin.minions().get(minionId);
        if (data == null) { set(13, ItemBuilder.of(Material.BARRIER).name("<red>Missing Minion").build(), null); return; }
        set(11, ItemBuilder.of(Material.LIME_CONCRETE).name("<green>Confirm").build(), e -> { if (action == Action.PICKUP) plugin.minions().pickup(player, data); else plugin.minions().delete(player, data); });
        set(13, ItemBuilder.of(action == Action.PICKUP ? Material.BUNDLE : Material.REDSTONE_BLOCK).name("<yellow>" + action + " " + data.type().displayName()).build(), null);
        set(15, ItemBuilder.of(Material.RED_CONCRETE).name("<red>Cancel").build(), e -> new MinionControlGui(plugin, minionId).open(player));
    }
}
