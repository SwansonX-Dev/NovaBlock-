package com.nova.novablock.economy;

import com.nova.novablock.NovaBlock;
import com.nova.novablock.compat.VaultHook;
import com.nova.novablock.island.Island;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Two-tier economy:
 *   - If Vault (xEconomy / Essentials Economy / etc.) is present, every transaction
 *     hits the player's Vault balance. Awards to an island are split evenly among
 *     online members; if no members are online the owner's balance is credited.
 *   - Otherwise we fall back to the per-island coin field stored in IslandData.
 *
 * Other systems keep calling award(island, amount) and balance(island) — the routing
 * is transparent to them.
 */
public class EconomyManager {

    private final NovaBlock plugin;
    private final VaultHook vault;

    public EconomyManager(NovaBlock plugin) {
        this.plugin = plugin;
        this.vault = new VaultHook(plugin);
        this.vault.setup();
    }

    public boolean usingVault() { return vault.isReady(); }

    // ----- per-island operations (legacy callers) -----

    public void award(Island island, long amount) {
        if (island == null || amount <= 0) return;
        if (!vault.isReady()) {
            island.data().addCoins(amount);
            return;
        }
        // Split amongst online members; if none online, credit owner.
        var members = island.data().getMembers();
        java.util.List<UUID> online = new java.util.ArrayList<>();
        for (UUID u : members) if (Bukkit.getPlayer(u) != null) online.add(u);
        if (online.isEmpty()) {
            vault.deposit(Bukkit.getOfflinePlayer(island.data().getOwner()), amount);
            return;
        }
        long share = Math.max(1, amount / online.size());
        for (UUID u : online) vault.deposit(Bukkit.getOfflinePlayer(u), share);
    }

    public boolean spend(Island island, long amount) {
        if (island == null || amount <= 0) return false;
        if (!vault.isReady()) {
            if (island.data().getCoins() < amount) return false;
            island.data().setCoins(island.data().getCoins() - amount);
            return true;
        }
        // Fall back to the owner's balance for island-wide spends.
        return vault.withdraw(Bukkit.getOfflinePlayer(island.data().getOwner()), amount);
    }

    public long balance(Island island) {
        if (island == null) return 0;
        if (!vault.isReady()) return island.data().getCoins();
        return (long) vault.balance(Bukkit.getOfflinePlayer(island.data().getOwner()));
    }

    // ----- per-player operations (new callers, e.g. pet store) -----

    public long balance(Player p) {
        if (p == null) return 0;
        if (vault.isReady()) return (long) vault.balance(p);
        var island = plugin.islands().ofPlayer(p);
        return island == null ? 0 : island.data().getCoins();
    }

    public boolean spend(Player p, long amount) {
        if (p == null || amount <= 0) return false;
        if (vault.isReady()) return vault.withdraw(p, amount);
        var island = plugin.islands().ofPlayer(p);
        if (island == null) return false;
        return spend(island, amount);
    }

    public void deposit(OfflinePlayer p, long amount) {
        if (p == null || amount <= 0) return;
        if (vault.isReady()) { vault.deposit(p, amount); return; }
        var island = plugin.islands().ofPlayer(p.getUniqueId());
        if (island != null) island.data().addCoins(amount);
    }
}
