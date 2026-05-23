package com.nova.novablock.island;

import com.nova.novablock.NovaBlock;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Enderman;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Ravager;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.Wither;
import org.bukkit.entity.WitherSkull;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Per-island flag enforcement. One Listener with a handler per flag, plus a
 * tick task that handles the location-driven flags (fly, always day).
 *
 * <p>All checks bail fast for locations outside any island so this is free
 * for vanilla play, and the OneBlock center remains owned by {@link com.nova.novablock.listener.BlockListener}
 * regardless of flag state.
 */
public class IslandFlagsManager implements Listener {

    private static final long TICK_PERIOD_TICKS = 10L; // 2 Hz — cheap

    private final NovaBlock plugin;
    private BukkitTask tickTask;

    /** Players we've granted fly to; lets us strip it cleanly when they leave the island. */
    private final Set<UUID> flyGranted = new HashSet<>();
    /** Players we've pinned to daytime; lets us reset when they leave. */
    private final Set<UUID> dayPinned = new HashSet<>();

    public IslandFlagsManager(NovaBlock plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        tickTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickAll, TICK_PERIOD_TICKS, TICK_PERIOD_TICKS);
    }

    public void shutdown() {
        if (tickTask != null) { tickTask.cancel(); tickTask = null; }
        for (UUID id : flyGranted) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && p.getGameMode() != GameMode.CREATIVE && p.getGameMode() != GameMode.SPECTATOR) {
                p.setAllowFlight(false);
                p.setFlying(false);
            }
        }
        flyGranted.clear();
        for (UUID id : dayPinned) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) p.resetPlayerTime();
        }
        dayPinned.clear();
    }

    // ---- tick task: fly + always-day ----

    private void tickAll() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            Island island = plugin.islands().atLocation(p.getLocation());
            boolean onOwnIsland = island != null && island.isMember(p);

            // FLY
            boolean shouldFly = onOwnIsland && island.data().isFlag(IslandFlag.ISLAND_FLY);
            if (p.getGameMode() != GameMode.CREATIVE && p.getGameMode() != GameMode.SPECTATOR) {
                if (shouldFly) {
                    if (!p.getAllowFlight()) {
                        p.setAllowFlight(true);
                        flyGranted.add(p.getUniqueId());
                    }
                } else if (flyGranted.remove(p.getUniqueId())) {
                    p.setAllowFlight(false);
                    p.setFlying(false);
                }
            }

            // ALWAYS_DAY
            boolean shouldPinDay = onOwnIsland && island.data().isFlag(IslandFlag.ALWAYS_DAY);
            if (shouldPinDay) {
                if (dayPinned.add(p.getUniqueId())) {
                    p.setPlayerTime(6000L, false); // absolute noon
                }
            } else if (dayPinned.remove(p.getUniqueId())) {
                p.resetPlayerTime();
            }
        }
    }

    // ---- helpers ----

    private Island islandAt(Location loc) {
        return plugin.islands().atLocation(loc);
    }

    private boolean flagOff(Island island, IslandFlag f) { return island != null && !island.data().isFlag(f); }
    private boolean flagOn(Island island, IslandFlag f)  { return island != null &&  island.data().isFlag(f); }

    // ---- NATURAL_MOB_SPAWNING ----

    @EventHandler(ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent event) {
        // CUSTOM = our own World.spawnEntity (OneBlock encounters, bosses, arena mobs) — never blocked.
        var reason = event.getSpawnReason();
        if (reason == CreatureSpawnEvent.SpawnReason.CUSTOM
                || reason == CreatureSpawnEvent.SpawnReason.COMMAND
                || reason == CreatureSpawnEvent.SpawnReason.SPAWNER_EGG
                || reason == CreatureSpawnEvent.SpawnReason.DISPENSE_EGG
                || reason == CreatureSpawnEvent.SpawnReason.BREEDING
                || reason == CreatureSpawnEvent.SpawnReason.EGG) return;
        Island island = islandAt(event.getLocation());
        if (flagOff(island, IslandFlag.NATURAL_MOB_SPAWNING)) event.setCancelled(true);
    }

    // ---- CREEPER_BLOCK_DAMAGE + TNT_BLOCK_DAMAGE + MOB_GRIEFING (via explosions) ----

    @EventHandler(ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        Entity source = event.getEntity();
        Island island = islandAt(event.getLocation());
        if (island == null) return;
        boolean creeper = source instanceof Creeper;
        boolean tntLike = source instanceof TNTPrimed
                || source.getType().name().equals("END_CRYSTAL")
                || source.getType().name().equals("MINECART_TNT");
        boolean witherProj = source instanceof WitherSkull || source instanceof Wither;
        if (creeper && flagOff(island, IslandFlag.CREEPER_BLOCK_DAMAGE)) {
            event.blockList().clear();
            return;
        }
        if (tntLike && flagOff(island, IslandFlag.TNT_BLOCK_DAMAGE)) {
            event.blockList().clear();
            return;
        }
        if (witherProj && flagOff(island, IslandFlag.MOB_GRIEFING)) {
            event.blockList().clear();
        }
    }

    // ---- MOB_GRIEFING (enderman pickup, ravager break, etc.) ----

    @EventHandler(ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        Entity e = event.getEntity();
        if (e instanceof Player) return;
        boolean grief = e instanceof Enderman || e instanceof Ravager || e instanceof Wither;
        if (!grief) return;
        Island island = islandAt(event.getBlock().getLocation());
        if (flagOff(island, IslandFlag.MOB_GRIEFING)) event.setCancelled(true);
    }

    // ---- FIRE_SPREAD ----

    @EventHandler(ignoreCancelled = true)
    public void onFireSpread(BlockSpreadEvent event) {
        if (event.getNewState().getType() != org.bukkit.Material.FIRE) return;
        Island island = islandAt(event.getBlock().getLocation());
        if (flagOff(island, IslandFlag.FIRE_SPREAD)) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBurn(BlockBurnEvent event) {
        Island island = islandAt(event.getBlock().getLocation());
        if (flagOff(island, IslandFlag.FIRE_SPREAD)) event.setCancelled(true);
    }

    // ---- PVP ----

    @EventHandler(ignoreCancelled = true)
    public void onPvp(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        Player attacker = resolvePlayer(event.getDamager());
        if (attacker == null) return;
        Island island = islandAt(event.getEntity().getLocation());
        if (flagOff(island, IslandFlag.PVP)) event.setCancelled(true);
    }

    private Player resolvePlayer(Entity damager) {
        if (damager instanceof Player p) return p;
        if (damager instanceof Projectile proj && proj.getShooter() instanceof Player p) return p;
        return null;
    }

    // ---- VISITOR_BUILD ----

    @EventHandler(ignoreCancelled = true)
    public void onVisitorBreak(BlockBreakEvent event) {
        Island island = islandAt(event.getBlock().getLocation());
        if (island == null) return;
        if (island.isMember(event.getPlayer())) return;
        if (!island.data().isFlag(IslandFlag.VISITOR_BUILD)) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onVisitorPlace(BlockPlaceEvent event) {
        Island island = islandAt(event.getBlock().getLocation());
        if (island == null) return;
        if (island.isMember(event.getPlayer())) return;
        if (!island.data().isFlag(IslandFlag.VISITOR_BUILD)) event.setCancelled(true);
    }

    // ---- KEEP_INVENTORY ----

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent event) {
        Island island = islandAt(event.getEntity().getLocation());
        if (island == null || !island.isMember(event.getEntity())) return;
        if (!island.data().isFlag(IslandFlag.KEEP_INVENTORY)) return;
        event.setKeepInventory(true);
        event.getDrops().clear();
        event.setKeepLevel(true);
        event.setDroppedExp(0);
    }

    // ---- NO_HUNGER_DRAIN ----

    @EventHandler(ignoreCancelled = true)
    public void onFood(FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player p)) return;
        if (event.getFoodLevel() >= p.getFoodLevel()) return; // increasing or same — always allow
        Island island = islandAt(p.getLocation());
        if (island == null || !island.isMember(p)) return;
        if (island.data().isFlag(IslandFlag.NO_HUNGER_DRAIN)) event.setCancelled(true);
    }
}
