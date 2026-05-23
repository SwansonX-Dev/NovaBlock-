package com.nova.novablock.gui;

import com.nova.novablock.NovaBlock;
import com.nova.novablock.util.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.entity.Player;

public class MainMenuGui extends ChestGui {

    private final NovaBlock plugin;

    public MainMenuGui(NovaBlock plugin) {
        super("<gradient:#7B61FF:#4FC3F7><bold>NovaBlock", 3);
        this.plugin = plugin;
    }

    @Override
    protected void build(Player p) {
        set(10, ItemBuilder.of(Material.GRASS_BLOCK)
                        .name("<green>Teleport Home")
                        .lore("<gray>Warp to your OneBlock.").build(),
                e -> { p.closeInventory(); var i = plugin.islands().ofPlayer(p); if (i != null) i.teleportHome(p); });

        set(11, ItemBuilder.of(Material.SPYGLASS)
                        .name("<aqua>Prophecy")
                        .lore("<gray>See and lock the next 10 blocks.").build(),
                e -> new ProphecyGui(plugin).open(p));

        set(12, ItemBuilder.of(Material.ENCHANTING_TABLE)
                        .name("<light_purple>Skills")
                        .lore("<gray>Spend XP on perks.").build(),
                e -> new SkillsGui(plugin).open(p));

        set(13, ItemBuilder.of(Material.BONE)
                        .name("<gold>Pets")
                        .lore("<gray>Summon and assign tasks.").build(),
                e -> new PetSelectGui(plugin).open(p));

        set(14, ItemBuilder.of(Material.WRITTEN_BOOK)
                        .name("<yellow>Daily Quest")
                        .lore("<gray>Check today's challenge.").build(),
                e -> new QuestGui(plugin).open(p));

        set(15, ItemBuilder.of(Material.EMERALD)
                        .name("<green>Shop")
                        .lore("<gray>Spend coins on upgrades.").build(),
                e -> new ShopGui(plugin).open(p));

        set(16, ItemBuilder.of(Material.PAPER)
                        .name("<white>Leaderboard")
                        .lore("<gray>Top islands by blocks broken.").build(),
                e -> new LeaderboardGui(plugin).open(p));

        fill(Material.GRAY_STAINED_GLASS_PANE, " ");
    }
}
