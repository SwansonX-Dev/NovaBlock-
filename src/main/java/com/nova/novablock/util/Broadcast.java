package com.nova.novablock.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Gamemode-scoped announcements.
 *
 * <p>Every NovaBlock announcement used to go out through {@code Bukkit.broadcast},
 * which reaches every player on the server. That was fine when the box ran one
 * gamemode; it isn't now — a Hardcore or Boxed player has no use for a OneBlock
 * prestige, a community raid call, or a weekly sprint podium, and the noise reads
 * as spam from a plugin they aren't playing.
 *
 * <p>Who counts as playing is decided by {@link GamemodeScope}, and is configurable
 * via {@code broadcast.scope}. The console always receives the message regardless
 * of scope — these lines double as the server log of what the plugin did.
 */
public final class Broadcast {

    private Broadcast() {}

    /** Announce a MiniMessage line to the gamemode. */
    public static void mm(String raw, TagResolver... resolvers) {
        send(Msg.mm(raw, resolvers));
    }

    /** Announce an already-built component to the gamemode. */
    public static void send(Component message) {
        if (message == null) return;
        Bukkit.getConsoleSender().sendMessage(message);
        for (Player p : GamemodeScope.audience()) p.sendMessage(message);
    }
}
