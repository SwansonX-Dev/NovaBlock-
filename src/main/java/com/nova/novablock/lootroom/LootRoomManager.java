package com.nova.novablock.lootroom;

import com.nova.novablock.NovaBlock;
import com.nova.novablock.island.Island;
import com.nova.novablock.island.IslandWorldManager;
import com.nova.novablock.lootroom.rooms.ArenaRoom;
import com.nova.novablock.lootroom.rooms.ParkourRoom;
import com.nova.novablock.lootroom.rooms.PuzzleRoom;
import com.nova.novablock.util.ItemBuilder;
import com.nova.novablock.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.block.Biome;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.scheduler.BukkitTask;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class LootRoomManager implements Listener {

    private final NovaBlock plugin;
    private final Map<String, LootRoom> registry = new HashMap<>();
    private final Map<UUID, LootRoomRun> active = new HashMap<>();
    /** Players who have a rift offered. */
    private final Map<UUID, RiftOffer> offers = new HashMap<>();
    /** Players who died mid-run; we'll redirect their respawn to the saved return location. */
    private final Map<UUID, Location> pendingReturn = new HashMap<>();
    /** Runs waiting for respawn before their instance world can be deleted. */
    private final Map<UUID, LootRoomRun> pendingCleanup = new HashMap<>();
    private BukkitTask tickTask;

    public LootRoomManager(NovaBlock plugin) {
        this.plugin = plugin;
        // Run at 5-tick cadence (4 Hz) so step-on detection feels instant.
        tickTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickAll, 20L, 5L);
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void registerDefaultRooms() {
        register(new ParkourRoom());
        register(new ArenaRoom(plugin));
        register(new PuzzleRoom());
    }

    /**
     * Sweep for {@code novablock_loot_*} world folders left over from a crash mid-run.
     * Called once at plugin enable. Folders are recognised by name prefix and removed
     * outright since the corresponding world is guaranteed not to be loaded yet.
     */
    public void cleanupOrphanWorlds() {
        java.io.File container = Bukkit.getWorldContainer();
        java.io.File[] children = container.listFiles((dir, name) -> name.startsWith("novablock_loot_"));
        if (children == null || children.length == 0) return;
        int removed = 0;
        for (java.io.File child : children) {
            if (!child.isDirectory()) continue;
            try {
                deleteDirectory(child.toPath());
                removed++;
            } catch (IOException ex) {
                plugin.getLogger().warning("Could not delete orphan loot world " + child.getName() + ": " + ex.getMessage());
            }
        }
        if (removed > 0) plugin.getLogger().info("Removed " + removed + " orphan loot-room worlds from prior session.");
    }

    public void register(LootRoom room) { registry.put(room.id(), room); }
    public LootRoom byId(String id) { return registry.get(id); }
    public int roomCount() { return registry.size(); }

    public void offerEntry(Player player, Island island, String roomId) {
        LootRoom room = registry.get(roomId);
        if (room == null) return;
        // Place the rift two blocks NORTH of the centre block, on the same Y as the centre.
        Location center = island.centerBlock();
        World w = center.getWorld();
        if (w == null) return;
        int bx = center.getBlockX() + 2;
        int by = center.getBlockY();
        int bz = center.getBlockZ();
        w.getBlockAt(bx, by, bz).setType(Material.END_PORTAL_FRAME);
        // FX above the portal
        w.spawnParticle(org.bukkit.Particle.PORTAL,
                new Location(w, bx + 0.5, by + 1.0, bz + 0.5), 60, 0.3, 1, 0.3, 0.5);
        // Cancel any older offer this player had (so we don't leave orphan portals).
        RiftOffer prior = offers.remove(player.getUniqueId());
        if (prior != null) prior.clearMarker();
        offers.put(player.getUniqueId(),
                new RiftOffer(roomId, island.data().getId(), w.getName(), bx, by, bz, System.currentTimeMillis() + 30_000));
        Msg.send(player, "<light_purple>★ A <yellow>" + room.displayName()
                + " <light_purple>rift opened next to your block! Step on it within 30s.");
        player.playSound(player.getLocation(), Sound.BLOCK_PORTAL_AMBIENT, 0.6f, 1.2f);
    }

    private void tickAll() {
        long now = System.currentTimeMillis();
        // Clean up expired offers + remove their portal frames
        offers.entrySet().removeIf(e -> {
            if (e.getValue().expiresAt < now) {
                e.getValue().clearMarker();
                Player p = Bukkit.getPlayer(e.getKey());
                if (p != null) Msg.actionBar(p, "<gray>Rift fizzled away.");
                return true;
            }
            return false;
        });
        // Trigger entry when the player is on or one block above the marker
        for (var entry : new HashMap<>(offers).entrySet()) {
            Player p = Bukkit.getPlayer(entry.getKey());
            if (p == null) continue;
            RiftOffer offer = entry.getValue();
            if (!p.getWorld().getName().equals(offer.worldName)) continue;
            int px = p.getLocation().getBlockX();
            int py = p.getLocation().getBlockY();
            int pz = p.getLocation().getBlockZ();
            // Player stands on the portal (py == bx+1) OR is in it (py == by).
            boolean onPortal = px == offer.bx && pz == offer.bz
                    && (py == offer.by || py == offer.by + 1);
            if (onPortal) enter(p, offer);
        }
        // Tick runs
        for (var it = active.entrySet().iterator(); it.hasNext(); ) {
            LootRoomRun run = it.next().getValue();
            try { run.room().tick(run); } catch (Throwable t) {
                plugin.getLogger().warning("Loot room tick error: " + t);
            }
            if (run.finished()) {
                complete(run);
                cleanupRunWorld(run);
                it.remove();
            }
        }
    }

    private void enter(Player p, RiftOffer offer) {
        offers.remove(p.getUniqueId());
        offer.clearMarker();
        LootRoom room = registry.get(offer.roomId);
        Island island = plugin.islands().get(offer.islandId);
        if (room == null || island == null) return;
        if (active.containsKey(p.getUniqueId())) finishEarly(p);
        World instance = createInstanceWorld(p);
        if (instance == null) {
            Msg.send(p, "<red>Could not create a private rift world. Try again.");
            return;
        }
        Location anchor = new Location(instance, 0, 100, 0);
        Location entry = room.build(anchor);
        Location returnLoc = p.getLocation().clone();
        LootRoomRun run = new LootRoomRun(room, p.getUniqueId(), island, anchor,
                returnLoc, instance.getName(), Bukkit.getCurrentTick());
        active.put(p.getUniqueId(), run);
        p.teleport(entry);
        room.onStart(run, p);
        Msg.title(p, "<light_purple>" + room.displayName(), "<gray>Complete to claim loot");
        p.playSound(p.getLocation(), Sound.ITEM_CHORUS_FRUIT_TELEPORT, 1f, 1f);
    }

    public void finishEarly(Player p) {
        LootRoomRun run = active.remove(p.getUniqueId());
        if (run == null) return;
        cleanupRoomMobs(run);
        p.teleport(run.returnLocation());
        cleanupRunWorld(run);
        Msg.actionBar(p, "<gray>You forfeited the rift.");
    }

    /**
     * Kill non-player living entities near the run's anchor so arena mobs from
     * a failed/aborted run don't keep ticking forever. Safe because each run
     * gets its own unique anchor X.
     */
    private void cleanupRoomMobs(LootRoomRun run) {
        World w = run.anchor().getWorld();
        if (w == null) return;
        for (Entity e : w.getNearbyEntities(run.anchor(), 24, 16, 24)) {
            if (e instanceof Player) continue;
            if (e instanceof LivingEntity le) le.remove();
        }
    }

    // ---- death / respawn / quit handling ----

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        UUID id = event.getEntity().getUniqueId();
        LootRoomRun run = active.remove(id);
        if (run == null) return;
        pendingReturn.put(id, run.returnLocation());
        pendingCleanup.put(id, run);
        cleanupRoomMobs(run);
        Msg.title(event.getEntity(), "<red>Rift Failed", "<gray>You fell in battle — no rewards.");
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Location ret = pendingReturn.remove(event.getPlayer().getUniqueId());
        if (ret != null && ret.getWorld() != null) {
            event.setRespawnLocation(ret);
        }
        LootRoomRun run = pendingCleanup.remove(event.getPlayer().getUniqueId());
        if (run != null) {
            Bukkit.getScheduler().runTask(plugin, () -> cleanupRunWorld(run));
        }
    }

    /** If a runner logs out mid-run, abort it cleanly so we don't leak ticking arenas. */
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        LootRoomRun run = active.remove(event.getPlayer().getUniqueId());
        if (run != null) {
            cleanupRoomMobs(run);
            cleanupRunWorld(run);
        }
        LootRoomRun pending = pendingCleanup.remove(event.getPlayer().getUniqueId());
        if (pending != null) cleanupRunWorld(pending);
        pendingReturn.remove(event.getPlayer().getUniqueId());
    }

    // ---- room protection: nothing inside an active arena should be break/place/explodable ----

    /** True if the location is inside the working bounds of any currently-active loot room. */
    private boolean insideAnyRoom(Location loc) {
        return runAt(loc) != null;
    }

    private LootRoomRun runAt(Location loc) {
        if (loc == null) return null;
        for (LootRoomRun run : active.values()) {
            Location a = run.anchor();
            if (a == null || a.getWorld() == null) continue;
            if (!a.getWorld().equals(loc.getWorld())) continue;
            // Each room fits comfortably inside an 18-block half-width box around its anchor;
            // the largest is the Arena at radius 11, with a small buffer.
            if (Math.abs(loc.getBlockX() - a.getBlockX()) > 18) continue;
            if (Math.abs(loc.getBlockZ() - a.getBlockZ()) > 18) continue;
            if (loc.getBlockY() < a.getBlockY() - 2 || loc.getBlockY() > a.getBlockY() + 10) continue;
            return run;
        }
        return null;
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreakInRoom(BlockBreakEvent event) {
        LootRoomRun run = runAt(event.getBlock().getLocation());
        if (run == null) return;
        if ("puzzle".equals(run.room().id()) && event.getBlock().getType() == Material.AMETHYST_BLOCK) return;
        event.setCancelled(true);
        Msg.actionBar(event.getPlayer(), "<red>You can't break blocks inside a rift.");
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlaceInRoom(BlockPlaceEvent event) {
        if (insideAnyRoom(event.getBlock().getLocation())) {
            event.setCancelled(true);
            Msg.actionBar(event.getPlayer(), "<red>You can't place blocks inside a rift.");
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(b -> insideAnyRoom(b.getLocation()));
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(b -> insideAnyRoom(b.getLocation()));
    }

    private void complete(LootRoomRun run) {
        Player p = run.player();
        if (p == null) return;
        Island island = plugin.islands().get(run.islandId());
        int reward = run.room().rewardCoins(island);
        // JACKPOT (Luck 10): +25% coin from chest-style rewards. Loot-room payouts qualify.
        if (com.nova.novablock.progression.Perk.hasPerk(plugin.progression().get(p),
                com.nova.novablock.progression.Perk.JACKPOT)) {
            reward = (int) Math.round(reward * 1.25);
        }
        if (plugin.seasons().active() == com.nova.novablock.season.SeasonManager.ServerEvent.DOUBLE_COINS) reward *= 2;
        plugin.economy().award(island, reward);
        // Loot-room completion is a magical event — reward MAGIC XP so the tree actually levels.
        plugin.progression().addXp(p, com.nova.novablock.progression.SkillType.MAGIC, 50L);
        plugin.progression().addXp(p, com.nova.novablock.progression.SkillType.LUCK, 20L);
        p.teleport(run.returnLocation());

        // Actual loot — each room defines a thematic drop list. Anything that
        // doesn't fit in the player's inventory falls at their feet so they
        // never silently lose a reward.
        var loot = run.room().rewardItems(island);
        int itemCount = 0;
        for (var item : loot) {
            if (item == null || item.getAmount() <= 0) continue;
            itemCount += item.getAmount();
            var overflow = p.getInventory().addItem(item);
            for (var leftover : overflow.values()) {
                p.getWorld().dropItemNaturally(p.getLocation(), leftover);
            }
        }

        Msg.title(p, "<gold>Rift Cleared!", "<yellow>+" + reward + " coins · <aqua>" + itemCount + " items");
        Msg.actionBar(p, "<gray>Check your inventory for loot.");
        p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        plugin.quests().onLootRoomCompleted(p);
    }

    private World createInstanceWorld(Player player) {
        String name = "novablock_loot_" + player.getUniqueId().toString().replace("-", "").substring(0, 12)
                + "_" + Long.toUnsignedString(System.currentTimeMillis(), 36);
        WorldCreator creator = new WorldCreator(name)
                .generator(new IslandWorldManager.VoidGenerator())
                .biomeProvider(new IslandWorldManager.SingleBiomeProvider(Biome.PLAINS))
                .generateStructures(false);
        World world = creator.createWorld();
        if (world == null) return null;
        world.setSpawnFlags(false, false);
        world.setDifficulty(Difficulty.NORMAL);
        world.setGameRule(GameRule.DO_MOB_SPAWNING, false);
        world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        world.setGameRule(GameRule.KEEP_INVENTORY, true);
        world.setGameRule(GameRule.DO_FIRE_TICK, false);
        world.setGameRule(GameRule.MOB_GRIEFING, false);
        world.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
        world.setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, false);
        return world;
    }

    private void cleanupRunWorld(LootRoomRun run) {
        cleanupRoomMobs(run);
        World world = Bukkit.getWorld(run.worldName());
        if (world == null) return;
        for (Player player : world.getPlayers()) {
            Location ret = run.returnLocation();
            if (ret.getWorld() != null) player.teleport(ret);
        }
        Path folder = world.getWorldFolder().toPath();
        if (!Bukkit.unloadWorld(world, false)) {
            plugin.getLogger().warning("Could not unload loot room world " + run.worldName());
            return;
        }
        try {
            deleteDirectory(folder);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not delete loot room world " + run.worldName() + ": " + e.getMessage());
        }
    }

    private void deleteDirectory(Path folder) throws IOException {
        if (!Files.exists(folder)) return;
        try (var paths = Files.walk(folder)) {
            paths.sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
        } catch (RuntimeException e) {
            if (e.getCause() instanceof IOException io) throw io;
            throw e;
        }
    }

    public LootRoomRun getRun(Player p) { return active.get(p.getUniqueId()); }

    public void shutdown() {
        if (tickTask != null) { tickTask.cancel(); tickTask = null; }
        // Send any active runs back to their return location so players aren't stranded.
        for (LootRoomRun run : active.values()) {
            Player p = run.player();
            if (p != null) p.teleport(run.returnLocation());
            cleanupRunWorld(run);
        }
        active.clear();
        for (LootRoomRun run : pendingCleanup.values()) cleanupRunWorld(run);
        pendingCleanup.clear();
        pendingReturn.clear();
        // Remove pending rift markers from the world.
        for (RiftOffer offer : offers.values()) offer.clearMarker();
        offers.clear();
    }

    /** Tracks both the run identity and the exact block we placed in the world. */
    private static final class RiftOffer {
        final String roomId;
        final UUID islandId;
        final String worldName;
        final int bx, by, bz;
        final long expiresAt;

        RiftOffer(String roomId, UUID islandId, String worldName, int bx, int by, int bz, long expiresAt) {
            this.roomId = roomId;
            this.islandId = islandId;
            this.worldName = worldName;
            this.bx = bx; this.by = by; this.bz = bz;
            this.expiresAt = expiresAt;
        }

        void clearMarker() {
            World w = Bukkit.getWorld(worldName);
            if (w == null) return;
            var block = w.getBlockAt(bx, by, bz);
            if (block.getType() == Material.END_PORTAL_FRAME) {
                block.setType(Material.AIR);
            }
        }
    }
}
