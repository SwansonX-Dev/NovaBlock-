package com.nova.novablock.listener;

import com.nova.novablock.NovaBlock;
import com.nova.novablock.island.Island;
import com.nova.novablock.util.Msg;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.Sound;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

/**
 * Two responsibilities:
 *
 * <ol>
 *   <li>Bridge vanilla Nether portals inside the OneBlock world to the same
 *       island's Nether half (and vice versa).</li>
 *   <li>Disable the vanilla Nether and vanilla End entirely — the OneBlock
 *       Nether is the only Nether on this server, and there is no End. Any
 *       Nether portal that would land outside our worlds, any End portal
 *       teleport, and any Eye-of-Ender activation of an End Portal Frame is
 *       cancelled with a player-facing message.</li>
 * </ol>
 *
 * <p>End Portal Frames placed inside loot rooms remain functional as rift
 * markers because that path doesn't go through {@link PlayerInteractEvent}
 * with an Ender Eye in hand.
 */
public class NetherPortalListener implements Listener {

    private final NovaBlock plugin;

    public NetherPortalListener(NovaBlock plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPortal(PlayerPortalEvent event) {
        Player player = event.getPlayer();
        if (player.getWorld() == null) return;
        PlayerTeleportEvent.TeleportCause cause = event.getCause();

        // The End is disabled entirely. Any END_PORTAL teleport (player walks
        // into a built end portal, throws an enderpearl into it, etc.) is
        // rejected with a clear message.
        if (cause == PlayerTeleportEvent.TeleportCause.END_PORTAL) {
            event.setCancelled(true);
            Msg.send(player, "<red>The End is disabled on this server.");
            return;
        }
        if (cause != PlayerTeleportEvent.TeleportCause.NETHER_PORTAL) return;

        String fromWorld = player.getWorld().getName();
        String overworld = plugin.worlds().worldName();
        String netherWorld = plugin.worlds().netherWorldName();
        boolean inOverworld = fromWorld.equals(overworld);
        boolean inNether = fromWorld.equals(netherWorld);

        if (!inOverworld && !inNether) {
            // Player is somewhere else trying to portal — most likely a leaked
            // vanilla world. Vanilla nether is disabled; cancel so they don't
            // travel into one.
            event.setCancelled(true);
            Msg.send(player, "<red>The vanilla Nether is disabled. Use <yellow>/ob home nether</yellow> from your island.");
            return;
        }

        Island island = plugin.islands().atLocation(player.getLocation());
        if (island == null) return;

        if (inOverworld) {
            if (!plugin.worlds().isNetherEnabled() || !island.isNetherUnlocked()) {
                event.setCancelled(true);
                Msg.send(player, "<red>This island's Nether is sealed. Reach Phase 7 to break through.");
                return;
            }
            Location dest = island.data().netherSpawnLocation();
            if (dest.getWorld() == null) {
                event.setCancelled(true);
                Msg.send(player, "<red>The Nether world isn't loaded right now.");
                return;
            }
            // Belt-and-suspenders: if Paper somehow set the destination to a
            // vanilla nether world, override it back to ours.
            event.setTo(dest);
            event.setCanCreatePortal(false);
        } else {
            Location dest = island.data().spawnLocation();
            if (dest.getWorld() == null) {
                event.setCancelled(true);
                Msg.send(player, "<red>The Overworld isn't loaded right now.");
                return;
            }
            event.setTo(dest);
            event.setCanCreatePortal(false);
        }
    }

    /**
     * Fires once per island the first time its owner enters their own Nether.
     * Welcomes them to Crimson Outpost so the dimension doesn't open silently
     * after the Overworld-side unlock broadcast. Persisted via
     * {@link com.nova.novablock.island.IslandData#setFirstNetherVisit(boolean)}
     * so a restart doesn't re-trigger.
     */
    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        if (player.getWorld() == null) return;
        String world = player.getWorld().getName();
        boolean intoNether = world.equals(plugin.worlds().netherWorldName());
        boolean intoEnd = world.equals(plugin.worlds().endWorldName());
        if (!intoNether && !intoEnd) return;
        Island island = plugin.islands().atLocation(player.getLocation());
        if (island == null) return;
        if (!player.getUniqueId().equals(island.data().getOwner())) return;

        if (intoNether) {
            if (!island.data().isFirstNetherVisit()) return;
            island.data().setFirstNetherVisit(false);
            plugin.storage().saveIsland(island.data());
            Msg.title(player,
                    "<#FF4D4D><bold>Crimson Outpost",
                    "<gray>Break the center to begin your Nether descent.");
            Msg.send(player, "<#FF6347>The Nether opens. Twelve phases lie between you and the Ashen Warlord.");
            player.playSound(player.getLocation(), Sound.AMBIENT_NETHER_WASTES_MOOD, 0.8f, 0.7f);
        } else {
            if (!island.data().isFirstEndVisit()) return;
            island.data().setFirstEndVisit(false);
            plugin.storage().saveIsland(island.data());
            Msg.title(player,
                    "<#E6E0FF><bold>Outer Islands",
                    "<gray>Break the center to begin your End ascent.");
            Msg.send(player, "<#C9B8FF>The Void yawns open. Twelve phases lie between you and the Void Throne.");
            player.playSound(player.getLocation(), Sound.AMBIENT_CAVE, 0.8f, 0.6f);
        }
    }

    /**
     * Activating an End Portal Frame with an Eye of Ender starts the multi-eye
     * sequence that completes a real End portal. Cancel the right-click in
     * either hand to lock the End out cleanly. The frame block itself stays
     * usable (we use it as the loot-room rift marker), only the eye insertion
     * is blocked.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEnderEyeInsert(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getClickedBlock() == null) return;
        if (event.getClickedBlock().getType() != Material.END_PORTAL_FRAME) return;
        if (event.getItem() == null || event.getItem().getType() != Material.ENDER_EYE) return;
        event.setCancelled(true);
        Player player = event.getPlayer();
        Msg.send(player, "<red>The End is disabled — End Portals can't be assembled.");
    }
}
