package com.nova.novablock.util;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.HashMap;
import java.util.Map;

/**
 * Tiny lookup for user-facing strings defined in messages.yml. Keeps the value
 * cached so callers can use {@code Messages.of("...", default)} without touching
 * the file every call. Reloaded by {@link com.nova.novablock.config.ConfigManager}.
 */
public final class Messages {

    private static final Map<String, String> CACHE = new HashMap<>();
    private static String prefix = "<gradient:#7B61FF:#4FC3F7><bold>NovaBlock</bold></gradient> <dark_gray>»</dark_gray> <gray>";

    private Messages() {}

    public static void reload(FileConfiguration cfg) {
        CACHE.clear();
        if (cfg == null) return;
        for (String key : cfg.getKeys(true)) {
            Object v = cfg.get(key);
            if (v instanceof String s) CACHE.put(key, s);
        }
        String p = cfg.getString("prefix");
        if (p != null && !p.isBlank()) prefix = p;
    }

    public static String of(String key, String fallback) {
        return CACHE.getOrDefault(key, fallback);
    }

    /**
     * Returns the message at {@code key} with {@code {placeholders}} substituted.
     * Pairs in {@code substitutions} are interleaved as key, value, key, value…
     */
    public static String format(String key, String fallback, String... substitutions) {
        String raw = of(key, fallback);
        for (int i = 0; i + 1 < substitutions.length; i += 2) {
            raw = raw.replace("{" + substitutions[i] + "}", substitutions[i + 1]);
        }
        return raw;
    }

    public static String prefix() { return prefix; }
}
