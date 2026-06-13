package com.nova.novablock.gui;

import com.nova.novablock.NovaBlock;
import com.nova.novablock.island.Island;
import com.nova.novablock.sprint.WeeklySprintManager;
import com.nova.novablock.util.ItemBuilder;
import com.nova.novablock.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Weekly competition view with two boards: Hardcore (blocks broken this week)
 * and Casual (daily quests completed this week, tie-broken by who hit 7/7 first).
 * Tab switching is done by clicking the labelled tab icon in row 1 — no
 * server round-trip, just reopen the GUI in the other mode.
 */
public class SprintGui extends ChestGui {

    public enum Tab { HARDCORE, CASUAL }

    private final NovaBlock plugin;
    private final Tab tab;

    public SprintGui(NovaBlock plugin) { this(plugin, Tab.HARDCORE); }

    public SprintGui(NovaBlock plugin, Tab tab) {
        super("<gradient:#7B61FF:#4FC3F7><bold>Weekly Sprint", 6);
        this.plugin = plugin;
        this.tab = tab;
    }

    @Override
    protected void build(Player viewer) {
        WeeklySprintManager sprint = plugin.sprint();

        // Tab selectors
        set(2, ItemBuilder.of(Material.NETHERITE_PICKAXE)
                .name((tab == Tab.HARDCORE ? "<red><bold>" : "<gray>") + "Hardcore")
                .lore("<gray>Top islands by blocks broken this week.",
                        tab == Tab.HARDCORE ? "<dark_gray>(viewing)" : "<yellow>Click to view.")
                .build(),
                e -> { if (tab != Tab.HARDCORE) new SprintGui(plugin, Tab.HARDCORE).open(viewer); });

        set(6, ItemBuilder.of(Material.WRITTEN_BOOK)
                .name((tab == Tab.CASUAL ? "<aqua><bold>" : "<gray>") + "Casual")
                .lore("<gray>Top players by daily quests completed this week.",
                        "<gray>Ties broken by who hit 7/7 first.",
                        tab == Tab.CASUAL ? "<dark_gray>(viewing)" : "<yellow>Click to view.")
                .build(),
                e -> { if (tab != Tab.CASUAL) new SprintGui(plugin, Tab.CASUAL).open(viewer); });

        long weekEnd = sprint.weekEnd();
        long now = System.currentTimeMillis();
        String label = now >= weekEnd
                ? "ended — resetting on next tick"
                : "resets in " + formatRemaining(weekEnd - now);
        set(4, ItemBuilder.of(Material.CLOCK)
                .name("<gradient:#7B61FF:#4FC3F7><bold>This Week")
                .lore("<gray>" + label,
                        "<gray>Sunday 20:00 podium broadcast.",
                        "<gold>Rewards: <yellow>" + rewardLine(tab))
                .glow().build(), null);

        if (tab == Tab.HARDCORE) renderHardcore(viewer);
        else renderCasual(viewer);

        set(45, ItemBuilder.of(Material.ARROW).name("<gray>Leaderboard").build(),
                e -> new LeaderboardGui(plugin).open(viewer));
        set(46, ItemBuilder.of(Material.GOLD_BLOCK)
                .name("<gold>Last Winners")
                .lore(lastWinnerLore())
                .build(),
                e -> new SprintHistoryGui(plugin).open(viewer));
        set(49, ItemBuilder.of(tab == Tab.HARDCORE ? Material.COMPASS : Material.NAME_TAG)
                .name("<aqua>Your Rank")
                .lore(rankLore(viewer))
                .build(), null);
        set(53, ItemBuilder.of(Material.NETHER_STAR)
                .name("<gradient:#7B61FF:#4FC3F7><bold>Main Menu")
                .lore("<dark_gray>/ob menu").glow().build(),
                e -> new MainMenuGui(plugin).open(viewer));

        fill(Material.GRAY_STAINED_GLASS_PANE, " ");
    }

    private void renderHardcore(Player viewer) {
        List<WeeklySprintManager.HardcoreRow> top = plugin.sprint().topHardcore(28);
        if (top.isEmpty()) {
            set(31, ItemBuilder.of(Material.STONE).name("<gray>No mining yet this week").build(), null);
            return;
        }
        int[] slots = innerSlots();
        for (int i = 0; i < top.size() && i < slots.length; i++) {
            var row = top.get(i);
            Island island = plugin.islands().all().get(row.islandUuid());
            OfflinePlayer owner = island == null ? null : Bukkit.getOfflinePlayer(island.data().getOwner());
            String name = owner == null || owner.getName() == null ? "Unknown" : owner.getName();
            String medal = rankPrefix(i);
            List<String> lore = new ArrayList<>();
            lore.add("<gray>Blocks this week: <white>" + row.blocks());
            if (island != null) lore.add("<gray>Phase: <white>" + (island.data().getPhaseIndex() + 1));
            ItemStack head = owner != null ? head(owner) : new ItemStack(Material.IRON_BLOCK);
            set(slots[i], decorate(head, "<red>" + medal + " " + name, lore), null);
        }
    }

