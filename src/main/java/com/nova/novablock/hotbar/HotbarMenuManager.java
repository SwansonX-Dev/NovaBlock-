package com.nova.novablock.hotbar;

import com.nova.novablock.NovaBlock;
import com.nova.novablock.gui.MainMenuGui;
import com.nova.novablock.util.ItemBuilder;
import com.nova.novablock.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * Persistent menu item that lives in the player's hotbar (slot 8 by default).
 * Right-click opens the main menu. The item cannot be dropped or moved.
 * /ob toggle removes/adds it for players who prefer a clean hotbar.
 */
public class HotbarMenuManager implements Listener {

    public static final NamespacedKey MENU_KEY = new NamespacedKey("novablock", "menu_item");
    private static final int SLOT = 8;

    private final NovaBlock plugin;

    public HotbarMenuManager(NovaBlock plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public ItemStack build() {
        ItemStack stack = ItemBuilder.of(Material.NETHER_STAR)
                .name("<gradient:#7B61FF:#4FC3F7><bold>NovaBlock Menu")
                .lore("<gray>Right-click to open the main menu.",
                        "<dark_gray>Use /ob toggle to hide.")
                .build();
        ItemMeta meta = stack.getItemMeta();
        meta.getPersistentDataContainer().set(MENU_KEY, PersistentDataType.BYTE, (byte) 1);
        stack.setItemMeta(meta);
        return stack;
    }

    public boolean isMenuItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(MENU_KEY, PersistentDataType.BYTE);
    }

    /** Place the menu item in slot 8 if free, else any free hotbar slot, else anywhere. */
    public void give(Player p) {
        if (!plugin.progression().get(p).isMenuItemEnabled()) return;
        PlayerInventory inv = p.getInventory();
        for (ItemStack s : inv.getContents()) if (isMenuItem(s)) return;
        ItemStack stack = build();
        if (isAir(inv.getItem(SLOT))) { inv.setItem(SLOT, stack); return; }
        for (int i = 0; i <= 8; i++) {
            if (isAir(inv.getItem(i))) { inv.setItem(i, stack); return; }
        }
        inv.addItem(stack);
    }

    private boolean isAir(ItemStack s) { return s == null || s.getType() == Material.AIR; }

    /** Remove the menu item from anywhere in the player's inventory. */
    public void remove(Player p) {
        PlayerInventory inv = p.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            if (isMenuItem(inv.getItem(i))) inv.setItem(i, null);
        }
    }

    /** Toggle the menu item on/off and persist the preference. */
    public void toggle(Player p) {
        boolean enabled = plugin.progression().get(p).isMenuItemEnabled();
        plugin.progression().get(p).setMenuItemEnabled(!enabled);
        plugin.progression().save(p.getUniqueId());
        if (enabled) {
            remove(p);
            Msg.actionBar(p, "<gray>Menu item hidden. Use <yellow>/ob toggle <gray>to bring it back.");
        } else {
            give(p);
            Msg.actionBar(p, "<green>Menu item enabled.");
        }
    }

    // ---------------- listeners ----------------

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (event.getPlayer().isOnline()) give(event.getPlayer());
        }, 20L);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (event.getPlayer().isOnline()) give(event.getPlayer());
        }, 5L);
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (!isMenuItem(event.getItem())) return;
        event.setCancelled(true);
        new MainMenuGui(plugin).open(event.getPlayer());
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        if (isMenuItem(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
            Msg.actionBar(event.getPlayer(), "<gray>Use <yellow>/ob toggle <gray>to remove the menu item.");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        boolean involvesMenu = isMenuItem(event.getCurrentItem()) || isMenuItem(event.getCursor());
        // Number-key hotbar swap can yank the menu item out of its slot even
        // when the hovered slot doesn't contain it.
        if (!involvesMenu && event.getHotbarButton() >= 0
                && event.getWhoClicked() instanceof Player hp) {
            involvesMenu = isMenuItem(hp.getInventory().getItem(event.getHotbarButton()));
        }
        if (!involvesMenu) return;
        event.setCancelled(true);
        if (event.getWhoClicked() instanceof Player p) Msg.actionBar(p, "<red>Menu item can't be moved.");
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (!isMenuItem(event.getOldCursor()) && !isMenuItem(event.getCursor())) return;
        event.setCancelled(true);
        if (event.getWhoClicked() instanceof Player p) Msg.actionBar(p, "<red>Menu item can't be moved.");
    }

    @EventHandler(ignoreCancelled = true)
    public void onSwapHand(PlayerSwapHandItemsEvent event) {
        if (isMenuItem(event.getMainHandItem()) || isMenuItem(event.getOffHandItem())) {
            event.setCancelled(true);
        }
    }

    /** Last line of defence for hopper / dropper / comparator moves involving the menu item. */
    @EventHandler(ignoreCancelled = true)
    public void onContainerMove(InventoryMoveItemEvent event) {
        if (isMenuItem(event.getItem())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(PlayerDeathEvent event) {
        event.getDrops().removeIf(this::isMenuItem);
    }

    @EventHandler
    public void onPickup(EntityPickupItemEvent event) {
        if (isMenuItem(event.getItem().getItemStack())) event.setCancelled(true);
    }
}
