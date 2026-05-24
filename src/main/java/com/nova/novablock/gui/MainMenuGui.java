package com.nova.novablock.gui;

import com.nova.novablock.NovaBlock;
import com.nova.novablock.util.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * Hub menu. Row 1 = NovaBlock-native gameplay, row 2 = xEconomy bridges,
 * row 3 = island settings + storage, row 4 = admin-defined custom buttons
 * (driven by {@link MainMenuConfig}).
 */
public class MainMenuGui extends ChestGui {

    private final NovaBlock plugin;

    public MainMenuGui(NovaBlock plugin) {
        super("<gradient:#7B61FF:#4FC3F7><bold>NovaBlock", 5);
        this.plugin = plugin;
    }

    @Override
    protected void build(Player p) {
        // Row 1: gameplay
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

        // Row 2: xEconomy bridges
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
                        .lore("<gray>Buy and manage island auto-sell chests.", "<dark_gray>/sellchest").build(),
                e -> { p.closeInventory(); p.performCommand("sellchest"); });

        set(23, ItemBuilder.of(Material.GOLD_BLOCK)
                        .name("<#FFC940>Bank")
                        .lore("<gray>Deposit coins to earn interest.", "<dark_gray>/bank").build(),
                e -> { p.closeInventory(); p.performCommand("bank"); });

        set(24, ItemBuilder.of(Material.COMPASS)
                        .name("<aqua>Stock Exchange")
                        .lore("<gray>Buy and sell stocks.", "<dark_gray>/stocks").build(),
                e -> { p.closeInventory(); p.performCommand("stocks"); });

        // Row 3: island settings + storage
        set(30, ItemBuilder.of(Material.COMPARATOR)
                        .name("<#9C27B0>Island Flags")
                        .lore("<gray>Toggle PVP, fly, mob spawning, and more.", "<dark_gray>/ob flags").build(),
                e -> new IslandFlagsGui(plugin).open(p));

        set(32, ItemBuilder.of(Material.ENDER_CHEST)
                        .name("<#FFC940>Island Storage")
                        .lore("<gray>Shared 54-slot chest for your island.", "<dark_gray>/ob storage").build(),
                e -> {
                    p.closeInventory();
                    com.nova.novablock.island.IslandStorageManager.tryOpen(plugin, p);
                });

        set(31, ItemBuilder.of(Material.ALLAY_SPAWN_EGG)
                        .name("<aqua>Companion")
                        .lore("<gray>Gather materials, recover void items, and loop discs.", "<dark_gray>/ob companion").build(),
                e -> new CompanionGui(plugin).open(p));

        // Row 4: admin-defined custom buttons (overlay)
        for (var entry : plugin.menuConfig().all().values()) {
            String cmd = entry.command;
            set(entry.slot, ItemBuilder.of(entry.material)
                            .name(entry.name)
                            .lore(entry.lore.toArray(new String[0])).build(),
                    e -> { p.closeInventory(); if (!cmd.isEmpty()) p.performCommand(cmd); });
        }

        fill(Material.GRAY_STAINED_GLASS_PANE, " ");
    }
}
