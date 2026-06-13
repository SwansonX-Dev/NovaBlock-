package com.nova.novablock.listener;

import com.nova.novablock.NovaBlock;
import com.nova.novablock.island.Island;
import com.nova.novablock.util.Msg;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

/**
 * The "Personal OneBlock" grant item — a tagged bedrock that, when placed in the
 * Community OneBlock world, creates the placer's own personal OneBlock island
 * (one-time use). Distributed via {@code /obadmin giveoneblock} so it can be
 * handed out by crates, shops, or rewards.
 */
public final class OneBlockGrantListener implements Listener {

    private final NovaBlock plugin;

    public OneBlockGrantListener(NovaBlock plugin) { this.plugin = plugin; }

    private static NamespacedKey key(NovaBlock plugin) {
        return new NamespacedKey(plugin, "oneblock_grant");
    }

    /** Build {@code amount} grant items (tagged bedrock). */
    public static ItemStack create(NovaBlock plugin, int amount) {
        ItemStack item = new ItemStack(Material.BEDROCK, Math.max(1, Math.min(64, amount)));
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Msg.mm("<!italic><gradient:#7B61FF:#4FC3F7><bold>Personal OneBlock"));
            meta.lore(List.of(
                    Msg.mm("<!italic><gray>Place this in the <white>Community OneBlock"),
                    Msg.mm("<!italic><gray>world to claim your own personal"),
                    Msg.mm("<!italic><gray>OneBlock island."),
                    Msg.mm("<!italic> "),
                    Msg.mm("<!italic><dark_gray>One-time use.")));
            meta.getPersistentDataContainer().set(key(plugin), PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        return item;
    }

    /** True if {@code item} is a Personal OneBlock grant item. */
    public static boolean is(NovaBlock plugin, ItemStack item) {
        if (item == null || item.getType() != Material.BEDROCK || !item.hasItemMeta()) return false;
        Byte b = item.getItemMeta().getPersistentDataContainer().get(key(plugin), PersistentDataType.BYTE);
        return b != null && b == (byte) 1;
    }

    // LOWEST so we intercept before any build-protection plugin cancels the place;
    // ignoreCancelled is false for the same reason. We always cancel the place
    // ourselves — a real bedrock must never be placed.
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onPlace(BlockPlaceEvent event) {
        if (!is(plugin, event.getItemInHand())) return;
        event.setCancelled(true);
        Player p = event.getPlayer();

        String communityWorld = plugin.community() == null ? null : plugin.community().communityWorldName();
        if (communityWorld == null || !p.getWorld().getName().equals(communityWorld)) {
            Msg.send(p, "<red>The Personal OneBlock can only be used in the <white>Community OneBlock<red> world.");
            return;
        }
        if (plugin.islands().ofPlayer(p) != null) {
            Msg.send(p, "<red>You already have a personal OneBlock. Use <yellow>/ob home<red>.");
            return;
        }
        if (!p.hasPermission("novablock.create")) {
            Msg.send(p, "<red>You don't have permission to create an island.");
            return;
        }

        // Consume one now (the item is guaranteed in hand during the place event);
        // creation + teleport run next tick to avoid teleporting mid-event.
        consumeOne(p, event.getHand());
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (plugin.islands().ofPlayer(p) != null) { refund(p); return; }
            Island island;
            try {
                island = plugin.islands().create(p);
            } catch (Exception ex) {
                plugin.getLogger().warning("Personal OneBlock grant failed for " + p.getName() + ": " + ex.getMessage());
                refund(p);
                Msg.send(p, "<red>Couldn't create your OneBlock right now — your item was returned.");
                return;
            }
            Msg.send(p, "<green>Your personal OneBlock has been created! Teleporting...");
            island.teleportHome(p);
        });
    }

    private void consumeOne(Player p, EquipmentSlot hand) {
        ItemStack inHand = hand == EquipmentSlot.OFF_HAND
                ? p.getInventory().getItemInOffHand() : p.getInventory().getItemInMainHand();
        if (!is(plugin, inHand)) return;
        int amt = inHand.getAmount();
        if (amt <= 1) {
            if (hand == EquipmentSlot.OFF_HAND) p.getInventory().setItemInOffHand(null);
            else p.getInventory().setItemInMainHand(null);
        } else {
            inHand.setAmount(amt - 1);
        }
    }

    private void refund(Player p) {
        var leftover = p.getInventory().addItem(create(plugin, 1));
        for (ItemStack drop : leftover.values()) p.getWorld().dropItemNaturally(p.getLocation(), drop);
    }
}
