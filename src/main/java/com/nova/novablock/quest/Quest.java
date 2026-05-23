package com.nova.novablock.quest;

import org.bukkit.Material;

public record Quest(String id, String displayName, String description,
                    QuestType type, Material targetMaterial, int requiredAmount,
                    long coinReward, long xpReward) {

    public enum QuestType { BREAK_BLOCK, BREAK_ANY, ADVANCE_PHASE, COMPLETE_LOOT_ROOM, KILL_BOSS }
}
