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
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public class LootRoomManager implements Listener {

    /** How long a rift's portal stays open for others to step in after the opener enters. */
    private static final long JOIN_WINDOW_MS = 60_000L;

    private final NovaBlock plugin;
    private final Map<String, LootRoom> registry = new HashMap<>();
    /** Active runs keyed by their unique instance world name (one entry per rift). */
    private final Map<String, LootRoomRun> runsByWorld = new HashMap<>();
    /** Every participant (opener + joiners) → their run, for per-player lookups. */
    private final Map<UUID, LootRoomRun> runByPlayer = new HashMap<>();
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
        register(new ParkourRoom(RoomTheme.OVERWORLD));
        register(new ArenaRoom(plugin, RoomTheme.OVERWORLD));
        register(new PuzzleRoom(RoomTheme.OVERWORLD));
        register(new ParkourRoom(RoomTheme.NETHER));
        register(new ArenaRoom(plugin, RoomTheme.NETHER));
        register(new PuzzleRoom(RoomTheme.NETHER));
        register(new ParkourRoom(RoomTheme.END));
        register(new ArenaRoom(plugin, RoomTheme.END));
        register(new PuzzleRoom(RoomTheme.END));
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
        // Place the rift two blocks NORTH of the centre block, one block above the centre's Y.
        Location center = island.centerBlock();
        World w = center.getWorld();
        if (w == null) return;
        int bx = center.getBlockX() + 2;
        int by = center.getBlockY() + 1;
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
        // Clean up expired offers + remove their portal frames. An opened offer
        // expiring just closes its join window — the run it spawned ticks on.
        offers.entrySet().removeIf(e -> {
            if (e.getValue().expiresAt < now) {
                e.getValue().clearMarker();
                if (!e.getValue().opened()) {
                    Player p = Bukkit.getPlayer(e.getKey());
                    if (p != null) Msg.actionBar(p, "<gray>Rift fizzled away.");
                }
                return true;
            }
            return false;
        });
        // Offer handling: an unopened offer waits for its OWNER to step on to open
        // the rift; an opened offer's portal lets ANYONE step on to join the run.
        for (var entry : new HashMap<>(offers).entrySet()) {
            RiftOffer offer = entry.getValue();
            if (!offer.opened()) {
                Player owner = Bukkit.getPlayer(entry.getKey());
                if (owner != null && onPortal(owner, offer)) enter(owner, offer);
                continue;
            }
            LootRoomRun run = runsByWorld.get(offer.openedWorld);
            if (run == null) { offer.clearMarker(); offers.remove(entry.getKey()); continue; }
            World w = Bukkit.getWorld(offer.worldName);
            if (w == null) continue;
            for (Player p : w.getPlayers()) {
                if (!run.hasParticipant(p.getUniqueId()) && onPortal(p, offer)) joinRun(p, run);
            }
        }
        // Tick runs
        for (var it = runsByWorld.entrySet().iterator(); it.hasNext(); ) {
            LootRoomRun run = it.next().getValue();
            World anchorWorld = run.anchor().getWorld();
            // Drop participants who went offline or left the rift world (teleport,
            // plugin-driven warp). Room logic only ever touches players still inside,
            // so this keeps ArenaRoom from throwing cross-world on getNearbyEntities.
            if (anchorWorld != null) pruneParticipants(run, anchorWorld);
            // No one left inside (or the world is gone) → abort cleanly, no reward.
            if (anchorWorld == null || run.participants().isEmpty()) {
                it.remove();
                cleanupRunWorld(run);
                forgetRun(run);
                continue;
            }
            try { run.room().tick(run); } catch (Throwable t) {
                plugin.getLogger().warning("Loot room tick error: " + t);
            }
            if (run.finished()) {
                complete(run);
                it.remove();
                cleanupRunWorld(run);
                forgetRun(run);
            }
        }
    }

    /** True if {@code p} is standing on (or one block inside) the offer's portal frame. */
    private boolean onPortal(Player p, RiftOffer offer) {
        if (!p.getWorld().getName().equals(offer.worldName)) return false;
        int px = p.getLocation().getBlockX();
        int py = p.getLocation().getBlockY();
        int pz = p.getLocation().getBlockZ();
        return px == offer.bx && pz == offer.bz && (py == offer.by || py == offer.by + 1);
    }

    /** Remove participants who logged off or are no longer inside the rift world. */
    private void pruneParticipants(LootRoomRun run, World anchorWorld) {
        for (Iterator<UUID> it = run.participants().iterator(); it.hasNext(); ) {
            UUID id = it.next();
            Player p = Bukkit.getPlayer(id);
            if (p == null || !p.isOnline() || !p.getWorld().equals(anchorWorld)) {
                it.remove();
                runByPlayer.remove(id, run);
            }
        }
    }

    /** Bring a player into an already-running rift via its open portal. */
    private void joinRun(Player p, LootRoomRun run) {
        LootRoomRun existing = runByPlayer.get(p.getUniqueId());
        if (existing == run) return;
        if (existing != null) finishEarly(p); // can't be in two rifts at once
        run.addParticipant(p.getUniqueId());
        runByPlayer.put(p.getUniqueId(), run);
        p.teleport(run.entryLocation());
        Msg.title(p, "<light_purple>" + run.room().displayName(), "<gray>You joined the rift");
        p.playSound(p.getLocation(), Sound.ITEM_CHORUS_FRUIT_TELEPORT, 1f, 1f);
        for (Player other : run.players()) {
            if (!other.equals(p)) Msg.actionBar(other, "<light_purple>" + p.getName() + " joined the rift!");
        }
    }

    /** Forget a finished/aborted run's bookkeeping: participant lookups + its open portal. */
    private void forgetRun(LootRoomRun run) {
        for (UUID id : run.participants()) runByPlayer.remove(id, run);
        closeOfferFor(run);
    }

    /** Remove and clear the join portal that opened this run, if it's still around. */
    private void closeOfferFor(LootRoomRun run) {
        offers.entrySet().removeIf(e -> {
            if (run.worldName().equals(e.getValue().openedWorld)) {
                e.getValue().clearMarker();
                return true;
            }
            return false;
        });
    }

    private void enter(Player p, RiftOffer offer) {
        LootRoom room = registry.get(offer.roomId);
        Island island = plugin.islands().get(offer.islandId);
        if (room == null || island == null) { offers.remove(p.getUniqueId()); offer.clearMarker(); return; }
        if (runByPlayer.containsKey(p.getUniqueId())) finishEarly(p);
        RoomTheme theme = themeForRoomId(room.id());
        World instance = createInstanceWorld(p, theme);
        if (instance == null) {
            Msg.send(p, "<red>Could not create a private rift world. Try again.");
            offers.remove(p.getUniqueId());
            offer.clearMarker();
            return;
        }
        Location anchor = new Location(instance, 0, 100, 0);
        Location entry = room.build(anchor);
        // Always send players back to the island's bedrock spawn next to the OneBlock.
        // Capturing p.getLocation() risked stranding them in mid-air where the rift
        // portal used to sit (the portal block is outside the bedrock skirt).
        Location returnLoc = island.data().spawnLocation();
        LootRoomRun run = new LootRoomRun(room, p.getUniqueId(), island, anchor, entry,
                returnLoc, instance.getName(), Bukkit.getCurrentTick());
        runsByWorld.put(instance.getName(), run);
        runByPlayer.put(p.getUniqueId(), run);
        // Keep the portal standing so others can step in — it now feeds this run.
        // Re-open the join window from now so latecomers aren't cut off by the
        // original 30s offer timer.
        offer.openedWorld = instance.getName();
        offer.expiresAt = System.currentTimeMillis() + JOIN_WINDOW_MS;
        p.teleport(entry);
        room.onStart(run, p);
        Msg.title(p, "<light_purple>" + room.displayName(), "<gray>Complete to claim loot");
        Msg.send(p, "<light_purple>Others can join — have them step on the rift portal within "
                + (JOIN_WINDOW_MS / 1000) + "s.");
        p.playSound(p.getLocation(), Sound.ITEM_CHORUS_FRUIT_TELEPORT, 1f, 1f);
    }

    /** Pull a single player out of their run. Ends the whole run only if they were the last one in. */
    public void finishEarly(Player p) {
        LootRoomRun run = runByPlayer.remove(p.getUniqueId());
        if (run == null) return;
        run.removeParticipant(p.getUniqueId());
        p.teleport(run.returnLocation());
        Msg.actionBar(p, "<gray>You forfeited the rift.");
        if (run.participants().isEmpty()) {
            runsByWorld.remove(run.worldName());
            cleanupRunWorld(run);
            closeOfferFor(run);
        }
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
        LootRoomRun run = runByPlayer.remove(id);
        if (run == null) return;
        run.removeParticipant(id);
        pendingReturn.put(id, run.returnLocation());
        // Only tear the rift down once the LAST participant has fallen — otherwise
        // a co-op partner's death would purge the world out from under the survivors.
        if (run.participants().isEmpty()) {
            runsByWorld.remove(run.worldName());
            cleanupRoomMobs(run);
            pendingCleanup.put(id, run);
            closeOfferFor(run);
        }
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

    /** If a runner logs out mid-run, drop them; abort the run only if they were the last one in. */
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        LootRoomRun run = runByPlayer.remove(id);
        if (run != null) {
            run.removeParticipant(id);
            if (run.participants().isEmpty()) {
                runsByWorld.remove(run.worldName());
                cleanupRunWorld(run);
                closeOfferFor(run);
            }
        }
        LootRoomRun pending = pendingCleanup.remove(id);
        if (pending != null) cleanupRunWorld(pending);
        pendingReturn.remove(id);
    }

    // ---- room protection: nothing inside an active arena should be break/place/explodable ----

    /** True if the location is inside the working bounds of any currently-active loot room. */
    private boolean insideAnyRoom(Location loc) {
        return runAt(loc) != null;
    }

    private LootRoomRun runAt(Location loc) {
        if (loc == null) return null;
        for (LootRoomRun run : runsByWorld.values()) {
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
        // Crystal Cache rooms ID as "puzzle_<theme>"; the bare "puzzle" check
        // never matched so amethyst targets were uncuttable.
        if (run.room().id().startsWith("puzzle_") && event.getBlock().getType() == Material.AMETHYST_BLOCK) return;
        event.setCancelled(true);
        Msg.actionBar(event.getPlayer(), "<red>You can't break blocks inside a rift.");
    }

    /** Parkour rifts forfeit on fall via teleport-to-start; suppress the actual fall damage so the player survives the drop. */
    @EventHandler(ignoreCancelled = true)
    public void onFallInRoom(org.bukkit.event.entity.EntityDamageEvent event) {
        if (event.getCause() != org.bukkit.event.entity.EntityDamageEvent.DamageCause.FALL) return;
        if (!(event.getEntity() instanceof Player)) return;
        LootRoomRun run = runAt(event.getEntity().getLocation());
        if (run == null || !run.room().id().startsWith("parkour_")) return;
        event.setCancelled(true);
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

    /** Rifts are co-op: party members can't hurt each other. Mobs still damage players. */
    @EventHandler(ignoreCancelled = true)
    public void onPvpInRoom(org.bukkit.event.entity.EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        if (runAt(event.getEntity().getLocation()) == null) return;
        if (damagerPlayer(event.getDamager()) != null) event.setCancelled(true);
    }

    /** The player behind a damage source — directly or via a projectile they fired. */
    private Player damagerPlayer(Entity damager) {
        if (damager instanceof Player p) return p;
        if (damager instanceof org.bukkit.entity.Projectile proj
                && proj.getShooter() instanceof Player p) return p;
        return null;
    }

    private void complete(LootRoomRun run) {
        java.util.List<Player> winners = run.players();
        if (winners.isEmpty()) return;
        Island island = plugin.islands().get(run.islandId());
        if (island == null) return;

        // Coin reward goes to the island bank ONCE. JACKPOT (the opener's Luck 10
        // perk) and the Double Coins event still apply to that single payout.
        int reward = run.room().rewardCoins(island);
        Player opener = run.player();
        if (opener != null && com.nova.novablock.progression.Perk.hasPerk(plugin.progression().get(opener),
                com.nova.novablock.progression.Perk.JACKPOT)) {
            reward = (int) Math.round(reward * 1.25);
        }
        if (plugin.seasons().active() == com.nova.novablock.season.SeasonManager.ServerEvent.DOUBLE_COINS) reward *= 2;
        plugin.economy().award(island, reward);

        // Loot + XP + completion credit are PER participant — each gets their own copy.
        for (Player p : winners) {
            plugin.progression().addXp(p, com.nova.novablock.progression.SkillType.MAGIC, 50L);
            plugin.progression().addXp(p, com.nova.novablock.progression.SkillType.LUCK, 20L);
            p.teleport(run.returnLocation());

            // Anything that doesn't fit in the player's inventory falls at their
            // feet so they never silently lose a reward. Each room's drop list is
            // freshly built per player, so clones aren't required.
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
            plugin.islandQuestline().record(p, com.nova.novablock.questline.IslandObjective.CLEAR_LOOT_ROOMS, 1);
            plugin.seasonalPaths().award(p, com.nova.novablock.season.SeasonalPathManager.PathSource.LOOT_ROOM, 90);
        }
    }

    private RoomTheme themeForRoomId(String roomId) {
        // Suffix is everything after the final underscore — defensive for legacy
        // unsuffixed room IDs (treat as overworld).
        if (roomId.endsWith("_nether")) return RoomTheme.NETHER;
        if (roomId.endsWith("_end")) return RoomTheme.END;
        return RoomTheme.OVERWORLD;
    }

    private World createInstanceWorld(Player player, RoomTheme theme) {
        String name = "novablock_loot_" + player.getUniqueId().toString().replace("-", "").substring(0, 12)
                + "_" + Long.toUnsignedString(System.currentTimeMillis(), 36);
        WorldCreator creator = new WorldCreator(name)
                .generator(new IslandWorldManager.VoidGenerator())
                .biomeProvider(new IslandWorldManager.SingleBiomeProvider(theme.biome()))
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

    public LootRoomRun getRun(Player p) { return runByPlayer.get(p.getUniqueId()); }

    public void shutdown() {
        if (tickTask != null) { tickTask.cancel(); tickTask = null; }
        // Send any active runs back to their return location so players aren't stranded.
        // cleanupRunWorld teleports every player in the instance out before deleting it.
        for (LootRoomRun run : runsByWorld.values()) {
            cleanupRunWorld(run);
        }
        runsByWorld.clear();
        runByPlayer.clear();
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
        long expiresAt;
        /** Non-null once the opener has entered: the instance world this portal now feeds for joiners. */
        String openedWorld;

        RiftOffer(String roomId, UUID islandId, String worldName, int bx, int by, int bz, long expiresAt) {
            this.roomId = roomId;
            this.islandId = islandId;
            this.worldName = worldName;
            this.bx = bx; this.by = by; this.bz = bz;
            this.expiresAt = expiresAt;
        }

        boolean opened() { return openedWorld != null; }

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
