package com.nova.novablock.community;

import org.bukkit.Material;

import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * The flavours of personal "OneBlock" resource node a player can place on their
 * Community OneBlock claim. Each type rerolls into a material from its own
 * weighted pool when mined. {@link DropMode#VANILLA} hands over the block's
 * natural drops (ores → raw ore/gems); {@link DropMode#BLOCK} hands over the
 * rolled block itself (building/decoration blocks). Every BLOCK-mode pool
 * material must be obtainable as an item.
 */
public enum CommunityNodeType {

    MINING("Mining", "<gradient:#B0BEC5:#ECEFF1>", Material.IRON_ORE, DropMode.VANILLA, List.of(
            w(Material.STONE, 60), w(Material.COAL_ORE, 22), w(Material.COPPER_ORE, 16),
            w(Material.IRON_ORE, 16), w(Material.GOLD_ORE, 8), w(Material.REDSTONE_ORE, 8),
            w(Material.LAPIS_ORE, 6), w(Material.DIAMOND_ORE, 3), w(Material.EMERALD_ORE, 1))),

    STONE("Stone", "<gradient:#9E9E9E:#CFD8DC>", Material.STONE, DropMode.BLOCK, List.of(
            w(Material.STONE, 40), w(Material.COBBLESTONE, 20), w(Material.GRANITE, 12),
            w(Material.DIORITE, 12), w(Material.ANDESITE, 12), w(Material.DEEPSLATE, 14),
            w(Material.COBBLED_DEEPSLATE, 10), w(Material.TUFF, 8), w(Material.CALCITE, 5),
            w(Material.DRIPSTONE_BLOCK, 4), w(Material.SMOOTH_BASALT, 5), w(Material.BLACKSTONE, 4))),

    BUILDING("Building", "<gradient:#A1887F:#D7CCC8>", Material.BRICKS, DropMode.BLOCK, List.of(
            w(Material.OAK_PLANKS, 16), w(Material.SPRUCE_PLANKS, 12), w(Material.BIRCH_PLANKS, 12),
            w(Material.OAK_LOG, 14), w(Material.STONE_BRICKS, 14), w(Material.BRICKS, 10),
            w(Material.SANDSTONE, 10), w(Material.SMOOTH_STONE, 10), w(Material.QUARTZ_BLOCK, 6),
            w(Material.TERRACOTTA, 8), w(Material.DEEPSLATE_BRICKS, 8), w(Material.MUD_BRICKS, 6),
            w(Material.POLISHED_ANDESITE, 6))),

    NATURE("Nature", "<gradient:#7CB342:#C5E1A5>", Material.GRASS_BLOCK, DropMode.BLOCK, List.of(
            w(Material.GRASS_BLOCK, 26), w(Material.DIRT, 22), w(Material.COARSE_DIRT, 12),
            w(Material.PODZOL, 10), w(Material.MOSS_BLOCK, 12), w(Material.ROOTED_DIRT, 8),
            w(Material.MUD, 8), w(Material.CLAY, 8), w(Material.MYCELIUM, 5),
            w(Material.MUDDY_MANGROVE_ROOTS, 4))),

    NETHER("Nether", "<gradient:#C62828:#FF8A65>", Material.NETHERRACK, DropMode.BLOCK, List.of(
            w(Material.NETHERRACK, 40), w(Material.NETHER_BRICKS, 14), w(Material.BLACKSTONE, 12),
            w(Material.BASALT, 10), w(Material.MAGMA_BLOCK, 8), w(Material.GLOWSTONE, 8),
            w(Material.NETHER_WART_BLOCK, 6), w(Material.WARPED_NYLIUM, 6), w(Material.CRIMSON_NYLIUM, 6),
            w(Material.SOUL_SAND, 8), w(Material.QUARTZ_BLOCK, 6), w(Material.NETHER_QUARTZ_ORE, 4))),

    SAND("Sand", "<gradient:#FDD835:#FFF59D>", Material.SAND, DropMode.BLOCK, List.of(
            w(Material.SAND, 30), w(Material.RED_SAND, 18), w(Material.SANDSTONE, 14),
            w(Material.CUT_SANDSTONE, 8), w(Material.RED_SANDSTONE, 10), w(Material.SMOOTH_SANDSTONE, 6),
            w(Material.GRAVEL, 16), w(Material.GLASS, 8), w(Material.CLAY, 8), w(Material.TERRACOTTA, 6))),

    OCEAN("Ocean", "<gradient:#0097A7:#80DEEA>", Material.PRISMARINE, DropMode.BLOCK, List.of(
            w(Material.PRISMARINE, 20), w(Material.PRISMARINE_BRICKS, 14), w(Material.DARK_PRISMARINE, 12),
            w(Material.SEA_LANTERN, 6), w(Material.TUBE_CORAL_BLOCK, 8), w(Material.BRAIN_CORAL_BLOCK, 8),
            w(Material.BUBBLE_CORAL_BLOCK, 8), w(Material.FIRE_CORAL_BLOCK, 8), w(Material.HORN_CORAL_BLOCK, 8),
            w(Material.SAND, 16), w(Material.CLAY, 10))),

    COLORFUL("Colorful", "<gradient:#E040FB:#FFD740>", Material.PINK_CONCRETE, DropMode.BLOCK, List.of(
            w(Material.WHITE_CONCRETE, 8), w(Material.ORANGE_CONCRETE, 8), w(Material.MAGENTA_CONCRETE, 8),
            w(Material.LIGHT_BLUE_CONCRETE, 8), w(Material.YELLOW_CONCRETE, 8), w(Material.LIME_CONCRETE, 8),
            w(Material.PINK_CONCRETE, 8), w(Material.CYAN_CONCRETE, 8), w(Material.PURPLE_CONCRETE, 8),
            w(Material.BLUE_CONCRETE, 8), w(Material.GREEN_CONCRETE, 8), w(Material.RED_CONCRETE, 8),
            w(Material.BLACK_CONCRETE, 8), w(Material.GLASS, 6)));

    public enum DropMode { VANILLA, BLOCK }

    public record Weighted(Material material, int weight) {}
    private static Weighted w(Material material, int weight) { return new Weighted(material, weight); }

    private final String displayName;
    private final String colorGradient;
    private final Material icon;
    private final DropMode dropMode;
    private final List<Weighted> pool;
    private final int totalWeight;

    CommunityNodeType(String displayName, String colorGradient, Material icon, DropMode dropMode, List<Weighted> pool) {
        this.displayName = displayName;
        this.colorGradient = colorGradient;
        this.icon = icon;
        this.dropMode = dropMode;
        this.pool = pool;
        int sum = 0;
        for (Weighted entry : pool) sum += entry.weight();
        this.totalWeight = sum;
    }

    public String id() { return name().toLowerCase(Locale.ROOT); }
    public String displayName() { return displayName; }
    public String colorGradient() { return colorGradient; }
    public Material icon() { return icon; }
    public DropMode dropMode() { return dropMode; }

    /** Pick a weighted-random material from this type's pool. */
    public Material roll(Random rng) {
        int roll = rng.nextInt(Math.max(1, totalWeight));
        int acc = 0;
        for (Weighted entry : pool) {
            acc += entry.weight();
            if (roll < acc) return entry.material();
        }
        return pool.get(0).material();
    }

    public static CommunityNodeType byId(String id) {
        if (id == null) return null;
        for (CommunityNodeType type : values()) {
            if (type.name().equalsIgnoreCase(id) || type.id().equalsIgnoreCase(id)) return type;
        }
        return null;
    }
}
