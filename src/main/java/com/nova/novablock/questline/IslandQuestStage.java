package com.nova.novablock.questline;

/** A resolved questline stage: its objective, target, reward, and milestone flag. */
public record IslandQuestStage(int stage, IslandObjective objective, int required,
                               long rewardCoins, boolean milestone) {

    public String describe() { return objective.describe(required); }
}
