package com.nova.novablock.gui;

import com.nova.novablock.NovaBlock;
import com.nova.novablock.island.Island;
import com.nova.novablock.util.ItemBuilder;
import dev.xsuite.economy.api.Bank;
import dev.xsuite.economy.api.Stocks;
import dev.xsuite.economy.api.XEconomy;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Top 45 islands sorted by blocks broken. Each entry shows the owner's
 * wallet, bank, and stock portfolio value if xEconomy's Bank / Stocks
 * services are available, plus a net-worth roll-up.
 */
public class LeaderboardGui extends ChestGui {

    private final NovaBlock plugin;

    public LeaderboardGui(NovaBlock plugin) {
        super("<white><bold>Leaderboard", 6);
        this.plugin = plugin;
    }

    @Override
    protected void build(Player viewer) {
        List<Island> sorted = plugin.islands().all().values().stream()
                .sorted(Comparator.comparingLong((Island i) -> i.data().getBlocksBroken()).reversed())
                .limit(45)
                .collect(Collectors.toList());

        Bank bank = XEconomy.bank();
        Stocks stocks = XEconomy.stocks();

        Material[] medals = {Material.NETHER_STAR, Material.DIAMOND_BLOCK, Material.GOLD_BLOCK};
        for (int i = 0; i < sorted.size(); i++) {
            Island island = sorted.get(i);
            Material icon = i < medals.length ? medals[i] : Material.IRON_BLOCK;
            OfflinePlayer owner = Bukkit.getOfflinePlayer(island.data().getOwner());
            String name = owner.getName() == null ? "Unknown" : owner.getName();

            long wallet = plugin.economy().balance(island);
            long bankCoins   = (bank == null)   ? 0 : bank.balanceCents(owner.getUniqueId()) / 100;
            long stockCoins  = (stocks == null) ? 0 : stocks.portfolioValueCents(owner.getUniqueId(), name) / 100;
            long netWorth = wallet + bankCoins + stockCoins;

            List<String> lore = new ArrayList<>();
            lore.add("<gray>Blocks broken: <white>" + island.data().getBlocksBroken());
            lore.add("<gray>Phase: <white>" + (island.data().getPhaseIndex() + 1));
            lore.add("<gray>Coins: <yellow>" + wallet);
            if (bank != null)   lore.add("<gray>Bank: <#FFC940>" + bankCoins);
            if (stocks != null) lore.add("<gray>Portfolio: <aqua>" + stockCoins);
            if (bank != null || stocks != null) {
                lore.add("<gray>Net worth: <gold>" + netWorth);
            }

            set(i, ItemBuilder.of(icon)
                    .name("<gold>#" + (i + 1) + " <yellow>" + name)
                    .lore(lore.toArray(new String[0]))
                    .build(), null);
        }
        set(49, ItemBuilder.of(Material.GOLDEN_HOE)
                .name("<gradient:#FF6B6B:#FFC940><bold>Weekly Sprint")
                .lore("<gray>Hardcore + Casual weekly boards.",
                        "<dark_gray>/ob sprint").glow().build(),
                e -> new SprintGui(plugin).open(viewer));
        set(53, ItemBuilder.of(Material.ARROW).name("<gray>Back").build(),
                e -> new MainMenuGui(plugin).open(viewer));
    }
}
