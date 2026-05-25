package com.nova.novablock.gui;

import com.nova.novablock.NovaBlock;
import com.nova.novablock.util.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/** Bedrock-friendly feature guide for new and returning players. */
public class HelpGui extends ChestGui {

    private final NovaBlock plugin;

    public HelpGui(NovaBlock plugin) {
        super("<gradient:#7B61FF:#4FC3F7><bold>NovaBlock Guide", 5);
        this.plugin = plugin;
    }

    @Override
    protected void build(Player p) {
        set(10, ItemBuilder.of(Material.GRASS_BLOCK)
                        .name("<green>OneBlock Island")
                        .lore("<gray>You are sent to your island when you join.",
                                "<gray>Mine the center block to progress phases.",
                                "<gray>If it disappears, use <yellow>/ob fix<gray>.",
                                "<dark_gray>/ob home  /ob fix")
                        .build(),
                e -> {
                    p.closeInventory();
                    var island = plugin.islands().ofPlayer(p);
                    if (island != null) island.teleportHome(p);
                });

        set(19, ItemBuilder.of(Material.ANVIL)
                        .name("<red>Fix OneBlock")
                        .lore("<gray>Restores your missing center block and anchor.",
                                "<gray>Does not reset phase progress.",
                                "<dark_gray>/ob fix")
                        .build(),
                e -> {
                    p.closeInventory();
                    p.performCommand("ob fix");
                });

        set(11, ItemBuilder.of(Material.NETHERITE_PICKAXE)
                        .name("<aqua>Paxel")
                        .lore("<gray>Your main island tool.",
                                "<gray>Break blocks, gain upgrades, and keep it safe.",
                                "<dark_gray>Admins can reissue with /obadmin givepaxel")
                        .build(), null);

        set(12, ItemBuilder.of(Material.ALLAY_SPAWN_EGG)
                        .name("<aqua>Companion")
                        .lore("<gray>Summon a helper that gathers allowed items,",
                                "<gray>picks up drops, and can play music.",
                                "<dark_gray>/ob companion")
                        .build(),
                e -> new CompanionGui(plugin).open(p));

        set(13, ItemBuilder.of(Material.BONE)
                        .name("<light_purple>xPets")
                        .lore("<gray>Cosmetic pets live outside NovaBlock.",
                                "<gray>Use the xPets menu to summon and manage them.",
                                "<dark_gray>/pets")
                        .build(),
                e -> {
                    p.closeInventory();
                    p.performCommand("pets");
                });

        set(14, ItemBuilder.of(Material.EMERALD)
                        .name("<green>Economy & Shop")
                        .lore("<gray>Buy supplies, sell materials, and use specials.",
                                "<gray>Sell chests can auto-sell island items.",
                                "<dark_gray>/shop  /sell  /sellchest")
                        .build(),
                e -> {
                    p.closeInventory();
                    p.performCommand("shop");
                });

        set(15, ItemBuilder.of(Material.GOLD_BLOCK)
                        .name("<gold>Bank")
                        .lore("<gray>Deposit coins and earn interest over time.",
                                "<gray>Use it to grow money while you play.",
                                "<dark_gray>/bank")
                        .build(),
                e -> {
                    p.closeInventory();
                    p.performCommand("bank");
                });

        set(16, ItemBuilder.of(Material.COMPASS)
                        .name("<aqua>Stocks")
                        .lore("<gray>Buy and sell shares in the stock exchange.",
                                "<gray>Watch prices, portfolios, and market news.",
                                "<dark_gray>/stocks")
                        .build(),
                e -> {
                    p.closeInventory();
                    p.performCommand("stocks");
                });

        set(20, ItemBuilder.of(Material.SPYGLASS)
                        .name("<#7B61FF>Prophecy")
                        .lore("<gray>Preview upcoming blocks and lock rare ones",
                                "<gray>for bonus rewards.",
                                "<dark_gray>/ob prophecy")
                        .build(),
                e -> new ProphecyGui(plugin).open(p));

        set(21, ItemBuilder.of(Material.ENCHANTING_TABLE)
                        .name("<light_purple>Skills")
                        .lore("<gray>Level skills by playing and unlock perks.",
                                "<gray>Mining, combat, and more are tracked.",
                                "<dark_gray>/ob skills")
                        .build(),
                e -> new SkillsGui(plugin).open(p));

        set(22, ItemBuilder.of(Material.WRITTEN_BOOK)
                        .name("<yellow>Daily Quests")
                        .lore("<gray>Complete daily goals for rewards.",
                                "<gray>Check back each day for a new task.",
                                "<dark_gray>/ob quest")
                        .build(),
                e -> new QuestGui(plugin).open(p));

        set(23, ItemBuilder.of(Material.ENDER_CHEST)
                        .name("<#FFC940>Island Storage")
                        .lore("<gray>Shared storage for island members.",
                                "<gray>Useful for phase materials and valuables.",
                                "<dark_gray>/ob storage")
                        .build(),
                e -> {
                    p.closeInventory();
                    com.nova.novablock.island.IslandStorageManager.tryOpen(plugin, p);
                });

        set(24, ItemBuilder.of(Material.COMPARATOR)
                        .name("<#9C27B0>Island Flags")
                        .lore("<gray>Control island settings like PVP, fly,",
                                "<gray>mob spawning, explosions, and more.",
                                "<dark_gray>/ob flags")
                        .build(),
                e -> new IslandFlagsGui(plugin).open(p));

        set(31, ItemBuilder.of(Material.NETHER_STAR)
                        .name("<gradient:#7B61FF:#4FC3F7><bold>Main Menu")
                        .lore("<gray>Open the NovaBlock hub menu.",
                                "<dark_gray>/ob menu")
                        .glow()
                        .build(),
                e -> new MainMenuGui(plugin).open(p));

        fill(Material.GRAY_STAINED_GLASS_PANE, " ");
    }
}
