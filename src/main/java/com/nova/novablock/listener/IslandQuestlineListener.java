package com.nova.novablock.listener;

import com.nova.novablock.NovaBlock;
import com.nova.novablock.island.Island;
import com.nova.novablock.questline.IslandObjective;
import org.bukkit.Material;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Ghast;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Phantom;
import org.bukkit.entity.Player;
import org.bukkit.entity.Slime;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.FurnaceExtractEvent;
import org.bukkit.event.player.PlayerFishEvent;

/**
 * Event-driven island-questline objectives that don't already have a hook in
 * gameplay code: fighting mobs ({@link IslandObjective#KILL_MOBS}) and running a
 * cobble generator ({@link IslandObjective#GENERATE_COBBLE}).
 */
public class IslandQuestlineListener implements Listener {

    private final NovaBlock plugin;

    public IslandQuestlineListener(NovaBlock plugin) { this.plugin = plugin; }

    /** Credit a hostile-mob kill to the killer's island questline. */
    @EventHandler(ignoreCancelled = true)
    public void onMobDeath(EntityDeathEvent event) {
        LivingEntity dead = event.getEntity();
        Player killer = dead.getKiller();
        if (killer == null) return;
        if (!isHostile(dead)) return;
        plugin.islandQuestline().record(killer, IslandObjective.KILL_MOBS, 1);
    }

    private static boolean isHostile(LivingEntity e) {
        return e instanceof Monster || e instanceof Slime || e instanceof Ghast || e instanceof Phantom;
    }

    /**
     * Credit cobblestone/stone produced by a cobble generator (lava meeting
     * water) to the island it formed on. {@link BlockFormEvent} fires per block
     * generated; there's no player attached, so we attribute it to the island.
     */
    @EventHandler(ignoreCancelled = true)
    public void onCobbleForm(BlockFormEvent event) {
        Material formed = event.getNewState().getType();
        if (formed != Material.COBBLESTONE && formed != Material.STONE) return;
        Island island = plugin.islands().atLocation(event.getBlock().getLocation());
        if (island == null) return;
        plugin.islandQuestline().recordForIsland(island, IslandObjective.GENERATE_COBBLE, 1, null);
    }

    /** Credit items pulled out of a furnace (fuel-gated, so not exploitable). */
    @EventHandler(ignoreCancelled = true)
    public void onSmelt(FurnaceExtractEvent event) {
        if (event.getItemAmount() <= 0) return;
        plugin.islandQuestline().record(event.getPlayer(), IslandObjective.SMELT_ITEMS, event.getItemAmount());
    }

    /** Credit harvesting a fully-grown crop on the island. */
    @EventHandler(ignoreCancelled = true)
    public void onCropHarvest(BlockBreakEvent event) {
        BlockData data = event.getBlock().getBlockData();
        if (!(data instanceof Ageable age) || age.getAge() < age.getMaximumAge()) return;
        Island island = plugin.islands().atLocation(event.getBlock().getLocation());
        if (island == null) return;
        plugin.islandQuestline().recordForIsland(island, IslandObjective.HARVEST_CROPS, 1, event.getPlayer());
    }

    /** Credit a successful fishing catch (rod-time gated). */
    @EventHandler(ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) return;
        plugin.islandQuestline().record(event.getPlayer(), IslandObjective.CATCH_FISH, 1);
    }
}
