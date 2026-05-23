package com.nova.novablock.gui;

import com.nova.novablock.NovaBlock;
import com.nova.novablock.island.Island;
import com.nova.novablock.util.ItemBuilder;
import com.nova.novablock.util.Msg;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Locale;

public class ShopGui extends ChestGui {

    private final NovaBlock plugin;

    public ShopGui(NovaBlock plugin) {
        super("<green><bold>Island Shop", 4);
        this.plugin = plugin;
    }

    @Override
    protected void build(Player p) {
        Island island = plugin.islands().ofPlayer(p);
        long bal = plugin.economy().balance(island);

        offer(p, island, 10, Material.IRON_INGOT, 16, 250, "Iron Ingots x16");
        offer(p, island, 11, Material.GOLD_INGOT, 8, 400, "Gold Ingots x8");
        offer(p, island, 12, Material.DIAMOND, 1, 1500, "Diamond");
        offer(p, island, 13, Material.NETHERITE_INGOT, 1, 25_000, "Netherite Ingot");
        offer(p, island, 14, Material.ENDER_PEARL, 4, 1200, "Ender Pearls x4");
        offer(p, island, 15, Material.EXPERIENCE_BOTTLE, 16, 800, "XP Bottles x16");
        offer(p, island, 16, Material.GOLDEN_APPLE, 4, 2000, "Golden Apples x4");

        offer(p, island, 19, Material.OAK_SAPLING, 8, 100, "Saplings x8");
        offer(p, island, 20, Material.WHEAT_SEEDS, 16, 50, "Seeds x16");
        offer(p, island, 21, Material.TORCH, 32, 100, "Torches x32");
        offer(p, island, 22, Material.BUCKET, 1, 200, "Bucket");
        offer(p, island, 23, Material.SHEARS, 1, 300, "Shears");

        offer(p, island, 25, Material.ENDER_CHEST, 1, 5000, "Ender Chest");

        set(31, ItemBuilder.of(Material.SUNFLOWER)
                .name("<gold>Balance")
                .lore("<yellow>" + String.format(Locale.US, "%,d", bal) + " coins").glow().build(), null);

        set(27, ItemBuilder.of(Material.ARROW).name("<gray>Back").build(),
                e -> new MainMenuGui(plugin).open(p));
        fill(Material.BLACK_STAINED_GLASS_PANE, " ");
    }

    private void offer(Player p, Island island, int slot, Material material, int amount, long cost, String name) {
        set(slot, ItemBuilder.of(material, amount)
                .name("<green>" + name)
                .lore("<gold>Cost: <yellow>" + cost + " coins", "<gray>Tap to buy.")
                .build(), e -> {
            if (!plugin.economy().spend(island, cost)) {
                Msg.actionBar(p, "<red>Not enough coins.");
                return;
            }
            p.getInventory().addItem(new ItemStack(material, amount));
            Msg.actionBar(p, "<green>Bought " + amount + "x " + material.name());
            open(p);
        });
    }
}
