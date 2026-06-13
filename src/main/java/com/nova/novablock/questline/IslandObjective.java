package com.nova.novablock.questline;

import org.bukkit.Material;

/**
 * The kinds of objective an endless island-questline stage can ask for. Every
 * value here must be infinitely repeatable through ordinary play — objectives
 * that can run out (e.g. advancing a finite phase track) are intentionally
 * excluded so the chain can never soft-lock.
 */
public enum IslandObjective {

    MINE_ONEBLOCKS("Mine", "OneBlock", "OneBlocks", Material.STONE),
    KILL_MOBS("Fight", "mob", "mobs", Material.ZOMBIE_HEAD),
    GENERATE_COBBLE("Run a cobble gen for", "cobblestone", "cobblestone", Material.COBBLESTONE),
    SMELT_ITEMS("Smelt", "item", "items", Material.FURNACE),
    HARVEST_CROPS("Harvest", "crop", "crops", Material.WHEAT),
    CATCH_FISH("Catch", "fish", "fish", Material.COD),
    CLEAR_LOOT_ROOMS("Clear", "loot room", "loot rooms", Material.END_PORTAL_FRAME),
    FULFILL_PROPHECIES("Fulfil", "prophecy", "prophecies", Material.AMETHYST_SHARD);

    public final String verb;
    public final String singular;
    public final String plural;
    public final Material icon;

    IslandObjective(String verb, String singular, String plural, Material icon) {
        this.verb = verb;
        this.singular = singular;
        this.plural = plural;
        this.icon = icon;
    }

    /** e.g. "Mine 320 OneBlocks" / "Fight 12 mobs" / "Run a cobble gen for 80 cobblestone". */
    public String describe(int required) {
        return verb + " " + required + " " + (required == 1 ? singular : plural);
    }
}
