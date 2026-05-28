package com.nova.novablock.phase;

import org.bukkit.Material;
import org.bukkit.entity.EntityType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class Phase {

    private final int index;
    private final String id;
    private final String displayName;
    private final String themeColor; // MiniMessage color e.g. "#7CFC00"
    private final int requiredBlocks;
    private final List<PhaseBlock> blocks;
    private final List<EntityType> mobs;
    private final String bossId;  // optional boss fight when phase completes
    private final List<String> lootRoomIds;
    private final int totalWeight;

    public Phase(int index, String id, String displayName, String themeColor, int requiredBlocks,
                 List<PhaseBlock> blocks, List<EntityType> mobs, String bossId, List<String> lootRoomIds) {
        this.index = index;
        this.id = id;
        this.displayName = displayName;
        this.themeColor = themeColor;
        this.requiredBlocks = requiredBlocks;
        this.blocks = Collections.unmodifiableList(blocks);
        this.mobs = Collections.unmodifiableList(mobs);
        this.bossId = bossId;
        this.lootRoomIds = Collections.unmodifiableList(lootRoomIds);
        int sum = 0;
        for (PhaseBlock b : blocks) sum += b.weight();
        this.totalWeight = sum;
    }

    public int getIndex() { return index; }
    public String getId() { return id; }
    public String getDisplayName() { return displayName; }
    public String getThemeColor() { return themeColor; }
    public int getRequiredBlocks() { return requiredBlocks; }
    public List<PhaseBlock> getBlocks() { return blocks; }
    public List<EntityType> getMobs() { return mobs; }
    public String getBossId() { return bossId; }
    public List<String> getLootRoomIds() { return lootRoomIds; }

    public Material rollBlock(Random rng) {
        if (totalWeight <= 0) return Material.STONE;
        int roll = rng.nextInt(totalWeight);
        int acc = 0;
        for (PhaseBlock b : blocks) {
            acc += b.weight();
            if (roll < acc) return b.material();
        }
        return blocks.get(blocks.size() - 1).material();
    }

    public EntityType rollMob(Random rng) {
        if (mobs.isEmpty()) return null;
        EntityType pick = mobs.get(rng.nextInt(mobs.size()));
        // Shulkers are uniquely disruptive on a 1-block island — levitation can
        // push the player off into the void and they're hard to escape. Reroll
        // most picks so they remain a rare hazard instead of a regular spawn.
        if (pick == EntityType.SHULKER && rng.nextInt(4) != 0) {
            pick = mobs.get(rng.nextInt(mobs.size()));
        }
        return pick;
    }

    public static List<EntityType> mobList(EntityType... types) {
        List<EntityType> list = new ArrayList<>();
        Collections.addAll(list, types);
        return list;
    }
}
