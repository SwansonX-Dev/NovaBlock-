package com.nova.novablock.island;

import com.nova.novablock.NovaBlock;
import com.nova.novablock.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
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
 * Per-island shared virtual storage — 54 slots (double chest layout), any
 * member can open and any change persists for the whole island. Storage data
 * is serialised as base64-encoded {@code BukkitObjectOutputStream} bytes and
 * stored alongside the island YAML.
 *
 * <p>Permission gate: openers need {@code novablock.storage}. Admins with
 * {@code novablock.admin} bypass the membership check via
 * {@link #openFor(Player, Island)}.
 */
public class IslandStorageManager implements Listener {

    public static final int SIZE = 54;

    private final NovaBlock plugin;
    /** Live inventory per island; created on first open, mutated by viewers, written to disk on close. */
    private final Map<UUID, Inventory> live = new HashMap<>();

    public IslandStorageManager(NovaBlock plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    /** Opens the storage for the player. Returns true on success. */
    public boolean openFor(Player viewer, Island island) {
        Inventory inv = live.computeIfAbsent(island.data().getId(), id -> loadInventory(island));
        viewer.openInventory(inv);
        return true;
    }

    /**
     * Read-only access to the island's storage inventory for scanning (e.g. paxel
     * dup-check on join). Loads from disk into the live cache if not already open.
     */
    public Inventory peekInventory(Island island) {
        return live.computeIfAbsent(island.data().getId(), id -> loadInventory(island));
    }

    private Inventory loadInventory(Island island) {
        Holder holder = new Holder(island.data().getId());
        Inventory inv = Bukkit.createInventory(holder, SIZE,
                net.kyori.adventure.text.Component.text("Island Storage"));
        holder.inventory = inv;
        String data = island.data().getStorageBase64();
        if (data != null && !data.isEmpty()) {
            ItemStack[] items = deserialize(data);
            if (items != null) {
                int n = Math.min(items.length, SIZE);
                for (int i = 0; i < n; i++) {
                    ItemStack item = items[i];
                    // Drop the legacy auto-sell marker so the slot becomes usable storage.
                    if (isLegacyAutoSellMarker(item)) continue;
                    inv.setItem(i, item);
                }
            }
        }
        return inv;
    }

    /** Detects the old auto-sell marker so we can clean it up on load. */
    private static boolean isLegacyAutoSellMarker(ItemStack item) {
        if (item == null || item.getType() != Material.EMERALD) return false;
        if (!item.hasItemMeta()) return false;
        var name = item.getItemMeta().displayName();
        if (name == null) return false;
        var plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(name);
        return "Auto-Sell Slot".equals(plain);
    }

    /** Force a save of all open storages — called on plugin disable. */
    public void shutdown() {
        for (Map.Entry<UUID, Inventory> e : live.entrySet()) {
            persist(e.getKey(), e.getValue());
        }
        live.clear();
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof Holder h)) return;
        persist(h.islandId, event.getInventory());
    }

    private void persist(UUID islandId, Inventory inv) {
        Island island = plugin.islands().get(islandId);
        if (island == null) return;
        String base64 = serialize(inv.getContents());
        island.data().setStorageBase64(base64);
        plugin.storage().saveIsland(island.data());
    }

    // ---- (de)serialisation ----

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

    /** Marks an Inventory as ours so InventoryCloseEvent can recover the island id. */
    private static final class Holder implements InventoryHolder {
        final UUID islandId;
        Inventory inventory;
        Holder(UUID islandId) { this.islandId = islandId; }
        @Override public Inventory getInventory() { return inventory; }
    }

    /** Static helper used by the command path to validate + deliver a nice error. */
    public static boolean tryOpen(NovaBlock plugin, Player p) {
        if (!p.hasPermission("novablock.storage")) {
            Msg.send(p, "<red>You don't have permission to open island storage.");
            return false;
        }
        Island island = plugin.islands().ofPlayer(p);
        if (island == null) {
            Msg.send(p, "<red>You don't have an island yet.");
            return false;
        }
        return plugin.islandStorage().openFor(p, island);
    }
}
