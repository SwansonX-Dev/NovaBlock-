package com.nova.novablock.listener;

import com.nova.novablock.NovaBlock;
import com.nova.novablock.community.CommunityHubManager;
import com.nova.novablock.util.Msg;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

/**
 * The "Community OneBlock" reward item — a tagged bedrock that, when placed on
 * the player's own claim in the community OneBlock world, spawns a brand-new
 * <em>shared</em> community OneBlock at that spot. Unlike the Personal OneBlock node
 * ({@link OneBlockGrantListener}), the result is a real community block: anyone
 * may mine it, it shares the community phase progression, and it feeds the
 * shared payout pool — the player only chooses where it appears. One-time use;
 * distributed via {@code /obadmin givecommunityblock} so crates, shops, or
 * rewards can hand it out.
 */
public final class CommunityBlockGrantListener implements Listener {

    private final NovaBlock plugin;

    public CommunityBlockGrantListener(NovaBlock plugin) { this.plugin = plugin; }

    private static NamespacedKey key(NovaBlock plugin) {
        return new NamespacedKey(plugin, "community_oneblock_grant");
    }

    /** Build {@code amount} Community OneBlock reward items (tagged bedrock). */
    public static ItemStack create(NovaBlock plugin, int amount) {
        ItemStack item = new ItemStack(Material.BEDROCK, Math.max(1, Math.min(64, amount)));
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Msg.mm("<!italic><gradient:#FFD54F:#FFF59D><bold>Community OneBlock"));
            meta.lore(List.of(
                    Msg.mm("<!italic><gray>Place it on <gold>your own claim<gray> in the"),
                    Msg.mm("<!italic><gray>community world to spawn a <white>shared<gray> OneBlock"),
                    Msg.mm("<!italic><gray>anyone can mine. Shares the community phase & pool."),
                    Msg.mm("<!italic> "),
                    Msg.mm("<!italic><dark_gray>Sneak-punch to reclaim · your claim in the community world.")));
            meta.getPersistentDataContainer().set(key(plugin), PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        return item;
    }

    /** True if {@code item} is a Community OneBlock reward item. */
    public static boolean is(NovaBlock plugin, ItemStack item) {
        if (item == null || item.getType() != Material.BEDROCK || !item.hasItemMeta()) return false;
        Byte b = item.getItemMeta().getPersistentDataContainer().get(key(plugin), PersistentDataType.BYTE);
        return b != null && b == (byte) 1;
    }

    // LOWEST + ignoreCancelled=false: intercept before any build-protection plugin
    // cancels the place, and always cancel it ourselves so a real bedrock is never
    // placed — we register a community OneBlock at the spot instead.
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onPlace(BlockPlaceEvent event) {
        if (!is(plugin, event.getItemInHand())) return;
        event.setCancelled(true);
        Player p = event.getPlayer();

        Block placed = event.getBlockPlaced();
        CommunityHubManager hub = plugin.community();
        if (hub == null || !hub.isEnabled()) {
            Msg.send(p, "<red>The community hub is currently disabled.");
            clearGhost(placed);
            return;
        }
        if (!placed.getWorld().getName().equals(hub.communityWorldName())) {
            Msg.send(p, "<red>A Community OneBlock can only be placed in the community world. "
                    + "Use <yellow>/warp community<red> first.");
            clearGhost(placed);
            return;
        }
        if (!com.nova.novablock.compat.ClaimBridge.ownsClaimAt(p, placed.getLocation())) {
            Msg.send(p, "<red>You can only place a Community OneBlock on your own claim.");
            clearGhost(placed);
            return;
        }
        if (hub.isCommunityBlock(placed.getLocation())) {
            Msg.send(p, "<red>There's already a community OneBlock there.");
            clearGhost(placed);
            return;
        }
        if (hub.oneblockCount() >= hub.maxOneblocks()) {
            Msg.send(p, "<red>The community has reached its OneBlock limit (" + hub.maxOneblocks() + ").");
            clearGhost(placed);
            return;
        }

        consumeOne(p, event.getHand());
        Location at = placed.getLocation();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (hub.addOneblockAt(at, p.getUniqueId())) {
                Msg.send(p, "<green>New community OneBlock placed! <gray>Anyone can mine it — "
                        + "it shares the community phase.");
                p.playSound(p.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.8f, 1.2f);
            } else {
                // Lost the spot/limit between the checks above and this tick — refund
                // and make sure no inert grant bedrock is left behind.
                refund(p);
                if (placed.getType() == Material.BEDROCK) placed.setType(Material.AIR, false);
                Msg.send(p, "<red>Couldn't place the community OneBlock there — your item was returned.");
            }
        });
    }

    /**
     * Belt-and-braces against a lingering grant bedrock. We always cancel the place,
     * but a cancelled solid-block placement can desync and leave a real/ghost bedrock
     * when we take a deny path (no community block overwrites the spot). Clear it next
     * tick so a rejected placement never leaves inert bedrock behind.
     */
    private void clearGhost(Block placed) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (placed.getType() == Material.BEDROCK) placed.setType(Material.AIR, false);
        });
    }

    /**
     * Sneak-punch (sneak + left-click) a placed shared community OneBlock to remove it:
     * the <b>owner</b> who placed it gets the reward item back; a <b>novablock.admin</b>
     * can clear anyone's (no item returned — admin cleanup). Anyone else just mines it
     * normally. Mirrors the personal-node reclaim. The implicit default block (no owner,
     * not in config) can't be reclaimed this way — admins manage it via /obadmin hub oneblock.
     */
    @EventHandler(ignoreCancelled = false)
    public void onReclaimPunch(PlayerInteractEvent event) {
        if (event.getAction() != Action.LEFT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND) return;
        Block block = event.getClickedBlock();
        if (block == null) return;
        Player p = event.getPlayer();
        if (!p.isSneaking()) return;

        CommunityHubManager hub = plugin.community();
        if (hub == null || !hub.isEnabled()) return;
        Location loc = block.getLocation();
        if (!hub.isCommunityBlock(loc)) return;

        java.util.UUID owner = hub.ownerAt(loc);
        boolean isOwner = owner != null && owner.equals(p.getUniqueId());
        boolean isAdmin = p.hasPermission("novablock.admin");
        if (!isOwner && !isAdmin) return; // not theirs — let them mine it normally

        event.setCancelled(true);
        if (!hub.removeOneblockAt(loc)) return;
        if (isOwner) {
            for (ItemStack leftover : p.getInventory().addItem(create(plugin, 1)).values()) {
                p.getWorld().dropItemNaturally(p.getLocation(), leftover);
            }
            Msg.send(p, "<gray>Community OneBlock reclaimed — your reward item was returned.");
        } else {
            Msg.send(p, "<gray>Removed that community OneBlock.");
        }
        p.playSound(p.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 0.8f, 1.0f);
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
        for (ItemStack leftover : p.getInventory().addItem(create(plugin, 1)).values()) {
            p.getWorld().dropItemNaturally(p.getLocation(), leftover);
        }
    }
}
