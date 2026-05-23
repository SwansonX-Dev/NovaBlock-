package com.nova.novablock.season;

import com.nova.novablock.NovaBlock;
import com.nova.novablock.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Server-wide timed events that change the rules briefly. These are the "limited-time hype"
 * mechanic — designed to bring everyone online into the moment.
 */
public class SeasonManager {

    public enum ServerEvent {
        DIAMOND_HOUR("Diamond Hour", "<aqua>", "5x diamond drop chance from the OneBlock"),
        DOUBLE_COINS("Coin Rush", "<gold>", "All coin rewards are doubled"),
        BLOOD_MOON("Blood Moon", "<red>", "Bosses spawn 4x more often"),
        LUSH_BLOOM("Lush Bloom", "<green>", "Rare lush blocks have a chance to appear in any phase"),
        RIFT_STORM("Rift Storm", "<light_purple>", "Loot rooms appear 5x more often");

        public final String displayName;
        public final String color;
        public final String description;

        ServerEvent(String name, String color, String desc) {
            this.displayName = name;
            this.color = color;
            this.description = desc;
        }
    }

    private final NovaBlock plugin;
    private ServerEvent active;
    private long activeUntil;
    private BukkitTask ticker;

    public SeasonManager(NovaBlock plugin) { this.plugin = plugin; }

    public void startSeasonTicker() {
        if (ticker != null) ticker.cancel();
        ticker = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L * 60, 20L * 60); // every minute
    }

    private void tick() {
        if (active != null && System.currentTimeMillis() > activeUntil) {
            endEvent();
        }
        // Roll new event ~ every 30 minutes (1/30 per minute)
        if (active == null && ThreadLocalRandom.current().nextInt(30) == 0) {
            startRandom();
        }
    }

    public ServerEvent active() { return active; }
    public long activeUntil() { return activeUntil; }

    public void startRandom() {
        ServerEvent[] values = ServerEvent.values();
        ServerEvent e = values[ThreadLocalRandom.current().nextInt(values.length)];
        startEvent(e, 10);
    }

    public void startEvent(ServerEvent e, int minutes) {
        this.active = e;
        this.activeUntil = System.currentTimeMillis() + minutes * 60_000L;
        for (Player p : Bukkit.getOnlinePlayers()) {
            Msg.title(p, e.color + "▶ " + e.displayName, "<gray>" + e.description);
            Msg.send(p, e.color + "<bold>SERVER EVENT</bold> <gray>– " + e.displayName + " <dark_gray>(" + minutes + "m)");
            p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.6f, 1.4f);
        }
    }

    public void endEvent() {
        if (active == null) return;
        for (Player p : Bukkit.getOnlinePlayers()) {
            Msg.send(p, "<gray>" + active.displayName + " has ended.");
        }
        this.active = null;
        this.activeUntil = 0;
    }

    public void shutdown() {
        if (ticker != null) { ticker.cancel(); ticker = null; }
        active = null;
        activeUntil = 0;
    }
}
