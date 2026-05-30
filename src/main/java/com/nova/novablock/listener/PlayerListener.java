package com.nova.novablock.listener;

import com.nova.novablock.NovaBlock;
import com.nova.novablock.gui.HelpGui;
import com.nova.novablock.island.Island;
import com.nova.novablock.season.SeasonalPathManager;
import com.nova.novablock.util.Msg;
import org.bukkit.GameMode;
import org.bukkit.Sound;
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
        var prog = plugin.progression().get(p); // warm cache
        long lastLoginDay = prog.getLastLoginDay();
        plugin.loginStreaks().recordLogin(p);
        plugin.seasonalPaths().ensureActive(p);
        if (prog.getLastLoginDay() != lastLoginDay) {
            plugin.seasonalPaths().award(p, SeasonalPathManager.PathSource.LOGIN, 50);
        }
        Island island = plugin.islands().ofPlayer(p);
        boolean firstJoin = false;
        if (island == null) {
            plugin.islands().create(p);
            firstJoin = true;
            Msg.send(p, com.nova.novablock.util.Messages.of("welcome-first",
                    "<gray>Welcome to <gradient:#7B61FF:#4FC3F7>NovaBlock</gradient><gray>! Use <yellow>/ob home</yellow> to visit your island."));
        } else {
            Msg.send(p, com.nova.novablock.util.Messages.of("welcome-back",
                    "<gray>Welcome back."));
        }
        boolean showFirstJoin = firstJoin;
        org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline()) return;
            var personal = plugin.playerSpawns().get(p.getUniqueId());
            var dest = personal != null ? personal : plugin.spawn().location();
            if (dest != null) p.teleport(dest);
            if (showFirstJoin) {
                Msg.title(p, "<gradient:#7B61FF:#4FC3F7>NovaBlock", "<gray>Your island is ready");
                p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
                // First-ever join — show the help guide. Returning players can reopen
                // with /novahelp or /ob help and shouldn't be interrupted.
                new HelpGui(plugin).open(p);
            }
            plugin.scoreboards().update(p);
        }, 20L);
        plugin.scoreboards().update(p);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.progression().save(event.getPlayer().getUniqueId());
        plugin.scoreboards().clear(event.getPlayer());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        var personal = plugin.playerSpawns().get(event.getPlayer().getUniqueId());
        if (personal != null) {
            event.setRespawnLocation(personal);
            return;
        }
        var server = plugin.spawn().location();
        if (server != null) {
            event.setRespawnLocation(server);
            return;
        }
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
