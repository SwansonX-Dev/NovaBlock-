package com.nova.novablock.lootroom;

import com.nova.novablock.island.Island;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;

import java.util.ArrayList;
import java.util.List;

public interface LootRoom {

    String id();
    String displayName();

    /** Build the room into the world at the given anchor (north-west bottom corner). Return entry teleport. */
    Location build(Location anchor);

    /** Called once per second during the run; should award & finish via finish(). */
    void tick(LootRoomRun run);

    /** Called when the player enters the room — initialize state. */
    default void onStart(LootRoomRun run, Player player) {}

    /** Coin reward when the player completes. */
    default int rewardCoins(Island island) { return 800 + island.data().getPhaseIndex() * 200; }

    /**
     * Item drops awarded on completion. Each room overrides with thematic loot.
     * The default gives a baseline of XP + a golden apple so rooms without an
     * override never feel empty.
     */
    default List<ItemStack> rewardItems(Island island) {
        int phase = island.data().getPhaseIndex();
        List<ItemStack> out = new ArrayList<>();
        out.add(new ItemStack(Material.EXPERIENCE_BOTTLE, 4 + phase));
        out.add(new ItemStack(Material.GOLDEN_APPLE, 1 + phase / 3));
        return out;
    }

    /** Helper for building enchanted-book drops. */
    static ItemStack enchantedBook(Enchantment ench, int level) {
        ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
        EnchantmentStorageMeta meta = (EnchantmentStorageMeta) book.getItemMeta();
        meta.addStoredEnchant(ench, Math.max(1, level), true);
        book.setItemMeta(meta);
        return book;
    }
}
