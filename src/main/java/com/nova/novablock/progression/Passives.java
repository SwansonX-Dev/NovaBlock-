package com.nova.novablock.progression;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Level-scaling passive rolls, mcMMO-style: a skill's primary passive chance
 * climbs every level (per-level rate, capped) instead of unlocking at a fixed
 * milestone. Numbers come from {@link SkillEffects} (config-backed).
 */
public final class Passives {

    private Passives() {}

    /** Rolls the skill's primary passive (e.g. double-drop / treasure) at the player's level. */
    public static boolean roll(PlayerProgression p, SkillType skill) {
        double chance = chance(p, skill);
        return chance > 0 && ThreadLocalRandom.current().nextDouble() < chance;
    }

    /** The current passive chance (0..cap) for display. */
    public static double chance(PlayerProgression p, SkillType skill) {
        return SkillEffects.passiveChance(skill, p.getLevel(skill));
    }

    /** Combat bonus-damage fraction the player has earned from Combat level (0..cap). */
    public static double combatBonus(PlayerProgression p) {
        return SkillEffects.passiveChance(SkillType.COMBAT, p.getLevel(SkillType.COMBAT));
    }
}
