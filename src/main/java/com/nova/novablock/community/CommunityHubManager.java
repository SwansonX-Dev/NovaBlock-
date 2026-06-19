package com.nova.novablock.community;

import com.nova.novablock.NovaBlock;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.block.Biome;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Top-level coordinator for community features. Owns {@link CommunityBlock},
 * {@link WeeklyGoal}, and {@link RaidScheduler}; loads/saves state via
 * {@link HubStorage}; provides the single integration surface used by
 * BlockListener / EventManager / NovaBlock.
 */
public class CommunityHubManager {

    private final NovaBlock plugin;
    private final CommunityBlock block;
    private final WeeklyGoal goal;
    private final RaidScheduler raids;
    private final CommunityLeaderboardDisplay leaderboard;
    private final HubStorage storage;
    private final AtomicBoolean dirty = new AtomicBoolean(false);
    private static final long SAVE_DEBOUNCE_TICKS = 100L; // ~5s

    public CommunityHubManager(NovaBlock plugin) {
        this.plugin = plugin;
        this.block = new CommunityBlock(plugin);
        this.goal = new WeeklyGoal(plugin);
        this.raids = new RaidScheduler(plugin);
        this.leaderboard = new CommunityLeaderboardDisplay(plugin, this);
        this.storage = new HubStorage(plugin);
        storage.load(block, goal, raids);
    }

    public CommunityBlock block() { return block; }
    public WeeklyGoal goal() { return goal; }
    public RaidScheduler raids() { return raids; }
    public CommunityLeaderboardDisplay leaderboard() { return leaderboard; }

    /** Place the shared platform + OneBlocks if missing. Called from NovaBlock after worlds load. */
    public void placeIfNeeded() {
        Location spawn = hubSpawnLocation();
        if (spawn == null) {
            plugin.getLogger().warning("Community world can't be placed yet — world not loaded.");
            return;
        }
        // Defer one tick so the world is fully ready when called from onEnable.
        Bukkit.getScheduler().runTask(plugin, () -> {
            buildStarterPlatform(spawn);
            int placed = 0;
            for (Location at : blockLocations()) {
                if (block.placeInitial(at)) placed++;
            }
            plugin.getLogger().info("Community hub ready in world '" + spawn.getWorld().getName()
                    + "' at " + spawn.getBlockX() + "," + spawn.getBlockY() + "," + spawn.getBlockZ()
                    + " (" + blockLocations().size() + " OneBlock location(s), " + placed + " repaired/placed).");
        });
    }

