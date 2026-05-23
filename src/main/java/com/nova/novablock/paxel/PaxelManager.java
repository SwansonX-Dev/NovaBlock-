package com.nova.novablock.paxel;

import com.nova.novablock.NovaBlock;
import com.nova.novablock.progression.PlayerProgression;
import com.nova.novablock.progression.SkillType;
import com.nova.novablock.util.ItemBuilder;
import com.nova.novablock.util.Msg;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

/**
 * The Paxel: a shovel/axe/pickaxe in one. Player-bound, soulbound, unbreakable.
 * Cannot be dropped, traded, moved out of the player's inventory, or lost on death.
 * Visually evolves wooden → netherite as the player levels Mining.
 *
 * Implementation uses a pickaxe (which already drops dirt/wood/etc with the right
 * tool semantics in 1.21) plus Efficiency for speed. We re-issue the item on join
 * if it's missing.
 */
public class PaxelManager implements Listener {

    public static final NamespacedKey PAXEL_OWNER = new NamespacedKey("novablock", "paxel_owner");
    public static final NamespacedKey PAXEL_TIER = new NamespacedKey("novablock", "paxel_tier");

    /** Mining level required to reach each tier (index = tier). */
    private static final int[] TIER_LEVELS = {0, 5, 10, 15, 20, 25};
    private static final Material[] TIER_MATERIALS = {
            Material.WOODEN_PICKAXE,
            Material.STONE_PICKAXE,
            Material.IRON_PICKAXE,
            Material.GOLDEN_PICKAXE,
            Material.DIAMOND_PICKAXE,
            Material.NETHERITE_PICKAXE
    };
    private static final String[] TIER_NAMES = {
            "Wooden Paxel", "Stone Paxel", "Iron Paxel", "Gold Paxel", "Diamond Paxel", "Netherite Paxel"
    };
    private static final String[] TIER_COLORS = {
            "#A0793A", "#8C8C8C", "#E0E0E0", "#FFD24D", "#7FFFE0", "#5A2A6A"
    };

    private final NovaBlock plugin;

