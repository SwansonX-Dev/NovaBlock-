package com.nova.novablock.gui;

import com.nova.novablock.NovaBlock;
import com.nova.novablock.island.Dimension;
import com.nova.novablock.island.Island;
import com.nova.novablock.util.ItemBuilder;
import com.nova.novablock.util.Msg;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Prestige selector. Each dimension (Overworld / Nether / End) prestiges
 * independently — its own level, its own reset — but the coin/XP/sell bonuses
 * stack across all three. The menu shows one panel per dimension with its
 * current level, eligibility, and what the next prestige adds to the stacked
 * bonus.
 */
public class PrestigeGui extends ChestGui {

    private final NovaBlock plugin;

    public PrestigeGui(NovaBlock plugin) {
        super("<gold><bold>Prestige", 3);
        this.plugin = plugin;
    }

    @Override
    protected void build(Player p) {
        Island island = plugin.islands().ofPlayer(p);

        set(4, ItemBuilder.of(Material.NETHER_STAR)
                .name("<gold><bold>Prestige")
                .lore("<gray>Each dimension prestiges on its own —",
                        "<gray>its level and phases reset separately.",
                        "<gray>All three bonuses <white>stack<gray> together.",
                        " ",
                        "<dark_gray>Stacked bonus is capped at level "
                                + plugin.prestige().maxLevel() + " per dimension.")
                .glow().build(), null);

        panel(p, island, Dimension.OVERWORLD, Material.GRASS_BLOCK, 11,
                "<green>", "Overworld", null);
        panel(p, island, Dimension.NETHER, Material.NETHERRACK, 13,
                "<red>", "Nether", "<gray>Reach <yellow>Overworld Phase 7<gray> to unlock.");
        panel(p, island, Dimension.END, Material.END_STONE, 15,
                "<#C9B8FF>", "End", "<gray>Complete the <#B47BFF>Nether<gray> to unlock.");

        set(22, ItemBuilder.of(Material.ARROW)
                .name("<gray>← Back to menu")
                .build(),
                e -> new MainMenuGui(plugin).open(p));

        fill(Material.BLACK_STAINED_GLASS_PANE, " ");
    }

    private void panel(Player p, Island island, Dimension dim, Material icon, int slot,
                       String color, String name, String lockedHint) {
        boolean unlocked = island != null && island.isUnlocked(dim);
        int level = island == null ? 0 : island.data().getPrestigeLevel(dim);
        boolean eligible = plugin.prestige().canPrestige(island, dim);

        List<String> lore = new ArrayList<>();
        lore.add("<gray>Current: " + color + "Prestige " + roman(level) + " <dark_gray>(" + level + ")");

        if (!unlocked) {
            lore.add(" ");
            lore.add("<red>✖ Locked");
            if (lockedHint != null) lore.add(lockedHint);
            set(slot, ItemBuilder.of(Material.GRAY_DYE)
                    .name(color + "<bold>" + name + " Prestige")
                    .lore(lore).build(), null);
            return;
        }

        // What the stacked total (and therefore the bonus) becomes after this prestige.
        int cappedTotal = plugin.prestige().cappedTotalLevel(island);
        boolean underCap = level < plugin.prestige().maxLevel();
        int nextTotal = cappedTotal + (underCap ? 1 : 0);
        int coinPct = pct(plugin.prestige().coinMultiplierAtTotal(nextTotal));
        int xpPct = pct(plugin.prestige().xpMultiplierAtTotal(nextTotal));
        int sellPct = pct(plugin.prestige().sellMultiplierAtTotal(nextTotal));

        lore.add(" ");
        lore.add("<gray>Next prestige — <white>stacked bonus becomes:");
        lore.add("<gray>• <yellow>+" + coinPct + "% <gray>coins");
        lore.add("<gray>• <yellow>+" + xpPct + "% <gray>skill XP");
        lore.add("<gray>• <yellow>+" + sellPct + "% <gray>sell prices");
        lore.add("<gray>• A lump-sum coin payout + an armor trim");
        lore.add(" ");
        lore.add("<dark_gray>Resets only the " + name + " to phase 1.");
        if (!underCap) {
            lore.add("<dark_gray>Level " + plugin.prestige().maxLevel()
                    + " reached — no further bonus, rewards still granted.");
        }
        lore.add(" ");

        if (eligible) {
            lore.add("<green>▶ Click to prestige your " + name + "!");
            lore.add("<dark_gray>This cannot be undone.");
            set(slot, ItemBuilder.of(icon)
                    .name(color + "<bold>" + name + " Prestige")
                    .lore(lore).glow().build(),
                    e -> {
                        p.closeInventory();
                        Island fresh = plugin.islands().ofPlayer(p);
                        if (fresh == null) {
                            Msg.send(p, "<red>You don't have an island.");
                            return;
                        }
                        plugin.prestige().doPrestige(p, fresh, dim);
                    });
        } else {
            var last = plugin.phases().get(dim, plugin.phases().phaseCount(dim) - 1);
            String lastName = last == null ? "the final phase" : last.getDisplayName();
            lore.add("<red>✖ Clear <white>" + lastName + " <red>first.");
            set(slot, ItemBuilder.of(icon)
                    .name(color + "<bold>" + name + " Prestige")
                    .lore(lore).build(), null);
        }
    }

    private static int pct(double mult) {
        return (int) Math.round(mult * 100 - 100);
    }

    private static String roman(int n) {
        if (n <= 0) return "0";
        String[] tens = {"", "X", "XX", "XXX", "XL", "L", "LX", "LXX", "LXXX", "XC"};
        String[] ones = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX"};
        return tens[(n / 10) % 10] + ones[n % 10];
    }
}
