package com.nova.novablock.community;

import com.nova.novablock.NovaBlock;
import com.nova.novablock.phase.Phase;
import com.nova.novablock.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Shared OneBlock at {@code /warp community}. Any online player may mine it: drops
 * go to the breaker normally, plus a configurable fraction of coin value is
 * siphoned into a shared pool that periodically pays out to contributors.
 *
 * <p>State is loaded/saved by {@link HubStorage} as a single yaml record.
 * Phase progression uses the shared {@link com.nova.novablock.phase.PhaseManager}.
 */
public class CommunityBlock {

    private final NovaBlock plugin;

    // ---- persisted state ----
    private long blocksBroken;
    private int phaseIndex;
    private int phaseProgress;
    private final Deque<Material> upcoming = new ArrayDeque<>();
    private final Map<UUID, Long> contributionByPlayer = new HashMap<>();
    private long lastPayoutAt;

    public CommunityBlock(NovaBlock plugin) {
        this.plugin = plugin;
        this.lastPayoutAt = System.currentTimeMillis();
    }

    // ---- accessors used by HubStorage / HubGui ----
    public long blocksBroken() { return blocksBroken; }
    public int phaseIndex() { return phaseIndex; }
    public int phaseProgress() { return phaseProgress; }
    public long lastPayoutAt() { return lastPayoutAt; }
    public Map<UUID, Long> contributionByPlayer() { return contributionByPlayer; }
    public Deque<Material> upcoming() { return upcoming; }

    public void setBlocksBroken(long v) { this.blocksBroken = v; }
    public void setPhaseIndex(int v) { this.phaseIndex = v; }
    public void setPhaseProgress(int v) { this.phaseProgress = v; }
    public void setLastPayoutAt(long v) { this.lastPayoutAt = v; }

    /**
     * Total coins currently in the shared pool (sum of contribution map).
     */
    public long pool() {
        long sum = 0;
        for (long v : contributionByPlayer.values()) sum += v;
        return sum;
    }

    /**
     * Initial placement: lay the bedrock anchor + put a starter block on top.
     * Called from CommunityHubManager on enable when block is missing.
     */
    public boolean placeInitial(Location center) {
        if (center == null || center.getWorld() == null) return false;
        boolean repaired = false;
        Location anchor = center.clone().add(0, -1, 0);
        if (anchor.getBlock().getType() != Material.BEDROCK) {
            anchor.getBlock().setType(Material.BEDROCK, false);
            repaired = true;
        }
        if (center.getBlock().getType() == Material.AIR
                || center.getBlock().getType() == Material.BEDROCK
                || center.getBlock().isLiquid()) {
            Phase phase = plugin.phases().getOrLast(phaseIndex);
            Material first = phase != null ? phase.rollBlock(ThreadLocalRandom.current()) : Material.STONE;
            center.getBlock().setType(first, false);
            repaired = true;
        }
        return repaired;
    }

