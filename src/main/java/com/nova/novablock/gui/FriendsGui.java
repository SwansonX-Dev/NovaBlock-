package com.nova.novablock.gui;

import com.nova.novablock.NovaBlock;
import com.nova.novablock.social.FriendManager;
import com.nova.novablock.util.ItemBuilder;
import com.nova.novablock.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Three-section friends GUI: incoming requests on top, online friends in the
 * middle band, offline friends at the bottom. Clicking an online friend opens
 * their island via the existing /ob visit pipeline.
 */
public class FriendsGui extends ChestGui {

    private final NovaBlock plugin;

    public FriendsGui(NovaBlock plugin) {
        super("<gradient:#7B61FF:#4FC3F7><bold>Friends", 6);
        this.plugin = plugin;
    }

    @Override
    protected void build(Player p) {
        UUID self = p.getUniqueId();
        FriendManager manager = plugin.friends();

        Set<UUID> incoming = manager.incoming(self);
        List<UUID> friends = new ArrayList<>(manager.friends(self));
        friends.sort(Comparator.comparing(uuid -> {
            Player online = Bukkit.getPlayer(uuid);
            return online != null ? 0 : 1;
        }));

        // Top row: incoming requests
        if (incoming.isEmpty()) {
            set(4, ItemBuilder.of(Material.PAPER)
                    .name("<gray>No pending requests")
                    .lore("<gray>Use <yellow>/ob friend add <name></yellow>",
                            "<gray>to send one.")
                    .build(), null);
        } else {
            int slot = 0;
            for (UUID requester : incoming) {
                if (slot >= 9) break;
                OfflinePlayer op = Bukkit.getOfflinePlayer(requester);
                String name = op.getName() == null ? "?" : op.getName();
                ItemStack head = head(op,
                        "<yellow>Request: <white>" + name,
                        "<gray>Click to <green>accept<gray>.",
                        "<gray>Shift-click to <red>deny<gray>.");
                set(slot++, head, click -> {
                    p.closeInventory();
                    if (click.getClick() == ClickType.SHIFT_LEFT || click.getClick() == ClickType.SHIFT_RIGHT) {
                        if (manager.denyRequest(self, requester)) {
                            Msg.send(p, "<gray>Denied request from " + name + ".");
                        }
                    } else {
                        var r = manager.acceptRequest(self, requester);
                        if (r == FriendManager.AcceptResult.OK) {
                            Msg.send(p, "<green>You and <yellow>" + name + "<green> are now friends.");
                            Player online = op.getPlayer();
                            if (online != null) Msg.send(online,
                                    "<green>You and <yellow>" + p.getName() + "<green> are now friends.");
                        }
                    }
                    new FriendsGui(plugin).open(p);
                });
            }
        }

        // Middle/bottom: friends. Online first.
        int slot = 9;
        if (friends.isEmpty()) {
            set(31, ItemBuilder.of(Material.BARRIER)
                    .name("<gray>No friends yet")
                    .lore("<gray>Add some with <yellow>/ob friend add <name></yellow>.",
                            "<gray>You can see when they log in,",
                            "<gray>and visit their islands from this menu.")
                    .build(), null);
        } else {
            for (UUID fid : friends) {
                if (slot >= 54) break;
                OfflinePlayer op = Bukkit.getOfflinePlayer(fid);
                Player online = op.getPlayer();
                String name = op.getName() == null ? "?" : op.getName();
                String status = online != null ? "<green>Online" : "<dark_gray>Offline";
                Consumer<InventoryClickEvent> handler = online != null
                        ? click -> {
                            p.closeInventory();
                            p.performCommand("ob visit " + name);
                        }
                        : click -> {
                            // Shift-click on an offline friend removes them — cheap rage-quit guardrail.
                            if (click.getClick() == ClickType.SHIFT_LEFT || click.getClick() == ClickType.SHIFT_RIGHT) {
                                if (manager.removeFriend(self, fid)) {
                                    Msg.send(p, "<gray>Removed " + name + " from your friends.");
                                }
                                new FriendsGui(plugin).open(p);
                            }
                        };
                ItemStack head = head(op,
                        (online != null ? "<green>" : "<gray>") + name,
                        status,
                        online != null ? "<yellow>Click to visit their island." : "<dark_gray>Shift-click to remove.");
                set(slot++, head, handler);
            }
        }

        set(49, ItemBuilder.of(Material.NETHER_STAR)
                .name("<gradient:#7B61FF:#4FC3F7><bold>Main Menu")
                .lore("<dark_gray>/ob menu").glow().build(),
                e -> new MainMenuGui(plugin).open(p));

        fill(Material.GRAY_STAINED_GLASS_PANE, " ");
    }

    private static ItemStack head(OfflinePlayer op, String name, String... lore) {
        ItemStack stack = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) stack.getItemMeta();
        if (meta != null) {
            meta.setOwningPlayer(op);
            meta.displayName(Msg.mm(name).decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
            List<net.kyori.adventure.text.Component> loreList = new ArrayList<>();
            for (String l : lore) loreList.add(Msg.mm(l).decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
            meta.lore(loreList);
            stack.setItemMeta(meta);
        }
        return stack;
    }
}
