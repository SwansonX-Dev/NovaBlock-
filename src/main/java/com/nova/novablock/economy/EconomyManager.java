package com.nova.novablock.economy;

import com.nova.novablock.NovaBlock;
import com.nova.novablock.island.Island;
import dev.xsuite.economy.api.Economy;
import dev.xsuite.economy.api.XEconomy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Currency routing for NovaBlock. Every transaction goes through the xEconomy
 * native API; callers continue to think in whole "coins" (long), which we
 * convert to xEconomy's cents at the boundary (×100).
 *
 * <p>Island-scoped awards split the credit among online members; if no members
 * are online the owner is credited. Island-scoped spends always debit the owner.
 */
public class EconomyManager {

    /** 1 NovaBlock coin == 100 xEconomy cents. */
    private static final long CENTS_PER_COIN = 100L;

    private final NovaBlock plugin;
    private final Economy eco;

    public EconomyManager(NovaBlock plugin) {
        this.plugin = plugin;
        this.eco = XEconomy.get();
        if (this.eco == null) {
            // depend: [xEconomy] in plugin.yml means we should never reach here.
            throw new IllegalStateException(
                    "xEconomy is required but no Economy provider is registered. " +
                    "Make sure the xEconomy plugin is installed and loaded.");
        }
    }

    // ----- per-island operations -----

    public void award(Island island, long amount) {
        if (island == null || amount <= 0) return;
        List<UUID> online = new ArrayList<>();
        for (UUID u : island.data().getMembers()) if (Bukkit.getPlayer(u) != null) online.add(u);
        if (online.isEmpty()) {
            depositCoins(island.data().getOwner(), amount);
            return;
        }
        long share = Math.max(1, amount / online.size());
        for (UUID u : online) depositCoins(u, share);
    }

    public boolean spend(Island island, long amount) {
        if (island == null || amount <= 0) return false;
        return withdrawCoins(island.data().getOwner(), amount);
    }

    public long balance(Island island) {
        if (island == null) return 0;
        return balanceCoins(island.data().getOwner());
    }

    // ----- per-player operations -----

    public long balance(Player p) {
        return p == null ? 0 : balanceCoins(p.getUniqueId());
    }

    public boolean spend(Player p, long amount) {
        if (p == null || amount <= 0) return false;
        return withdrawCoins(p.getUniqueId(), amount);
    }

    public void deposit(OfflinePlayer p, long amount) {
        if (p == null || amount <= 0) return;
        depositCoins(p.getUniqueId(), amount);
    }

    // ----- xEconomy adapters (whole coins → cents) -----

    private void depositCoins(UUID id, long coins) {
        OfflinePlayer op = Bukkit.getOfflinePlayer(id);
        String name = op.getName() != null ? op.getName() : id.toString();
        eco.deposit(id, name, coins * CENTS_PER_COIN);
    }

    private boolean withdrawCoins(UUID id, long coins) {
        OfflinePlayer op = Bukkit.getOfflinePlayer(id);
        String name = op.getName() != null ? op.getName() : id.toString();
        return eco.withdraw(id, name, coins * CENTS_PER_COIN);
    }

    private long balanceCoins(UUID id) {
        return eco.balanceCents(id) / CENTS_PER_COIN;
    }
}
