package com.nova.novablock.progression;

import com.nova.novablock.NovaBlock;
import com.nova.novablock.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Daily login streak. Each UTC day a player joins, their streak advances; a
 * missed day resets to 1. Milestone rewards are dispatched as console commands
 * (reuses the prestige reward-commands pattern) at days 1/3/7/14/30.
 *
 * <p>Day boundaries use UTC so the reward fires consistently regardless of
 * server-host timezone. Streak state lives on {@link PlayerProgression}.
 */
public class LoginStreakManager {

    private final NovaBlock plugin;

    public LoginStreakManager(NovaBlock plugin) { this.plugin = plugin; }

    /** Call once per player join. Idempotent within the same UTC day. */
    public void recordLogin(Player p) {
        PlayerProgression prog = plugin.progression().get(p);
        long today = System.currentTimeMillis() / 86_400_000L;
        long last = prog.getLastLoginDay();
        if (last == today) return; // already counted today
        int newStreak;
        if (last == today - 1) newStreak = prog.getLoginStreak() + 1;
        else newStreak = 1; // missed a day (or first login ever)
        prog.setLoginStreak(newStreak);
        prog.setLastLoginDay(today);
        plugin.progression().save(p.getUniqueId());
        awardIfMilestone(p, newStreak);
    }

    private void awardIfMilestone(Player p, int streak) {
        long coins = milestoneCoins(streak);
        if (coins <= 0) return;
        var island = plugin.islands().ofPlayer(p);
        if (island != null) plugin.economy().award(island, coins);
        Msg.title(p, "<gold>★ Day " + streak + " Streak!", "<yellow>+" + coins + " coins");
        p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1.1f);
        // Run any admin-configured commands so server owners can hand out custom rewards.
        List<String> commands = plugin.getConfig().getStringList("login-streak.commands-day-" + streak);
        for (String raw : commands) {
            String cmd = raw.replace("%player%", p.getName()).replace("%streak%", String.valueOf(streak));
            try { Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd); }
            catch (Throwable t) { plugin.getLogger().warning("Login-streak reward cmd failed: " + cmd); }
        }
    }

    private long milestoneCoins(int streak) {
        return switch (streak) {
            case 1  -> 250L;
            case 3  -> 1_000L;
            case 7  -> 3_000L;
            case 14 -> 8_000L;
            case 30 -> 25_000L;
            default -> 0L;
        };
    }
}
