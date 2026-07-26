package com.nova.novablock.util;

import com.nova.novablock.NovaBlock;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Which worlds and players belong to the NovaBlock gamemode.
 *
 * <p>The server runs several gamemodes side by side in their own worlds, so
 * "everyone online" is no longer the same audience as "everyone playing this".
 * Anything that reaches out to {@code Bukkit.getOnlinePlayers()} — announcements,
 * server-event titles, the AFK sweep — has to filter through here first, or it
 * leaks into gamemodes that never asked for it.
 *
 * <p>Membership is decided by the world the player is standing in, not by what
 * they own. Someone with an island who is currently off playing Hardcore is
 * exactly the person being spammed, so an island must not opt them back in.
 */
public final class GamemodeScope {

    /**
     * Prefix carried by every private loot-room instance world
     * ({@code LootRoomManager.createInstanceWorld}). Instances are created and
     * destroyed per run, so they can't be listed — they have to be matched.
     */
    private static final String LOOT_INSTANCE_PREFIX = "novablock_loot_";

    private GamemodeScope() {}

    /** True if this world belongs to NovaBlock. */
    public static boolean isGamemodeWorld(World world) {
        NovaBlock plugin = NovaBlock.get();
        return plugin != null && isGamemodeWorld(plugin, world);
    }

    /** True if this player is currently playing NovaBlock. */
    public static boolean isPlaying(Player p) {
        return p != null && isGamemodeWorld(p.getWorld());
    }

    static boolean isGamemodeWorld(NovaBlock plugin, World world) {
        if (world == null) return false;
        String name = world.getName();

        if (name.toLowerCase(Locale.ROOT).startsWith(LOOT_INSTANCE_PREFIX)) return true;

        var worlds = plugin.worlds();
        if (worlds != null && (name.equalsIgnoreCase(worlds.worldName())
                || name.equalsIgnoreCase(worlds.netherWorldName())
                || name.equalsIgnoreCase(worlds.endWorldName()))) {
            return true;
        }
        var community = plugin.community();
        if (community != null && name.equalsIgnoreCase(community.communityWorldName())) return true;

        for (String extra : plugin.getConfig().getStringList("gamemode.extra-worlds")) {
            if (extra != null && name.equalsIgnoreCase(extra)) return true;
        }
        return false;
    }

    /**
     * The players an announcement reaches — use for the non-chat half of an
     * announcement too (titles, sounds), so the effect and the message agree on
     * who is listening.
     */
    public static List<Player> audience() {
        NovaBlock plugin = NovaBlock.get();
        List<Player> out = new ArrayList<>();
        if (plugin == null) return out;

        String scope = scope(plugin);
        boolean everyone = scope.equals("server");
        boolean includeMembers = scope.equals("members");
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (everyone
                    || isGamemodeWorld(plugin, p.getWorld())
                    || (includeMembers && hasIsland(plugin, p))) {
                out.add(p);
            }
        }
        return out;
    }

    private static boolean hasIsland(NovaBlock plugin, Player p) {
        return plugin.islands() != null && plugin.islands().ofPlayer(p) != null;
    }

    /** {@code world} (default), {@code members}, or {@code server}. */
    static String scope(NovaBlock plugin) {
        String raw = plugin.getConfig().getString("gamemode.broadcast-scope", "world");
        if (raw == null) return "world";
        String v = raw.trim().toLowerCase(Locale.ROOT);
        return switch (v) {
            case "server", "members", "world" -> v;
            default -> "world"; // a typo must not silently reopen the spam
        };
    }
}