    private void renderCasual(Player viewer) {
        List<WeeklySprintManager.CasualRow> top = plugin.sprint().topCasual(28);
        if (top.isEmpty()) {
            set(31, ItemBuilder.of(Material.PAPER).name("<gray>No quests finished yet this week").build(), null);
            return;
        }
        int[] slots = innerSlots();
        SimpleDateFormat fmt = new SimpleDateFormat("EEE HH:mm");
        for (int i = 0; i < top.size() && i < slots.length; i++) {
            var row = top.get(i);
            OfflinePlayer op = Bukkit.getOfflinePlayer(row.playerUuid());
            String name = op.getName() == null ? "Unknown" : op.getName();
            List<String> lore = new ArrayList<>();
            int casualMax = com.nova.novablock.sprint.WeeklySprintManager.casualMax();
            lore.add("<gray>Quests this week: <white>" + row.quests() + "<gray>/" + casualMax);
            if (row.firstSevenAt() > 0L) {
                lore.add("<gray>Maxed: <#FFC940>" + fmt.format(new Date(row.firstSevenAt())));
            } else if (row.quests() == casualMax) {
                // Edge case after a restart that lost the timestamp.
                lore.add("<gray>Maxed: <#FFC940>this week");
            }
            String medal = rankPrefix(i);
            set(slots[i], decorate(head(op), "<aqua>" + medal + " " + name, lore), null);
        }
    }

    /** Inner-area slots (rows 2–4, cols 1–7) — 21 entries. */
    private static int[] innerSlots() {
        return new int[] {
                10, 11, 12, 13, 14, 15, 16,
                19, 20, 21, 22, 23, 24, 25,
                28, 29, 30, 31, 32, 33, 34
        };
    }

    private static String rankPrefix(int i) {
        return switch (i) {
            case 0 -> "<gold>#1";
            case 1 -> "<white>#2";
            case 2 -> "<#CD7F32>#3";
            default -> "<gray>#" + (i + 1);
        };
    }

    private static String formatRemaining(long millis) {
        long minutes = millis / 60_000;
        long days = minutes / (60 * 24);
        long hours = (minutes / 60) % 24;
        long mins = minutes % 60;
        if (days > 0) return days + "d " + hours + "h";
        if (hours > 0) return hours + "h " + mins + "m";
        return mins + "m";
    }

    private List<String> rankLore(Player viewer) {
        List<String> lore = new ArrayList<>();
        if (tab == Tab.HARDCORE) {
            Island island = plugin.islands().ofPlayer(viewer);
            if (island == null) {
                lore.add("<gray>Create an island to join Hardcore.");
                return lore;
            }
            int rank = plugin.sprint().hardcoreRank(island.data().getId());
            long score = plugin.sprint().hardcoreScore(island.data().getId());
            lore.add(rank > 0 ? "<gray>Rank: <yellow>#" + rank : "<gray>Rank: <white>Unranked");
            lore.add("<gray>Blocks this week: <white>" + score);
            return lore;
        }
        int rank = plugin.sprint().casualRank(viewer.getUniqueId());
        int quests = plugin.sprint().casualQuests(viewer.getUniqueId());
        lore.add(rank > 0 ? "<gray>Rank: <yellow>#" + rank : "<gray>Rank: <white>Unranked");
        lore.add("<gray>Quests this week: <white>" + quests + "<gray>/7");
        return lore;
    }

    private String rewardLine(Tab tab) {
        List<Long> rewards = tab == Tab.HARDCORE ? plugin.sprint().hardcoreCoinRewards() : plugin.sprint().casualCoinRewards();
        List<String> parts = new ArrayList<>();
        for (int i = 0; i < Math.min(3, rewards.size()); i++) {
            long coins = rewards.get(i);
            if (coins > 0) parts.add("#" + (i + 1) + " " + formatNumber(coins));
        }
        return parts.isEmpty() ? "No coin rewards" : String.join(" / ", parts);
    }

    private List<String> lastWinnerLore() {
        List<WeeklySprintManager.WinnerRow> winners = plugin.sprint().lastWinners();
        if (winners.isEmpty()) return List.of("<gray>No completed sprint yet.");
        List<String> lore = new ArrayList<>();
        for (WeeklySprintManager.WinnerRow row : winners) {
            lore.add("<gray>" + prettyBoard(row.board()) + " #" + row.place()
                    + ": <yellow>" + row.name()
                    + " <dark_gray>(" + formatNumber(row.score()) + ")");
            if (lore.size() >= 6) break;
        }
        lore.add("<yellow>Click to view.");
        return lore;
    }

    static String prettyBoard(String board) {
        return "casual".equalsIgnoreCase(board) ? "Casual" : "Hardcore";
    }

    static String formatNumber(long value) {
        return String.format(Locale.US, "%,d", value);
    }

    private static ItemStack head(OfflinePlayer op) {
        ItemStack stack = new ItemStack(Material.PLAYER_HEAD);
        if (stack.getItemMeta() instanceof SkullMeta meta) {
            meta.setOwningPlayer(op);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private static ItemStack decorate(ItemStack stack, String name, List<String> lore) {
        var meta = stack.getItemMeta();
        if (meta == null) return stack;
        meta.displayName(Msg.mm(name).decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
        var loreList = new ArrayList<net.kyori.adventure.text.Component>();
        for (String l : lore) loreList.add(Msg.mm(l).decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
        meta.lore(loreList);
        stack.setItemMeta(meta);
        return stack;
    }
}
