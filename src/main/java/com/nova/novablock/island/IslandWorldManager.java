package com.nova.novablock.island;

import com.nova.novablock.NovaBlock;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.block.Biome;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;

import java.util.List;
import java.util.Random;

public class IslandWorldManager {

    public static final String DEFAULT_WORLD_NAME = "oneblock";
    public static final String LEGACY_WORLD_NAME = "novablock_world";
    public static final String DEFAULT_NETHER_WORLD_NAME = "oneblock_nether";
    public static final String DEFAULT_END_WORLD_NAME = "oneblock_end";
    /**
     * Distance between OneBlock slots. Loaded from config.yml `slotSize` on first
     * init; baked in for the life of the world (changing it later would shift
     * existing islands off their saved coordinates).
     */
    public static int SLOT_SIZE = 256;

    private final NovaBlock plugin;
    private World world;
    private World netherWorld;
    private World endWorld;
    private String worldName = DEFAULT_WORLD_NAME;
    private String netherWorldName = DEFAULT_NETHER_WORLD_NAME;
    private String endWorldName = DEFAULT_END_WORLD_NAME;
    private boolean netherEnabled = true;
    private boolean endEnabled = true;

    public IslandWorldManager(NovaBlock plugin) { this.plugin = plugin; }

    public void init() {
        this.worldName = plugin.getConfig().getString("islandWorld", DEFAULT_WORLD_NAME);
        if (worldName == null || worldName.isBlank()) worldName = DEFAULT_WORLD_NAME;
        this.netherWorldName = plugin.getConfig().getString("netherIslandWorld", DEFAULT_NETHER_WORLD_NAME);
        if (netherWorldName == null || netherWorldName.isBlank()) netherWorldName = DEFAULT_NETHER_WORLD_NAME;
        this.netherEnabled = plugin.getConfig().getBoolean("netherEnabled", true);
        this.endWorldName = plugin.getConfig().getString("endIslandWorld", DEFAULT_END_WORLD_NAME);
        if (endWorldName == null || endWorldName.isBlank()) endWorldName = DEFAULT_END_WORLD_NAME;
        this.endEnabled = plugin.getConfig().getBoolean("endEnabled", true);
        SLOT_SIZE = Math.max(32, plugin.getConfig().getInt("slotSize", 256));
        this.world = ensureWorld(worldName);
        // Reapply gamerules every startup so config edits take effect even on existing worlds.
        if (world != null) configureVoidWorld(world);
        if (netherEnabled) {
            this.netherWorld = ensureNetherWorld();
            if (netherWorld != null) configureVoidWorld(netherWorld);
        }
        if (endEnabled) {
            this.endWorld = ensureEndWorld();
            if (endWorld != null) configureVoidWorld(endWorld);
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
            configureVoidWorld(created);
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
            configureVoidWorld(created);
        }
        return created;
    }

    public void configureVoidWorld(World world) {
        var cfg = plugin.getConfig();
        boolean disableSpawning = cfg.getBoolean("disableNaturalSpawning", true);
        boolean keepInv = cfg.getBoolean("keepInventoryOnDeath", true);
        boolean mobGriefing = cfg.getBoolean("mobGriefing", true);
        world.setSpawnFlags(false, false);
        world.setDifficulty(Difficulty.NORMAL);
        world.setGameRule(org.bukkit.GameRule.DO_MOB_SPAWNING, !disableSpawning);
        world.setGameRule(org.bukkit.GameRule.DO_DAYLIGHT_CYCLE, true);
        world.setGameRule(org.bukkit.GameRule.KEEP_INVENTORY, keepInv);
        world.setGameRule(org.bukkit.GameRule.DO_FIRE_TICK, false);
        world.setGameRule(org.bukkit.GameRule.MOB_GRIEFING, mobGriefing);
    }

    /**
     * Void-pad world stamped as an End environment so the sky, ambience and
     * lighting read correctly. As with the Nether world, {@link VoidGenerator}
     * suppresses all vanilla generation (no main End island, no dragon, no
     * outer-island terrain) — the only blocks are the ones the OneBlock spawns.
     */
    public World ensureEndWorld() {
        World existing = Bukkit.getWorld(endWorldName);
        if (existing != null) return existing;
        WorldCreator wc = new WorldCreator(endWorldName)
                .generator(new VoidGenerator())
                .biomeProvider(new SingleBiomeProvider(Biome.THE_END))
                .environment(World.Environment.THE_END)
                .generateStructures(false);
        World created = wc.createWorld();
        if (created != null) {
            configureVoidWorld(created);
        }
        return created;
    }

    public World getWorld() { return world; }
    public String worldName() { return worldName; }
    public World getNetherWorld() { return netherWorld; }
    public String netherWorldName() { return netherWorldName; }
    public boolean isNetherEnabled() { return netherEnabled; }
    public World getEndWorld() { return endWorld; }
    public String endWorldName() { return endWorldName; }
    public boolean isEndEnabled() { return endEnabled; }

