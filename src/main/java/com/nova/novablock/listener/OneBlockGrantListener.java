package com.nova.novablock.listener;

import com.nova.novablock.NovaBlock;
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
 * The "Personal OneBlock" grant item — a tagged bedrock that, when placed on the
 * player's own Community OneBlock claim, drops a regenerating personal resource
 * OneBlock node (works with or without a personal island; great for donor
 * perks). One-time use. Distributed via {@code /obadmin giveoneblock} so it can
 * be handed out by crates, shops, or rewards.
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
                    Msg.mm("<!italic><gray>Place on your <white>Community OneBlock<gray> claim"),
                    Msg.mm("<!italic><gray>to drop your own regenerating resource"),
                    Msg.mm("<!italic><gray>OneBlock — mine it for blocks & coins."),
                    Msg.mm("<!italic> "),
                    Msg.mm("<!italic><dark_gray>Sneak-break to reclaim · one-time use.")));
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

        // Drop a personal resource OneBlock node on the player's own claim — works
        // whether or not they have a personal island (great for donor perks).
        org.bukkit.block.Block placed = event.getBlockPlaced();
        if (!com.nova.novablock.compat.ClaimBridge.ownsClaimAt(p, placed.getLocation())) {
            Msg.send(p, "<red>Place your Personal OneBlock on your own community claim. <gray>Claim the land here first.");
            return;
        }
        int limit = plugin.communityNodes().limit(p);
        if (plugin.communityNodes().count(p.getUniqueId()) >= limit) {
            Msg.send(p, "<red>You've reached your Personal OneBlock limit (" + limit + ").");
            return;
        }
        consumeOne(p, event.getHand());
        org.bukkit.Location nodeLoc = placed.getLocation();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            plugin.communityNodes().place(p, nodeLoc.getBlock());
            Msg.send(p, "<green>Personal OneBlock placed! <gray>Mine it for resources — it regenerates.");
            p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_AMETHYST_BLOCK_PLACE, 0.8f, 1.0f);
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

}
