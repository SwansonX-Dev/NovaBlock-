package com.nova.novablock.listener;

import com.nova.novablock.NovaBlock;
import com.nova.novablock.community.CommunityNodeType;
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

    private static NamespacedKey typeKey(NovaBlock plugin) {
        return new NamespacedKey(plugin, "oneblock_grant_type");
    }

    /** Build {@code amount} grant items of the given node type (tagged bedrock). */
    public static ItemStack create(NovaBlock plugin, CommunityNodeType type, int amount) {
        ItemStack item = new ItemStack(Material.BEDROCK, Math.max(1, Math.min(64, amount)));
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Msg.mm("<!italic>" + type.colorGradient() + "<bold>Personal OneBlock — " + type.displayName()));
            String reward = type.dropMode() == CommunityNodeType.DropMode.VANILLA ? "ores & coins" : "blocks & coins";
            meta.lore(List.of(
                    Msg.mm("<!italic><gray>Place on land you own (claim or island)"),
                    Msg.mm("<!italic><gray>to drop a regenerating <white>" + type.displayName() + "<gray> OneBlock."),
                    Msg.mm("<!italic><gray>Mine it for <white>" + reward + "<gray>."),
                    Msg.mm("<!italic> "),
                    Msg.mm("<!italic><dark_gray>Sneak-punch to reclaim · one-time use.")));
            var pdc = meta.getPersistentDataContainer();
            pdc.set(key(plugin), PersistentDataType.BYTE, (byte) 1);
            pdc.set(typeKey(plugin), PersistentDataType.STRING, type.id());
            item.setItemMeta(meta);
        }
        return item;
    }

    /** Node type carried by the grant item; defaults to MINING (and for legacy items). */
    public static CommunityNodeType typeOf(NovaBlock plugin, ItemStack item) {
        if (item == null || !item.hasItemMeta()) return CommunityNodeType.MINING;
        String id = item.getItemMeta().getPersistentDataContainer().get(typeKey(plugin), PersistentDataType.STRING);
        CommunityNodeType type = CommunityNodeType.byId(id);
        return type == null ? CommunityNodeType.MINING : type;
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

        // Drop a personal resource OneBlock node on land the player owns — their
        // xGuard claim (any world) or their own island. Works in every world.
        org.bukkit.block.Block placed = event.getBlockPlaced();
        if (!canPlaceAt(p, placed.getLocation())) {
            Msg.send(p, "<red>Place your Personal OneBlock on land you own — your claim or your island.");
            clearGhost(placed);
            return;
        }
        int limit = plugin.communityNodes().limit(p);
        if (plugin.communityNodes().count(p.getUniqueId()) >= limit) {
            Msg.send(p, "<red>You've reached your Personal OneBlock limit (" + limit + ").");
            clearGhost(placed);
            return;
        }
        CommunityNodeType type = typeOf(plugin, event.getItemInHand());
        consumeOne(p, event.getHand());
        org.bukkit.Location nodeLoc = placed.getLocation();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            plugin.communityNodes().place(p, nodeLoc.getBlock(), type);
            Msg.send(p, "<green>Personal " + type.displayName() + " OneBlock placed! <gray>Mine it — it regenerates.");
            p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_AMETHYST_BLOCK_PLACE, 0.8f, 1.0f);
        });
    }

    /** Can the player drop a node here — i.e. is this land they own (claim or island)? */
    private boolean canPlaceAt(Player p, org.bukkit.Location loc) {
        if (com.nova.novablock.compat.ClaimBridge.ownsClaimAt(p, loc)) return true;
        com.nova.novablock.island.Island island = plugin.islands().atLocation(loc);
        return island != null && island.isMember(p);
    }

    /**
     * Belt-and-braces against a lingering grant bedrock. We always cancel the place,
     * but a cancelled solid-block placement can desync and leave a real/ghost bedrock
     * when we take a deny path (no node overwrites the spot). Clear it next tick so a
     * rejected placement never leaves inert bedrock behind.
     */
    private void clearGhost(org.bukkit.block.Block placed) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (placed.getType() == Material.BEDROCK) placed.setType(Material.AIR, false);
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
