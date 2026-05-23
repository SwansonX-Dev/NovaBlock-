package com.nova.novablock.compat;

import com.nova.novablock.NovaBlock;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.lang.reflect.Method;

/**
 * Soft hook into Vault's Economy service. xEconomy (and most other coin plugins)
 * register a Vault Economy implementation, so going through Vault means we work
 * with any of them out of the box.
 *
 * Reflection is used so the plugin still compiles + runs on servers without
 * Vault installed.
 */
public final class VaultHook {

    private final NovaBlock plugin;
    private Object economy;
    private Method getBalance;
    private Method depositPlayer;
    private Method withdrawPlayer;
    private Method has;

    public VaultHook(NovaBlock plugin) {
        this.plugin = plugin;
    }

    /** Tries to wire up Vault. Returns true if Vault + a registered economy exist. */
    public boolean setup() {
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) return false;
        try {
            Class<?> economyClass = Class.forName("net.milkbowl.vault.economy.Economy");
            RegisteredServiceProvider<?> rsp = Bukkit.getServicesManager().getRegistration(economyClass);
            if (rsp == null) return false;
            economy = rsp.getProvider();
            getBalance = economyClass.getMethod("getBalance", OfflinePlayer.class);
            depositPlayer = economyClass.getMethod("depositPlayer", OfflinePlayer.class, double.class);
            withdrawPlayer = economyClass.getMethod("withdrawPlayer", OfflinePlayer.class, double.class);
            has = economyClass.getMethod("has", OfflinePlayer.class, double.class);
            plugin.getLogger().info("Vault economy hooked: " + economy.getClass().getSimpleName()
                    + " (xEconomy / Essentials / etc. will be used for coins).");
            return true;
        } catch (Throwable t) {
            plugin.getLogger().warning("Vault present but no economy provider registered: " + t.getMessage());
            return false;
        }
    }

    public boolean isReady() { return economy != null; }

    public double balance(OfflinePlayer p) {
        if (!isReady()) return 0;
        try { return ((Number) getBalance.invoke(economy, p)).doubleValue(); }
        catch (Throwable t) { return 0; }
    }

    public void deposit(OfflinePlayer p, double amount) {
        if (!isReady() || amount <= 0) return;
        try { depositPlayer.invoke(economy, p, amount); } catch (Throwable ignored) {}
    }

    public boolean withdraw(OfflinePlayer p, double amount) {
        if (!isReady() || amount <= 0) return false;
        try {
            if (!(boolean) has.invoke(economy, p, amount)) return false;
            withdrawPlayer.invoke(economy, p, amount);
            return true;
        } catch (Throwable t) { return false; }
    }
}