    public PaxelManager(NovaBlock plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    /** Tier this player has earned based on their Mining skill level. */
    public int tierFor(Player p) {
        int level = plugin.progression().get(p).getLevel(SkillType.MINING);
        int t = 0;
        for (int i = 0; i < TIER_LEVELS.length; i++) {
            if (level >= TIER_LEVELS[i]) t = i;
        }
        return t;
    }

    /** Build a fresh paxel item for the given player at the given tier. */
    public ItemStack build(Player owner, int tier) {
        tier = Math.max(0, Math.min(TIER_MATERIALS.length - 1, tier));
        Material mat = TIER_MATERIALS[tier];
        ItemStack stack = ItemBuilder.of(mat).build();
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Msg.mm("<" + TIER_COLORS[tier] + "><bold>" + TIER_NAMES[tier]
                + " <gray>(" + owner.getName() + ")")
                .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
        List<net.kyori.adventure.text.Component> lore = new java.util.ArrayList<>();
        lore.add(Msg.mm("<gray>Shovel · Axe · Pickaxe in one.").decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
        lore.add(Msg.mm("<gray>Tier <yellow>" + (tier + 1) + "/" + TIER_MATERIALS.length).decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
        lore.add(Msg.mm("<gold>★ Soulbound to " + owner.getName()).decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
        lore.add(Msg.mm("<dark_gray>Cannot be dropped or traded.").decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
        meta.lore(lore);
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES);
        meta.addEnchant(Enchantment.EFFICIENCY, Math.min(5, 2 + tier), true);
        if (tier >= 2) meta.addEnchant(Enchantment.FORTUNE, Math.min(3, tier - 1), true);
        meta.addEnchant(Enchantment.UNBREAKING, 3, true);
        meta.addEnchant(Enchantment.MENDING, 1, true);
        meta.getPersistentDataContainer().set(PAXEL_OWNER, PersistentDataType.STRING, owner.getUniqueId().toString());
        meta.getPersistentDataContainer().set(PAXEL_TIER, PersistentDataType.INTEGER, tier);
        if (meta instanceof Damageable d) d.setDamage(0);
        stack.setItemMeta(meta);
        return stack;
    }

    public boolean isPaxel(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(PAXEL_OWNER, PersistentDataType.STRING);
    }

    public boolean isOwner(ItemStack item, Player p) {
        if (!isPaxel(item)) return false;
        String owner = item.getItemMeta().getPersistentDataContainer().get(PAXEL_OWNER, PersistentDataType.STRING);
        return p.getUniqueId().toString().equals(owner);
    }

    public int tierOf(ItemStack item) {
        if (!isPaxel(item)) return -1;
        Integer t = item.getItemMeta().getPersistentDataContainer().get(PAXEL_TIER, PersistentDataType.INTEGER);
        return t == null ? 0 : t;
    }

    /** Give the player a paxel if they don't already have one. */
    public void give(Player p) {
        PlayerInventory inv = p.getInventory();
        for (ItemStack it : inv.getContents()) {
            if (isOwner(it, p)) return;
        }
        ItemStack paxel = build(p, tierFor(p));
        inv.addItem(paxel);
        Msg.actionBar(p, "<gold>Your Paxel is in your inventory.");
    }

    /** Check the player's inventory for a paxel and upgrade its tier if they've leveled up. */
    public void refreshTier(Player p) {
        PlayerInventory inv = p.getInventory();
        int target = tierFor(p);
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack it = inv.getItem(i);
            if (!isOwner(it, p)) continue;
            int current = tierOf(it);
            if (current < target) {
                inv.setItem(i, build(p, target));
                Msg.title(p, "<gold>Paxel Upgraded", "<" + TIER_COLORS[target] + ">" + TIER_NAMES[target]);
                p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_USE, 0.8f, 1.4f);
            }
            return;
        }
        // No paxel in inventory: re-issue one.
        give(p);
    }

    /** Called by BlockListener when the player mines — checks for tier promotion. */
    public void onMine(Player p, Material broken) {
        refreshTier(p);
    }

    // ---------------- listeners: lock it down ----------------

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        // Slight delay so progression cache is warm.
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (event.getPlayer().isOnline()) {
                give(event.getPlayer());
                refreshTier(event.getPlayer());
            }
        }, 25L);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (event.getPlayer().isOnline()) give(event.getPlayer());
        }, 5L);
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        if (isPaxel(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
            Msg.actionBar(event.getPlayer(), "<red>You can't drop the Paxel.");
        }
    }

    /** Prevent moving the paxel into another inventory (chests, ender chests, etc.). */
    @EventHandler
    public void onClick(InventoryClickEvent event) {
        ItemStack moved = event.getCurrentItem();
        ItemStack cursor = event.getCursor();
        boolean blocked = false;
        // Block any attempt to put the paxel into a foreign inventory
        if (event.getClickedInventory() != null
                && event.getClickedInventory().getType() != InventoryType.PLAYER
                && event.getClickedInventory().getType() != InventoryType.CRAFTING) {
            if (isPaxel(cursor) || isPaxel(moved)) blocked = true;
        }
        // Block shift-clicking paxel out of player inventory into a container
        if (event.isShiftClick() && isPaxel(moved)
                && event.getView().getTopInventory().getType() != InventoryType.CRAFTING
                && event.getView().getTopInventory().getType() != InventoryType.PLAYER) {
            blocked = true;
        }
        if (blocked) {
            event.setCancelled(true);
            if (event.getWhoClicked() instanceof Player p) Msg.actionBar(p, "<red>The Paxel stays with you.");
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        ItemStack item = event.getOldCursor();
        if (!isPaxel(item)) return;
        // Only allow drag within the player's own inventory
        for (int slot : event.getRawSlots()) {
            if (slot >= event.getView().getTopInventory().getSize()
                    && event.getView().getTopInventory().getType() == InventoryType.CRAFTING) continue;
            // top inventory slot — block it
            if (slot < event.getView().getTopInventory().getSize()
                    && event.getView().getTopInventory().getType() != InventoryType.PLAYER
                    && event.getView().getTopInventory().getType() != InventoryType.CRAFTING) {
                event.setCancelled(true);
                return;
            }
        }
    }

    /** Prevent the paxel item from appearing as a death drop. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(PlayerDeathEvent event) {
        event.getDrops().removeIf(this::isPaxel);
    }

    /** Stop other players picking up a stray paxel (shouldn't happen, but belt and braces). */
    @EventHandler
    public void onPickup(EntityPickupItemEvent event) {
        ItemStack stack = event.getItem().getItemStack();
        if (!isPaxel(stack)) return;
        if (event.getEntity() instanceof Player p && isOwner(stack, p)) return;
        event.setCancelled(true);
    }
}
