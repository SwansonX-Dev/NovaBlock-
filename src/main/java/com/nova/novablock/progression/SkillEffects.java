package com.nova.novablock.progression;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/**
 * Central, config-backed tuning for the skill system: per-skill XP rewards,
 * level-scaling passive chances, and active-ability parameters. All values have
 * code-baked defaults so the system works even against a stale or missing
 * skills.yml. Reloaded via {@code ConfigManager.loadAll()} ( /obadmin reload ).
 */
public final class SkillEffects {

    private SkillEffects() {}

    /** Active-ability tuning. Duration scales with level up to a cap; cooldown is flat. */
    public record AbilityCfg(int durationBaseTicks, int durationPerLevelTicks,
                             int durationMaxTicks, int cooldownTicks, int dropMultiplier) {
        public int durationTicks(int level) {
            return Math.min(durationMaxTicks, durationBaseTicks + durationPerLevelTicks * Math.max(0, level));
        }
    }

    // chance = min(cap, perLevel * level); index 0 = perLevel, index 1 = cap.
    private static final Map<SkillType, double[]> PASSIVE = new EnumMap<>(SkillType.class);
    private static final Map<SkillType, Long> XP = new EnumMap<>(SkillType.class);
    private static final Map<String, AbilityCfg> ABILITIES = new HashMap<>();

    private static final AbilityCfg DEFAULT_ABILITY = new AbilityCfg(40, 4, 240, 4800, 2);

    static {
        applyDefaults();
    }

    private static void applyDefaults() {
        PASSIVE.clear();
        XP.clear();
        ABILITIES.clear();

        // Level-scaling passive "primary" chance per skill (per-level, cap).
        PASSIVE.put(SkillType.MINING, new double[]{0.0010, 0.10});      // double ore/stone drop
        PASSIVE.put(SkillType.EXCAVATION, new double[]{0.0015, 0.15});  // double dirt/sand drop
        PASSIVE.put(SkillType.WOODCUTTING, new double[]{0.0010, 0.10}); // double log drop
        PASSIVE.put(SkillType.FARMING, new double[]{0.0015, 0.15});     // double harvest
        PASSIVE.put(SkillType.FISHING, new double[]{0.0010, 0.10});     // treasure / extra catch
        PASSIVE.put(SkillType.COMBAT, new double[]{0.0020, 0.20});      // bonus boss-damage fraction
        PASSIVE.put(SkillType.MAGIC, new double[]{0.0, 0.0});
        PASSIVE.put(SkillType.LUCK, new double[]{0.0, 0.0});

        // Base XP granted per qualifying action for the gathering skills.
        XP.put(SkillType.FARMING, 8L);
        XP.put(SkillType.FISHING, 12L);
        XP.put(SkillType.WOODCUTTING, 6L);
        XP.put(SkillType.EXCAVATION, 4L);
        XP.put(SkillType.MINING, 3L);   // non-OneBlock ore/stone breaks (center handled in BlockListener)

        // Active abilities: ready your tool (right-click), next action triggers a timed buff.
        ABILITIES.put("super_breaker", new AbilityCfg(40, 4, 240, 4800, 3));   // Mining   (pickaxe)
        ABILITIES.put("giga_drill",   new AbilityCfg(40, 4, 240, 4800, 3));    // Excavation (shovel)
        ABILITIES.put("tree_feller",  new AbilityCfg(20, 0, 20, 4800, 1));     // Woodcutting (axe)
        ABILITIES.put("green_terra",  new AbilityCfg(40, 4, 240, 4800, 2));    // Farming  (hoe)
        ABILITIES.put("berserk",      new AbilityCfg(60, 6, 300, 4800, 1));    // Combat   (sword)
    }

    public static void load(FileConfiguration c) {
        applyDefaults();
        if (c == null) return;

        for (SkillType s : SkillType.values()) {
            String base = "skills." + s.name().toLowerCase();
            double[] def = PASSIVE.getOrDefault(s, new double[]{0.0, 0.0});
            double perLevel = c.getDouble(base + ".passive.per-level", def[0]);
            double cap = c.getDouble(base + ".passive.cap", def[1]);
            PASSIVE.put(s, new double[]{Math.max(0, perLevel), Math.max(0, cap)});
            if (XP.containsKey(s)) {
                XP.put(s, Math.max(0L, c.getLong(base + ".xp-per-action", XP.get(s))));
            }
        }

        for (Map.Entry<String, AbilityCfg> e : new HashMap<>(ABILITIES).entrySet()) {
            String base = "abilities." + e.getKey();
            AbilityCfg d = e.getValue();
            ABILITIES.put(e.getKey(), new AbilityCfg(
                    c.getInt(base + ".duration-base-ticks", d.durationBaseTicks()),
                    c.getInt(base + ".duration-per-level-ticks", d.durationPerLevelTicks()),
                    c.getInt(base + ".duration-max-ticks", d.durationMaxTicks()),
                    c.getInt(base + ".cooldown-ticks", d.cooldownTicks()),
                    Math.max(1, c.getInt(base + ".drop-multiplier", d.dropMultiplier()))));
        }
    }

    /** Level-scaling primary passive chance for a skill (0..cap). */
    public static double passiveChance(SkillType skill, int level) {
        double[] pc = PASSIVE.get(skill);
        if (pc == null) return 0.0;
        return Math.min(pc[1], pc[0] * Math.max(0, level));
    }

    /** Base XP for one qualifying action of a gathering skill. */
    public static long xpPerAction(SkillType skill) {
        return XP.getOrDefault(skill, 0L);
    }

    public static AbilityCfg ability(String id) {
        return ABILITIES.getOrDefault(id, DEFAULT_ABILITY);
    }
}
