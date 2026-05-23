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
        phases.add(p(0, "plains", "Green Plains", "#7CFC00", 100,
                List.of(
                        b(Material.GRASS_BLOCK, 50),
                        b(Material.DIRT, 30),
                        b(Material.OAK_LOG, 10),
                        b(Material.OAK_LEAVES, 8),
                        b(Material.POPPY, 1),
                        b(Material.WHEAT_SEEDS, 1)
                ),
                Phase.mobList(EntityType.COW, EntityType.PIG, EntityType.CHICKEN, EntityType.SHEEP),
                null, List.of("parkour")));

        phases.add(p(1, "underground", "Underground", "#8B6F47", 150,
                List.of(
                        b(Material.STONE, 50),
                        b(Material.COBBLESTONE, 25),
                        b(Material.COAL_ORE, 12),
                        b(Material.IRON_ORE, 6),
                        b(Material.GRAVEL, 5),
                        b(Material.LAPIS_ORE, 1),
                        b(Material.REDSTONE_ORE, 1)
                ),
                Phase.mobList(EntityType.ZOMBIE, EntityType.SKELETON, EntityType.SPIDER, EntityType.SILVERFISH),
                null, List.of("arena")));

        phases.add(p(2, "snow", "Frozen Tundra", "#A0E7FF", 200,
                List.of(
                        b(Material.SNOW_BLOCK, 35),
                        b(Material.PACKED_ICE, 20),
                        b(Material.SPRUCE_LOG, 15),
                        b(Material.BLUE_ICE, 5),
                        b(Material.DIAMOND_ORE, 2),
                        b(Material.SWEET_BERRY_BUSH, 3)
                ),
                Phase.mobList(EntityType.STRAY, EntityType.POLAR_BEAR, EntityType.WOLF, EntityType.FOX),
                "frostborn_sentinel", List.of("puzzle", "arena")));

        phases.add(p(3, "desert", "Burning Desert", "#FFD27A", 250,
                List.of(
                        b(Material.SAND, 45),
                        b(Material.SANDSTONE, 25),
                        b(Material.CACTUS, 8),
                        b(Material.DEAD_BUSH, 5),
                        b(Material.GOLD_ORE, 6),
                        b(Material.RAW_GOLD_BLOCK, 1)
                ),
                Phase.mobList(EntityType.HUSK, EntityType.RABBIT, EntityType.CAMEL),
                null, List.of("parkour", "puzzle")));

        phases.add(p(4, "ocean", "Sunken Reef", "#3FB6FF", 300,
                List.of(
                        b(Material.PRISMARINE, 25),
                        b(Material.DARK_PRISMARINE, 20),
                        b(Material.PRISMARINE_BRICKS, 18),
                        b(Material.SEA_LANTERN, 5),
                        b(Material.SPONGE, 5),
                        b(Material.TUBE_CORAL_BLOCK, 8),
                        b(Material.BRAIN_CORAL_BLOCK, 4),
                        b(Material.CONDUIT, 1)
                ),
                Phase.mobList(EntityType.DROWNED, EntityType.GUARDIAN, EntityType.PUFFERFISH, EntityType.SQUID),
                null, List.of("arena")));

        phases.add(p(5, "nether", "Nether Gates", "#FF4D4D", 400,
                List.of(
                        b(Material.NETHERRACK, 50),
                        b(Material.SOUL_SAND, 15),
                        b(Material.NETHER_QUARTZ_ORE, 10),
                        b(Material.NETHER_GOLD_ORE, 8),
                        b(Material.CRIMSON_NYLIUM, 8),
                        b(Material.WARPED_NYLIUM, 6),
                        b(Material.GLOWSTONE, 3),
                        b(Material.MAGMA_BLOCK, 4)
                ),
                Phase.mobList(EntityType.PIGLIN, EntityType.BLAZE, EntityType.MAGMA_CUBE, EntityType.HOGLIN),
                "magma_tyrant", List.of("arena", "puzzle")));

        phases.add(p(6, "ancient", "Ancient Mines", "#FFB347", 500,
                List.of(
                        b(Material.DEEPSLATE, 40),
                        b(Material.COBBLED_DEEPSLATE, 15),
                        b(Material.DEEPSLATE_DIAMOND_ORE, 5),
                        b(Material.DEEPSLATE_GOLD_ORE, 8),
                        b(Material.DEEPSLATE_REDSTONE_ORE, 8),
                        b(Material.DEEPSLATE_EMERALD_ORE, 3),
                        b(Material.ANCIENT_DEBRIS, 1)
                ),
                // Wardens removed from random spawns — too punishing for casual mining.
                // Boss-style Warden fights can still be triggered explicitly via the boss system.
                Phase.mobList(EntityType.SILVERFISH, EntityType.ZOMBIE, EntityType.ALLAY, EntityType.ENDERMITE),
                null, List.of("puzzle")));

        phases.add(p(7, "garden", "Lush Garden", "#7BFFBB", 600,
                List.of(
                        b(Material.MOSS_BLOCK, 30),
                        b(Material.AZALEA, 8),
                        b(Material.FLOWERING_AZALEA, 4),
                        b(Material.OAK_LEAVES, 10),
                        b(Material.MOSSY_COBBLESTONE, 10),
                        b(Material.SCULK, 5),
                        b(Material.BAMBOO_BLOCK, 8),
                        b(Material.CAKE, 1)
                ),
                Phase.mobList(EntityType.AXOLOTL, EntityType.GLOW_SQUID, EntityType.ALLAY, EntityType.PARROT),
                null, List.of("parkour", "puzzle")));

        phases.add(p(8, "stronghold", "Stronghold Halls", "#9966CC", 700,
                List.of(
                        b(Material.STONE_BRICKS, 30),
                        b(Material.MOSSY_STONE_BRICKS, 15),
                        b(Material.CRACKED_STONE_BRICKS, 12),
                        b(Material.CHISELED_STONE_BRICKS, 6),
                        b(Material.BOOKSHELF, 10),
                        b(Material.ENDER_CHEST, 1),
                        b(Material.IRON_BARS, 5),
                        b(Material.OBSIDIAN, 3)
                ),
                Phase.mobList(EntityType.SILVERFISH, EntityType.ENDERMAN, EntityType.EVOKER, EntityType.VINDICATOR),
                null, List.of("arena", "puzzle")));

        phases.add(p(9, "end", "End Voyage", "#E6E0FF", 900,
                List.of(
                        b(Material.END_STONE, 40),
                        b(Material.PURPUR_BLOCK, 12),
                        b(Material.PURPUR_PILLAR, 8),
                        b(Material.OBSIDIAN, 8),
                        b(Material.END_STONE_BRICKS, 10),
                        b(Material.SHULKER_BOX, 1),
                        b(Material.ENDER_CHEST, 2)
                ),
                Phase.mobList(EntityType.ENDERMAN, EntityType.SHULKER, EntityType.PHANTOM),
                "void_herald", List.of("arena")));

        phases.add(p(10, "celestial", "Celestial Vault", "#FFE680", 1200,
                List.of(
                        b(Material.AMETHYST_BLOCK, 20),
                        b(Material.CALCITE, 15),
                        b(Material.SMOOTH_BASALT, 15),
                        b(Material.BUDDING_AMETHYST, 4),
                        b(Material.TUFF, 10),
                        b(Material.GLOWSTONE, 6),
                        b(Material.BEACON, 1)
                ),
                Phase.mobList(EntityType.ALLAY, EntityType.VEX, EntityType.BREEZE),
                null, List.of("puzzle", "arena")));

        phases.add(p(11, "void", "Void Beyond", "#9C27B0", 2000,
                List.of(
                        b(Material.OBSIDIAN, 25),
                        b(Material.CRYING_OBSIDIAN, 10),
                        b(Material.REINFORCED_DEEPSLATE, 8),
                        b(Material.SCULK_CATALYST, 4),
                        b(Material.SCULK_SHRIEKER, 3),
                        b(Material.SCULK, 10),
                        b(Material.NETHERITE_BLOCK, 1)
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
