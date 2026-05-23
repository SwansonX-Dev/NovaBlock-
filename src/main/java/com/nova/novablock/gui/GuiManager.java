package com.nova.novablock.gui;

import com.nova.novablock.NovaBlock;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.InventoryHolder;

public class GuiManager implements Listener {

    private final NovaBlock plugin;

    public GuiManager(NovaBlock plugin) { this.plugin = plugin; }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (holder instanceof ChestGui.Holder ch) ch.gui.handleClick(event);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (holder instanceof ChestGui.Holder) event.setCancelled(true);
    }
}
