package com.nova.novablock.gui;

import com.nova.novablock.NovaBlock;
import com.nova.novablock.progression.PlayerProgression;
import com.nova.novablock.season.SeasonalPathManager;
import com.nova.novablock.util.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.entity.Player;

public final class SeasonalPathGui extends ChestGui {

    private final NovaBlock plugin;

    public SeasonalPathGui(NovaBlock plugin) {
        super("<gradient:#7B61FF:#4FC3F7>Monthly Path", 6);
        this.plugin = plugin;
    }

    @Override
    protected void build(Player player) {
        plugin.seasonalPaths().ensureActive(player);
        PlayerProgression prog = plugin.progression().get(player);
        SeasonalPathManager.SeasonalPath path = plugin.seasonalPaths().activePath();
        int tier = plugin.seasonalPaths().tierFor(prog.getSeasonalPathPoints());
        int next = Math.min(SeasonalPathManager.TIER_COUNT, tier + 1);

        set(4, ItemBuilder.of(path.icon())
                .name("<" + path.color() + ">" + path.name())
                .lore("<gray>Tier <yellow>" + tier + "<gray> / " + SeasonalPathManager.TIER_COUNT,
                        "<gray>Points <white>" + prog.getSeasonalPathPoints()
                                + "<dark_gray> / " + plugin.seasonalPaths().pointsForTier(next),
                        "<gray>Atlas: " + plugin.seasonalPaths().atlasTitle(prog))
                .glow()
                .build(), null);

        for (int i = 1; i <= SeasonalPathManager.TIER_COUNT; i++) {
            int row = (i - 1) / 9;
            int col = (i - 1) % 9;
            int slot = 9 + row * 9 + col;
            boolean unlocked = tier >= i;
            boolean claimed = prog.hasClaimedSeasonalTier(i);
            Material mat = claimed ? Material.LIME_STAINED_GLASS_PANE
                    : unlocked ? Material.YELLOW_STAINED_GLASS_PANE
                    : Material.GRAY_STAINED_GLASS_PANE;
            set(slot, ItemBuilder.of(mat)
                    .name((claimed ? "<green>" : unlocked ? "<yellow>" : "<gray>") + "Tier " + i)
                    .lore(plugin.seasonalPaths().tierLore(prog, path, i))
                    .build(), null);
        }

        set(45, ItemBuilder.of(Material.BOOK)
                .name("<aqua>Nova Atlas")
                .lore("<gray>Permanent collection score.",
                        "<gray>Score: <yellow>" + prog.getAtlasScore(),
                        plugin.seasonalPaths().atlasTitle(prog))
                .build(), null);

        set(53, ItemBuilder.of(Material.ARROW)
                .name("<gray>Back")
                .lore("<dark_gray>/ob menu")
                .build(), e -> new MainMenuGui(plugin).open(player));

        fill(Material.BLACK_STAINED_GLASS_PANE, " ");
    }
}