    /** Resolve the world name backing a dimension track. */
    public String worldName(Dimension d) {
        return switch (d) {
            case OVERWORLD -> worldName;
            case NETHER -> netherWorldName;
            case END -> endWorldName;
        };
    }

    /**
     * Unload the default vanilla Nether and End worlds if they're loaded.
     * Any players currently inside are evacuated to the configured /spawn
     * (or the primary world spawn as a fallback) so the unload doesn't fail
     * silently. Names default to the Bukkit defaults (`world_nether`,
     * `world_the_end`) but are configurable via {@code vanillaNetherWorld}
     * and {@code vanillaEndWorld}. Gated by {@code unloadVanillaDimensions}.
     */
    public void cleanupVanillaDimensions() {
        if (!plugin.getConfig().getBoolean("unloadVanillaDimensions", true)) return;
        String vanillaNether = plugin.getConfig().getString("vanillaNetherWorld", "world_nether");
        String vanillaEnd = plugin.getConfig().getString("vanillaEndWorld", "world_the_end");
        unloadIfPresent(vanillaNether);
        unloadIfPresent(vanillaEnd);
    }

    private void unloadIfPresent(String name) {
        if (name == null || name.isBlank()) return;
        World target = Bukkit.getWorld(name);
        if (target == null) return;
        // Don't try to unload one of our own worlds even if a server owner
        // accidentally points the config at it.
        if (name.equals(worldName) || name.equals(netherWorldName) || name.equals(endWorldName)) return;
        evacuate(target);
        if (Bukkit.unloadWorld(target, false)) {
            plugin.getLogger().info("Unloaded vanilla dimension: " + name);
        } else {
            plugin.getLogger().warning("Could not unload vanilla dimension: " + name
                    + " (players may still be inside or the world is busy).");
        }
    }

    private void evacuate(World doomed) {
        if (doomed.getPlayers().isEmpty()) return;
        org.bukkit.Location safe = plugin.spawn().location();
        if (safe == null || safe.getWorld() == null || safe.getWorld().equals(doomed)) {
            // Fall back to the first non-doomed loaded world.
            for (World w : Bukkit.getWorlds()) {
                if (!w.equals(doomed)) { safe = w.getSpawnLocation(); break; }
            }
        }
        if (safe == null || safe.getWorld() == null || safe.getWorld().equals(doomed)) {
            plugin.getLogger().warning("No safe destination to evacuate players from "
                    + doomed.getName() + "; skipping unload.");
            return;
        }
        for (var player : doomed.getPlayers()) {
            player.teleport(safe);
        }
    }

    public static final class VoidGenerator extends ChunkGenerator {
        @Override public void generateNoise(WorldInfo w, Random r, int cx, int cz, ChunkData d) {}
        @Override public void generateSurface(WorldInfo w, Random r, int cx, int cz, ChunkData d) {}
        @Override public void generateBedrock(WorldInfo w, Random r, int cx, int cz, ChunkData d) {}
        @Override public void generateCaves(WorldInfo w, Random r, int cx, int cz, ChunkData d) {}

        // No-arg variants for older API consumers.
        @Override public boolean shouldGenerateNoise() { return false; }
        @Override public boolean shouldGenerateSurface() { return false; }
        @Override public boolean shouldGenerateBedrock() { return false; }
        @Override public boolean shouldGenerateCaves() { return false; }
        @Override public boolean shouldGenerateDecorations() { return false; }
        @Override public boolean shouldGenerateMobs() { return false; }
        @Override public boolean shouldGenerateStructures() { return false; }

        // Per-chunk variants — Paper consults these on every chunk and has, on
        // some versions, skipped the no-arg forwarders. Without these overrides
        // vanilla terrain leaked into chunks generated far from spawn.
        @Override public boolean shouldGenerateNoise(WorldInfo w, Random r, int cx, int cz) { return false; }
        @Override public boolean shouldGenerateSurface(WorldInfo w, Random r, int cx, int cz) { return false; }
        @Override public boolean shouldGenerateCaves(WorldInfo w, Random r, int cx, int cz) { return false; }
        @Override public boolean shouldGenerateDecorations(WorldInfo w, Random r, int cx, int cz) { return false; }
        @Override public boolean shouldGenerateMobs(WorldInfo w, Random r, int cx, int cz) { return false; }
        @Override public boolean shouldGenerateStructures(WorldInfo w, Random r, int cx, int cz) { return false; }

        @Override
        public Location getFixedSpawnLocation(World world, Random random) {
            return new Location(world, 0, 80, 0);
        }

        @Override
        public List<BlockPopulator> getDefaultPopulators(World world) {
            return List.of();
        }
    }

    public static final class SingleBiomeProvider extends BiomeProvider {
        private final Biome biome;
        public SingleBiomeProvider(Biome biome) { this.biome = biome; }
        @Override public Biome getBiome(WorldInfo w, int x, int y, int z) { return biome; }
        @Override public List<Biome> getBiomes(WorldInfo w) { return List.of(biome); }
    }
}
