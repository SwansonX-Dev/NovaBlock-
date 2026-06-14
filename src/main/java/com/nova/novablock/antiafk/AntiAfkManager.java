package com.nova.novablock.antiafk;

import com.nova.novablock.NovaBlock;
import com.nova.novablock.gui.AfkCheckGui;
import com.nova.novablock.gui.ChestGui;
import com.nova.novablock.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Anti-auto-mine check. A player who mines their OneBlock <em>continuously</em>
 * for the configured interval is shown a {@link AfkCheckGui} and must click the
 * green wool to prove a human is present. Failing to (timeout, wrong click, or
 * trying to close the GUI) warps them to spawn — interrupting the auto-mine loop.
 *
 * <p>This targets AFK auto-mining with hacked/modded clients specifically: the
 * clock only advances while the player keeps breaking blocks (driven by
 * {@link com.nova.novablock.listener.BlockListener#recordMineActivity}). A player
 * who simply stands AFK without mining is never challenged — once they stop
 * mining for {@code active-window-seconds}, the session resets.
 */
public class AntiAfkManager implements Listener {

    private static final long DEFAULT_CHALLENGE_INTERVAL_MS = 30L * 60L * 1000L;   // 30 min
    private static final long DEFAULT_CHALLENGE_TIMEOUT_MS  = 30L * 1000L;          // 30s to react
    private static final long DEFAULT_ACTIVE_WINDOW_MS      = 30L * 1000L;          // gap that ends a session
    private static final long TICK_PERIOD_TICKS     = 20L;                          // every 1s

    private static final double DEFAULT_MOVE_THRESHOLD = 1.5;   // blocks from the mining spot

    private final NovaBlock plugin;
    private final Map<UUID, State> states = new HashMap<>();
    private BukkitTask tickTask;
    private boolean enabled;
    private long challengeIntervalMs;
    private long challengeTimeoutMs;
    private long activeWindowMs;
    private double moveThresholdSq;

    public AntiAfkManager(NovaBlock plugin) {
        this.plugin = plugin;
        reload();
        Bukkit.getPluginManager().registerEvents(this, plugin);
        tickTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickAll, TICK_PERIOD_TICKS, TICK_PERIOD_TICKS);
    }

    public void reload() {
        var cfg = plugin.configs().main();
        enabled = cfg.getBoolean("anti-afk.enabled", false);
        challengeIntervalMs = Math.max(60_000L,
                cfg.getLong("anti-afk.challenge-interval-seconds", DEFAULT_CHALLENGE_INTERVAL_MS / 1000L) * 1000L);
        challengeTimeoutMs = Math.max(5_000L,
                cfg.getLong("anti-afk.challenge-timeout-seconds", DEFAULT_CHALLENGE_TIMEOUT_MS / 1000L) * 1000L);
        activeWindowMs = Math.max(5_000L,
                cfg.getLong("anti-afk.active-window-seconds", DEFAULT_ACTIVE_WINDOW_MS / 1000L) * 1000L);
        double moveThreshold = Math.max(0.5, cfg.getDouble("anti-afk.move-threshold-blocks", DEFAULT_MOVE_THRESHOLD));
        moveThresholdSq = moveThreshold * moveThreshold;
        if (!enabled) states.clear();
    }

    public void shutdown() {
        if (tickTask != null) { tickTask.cancel(); tickTask = null; }
        states.clear();
    }

    /** Called by BlockListener after a successful OneBlock break. */
    public void recordMineActivity(Player p) {
        if (!enabled) return;
        State s = states.computeIfAbsent(p.getUniqueId(), id -> new State());
        if (s.challengePending) return;
        long now = System.currentTimeMillis();
        // A gap longer than the active window ends the previous session — start a fresh one,
        // anchored at the current spot so leaving it later resets the clock.
        if (now - s.lastMineAt > activeWindowMs) startSession(s, p, now);
        s.lastMineAt = now;
    }

    private void startSession(State s, Player p, long now) {
        s.sessionStartAt = now;
        var loc = p.getLocation();
        s.world = loc.getWorld().getUID();
        s.ax = loc.getX();
        s.ay = loc.getY();
        s.az = loc.getZ();
    }

    /** True if the player has moved away from their anchored mining spot (or changed world). */
    private boolean movedAway(State s, Player p) {
        var loc = p.getLocation();
        if (loc.getWorld() == null || !loc.getWorld().getUID().equals(s.world)) return true;
        double dx = loc.getX() - s.ax, dy = loc.getY() - s.ay, dz = loc.getZ() - s.az;
        return dx * dx + dy * dy + dz * dz > moveThresholdSq;
    }

    private void tickAll() {
        if (!enabled) return;
        long now = System.currentTimeMillis();
        for (Player p : Bukkit.getOnlinePlayers()) {
            State s = states.get(p.getUniqueId());
            if (s == null) continue;
            if (s.challengePending) {
                if (now - s.challengeIssuedAt > challengeTimeoutMs) failChallenge(p, s);
                continue;
            }
            if (s.sessionStartAt == 0) continue;                 // not mining
            if (now - s.lastMineAt > activeWindowMs) {           // stopped mining — disarm (AFK is fine)
                s.sessionStartAt = 0;
                continue;
            }
            if (movedAway(s, p)) {                               // moved around — actually playing, reset the clock
                startSession(s, p, now);
                continue;
            }
            if (now - s.sessionStartAt >= challengeIntervalMs) { // standing still, mining non-stop past the interval
                issueChallenge(p, s, now);
            }
        }
    }

    private void issueChallenge(Player p, State s, long now) {
        s.challengePending = true;
        s.challengeIssuedAt = now;
        Msg.send(p, "<gold>★ Auto-mine check: <yellow>click the <green>green wool<yellow> within "
                + (challengeTimeoutMs / 1000L) + "s.");
        p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_BELL, 1f, 1.2f);
        new AfkCheckGui(plugin).open(p);
    }

    /** Green wool clicked — verified human. */
    public void guiPass(Player p) {
        State s = states.get(p.getUniqueId());
        if (s == null || !s.challengePending) return;
        s.challengePending = false;
        s.challengeIssuedAt = 0;
        long now = System.currentTimeMillis();
        // Keep the session running (re-anchored here) so a non-stop miner is re-checked next interval.
        startSession(s, p, now);
        s.lastMineAt = now;
        p.closeInventory();
        Msg.send(p, "<green>✓ Verified — back to mining.");
    }

    /** Wrong wool clicked — treat as a failed check. */
    public void guiWrong(Player p) {
        State s = states.get(p.getUniqueId());
        if (s == null || !s.challengePending) return;
        Msg.send(p, "<red>Wrong block.");
        failChallenge(p, s);
    }

    private void failChallenge(Player p, State s) {
        s.challengePending = false;
        s.challengeIssuedAt = 0;
        s.sessionStartAt = 0;
        p.closeInventory();
        Msg.send(p, "<red>Auto-mine check failed — sending you to spawn.");
        // performCommand runs as the player so warp permissions/cooldowns apply.
        p.performCommand("warp spawn");
    }

    /** Reopen the check if the player tries to escape it by closing the GUI. */
    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!enabled || !(event.getPlayer() instanceof Player p)) return;
        State s = states.get(p.getUniqueId());
        if (s == null || !s.challengePending) return;
        if (!(event.getInventory().getHolder() instanceof ChestGui.Holder h) || !(h.gui instanceof AfkCheckGui)) return;
        // Can't open an inventory from within the close event — do it next tick.
        Bukkit.getScheduler().runTask(plugin, () -> {
            State cur = states.get(p.getUniqueId());
            if (enabled && p.isOnline() && cur != null && cur.challengePending) new AfkCheckGui(plugin).open(p);
        });
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (!enabled) return;
        states.put(event.getPlayer().getUniqueId(), new State());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        states.remove(event.getPlayer().getUniqueId());
    }

    private static final class State {
        long sessionStartAt;    // when the current standing-still mining session began (0 = not mining)
        long lastMineAt;        // last OneBlock break
        UUID world;             // anchored mining spot (resets the clock if the player leaves it)
        double ax, ay, az;
        boolean challengePending;
        long challengeIssuedAt;
    }
}