    /**
     * Handles a break on the community block. Caller has already verified the
     * location matches and cancelled the vanilla event; this method drops, taxes,
     * and replaces the block.
     */
    public void onBreak(Player player, Material broken, BlockBreakEvent event, Location center) {
        if (center == null || center.getWorld() == null) return;
        var cfg = plugin.getConfig();

        // Drop natural drops to breaker, no perk-stacking — community block is intentionally
        // independent of personal-island progression so it can't be used to power-level.
        var loc = center.clone().add(0.5, 0.5, 0.5);
        // If the community block was a chest (or other container), drop its contents
        // explicitly — block.getDrops() only returns the chest item, not the inventory.
        if (event.getBlock().getState() instanceof org.bukkit.block.Container container) {
            for (var content : container.getInventory().getContents()) {
                if (content == null || content.getType().isAir()) continue;
                center.getWorld().dropItemNaturally(loc, content);
            }
            container.getInventory().clear();
        }
        for (var drop : event.getBlock().getDrops(player.getInventory().getItemInMainHand())) {
            center.getWorld().dropItemNaturally(loc, drop);
        }

        // Pick the next block from upcoming queue or roll fresh.
        Phase phase = plugin.phases().getOrLast(phaseIndex);
        Material next = upcoming.pollFirst();
        if (next == null && phase != null) next = phase.rollBlock(ThreadLocalRandom.current());
        if (next == null) next = Material.STONE;
        center.getBlock().setType(next, false);
        if (next == Material.CHEST && phase != null && plugin.blockListener() != null) {
            // Reuse the island chest-fill (phase-themed loot, dedup mark, tile-init race fallback)
            // so community chests pay out the same way personal-island ones do.
            plugin.blockListener().fillPhaseChest(center.getBlock(), phase);
            final Phase phaseRef = phase;
            Bukkit.getScheduler().runTask(plugin, () -> plugin.blockListener().fillPhaseChest(center.getBlock(), phaseRef));
        }
        Location anchor = center.clone().add(0, -1, 0);
        if (anchor.getBlock().getType() != Material.BEDROCK) {
            anchor.getBlock().setType(Material.BEDROCK, false);
        }

        // Per-break bonus always goes 100% into the pool; rare-block coin value
        // is taxed at the configured fraction so chests/beacons feel impactful
        // without dwarfing routine mining.
        long bonus = Math.max(0, cfg.getLong("community.block.bonus-coins-per-break", 1));
        double taxFraction = Math.max(0.0, Math.min(1.0, cfg.getDouble("community.block.coin-tax-fraction", 0.20)));
        long contributionThisBreak = bonus + Math.round(bonusForRareBlock(broken) * taxFraction);
        if (contributionThisBreak > 0) {
            contributionByPlayer.merge(player.getUniqueId(), contributionThisBreak, Long::sum);
        }

        // Refill the prophecy queue lazily.
        if (phase != null) {
            while (upcoming.size() < 5) upcoming.addLast(phase.rollBlock(ThreadLocalRandom.current()));
        }

        blocksBroken++;
        phaseProgress++;
        plugin.claimBlockRewards().recordCommunityBreak(player);
        int required = phase == null ? Integer.MAX_VALUE : scaledRequiredBlocks(phase);
        if (phase != null && phaseProgress >= required) {
            int nextIdx = phaseIndex + 1;
            Phase nextPhase = plugin.phases().get(nextIdx);
            if (nextPhase != null) {
                phaseIndex = nextIdx;
                phaseProgress = 0;
                upcoming.clear();
                if (cfg.getBoolean("community.block.broadcast.phase-advance", true)) {
                    Bukkit.broadcast(Msg.mm("<gold>✦ <yellow>Community OneBlock <gray>advanced to <"
                            + nextPhase.getThemeColor() + ">" + nextPhase.getDisplayName() + "<gray>!"));
                }
            }
        }

        long everyN = Math.max(1, cfg.getLong("community.block.broadcast.every-nth-break", 500));
        if (blocksBroken % everyN == 0) {
            Bukkit.broadcast(Msg.mm("<gold>✦ <yellow>" + player.getName()
                    + " <gray>mined the <gold>" + blocksBroken + "th <gray>community block!"));
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1.1f);
        }
        Msg.actionBar(player, "<gray>+<gold>" + contributionThisBreak + "<gray> to pool · <yellow>"
                + pool() + "<gray> total");
    }

    /**
     * Pays out the current contribution pool to contributors (proportionally),
     * filtering out those below the minimum-contribution floor. Clears the pool.
     * Returns total coins distributed.
     */
    public long payout() {
        if (contributionByPlayer.isEmpty()) return 0;
        var cfg = plugin.getConfig();
        long floor = Math.max(0, cfg.getLong("community.block.payout.min-contribution-coins", 25));

        // Filter contributors below the floor; they get nothing this payout but stay
        // in the map so a follow-up tick can pay them once they cross.
        List<Map.Entry<UUID, Long>> qualifying = new ArrayList<>();
        long totalQualifying = 0;
        for (var e : contributionByPlayer.entrySet()) {
            if (e.getValue() >= floor) {
                qualifying.add(e);
                totalQualifying += e.getValue();
            }
        }
        if (qualifying.isEmpty()) return 0;

        // Each qualifying player gets their own contribution back as coins (1:1).
        // Anyone above floor wins; under-floor contributors carry forward.
        long distributed = 0;
        // Sort biggest contributors first for nice broadcast text.
        qualifying.sort(Comparator.comparingLong((Map.Entry<UUID, Long> e) -> e.getValue()).reversed());
        for (var e : qualifying) {
            OfflinePlayer op = Bukkit.getOfflinePlayer(e.getKey());
            plugin.economy().deposit(op, e.getValue());
            distributed += e.getValue();
            Player online = op.getPlayer();
            if (online != null) {
                Msg.send(online, "<gold>✦ Community pool payout: <yellow>+" + e.getValue() + " coins");
            }
            contributionByPlayer.remove(e.getKey());
        }
        lastPayoutAt = System.currentTimeMillis();
        Bukkit.broadcast(Msg.mm("<gold>✦ <yellow>Community pool paid out <white>" + distributed
                + " <yellow>coins <gray>to " + qualifying.size() + " contributors."));
        return distributed;
    }

    /** Trigger payout if either threshold is met. Returns true if a payout fired. */
    public boolean tickPayoutIfDue() {
        var cfg = plugin.getConfig();
        long blockThreshold = Math.max(1, cfg.getLong("community.block.payout.block-threshold", 500));
        long intervalMs = Math.max(60_000L,
                cfg.getLong("community.block.payout.interval-minutes", 60) * 60_000L);
        long sinceLast = System.currentTimeMillis() - lastPayoutAt;
        // We don't track blocks-since-last-payout separately; use pool size + interval.
        // Block threshold maps to "pool has at least N coins" (close enough given tax/bonus rates).
        if (sinceLast >= intervalMs || pool() >= blockThreshold) {
            payout();
            return true;
        }
        return false;
    }

    /** Coin value of "rare" community blocks. Mirrors BlockListener.handleBlockEvents subset. */
    private static long bonusForRareBlock(Material m) {
        return switch (m) {
            case ENDER_CHEST -> 50;
            case BEACON -> 2500;
            case CONDUIT -> 1500;
            case SHULKER_BOX -> 1000;
            case SCULK_CATALYST -> 800;
            case NETHERITE_BLOCK -> 8000;
            case CAKE -> 600;
            default -> 0;
        };
    }

    private int scaledRequiredBlocks(Phase phase) {
        double mult = Math.max(1.0, plugin.getConfig()
                .getDouble("community.oneblocks.phase-required-multiplier", 10.0));
        return Math.max(1, (int) Math.ceil(phase.getRequiredBlocks() * mult));
    }
}
