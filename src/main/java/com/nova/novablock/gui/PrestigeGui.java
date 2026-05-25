package com.nova.novablock.gui;

import com.nova.novablock.NovaBlock;
import com.nova.novablock.island.Island;
import com.nova.novablock.util.ItemBuilder;
import com.nova.novablock.util.Msg;
import org.bukkit.Material;
import org.bukkit.entity.Player;

public class PrestigeGui extends ChestGui {

    private final NovaBlock plugin;

    public PrestigeGui(NovaBlock plugin) {
        super("<gold><bold>Prestige", 1);
        this.plugin = plugin;
    }

    @Override
    protected void build(Player p) {
        Island island = plugin.islands().ofPlayer(p);
        int current = island == null ? 0 : island.data().getPrestigeLevel();
        int next = current + 1;
        double nextCoinMult = 1.0 + 0.10 * Math.min(next, 10);
        double nextXpMult = 1.0 + 0.05 * Math.min(next, 10);

        set(2, ItemBuilder.of(Material.BOOK)
                .name("<gold>Prestige " + next + " Rewards")
                .lore("<gray>• Permanent <yellow>+" + Math.round(nextCoinMult * 100 - 100) + "% <gray>coin gain",
                        "<gray>• Permanent <yellow>+" + Math.round(nextXpMult * 100 - 100) + "% <gray>skill XP",
                        "<gray>• A lump-sum coin payout",
                        "<gray>• A new pet",
                        "<gray>• 10 swansonx stocks",
                        "<gray>• Title: <gold>✦ Prestige " + roman(next),
                        " ",
                        "<dark_gray>Resets: <gray>phase to 1 (skills, coins, storage are kept).")
                .build(), null);

        set(4, ItemBuilder.of(Material.LIME_CONCRETE)
                .name("<green><bold>Confirm Prestige")
                .lore("<gray>Click to prestige now.",
                        "<dark_gray>This cannot be undone.")
                .glow().build(),
                e -> {
                    p.closeInventory();
                    if (island == null) {
                        Msg.send(p, "<red>You don't have an island.");
                        return;
                    }
                    plugin.prestige().doPrestige(p, island);
                });

        set(6, ItemBuilder.of(Material.RED_CONCRETE)
                .name("<red>Cancel")
                .lore("<gray>Close this menu.")
                .build(),
                e -> p.closeInventory());

        fill(Material.BLACK_STAINED_GLASS_PANE, " ");
    }

    private static String roman(int n) {
        String[] tens = {"", "X", "XX", "XXX", "XL", "L", "LX", "LXX", "LXXX", "XC"};
        String[] ones = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX"};
        return tens[(n / 10) % 10] + ones[n % 10];
    }
}
