package com.nova.novablock.antiafk;

import com.nova.novablock.NovaBlock;
import com.nova.novablock.util.Msg;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Anti-auto-mine check. A player who keeps a <strong>pickaxe or paxel</strong>
 * in hand for the configured interval (default 30 min) is challenged to type a
 * random single letter in chat before they can mine again. While the challenge
 * is pending all block-breaking is blocked (see
 * {@link com.nova.novablock.listener.BlockListener}), and the answer message is
 * swallowed so the letter never appears in public chat. Ignoring the challenge
 * for {@code challenge-timeout-seconds} warps the player to spawn, breaking the
 * auto-mine loop.
 *
 * <p>The clock only advances while a mining tool is held — switching to any
 * other item resets it — so a player who isn't holding a tool is never
 * challenged.
 *
 * <p>Threading: chat events fire async, so {@link #states} is concurrent and the
 * challenge flag is volatile. Chat handlers only read the flag and cancel the
 * event; the actual verification is bounced to the main thread.
 */
public class AntiAfkManager implements Listener {

    private static final long DEFAULT_CHALLENGE_INTERVAL_MS = 30L * 60L * 1000L;   // 30 min
    private static final long DEFAULT_CHALLENGE_TIMEOUT_MS  = 30L * 1000L;          // 30s to react
    private static final long TICK_PERIOD_TICKS = 20L;                              // every 1s
    private static final long NUDGE_THROTTLE_MS = 3_000L;

    private final NovaBlock plugin;
    private final Map<UUID, State> states = new ConcurrentHashMap<>();
    private BukkitTask tickTask;
    private boolean enabled;
    private boolean warpOnTimeout;
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
        enabled = cfg.getBoolean("anti-afk.enabled", false);
        challengeIntervalMs = Math.max(60_000L,
                cfg.getLong("anti-afk.challenge-interval-seconds", DEFAULT_CHALLENGE_INTERVAL_MS / 1000L) * 1000L);
        challengeTimeoutMs = Math.max(5_000L,
                cfg.getLong("anti-afk.challenge-timeout-seconds", DEFAULT_CHALLENGE_TIMEOUT_MS / 1000L) * 1000L);
        warpOnTimeout = cfg.getBoolean("anti-afk.warp-on-timeout", true);
        if (!enabled) states.clear();
    }

    public void shutdown() {
        if (tickTask != null) { tickTask.cancel(); tickTask = null; }
        states.clear();
    }

    /** True while a player owes us a letter — used by BlockListener to block mining. */
    public boolean isChallengePending(Player p) {
        State s = states.get(p.getUniqueId());
        return s != null && s.challengePending;
    }

    /**
     * Called from the break handlers: if the player has a pending challenge, this
     * reminds them (throttled) and returns true so the caller cancels the break.
     */
    public boolean blocksMining(Player p) {
        State s = states.get(p.getUniqueId());
        if (s == null || !s.challengePending) return false;
        long now = System.currentTimeMillis();
        if (now - s.lastNudgeMs > NUDGE_THROTTLE_MS) {
            s.lastNudgeMs = now;
            Msg.actionBar(p, "<red>AFK check — type <yellow>" + s.letter + "<red> in chat to keep mining.");
        }
        return true;
    }

    private void tickAll() {
        if (!enabled) return;
        long now = System.currentTimeMillis();
        for (Player p : Bukkit.getOnlinePlayers()) {
            State s = states.computeIfAbsent(p.getUniqueId(), id -> new State());
            if (s.challengePending) {
                if (warpOnTimeout && now - s.challengeIssuedAt > challengeTimeoutMs) failChallenge(p, s);
                continue;
            }
            if (!isMiningTool(p.getInventory().getItemInMainHand())) {
                s.holdingSince = 0;                              // not holding a tool — disarm
                continue;
            }
            if (s.holdingSince == 0) s.holdingSince = now;       // just picked one up — start the clock
            if (now - s.holdingSince >= challengeIntervalMs) issueChallenge(p, s, now);
        }
    }

    private boolean isMiningTool(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;
        if (plugin.paxels().isPaxel(item)) return true;
        return item.getType().name().endsWith("_PICKAXE");
    }

    private void issueChallenge(Player p, State s, long now) {
        s.challengePending = true;
        s.challengeIssuedAt = now;
        s.letter = (char) ('A' + ThreadLocalRandom.current().nextInt(26));
        Msg.send(p, "<gold>★ AFK check: <yellow>type the letter <green><bold>" + s.letter
                + "</bold><yellow> in chat to keep mining.");
        if (warpOnTimeout) {
            Msg.send(p, "<gray>You can't mine until you do — and you'll be sent to spawn in <yellow>"
                    + (challengeTimeoutMs / 1000L) + "s<gray> if you don't.");
        } else {
            Msg.send(p, "<gray>You can't mine until you do.");
        }
        Msg.title(p, "<gold>AFK Check", "<yellow>Type <green>" + s.letter + "<yellow> in chat");
        p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_BELL, 1f, 1.2f);
    }

    /**
     * Receive a chat answer. Runs on the async chat thread — dedupes (both the
     * legacy and Paper chat events can fire for one message) and bounces the
     * real work to the main thread.
     */
    private void submitAnswer(Player p, String message) {
        State s = states.get(p.getUniqueId());
        if (s == null || !s.challengePending || s.answerQueued) return;
        s.answerQueued = true;
        Bukkit.getScheduler().runTask(plugin, () -> verifyAnswer(p, message));
    }

    private void verifyAnswer(Player p, String message) {
        State s = states.get(p.getUniqueId());
        if (s == null) return;
        s.answerQueued = false;
        if (!s.challengePending) return;
        String answer = message == null ? "" : message.trim();
        if (answer.equalsIgnoreCase(String.valueOf(s.letter))) {
            passChallenge(p, s);
        } else {
            Msg.send(p, "<red>Wrong letter. Type <yellow>" + s.letter + "<red> in chat to keep mining.");
        }
    }

    private void passChallenge(Player p, State s) {
        s.challengePending = false;
        s.challengeIssuedAt = 0;
        s.holdingSince = System.currentTimeMillis();             // restart the clock from now
        Msg.send(p, "<green>✓ Verified — back to mining.");
        p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.4f);
    }

    private void failChallenge(Player p, State s) {
        s.challengePending = false;
        s.challengeIssuedAt = 0;
        s.holdingSince = 0;
        Msg.send(p, "<red>AFK check failed — sending you to spawn.");
        // performCommand runs as the player so warp permissions/cooldowns apply.
        p.performCommand("warp spawn");
    }

    // Chat answers must be hidden so the letter never broadcasts. We listen on
    // both the legacy and the Paper chat events (CMI/other plugins may use either)
    // and cancel whichever fires; submitAnswer() dedupes the verification.

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChatLegacy(AsyncPlayerChatEvent event) {
        if (!enabled || !isChallengePending(event.getPlayer())) return;
        event.setCancelled(true);
        submitAnswer(event.getPlayer(), event.getMessage());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChatPaper(AsyncChatEvent event) {
        if (!enabled || !isChallengePending(event.getPlayer())) return;
        event.setCancelled(true);
        submitAnswer(event.getPlayer(),
                PlainTextComponentSerializer.plainText().serialize(event.message()));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        states.remove(event.getPlayer().getUniqueId());
    }

    private static final class State {
        volatile boolean challengePending;
        volatile boolean answerQueued;
        long challengeIssuedAt;
        long holdingSince;          // when the player started continuously holding a mining tool (0 = none)
        char letter;
        long lastNudgeMs;
    }
}
