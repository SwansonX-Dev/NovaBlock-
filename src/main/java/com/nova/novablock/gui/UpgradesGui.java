package com.nova.novablock.gui;

import com.nova.novablock.NovaBlock;
import com.nova.novablock.island.Island;
import com.nova.novablock.island.IslandUpgrade;
import com.nova.novablock.util.ItemBuilder;
import com.nova.novablock.util.Msg;
import org.bukkit.Material;
import org.bukkit.entity.Player;

public class UpgradesGui extends ChestGui {

    private final NovaBlock plugin;

    public UpgradesGui(NovaBlock plugin) {
        super("<gold><bold>Island Upgrades", 3);
        this.plugin = plugin;
    }

    @Override
    protected void build(Player p) {
        Island island = plugin.islands().ofPlayer(p);
        if (island == null) return;
        // Upgrades are funded by the shared island bank, spendable by owner + co-owners.
        long coins = island.data().getBankBalance();
        boolean canSpend = island.roleOf(p).canSpendBank();

        set(4, ItemBuilder.of(Material.GOLD_INGOT)
                .name("<gold>Island Bank: <yellow>" + plugin.economy().format(coins) + " coins")
                .lore(canSpend
                                ? "<gray>Upgrades are paid from the island bank."
                                : "<red>Only the owner or a co-owner can buy upgrades.",
                        "<dark_gray>Deposit with /ob bank deposit <amount>")
                .build(), null);

        int[] slots = {11, 13, 15, 22};
        IslandUpgrade[] upgrades = IslandUpgrade.values();
        for (int i = 0; i < upgrades.length && i < slots.length; i++) {
            IslandUpgrade up = upgrades[i];
            int level = island.data().getUpgradeLevel(up);
            long nextCost = up.costFor(level);
            boolean maxed = nextCost < 0;

            ItemBuilder ib = ItemBuilder.of(up.icon)
                    .name("<gold>" + up.displayName + " <gray>Lv " + level + "/" + up.maxLevel)
                    .lore("<gray>" + up.description)
                    .lore(" ")
                    .lore("<aqua>Now: <white>" + up.currentEffect(level));
            if (maxed) {
                ib.lore("<green>Maxed.").glow();
            } else {
                ib.lore("<light_purple>Next: <white>" + up.nextEffect(level));
                ib.lore(" ");
                String afford = coins >= nextCost ? "<yellow>" : "<red>";
                ib.lore("<gray>Cost: " + afford + nextCost + " coins");
                ib.lore("<dark_gray>Click to purchase.");
            }
            set(slots[i], ib.build(), e -> tryPurchase(p, island, up));
        }

        set(26, ItemBuilder.of(Material.ARROW)
                        .name("<gray>← Back to menu").build(),
                e -> new MainMenuGui(plugin).open(p));

        fill(Material.BLACK_STAINED_GLASS_PANE, " ");
    }

    private void tryPurchase(Player p, Island island, IslandUpgrade up) {
        if (!island.roleOf(p).canSpendBank()) {
            Msg.actionBar(p, "<red>Only the owner or a co-owner can buy upgrades.");
            return;
        }
        int level = island.data().getUpgradeLevel(up);
        long cost = up.costFor(level);
        if (cost < 0) {
            Msg.actionBar(p, "<gray>Already maxed.");
            return;
        }
        if (!plugin.islands().bankSpend(island, cost)) {
            Msg.actionBar(p, "<red>The island bank can't cover that. <gray>/ob bank deposit <amount>");
            return;
        }
        island.data().setUpgradeLevel(up, level + 1);
        plugin.storage().saveIsland(island.data());
        Msg.send(p, "<green>Upgraded <yellow>" + up.displayName + "<green> to Lv " + (level + 1) + ".");
        p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_ANVIL_USE, 0.8f, 1.3f);
        open(p);
    }
}
