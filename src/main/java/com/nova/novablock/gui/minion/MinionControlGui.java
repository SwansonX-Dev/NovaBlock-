package com.nova.novablock.gui.minion;

import com.nova.novablock.NovaBlock;
import com.nova.novablock.gui.ChestGui;
import com.nova.novablock.minion.MinionData;
import com.nova.novablock.minion.MinionSkin;
import com.nova.novablock.minion.MinionUpgrade;
import com.nova.novablock.util.ItemBuilder;
import com.nova.novablock.util.Msg;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import java.util.ArrayList;
import java.util.List;

public class MinionControlGui extends ChestGui {
    private final NovaBlock plugin;
    private final java.util.UUID minionId;
    public MinionControlGui(NovaBlock plugin, java.util.UUID minionId) { super("<dark_gray>Minion Control", 5); this.plugin = plugin; this.minionId = minionId; }
    @Override protected void build(Player player) {
        fill(Material.BLACK_STAINED_GLASS_PANE, " ");
        MinionData data = plugin.minions().get(minionId);
        if (data == null) { set(22, ItemBuilder.of(Material.BARRIER).name("<red>Missing Minion").build(), null); return; }
        set(4, ItemBuilder.of(data.type().displayMaterial()).name("<gold>" + data.type().displayName()).lore(statusLore(data)).hideFlags().build(), null);
        set(10, ItemBuilder.of(data.hasLinkedChest() ? Material.CHEST : Material.IRON_CHAIN).name("<yellow>Link Chest").lore("<gray>Click, then punch a chest or barrel.").build(), e -> plugin.minions().beginLink(player, data.id()));
        set(12, ItemBuilder.of(Material.COAL).name("<yellow>Add Fuel").lore("<gray>Click with fuel on cursor or main hand.").build(), e -> {
            ItemStack fuel = e.getCursor();
            if (fuel == null || fuel.getType().isAir()) fuel = player.getInventory().getItemInMainHand();
            if (plugin.minions().addFuel(player, data, fuel)) open(player);
        });
        setUpgrade(player, data, 14, MinionUpgrade.SPEED, Material.SUGAR);
        setUpgrade(player, data, 15, MinionUpgrade.YIELD, Material.HOPPER);
        setUpgrade(player, data, 16, MinionUpgrade.FUEL_EFFICIENCY, Material.BLAZE_POWDER);
        setUpgrade(player, data, 23, MinionUpgrade.COMPACTOR, Material.IRON_BLOCK);
        set(28, ItemBuilder.of(Material.ARMOR_STAND).name("<aqua>Skin").lore("<gray>Current: <white>" + MinionSkin.byId(data.skin()).displayName()).build(), e -> new MinionSkinGui(plugin, data.id()).open(player));
        set(34, ItemBuilder.of(Material.BUNDLE).name("<green>Pick Up").build(), e -> new MinionConfirmGui(plugin, data.id(), MinionConfirmGui.Action.PICKUP).open(player));
        set(36, ItemBuilder.of(Material.ENDER_PEARL).name("<yellow>Move").lore("<gray>Preserves fuel, upgrades, skin, link, and logs.").build(), e -> plugin.minions().beginMove(player, data.id()));
        if (player.hasPermission("novablock.minions.admin") || player.hasPermission("novablock.admin.minions")) {
            set(38, ItemBuilder.of(Material.DISPENSER).name("<gold>Test Roll").build(), e -> {
                ItemStack output = plugin.minions().testRoll(data);
                Msg.send(player, "<gold>Test roll: <yellow>" + output.getAmount() + "x " + plugin.minions().pretty(output.getType()));
            });
        }
        set(40, ItemBuilder.of(Material.REDSTONE_BLOCK).name("<red>Delete").build(), e -> new MinionConfirmGui(plugin, data.id(), MinionConfirmGui.Action.DELETE).open(player));
        set(44, ItemBuilder.of(Material.MAP).name("<aqua>Overview").build(), e -> new MinionOverviewGui(plugin).open(player));
    }
    private void setUpgrade(Player player, MinionData data, int slot, MinionUpgrade upgrade, Material material) {
        int current = data.upgrade(upgrade);
        set(slot, ItemBuilder.of(material).name("<yellow>" + upgrade.displayName()).lore("<gray>Level: <white>" + current + "/" + upgrade.maxLevel(), current >= upgrade.maxLevel() ? "<green>Maxed." : "<gray>Cost: <yellow>" + plugin.minions().upgradeCost(upgrade, current + 1)).hideFlags().build(), e -> { if (plugin.minions().upgrade(player, data, upgrade)) open(player); });
    }
    private List<String> statusLore(MinionData data) {
        List<String> lore = new ArrayList<>();
        lore.add("<gray>Status: " + data.status().color() + data.status().displayName());
        lore.add("<gray>Linked: <white>" + (data.hasLinkedChest() ? data.linkedX() + ", " + data.linkedY() + ", " + data.linkedZ() : "None"));
        lore.add("<gray>Fuel: <white>" + (data.fuelTicksRemaining() / 20 / 60) + "m");
        if (!data.productionLog().isEmpty()) { lore.add("<gray>Recent:"); for (String log : data.productionLog()) lore.add("<dark_gray>- <white>" + log); }
        return lore;
    }
}
