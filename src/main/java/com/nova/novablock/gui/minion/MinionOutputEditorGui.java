package com.nova.novablock.gui.minion;

import com.nova.novablock.NovaBlock;
import com.nova.novablock.gui.ChestGui;
import com.nova.novablock.minion.MinionDrop;
import com.nova.novablock.minion.MinionType;
import com.nova.novablock.util.ItemBuilder;
import com.nova.novablock.util.Msg;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import java.util.List;

public class MinionOutputEditorGui extends ChestGui {
    private final NovaBlock plugin;
    private final MinionType type;
    public MinionOutputEditorGui(NovaBlock plugin, MinionType type) { super("<dark_gray>" + type.displayName() + " Outputs", 6); this.plugin = plugin; this.type = type; }
    @Override protected void build(Player player) {
        fill(Material.BLACK_STAINED_GLASS_PANE, " ");
        set(4, ItemBuilder.of(type.displayMaterial()).name("<gold>" + type.displayName()).lore("<gray>Total weight: <white>" + totalWeight(), "<gray>Shop: " + (plugin.minions().isShopEnabled(type) ? "<green>Enabled" : "<red>Disabled")).hideFlags().build(), null);
        List<MinionDrop> drops = plugin.minions().drops(type);
        for (int i = 0; i < Math.min(36, drops.size()); i++) {
            MinionDrop drop = drops.get(i);
            set(i + 9, ItemBuilder.of(drop.material()).name("<yellow>" + plugin.minions().pretty(drop.material())).lore("<gray>Weight: <white>" + drop.weight(), "<gray>Amount: <white>" + drop.minAmount() + "-" + drop.maxAmount(), "<gray>Rare cap: <white>" + Math.round(drop.rareChance() * 10000.0) / 100.0 + "%", "<green>Left: +1 weight", "<red>Right: -1 weight", "<yellow>Shift-left: +1 max", "<gray>Shift-right: -1 max").hideFlags().build(), e -> {
                if (e.getClick() == ClickType.SHIFT_LEFT) plugin.minions().adjustDropAmount(type, drop.material(), 0, 1);
                else if (e.getClick() == ClickType.SHIFT_RIGHT) plugin.minions().adjustDropAmount(type, drop.material(), 0, -1);
                else if (e.isRightClick()) plugin.minions().adjustDropWeight(type, drop.material(), -1);
                else plugin.minions().adjustDropWeight(type, drop.material(), 1);
                open(player);
            });
        }
        set(44, ItemBuilder.of(plugin.minions().isShopEnabled(type) ? Material.LIME_CONCRETE : Material.RED_CONCRETE).name(plugin.minions().isShopEnabled(type) ? "<green>Shop Enabled" : "<red>Shop Disabled").build(), e -> { plugin.minions().setShopEnabled(type, !plugin.minions().isShopEnabled(type)); open(player); });
        set(45, ItemBuilder.of(Material.HOPPER).name("<green>Add Held Item").build(), e -> { Material m = heldMaterial(player, e.getCursor()); if (m != null) { plugin.minions().addDrop(type, m, 10); open(player); } });
        set(46, ItemBuilder.of(Material.BARRIER).name("<red>Remove Held Item").build(), e -> { Material m = heldMaterial(player, e.getCursor()); if (m != null) { plugin.minions().removeDrop(type, m); open(player); } });
        set(47, ItemBuilder.of(Material.LIME_DYE).name("<green>Held Max +1").build(), e -> { Material m = heldMaterial(player, e.getCursor()); if (m != null) { plugin.minions().adjustDropAmount(type, m, 0, 1); open(player); } });
        set(48, ItemBuilder.of(Material.RED_DYE).name("<red>Held Max -1").build(), e -> { Material m = heldMaterial(player, e.getCursor()); if (m != null) { plugin.minions().adjustDropAmount(type, m, 0, -1); open(player); } });
        set(49, ItemBuilder.of(Material.ARROW).name("<yellow>Back").build(), e -> new MinionOutputAdminGui(plugin).open(player));
        set(50, ItemBuilder.of(Material.GOLD_NUGGET).name("<gold>Held Rare +1%").build(), e -> { Material m = heldMaterial(player, e.getCursor()); if (m != null) { plugin.minions().adjustDropRareChance(type, m, 0.01); open(player); } });
        set(51, ItemBuilder.of(Material.IRON_NUGGET).name("<gray>Held Rare -1%").build(), e -> { Material m = heldMaterial(player, e.getCursor()); if (m != null) { plugin.minions().adjustDropRareChance(type, m, -0.01); open(player); } });
        set(52, ItemBuilder.of(Material.TNT).name("<red>Reset To Defaults").build(), e -> { plugin.minions().resetOutputTable(type); open(player); });
        set(53, ItemBuilder.of(Material.WRITABLE_BOOK).name("<aqua>Saved To Config").lore("<white>minions.outputs." + type.id()).build(), null);
    }
    private int totalWeight() { int total = 0; for (MinionDrop drop : plugin.minions().drops(type)) total += drop.weight(); return total; }
    private Material heldMaterial(Player player, ItemStack cursor) {
        Material material = cursor != null && !cursor.getType().isAir() ? cursor.getType() : player.getInventory().getItemInMainHand().getType();
        if (material == null || material.isAir()) { Msg.send(player, "<red>Hold or cursor the output item first."); return null; }
        return material;
    }
}
