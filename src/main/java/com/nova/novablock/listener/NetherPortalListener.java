package com.nova.novablock.listener;

import com.nova.novablock.NovaBlock;
import com.nova.novablock.island.Island;
import com.nova.novablock.util.Msg;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

/**
 * Vanilla Nether portals in the OneBlock world bridge to the same island's
 * Nether half, and vice versa. The portal is resolved against
 * {@link com.nova.novablock.island.IslandManager#atLocation(Location)} so a
 * portal placed on a visited island sends the visitor to that island's other
 * half — matching the standard "portals link points at the same x/z" mental
 * model rather than always sending the player to their own.
 */
public class NetherPortalListener implements Listener {

    private final NovaBlock plugin;

    public NetherPortalListener(NovaBlock plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPortal(PlayerPortalEvent event) {
        if (event.getCause() != PlayerTeleportEvent.TeleportCause.NETHER_PORTAL) return;
        Player player = event.getPlayer();
        if (player.getWorld() == null) return;

        String worldName = player.getWorld().getName();
        String overworld = plugin.worlds().worldName();
        String netherWorld = plugin.worlds().netherWorldName();
        boolean inOverworld = worldName.equals(overworld);
        boolean inNether = worldName.equals(netherWorld);
        if (!inOverworld && !inNether) return; // not in a OneBlock world — let vanilla handle.

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
}
