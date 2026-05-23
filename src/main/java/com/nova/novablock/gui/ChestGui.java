package com.nova.novablock.gui;

import com.nova.novablock.util.Msg;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Base chest-style GUI with click handlers per slot. Chest UIs work natively on
 * Bedrock/mobile via Geyser, with no special handling required.
 */
public abstract class ChestGui {

    private final int size;
    private final Component title;
    private Inventory inventory;
    private final Map<Integer, Consumer<InventoryClickEvent>> handlers = new HashMap<>();

    protected ChestGui(String title, int rows) {
        if (rows < 1 || rows > 6) throw new IllegalArgumentException("rows 1..6");
        this.size = rows * 9;
        this.title = Msg.mm(title);
    }

    /** Populate items + handlers. Called every time the menu opens. */
    protected abstract void build(Player player);

    public final void open(Player player) {
        this.inventory = Bukkit.createInventory(new Holder(this), size, title);
        this.handlers.clear();
        build(player);
        player.openInventory(inventory);
    }

    protected final void set(int slot, ItemStack item, Consumer<InventoryClickEvent> onClick) {
        inventory.setItem(slot, item);
        if (onClick != null) handlers.put(slot, onClick);
    }

    protected final void fill(Material material, String name) {
        ItemStack filler = com.nova.novablock.util.ItemBuilder.of(material).name(name).build();
        for (int i = 0; i < size; i++) {
            if (inventory.getItem(i) == null) inventory.setItem(i, filler);
        }
    }

    protected final void clearSlot(int slot) {
        inventory.setItem(slot, null);
        handlers.remove(slot);
    }

    void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);
        Consumer<InventoryClickEvent> handler = handlers.get(event.getRawSlot());
        if (handler != null) handler.accept(event);
    }

    public Inventory inventory() { return inventory; }
    public int size() { return size; }

    /** Inventory holder used by GuiManager to route clicks. */
    public static final class Holder implements org.bukkit.inventory.InventoryHolder {
        public final ChestGui gui;
        Holder(ChestGui gui) { this.gui = gui; }
        @Override public Inventory getInventory() { return gui.inventory; }
    }
}
