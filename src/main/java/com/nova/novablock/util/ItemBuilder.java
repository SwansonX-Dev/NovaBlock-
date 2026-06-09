package com.nova.novablock.util;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class ItemBuilder {

    private final ItemStack stack;
    private final ItemMeta meta;

    private ItemBuilder(Material m, int amount) {
        this.stack = new ItemStack(m, amount);
        this.meta = stack.getItemMeta();
    }

    public static ItemBuilder of(Material m) { return new ItemBuilder(m, 1); }
    public static ItemBuilder of(Material m, int amount) { return new ItemBuilder(m, amount); }

    public ItemBuilder name(String raw) {
        meta.displayName(Msg.mm(raw).decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
        return this;
    }

    public ItemBuilder lore(String... lines) {
        List<Component> existing = meta.lore();
        List<Component> list = existing != null ? new ArrayList<>(existing) : new ArrayList<>();
        for (String l : lines) list.add(Msg.mm(l).decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
        meta.lore(list);
        return this;
    }

    public ItemBuilder lore(List<String> lines) {
        return lore(lines.toArray(new String[0]));
    }

    /** Set a PLAYER_HEAD's texture to the given player's skin. No-op on non-skull items. */
    public ItemBuilder skull(org.bukkit.OfflinePlayer owner) {
        if (meta instanceof org.bukkit.inventory.meta.SkullMeta sm) {
            sm.setOwningPlayer(owner);
        }
        return this;
    }

    public ItemBuilder glow() {
        meta.addEnchant(Enchantment.UNBREAKING, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        return this;
    }

    public ItemBuilder hideFlags() {
        meta.addItemFlags(ItemFlag.values());
        return this;
    }

    public ItemBuilder model(int data) {
        meta.setCustomModelData(data);
        return this;
    }

    public ItemBuilder tag(NamespacedKey key, String value) {
        meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, value);
        return this;
    }

    public ItemBuilder enchant(Enchantment e, int lvl) {
        meta.addEnchant(e, lvl, true);
        return this;
    }

    public ItemStack build() {
        stack.setItemMeta(meta);
        return stack;
    }
}
