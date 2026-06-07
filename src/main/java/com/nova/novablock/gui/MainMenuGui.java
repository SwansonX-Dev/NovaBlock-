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
                        .lore("<gray>Top islands by blocks broken.",
                                "<dark_gray>Open Sprint for the weekly boards.").build(),
                e -> new LeaderboardGui(plugin).open(p));

        set(26, ItemBuilder.of(Material.GOLDEN_HOE)
                        .name("<gradient:#FF6B6B:#FFC940><bold>Weekly Sprint")
                        .lore("<gray>Hardcore (blocks this week) +",
                                "<gray>Casual (daily quests this week).",
                                "<gray>Sunday 20:00 podium broadcast.",
                                "<dark_gray>/ob sprint").glow().build(),
                e -> new SprintGui(plugin).open(p));

        int onlineFriendCount = plugin.friends().onlineFriends(p.getUniqueId()).size();
        int incoming = plugin.friends().incoming(p.getUniqueId()).size();
        var friendsLore = new java.util.ArrayList<String>();
        friendsLore.add("<gray>" + onlineFriendCount + " online · " + plugin.friends().friends(p.getUniqueId()).size() + " total");
        if (incoming > 0) friendsLore.add("<yellow>" + incoming + " pending request" + (incoming == 1 ? "" : "s"));
        friendsLore.add("<dark_gray>/ob friend");
        set(25, ItemBuilder.of(Material.PLAYER_HEAD)
                        .name("<aqua>Friends")
                        .lore(friendsLore.toArray(new String[0]))
                        .build(),
                e -> new FriendsGui(plugin).open(p));

        set(16, ItemBuilder.of(Material.ANVIL)
                        .name("<red>Fix OneBlock")
                        .lore("<gray>Restore your missing or invalid center block.",
                                "<dark_gray>/ob fix").build(),
                e -> {
                    p.closeInventory();
                    p.performCommand("ob fix");
                });

        var path = plugin.seasonalPaths().activePath();
        var prog = plugin.progression().get(p);
        int pathTier = plugin.seasonalPaths().tierFor(prog.getSeasonalPathPoints());
        set(10, ItemBuilder.of(path.icon())
                        .name("<" + path.color() + ">Monthly Path")
                        .lore("<gray>" + path.name(),
                                "<gray>Tier <yellow>" + pathTier + "<gray> / "
                                        + com.nova.novablock.season.SeasonalPathManager.TIER_COUNT,
                                "<dark_gray>/ob path").build(),
                e -> new SeasonalPathGui(plugin).open(p));

        // Row 2: xEconomy bridges
        set(20, ItemBuilder.of(Material.EMERALD)
                        .name("<green>Shop")
                        .lore("<gray>Browse the dynamic market.", "<dark_gray>/shop").build(),
                e -> runExternal(p, "shop"));

        set(21, ItemBuilder.of(Material.GOLD_INGOT)
                        .name("<gold>Sell Items")
                        .lore("<gray>Sell what's in your hand or inventory.", "<dark_gray>/sell").build(),
                e -> runExternal(p, "sell"));

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
        var islandForTeam = plugin.islands().ofPlayer(p);
        long bankCoins = islandForTeam == null ? 0 : islandForTeam.data().getBankBalance();
        set(28, ItemBuilder.of(Material.PLAYER_HEAD)
                        .name("<#7FFFE0>Island Team")
                        .lore("<gray>Roster, roles, and the shared island bank.",
                                "<gray>Bank: <yellow>" + plugin.economy().format(bankCoins) + " coins",
                                "<dark_gray>/ob team · /ob bank · /ob promote").build(),
                e -> { p.closeInventory(); p.performCommand("ob team"); });

        set(29, ItemBuilder.of(Material.ANVIL)
                        .name("<gold>Island Upgrades")
                        .lore("<gray>Spend the island bank on permanent perks.",
                                "<dark_gray>/ob upgrades").build(),
                e -> new UpgradesGui(plugin).open(p));

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
                        .name("<light_purple>Pets")
                        .lore("<gray>Open xPets to summon and manage pets.", "<dark_gray>/pets").build(),
                e -> {
                    p.closeInventory();
                    if (org.bukkit.Bukkit.getPluginManager().getPlugin("xPets") == null) {
                        com.nova.novablock.util.Msg.send(p, "<red>xPets isn't installed on this server.");
                        return;
                    }
                    p.performCommand("pets");
                });

        var island = plugin.islands().ofPlayer(p);
        int prestige = island == null ? 0 : island.data().getPrestigeLevel();
        set(33, ItemBuilder.of(Material.NETHER_STAR)
                        .name("<gold>Prestige")
                        .lore("<gray>Current level: <yellow>" + prestige,
                                "<gray>Reach the end of Phase 12 to prestige.",
                                "<dark_gray>/ob prestige").build(),
                e -> { p.closeInventory(); p.performCommand("ob prestige"); });

        set(40, ItemBuilder.of(Material.KNOWLEDGE_BOOK)
                        .name("<gradient:#7B61FF:#4FC3F7>Server Guide")
                        .lore("<gray>Learn xPets, economy, stocks, paxel, and more.",
                                "<dark_gray>/ob help").build(),
                e -> new HelpGui(plugin).open(p));

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

    /** Closes the menu and runs an external command if one is registered, otherwise tells the player. */
    private void runExternal(Player p, String command) {
        p.closeInventory();
        if (org.bukkit.Bukkit.getCommandMap().getCommand(command) == null) {
            com.nova.novablock.util.Msg.send(p, "<red>The /" + command + " command isn't installed on this server.");
            return;
        }
        p.performCommand(command);
    }
}
