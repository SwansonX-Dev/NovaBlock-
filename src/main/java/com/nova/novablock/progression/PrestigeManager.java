package com.nova.novablock.progression;

import com.nova.novablock.NovaBlock;
import com.nova.novablock.island.Island;
import com.nova.novablock.phase.Phase;
import com.nova.novablock.prophecy.ProphecyManager;
import com.nova.novablock.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.List;

public class PrestigeManager {

    private final NovaBlock plugin;
    private double coinMultPerLevel = 0.10;
    private double xpMultPerLevel = 0.05;
    private int maxLevel = 10;
    private long baseCoinReward = 50_000L;
    private List<String> rewardCommands = List.of();

    public PrestigeManager(NovaBlock plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        FileConfiguration cfg = plugin.configs().main();
        this.coinMultPerLevel = cfg.getDouble("prestige.coin-multiplier-per-level", 0.10);
        this.xpMultPerLevel = cfg.getDouble("prestige.xp-multiplier-per-level", 0.05);
        this.maxLevel = cfg.getInt("prestige.max-level", 10);
        this.baseCoinReward = cfg.getLong("prestige.base-coin-reward", 50_000L);
        this.rewardCommands = cfg.getStringList("prestige.reward-commands");
    }

    public boolean canPrestige(Island island) {
        if (island == null) return false;
        int lastIdx = plugin.phases().phaseCount() - 1;
        if (island.data().getPhaseIndex() < lastIdx) return false;
        Phase last = plugin.phases().get(lastIdx);
        return last != null && island.data().getPhaseProgress() >= last.getRequiredBlocks();
    }

    public void doPrestige(Player player, Island island) {
        if (!canPrestige(island)) {
            Msg.send(player, "<red>You must complete Phase " + plugin.phases().phaseCount()
                    + " before you can prestige.");
            return;
        }

        int newLevel = island.data().getPrestigeLevel() + 1;
        island.data().setPrestigeLevel(newLevel);
        island.data().setPhaseIndex(0);
        island.data().setPhaseProgress(0);
        island.upcomingBlocks().clear();
        Phase first = plugin.phases().get(0);
        if (first != null) {
            island.refillUpcoming(first, ProphecyManager.QUEUE_SIZE);
        }

        plugin.paxels().refreshTier(player);

        long lump = baseCoinReward * newLevel;
        plugin.economy().award(island, lump);

        for (String raw : rewardCommands) {
            String cmd = raw.replace("%player%", player.getName())
                    .replace("%level%", String.valueOf(newLevel));
            try {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
            } catch (Throwable t) {
                plugin.getLogger().warning("Prestige reward command failed (" + cmd + "): " + t.getMessage());
            }
        }

        plugin.storage().saveIsland(island.data());

        String title = title(newLevel);
        Msg.title(player, "<gold>✦ Prestige " + roman(newLevel), "<gray>+"
                + Math.round(coinMultiplier(island) * 100 - 100) + "% coins, +"
                + Math.round(xpMultiplier(island) * 100 - 100) + "% XP");
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 0.8f);

        Bukkit.broadcast(Msg.mm("<gold>" + title + " <gray>— <yellow>"
                + player.getName() + " <gray>has prestiged!"));
    }

    public double coinMultiplier(Island island) {
        return 1.0 + coinMultPerLevel * cappedLevel(island);
    }

    public double xpMultiplier(Island island) {
        return 1.0 + xpMultPerLevel * cappedLevel(island);
    }

    private int cappedLevel(Island island) {
        if (island == null) return 0;
        return Math.min(island.data().getPrestigeLevel(), maxLevel);
    }

    public String title(int prestigeLevel) {
        if (prestigeLevel <= 0) return "";
        String color = colorFor(prestigeLevel);
        return "<" + color + ">✦ Prestige " + roman(prestigeLevel) + "</" + color + ">";
    }

    private static String colorFor(int level) {
        if (level >= 10) return "#FF4DDA";
        if (level >= 7) return "#FFD700";
        if (level >= 4) return "#7BFFBB";
        return "#9FC5FF";
    }

    private static String roman(int n) {
        if (n <= 0) return String.valueOf(n);
        String[] thousands = {"", "M", "MM", "MMM"};
        String[] hundreds = {"", "C", "CC", "CCC", "CD", "D", "DC", "DCC", "DCCC", "CM"};
        String[] tens = {"", "X", "XX", "XXX", "XL", "L", "LX", "LXX", "LXXX", "XC"};
        String[] ones = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX"};
        return thousands[(n / 1000) % 4] + hundreds[(n / 100) % 10]
                + tens[(n / 10) % 10] + ones[n % 10];
    }
}
