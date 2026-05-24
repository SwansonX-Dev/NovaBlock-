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
                Phase.mobList(EntityType.COW, EntityType.PIG, EntityType.CHICKEN, EntityType.SHEEP),
                null, List.of("parkour")));

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
                null, List.of("arena")));

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
                "frostborn_sentinel", List.of("puzzle", "arena")));

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
                Phase.mobList(EntityType.HUSK, EntityType.RABBIT, EntityType.CAMEL),
                null, List.of("parkour", "puzzle")));

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
                null, List.of("arena")));

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
                        b(Material.CHEST, 3)
                ),
                Phase.mobList(EntityType.PIGLIN, EntityType.BLAZE, EntityType.MAGMA_CUBE, EntityType.HOGLIN),
                "magma_tyrant", List.of("arena", "puzzle")));

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
                null, List.of("puzzle")));

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
                null, List.of("parkour", "puzzle")));

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
                Phase.mobList(EntityType.SILVERFISH, EntityType.ENDERMAN, EntityType.EVOKER, EntityType.VINDICATOR),
                null, List.of("arena", "puzzle")));

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
                "void_herald", List.of("arena")));

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
                null, List.of("puzzle", "arena")));

        phases.add(p(11, "void", "Void Beyond", "#9C27B0", 4200,
                List.of(
                        b(Material.OBSIDIAN, 28),
                        b(Material.CRYING_OBSIDIAN, 14),
                        b(Material.DEEPSLATE, 10),
                        b(Material.SCULK_CATALYST, 5),
                        b(Material.SCULK_SHRIEKER, 3),
                        b(Material.SCULK, 14),
                        b(Material.SCULK_SENSOR, 2),
                        b(Material.NETHERITE_BLOCK, 1),
                        b(Material.CHEST, 3)
                ),
                // Warden / Wither / Ravager removed from random spawns — would
                // routinely kill players just for mining. Reserved for explicit boss fights.
                Phase.mobList(EntityType.VEX, EntityType.PHANTOM, EntityType.EVOKER, EntityType.SHULKER),
                "void_herald", List.of("arena")));
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
}
