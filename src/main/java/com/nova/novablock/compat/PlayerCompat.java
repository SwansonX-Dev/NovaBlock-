package com.nova.novablock.compat;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Soft hook into Floodgate so the rest of the plugin can ask
 * "is this player on Bedrock?" without taking a hard dependency.
 *
 * On a Java-only server Floodgate is absent and every player is treated as Java.
 */
public final class PlayerCompat {

    private static boolean initialized;
    private static Object floodgateInstance;
    private static Method isFloodgatePlayer;

    private PlayerCompat() {}

    public static boolean isBedrock(Player player) {
        return player != null && isBedrock(player.getUniqueId());
    }

    public static boolean isBedrock(UUID id) {
        if (id == null) return false;
        if (!initialized) detect();
        if (floodgateInstance == null) return false;
        try {
            return (boolean) isFloodgatePlayer.invoke(floodgateInstance, id);
        } catch (Throwable t) {
            return false;
        }
    }

    private static void detect() {
        initialized = true;
        if (Bukkit.getPluginManager().getPlugin("floodgate") == null) return;
        try {
            Class<?> api = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
            floodgateInstance = api.getMethod("getInstance").invoke(null);
            isFloodgatePlayer = api.getMethod("isFloodgatePlayer", UUID.class);
        } catch (Throwable ignored) {
            floodgateInstance = null;
        }
    }
}
