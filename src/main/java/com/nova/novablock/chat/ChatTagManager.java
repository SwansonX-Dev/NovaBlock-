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
    /**
     * The legacy chat format captured for the message currently being processed.
     * Paper fires the legacy event and then {@code processModern} on the same thread,
     * synchronously, so a thread-local carries it safely between the two.
     */
    private final ThreadLocal<String> legacyFormat = new ThreadLocal<>();
    /** token -> snapshot. Insertion-ordered so the oldest is the one evicted at the cap. */
    private final Map<UUID, Snapshot> snapshots =
            java.util.Collections.synchronizedMap(new LinkedHashMap<>());
    /** Last tag use per player, for the anti-spam cooldown. */
    private final Map<UUID, Long> lastUse = new ConcurrentHashMap<>();

    public ChatTagManager(NovaBlock plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        // One tick in, so every plugin has enabled and registered its listeners.
        plugin.getServer().getScheduler().runTask(plugin, this::warnIfLegacyChatPipeline);
    }

    /**
     * Shout if anything on the server has put chat on Paper's legacy pipeline, because
     * that silently breaks these tags.
     *
     * <p>Paper decides between its modern and legacy chat paths server-wide by asking
     * whether <i>any</i> plugin has registered a listener on the deprecated
     * {@code AsyncPlayerChatEvent}/{@code PlayerChatEvent}. If one has, the finished
     * message is rendered by serialising it to a legacy section-code string and parsing
     * it straight back. Colours survive that round trip; hover and click events cannot
     * be represented in legacy text at all, so they are dropped. The tags still appear,
     * coloured and correct, and simply do nothing.
     *
     * <p>Nothing we can do at our end fixes it — the flattening happens after every
     * chat listener has run — so the only cure is the named plugin dropping its legacy
     * listener. Naming it here turns a baffling symptom into a one-line log answer.
     */
    private void warnIfLegacyChatPipeline() {
        java.util.Set<String> culprits = new java.util.LinkedHashSet<>();
        for (var l : org.bukkit.event.player.AsyncPlayerChatEvent.getHandlerList().getRegisteredListeners()) {
            culprits.add(l.getPlugin().getName());
        }
        for (var l : org.bukkit.event.player.PlayerChatEvent.getHandlerList().getRegisteredListeners()) {
            culprits.add(l.getPlugin().getName());
        }
        if (culprits.isEmpty()) return;

        plugin.getLogger().info("Chat is on Paper's LEGACY pipeline because these plugins listen to the "
                + "deprecated chat event: " + String.join(", ", culprits) + ". That path re-renders every "
                + "message through legacy colour codes, which strips all hover and click events. "
                + "Rendering messages that use the [item]/[inv]/[ender] tags ourselves so they keep theirs; "
                + "all other chat is left alone.");
        registerLegacyFormatProbe();
    }

    /**
     * Watch the legacy event purely to learn the chat format other plugins settled on,
     * so {@link #preservingRenderer} can reproduce it around our message.
     *
     * <p>Registered only once something else has already forced the legacy pipeline —
     * NovaBlock must never be the plugin that puts chat there. MONITOR so we read the
     * format after every other plugin has finished changing it, and nothing is modified.
     */
    private void registerLegacyFormatProbe() {
        Listener probe = new Listener() {};
        plugin.getServer().getPluginManager().registerEvent(
                org.bukkit.event.player.AsyncPlayerChatEvent.class, probe, EventPriority.MONITOR,
                (listener, event) -> {
                    if (event instanceof org.bukkit.event.player.AsyncPlayerChatEvent chat) {
                        legacyFormat.set(chat.getFormat());
                    }
                },
                plugin, true);
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

        // If chat is on the legacy pipeline, the default renderer would flatten the
        // hover and click events we just built straight back out again. Render this one
        // message ourselves instead. Only messages that actually carry a tag get this
        // treatment, so ordinary chat keeps rendering exactly as it does today.
        String format = legacyFormat.get();
        legacyFormat.remove();
        if (format != null) event.renderer(preservingRenderer(format));

        touchCooldown(p);
    }

    /**
     * Rebuilds the chat line from the legacy format string without ever passing our own
     * message through a legacy serializer.
     *
     * <p>The format is rendered with a marker standing in for the message, then split at
     * the marker: the surrounding text (the other plugin's prefix/suffix, colour codes and
     * all) is parsed from legacy, and the real message component is appended between them
     * untouched — so its hover and click events survive.
     *
     * <p>Caveat: a trailing colour code in the format meant to colour the message body no
     * longer bleeds into it, because the message is a sibling rather than a continuation.
     * Tag messages may therefore render in the default colour where they previously took
     * the format's. Only tag messages are affected.
     */
    private io.papermc.paper.chat.ChatRenderer preservingRenderer(String format) {
        return (source, sourceDisplayName, message, viewer) -> {
            var legacy = net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection();
            String marker = " novablock-message ";
            String rendered;
            try {
                rendered = String.format(format, legacy.serialize(sourceDisplayName), marker);
            } catch (Exception e) {
                // A format we can't fill is not worth losing the message over.
                return message;
            }
            int at = rendered.indexOf(marker);
            if (at < 0) return legacy.deserialize(rendered);
            // Deliberately built by appending to a plain Component rather than through
            // Component.text()'s builder. A builder's build() is a generic method whose
            // erased return type changed between Adventure 4 and 5, so a jar compiled
            // against one throws NoSuchMethodError on the other; Component.append is a
            // plain Component -> Component call with the same descriptor on both.
            Component before = legacy.deserialize(rendered.substring(0, at));
            Component after = legacy.deserialize(rendered.substring(at + marker.length()));
            return before.append(message).append(after);
        };
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
