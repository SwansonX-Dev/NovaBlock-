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
 * Anti-AFK chat challenge. After {@link #CHALLENGE_INTERVAL_MS} of mining
 * activity, the player is prompted to type a random letter in chat. If they
 * don't respond within {@link #CHALLENGE_TIMEOUT_MS} they're kicked.
 *
 * <p>"Activity" is bumped from {@link com.nova.novablock.listener.BlockListener}
 * each time the player breaks their OneBlock — that's the loop we care about
 * preventing AFK farming on. Other movement / chat / interactions don't reset
 * the clock by themselves, but a successful challenge response does.
 */
public class AntiAfkManager implements Listener {

    private static final long CHALLENGE_INTERVAL_MS = 30L * 60L * 1000L;   // 30 min
    private static final long CHALLENGE_TIMEOUT_MS  = 60L * 1000L;          // 60s
    private static final long TICK_PERIOD_TICKS     = 20L * 5L;             // every 5s

    private final NovaBlock plugin;
    private final Map<UUID, State> states = new HashMap<>();
    private BukkitTask tickTask;

    public AntiAfkManager(NovaBlock plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        tickTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickAll, TICK_PERIOD_TICKS, TICK_PERIOD_TICKS);
    }

    public void shutdown() {
        if (tickTask != null) { tickTask.cancel(); tickTask = null; }
        states.clear();
    }

    /** Called by BlockListener after a successful OneBlock break. */
    public void recordMineActivity(Player p) {
        State s = states.computeIfAbsent(p.getUniqueId(), id -> new State(System.currentTimeMillis() + CHALLENGE_INTERVAL_MS));
        // If they're already being challenged we don't reset the clock; only a
        // correct chat reply clears the challenge.
        if (s.activeChallenge == 0) {
            // Nudge the next-check forward only if they have NOT yet earned a check.
            // (We don't push it past now+interval so a steady miner still gets checked.)
        }
    }

    private void tickAll() {
        long now = System.currentTimeMillis();
        for (Player p : Bukkit.getOnlinePlayers()) {
            State s = states.get(p.getUniqueId());
            if (s == null) continue;
            if (s.activeChallenge != 0) {
                // Pending challenge — kick on timeout.
                if (now - s.challengeIssuedAt > CHALLENGE_TIMEOUT_MS) {
                    p.kick(Msg.mm("<red>Kicked for AFK — no chat response to the captcha."));
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
                + " <yellow>in chat within 60s.");
        p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_BELL, 1f, 1.2f);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChat(AsyncChatEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        State s = states.get(id);
        if (s == null || s.activeChallenge == 0) return;
        String body = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
        if (body.isEmpty()) return;
        // Accept either the bare letter or it being the first non-whitespace character.
        char first = Character.toUpperCase(body.charAt(0));
        if (first == s.activeChallenge) {
            long now = System.currentTimeMillis();
            s.activeChallenge = 0;
            s.challengeIssuedAt = 0;
            s.nextCheckAt = now + CHALLENGE_INTERVAL_MS;
            Bukkit.getScheduler().runTask(plugin, () ->
                    Msg.send(event.getPlayer(), "<green>✓ Verified — see you in 30 minutes."));
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        states.put(event.getPlayer().getUniqueId(),
                new State(System.currentTimeMillis() + CHALLENGE_INTERVAL_MS));
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
