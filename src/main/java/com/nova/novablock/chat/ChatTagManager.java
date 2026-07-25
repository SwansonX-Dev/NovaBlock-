package com.nova.novablock.chat;

import com.nova.novablock.NovaBlock;
import com.nova.novablock.util.Msg;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Chat tags that show off what you're carrying: {@code [item]}, {@code [inv]} and
 * {@code [ender]}.
 *
 * <p>{@code [item]} becomes the held item's name with the vanilla tooltip on hover.
 * {@code [inv]} and {@code [ender]} become clickable text — hover for a short summary,
 * click to open a read-only {@link SnapshotGui}.
 *
 * <p>The inventory is <b>snapshotted at send time</b>, not read when someone clicks.
 * That means the GUI shows what the sender actually had when they posted, and the tag
 * can't be turned into a live spying tool minutes later. Snapshots expire and are
 * capped in number, so a busy chat can't grow them without bound.
 *
 * <p>Runs at {@link EventPriority#LOW} and only rewrites {@code event.message()},
 * deliberately not taking over rendering: CMI owns the chat format on this server and
 * wraps the already-substituted message rather than fighting us for it.
 */
public class ChatTagManager implements Listener {

    /** What a snapshot was taken from — decides the GUI shape. */
    public enum Kind { INVENTORY, ENDER }

    /** A frozen copy of someone's contents, with the name to title the GUI. */
    public record Snapshot(String ownerName, Kind kind, ItemStack[] contents, long expiresAt) {}

    private final NovaBlock plugin;
    /** token -> snapshot. Insertion-ordered so the oldest is the one evicted at the cap. */
    private final Map<UUID, Snapshot> snapshots =
            java.util.Collections.synchronizedMap(new LinkedHashMap<>());
    /** Last tag use per player, for the anti-spam cooldown. */
    private final Map<UUID, Long> lastUse = new ConcurrentHashMap<>();

    public ChatTagManager(NovaBlock plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    // ---------------- config ----------------

    private boolean enabled() { return plugin.getConfig().getBoolean("chat.tags.enabled", true); }
    private int cooldownSeconds() { return Math.max(0, plugin.getConfig().getInt("chat.tags.cooldown-seconds", 5)); }
    private int snapshotMinutes() { return Math.max(1, plugin.getConfig().getInt("chat.tags.snapshot-minutes", 10)); }
    private int maxSnapshots() { return Math.max(16, plugin.getConfig().getInt("chat.tags.max-snapshots", 200)); }

    // ---------------- chat ----------------

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        if (!enabled()) return;

        Component message = event.message();
        String plain = PlainTextComponentSerializer.plainText().serialize(message);
        boolean wantsItem = plain.contains("[item]");
        boolean wantsInv = plain.contains("[inv]");
        boolean wantsEnder = plain.contains("[ender]");
        if (!wantsItem && !wantsInv && !wantsEnder) return;

        Player p = event.getPlayer();
        if (!onCooldown(p)) return;

        // NOTE: this reads the inventory from the async chat thread. Contents are cloned
        // immediately, and a torn read can at worst show a stale stack in a cosmetic
        // preview — not worth blocking the chat thread on a sync round-trip for.
        if (wantsItem && p.hasPermission("novablock.chat.item")) {
            message = replaceOnce(message, "[item]", heldItemComponent(p));
        }
        if (wantsInv && p.hasPermission("novablock.chat.inv")) {
            message = replaceOnce(message, "[inv]",
                    snapshotComponent(p, Kind.INVENTORY, p.getInventory().getContents(), "Inventory", "#4FC3F7"));
        }
        if (wantsEnder && p.hasPermission("novablock.chat.ender")) {
            message = replaceOnce(message, "[ender]",
                    snapshotComponent(p, Kind.ENDER, p.getEnderChest().getContents(), "Ender Chest", "#B57EDC"));
        }

        event.message(message);
        touchCooldown(p);
    }

    /**
     * Replace only the FIRST occurrence of a tag. Without this, one message full of
     * {@code [inv][inv][inv]} would mint a snapshot per copy and spam the viewer list.
     */
    private Component replaceOnce(Component message, String literal, Component replacement) {
        return message.replaceText(TextReplacementConfig.builder()
                .matchLiteral(literal)
                .replacement(replacement)
                .times(1)
                .build());
    }

    private boolean onCooldown(Player p) {
        int cooldown = cooldownSeconds();
        if (cooldown <= 0) return true;
        Long last = lastUse.get(p.getUniqueId());
        if (last == null) return true;
        long remaining = (last + cooldown * 1000L) - System.currentTimeMillis();
        if (remaining <= 0) return true;
        Msg.actionBar(p, "<gray>Chat tags are on cooldown — <yellow>"
                + Math.max(1, remaining / 1000) + "s<gray> left.");
        return false;
    }

    private void touchCooldown(Player p) {
        if (cooldownSeconds() > 0) lastUse.put(p.getUniqueId(), System.currentTimeMillis());
    }

    // ---------------- components ----------------

    /** The held item as its display name, carrying the vanilla tooltip on hover. */
    private Component heldItemComponent(Player p) {
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (hand == null || hand.getType() == Material.AIR) {
            return Msg.mm("<gray><italic>[nothing]");
        }
        Component name = hand.displayName(); // already carries the show_item hover
        if (hand.getAmount() > 1) {
            return name.append(Msg.mm("<gray> ×" + hand.getAmount()));
        }
        return name;
    }

    /** Freeze the contents, register them under a token, and build the clickable tag. */
    private Component snapshotComponent(Player p, Kind kind, ItemStack[] contents,
                                        String label, String colour) {
        ItemStack[] copy = new ItemStack[contents.length];
        for (int i = 0; i < contents.length; i++) {
            ItemStack it = contents[i];
            copy[i] = it == null ? null : it.clone();
        }
        UUID token = store(new Snapshot(p.getName(), kind, copy,
                System.currentTimeMillis() + snapshotMinutes() * 60_000L));

        return Msg.mm("<" + colour + ">[" + label + "]")
                .hoverEvent(HoverEvent.showText(summary(copy, label)))
                .clickEvent(ClickEvent.runCommand("/obchatview " + token));
    }

    /** Short "what's in here" tooltip — the first few stacks plus a remainder count. */
    private Component summary(ItemStack[] contents, String label) {
        List<String> lines = new ArrayList<>();
        int distinct = 0;
        for (ItemStack it : contents) {
            if (it == null || it.getType() == Material.AIR) continue;
            distinct++;
            if (lines.size() < 8) {
                lines.add("<gray>· <white>" + prettyName(it.getType())
                        + (it.getAmount() > 1 ? " <gray>×" + it.getAmount() : ""));
            }
        }
        if (distinct == 0) return Msg.mm("<gray><italic>Empty " + label.toLowerCase());

        StringBuilder sb = new StringBuilder("<white><bold>" + label + "<reset><gray> — "
                + distinct + " stack" + (distinct == 1 ? "" : "s") + "<newline>");
        sb.append(String.join("<newline>", lines));
        if (distinct > lines.size()) {
            sb.append("<newline><dark_gray>…and ").append(distinct - lines.size()).append(" more");
        }
        sb.append("<newline><newline><yellow>Click to view");
        return Msg.mm(sb.toString());
    }

    private String prettyName(Material m) {
        String[] parts = m.name().toLowerCase().split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb.toString();
    }

    // ---------------- snapshot store ----------------

    private UUID store(Snapshot snapshot) {
        UUID token = UUID.randomUUID();
        synchronized (snapshots) {
            sweep();
            // Still over the cap after sweeping (a burst of fresh snapshots): drop the
            // oldest until there's room, so the map can't grow without bound.
            var it = snapshots.entrySet().iterator();
            while (snapshots.size() >= maxSnapshots() && it.hasNext()) { it.next(); it.remove(); }
            snapshots.put(token, snapshot);
        }
        return token;
    }

    /** Drop expired snapshots. Called on every insert — no scheduler needed. */
    private void sweep() {
        long now = System.currentTimeMillis();
        snapshots.entrySet().removeIf(e -> e.getValue().expiresAt() < now);
    }

    /** The snapshot behind a token, or null if it never existed or has expired. */
    public Snapshot snapshot(UUID token) {
        synchronized (snapshots) {
            Snapshot s = snapshots.get(token);
            if (s == null) return null;
            if (s.expiresAt() < System.currentTimeMillis()) { snapshots.remove(token); return null; }
            return s;
        }
    }

    public void clear() { synchronized (snapshots) { snapshots.clear(); } }
}
