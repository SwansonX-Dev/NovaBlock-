package com.nova.novablock.phase;

import com.nova.novablock.NovaBlock;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PhaseManager {

    private final NovaBlock plugin;
    private final List<Phase> phases = new ArrayList<>();
    private final List<Phase> netherPhases = new ArrayList<>();

    public PhaseManager(NovaBlock plugin) { this.plugin = plugin; }

    public void loadPhases() {
        phases.clear();
        // Default phases — could be moved to phases.yml later. 12 phases.
        phases.add(p(0, "plains", "Green Plains", "#7CFC00", 250,
                List.of(
                        b(Material.GRASS_BLOCK, 42),
                        b(Material.DIRT, 28),
                        b(Material.OAK_LOG, 16),
                        b(Material.OAK_LEAVES, 12),
                        b(Material.MOSS_BLOCK, 5),
                        b(Material.HAY_BLOCK, 2),
                        b(Material.POPPY, 2),
                        b(Material.WHEAT, 2),
                        b(Material.CARROTS, 2),
                        b(Material.POTATOES, 2),
                        b(Material.PUMPKIN, 1),
                        b(Material.MELON, 1),
                        b(Material.CHEST, 3)
                ),
                Phase.mobList(EntityType.COW, EntityType.PIG, EntityType.CHICKEN, EntityType.SHEEP, EntityType.VILLAGER),
                null, List.of("parkour_overworld")));

        phases.add(p(1, "underground", "Underground", "#8B6F47", 450,
                List.of(
                        b(Material.STONE, 42),
                        b(Material.COBBLESTONE, 24),
                        b(Material.ANDESITE, 8),
                        b(Material.DIORITE, 6),
                        b(Material.GRANITE, 6),
                        b(Material.COAL_ORE, 14),
                        b(Material.IRON_ORE, 14),
                        b(Material.RAW_IRON_BLOCK, 2),
                        b(Material.GRAVEL, 6),
                        b(Material.DIRT, 6),
                        b(Material.CLAY, 3),
                        b(Material.LAPIS_ORE, 2),
                        b(Material.REDSTONE_ORE, 2),
                        b(Material.CHEST, 3)
                ),
                Phase.mobList(EntityType.ZOMBIE, EntityType.SKELETON, EntityType.SPIDER, EntityType.SILVERFISH),
                null, List.of("arena_overworld")));

        phases.add(p(2, "snow", "Frozen Tundra", "#A0E7FF", 650,
                List.of(
                        b(Material.SNOW_BLOCK, 35),
                        b(Material.ICE, 12),
                        b(Material.PACKED_ICE, 18),
                        b(Material.SPRUCE_LOG, 18),
                        b(Material.SPRUCE_LEAVES, 8),
                        b(Material.BLUE_ICE, 6),
                        b(Material.IRON_ORE, 6),
                        b(Material.PUMPKIN, 4),
                        b(Material.BEETROOTS, 2),
                        b(Material.DIAMOND_ORE, 2),
                        b(Material.SWEET_BERRY_BUSH, 3),
                        b(Material.CHEST, 3)
                ),
                Phase.mobList(EntityType.STRAY, EntityType.POLAR_BEAR, EntityType.WOLF, EntityType.FOX),
                "frostborn_sentinel", List.of("puzzle_overworld", "arena_overworld")));

        phases.add(p(3, "desert", "Burning Desert", "#FFD27A", 850,
                List.of(
                        b(Material.SAND, 36),
                        b(Material.SANDSTONE, 24),
                        b(Material.RED_SAND, 10),
                        b(Material.TERRACOTTA, 8),
                        b(Material.CACTUS, 8),
                        b(Material.DEAD_BUSH, 5),
                        b(Material.SUGAR_CANE, 4),
                        b(Material.MELON, 3),
                        b(Material.IRON_ORE, 5),
                        b(Material.GOLD_ORE, 8),
                        b(Material.RAW_GOLD_BLOCK, 2),
                        b(Material.CHEST, 3)
                ),
                // Camel diluted (was 1/3 → 1/8) so it stops dominating spawns;
                // armadillo added so deserts have their signature 1.20.5 mob.
                Phase.mobList(
                        EntityType.HUSK, EntityType.HUSK, EntityType.HUSK,
                        EntityType.RABBIT, EntityType.RABBIT,
                        EntityType.ARMADILLO, EntityType.ARMADILLO,
                        EntityType.CAMEL),
                null, List.of("parkour_overworld", "puzzle_overworld")));

        phases.add(p(4, "ocean", "Sunken Reef", "#3FB6FF", 1050,
                List.of(
                        b(Material.PRISMARINE, 25),
                        b(Material.DARK_PRISMARINE, 20),
                        b(Material.PRISMARINE_BRICKS, 18),
                        b(Material.SAND, 8),
                        b(Material.GRAVEL, 8),
                        b(Material.SEA_LANTERN, 5),
                        b(Material.SPONGE, 5),
                        b(Material.TUBE_CORAL_BLOCK, 8),
                        b(Material.BRAIN_CORAL_BLOCK, 4),
                        b(Material.CONDUIT, 1),
                        b(Material.CHEST, 3)
                ),
                Phase.mobList(EntityType.DROWNED, EntityType.GUARDIAN, EntityType.PUFFERFISH, EntityType.SQUID),
                null, List.of("arena_overworld")));

        phases.add(p(5, "nether", "Nether Gates", "#FF4D4D", 1300,
                List.of(
                        b(Material.NETHERRACK, 42),
                        b(Material.SOUL_SAND, 15),
                        b(Material.BASALT, 10),
                        b(Material.BLACKSTONE, 8),
                        b(Material.NETHER_QUARTZ_ORE, 10),
                        b(Material.NETHER_GOLD_ORE, 10),
                        b(Material.CRIMSON_NYLIUM, 8),
                        b(Material.WARPED_NYLIUM, 6),
                        b(Material.GLOWSTONE, 5),
                        b(Material.MAGMA_BLOCK, 6),
                        // ~1.5% of rolls → ~20 obsidian across the phase, enough
                        // to assemble the first Nether portal without paying shop.
                        b(Material.OBSIDIAN, 2),
                        b(Material.CHEST, 3)
                ),
                Phase.mobList(EntityType.PIGLIN, EntityType.BLAZE, EntityType.MAGMA_CUBE, EntityType.HOGLIN),
                "magma_tyrant", List.of("arena_overworld", "puzzle_overworld")));

        phases.add(p(6, "ancient", "Ancient Mines", "#FFB347", 1600,
                List.of(
                        b(Material.DEEPSLATE, 40),
                        b(Material.COBBLED_DEEPSLATE, 18),
                        b(Material.DEEPSLATE_IRON_ORE, 12),
                        b(Material.RAW_IRON_BLOCK, 3),
                        b(Material.DEEPSLATE_DIAMOND_ORE, 6),
                        b(Material.DEEPSLATE_GOLD_ORE, 8),
                        b(Material.DEEPSLATE_REDSTONE_ORE, 8),
                        b(Material.DEEPSLATE_EMERALD_ORE, 3),
                        b(Material.ANCIENT_DEBRIS, 1),
                        b(Material.CHEST, 3)
                ),
                // Wardens removed from random spawns — too punishing for casual mining.
                // Boss-style Warden fights can still be triggered explicitly via the boss system.
                Phase.mobList(EntityType.SILVERFISH, EntityType.ZOMBIE, EntityType.ALLAY, EntityType.ENDERMITE),
                null, List.of("puzzle_overworld")));

        phases.add(p(7, "garden", "Lush Garden", "#7BFFBB", 1900,
                List.of(
                        b(Material.MOSS_BLOCK, 30),
                        b(Material.AZALEA, 10),
                        b(Material.FLOWERING_AZALEA, 8),
                        b(Material.OAK_LEAVES, 12),
                        b(Material.MOSSY_COBBLESTONE, 10),
                        b(Material.CLAY, 5),
                        b(Material.ROOTED_DIRT, 6),
                        b(Material.SCULK, 5),
                        b(Material.BAMBOO_BLOCK, 10),
                        b(Material.CAKE, 1),
                        b(Material.CHEST, 3)
                ),
                Phase.mobList(EntityType.AXOLOTL, EntityType.GLOW_SQUID, EntityType.ALLAY, EntityType.PARROT),
                null, List.of("parkour_overworld", "puzzle_overworld")));

        phases.add(p(8, "stronghold", "Stronghold Halls", "#9966CC", 2300,
                List.of(
                        b(Material.STONE_BRICKS, 30),
                        b(Material.MOSSY_STONE_BRICKS, 18),
                        b(Material.CRACKED_STONE_BRICKS, 12),
                        b(Material.CHISELED_STONE_BRICKS, 6),
                        b(Material.BOOKSHELF, 14),
                        b(Material.ENDER_CHEST, 1),
                        b(Material.IRON_BARS, 8),
                        b(Material.OBSIDIAN, 4),
                        b(Material.CHEST, 3)
                ),
                Phase.mobList(EntityType.SILVERFISH, EntityType.ENDERMAN, EntityType.EVOKER, EntityType.VINDICATOR, EntityType.VILLAGER),
                null, List.of("arena_overworld", "puzzle_overworld")));

        phases.add(p(9, "end", "End Voyage", "#E6E0FF", 2800,
                List.of(
                        b(Material.END_STONE, 40),
                        b(Material.PURPUR_BLOCK, 16),
                        b(Material.PURPUR_PILLAR, 10),
                        b(Material.OBSIDIAN, 10),
                        b(Material.END_STONE_BRICKS, 14),
                        b(Material.SHULKER_BOX, 1),
                        b(Material.ENDER_CHEST, 2),
                        b(Material.CHEST, 3)
                ),
                Phase.mobList(EntityType.ENDERMAN, EntityType.SHULKER, EntityType.PHANTOM),
                "void_herald", List.of("arena_overworld")));

        phases.add(p(10, "celestial", "Celestial Vault", "#FFE680", 3400,
                List.of(
                        b(Material.AMETHYST_BLOCK, 24),
                        b(Material.CALCITE, 18),
                        b(Material.SMOOTH_BASALT, 18),
                        b(Material.BUDDING_AMETHYST, 5),
                        b(Material.TUFF, 12),
                        b(Material.GLOWSTONE, 8),
                        b(Material.SEA_LANTERN, 5),
                        b(Material.BEACON, 1),
                        b(Material.CHEST, 3)
                ),
                Phase.mobList(EntityType.ALLAY, EntityType.VEX, EntityType.BREEZE),
                null, List.of("puzzle_overworld", "arena_overworld")));

        phases.add(p(11, "void", "Void Beyond", "#9C27B0", 4200,
                List.of(
                        b(Material.OBSIDIAN, 12),
                        b(Material.CRYING_OBSIDIAN, 6),
                        b(Material.DEEPSLATE, 14),
                        b(Material.SCULK_CATALYST, 5),
                        b(Material.SCULK_SHRIEKER, 4),
                        b(Material.SCULK, 18),
                        b(Material.SCULK_SENSOR, 3),
                        b(Material.END_STONE_BRICKS, 10),
                        b(Material.BLACKSTONE, 6),
                        b(Material.NETHERITE_BLOCK, 1),
                        b(Material.CHEST, 3)
                ),
                // Warden / Wither / Ravager removed from random spawns — would
                // routinely kill players just for mining. Reserved for explicit boss fights.
                Phase.mobList(EntityType.VEX, EntityType.PHANTOM, EntityType.EVOKER, EntityType.SHULKER),
                "void_herald", List.of("arena_overworld")));
    }

    private static Phase p(int idx, String id, String name, String color, int req,
                           List<PhaseBlock> blocks, List<EntityType> mobs,
                           String boss, List<String> rooms) {
        return new Phase(idx, id, name, color, req, blocks, mobs, boss, rooms);
    }

    private static PhaseBlock b(Material m, int w) { return new PhaseBlock(m, w); }

    public Phase get(int index) {
        if (index < 0 || index >= phases.size()) return null;
        return phases.get(index);
    }

    public Phase getOrLast(int index) {
        if (phases.isEmpty()) return null;
        if (index < 0) return phases.get(0);
        if (index >= phases.size()) return phases.get(phases.size() - 1);
        return phases.get(index);
    }

    public List<Phase> all() { return Collections.unmodifiableList(phases); }
    public int phaseCount() { return phases.size(); }

    /**
     * Twelve Nether phases — unlocked when an island clears Overworld Phase 6.
     * Mirrors the Overworld {@code requiredBlocks} curve so the curve doesn't
     * surprise returning players. Final phase carries the {@code ashen_warlord}
     * climax boss. Loot-room IDs use the {@code _nether} suffix added in Slice 3.
     */
    public void loadNetherPhases() {
        netherPhases.clear();
        netherPhases.add(p(0, "nether_outpost", "Crimson Outpost", "#FF4D4D", 250,
                List.of(
                        b(Material.NETHERRACK, 48),
                        b(Material.NETHER_BRICKS, 18),
                        b(Material.RED_NETHER_BRICKS, 8),
                        b(Material.NETHER_QUARTZ_ORE, 10),
                        b(Material.GLOWSTONE, 5),
                        b(Material.SOUL_SAND, 4),
                        b(Material.CHEST, 3)
                ),
                Phase.mobList(EntityType.ZOMBIFIED_PIGLIN, EntityType.PIGLIN),
                null, List.of("parkour_nether")));
        netherPhases.add(p(1, "nether_crimson_grove", "Crimson Grove", "#A8324A", 400,
                List.of(
                        b(Material.CRIMSON_NYLIUM, 36),
                        b(Material.CRIMSON_STEM, 18),
                        b(Material.CRIMSON_HYPHAE, 8),
                        b(Material.NETHER_WART_BLOCK, 14),
                        b(Material.SHROOMLIGHT, 6),
                        b(Material.CRIMSON_FUNGUS, 4),
                        b(Material.CHEST, 3)
                ),
                Phase.mobList(EntityType.PIGLIN, EntityType.HOGLIN),
                null, List.of("arena_nether")));
        netherPhases.add(p(2, "nether_basalt_delta", "Basalt Delta", "#3C2F4A", 550,
                List.of(
                        b(Material.BASALT, 30),
                        b(Material.SMOOTH_BASALT, 12),
                        b(Material.BLACKSTONE, 18),
                        b(Material.MAGMA_BLOCK, 12),
                        b(Material.GLOWSTONE, 6),
                        b(Material.GRAVEL, 6),
                        b(Material.CHEST, 3)
                ),
                Phase.mobList(EntityType.MAGMA_CUBE, EntityType.STRIDER, EntityType.GHAST),
                null, List.of("puzzle_nether")));
        netherPhases.add(p(3, "nether_warped_grove", "Warped Grove", "#1C9A91", 750,
                List.of(
                        b(Material.WARPED_NYLIUM, 36),
                        b(Material.WARPED_STEM, 18),
                        b(Material.WARPED_HYPHAE, 8),
                        b(Material.WARPED_WART_BLOCK, 14),
                        b(Material.SHROOMLIGHT, 6),
                        b(Material.TWISTING_VINES, 4),
                        b(Material.CHEST, 3)
                ),
                Phase.mobList(EntityType.ENDERMAN, EntityType.PIGLIN),
                null, List.of("parkour_nether")));
        netherPhases.add(p(4, "nether_soul_valley", "Soul Valley", "#7CDFFF", 950,
                List.of(
                        b(Material.SOUL_SAND, 30),
                        b(Material.SOUL_SOIL, 22),
                        b(Material.BONE_BLOCK, 14),
                        b(Material.SOUL_TORCH, 4),
                        b(Material.BLACKSTONE, 10),
                        b(Material.GLOWSTONE, 4),
                        b(Material.CHEST, 3)
                ),
                Phase.mobList(EntityType.SKELETON, EntityType.GHAST),
                null, List.of("arena_nether")));
        netherPhases.add(p(5, "nether_blackstone_keep", "Blackstone Keep", "#2A2230", 1200,
                List.of(
                        b(Material.POLISHED_BLACKSTONE, 26),
                        b(Material.POLISHED_BLACKSTONE_BRICKS, 18),
                        b(Material.CHISELED_POLISHED_BLACKSTONE, 8),
                        b(Material.GILDED_BLACKSTONE, 6),
                        b(Material.BLACKSTONE, 14),
                        b(Material.NETHER_GOLD_ORE, 8),
                        b(Material.CHEST, 3)
                ),
                Phase.mobList(EntityType.PIGLIN, EntityType.WITHER_SKELETON),
                null, List.of("puzzle_nether")));
        netherPhases.add(p(6, "nether_bastion", "Bastion Halls", "#C7A04B", 1500,
                List.of(
                        b(Material.POLISHED_BLACKSTONE_BRICKS, 22),
                        b(Material.NETHER_BRICKS, 14),
                        b(Material.GILDED_BLACKSTONE, 10),
                        b(Material.GOLD_BLOCK, 4),
                        b(Material.CHAIN, 4),
                        b(Material.LODESTONE, 2),
                        b(Material.MAGMA_BLOCK, 8),
                        b(Material.CHEST, 3)
                ),
                Phase.mobList(EntityType.PIGLIN_BRUTE, EntityType.PIGLIN, EntityType.HOGLIN),
                "piglin_chieftain", List.of("arena_nether")));
        netherPhases.add(p(7, "nether_magma_caverns", "Magma Caverns", "#FF6E2B", 1850,
                List.of(
                        b(Material.MAGMA_BLOCK, 28),
                        b(Material.NETHER_QUARTZ_ORE, 14),
                        b(Material.BASALT, 14),
                        b(Material.GLOWSTONE, 8),
                        b(Material.OBSIDIAN, 4),
                        b(Material.SOUL_FIRE, 2),
                        b(Material.CHEST, 3)
                ),
                Phase.mobList(EntityType.MAGMA_CUBE, EntityType.BLAZE),
                null, List.of("parkour_nether")));
        netherPhases.add(p(8, "nether_fortress", "Fortress of Ash", "#5A2A2A", 2250,
                List.of(
                        b(Material.NETHER_BRICKS, 30),
                        b(Material.NETHER_BRICK_FENCE, 10),
                        b(Material.NETHER_WART_BLOCK, 8),
                        b(Material.GLOWSTONE, 6),
                        b(Material.SOUL_SAND, 4),
                        b(Material.NETHER_WART, 6),
                        b(Material.CHEST, 3)
                ),
                Phase.mobList(EntityType.BLAZE, EntityType.WITHER_SKELETON),
                null, List.of("arena_nether")));
        netherPhases.add(p(9, "nether_warped_temple", "Warped Temple", "#3CD6B7", 2700,
                List.of(
                        b(Material.WARPED_HYPHAE, 24),
                        b(Material.WARPED_PLANKS, 10),
                        b(Material.RESPAWN_ANCHOR, 1),
                        b(Material.GLOWSTONE, 8),
                        b(Material.GILDED_BLACKSTONE, 4),
                        b(Material.SHROOMLIGHT, 6),
                        b(Material.ENDER_CHEST, 2),
                        b(Material.CHEST, 3)
                ),
                Phase.mobList(EntityType.ENDERMAN, EntityType.PIGLIN_BRUTE),
                null, List.of("puzzle_nether")));
        netherPhases.add(p(10, "nether_blazing_throne", "Blazing Throne", "#FFB347", 3300,
                List.of(
                        b(Material.GILDED_BLACKSTONE, 20),
                        b(Material.MAGMA_BLOCK, 14),
                        b(Material.GLOWSTONE, 10),
                        b(Material.NETHERITE_BLOCK, 1),
                        b(Material.GOLD_BLOCK, 4),
                        b(Material.NETHER_GOLD_ORE, 10),
                        b(Material.SHROOMLIGHT, 4),
                        b(Material.CHEST, 3)
                ),
                Phase.mobList(EntityType.BLAZE, EntityType.GHAST, EntityType.MAGMA_CUBE),
                null, List.of("arena_nether")));
        netherPhases.add(p(11, "nether_ashen_throne", "Ashen Throne", "#FF6347", 4200,
                List.of(
                        b(Material.ANCIENT_DEBRIS, 1),
                        b(Material.NETHERITE_BLOCK, 1),
                        b(Material.GILDED_BLACKSTONE, 12),
                        b(Material.CRYING_OBSIDIAN, 6),
                        b(Material.OBSIDIAN, 8),
                        b(Material.RESPAWN_ANCHOR, 2),
                        b(Material.SOUL_FIRE, 4),
                        b(Material.GLOWSTONE, 8),
                        b(Material.CHEST, 3)
                ),
                Phase.mobList(EntityType.WITHER_SKELETON, EntityType.PIGLIN_BRUTE),
                "ashen_warlord", List.of("arena_nether")));
    }

    public Phase getNether(int index) {
        if (index < 0 || index >= netherPhases.size()) return null;
        return netherPhases.get(index);
    }

    public Phase getNetherOrLast(int index) {
        if (netherPhases.isEmpty()) return null;
        if (index < 0) return netherPhases.get(0);
        if (index >= netherPhases.size()) return netherPhases.get(netherPhases.size() - 1);
        return netherPhases.get(index);
    }

    public List<Phase> allNether() { return Collections.unmodifiableList(netherPhases); }
    public int netherPhaseCount() { return netherPhases.size(); }
}
