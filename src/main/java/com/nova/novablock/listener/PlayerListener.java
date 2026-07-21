package com.nova.novablock.listener;

import com.nova.novablock.NovaBlock;
import com.nova.novablock.gui.HelpGui;
import com.nova.novablock.island.Island;
import com.nova.novablock.season.SeasonalPathManager;
import com.nova.novablock.util.Msg;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
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
        // Restore their /ob islands choice before anything reads "their" island.
        plugin.islands().restoreActiveIsland(p.getUniqueId());
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
        // Keep the island's inactivity clock fresh — this is what stops an island a
        // member still plays on from ever being seen as purgeable.
        Island active = plugin.islands().ofPlayer(p);
        if (active != null) active.data().touchActivity();
        boolean showFirstJoin = firstJoin;
        // Notify online friends — gated by the joining player's own opt-out so they
        // can hide their join from friends without affecting incoming visibility.
        if (plugin.friends().wantsJoinNotify(p.getUniqueId())) {
            for (var friend : plugin.friends().onlineFriends(p.getUniqueId())) {
                if (friend.equals(p)) continue;
                Msg.send(friend, "<gray>[<aqua>Friend<gray>] <yellow>" + p.getName() + " <gray>joined.");
            }
        }
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
            plugin.rankNameplates().refreshAll();
        }, 20L);
        plugin.scoreboards().update(p);
        plugin.rankNameplates().refreshAll();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        // Stamp the island active on the way out as well as on join, so a long session
        // counts from when it ended rather than when it started.
        Island quitting = plugin.islands().ofPlayer(event.getPlayer());
        if (quitting != null) quitting.data().touchActivity();
        plugin.progression().save(event.getPlayer().getUniqueId());
        plugin.rankNameplates().remove(event.getPlayer());
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

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onVoid(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof org.bukkit.entity.Player p)) return;
        if (event.getCause() != EntityDamageEvent.DamageCause.VOID) return;
        if (p.getGameMode() == GameMode.CREATIVE) return;

        String world = p.getWorld().getName();

        // OG OneBlock world is handled by the OGOneBlock plugin's own void listener.
        String ogWorld = plugin.getConfig().getString("ogWorld", "OGOBworld");
        if (world.equals(ogWorld)) return;

        if (plugin.community() != null && world.equals(plugin.community().communityWorldName())) {
            Location hub = plugin.community().hubSpawnLocation();
            if (hub == null || hub.getWorld() == null) return;
            event.setCancelled(true);
            p.setFallDistance(0);
            hub.getChunk().load();
            p.teleportAsync(hub);
            Msg.actionBar(p, "<red>Saved you from the void.");
            return;
        }

        Island island = plugin.islands().ofPlayer(p);
        if (island != null) {
            event.setCancelled(true);
            p.setFallDistance(0);
            island.teleportHome(p);
            Msg.actionBar(p, "<red>Saved you from the void.");
        }
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent event) {
        // Update scoreboard a tick later, and re-tier the paxel — entering the community
        // world can raise the earned tier (community phase folds into PaxelManager.tierFor).
        org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
            if (!event.getPlayer().isOnline()) return;
            plugin.scoreboards().update(event.getPlayer());
            plugin.rankNameplates().refreshAll();
            plugin.paxels().refreshTier(event.getPlayer());
        });
    }
}
