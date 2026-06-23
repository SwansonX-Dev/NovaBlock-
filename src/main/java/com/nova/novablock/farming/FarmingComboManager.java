package com.nova.novablock.farming;

import com.nova.novablock.NovaBlock;
import com.nova.novablock.progression.SkillType;
import com.nova.novablock.util.Msg;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks a per-player MANUAL farming combo: chaining hand-harvests of fully-grown
 * crops within a short time window ramps a temporary sell-price boost (registered
 * with xEconomy's SellBoosts) plus bonus Farming XP. Minions never fire a
 * {@code BlockBreakEvent}, so the combo is inherently a reward for hands-on
 * farming — there's no way to build it with automation.
 *
 * <p>State is ephemeral (in-memory only) and decays on its own clock: a player
 * who stops harvesting for the configured window loses the combo and its boost.
 */
public class FarmingComboManager {

    private final NovaBlock plugin;
    private final Map<UUID, Combo> combos = new ConcurrentHashMap<>();

    private long windowMs = 6_000L;
    private double boostPerCombo = 0.02;
    private double boostCap = 0.50;
    private int xpPerCombo = 2;

    public FarmingComboManager(NovaBlock plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        FileConfiguration cfg = plugin.configs().main();
        this.windowMs = Math.max(1000L, cfg.getLong("farming.combo.window-seconds", 6) * 1000L);
        this.boostPerCombo = cfg.getDouble("farming.combo.boost-per-combo", 0.02);
        this.boostCap = cfg.getDouble("farming.combo.boost-cap", 0.50);
        this.xpPerCombo = cfg.getInt("farming.combo.xp-per-combo", 2);
    }

    private static final class Combo {
        int count;
        long lastMs;
    }

    /** Record a manual crop harvest: extend or restart the combo, award bonus XP, show the bar. */
    public void recordHarvest(Player p) {
        long now = System.currentTimeMillis();
        Combo c = combos.computeIfAbsent(p.getUniqueId(), k -> new Combo());
        if (now - c.lastMs > windowMs) c.count = 0; // lapsed → restart the streak
        c.count++;
        c.lastMs = now;

        // Bonus XP scales with the combo (the first harvest of a streak gets none).
        int bonusXp = xpPerCombo * (c.count - 1);
        if (bonusXp > 0) plugin.progression().addXp(p, SkillType.FARMING, bonusXp);

        if (c.count >= 2) {
            int pct = (int) Math.round(currentBoost(c.count) * 100.0);
            Msg.actionBar(p, "<#A3E635>Farming Combo x" + c.count + "! <gray>+" + pct + "% crop sell");
        }
    }

    private double currentBoost(int count) {
        return Math.min(boostCap, boostPerCombo * count);
    }

    /**
     * SellBoosts provider — the multiplier applied to this player's sells while
     * their farming combo is hot. Cheap and side-effect free (SellBoosts
     * contract): an expired/absent combo yields 1.0. Note SellBoosts has no
     * per-material hook, so an active combo briefly boosts ANY sale — intentional
     * ("farm, then cash in while it's hot"); the short window keeps it tied to
     * active play.
     */
    public double sellMultiplierFor(UUID playerId) {
        Combo c = combos.get(playerId);
        if (c == null) return 1.0;
        if (System.currentTimeMillis() - c.lastMs > windowMs) return 1.0;
        return 1.0 + currentBoost(c.count);
    }

    /** Live combo count (0 when expired/absent). Exposed for placeholders/HUD. */
    public int comboOf(UUID playerId) {
        Combo c = combos.get(playerId);
        if (c == null || System.currentTimeMillis() - c.lastMs > windowMs) return 0;
        return c.count;
    }

    /** Drop stale entries so the map can't grow unbounded over a long uptime. */
    public void sweep() {
        long now = System.currentTimeMillis();
        combos.entrySet().removeIf(e -> now - e.getValue().lastMs > windowMs);
    }
}
