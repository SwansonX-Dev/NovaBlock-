package com.nova.novablock.antiafk;

import com.nova.novablock.NovaBlock;
import com.nova.novablock.util.Msg;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Anti-AFK chat challenge. After the configured interval of mining activity,
 * the player is prompted to type a random letter in chat. If they don't respond
 * within the configured timeout they're warped to spawn (via `/warp spawn`).
 *
 * <p>"Activity" is bumped from {@link com.nova.novablock.listener.BlockListener}
 * each time the player breaks their OneBlock — that's the loop we care about
 * preventing AFK farming on. Other movement / chat / interactions don't reset
 * the clock by themselves, but a successful challenge response does.
 */
public class AntiAfkManager implements Listener {

    private static final long DEFAULT_CHALLENGE_INTERVAL_MS = 30L * 60L * 1000L;   // 30 min
    private static final long DEFAULT_CHALLENGE_TIMEOUT_MS  = 10L * 1000L;          // 10s
    private static final long TICK_PERIOD_TICKS     = 20L;                          // every 1s

    private final NovaBlock plugin;
    private final Map<UUID, State> states = new HashMap<>();
    private BukkitTask tickTask;
    private boolean enabled;
    private long challengeIntervalMs;
    private long challengeTimeoutMs;

    public AntiAfkManager(NovaBlock plugin) {
        this.plugin = plugin;
        reload();
        Bukkit.getPluginManager().registerEvents(this, plugin);
        tickTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickAll, TICK_PERIOD_TICKS, TICK_PERIOD_TICKS);
    }

    public void reload() {
        var cfg = plugin.configs().main();
        boolean wasEnabled = enabled;
        enabled = cfg.getBoolean("anti-afk.enabled", false);
        challengeIntervalMs = Math.max(60_000L,
                cfg.getLong("anti-afk.challenge-interval-seconds", DEFAULT_CHALLENGE_INTERVAL_MS / 1000L) * 1000L);
        challengeTimeoutMs = Math.max(10_000L,
                cfg.getLong("anti-afk.challenge-timeout-seconds", DEFAULT_CHALLENGE_TIMEOUT_MS / 1000L) * 1000L);
        if (!enabled) {
            states.clear();
            return;
        }
        // Keep existing pending challenges so /obadmin reload doesn't silently
        // give a free pass to anyone who was about to be caught.
        if (!wasEnabled) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                states.putIfAbsent(player.getUniqueId(), new State(Long.MAX_VALUE));
            }
        }
    }

    public void shutdown() {
        if (tickTask != null) { tickTask.cancel(); tickTask = null; }
        states.clear();
    }

    /** Called by BlockListener after a successful OneBlock break. */
    public void recordMineActivity(Player p) {
        if (!enabled) return;
        State s = states.computeIfAbsent(p.getUniqueId(), id -> new State(Long.MAX_VALUE));
        if (s.activeChallenge != 0) return;
        // Arm the challenge clock on first mining activity since clearance — that way
        // a player who just chats in the lobby is never challenged, only active miners.
        if (s.nextCheckAt == Long.MAX_VALUE) {
            s.nextCheckAt = System.currentTimeMillis() + challengeIntervalMs;
        }
    }

    private void tickAll() {
        if (!enabled) return;
        long now = System.currentTimeMillis();
        for (Player p : Bukkit.getOnlinePlayers()) {
            State s = states.get(p.getUniqueId());
            if (s == null) continue;
            if (s.activeChallenge != 0) {
                // Pending challenge — warp to spawn on timeout.
                if (now - s.challengeIssuedAt > challengeTimeoutMs) {
                    failChallenge(p, s);
                }
                continue;
            }
            if (now >= s.nextCheckAt) {
                issueChallenge(p, s, now);
            }
        }
    }

    private void issueChallenge(Player p, State s, long now) {
        char letter = (char) ('A' + ThreadLocalRandom.current().nextInt(26));
        s.activeChallenge = letter;
        s.challengeIssuedAt = now;
        Msg.send(p, "<gold>★ Anti-AFK check: <yellow>type the letter <white><bold>" + letter
                + " <yellow>in chat within " + (challengeTimeoutMs / 1000L) + "s.");
        p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_BELL, 1f, 1.2f);
    }

    private void failChallenge(Player p, State s) {
        s.activeChallenge = 0;
        s.challengeIssuedAt = 0;
        s.nextCheckAt = Long.MAX_VALUE;
        Msg.send(p, "<red>AFK check failed — sending you to spawn.");
        // performCommand runs as the player so warp permissions/cooldowns apply.
        p.performCommand("warp spawn");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChat(AsyncChatEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        if (!enabled) return;
        State s = states.get(id);
        if (s == null || s.activeChallenge == 0) return;
        String body = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
        if (body.isEmpty()) return;
        // Accept either the bare letter or it being the first non-whitespace character.
        char first = Character.toUpperCase(body.charAt(0));
        if (first == s.activeChallenge) {
            s.activeChallenge = 0;
            s.challengeIssuedAt = 0;
            // Disarm until they start mining again; next mine-activity sets the new deadline.
            s.nextCheckAt = Long.MAX_VALUE;
            Bukkit.getScheduler().runTask(plugin, () ->
                    Msg.send(event.getPlayer(), "<green>✓ Verified — see you in "
                            + (challengeIntervalMs / 60_000L) + " minutes."));
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (!enabled) return;
        states.put(event.getPlayer().getUniqueId(), new State(Long.MAX_VALUE));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        states.remove(event.getPlayer().getUniqueId());
    }

    private static final class State {
        long nextCheckAt;
        char activeChallenge;   // 0 = no challenge pending
        long challengeIssuedAt;

        State(long nextCheckAt) { this.nextCheckAt = nextCheckAt; }
    }
}
