package com.nova.novablock.ability;

import com.nova.novablock.NovaBlock;
import com.nova.novablock.progression.SkillEffects;
import com.nova.novablock.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Tracks ready / active / cooldown state for {@link ActiveAbility}s. State is
 * checked lazily against the server tick, so no repeating task is required.
 */
public class AbilityManager {

    /** After readying, the player has this long to swing before it lapses. */
    private static final long READY_WINDOW_TICKS = 80L; // 4s

    private final NovaBlock plugin;

    private final Map<UUID, ActiveAbility> readied = new HashMap<>();
    private final Map<UUID, Long> readyExpiry = new HashMap<>();
    private final Map<UUID, ActiveAbility> active = new HashMap<>();
    private final Map<UUID, Long> activeExpiry = new HashMap<>();
    private final Map<String, Long> cooldownUntil = new HashMap<>();

    public AbilityManager(NovaBlock plugin) { this.plugin = plugin; }

    private static long now() { return Bukkit.getServer().getCurrentTick(); }

    private String cdKey(UUID id, ActiveAbility a) { return id + ":" + a.id; }

    /** Right-clicked with a skill tool — arm the ability if available. */
    public void ready(Player p, ActiveAbility ability) {
        if (ability == null) return;
        UUID id = p.getUniqueId();
        if (active.get(id) == ability && now() < activeExpiry.getOrDefault(id, 0L)) {
            return; // already mid-ability
        }
        if (readied.get(id) == ability && now() < readyExpiry.getOrDefault(id, 0L)) {
            return; // already armed — don't re-announce on every right-click
        }
        long cdEnd = cooldownUntil.getOrDefault(cdKey(id, ability), 0L);
        if (now() < cdEnd) {
            long secs = Math.max(1, (cdEnd - now()) / 20);
            Msg.actionBar(p, "<gray>" + ability.displayName + " on cooldown: <white>" + secs + "s");
            return;
        }
        readied.put(id, ability);
        readyExpiry.put(id, now() + READY_WINDOW_TICKS);
        Msg.actionBar(p, "<yellow>" + ability.displayName + " <gray>ready — strike to activate!");
        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.6f, 1.6f);
    }

    /**
     * If the player has {@code ability} readied (and not on cooldown), activate it.
     * @return true if it was activated this call.
     */
    public boolean tryActivate(Player p, ActiveAbility ability) {
        UUID id = p.getUniqueId();
        if (readied.get(id) != ability) return false;
        if (now() > readyExpiry.getOrDefault(id, 0L)) { clearReady(id); return false; }
        if (now() < cooldownUntil.getOrDefault(cdKey(id, ability), 0L)) { clearReady(id); return false; }
        clearReady(id);

        SkillEffects.AbilityCfg cfg = ability.cfg();
        int level = plugin.progression().get(p).getLevel(ability.skill);
        int durationTicks = cfg.durationTicks(level);

        active.put(id, ability);
        activeExpiry.put(id, now() + durationTicks);
        cooldownUntil.put(cdKey(id, ability), now() + durationTicks + cfg.cooldownTicks());

        applyBuff(p, ability, durationTicks);
        Msg.title(p, "<gold>" + ability.displayName + "!", "<gray>" + (durationTicks / 20) + "s");
        p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.5f, 1.4f);
        return true;
    }

    private void applyBuff(Player p, ActiveAbility ability, int durationTicks) {
        switch (ability) {
            case SUPER_BREAKER, GIGA_DRILL ->
                    p.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, durationTicks, 2, true, false, true));
            case BERSERK ->
                    p.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, durationTicks, 0, true, false, true));
            default -> { /* Tree Feller / Green Terra are action effects, no potion */ }
        }
    }

    public boolean isActive(Player p, ActiveAbility ability) {
        UUID id = p.getUniqueId();
        if (active.get(id) != ability) return false;
        if (now() > activeExpiry.getOrDefault(id, 0L)) { active.remove(id); activeExpiry.remove(id); return false; }
        return true;
    }

    /** Drop multiplier to apply while this ability is active (1 if not active). */
    public int dropMultiplier(Player p, ActiveAbility ability) {
        return isActive(p, ability) ? ability.cfg().dropMultiplier() : 1;
    }

    /** Remaining cooldown seconds for display (0 if ready). */
    public long cooldownSeconds(Player p, ActiveAbility ability) {
        long end = cooldownUntil.getOrDefault(cdKey(p.getUniqueId(), ability), 0L);
        return Math.max(0L, (end - now()) / 20);
    }

    private void clearReady(UUID id) {
        readied.remove(id);
        readyExpiry.remove(id);
    }

    public void clear(UUID id) {
        clearReady(id);
        active.remove(id);
        activeExpiry.remove(id);
        cooldownUntil.keySet().removeIf(k -> k.startsWith(id + ":"));
    }
}
