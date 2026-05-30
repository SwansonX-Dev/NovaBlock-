package com.nova.novablock.compat;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.lang.reflect.Method;
import java.util.Locale;
import java.util.UUID;

public final class LuckPermsRanks {
    private LuckPermsRanks() {}

    public static String rankedName(OfflinePlayer player, String fallbackName, String nameColor) {
        String name = fallbackName == null || fallbackName.isBlank() ? "Unknown" : fallbackName;
        String rank = rank(player);
        if (rank.isBlank()) return nameColor + name;
        return "<gray>[<#FFC940>" + rank + "<gray>] " + nameColor + name;
    }

    public static String rank(OfflinePlayer player) {
        if (player == null || Bukkit.getPluginManager().getPlugin("LuckPerms") == null) return "";
        return readRank(player);
    }

    private static String readRank(OfflinePlayer player) {
        try {
            Class<?> providerClass = Class.forName("net.luckperms.api.LuckPermsProvider");
            Object luckPerms = providerClass.getMethod("get").invoke(null);
            Object userManager = luckPerms.getClass().getMethod("getUserManager").invoke(luckPerms);
            Object user = userManager.getClass().getMethod("getUser", UUID.class).invoke(userManager, player.getUniqueId());
            if (user == null) return "";

            String primaryGroup = String.valueOf(user.getClass().getMethod("getPrimaryGroup").invoke(user));
            String displayName = groupDisplayName(luckPerms, primaryGroup);
            return clean(displayName == null || displayName.isBlank() ? primaryGroup : displayName);
        } catch (ReflectiveOperationException | RuntimeException ex) {
            return "";
        }
    }

    private static String groupDisplayName(Object luckPerms, String primaryGroup) {
        try {
            Object groupManager = luckPerms.getClass().getMethod("getGroupManager").invoke(luckPerms);
            Object group = groupManager.getClass().getMethod("getGroup", String.class).invoke(groupManager, primaryGroup);
            if (group == null) return null;
            Method displayName = group.getClass().getMethod("getDisplayName");
            Object value = displayName.invoke(group);
            return value == null ? null : String.valueOf(value);
        } catch (ReflectiveOperationException | RuntimeException ex) {
            return null;
        }
    }

    private static String clean(String raw) {
        String value = raw == null ? "" : raw
                .replaceAll("(?i)[&§][0-9A-FK-ORX]", "")
                .replaceAll("<[^>]+>", "")
                .replace('<', ' ')
                .replace('>', ' ')
                .trim();
        if (value.isBlank()) return "";
        String[] parts = value.toLowerCase(Locale.ROOT).replace('-', '_').split("_+");
        StringBuilder out = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) continue;
            if (out.length() > 0) out.append(' ');
            out.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return out.isEmpty() ? value : out.toString();
    }
}
