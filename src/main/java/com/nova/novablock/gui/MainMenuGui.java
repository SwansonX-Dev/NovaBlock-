package com.nova.novablock.gui;

import com.nova.novablock.NovaBlock;
import com.nova.novablock.util.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * Hub menu. Top row is NovaBlock-native (island features, skills, quests).
 * Bottom row routes to xEconomy commands — NovaBlock owns the gameplay,
 * xEconomy owns the money. Each button performs the underlying command so
 * permissions and UI live with the plugin that owns the feature.
 */
public class MainMenuGui extends ChestGui {

    private final NovaBlock plugin;

    public MainMenuGui(NovaBlock plugin) {
        super("<gradient:#7B61FF:#4FC3F7><bold>NovaBlock", 4);
        this.plugin = plugin;
    }

    @Override
    protected void build(Player p) {
        // Row 1: gameplay features
        set(11, ItemBuilder.of(Material.GRASS_BLOCK)
                        .name("<green>Teleport Home")
                        .lore("<gray>Warp to your OneBlock.").build(),
                e -> { p.closeInventory(); var i = plugin.islands().ofPlayer(p); if (i != null) i.teleportHome(p); });

        set(12, ItemBuilder.of(Material.SPYGLASS)
                        .name("<aqua>Prophecy")
                        .lore("<gray>See and lock the next 10 blocks.").build(),
                e -> new ProphecyGui(plugin).open(p));

        set(13, ItemBuilder.of(Material.ENCHANTING_TABLE)
                        .name("<light_purple>Skills")
                        .lore("<gray>Spend XP on perks.").build(),
                e -> new SkillsGui(plugin).open(p));

        set(14, ItemBuilder.of(Material.WRITTEN_BOOK)
                        .name("<yellow>Daily Quest")
                        .lore("<gray>Check today's challenge.").build(),
                e -> new QuestGui(plugin).open(p));

        set(15, ItemBuilder.of(Material.PAPER)
                        .name("<white>Leaderboard")
                        .lore("<gray>Top islands by blocks broken.").build(),
                e -> new LeaderboardGui(plugin).open(p));

        // Row 2: xEconomy bridges — each button closes this menu and runs the
        // matching xEconomy command. xEconomy handles permissions + UI.
        set(20, ItemBuilder.of(Material.EMERALD)
                        .name("<green>Shop")
                        .lore("<gray>Browse the dynamic market.", "<dark_gray>/shop").build(),
                e -> { p.closeInventory(); p.performCommand("shop"); });

        set(21, ItemBuilder.of(Material.GOLD_INGOT)
                        .name("<gold>Sell Items")
                        .lore("<gray>Sell what's in your hand or inventory.", "<dark_gray>/sell").build(),
                e -> { p.closeInventory(); p.performCommand("sell"); });

        set(22, ItemBuilder.of(Material.CHEST)
                        .name("<yellow>Sell Chests")
                        .lore("<gray>Auto-sell chests on your island.", "<dark_gray>/sellchest").build(),
                e -> { p.closeInventory(); p.performCommand("sellchest"); });

        set(23, ItemBuilder.of(Material.GOLD_BLOCK)
                        .name("<#FFC940>Bank")
                        .lore("<gray>Deposit coins to earn interest.", "<dark_gray>/bank").build(),
                e -> { p.closeInventory(); p.performCommand("bank"); });

        set(24, ItemBuilder.of(Material.COMPASS)
                        .name("<aqua>Stock Exchange")
                        .lore("<gray>Buy and sell stocks.", "<dark_gray>/stocks").build(),
                e -> { p.closeInventory(); p.performCommand("stocks"); });

        fill(Material.GRAY_STAINED_GLASS_PANE, " ");
    }
}
