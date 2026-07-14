package com.nova.novablock.progression;

import com.nova.novablock.NovaBlock;
import com.nova.novablock.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ProgressionManager {

    private final NovaBlock plugin;
    private final Map<UUID, PlayerProgression> cache = new HashMap<>();
    private final Map<UUID, Map<SkillType, Double>> fractionalBuffer = new HashMap<>();

    public ProgressionManager(NovaBlock plugin) { this.plugin = plugin; }

    public PlayerProgression get(UUID id) {
        return cache.computeIfAbsent(id, plugin.storage()::loadProgression);
    }

    public PlayerProgression get(Player p) { return get(p.getUniqueId()); }

    public void save(UUID id) {
        PlayerProgression p = cache.get(id);
        if (p != null) plugin.storage().saveProgression(p);
    }

    public void saveAll() {
        for (PlayerProgression p : cache.values()) plugin.storage().saveProgression(p);
    }

    /**
     * Persist only online players' progression — used by the periodic autosave.
     * Offline players in the cache haven't mutated since their quit-save (all
     * progression changes happen for an online player), so re-saving them every
     * cycle is pure waste; this keeps the autosave's work proportional to the
     * live player count instead of every unique login since restart.
     */
    public void saveOnline() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            PlayerProgression pr = cache.get(p.getUniqueId());
            if (pr != null) plugin.storage().saveProgression(pr);
        }
    }

    public void unload(UUID id) {
        save(id);
        cache.remove(id);
        fractionalBuffer.remove(id);
    }

    public void delete(UUID id) {
        cache.remove(id);
        plugin.storage().deleteProgression(id);
    }

    /** Accumulates fractional XP per-player so multipliers like 1.05 still tick over time. */
    public void addXp(Player player, SkillType skill, double amount) {
        if (amount <= 0) return;
        amount = applyXpMultipliers(player, amount);
        Map<SkillType, Double> bySkill = fractionalBuffer.computeIfAbsent(player.getUniqueId(), k -> new HashMap<>());
        double total = bySkill.getOrDefault(skill, 0.0) + amount;
        long whole = (long) total;
        bySkill.put(skill, total - whole);
        if (whole > 0) doAddXp(player, skill, whole);
    }

    public void addXp(Player player, SkillType skill, long amount) {
        if (amount <= 0) return;
        // ARCANE_LURE / future multipliers route through the fractional path so the
        // bonus applies uniformly. Plain whole-amount calls skip the buffer.
        if (hasXpMultiplier(player)) {
            addXp(player, skill, (double) amount);
            return;
        }
        doAddXp(player, skill, amount);
    }

    private boolean hasXpMultiplier(Player player) {
        if (Perk.hasPerk(get(player), Perk.ARCANE_LURE)) return true;
        var island = plugin.islands().ofPlayer(player);
        return island != null && island.data().isFlag(com.nova.novablock.island.IslandFlag.NIGHTMARE_MODE);
    }

    /** Single source of truth for XP multipliers; both overloads call into this. */
    private double applyXpMultipliers(Player player, double amount) {
        if (Perk.hasPerk(get(player), Perk.ARCANE_LURE)) amount *= 1.10;
        var island = plugin.islands().ofPlayer(player);
        if (island != null && island.data().isFlag(com.nova.novablock.island.IslandFlag.NIGHTMARE_MODE)) {
            amount *= 0.5;
        }
        return amount;
    }

    private void doAddXp(Player player, SkillType skill, long amount) {
        PlayerProgression p = get(player);
        // At the level cap, stop accumulating XP so it doesn't grow unbounded and the
        // progress bar reads as full.
        if (p.isMaxLevel(skill)) {
            p.setXp(skill, 0L);
            return;
        }
        p.setXp(skill, p.getXp(skill) + amount);
        while (!p.isMaxLevel(skill) && p.getXp(skill) >= PlayerProgression.xpForLevel(p.getLevel(skill))) {
            p.setXp(skill, p.getXp(skill) - PlayerProgression.xpForLevel(p.getLevel(skill)));
            p.setLevel(skill, p.getLevel(skill) + 1);
            int newLevel = p.getLevel(skill);
            Msg.title(player, "<" + skill.color() + ">+ " + skill.displayName() + " Lv " + newLevel,
                    "<gray>Check /ob skills for perks");
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.4f);
            // Notify if perk unlocked at this level
            for (Perk perk : Perk.values()) {
                if (perk.skill == skill && perk.requiredLevel == newLevel) {
                    Msg.send(player, "<gold>Unlocked: <yellow>" + perk.name + " <gray>– " + perk.description);
                }
            }
            if (p.isMaxLevel(skill)) {
                p.setXp(skill, 0L);
                Msg.send(player, "<gold>✦ <yellow>" + skill.displayName()
                        + " <gold>has reached the level cap (<white>" + PlayerProgression.maxLevel() + "</white>)!");
            }
        }
    }
}
