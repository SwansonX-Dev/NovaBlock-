package com.nova.novablock.scoreboard;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextReplacementConfig;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;

import java.util.regex.Pattern;

/**
 * Strips the island role badge ([Owner]/[Member]/[Visitor]) out of server
 * broadcasts.
 *
 * <p>The badge is a scoreboard team prefix set by {@link RankNameplateManager}
 * so it shows above the head and in tab. Minecraft also stamps that team prefix
 * onto the player's name in death and advancement messages — where the badge is
 * noise ("who owns the ground I'm standing on" makes no sense in a death line).
 * We remove just that token from those two message types, leaving the LuckPerms
 * rank prefix and the nameplate untouched.
 *
 * <p>Match is text-based: the badge is rendered as its own gold "[Owner] "
 * component (trailing space baked in by {@code buildPrefix}), so the regex
 * removes the whole token cleanly without leaving a stray space.
 */
public class IslandBadgeMessageFilter implements Listener {

    private static final Pattern BADGE = Pattern.compile("\\[(Owner|Member|Visitor)] ?");
    private static final TextReplacementConfig STRIP = TextReplacementConfig.builder()
            .match(BADGE)
            .replacement(Component.empty())
            .build();

    private static Component strip(Component c) {
        return c == null ? null : c.replaceText(STRIP);
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        event.deathMessage(strip(event.deathMessage()));
    }

    @EventHandler
    public void onAdvancement(PlayerAdvancementDoneEvent event) {
        if (event.message() != null) event.message(strip(event.message()));
    }
}
