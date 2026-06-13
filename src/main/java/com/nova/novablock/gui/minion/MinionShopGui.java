package com.nova.novablock.gui.minion;

import com.nova.novablock.NovaBlock;
import com.nova.novablock.gui.ChestGui;
import com.nova.novablock.island.Island;
import com.nova.novablock.minion.MinionType;
import com.nova.novablock.util.ItemBuilder;
import com.nova.novablock.util.Msg;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import java.util.ArrayList;
import java.util.List;

public class MinionShopGui extends ChestGui {
    private final NovaBlock plugin;
    public MinionShopGui(NovaBlock plugin) { super("<dark_gray>Minion Shop", 5); this.plugin = plugin; }
    @Override protected void build(Player player) {
        fill(Material.GRAY_STAINED_GLASS_PANE, " ");
        Island island = plugin.islands().ofPlayer(player);
        int phase = island == null ? -1 : island.data().getPhaseIndex();
        set(4, ItemBuilder.of(Material.NETHER_STAR).name("<gold>Minion Shop").lore("<gray>Buy phase-unlocked minions.").build(), null);
        int[] slots = {10,11,12,13,14,15,16,19,20,21,22,23};
        List<MinionType> types = new ArrayList<>();
        for (MinionType t : MinionType.values()) if (!t.community()) types.add(t);
        for (int i = 0; i < types.size() && i < slots.length; i++) {
            MinionType type = types.get(i);
            boolean unlocked = island != null && type.unlocked(phase);
            boolean enabled = plugin.minions().isShopEnabled(type);
            long price = type.shopPrice(plugin);
            List<String> lore = new ArrayList<>();
            lore.add("<gray>Requires: <yellow>" + type.phaseName(plugin));
            lore.add("<gray>Price: <yellow>" + price + " coins");
            lore.add(!enabled ? "<red>Disabled in shop." : unlocked ? "<green>Click to buy." : "<red>Locked by phase.");
            set(slots[i], ItemBuilder.of(unlocked && enabled ? type.displayMaterial() : Material.BARRIER).name((unlocked && enabled ? "<gold>" : "<red>") + type.displayName()).lore(lore).hideFlags().build(), e -> buy(player, type, unlocked, enabled, price));
        }
        set(40, ItemBuilder.of(Material.MAP).name("<aqua>Overview").build(), e -> new MinionOverviewGui(plugin).open(player));
    }
    private void buy(Player player, MinionType type, boolean unlocked, boolean enabled, long price) {
        if (!player.hasPermission("novablock.minions.buy")) { Msg.send(player, "<red>You don't have permission to buy minions."); return; }
        if (!enabled) { Msg.send(player, "<red>That minion is disabled in the shop."); return; }
        if (!unlocked && !player.hasPermission("novablock.minions.admin")) { Msg.send(player, "<red>Your island has not unlocked this minion."); return; }
        var item = type.createItem(plugin, 1);
        if (!plugin.minions().hasInventoryRoom(player.getInventory(), item)) { Msg.send(player, "<red>Make room in your inventory first."); return; }
        if (!plugin.economy().spend(player, price)) { Msg.send(player, "<red>You need <yellow>" + price + " coins<red>."); return; }
        player.getInventory().addItem(item);
        open(player);
    }
}
