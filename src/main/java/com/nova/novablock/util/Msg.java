package com.nova.novablock.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class Msg {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private Msg() {}

    public static Component mm(String raw, TagResolver... resolvers) {
        return MM.deserialize(raw, resolvers);
    }

    public static Component prefixed(String raw, TagResolver... resolvers) {
        return mm(Messages.prefix() + raw, resolvers);
    }

    public static void send(CommandSender to, String raw, TagResolver... resolvers) {
        to.sendMessage(prefixed(raw, resolvers));
    }

    public static void raw(CommandSender to, String raw, TagResolver... resolvers) {
        to.sendMessage(mm(raw, resolvers));
    }

    public static void actionBar(Player p, String raw, TagResolver... resolvers) {
        p.sendActionBar(mm(raw, resolvers));
    }

    public static void title(Player p, String title, String subtitle) {
        p.showTitle(net.kyori.adventure.title.Title.title(mm(title), mm(subtitle)));
    }

    public static TagResolver ph(String key, String value) {
        return Placeholder.parsed(key, value == null ? "" : value);
    }
}
