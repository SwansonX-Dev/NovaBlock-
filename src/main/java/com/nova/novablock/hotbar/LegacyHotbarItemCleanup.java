package com.nova.novablock.hotbar;

import com.nova.novablock.NovaBlock;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataType;

/**
 * Strips the retired hotbar items — the NovaBlock menu nether star and the
 * backpack bundle — from players who still carry one.
 *
 * <p>Both were soulbound by their managers' listeners (undroppable, unmovable,
 * removed from death drops). Those listeners are gone, so any leftover copy would
 * otherwise turn into a real, tradeable Nether Star / Bundle in the player's
 * inventory. The menu is reachable with {@code /novablock} and the backpack with
 * {@code /bp}, so nothing is lost by clearing them.
 */
public class LegacyHotbarItemCleanup implements Listener {

    private static final NamespacedKey MENU_KEY = new NamespacedKey("novablock", "menu_item");
    private static final NamespacedKey BACKPACK_KEY = new NamespacedKey("novablock", "backpack_item");

    private final NovaBlock plugin;

    public LegacyHotbarItemCleanup(NovaBlock plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        // Deferred a tick so it runs after any other plugin restoring the inventory.
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (event.getPlayer().isOnline()) strip(event.getPlayer());
        }, 20L);
    }

    /** Remove every legacy hotbar item from the player's inventory. */
    public void strip(Player p) {
        PlayerInventory inv = p.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            if (isLegacyItem(inv.getItem(i))) inv.setItem(i, null);
        }
    }

    private boolean isLegacyItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        var pdc = item.getItemMeta().getPersistentDataContainer();
        return pdc.has(MENU_KEY, PersistentDataType.BYTE)
                || pdc.has(BACKPACK_KEY, PersistentDataType.BYTE);
    }
}
