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
                        .name("<light_purple>xPets")
                        .lore("<gray>Open the pets menu to summon, manage,",
                                "<gray>and use your unlocked pets.",
                                "<dark_gray>/pets")
                        .build(),
                e -> {
                    p.closeInventory();
                    if (org.bukkit.Bukkit.getPluginManager().getPlugin("xPets") == null) {
                        com.nova.novablock.util.Msg.send(p, "<red>xPets isn't installed on this server.");
                        return;
                    }
                    p.performCommand("pets");
                });

        set(14, ItemBuilder.of(Material.EMERALD)
                        .name("<green>Economy & Shop")
                        .lore("<gray>Buy supplies, sell materials, and use specials.",
                                "<gray>Sell chests can auto-sell island items.",
                                "<dark_gray>/shop  /sell  /sellchest")
                        .build(),
                e -> runExternal(p, "shop"));

        set(15, ItemBuilder.of(Material.GOLD_BLOCK)
                        .name("<gold>Bank")
                        .lore("<gray>Deposit coins and earn interest over time.",
                                "<gray>Use it to grow money while you play.",
                                "<dark_gray>/bank")
                        .build(),
                e -> runExternal(p, "bank"));

        set(16, ItemBuilder.of(Material.COMPASS)
                        .name("<aqua>Stocks")
                        .lore("<gray>Buy and sell shares in the stock exchange.",
                                "<gray>Watch prices, portfolios, and market news.",
                                "<dark_gray>/stocks")
                        .build(),
                e -> runExternal(p, "stocks"));

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

        set(25, ItemBuilder.of(Material.PLAYER_HEAD)
                        .name("<#7FFFE0>Island Team & Bank")
                        .lore("<gray>Invite members, then <yellow>/ob promote<gray> them to",
                                "<gray>Co-Owner so they can run the roster and spend",
                                "<gray>the shared island bank on upgrades.",
                                "<gray>Members deposit; the owner can withdraw.",
                                "<dark_gray>/ob team · /ob bank · /ob promote · /ob kick")
                        .build(),
                e -> { p.closeInventory(); p.performCommand("ob team"); });

        set(30, ItemBuilder.of(Material.BEACON)
                        .name("<gold>Prestige")
                        .lore("<gray>Reset phase to 1 for permanent coin and XP boosts.",
                                "<gray>Requires completing Phase 12.",
                                "<dark_gray>/ob prestige").build(),
                e -> {
                    p.closeInventory();
                    p.performCommand("ob prestige");
                });

        set(28, ItemBuilder.of(Material.END_PORTAL_FRAME)
                        .name("<gradient:#7B61FF:#4FC3F7><bold>Endgame Arc")
                        .lore("<gray>How NovaBlock unfolds long-term:",
                                "<gray>phases → bosses → prestige → atlas.",
                                "<yellow>Click to see the path.")
                        .glow()
                        .build(),
                e -> new EndgameArcGui(plugin).open(p));

        set(32, ItemBuilder.of(Material.NETHER_STAR)
                        .name("<gradient:#7B61FF:#4FC3F7><bold>Main Menu")
                        .lore("<gray>Open the NovaBlock hub menu.",
                                "<dark_gray>/ob menu")
                        .glow()
                        .build(),
                e -> new MainMenuGui(plugin).open(p));

        fill(Material.GRAY_STAINED_GLASS_PANE, " ");
    }

    private void runExternal(Player p, String command) {
        p.closeInventory();
        if (org.bukkit.Bukkit.getCommandMap().getCommand(command) == null) {
            com.nova.novablock.util.Msg.send(p, "<red>The /" + command + " command isn't installed on this server.");
            return;
        }
        p.performCommand(command);
    }
}
