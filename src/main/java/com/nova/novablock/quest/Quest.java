package com.nova.novablock.quest;

import org.bukkit.Material;

/**
 * A single daily quest definition.
 *
 * <p>{@code targetMaterial} is only meaningful for {@link QuestType#BREAK_BLOCK}
 * (the block that counts); for every other type it is unused and may be
 * {@code null}. {@code icon} is the item shown in the quest GUI — it lets
 * non-block quests (bosses, prophecies, pet missions…) render a sensible item.
 */
public record Quest(String id, String displayName, String description,
                    QuestType type, QuestCategory category,
                    Material targetMaterial, Material icon,
                    int requiredAmount, long coinReward, long xpReward) {

    public enum QuestType {
        BREAK_BLOCK,
        BREAK_ANY,
        ADVANCE_PHASE,
        COMPLETE_LOOT_ROOM,
        KILL_BOSS,
        FULFILL_PROPHECY,
        REACH_COMBO,
        COMMUNITY_BREAK,
        OG_BREAK,
        PET_GATHER
    }

    /** Grouping used for GUI tags and to guarantee daily variety across game modes. */
    public enum QuestCategory {
        PERSONAL("<green>Personal OneBlock"),
        COMMUNITY("<aqua>Community OneBlock"),
        OG("<red>OG OneBlock"),
        PET("<light_purple>Pets");

        public final String label;
        QuestCategory(String label) { this.label = label; }
    }
}