    /**
     * Cheap self-heal used by the existing one-minute community tick. It checks
     * only the configured block and anchor, so it stays effectively free.
     */
    public void repairIfNeeded() {
        if (!isEnabled()) return;
        for (Location at : blockLocations()) {
            if (block.placeInitial(at)) {
                plugin.getLogger().info("Repaired missing community OneBlock at "
                        + at.getWorld().getName() + " "
                        + at.getBlockX() + "," + at.getBlockY() + "," + at.getBlockZ());
            }
        }
    }

    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("community.enabled", true);
    }

    /** True if the broken location matches the community block coordinates. */
    public boolean isCommunityBlock(Location loc) {
        if (!isEnabled() || loc == null) return false;
        for (Location at : blockLocations()) {
            if (sameBlock(at, loc)) return true;
        }
        return false;
    }

    /** True if loc is the bedrock anchor directly below the community block. */
    public boolean isAnchorBlock(Location loc) {
        if (!isEnabled() || loc == null) return false;
        for (Location at : blockLocations()) {
            if (at == null || at.getWorld() == null || loc.getWorld() == null) continue;
            if (!at.getWorld().equals(loc.getWorld())) continue;
            if (at.getBlockX() == loc.getBlockX()
                    && at.getBlockY() - 1 == loc.getBlockY()
                    && at.getBlockZ() == loc.getBlockZ()) return true;
        }
        return false;
    }

    /** True if loc is in the regen column (block ± 1 vertical, same x/z). */
    public boolean isInRegenColumn(Location loc) {
        if (!isEnabled() || loc == null) return false;
        for (Location at : blockLocations()) {
            if (at == null || at.getWorld() == null || loc.getWorld() == null) continue;
            if (!at.getWorld().equals(loc.getWorld())) continue;
            if (at.getBlockX() == loc.getBlockX()
                    && at.getBlockZ() == loc.getBlockZ()
                    && loc.getBlockY() >= at.getBlockY() - 1
                    && loc.getBlockY() <= at.getBlockY() + 1) return true;
        }
        return false;
    }

    /** Routes a break through CommunityBlock, then counts it toward the weekly goal. */
    public void handleBreak(Player player, BlockBreakEvent event) {
        if (!isEnabled()) return;
        Location at = event.getBlock().getLocation();
        if (!isCommunityBlock(at)) return;
        block.onBreak(player, event.getBlock().getType(), event, at);
        goal.recordBreak(player, 1L);
        plugin.quests().onCommunityBreak(player);
        block.tickPayoutIfDue();
        markDirty();
    }

    public void markDirty() {
        if (!dirty.compareAndSet(false, true)) return;
        Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, this::flush, SAVE_DEBOUNCE_TICKS);
    }

    public void flushNow() {
        dirty.set(false);
        storage.save(block, goal, raids);
    }

    private void flush() {
        if (!dirty.compareAndSet(true, false)) return;
        storage.save(block, goal, raids);
    }

    public Location primaryBlockLocation() {
        List<Location> blocks = blockLocations();
        return blocks.isEmpty() ? null : blocks.get(0);
    }

    public Location hubSpawnLocation() {
        String worldName = communityWorldName();
        World world = ensureCommunityWorld(worldName);
        if (world == null) return null;
        double x = plugin.getConfig().getDouble("community.world.spawn.x", 0.0);
        double y = plugin.getConfig().getDouble("community.world.spawn.y", 80.0);
        double z = plugin.getConfig().getDouble("community.world.spawn.z", 0.0);
        float yaw = (float) plugin.getConfig().getDouble("community.world.spawn.yaw", 0.0);
        float pitch = (float) plugin.getConfig().getDouble("community.world.spawn.pitch", 0.0);
        return new Location(world, x, y, z, yaw, pitch);
    }

    /**
     * Set the community world spawn point (position + facing) to {@code loc}.
     * Deliberately touches ONLY {@code community.world.spawn} — the starter
     * platform and the OneBlock positions are left exactly as they are.
     */
    public void setHubSpawn(Location loc) {
        plugin.getConfig().set("community.world.spawn.x", loc.getX());
        plugin.getConfig().set("community.world.spawn.y", loc.getY());
        plugin.getConfig().set("community.world.spawn.z", loc.getZ());
        plugin.getConfig().set("community.world.spawn.yaw", (double) loc.getYaw());
        plugin.getConfig().set("community.world.spawn.pitch", (double) loc.getPitch());
        plugin.saveConfig();
    }

    /**
     * Editable view of the configured community OneBlock positions, in the
     * community world. Reads the raw {@code community.oneblocks.positions} list
     * (no spawn+1 fallback) so admin edits operate on exactly what's persisted.
     */
    public List<Location> configuredOneblocks() {
        World world = ensureCommunityWorld(communityWorldName());
        List<Location> out = new ArrayList<>();
        if (world == null) return out;
        for (Map<?, ?> row : plugin.getConfig().getMapList("community.oneblocks.positions")) {
            out.add(new Location(world,
                    (int) number(row.get("x"), 0),
                    (int) number(row.get("y"), 0),
                    (int) number(row.get("z"), 0)));
        }
        return out;
    }

    /**
     * Persist a new list of OneBlock positions to config (block coords). Any
     * existing per-position {@code owner} (matched by coordinates) is carried over
     * so admin add/delete/move don't wipe reward-block ownership.
     */
    public void saveOneblocks(List<Location> locs) {
        Map<String, String> owners = new java.util.HashMap<>();
        for (Map<?, ?> row : plugin.getConfig().getMapList("community.oneblocks.positions")) {
            Object o = row.get("owner");
            if (o instanceof String s && !s.isBlank()) {
                owners.put(blockKey((int) number(row.get("x"), 0),
                        (int) number(row.get("y"), 0), (int) number(row.get("z"), 0)), s);
            }
        }
        List<Map<String, Object>> positions = new ArrayList<>();
        for (Location l : locs) {
            Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("x", l.getBlockX());
            m.put("y", l.getBlockY());
            m.put("z", l.getBlockZ());
            String owner = owners.get(blockKey(l.getBlockX(), l.getBlockY(), l.getBlockZ()));
            if (owner != null) m.put("owner", owner);
            positions.add(m);
        }
        plugin.getConfig().set("community.oneblocks.positions", positions);
        plugin.saveConfig();
    }

    /** UUID of the player who placed the community OneBlock at {@code at}, or null (unowned / admin / default). */
    public java.util.UUID ownerAt(Location at) {
        if (at == null) return null;
        for (Map<?, ?> row : plugin.getConfig().getMapList("community.oneblocks.positions")) {
            if (sameRow(row, at)) {
                Object o = row.get("owner");
                if (o instanceof String s && !s.isBlank()) {
                    try { return java.util.UUID.fromString(s); } catch (IllegalArgumentException ignored) {}
                }
                return null;
            }
        }
        return null;
    }

    /** Stamp (or clear, if null) the owner on the position at {@code at}. */
    public void setOwnerAt(Location at, java.util.UUID owner) {
        List<Map<String, Object>> positions = rawPositions();
        boolean changed = false;
        for (Map<String, Object> row : positions) {
            if (sameRow(row, at)) {
                if (owner != null) row.put("owner", owner.toString());
                else row.remove("owner");
                changed = true;
                break;
            }
        }
        if (changed) {
            plugin.getConfig().set("community.oneblocks.positions", positions);
            plugin.saveConfig();
        }
    }

    /** Remove the shared community OneBlock at {@code at}: drop it from config and tear down the block + anchor. */
    public boolean removeOneblockAt(Location at) {
        if (at == null || at.getWorld() == null) return false;
        List<Location> locs = configuredOneblocks();
        if (locs.isEmpty()) locs.addAll(blockLocations());
        if (!locs.removeIf(l -> sameBlock(l, at))) return false;
        saveOneblocks(locs);
        at.getBlock().setType(Material.AIR, false);
        Location anchor = at.clone().add(0, -1, 0);
        if (anchor.getBlock().getType() == Material.BEDROCK) anchor.getBlock().setType(Material.AIR, false);
        return true;
    }

    /** Mutable copy of the raw config position rows (x/y/z[/owner]), preserving the owner field. */
    private List<Map<String, Object>> rawPositions() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<?, ?> row : plugin.getConfig().getMapList("community.oneblocks.positions")) {
            Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("x", (int) number(row.get("x"), 0));
            m.put("y", (int) number(row.get("y"), 0));
            m.put("z", (int) number(row.get("z"), 0));
            Object owner = row.get("owner");
            if (owner instanceof String s && !s.isBlank()) m.put("owner", s);
            out.add(m);
        }
        return out;
    }

    private static boolean sameRow(Map<?, ?> row, Location at) {
        return at != null
                && (int) number(row.get("x"), Integer.MIN_VALUE) == at.getBlockX()
                && (int) number(row.get("y"), Integer.MIN_VALUE) == at.getBlockY()
                && (int) number(row.get("z"), Integer.MIN_VALUE) == at.getBlockZ();
    }

    private static String blockKey(int x, int y, int z) {
        return x + ":" + y + ":" + z;
    }

    /** Place the OneBlock (and its bedrock anchor) at {@code at} if missing. */
    public boolean placeOneblock(Location at) {
        return block.placeInitial(at);
    }

    /** How many shared community OneBlocks currently exist (configured + implicit default). */
    public int oneblockCount() {
        return blockLocations().size();
    }

    /** Hard cap on the number of community OneBlocks, to bound player-grown hubs. */
    public int maxOneblocks() {
        return Math.max(1, plugin.getConfig().getInt("community.oneblocks.max", 200));
    }

    /**
     * Register a brand-new <em>shared</em> community OneBlock at {@code at} and lay
     * it down (bedrock anchor + breakable block on top). Used by the Community
     * OneBlock reward item so players can grow the communal hub. The result is a
     * real community block — anyone may mine it, it shares the community phase and
     * feeds the shared payout pool.
     *
     * @return true if registered and placed; false if disabled, not the community
     *         world, a block already exists there, or the cap is reached.
     */
    public boolean addOneblockAt(Location at) {
        return addOneblockAt(at, null);
    }

    /** As {@link #addOneblockAt(Location)}, recording {@code owner} as the placer (null = unowned). */
    public boolean addOneblockAt(Location at, java.util.UUID owner) {
        if (!isEnabled() || at == null || at.getWorld() == null) return false;
        if (!at.getWorld().getName().equals(communityWorldName())) return false;
        if (isCommunityBlock(at)) return false;
        if (oneblockCount() >= maxOneblocks()) return false;
        // Seed with the current effective set so the implicit spawn+1 default block
        // (used while no positions are configured) is preserved once we persist.
        List<Location> locs = configuredOneblocks();
        if (locs.isEmpty()) locs.addAll(blockLocations());
        for (Location l : locs) {
            if (sameBlock(l, at)) return false;
        }
        locs.add(at);
        saveOneblocks(locs);          // persists positions, preserving other owners
        if (owner != null) setOwnerAt(at, owner); // stamp the placer on the new block
        block.placeInitial(at);
        return true;
    }

    /** True if two locations refer to the same block (exposed for admin tooling). */
    public static boolean sameBlockLoc(Location a, Location b) {
        return sameBlock(a, b);
    }

    public String communityWorldName() {
        String configured = plugin.getConfig().getString("community.world.name", "community_oneblock");
        if (configured == null || configured.isBlank()) {
            plugin.getLogger().warning("community.world.name is blank; using community_oneblock.");
            return "community_oneblock";
        }
        return configured;
    }

    public World ensureCommunityWorld(String worldName) {
        World existing = Bukkit.getWorld(worldName);
        if (existing != null) {
            if (!(existing.getGenerator() instanceof com.nova.novablock.island.IslandWorldManager.VoidGenerator)) {
                plugin.getLogger().warning("Community world '" + worldName + "' is already loaded with a "
                        + "non-void generator (" + (existing.getGenerator() == null ? "vanilla" : existing.getGenerator().getClass().getName())
                        + "). New chunks will not be void. Unload it via Multiverse / restart with the world deleted "
                        + "so NovaBlock can recreate it with its void generator.");
            }
            return existing;
        }
        plugin.getLogger().info("Creating community OneBlock world '" + worldName + "'.");
        WorldCreator creator = new WorldCreator(worldName)
                .generator(new com.nova.novablock.island.IslandWorldManager.VoidGenerator())
                .biomeProvider(new com.nova.novablock.island.IslandWorldManager.SingleBiomeProvider(Biome.PLAINS))
                .generateStructures(false);
        World created = creator.createWorld();
        if (created == null) {
            plugin.getLogger().warning("Failed to create community OneBlock world '" + worldName + "'.");
            return null;
        }
        plugin.worlds().configureVoidWorld(created);
        return created;
    }

    public List<Location> blockLocations() {
        Location spawn = hubSpawnLocation();
        if (spawn == null || spawn.getWorld() == null) {
            Location legacy = plugin.spawn().communityBlockLocation();
            return legacy == null ? List.of() : List.of(legacy);
        }

        List<Map<?, ?>> raw = plugin.getConfig().getMapList("community.oneblocks.positions");
        if (raw.isEmpty()) {
            Location legacy = plugin.spawn().communityBlockLocation();
            return legacy == null ? List.of(spawn.clone().add(0, 1, 0)) : List.of(legacy);
        }

        List<Location> out = new ArrayList<>();
        for (Map<?, ?> row : raw) {
            double x = number(row.get("x"), spawn.getX());
            double y = number(row.get("y"), spawn.getY() + 1);
            double z = number(row.get("z"), spawn.getZ());
            out.add(new Location(spawn.getWorld(), x, y, z));
        }
        return out;
    }

    private void buildStarterPlatform(Location spawn) {
        if (spawn.getWorld() == null) return;
        int size = Math.max(1, plugin.getConfig().getInt("community.world.platform-size", 10));
        int start = -size / 2;
        int end = start + size - 1;
        int y = spawn.getBlockY();
        for (int dx = start; dx <= end; dx++) {
            for (int dz = start; dz <= end; dz++) {
                spawn.getWorld().getBlockAt(spawn.getBlockX() + dx, y, spawn.getBlockZ() + dz)
                        .setType(Material.BEDROCK, false);
            }
        }
    }

    private static boolean sameBlock(Location a, Location b) {
        return a != null && b != null
                && a.getWorld() != null && b.getWorld() != null
                && a.getWorld().equals(b.getWorld())
                && a.getBlockX() == b.getBlockX()
                && a.getBlockY() == b.getBlockY()
                && a.getBlockZ() == b.getBlockZ();
    }

    private static double number(Object raw, double fallback) {
        if (raw instanceof Number n) return n.doubleValue();
        if (raw instanceof String s) {
            try { return Double.parseDouble(s); }
            catch (NumberFormatException ignored) {}
        }
        return fallback;
    }

    public void shutdown() {
        leaderboard.shutdown();
        raids.shutdown();
        // Force a final sync save with current state.
        dirty.set(false);
        storage.save(block, goal, raids);
    }
}
