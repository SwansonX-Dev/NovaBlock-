package com.nova.novablock.gui;

import com.nova.novablock.NovaBlock;
import com.nova.novablock.social.FriendManager;
import com.nova.novablock.util.ItemBuilder;
import com.nova.novablock.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Player-picker for adding friends without typing names. Lists every other
 * online player as a clickable head; clicking sends a friend request (or accepts
 * one already incoming from them). Paginated so it scales past one chest.
 *
 * <p>Online-only by design — picking from a list is the point. Offline players
 * can still be added by name with {@code /ob friend add <name>}.
 */
public class AddFriendGui extends ChestGui {

    private static final int PER_PAGE = 45; // top 5 rows; bottom row is nav

    private final NovaBlock plugin;
    private final int page;

    public AddFriendGui(NovaBlock plugin, int page) {
        super("<gradient:#7B61FF:#4FC3F7><bold>Add Friend", 6);
        this.plugin = plugin;
        this.page = Math.max(0, page);
    }

    @Override
    protected void build(Player p) {
        UUID self = p.getUniqueId();
        FriendManager manager = plugin.friends();
        Set<UUID> friends = manager.friends(self);
        Set<UUID> outgoing = manager.outgoing(self);
        Set<UUID> incoming = manager.incoming(self);

        // Candidates: everyone online except yourself and people you're already friends with.
        List<Player> candidates = new ArrayList<>();
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.getUniqueId().equals(self)) continue;
            if (friends.contains(online.getUniqueId())) continue;
            candidates.add(online);
        }
        candidates.sort(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER));

        int maxPage = candidates.isEmpty() ? 0 : (candidates.size() - 1) / PER_PAGE;
        int safePage = Math.min(page, maxPage);
        int start = safePage * PER_PAGE;
        int end = Math.min(start + PER_PAGE, candidates.size());

        if (candidates.isEmpty()) {
            set(22, ItemBuilder.of(Material.BARRIER)
                    .name("<gray>No one else online")
                    .lore("<gray>No other players are online to add right now.",
                            "<gray>You can still add by name with",
                            "<yellow>/ob friend add <name></yellow>.")
                    .build(), null);
        }

        int slot = 0;
        for (int i = start; i < end; i++) {
            Player target = candidates.get(i);
            UUID targetId = target.getUniqueId();
            String name = target.getName();

            boolean alreadySent = outgoing.contains(targetId);
            boolean wantsYou = incoming.contains(targetId);

            String action;
            if (wantsYou) action = "<green>Click to accept their request.";
            else if (alreadySent) action = "<yellow>Request already sent.";
            else action = "<green>Click to send a friend request.";

            ItemStack head = head(target,
                    "<white>" + name,
                    wantsYou ? "<aqua>Wants to be your friend" : "<green>Online",
                    action);

            set(slot++, head, click -> {
                FriendManager.AddResult r = manager.addRequest(self, targetId);
                switch (r) {
                    case SENT -> {
                        Msg.send(p, "<green>Friend request sent to <yellow>" + name + "<green>.");
                        Player online = Bukkit.getPlayer(targetId);
                        if (online != null) Msg.send(online,
                                "<aqua>Friend request from <yellow>" + p.getName()
                                        + "<aqua>. <gray>(<yellow>/ob friend accept " + p.getName() + "<gray>)");
                    }
                    case ACCEPTED_INCOMING -> {
                        Msg.send(p, "<green>You and <yellow>" + name + "<green> are now friends.");
                        Player online = Bukkit.getPlayer(targetId);
                        if (online != null) Msg.send(online,
                                "<green>You and <yellow>" + p.getName() + "<green> are now friends.");
                    }
                    case ALREADY_FRIENDS -> Msg.send(p, "<yellow>You're already friends with " + name + ".");
                    case ALREADY_SENT -> Msg.send(p, "<yellow>You already sent a request to " + name + ".");
                    case SELF -> Msg.send(p, "<red>You can't friend yourself.");
                }
                new AddFriendGui(plugin, safePage).open(p);
            });
        }

        // Bottom-row nav.
        if (safePage > 0) {
            set(45, ItemBuilder.of(Material.ARROW)
                    .name("<yellow>Previous page")
                    .lore("<gray>Page " + safePage + " / " + (maxPage + 1)).build(),
                    e -> new AddFriendGui(plugin, safePage - 1).open(p));
        }
        if (safePage < maxPage) {
            set(53, ItemBuilder.of(Material.ARROW)
                    .name("<yellow>Next page")
                    .lore("<gray>Page " + (safePage + 2) + " / " + (maxPage + 1)).build(),
                    e -> new AddFriendGui(plugin, safePage + 1).open(p));
        }
        set(49, ItemBuilder.of(Material.OAK_DOOR)
                .name("<yellow>Back to Friends")
                .lore("<dark_gray>/ob friend").build(),
                e -> new FriendsGui(plugin).open(p));

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
