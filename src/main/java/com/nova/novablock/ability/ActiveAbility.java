package com.nova.novablock.ability;

import com.nova.novablock.progression.SkillEffects;
import com.nova.novablock.progression.SkillType;
import org.bukkit.Material;

/**
 * mcMMO-style active abilities. Each maps to a skill and a held-tool category.
 * The player right-clicks with the tool to "ready" it, then the next matching
 * action (break / attack) triggers a timed buff that goes on cooldown.
 */
public enum ActiveAbility {

    SUPER_BREAKER(SkillType.MINING, "super_breaker", "Super Breaker", ToolType.PICKAXE),
    GIGA_DRILL(SkillType.EXCAVATION, "giga_drill", "Giga Drill Breaker", ToolType.SHOVEL),
    TREE_FELLER(SkillType.WOODCUTTING, "tree_feller", "Tree Feller", ToolType.AXE),
    GREEN_TERRA(SkillType.FARMING, "green_terra", "Green Terra", ToolType.HOE),
    BERSERK(SkillType.COMBAT, "berserk", "Berserk", ToolType.SWORD);

    public final SkillType skill;
    public final String id;
    public final String displayName;
    public final ToolType tool;

    ActiveAbility(SkillType skill, String id, String displayName, ToolType tool) {
        this.skill = skill;
        this.id = id;
        this.displayName = displayName;
        this.tool = tool;
    }

    public SkillEffects.AbilityCfg cfg() {
        return SkillEffects.ability(id);
    }

    /** The ability belonging to a skill, or null if that skill has none. */
    public static ActiveAbility forSkill(SkillType skill) {
        for (ActiveAbility a : values()) {
            if (a.skill == skill) return a;
        }
        return null;
    }

    /** The ability whose tool matches the held material, or null. */
    public static ActiveAbility forTool(Material held) {
        if (held == null) return null;
        for (ActiveAbility a : values()) {
            if (a.tool.matches(held)) return a;
        }
        return null;
    }

    /** Tool families, matched by Material name suffix so all tiers (wood..netherite) qualify. */
    public enum ToolType {
        PICKAXE("_PICKAXE"),
        SHOVEL("_SHOVEL"),
        AXE("_AXE"),
        HOE("_HOE"),
        SWORD("_SWORD");

        private final String suffix;

        ToolType(String suffix) { this.suffix = suffix; }

        public boolean matches(Material m) {
            // "_AXE" intentionally does not match "_PICKAXE" (those end in "PICKAXE").
            return m != null && m.name().endsWith(suffix);
        }
    }
}
