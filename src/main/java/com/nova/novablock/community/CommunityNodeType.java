package com.nova.novablock.community;

import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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
            w(Material.STONE, 50), w(Material.COBBLESTONE, 24), w(Material.DEEPSLATE, 20),
            w(Material.COAL_ORE, 20), w(Material.DEEPSLATE_COAL_ORE, 12),
            w(Material.COPPER_ORE, 16), w(Material.DEEPSLATE_COPPER_ORE, 10),
            w(Material.IRON_ORE, 16), w(Material.DEEPSLATE_IRON_ORE, 10),
            w(Material.GOLD_ORE, 8), w(Material.DEEPSLATE_GOLD_ORE, 5),
            w(Material.REDSTONE_ORE, 8), w(Material.DEEPSLATE_REDSTONE_ORE, 5),
            w(Material.LAPIS_ORE, 6), w(Material.DEEPSLATE_LAPIS_ORE, 4),
            w(Material.NETHER_QUARTZ_ORE, 6), w(Material.NETHER_GOLD_ORE, 4),
            w(Material.DIAMOND_ORE, 3), w(Material.DEEPSLATE_DIAMOND_ORE, 2),
            w(Material.EMERALD_ORE, 1), w(Material.DEEPSLATE_EMERALD_ORE, 1),
            w(Material.ANCIENT_DEBRIS, 1))),

    STONE("Stone", "<gradient:#9E9E9E:#CFD8DC>", Material.STONE, DropMode.BLOCK, List.of(
            w(Material.STONE, 36), w(Material.COBBLESTONE, 20), w(Material.GRANITE, 12),
            w(Material.DIORITE, 12), w(Material.ANDESITE, 12), w(Material.DEEPSLATE, 14),
            w(Material.COBBLED_DEEPSLATE, 10), w(Material.TUFF, 8), w(Material.CALCITE, 6),
            w(Material.DRIPSTONE_BLOCK, 5), w(Material.SMOOTH_BASALT, 6), w(Material.BLACKSTONE, 5),
            w(Material.STONE_BRICKS, 10), w(Material.MOSSY_COBBLESTONE, 6), w(Material.MOSSY_STONE_BRICKS, 5),
            w(Material.SMOOTH_STONE, 8), w(Material.POLISHED_GRANITE, 5), w(Material.POLISHED_DIORITE, 5),
            w(Material.POLISHED_ANDESITE, 5), w(Material.POLISHED_DEEPSLATE, 6), w(Material.POLISHED_TUFF, 5),
            w(Material.GRAVEL, 8), w(Material.END_STONE, 4))),

    BUILDING("Building", "<gradient:#A1887F:#D7CCC8>", Material.BRICKS, DropMode.BLOCK, List.of(
            w(Material.OAK_PLANKS, 14), w(Material.SPRUCE_PLANKS, 12), w(Material.BIRCH_PLANKS, 12),
            w(Material.JUNGLE_PLANKS, 8), w(Material.ACACIA_PLANKS, 8), w(Material.DARK_OAK_PLANKS, 8),
            w(Material.MANGROVE_PLANKS, 6), w(Material.CHERRY_PLANKS, 6),
            w(Material.OAK_LOG, 12), w(Material.SPRUCE_LOG, 8), w(Material.BIRCH_LOG, 8),
            w(Material.STONE_BRICKS, 12), w(Material.CHISELED_STONE_BRICKS, 6), w(Material.BRICKS, 10),
            w(Material.DEEPSLATE_BRICKS, 8), w(Material.POLISHED_BLACKSTONE_BRICKS, 6), w(Material.MUD_BRICKS, 6),
            w(Material.SANDSTONE, 8), w(Material.SMOOTH_STONE, 8), w(Material.QUARTZ_BLOCK, 6),
            w(Material.SMOOTH_QUARTZ, 5), w(Material.CUT_COPPER, 6), w(Material.TERRACOTTA, 8),
            w(Material.BOOKSHELF, 5), w(Material.GLASS, 8), w(Material.POLISHED_ANDESITE, 6))),

    NATURE("Nature", "<gradient:#7CB342:#C5E1A5>", Material.GRASS_BLOCK, DropMode.BLOCK, List.of(
            w(Material.GRASS_BLOCK, 24), w(Material.DIRT, 20), w(Material.COARSE_DIRT, 12),
            w(Material.PODZOL, 10), w(Material.MOSS_BLOCK, 12), w(Material.ROOTED_DIRT, 8),
            w(Material.MUD, 8), w(Material.PACKED_MUD, 6), w(Material.CLAY, 8),
            w(Material.MYCELIUM, 5), w(Material.MUDDY_MANGROVE_ROOTS, 4),
            w(Material.OAK_LOG, 10), w(Material.BIRCH_LOG, 8), w(Material.SPRUCE_LOG, 8),
            w(Material.PUMPKIN, 6), w(Material.MELON, 6), w(Material.HAY_BLOCK, 5),
            w(Material.BAMBOO_BLOCK, 5))),

    NETHER("Nether", "<gradient:#C62828:#FF8A65>", Material.NETHERRACK, DropMode.BLOCK, List.of(
            w(Material.NETHERRACK, 36), w(Material.NETHER_BRICKS, 14), w(Material.RED_NETHER_BRICKS, 6),
            w(Material.CHISELED_NETHER_BRICKS, 5), w(Material.BLACKSTONE, 12), w(Material.POLISHED_BLACKSTONE, 8),
            w(Material.GILDED_BLACKSTONE, 3), w(Material.BASALT, 10), w(Material.SMOOTH_BASALT, 6),
            w(Material.MAGMA_BLOCK, 8), w(Material.GLOWSTONE, 8), w(Material.SHROOMLIGHT, 5),
            w(Material.NETHER_WART_BLOCK, 6), w(Material.WARPED_WART_BLOCK, 5),
            w(Material.WARPED_NYLIUM, 6), w(Material.CRIMSON_NYLIUM, 6),
            w(Material.CRIMSON_STEM, 6), w(Material.WARPED_STEM, 6),
            w(Material.SOUL_SAND, 8), w(Material.SOUL_SOIL, 6), w(Material.BONE_BLOCK, 4),
            w(Material.QUARTZ_BLOCK, 6), w(Material.NETHER_QUARTZ_ORE, 4), w(Material.NETHER_GOLD_ORE, 3))),

    SAND("Sand", "<gradient:#FDD835:#FFF59D>", Material.SAND, DropMode.BLOCK, List.of(
            w(Material.SAND, 28), w(Material.RED_SAND, 18), w(Material.GRAVEL, 16),
            w(Material.SANDSTONE, 14), w(Material.CUT_SANDSTONE, 8), w(Material.SMOOTH_SANDSTONE, 6),
            w(Material.CHISELED_SANDSTONE, 5), w(Material.RED_SANDSTONE, 10), w(Material.CUT_RED_SANDSTONE, 6),
            w(Material.SMOOTH_RED_SANDSTONE, 5), w(Material.CHISELED_RED_SANDSTONE, 4),
            w(Material.GLASS, 8), w(Material.CLAY, 8), w(Material.TERRACOTTA, 6),
            w(Material.ORANGE_TERRACOTTA, 4), w(Material.WHITE_TERRACOTTA, 4), w(Material.YELLOW_TERRACOTTA, 4))),

    OCEAN("Ocean", "<gradient:#0097A7:#80DEEA>", Material.PRISMARINE, DropMode.BLOCK, List.of(
            w(Material.PRISMARINE, 18), w(Material.PRISMARINE_BRICKS, 14), w(Material.DARK_PRISMARINE, 12),
            w(Material.SEA_LANTERN, 6), w(Material.SPONGE, 5), w(Material.WET_SPONGE, 3),
            // Dead coral variants: live coral dries out to these in air anyway, so a node
            // would silently become/give the dead block — use them directly (stable + honest).
            w(Material.DEAD_TUBE_CORAL_BLOCK, 6), w(Material.DEAD_BRAIN_CORAL_BLOCK, 6), w(Material.DEAD_BUBBLE_CORAL_BLOCK, 6),
            w(Material.DEAD_FIRE_CORAL_BLOCK, 6), w(Material.DEAD_HORN_CORAL_BLOCK, 6),
            w(Material.PACKED_ICE, 6), w(Material.BLUE_ICE, 4), w(Material.DRIED_KELP_BLOCK, 6),
            w(Material.MAGMA_BLOCK, 5), w(Material.GRAVEL, 12), w(Material.SAND, 14), w(Material.CLAY, 10))),

    CONCRETE("Concrete", "<gradient:#90A4AE:#ECEFF1>", Material.LIGHT_BLUE_CONCRETE, DropMode.BLOCK, List.of(
            w(Material.WHITE_CONCRETE, 8), w(Material.ORANGE_CONCRETE, 8), w(Material.MAGENTA_CONCRETE, 8),
            w(Material.LIGHT_BLUE_CONCRETE, 8), w(Material.YELLOW_CONCRETE, 8), w(Material.LIME_CONCRETE, 8),
            w(Material.PINK_CONCRETE, 8), w(Material.GRAY_CONCRETE, 8), w(Material.LIGHT_GRAY_CONCRETE, 8),
            w(Material.CYAN_CONCRETE, 8), w(Material.PURPLE_CONCRETE, 8), w(Material.BLUE_CONCRETE, 8),
            w(Material.BROWN_CONCRETE, 8), w(Material.GREEN_CONCRETE, 8), w(Material.RED_CONCRETE, 8),
            w(Material.BLACK_CONCRETE, 8))),

    WOOL("Wool", "<gradient:#F06292:#FFF176>", Material.WHITE_WOOL, DropMode.BLOCK, List.of(
            w(Material.WHITE_WOOL, 8), w(Material.ORANGE_WOOL, 8), w(Material.MAGENTA_WOOL, 8),
            w(Material.LIGHT_BLUE_WOOL, 8), w(Material.YELLOW_WOOL, 8), w(Material.LIME_WOOL, 8),
            w(Material.PINK_WOOL, 8), w(Material.GRAY_WOOL, 8), w(Material.LIGHT_GRAY_WOOL, 8),
            w(Material.CYAN_WOOL, 8), w(Material.PURPLE_WOOL, 8), w(Material.BLUE_WOOL, 8),
            w(Material.BROWN_WOOL, 8), w(Material.GREEN_WOOL, 8), w(Material.RED_WOOL, 8),
            w(Material.BLACK_WOOL, 8)));

    public enum DropMode { VANILLA, BLOCK }

    public record Weighted(Material material, int weight) {}
    private static Weighted w(Material material, int weight) { return new Weighted(material, weight); }

    private final String displayName;
    private final String colorGradient;
    private final Material icon;
    private final DropMode dropMode;
    private final List<Weighted> pool;

    CommunityNodeType(String displayName, String colorGradient, Material icon, DropMode dropMode, List<Weighted> pool) {
        this.displayName = displayName;
        this.colorGradient = colorGradient;
        this.icon = icon;
        this.dropMode = dropMode;
        this.pool = pool;
    }

    public String id() { return name().toLowerCase(Locale.ROOT); }
    public String displayName() { return displayName; }
    public String colorGradient() { return colorGradient; }
    public Material icon() { return icon; }
    public DropMode dropMode() { return dropMode; }

    /** The immutable code-default pool — the seed {@link NodePoolManager} copies and can override. */
    public List<Weighted> defaultPool() { return pool; }

    /** Lowercase ids of all types, for command tab-completion. */
    public static List<String> ids() {
        List<String> out = new ArrayList<>(values().length);
        for (CommunityNodeType type : values()) out.add(type.id());
        return out;
    }

    /** Comma-joined lowercase ids, for usage/help text. */
    public static String idList() {
        return String.join(", ", ids());
    }

    public static CommunityNodeType byId(String id) {
        if (id == null) return null;
        // Legacy alias: the retired "Colorful" type (concrete colours + glass) is now CONCRETE,
        // so existing grant items / placed nodes migrate to the closest equivalent.
        if (id.equalsIgnoreCase("colorful")) return CONCRETE;
        for (CommunityNodeType type : values()) {
            if (type.name().equalsIgnoreCase(id) || type.id().equalsIgnoreCase(id)) return type;
        }
        return null;
    }
}
