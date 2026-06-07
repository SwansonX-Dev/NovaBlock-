package com.nova.novablock.progression;

import com.nova.novablock.NovaBlock;
import com.nova.novablock.island.Island;
import com.nova.novablock.phase.Phase;
import com.nova.novablock.prophecy.ProphecyManager;
import com.nova.novablock.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class PrestigeManager {

    /**
     * Armor-trim templates awarded on prestige. Pulled from the full 1.21 trim list,
     * minus NETHERITE_UPGRADE (vanilla-farmable from bastion loot) so prestige
     * remains a meaningful source of cosmetic trims rather than gating crafting.
     */
    private static final List<Material> PRESTIGE_TEMPLATES = List.of(
            Material.SENTRY_ARMOR_TRIM_SMITHING_TEMPLATE,
            Material.DUNE_ARMOR_TRIM_SMITHING_TEMPLATE,
            Material.COAST_ARMOR_TRIM_SMITHING_TEMPLATE,
            Material.WILD_ARMOR_TRIM_SMITHING_TEMPLATE,
            Material.WARD_ARMOR_TRIM_SMITHING_TEMPLATE,
            Material.EYE_ARMOR_TRIM_SMITHING_TEMPLATE,
            Material.VEX_ARMOR_TRIM_SMITHING_TEMPLATE,
            Material.TIDE_ARMOR_TRIM_SMITHING_TEMPLATE,
            Material.SNOUT_ARMOR_TRIM_SMITHING_TEMPLATE,
            Material.RIB_ARMOR_TRIM_SMITHING_TEMPLATE,
            Material.SPIRE_ARMOR_TRIM_SMITHING_TEMPLATE,
            Material.WAYFINDER_ARMOR_TRIM_SMITHING_TEMPLATE,
            Material.SHAPER_ARMOR_TRIM_SMITHING_TEMPLATE,
            Material.SILENCE_ARMOR_TRIM_SMITHING_TEMPLATE,
            Material.RAISER_ARMOR_TRIM_SMITHING_TEMPLATE,
            Material.HOST_ARMOR_TRIM_SMITHING_TEMPLATE,
            Material.FLOW_ARMOR_TRIM_SMITHING_TEMPLATE,
            Material.BOLT_ARMOR_TRIM_SMITHING_TEMPLATE
    );

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
        // Prestige clears Nightmare so players can opt back in if they want.
        island.data().setFlag(com.nova.novablock.island.IslandFlag.NIGHTMARE_MODE, false);
        island.upcomingBlocks().clear();
        Phase first = plugin.phases().get(0);
        if (first != null) {
            island.refillUpcoming(first, ProphecyManager.QUEUE_SIZE);
        }

        plugin.paxels().refreshTier(player);

        long lump = baseCoinReward * newLevel;
        plugin.economy().award(island, lump);

        Material template = pickPrestigeTemplate(island);
        boolean collectionComplete = template == null;
        if (collectionComplete) {
            // All 18 templates already collected — pick any at random so the reward
            // doesn't silently disappear once the set is complete.
            template = PRESTIGE_TEMPLATES.get(
                    ThreadLocalRandom.current().nextInt(PRESTIGE_TEMPLATES.size()));
        } else {
            island.data().getReceivedPrestigeTemplates().add(template.name());
        }
        ItemStack templateItem = new ItemStack(template);
        var overflow = player.getInventory().addItem(templateItem);
        for (var leftover : overflow.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), leftover);
        }

        for (String raw : rewardCommands) {
            String cmd = raw.replace("%player%", player.getName())
                    .replace("%level_capped%", String.valueOf(Math.min(newLevel, maxLevel)))
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
        int collected = island.data().getReceivedPrestigeTemplates().size();
        int total = PRESTIGE_TEMPLATES.size();
        if (collectionComplete) {
            Msg.send(player, "<light_purple>★ Prestige reward: <white>"
                    + prettyTemplateName(template)
                    + " <gray>(collection complete — random template)");
        } else {
            Msg.send(player, "<light_purple>★ Prestige reward: <white>"
                    + prettyTemplateName(template)
                    + " <dark_gray>(" + collected + "/" + total + " trims)");
        }
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 0.8f);
        plugin.seasonalPaths().award(player, com.nova.novablock.season.SeasonalPathManager.PathSource.PRESTIGE, 500);

        Bukkit.broadcast(Msg.mm("<gold>" + title + " <gray>— <yellow>"
                + player.getName() + " <gray>has prestiged!"));
    }

    public double coinMultiplier(Island island) {
        return 1.0 + coinMultPerLevel * cappedLevel(island);
    }

    public double xpMultiplier(Island island) {
        return 1.0 + xpMultPerLevel * cappedLevel(island);
    }

    public double coinMultiplierAtLevel(int level) {
        return 1.0 + coinMultPerLevel * Math.min(Math.max(0, level), maxLevel);
    }

    public double xpMultiplierAtLevel(int level) {
        return 1.0 + xpMultPerLevel * Math.min(Math.max(0, level), maxLevel);
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

    /**
     * Pick a smithing template the island hasn't received yet. Returns {@code null}
     * when every template in {@link #PRESTIGE_TEMPLATES} has already been awarded;
     * the caller falls back to a fully random pick in that case.
     */
    private Material pickPrestigeTemplate(Island island) {
        var received = island.data().getReceivedPrestigeTemplates();
        java.util.List<Material> remaining = new java.util.ArrayList<>();
        for (Material m : PRESTIGE_TEMPLATES) {
            if (!received.contains(m.name())) remaining.add(m);
        }
        if (remaining.isEmpty()) return null;
        return remaining.get(ThreadLocalRandom.current().nextInt(remaining.size()));
    }

    /** "SENTRY_ARMOR_TRIM_SMITHING_TEMPLATE" → "Sentry Armor Trim Smithing Template". */
    private static String prettyTemplateName(Material m) {
        String[] parts = m.name().toLowerCase().split("_");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) sb.append(' ');
            sb.append(Character.toUpperCase(parts[i].charAt(0))).append(parts[i].substring(1));
        }
        return sb.toString();
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
