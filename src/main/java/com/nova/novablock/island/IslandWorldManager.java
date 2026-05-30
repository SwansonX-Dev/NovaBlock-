package com.nova.novablock.island;

import com.nova.novablock.NovaBlock;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.block.Biome;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;

import java.util.List;
import java.util.Random;

public class IslandWorldManager {

    public static final String DEFAULT_WORLD_NAME = "oneblock";
    public static final String LEGACY_WORLD_NAME = "novablock_world";
    public static final String DEFAULT_NETHER_WORLD_NAME = "oneblock_nether";
    /**
     * Distance between OneBlock slots. Loaded from config.yml `slotSize` on first
     * init; baked in for the life of the world (changing it later would shift
     * existing islands off their saved coordinates).
     */
    public static int SLOT_SIZE = 256;

    private final NovaBlock plugin;
    private World world;
    private World netherWorld;
    private String worldName = DEFAULT_WORLD_NAME;
    private String netherWorldName = DEFAULT_NETHER_WORLD_NAME;
    private boolean netherEnabled = true;

    public IslandWorldManager(NovaBlock plugin) { this.plugin = plugin; }

    public void init() {
        this.worldName = plugin.getConfig().getString("islandWorld", DEFAULT_WORLD_NAME);
        if (worldName == null || worldName.isBlank()) worldName = DEFAULT_WORLD_NAME;
        this.netherWorldName = plugin.getConfig().getString("netherIslandWorld", DEFAULT_NETHER_WORLD_NAME);
        if (netherWorldName == null || netherWorldName.isBlank()) netherWorldName = DEFAULT_NETHER_WORLD_NAME;
        this.netherEnabled = plugin.getConfig().getBoolean("netherEnabled", true);
        SLOT_SIZE = Math.max(32, plugin.getConfig().getInt("slotSize", 256));
        this.world = ensureWorld(worldName);
        // Reapply gamerules every startup so config edits take effect even on existing worlds.
        if (world != null) configure(world);
        if (netherEnabled) {
            this.netherWorld = ensureNetherWorld();
            if (netherWorld != null) configure(netherWorld);
        }
    }

    public World ensureWorld(String name) {
        String targetName = name == null || name.isBlank() ? worldName : name;
        World existing = Bukkit.getWorld(targetName);
        if (existing != null) return existing;
        // Don't set worldType — our ChunkGenerator handles every step, and FLAT
        // would otherwise look for a superflat layers preset that we don't supply.
        WorldCreator wc = new WorldCreator(targetName)
                .generator(new VoidGenerator())
                .biomeProvider(new SingleBiomeProvider(Biome.PLAINS))
                .generateStructures(false);
        World created = wc.createWorld();
        if (created != null) {
            configure(created);
        }
        return created;
    }

    /**
     * Same void-pad pattern as the Overworld world, but stamped as a Nether
     * environment so lighting/ambience reads correctly. We keep our own chunk
     * generator and biome provider — Paper won't insert vanilla Nether terrain
     * because {@link VoidGenerator} returns {@code false} for every generate-* hook.
     */
    public World ensureNetherWorld() {
        World existing = Bukkit.getWorld(netherWorldName);
        if (existing != null) return existing;
        WorldCreator wc = new WorldCreator(netherWorldName)
                .generator(new VoidGenerator())
                .biomeProvider(new SingleBiomeProvider(Biome.CRIMSON_FOREST))
                .environment(World.Environment.NETHER)
                .generateStructures(false);
        World created = wc.createWorld();
        if (created != null) {
            configure(created);
        }
        return created;
    }

    private void configure(World world) {
        var cfg = plugin.getConfig();
        boolean disableSpawning = cfg.getBoolean("disableNaturalSpawning", true);
        boolean keepInv = cfg.getBoolean("keepInventoryOnDeath", true);
        world.setSpawnFlags(false, false);
        world.setDifficulty(Difficulty.NORMAL);
        world.setGameRule(org.bukkit.GameRule.DO_MOB_SPAWNING, !disableSpawning);
        world.setGameRule(org.bukkit.GameRule.DO_DAYLIGHT_CYCLE, true);
        world.setGameRule(org.bukkit.GameRule.KEEP_INVENTORY, keepInv);
        world.setGameRule(org.bukkit.GameRule.DO_FIRE_TICK, false);
        world.setGameRule(org.bukkit.GameRule.MOB_GRIEFING, false);
    }

    public World getWorld() { return world; }
    public String worldName() { return worldName; }
    public World getNetherWorld() { return netherWorld; }
    public String netherWorldName() { return netherWorldName; }
    public boolean isNetherEnabled() { return netherEnabled; }

    public static final class VoidGenerator extends ChunkGenerator {
        @Override public void generateNoise(WorldInfo w, Random r, int cx, int cz, ChunkData d) {}
        @Override public void generateSurface(WorldInfo w, Random r, int cx, int cz, ChunkData d) {}
        @Override public void generateBedrock(WorldInfo w, Random r, int cx, int cz, ChunkData d) {}
        @Override public void generateCaves(WorldInfo w, Random r, int cx, int cz, ChunkData d) {}
        @Override public boolean shouldGenerateNoise() { return false; }
        @Override public boolean shouldGenerateSurface() { return false; }
        @Override public boolean shouldGenerateBedrock() { return false; }
        @Override public boolean shouldGenerateCaves() { return false; }
        @Override public boolean shouldGenerateDecorations() { return false; }
        @Override public boolean shouldGenerateMobs() { return false; }
        @Override public boolean shouldGenerateStructures() { return false; }
    }

    public static final class SingleBiomeProvider extends BiomeProvider {
        private final Biome biome;
        public SingleBiomeProvider(Biome biome) { this.biome = biome; }
        @Override public Biome getBiome(WorldInfo w, int x, int y, int z) { return biome; }
        @Override public List<Biome> getBiomes(WorldInfo w) { return List.of(biome); }
    }
}
