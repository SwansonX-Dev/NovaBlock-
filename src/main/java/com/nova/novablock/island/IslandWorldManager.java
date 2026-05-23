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

    public static final String WORLD_NAME = "novablock_world";
    public static final int SLOT_SIZE = 256;

    private final NovaBlock plugin;
    private World world;

    public IslandWorldManager(NovaBlock plugin) { this.plugin = plugin; }

    public void init() {
        World existing = Bukkit.getWorld(WORLD_NAME);
        if (existing != null) { this.world = existing; return; }
        // Don't set worldType — our ChunkGenerator handles every step, and FLAT
        // would otherwise look for a superflat layers preset that we don't supply.
        WorldCreator wc = new WorldCreator(WORLD_NAME)
                .generator(new VoidGenerator())
                .biomeProvider(new SingleBiomeProvider(Biome.PLAINS))
                .generateStructures(false);
        this.world = wc.createWorld();
        if (world != null) {
            world.setSpawnFlags(false, false);
            world.setDifficulty(Difficulty.NORMAL);
            world.setGameRule(org.bukkit.GameRule.DO_MOB_SPAWNING, false);
            world.setGameRule(org.bukkit.GameRule.DO_DAYLIGHT_CYCLE, true);
            world.setGameRule(org.bukkit.GameRule.KEEP_INVENTORY, true);
            world.setGameRule(org.bukkit.GameRule.DO_FIRE_TICK, false);
            world.setGameRule(org.bukkit.GameRule.MOB_GRIEFING, false);
        }
    }

    public World getWorld() { return world; }

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
