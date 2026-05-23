package com.nova.novablock.progression;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PlayerProgression {

    private final UUID playerId;
    private final Map<SkillType, Long> xp = new EnumMap<>(SkillType.class);
    private final Map<SkillType, Integer> level = new EnumMap<>(SkillType.class);
    private final Map<String, Integer> petLevels = new HashMap<>();

    private String selectedPet;
    private int questProgress;
    private long questDayStamp;
    private boolean menuItemEnabled = true;
    private boolean scoreboardEnabled = true;

    public PlayerProgression(UUID playerId) {
        this.playerId = playerId;
        for (SkillType t : SkillType.values()) {
            xp.put(t, 0L);
            level.put(t, 1);
        }
    }

    public UUID getPlayerId() { return playerId; }

    public long getXp(SkillType t) { return xp.getOrDefault(t, 0L); }
    public void setXp(SkillType t, long v) { xp.put(t, v); }

    public int getLevel(SkillType t) { return level.getOrDefault(t, 1); }
    public void setLevel(SkillType t, int v) { level.put(t, v); }

    public String getSelectedPet() { return selectedPet; }
    public void setSelectedPet(String s) { this.selectedPet = s; }

    public Map<String, Integer> getPetLevels() { return petLevels; }
    public int getPetLevel(String petId) { return petLevels.getOrDefault(petId, 0); }
    public void unlockPet(String petId) { petLevels.putIfAbsent(petId, 1); }
    public void levelUpPet(String petId) { petLevels.merge(petId, 1, Integer::sum); }

    public int getQuestProgress() { return questProgress; }
    public void setQuestProgress(int v) { this.questProgress = v; }
    public void addQuestProgress(int v) { this.questProgress += v; }

    public long getQuestDayStamp() { return questDayStamp; }
    public void setQuestDayStamp(long v) { this.questDayStamp = v; }

    public boolean isMenuItemEnabled() { return menuItemEnabled; }
    public void setMenuItemEnabled(boolean v) { this.menuItemEnabled = v; }

    public boolean isScoreboardEnabled() { return scoreboardEnabled; }
    public void setScoreboardEnabled(boolean v) { this.scoreboardEnabled = v; }

    /**
     * XP needed to reach the next skill level. Linear curve tuned so that:
     *   level 5  ≈ 1,200 blocks  (first perk unlock — quick)
     *   level 10 ≈ 4,200 blocks  (mid-game)
     *   level 20 ≈ 15,000 blocks (late-game)
     *   level 30 ≈ 33,000 blocks (very long tail — top perks, but reachable)
     */
    public static long xpForLevel(int level) {
        return 200L + 100L * level;
    }
}
