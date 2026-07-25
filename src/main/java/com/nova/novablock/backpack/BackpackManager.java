package com.nova.novablock.backpack;

import com.nova.novablock.NovaBlock;
import com.nova.novablock.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Personal backpack — a per-player 54-slot (double chest) virtual inventory that
 * survives relogs. Built to relieve inventory pressure from the high-variety
 * Community OneBlock pool: offload here instead of shuttling to a chest.
 *
 * <p>{@code /backpack} (alias {@code /bp}) opens it. {@code /bp toggle} flips the
 * per-player auto-grab preference: while it is on, items the player picks up go
 * straight into the backpack, keeping the main inventory clear.
 *
 * <p>Mirrors {@link com.nova.novablock.island.IslandStorageManager} but keyed by
 * player UUID; contents are base64-encoded {@code BukkitObjectOutputStream} bytes
 * persisted on the player's {@link com.nova.novablock.progression.PlayerProgression}.
 */
public class BackpackManager implements Listener {

    public static final int SIZE = 54;

    private final NovaBlock plugin;
    /** Live inventory per online player; loaded on demand, mutated by viewer/auto-grab, persisted on close/quit. */
    private final Map<UUID, Inventory> live = new HashMap<>();

    public BackpackManager(NovaBlock plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    // ---------------- open / storage ----------------

    /** Opens the player's backpack. */
    public boolean openFor(Player p) {
        p.openInventory(inventory(p.getUniqueId()));
        return true;
    }

    /** The live backpack inventory for a player, loaded from disk on first access. */
    private Inventory inventory(UUID playerId) {
        return live.computeIfAbsent(playerId, this::loadInventory);
    }

    private Inventory loadInventory(UUID playerId) {
        Holder holder = new Holder(playerId);
        Inventory inv = Bukkit.createInventory(holder, SIZE,
                net.kyori.adventure.text.Component.text("Backpack"));
        holder.inventory = inv;
        String data = plugin.progression().get(playerId).getBackpackBase64();
        if (data != null && !data.isEmpty()) {
            ItemStack[] items = deserialize(data);
            if (items != null) {
                int n = Math.min(items.length, SIZE);
                for (int i = 0; i < n; i++) inv.setItem(i, items[i]);
            }
        }
        return inv;
    }

    /** Flush all open backpacks — called on plugin disable. */
    public void shutdown() {
        for (Map.Entry<UUID, Inventory> e : live.entrySet()) persistDisk(e.getKey(), e.getValue());
        live.clear();
    }

    /** Update the in-memory progression record (no disk write); autosave/quit flush it out. */
    private void persistObject(UUID playerId) {
        Inventory inv = live.get(playerId);
        if (inv != null) plugin.progression().get(playerId).setBackpackBase64(serialize(inv.getContents()));
    }

    /** Update the record and write it to disk immediately. */
    private void persistDisk(UUID playerId, Inventory inv) {
        plugin.progression().get(playerId).setBackpackBase64(serialize(inv.getContents()));
        plugin.progression().save(playerId);
    }

    // ---------------- auto-grab ----------------

    /** Toggle auto-grab on/off, persisting the preference. */
    public void toggleAutoGrab(Player p) {
        var prog = plugin.progression().get(p);
        boolean enabled = prog.isAutoGrabEnabled();
        prog.setAutoGrabEnabled(!enabled);
        plugin.progression().save(p.getUniqueId());
        Msg.actionBar(p, enabled
                ? "<gray>Auto-grab <bold>OFF</bold><gray>. <yellow>/bp toggle<gray> to re-enable."
                : "<green>Auto-grab <bold>ON</bold><green>. Picked-up items now go into your backpack.");
    }

    private boolean isAir(ItemStack s) { return s == null || s.getType() == Material.AIR; }

    /**
     * Route an item into the player's backpack when auto-grab is enabled. Used by the
     * paxel telekinesis path: paxel-mined drops never become ground items (the break
     * handler delivers them straight to the player), so they bypass {@link #onPickup}.
     * Sending them through here lands them in the backpack just like a walked-over pickup.
     *
     * @return {@code null} if the whole stack was stored (or the item was air); the
     *         leftover stack if the backpack is off or full (caller handles the remainder).
     */
    public ItemStack routeToBackpack(Player p, ItemStack item) {
        if (isAir(item)) return null;
        if (!plugin.progression().get(p).isAutoGrabEnabled()) return item;
        Inventory bp = inventory(p.getUniqueId());
        Map<Integer, ItemStack> overflow = bp.addItem(item.clone());
        persistObject(p.getUniqueId());
        return overflow.isEmpty() ? null : overflow.values().iterator().next();
    }

    // ---------------- listeners ----------------

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        Inventory inv = live.remove(id);
        if (inv != null) persistDisk(id, inv);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof Holder h)) return;
        persistDisk(h.playerId, event.getInventory()); // keep the live ref while the player is online
    }

    /**
     * Auto-grab: while the preference is on, route picked-up items straight into the
     * backpack (keeping the main inventory clear). The soulbound paxel is never
     * swallowed. When the backpack is full, the remainder falls through to normal pickup.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player p)) return;
        ItemStack stack = event.getItem().getItemStack();
        if (plugin.paxels().isPaxel(stack)) return;
        if (!plugin.progression().get(p).isAutoGrabEnabled()) return;

        Inventory bp = inventory(p.getUniqueId());
        Map<Integer, ItemStack> overflow = bp.addItem(stack.clone());
        if (overflow.isEmpty()) {
            event.getItem().remove();
            event.setCancelled(true);
            persistObject(p.getUniqueId());
            return;
        }
        // Partial: store what fit, leave the rest on the ground for a normal pickup.
        int remaining = overflow.values().iterator().next().getAmount();
        if (remaining < stack.getAmount()) {
            stack.setAmount(remaining);
            event.getItem().setItemStack(stack);
            persistObject(p.getUniqueId());
        }
        // else: backpack full, nothing stored — let vanilla handle it.
    }

    // ---- (de)serialisation (identical scheme to IslandStorageManager) ----

    private static String serialize(ItemStack[] items) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             BukkitObjectOutputStream out = new BukkitObjectOutputStream(baos)) {
            out.writeInt(items.length);
            for (ItemStack item : items) out.writeObject(item);
            out.flush();
            return Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (IOException ex) {
            return "";
        }
    }

    private static ItemStack[] deserialize(String base64) {
        try (ByteArrayInputStream bais = new ByteArrayInputStream(Base64.getDecoder().decode(base64));
             BukkitObjectInputStream in = new BukkitObjectInputStream(bais)) {
            int len = in.readInt();
            ItemStack[] items = new ItemStack[len];
            for (int i = 0; i < len; i++) items[i] = (ItemStack) in.readObject();
            return items;
        } catch (IOException | ClassNotFoundException ex) {
            return null;
        }
    }

    /** Marks an Inventory as ours so InventoryCloseEvent can recover the owner id. */
    private static final class Holder implements InventoryHolder {
        final UUID playerId;
        Inventory inventory;
        Holder(UUID playerId) { this.playerId = playerId; }
        @Override public Inventory getInventory() { return inventory; }
    }

    /** Static helper used by the command path to validate + deliver a nice error. */
    public static boolean tryOpen(NovaBlock plugin, Player p) {
        if (!p.hasPermission("novablock.backpack")) {
            Msg.send(p, "<red>You don't have permission to open your backpack.");
            return false;
        }
        return plugin.backpacks().openFor(p);
    }
}
