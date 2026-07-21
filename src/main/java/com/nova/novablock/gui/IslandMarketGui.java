package com.nova.novablock.gui;

import com.nova.novablock.NovaBlock;
import com.nova.novablock.island.Island;
import com.nova.novablock.island.IslandData;
import com.nova.novablock.island.IslandMarketService;
import com.nova.novablock.util.ItemBuilder;
import com.nova.novablock.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/** Browse islands listed for sale and buy one. */
public class IslandMarketGui extends ChestGui {

    private static final int[] SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34};

    private final NovaBlock plugin;

    public IslandMarketGui(NovaBlock plugin) {
        super("<#7B61FF><bold>Island Market", 5);
        this.plugin = plugin;
    }

    @Override
    protected void build(Player p) {
        List<IslandMarketService.Listing> listings = plugin.market().listings();

        if (listings.isEmpty()) {
            set(22, ItemBuilder.of(Material.BARRIER)
                    .name("<gray>No islands for sale")
                    .lore("<dark_gray>List yours with <yellow>/ob sell <price>")
                    .build(), null);
        }

        int i = 0;
        for (IslandMarketService.Listing listing : listings) {
            if (i >= SLOTS.length) break;
            Island island = plugin.islands().get(listing.islandId());
            if (island == null) continue;   // defensive: load() prunes these
            IslandData data = island.data();

            String sellerName = Bukkit.getOfflinePlayer(listing.seller()).getName();
            if (sellerName == null) sellerName = "Unknown";

            List<String> lore = new ArrayList<>();
            lore.add("<gray>Seller: <white>" + sellerName);
            lore.add("<gray>Level: <white>" + data.getLevel());
            lore.add("<gray>Phase: <white>" + (data.getPhaseIndex() + 1));
            lore.add("<gray>Prestige: <white>" + data.getTotalPrestigeLevel());
            lore.add("<gray>Blocks broken: <white>" + data.getBlocksBroken());
            lore.add(" ");
            lore.add("<gray>Price: <gold>" + plugin.economy().format(listing.price()));
            lore.add(" ");
            // Tell them up front why they can't buy, rather than on click.
            if (listing.seller().equals(p.getUniqueId())) {
                lore.add("<dark_gray>Your own listing.");
            } else if (plugin.islands().ofPlayer(p) != null) {
                lore.add("<red>You must sell your island first.");
            } else if (plugin.economy().balance(p) < listing.price()) {
                lore.add("<red>You can't afford this.");
            } else {
                lore.add("<yellow>Click to buy.");
            }

            final java.util.UUID islandId = listing.islandId();
            set(SLOTS[i++], ItemBuilder.of(Material.GRASS_BLOCK)
                    .name("<#7B61FF>" + sellerName + "'s island")
                    .lore(lore).build(), e -> attemptBuy(p, islandId));
        }

        set(40, ItemBuilder.of(Material.ARROW).name("<gray>Close").build(), e -> p.closeInventory());
        fill(Material.BLACK_STAINED_GLASS_PANE, " ");
    }

    private void attemptBuy(Player p, java.util.UUID islandId) {
        IslandMarketService.Result result = plugin.market().buy(p, islandId);
        switch (result) {
            case OK -> {
                p.closeInventory();
                Msg.send(p, "<green>Island purchased! Use <yellow>/ob home<green> to visit it.");
                p.playSound(p.getLocation(), org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
            }
            case OWN_LISTING -> Msg.send(p, "<red>That's your own island.");
            case BUYER_HAS_ISLAND -> Msg.send(p,
                    "<red>You already have an island. Sell it first with <yellow>/ob sell <price><red>.");
            case CANT_AFFORD -> Msg.send(p, "<red>You can't afford that island.");
            case NOT_LISTED -> {
                Msg.send(p, "<red>That island is no longer for sale.");
                open(p);
            }
            case ISLAND_GONE -> {
                Msg.send(p, "<red>That island no longer exists.");
                open(p);
            }
            default -> Msg.send(p, "<red>Purchase failed.");
        }
    }
}
