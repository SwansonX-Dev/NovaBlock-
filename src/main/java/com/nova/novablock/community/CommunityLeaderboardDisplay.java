package com.nova.novablock.community;

import com.nova.novablock.NovaBlock;
import com.nova.novablock.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;

public class CommunityLeaderboardDisplay {

    private final NovaBlock plugin;
    private final CommunityHubManager hub;
    private final NamespacedKey key;
    private UUID displayId;
    private BukkitTask tickTask;

    CommunityLeaderboardDisplay(NovaBlock plugin, CommunityHubManager hub) {
        this.plugin = plugin;
        this.hub = hub;
        this.key = new NamespacedKey(plugin, "community_leaderboard");
        start();
    }

    private void start() {
        tickTask = Bukkit.getScheduler().runTaskTimer(plugin, this::refresh, 40L, 20L * 30L);
    }

    public void refresh() {
        if (!hub.isEnabled()) {
            remove();
            return;
        }
        Location base = plugin.spawn().communityBlockLocation();
        if (base == null || base.getWorld() == null) {
            remove();
            return;
        }
        Location loc = base.clone().add(0.5, 2.8, 0.5);
        TextDisplay display = ensure(loc);
        if (display == null) return;
        display.teleport(loc);
        display.text(Msg.mm(text()));
    }

    public void shutdown() {
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
        remove();
    }

    private TextDisplay ensure(Location loc) {
        if (displayId != null) {
            Entity existing = Bukkit.getEntity(displayId);
            if (existing instanceof TextDisplay td && !td.isDead()) return td;
        }
        cleanupTagged(loc);
        TextDisplay display = loc.getWorld().spawn(loc, TextDisplay.class, td -> {
            td.setBillboard(Display.Billboard.CENTER);
            td.setDefaultBackground(false);
            td.setPersistent(false);
            td.setViewRange(1.0f);
            td.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
        });
        displayId = display.getUniqueId();
        return display;
    }

    private String text() {
        StringBuilder out = new StringBuilder();
        out.append("<gold><bold>Community Hub</bold>\n");
        out.append("<yellow>Weekly: <white>")
                .append(hub.goal().progress())
                .append("/")
                .append(hub.goal().target())
                .append("\n");
        out.append("<gold>Pool: <yellow>")
                .append(hub.block().pool())
                .append(" coins\n");

        int rank = 1;
        for (Map.Entry<UUID, Long> entry : hub.goal().topContributors(3)) {
            OfflinePlayer op = Bukkit.getOfflinePlayer(entry.getKey());
            String name = op.getName() == null ? "Unknown" : op.getName();
            out.append("<gray>").append(rank).append(". <white>")
                    .append(name)
                    .append(" <yellow>")
                    .append(entry.getValue())
                    .append("\n");
            rank++;
        }
        if (rank == 1) out.append("<dark_gray>No contributors yet\n");
        out.append("<aqua>/ob hub");
        return out.toString();
    }

    private void remove() {
        if (displayId != null) {
            Entity existing = Bukkit.getEntity(displayId);
            if (existing != null) existing.remove();
            displayId = null;
        }
    }

    private void cleanupTagged(Location loc) {
        if (loc.getWorld() == null) return;
        for (Entity entity : loc.getWorld().getNearbyEntities(loc, 6, 6, 6)) {
            if (entity.getPersistentDataContainer().has(key, PersistentDataType.BYTE)) {
                entity.remove();
            }
        }
    }
}
