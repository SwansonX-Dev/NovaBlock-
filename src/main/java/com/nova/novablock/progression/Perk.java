package com.nova.novablock.progression;

/**
 * Skill-tree perks. Each perk corresponds to a level milestone in a SkillType.
 * Effects are queried directly from this enum by other systems so the perks
 * stay in one file.
 */
public enum Perk {
    // Mining
    LUCKY_BREAK(SkillType.MINING, 5, "Lucky Break", "5% chance to double drops"),
    DEEP_VEIN(SkillType.MINING, 10, "Deep Vein", "+1 ore on rare blocks"),
    QUARRY(SkillType.MINING, 20, "Quarry", "+10% coin reward per block"),
    PROPHET(SkillType.MINING, 30, "Prophet", "Can lock 2 prophecies at once"),

    // Combat
    BERSERKER(SkillType.COMBAT, 5, "Berserker", "+15% damage to bosses"),
    STAGGER(SkillType.COMBAT, 10, "Stagger", "Hits slow bosses briefly"),
    SECOND_WIND(SkillType.COMBAT, 20, "Second Wind", "Heal 4HP on boss kill"),
    EXECUTIONER(SkillType.COMBAT, 30, "Executioner", "+50% dmg below 25% boss HP"),

    // Magic
    AETHER_SIGHT(SkillType.MAGIC, 5, "Aether Sight", "See 12 upcoming blocks"),
    RIFTWALKER(SkillType.MAGIC, 10, "Riftwalker", "Loot rooms appear 20% more"),
    ARCANE_LURE(SkillType.MAGIC, 20, "Arcane Lure", "Pets gain XP 2x faster"),
    TIMESHIFT(SkillType.MAGIC, 30, "Timeshift", "Reroll your prophecy once / day"),

    // Luck
    FOUR_LEAF(SkillType.LUCK, 5, "Four-Leaf", "+5% rare block weight"),
    JACKPOT(SkillType.LUCK, 10, "Jackpot", "+25% coin from chests"),
    FATE_THIEF(SkillType.LUCK, 20, "Fate Thief", "1% chance any block becomes diamond"),
    GAMBLER(SkillType.LUCK, 30, "Gambler", "Daily wheel spin guaranteed top half");

    public final SkillType skill;
    public final int requiredLevel;
    public final String name;
    public final String description;

    Perk(SkillType s, int lvl, String name, String desc) {
        this.skill = s;
        this.requiredLevel = lvl;
        this.name = name;
        this.description = desc;
    }

    public static boolean hasPerk(PlayerProgression p, Perk perk) {
        return p.getLevel(perk.skill) >= perk.requiredLevel;
    }
}
