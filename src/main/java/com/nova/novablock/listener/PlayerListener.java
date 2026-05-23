package com.nova.novablock.listener;

import com.nova.novablock.NovaBlock;
import com.nova.novablock.compat.PlayerCompat;
import com.nova.novablock.gui.MainMenuGui;
import com.nova.novablock.gui.WelcomeGui;
import com.nova.novablock.island.Island;
import com.nova.novablock.util.Msg;
import org.bukkit.GameMode;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

public class PlayerListener implements Listener {

    private final NovaBlock plugin;

    public PlayerListener(NovaBlock plugin) { this.plugin = plugin; }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        var p = event.getPlayer();
        plugin.progression().get(p); // warm cache
        Island island = plugin.islands().ofPlayer(p);
        if (island == null) {
            Msg.send(p, "<gray>Welcome to <gradient:#7B61FF:#4FC3F7>NovaBlock</gradient><gray>!");
            // Auto-open the welcome chest GUI a moment after join. Works on Bedrock.
            org.bukkit.Bukkit.getScheduler().runTaskLater(plugin,
                    () -> { if (p.isOnline()) new WelcomeGui(plugin).open(p); }, 30L);
        } else {
            Msg.send(p, "<gray>Welcome back. Your island awaits — <yellow>/ob home</yellow>.");
        }
        plugin.scoreboards().update(p);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.progression().save(event.getPlayer().getUniqueId());
        plugin.scoreboards().clear(event.getPlayer());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Island island = plugin.islands().ofPlayer(event.getPlayer());
        if (island != null) event.setRespawnLocation(island.data().spawnLocation());
    }

    @EventHandler
    public void onVoid(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof org.bukkit.entity.Player p)) return;
        if (event.getCause() != EntityDamageEvent.DamageCause.VOID) return;
        Island island = plugin.islands().ofPlayer(p);
        if (island != null && p.getGameMode() != GameMode.CREATIVE) {
            event.setCancelled(true);
            p.setFallDistance(0);
            island.teleportHome(p);
            Msg.actionBar(p, "<red>Saved you from the void.");
        }
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent event) {
        // Update scoreboard a tick later
        org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> plugin.scoreboards().update(event.getPlayer()));
    }
}
