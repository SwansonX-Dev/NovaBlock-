package com.nova.novablock.progression;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class PlayerProgression {

    private final UUID playerId;
    private final Map<SkillType, Long> xp = new EnumMap<>(SkillType.class);
    private final Map<SkillType, Integer> level = new EnumMap<>(SkillType.class);

    private int questProgress;
    private long questDayStamp;
    private long lastRerollDay;
    private long lastLoginDay;
    private int loginStreak;
    private boolean menuItemEnabled = true;
    private boolean scoreboardEnabled = true;
    private boolean autoSellEnabled = false;
    private boolean backpackItemEnabled = false;
    private String backpackBase64 = "";
    private int atlasScore;
    private String seasonalPathKey = "";
    private int seasonalPathPoints;
    private long claimRewardBreaks;
    private final Set<Integer> claimedSeasonalTiers = new HashSet<>();
    private final Set<String> pendingRewardCommands = new HashSet<>();

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

    public int getQuestProgress() { return questProgress; }
    public void setQuestProgress(int v) { this.questProgress = v; }
    public void addQuestProgress(int v) { this.questProgress += v; }

    public long getQuestDayStamp() { return questDayStamp; }
    public void setQuestDayStamp(long v) { this.questDayStamp = v; }

    public long getLastRerollDay() { return lastRerollDay; }
    public void setLastRerollDay(long v) { this.lastRerollDay = v; }

    public long getLastLoginDay() { return lastLoginDay; }
    public void setLastLoginDay(long v) { this.lastLoginDay = v; }

    public int getLoginStreak() { return loginStreak; }
    public void setLoginStreak(int v) { this.loginStreak = v; }

    public boolean isMenuItemEnabled() { return menuItemEnabled; }
    public void setMenuItemEnabled(boolean v) { this.menuItemEnabled = v; }

    public boolean isScoreboardEnabled() { return scoreboardEnabled; }
    public void setScoreboardEnabled(boolean v) { this.scoreboardEnabled = v; }

    /** When enabled, common drops mined on the Community OneBlock are auto-sold for coins. */
    public boolean isAutoSellEnabled() { return autoSellEnabled; }
    public void setAutoSellEnabled(boolean v) { this.autoSellEnabled = v; }

    /** When enabled, the backpack hotbar item is shown and picked-up items auto-grab into the backpack. */
    public boolean isBackpackItemEnabled() { return backpackItemEnabled; }
    public void setBackpackItemEnabled(boolean v) { this.backpackItemEnabled = v; }

    /** Base64-serialised contents of this player's personal backpack ("" when empty). */
    public String getBackpackBase64() { return backpackBase64 == null ? "" : backpackBase64; }
    public void setBackpackBase64(String v) { this.backpackBase64 = v == null ? "" : v; }

    public int getAtlasScore() { return atlasScore; }
    public void addAtlasScore(int amount) { this.atlasScore = Math.max(0, this.atlasScore + amount); }
    public void setAtlasScore(int value) { this.atlasScore = Math.max(0, value); }

    public String getSeasonalPathKey() { return seasonalPathKey == null ? "" : seasonalPathKey; }
    public void setSeasonalPathKey(String seasonalPathKey) { this.seasonalPathKey = seasonalPathKey == null ? "" : seasonalPathKey; }

    public int getSeasonalPathPoints() { return seasonalPathPoints; }
    public void addSeasonalPathPoints(int amount) { this.seasonalPathPoints = Math.max(0, this.seasonalPathPoints + amount); }
    public void setSeasonalPathPoints(int value) { this.seasonalPathPoints = Math.max(0, value); }

    public long getClaimRewardBreaks() { return claimRewardBreaks; }
    public void setClaimRewardBreaks(long value) { this.claimRewardBreaks = Math.max(0L, value); }
    public long incrementClaimRewardBreaks() { return ++claimRewardBreaks; }

    public Set<Integer> getClaimedSeasonalTiers() { return claimedSeasonalTiers; }
    public void setClaimedSeasonalTiers(Set<Integer> tiers) {
        claimedSeasonalTiers.clear();
        if (tiers != null) claimedSeasonalTiers.addAll(tiers);
    }
    public boolean hasClaimedSeasonalTier(int tier) { return claimedSeasonalTiers.contains(tier); }
    public void markSeasonalTierClaimed(int tier) { claimedSeasonalTiers.add(tier); }

    public Set<String> getPendingRewardCommands() { return pendingRewardCommands; }
    public void setPendingRewardCommands(Set<String> commands) {
        pendingRewardCommands.clear();
        if (commands != null) pendingRewardCommands.addAll(commands);
    }
    public void addPendingRewardCommand(String command) {
        if (command != null && !command.isBlank()) pendingRewardCommands.add(command);
    }
    public boolean removePendingRewardCommand(String command) { return pendingRewardCommands.remove(command); }

    /** Default linear curve. Overridden at runtime by skills.yml `xp-curve` entries. */
    private static long xpBase = 200L;
    private static long xpPerLevel = 100L;

    public static void setXpCurve(long base, long perLevel) {
        xpBase = Math.max(1, base);
        xpPerLevel = Math.max(1, perLevel);
    }

    /**
     * XP needed to reach the next skill level. Linear curve tuned so that with
     * the defaults (base=200, perLevel=100):
     *   level 5  ≈ 1,200 blocks  (first perk unlock — quick)
     *   level 10 ≈ 4,200 blocks  (mid-game)
     *   level 20 ≈ 15,000 blocks (late-game)
     *   level 30 ≈ 33,000 blocks (very long tail — top perks, but reachable)
     */
    public static long xpForLevel(int level) {
        return xpBase + xpPerLevel * level;
    }
}
