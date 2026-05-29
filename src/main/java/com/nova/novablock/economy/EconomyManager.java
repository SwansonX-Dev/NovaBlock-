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

    /**
     * Best-effort auto-sell of a single ItemStack via xEconomy's MarketService.
     * Returns the coins earned (already deposited to the island), or 0 if the
     * material has no market entry or the call failed.
     *
     * <p>Uses reflection because the paper-side {@code MarketService} isn't
     * exposed via the {@code xeconomy-api} module. Soft-degrades if xEconomy
     * ever renames or moves the method.
     */
    public long autoSellItem(Island island, org.bukkit.inventory.ItemStack stack) {
        if (island == null || stack == null || stack.getType().isAir() || stack.getAmount() <= 0) return 0;
        // Defense in depth: never sell a paxel, even if a future code path puts one
        // here. The IslandStorageManager click handler is the primary gate; this is
        // the secondary one so the exploit can't sneak in via a new entry point.
        if (plugin.paxels() != null && plugin.paxels().isPaxel(stack)) return 0;
        try {
            var pluginObj = Bukkit.getPluginManager().getPlugin("xEconomy");
            if (pluginObj == null) return 0;
            var marketMethod = pluginObj.getClass().getMethod("market");
            Object market = marketMethod.invoke(pluginObj);
            if (market == null) return 0;
            var sellMethod = market.getClass()
                    .getMethod("sell", org.bukkit.Material.class, int.class);
            Object result = sellMethod.invoke(market, stack.getType(), stack.getAmount());
            long cents = result instanceof Long l ? l : 0L;
            if (cents <= 0) return 0;
            long coins = cents / CENTS_PER_COIN;
            int autoSellLevel = island.data().getUpgradeLevel(
                    com.nova.novablock.island.IslandUpgrade.STORAGE_AUTOSELL);
            if (autoSellLevel > 0) coins = Math.round(coins * (1.0 + 0.25 * autoSellLevel));
            if (coins > 0) award(island, coins);
            return coins;
        } catch (ReflectiveOperationException ex) {
            return 0;
        }
    }
}
