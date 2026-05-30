package com.nova.novablock.gui;

import com.nova.novablock.NovaBlock;
import com.nova.novablock.util.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * Lays out the long-term arc so a new player can see where this is going:
 * phases → bosses → prestige → seasonal path → leaderboard. The audit found
 * the existing HelpGui was a feature catalog but never explained the *spine*.
 */
public class EndgameArcGui extends ChestGui {

    private final NovaBlock plugin;

    public EndgameArcGui(NovaBlock plugin) {
        super("<gradient:#7B61FF:#4FC3F7><bold>Endgame Arc", 5);
        this.plugin = plugin;
    }

    @Override
    protected void build(Player p) {
        int phaseCount = plugin.phases().phaseCount();

        set(10, ItemBuilder.of(Material.GRASS_BLOCK)
                .name("<green>1. Mine the OneBlock")
                .lore("<gray>Break blocks at your island center.",
                        "<gray>Every block advances your phase progress.",
                        "<dark_gray>" + phaseCount + " phases total.")
                .build(), null);

        set(11, ItemBuilder.of(Material.WITHER_SKELETON_SKULL)
                .name("<red>2. Beat the boss")
                .lore("<gray>Bosses spawn on themed phases (Frostborn,",
                        "<gray>Magma, Void Herald). Defeating them ties",
                        "<gray>each act to the next.")
                .build(), null);

        set(12, ItemBuilder.of(Material.END_PORTAL_FRAME)
                .name("<dark_purple>3. Rifts & loot rooms")
                .lore("<gray>Hidden parkour, arena, and puzzle rooms",
                        "<gray>roll on a block cooldown.",
                        "<gray>Higher phases = richer rooms.")
                .build(), null);

        set(13, ItemBuilder.of(Material.BEACON)
                .name("<gold>4. Prestige")
                .lore("<gray>Finish Phase " + phaseCount + " to prestige.",
                        "<gray>Each prestige is permanent: <yellow>+10% coins<gray>,",
                        "<gray><yellow>+5% xp<gray>, plus a random armor trim.",
                        "<gray>Cap at level 10; broadcast to the server.",
                        "<dark_gray>/ob prestige")
                .glow()
                .build(), e -> {
                    p.closeInventory();
                    p.performCommand("ob prestige");
                });

        set(14, ItemBuilder.of(Material.MAP)
                .name("<aqua>5. Seasonal Path")
                .lore("<gray>30 tiers, new theme every month.",
                        "<gray>1-of-1 claims broadcast server-wide.",
                        "<dark_gray>/ob path")
                .build(), e -> {
                    p.closeInventory();
                    new SeasonalPathGui(plugin).open(p);
                });

        set(15, ItemBuilder.of(Material.GOLD_INGOT)
                .name("<yellow>6. Leaderboard")
                .lore("<gray>Islands ranked by total blocks broken,",
                        "<gray>plus wallet, bank, and stock portfolio.",
                        "<dark_gray>/ob leaderboard")
                .build(), e -> {
                    p.closeInventory();
                    new LeaderboardGui(plugin).open(p);
                });

        set(16, ItemBuilder.of(Material.NETHER_STAR)
                .name("<#FFC940>7. Atlas Score")
                .lore("<gray>Your lifetime seasonal-path total.",
                        "<gray>Carries across every season.",
                        "<gray>The number that says how long you've been here.")
                .build(), null);

        set(31, ItemBuilder.of(Material.BOOK)
                .name("<gradient:#7B61FF:#4FC3F7><bold>Back to Guide")
                .lore("<gray>Return to the main guide.")
                .build(), e -> new HelpGui(plugin).open(p));

        fill(Material.GRAY_STAINED_GLASS_PANE, " ");
    }
}
