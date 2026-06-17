package com.nova.novablock.community;

import com.nova.novablock.NovaBlock;
import com.nova.novablock.phase.Phase;
import com.nova.novablock.phase.PhaseBlock;
import com.nova.novablock.season.SeasonManager;
import com.nova.novablock.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
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

    /**
     * Hand-tuned weighted block pool for the community OneBlock. This is the server's
     * primary resource-gathering OneBlock, so the pool spans every overworld+nether+end
     * stone, every wood/leaf type, every ore (with deepslate variants), common foodstuffs,
     * and a small tail of rare loot blocks. Phase-themed selection is bypassed for the
     * community block — the pool is identical regardless of how far the community has phased.
     */
    private static final List<PhaseBlock> COMMUNITY_BLOCKS = List.of(
            // --- stones (common) ---
            new PhaseBlock(Material.STONE, 30),
            new PhaseBlock(Material.COBBLESTONE, 22),
            new PhaseBlock(Material.DEEPSLATE, 18),
            new PhaseBlock(Material.COBBLED_DEEPSLATE, 14),
            new PhaseBlock(Material.ANDESITE, 10),
            new PhaseBlock(Material.DIORITE, 10),
            new PhaseBlock(Material.GRANITE, 10),
            new PhaseBlock(Material.TUFF, 8),
            new PhaseBlock(Material.CALCITE, 6),
            new PhaseBlock(Material.DRIPSTONE_BLOCK, 6),
            new PhaseBlock(Material.BASALT, 8),
            new PhaseBlock(Material.BLACKSTONE, 8),
            new PhaseBlock(Material.NETHERRACK, 12),
            new PhaseBlock(Material.END_STONE, 5),
            // --- earth ---
            new PhaseBlock(Material.DIRT, 22),
            new PhaseBlock(Material.GRASS_BLOCK, 18),
            new PhaseBlock(Material.COARSE_DIRT, 8),
            new PhaseBlock(Material.PODZOL, 5),
            new PhaseBlock(Material.MYCELIUM, 4),
            new PhaseBlock(Material.ROOTED_DIRT, 5),
            new PhaseBlock(Material.MOSS_BLOCK, 6),
            new PhaseBlock(Material.MUD, 6),
            new PhaseBlock(Material.CLAY, 5),
            // --- sands / gravel ---
            new PhaseBlock(Material.SAND, 16),
            new PhaseBlock(Material.RED_SAND, 6),
            new PhaseBlock(Material.GRAVEL, 12),
            new PhaseBlock(Material.SOUL_SAND, 3),
            new PhaseBlock(Material.SOUL_SOIL, 3),
            // --- snow / ice ---
            new PhaseBlock(Material.SNOW_BLOCK, 6),
            new PhaseBlock(Material.ICE, 3),
            new PhaseBlock(Material.PACKED_ICE, 2),
            new PhaseBlock(Material.BLUE_ICE, 1),
            // --- logs (every wood type) ---
            new PhaseBlock(Material.OAK_LOG, 10),
            new PhaseBlock(Material.BIRCH_LOG, 8),
            new PhaseBlock(Material.SPRUCE_LOG, 8),
            new PhaseBlock(Material.JUNGLE_LOG, 6),
            new PhaseBlock(Material.ACACIA_LOG, 6),
            new PhaseBlock(Material.DARK_OAK_LOG, 6),
            new PhaseBlock(Material.MANGROVE_LOG, 6),
            new PhaseBlock(Material.CHERRY_LOG, 5),
            new PhaseBlock(Material.PALE_OAK_LOG, 5),
            new PhaseBlock(Material.CRIMSON_STEM, 5),
            new PhaseBlock(Material.WARPED_STEM, 5),
            new PhaseBlock(Material.BAMBOO_BLOCK, 4),
            // --- leaves ---
            new PhaseBlock(Material.OAK_LEAVES, 5),
            new PhaseBlock(Material.BIRCH_LEAVES, 4),
            new PhaseBlock(Material.SPRUCE_LEAVES, 4),
            new PhaseBlock(Material.JUNGLE_LEAVES, 3),
            new PhaseBlock(Material.ACACIA_LEAVES, 3),
            new PhaseBlock(Material.DARK_OAK_LEAVES, 3),
            new PhaseBlock(Material.MANGROVE_LEAVES, 3),
            new PhaseBlock(Material.CHERRY_LEAVES, 2),
            new PhaseBlock(Material.PALE_OAK_LEAVES, 2),
            new PhaseBlock(Material.AZALEA_LEAVES, 2),
            new PhaseBlock(Material.FLOWERING_AZALEA_LEAVES, 1),
            new PhaseBlock(Material.NETHER_WART_BLOCK, 2),
            new PhaseBlock(Material.WARPED_WART_BLOCK, 2),
            // --- ores (rarer) ---
            new PhaseBlock(Material.COAL_ORE, 8),
            new PhaseBlock(Material.DEEPSLATE_COAL_ORE, 4),
            new PhaseBlock(Material.IRON_ORE, 8),
            new PhaseBlock(Material.DEEPSLATE_IRON_ORE, 4),
            new PhaseBlock(Material.COPPER_ORE, 6),
            new PhaseBlock(Material.DEEPSLATE_COPPER_ORE, 3),
            new PhaseBlock(Material.GOLD_ORE, 4),
            new PhaseBlock(Material.DEEPSLATE_GOLD_ORE, 2),
            new PhaseBlock(Material.REDSTONE_ORE, 4),
            new PhaseBlock(Material.DEEPSLATE_REDSTONE_ORE, 2),
            new PhaseBlock(Material.LAPIS_ORE, 3),
            new PhaseBlock(Material.DEEPSLATE_LAPIS_ORE, 2),
            new PhaseBlock(Material.DIAMOND_ORE, 2),
            new PhaseBlock(Material.DEEPSLATE_DIAMOND_ORE, 1),
            new PhaseBlock(Material.EMERALD_ORE, 2),
            new PhaseBlock(Material.DEEPSLATE_EMERALD_ORE, 1),
            new PhaseBlock(Material.NETHER_QUARTZ_ORE, 3),
            new PhaseBlock(Material.NETHER_GOLD_ORE, 2),
            new PhaseBlock(Material.ANCIENT_DEBRIS, 1),
            // --- food + plants ---
            new PhaseBlock(Material.HAY_BLOCK, 3),
            new PhaseBlock(Material.PUMPKIN, 4),
            new PhaseBlock(Material.MELON, 4),
            new PhaseBlock(Material.SWEET_BERRY_BUSH, 2),
            new PhaseBlock(Material.CACTUS, 3),
            new PhaseBlock(Material.SUGAR_CANE, 3),
            new PhaseBlock(Material.KELP, 2),
            // --- specials ---
            new PhaseBlock(Material.AMETHYST_BLOCK, 3),
            new PhaseBlock(Material.GLOWSTONE, 3),
            new PhaseBlock(Material.MAGMA_BLOCK, 2),
            new PhaseBlock(Material.OBSIDIAN, 2),
            new PhaseBlock(Material.SCULK, 1),
            new PhaseBlock(Material.RED_MUSHROOM_BLOCK, 1),
            new PhaseBlock(Material.BROWN_MUSHROOM_BLOCK, 1),
            new PhaseBlock(Material.PURPUR_BLOCK, 1),
            // --- building stones (variant family) ---
            new PhaseBlock(Material.SANDSTONE, 6),
            new PhaseBlock(Material.RED_SANDSTONE, 3),
            new PhaseBlock(Material.SMOOTH_STONE, 4),
            new PhaseBlock(Material.SMOOTH_SANDSTONE, 2),
            new PhaseBlock(Material.STONE_BRICKS, 4),
            new PhaseBlock(Material.MOSSY_STONE_BRICKS, 2),
            new PhaseBlock(Material.DEEPSLATE_BRICKS, 3),
            new PhaseBlock(Material.POLISHED_DEEPSLATE, 3),
            new PhaseBlock(Material.POLISHED_GRANITE, 2),
            new PhaseBlock(Material.POLISHED_DIORITE, 2),
            new PhaseBlock(Material.POLISHED_ANDESITE, 2),
            new PhaseBlock(Material.POLISHED_TUFF, 2),
            new PhaseBlock(Material.POLISHED_BLACKSTONE, 2),
            new PhaseBlock(Material.GILDED_BLACKSTONE, 1),
            new PhaseBlock(Material.NETHER_BRICKS, 3),
            new PhaseBlock(Material.RED_NETHER_BRICKS, 1),
            new PhaseBlock(Material.END_STONE_BRICKS, 2),
            new PhaseBlock(Material.PRISMARINE, 2),
            new PhaseBlock(Material.TERRACOTTA, 3),
            // --- metal / raw blocks ---
            new PhaseBlock(Material.RAW_IRON_BLOCK, 2),
            new PhaseBlock(Material.RAW_COPPER_BLOCK, 2),
            new PhaseBlock(Material.RAW_GOLD_BLOCK, 1),
            new PhaseBlock(Material.IRON_BLOCK, 1),
            new PhaseBlock(Material.COPPER_BLOCK, 1),
            new PhaseBlock(Material.GOLD_BLOCK, 1),
            new PhaseBlock(Material.DIAMOND_BLOCK, 1),
            new PhaseBlock(Material.EMERALD_BLOCK, 1),
            // --- crops + naturals ---
            new PhaseBlock(Material.WHEAT, 2),
            new PhaseBlock(Material.CARROTS, 2),
            new PhaseBlock(Material.POTATOES, 2),
            new PhaseBlock(Material.BEETROOTS, 1),
            new PhaseBlock(Material.SUSPICIOUS_SAND, 1),
            new PhaseBlock(Material.SUSPICIOUS_GRAVEL, 1),
            new PhaseBlock(Material.HONEYCOMB_BLOCK, 1),
            new PhaseBlock(Material.SHROOMLIGHT, 1),
            new PhaseBlock(Material.SEA_LANTERN, 1),
            new PhaseBlock(Material.DRIED_KELP_BLOCK, 1),
            new PhaseBlock(Material.MUSHROOM_STEM, 1),
            // --- rare loot (very tail) ---
            new PhaseBlock(Material.CHEST, 5),
            new PhaseBlock(Material.ENDER_CHEST, 1),
            new PhaseBlock(Material.SCULK_CATALYST, 1),
            new PhaseBlock(Material.BEACON, 1),
            new PhaseBlock(Material.CONDUIT, 1),
            new PhaseBlock(Material.SHULKER_BOX, 1)
    );
    private static final int COMMUNITY_TOTAL_WEIGHT;
    static {
        int sum = 0;
        for (PhaseBlock b : COMMUNITY_BLOCKS) sum += b.effectiveWeight();
        COMMUNITY_TOTAL_WEIGHT = sum;
    }

    private static Material rollCommunityBlock(Random rng) {
        if (COMMUNITY_TOTAL_WEIGHT <= 0) return Material.STONE;
        int roll = rng.nextInt(COMMUNITY_TOTAL_WEIGHT);
        int acc = 0;
        for (PhaseBlock b : COMMUNITY_BLOCKS) {
            acc += b.effectiveWeight();
            if (roll < acc) return b.material();
        }
        return COMMUNITY_BLOCKS.get(COMMUNITY_BLOCKS.size() - 1).material();
    }

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
            center.getBlock().setType(rollCommunityBlock(ThreadLocalRandom.current()), false);
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
        // Distribution: paxel holders get telekinesis + auto-smelt (parity with island
        // mining); with auto-sell on, priced drops convert straight to coins instead of
        // filling the inventory. Anything unpriced still reaches the player so rares are
        // never lost. Non-paxel players keep the classic ground-drop behaviour.
        var prog = plugin.progression().get(player);
        ItemStack hand = player.getInventory().getItemInMainHand();
        boolean paxel = plugin.paxels().isOwner(hand, player);
        boolean autoSell = cfg.getBoolean("community.autosell.enabled", true) && prog.isAutoSellEnabled();
        boolean coinRush = plugin.seasons().active() == SeasonManager.ServerEvent.DOUBLE_COINS;

        List<ItemStack> drops = new ArrayList<>();
        // Container contents (chest etc.) drop un-smelted, matching the island path.
        if (event.getBlock().getState() instanceof org.bukkit.block.Container container) {
            for (var content : container.getInventory().getContents()) {
                if (content == null || content.getType().isAir()) continue;
                drops.add(content.clone());
            }
            container.getInventory().clear();
        }
        for (var drop : event.getBlock().getDrops(hand)) {
            if (drop == null || drop.getType().isAir()) continue;
            drops.add(paxel ? plugin.paxels().maybeSmelt(drop) : drop);
        }

        // Auto-sell at xEconomy's LIVE market price (cents, fluctuates with supply/
        // demand). Sell a drop only when the market returns a price > 0 (sellable and
        // not sell-blacklisted, e.g. PAPER); anything unsellable — or everything, if
        // xEconomy/Market is unavailable — is kept and routed to the deposit chest.
        dev.xsuite.economy.api.Market market = autoSell ? dev.xsuite.economy.api.XEconomy.market() : null;
        long autoSellCents = 0;
        List<ItemStack> toGive = new ArrayList<>();
        for (ItemStack drop : drops) {
            long cents = market != null ? market.quoteSell(drop.getType(), drop.getAmount()) : 0L;
            if (cents > 0) autoSellCents += cents;
            else toGive.add(drop);
        }
        if (coinRush) autoSellCents *= 2;
        if (autoSellCents > 0) {
            plugin.economy().depositCents(player, autoSellCents);
            // Shown on the action bar below (folded into the pool line so it isn't clobbered).
        }
        // Coins earned, rounded for the action-bar display only (the deposit above is exact cents).
        long autoSellEarned = (autoSellCents + 50) / 100;

        // Route the remaining drops through the player's linked deposit chest first;
        // whatever it can't take falls back to today's paxel/ground behaviour.
        List<ItemStack> afterChest = new ArrayList<>();
        for (ItemStack drop : toGive) {
            ItemStack left = plugin.depositChests().deposit(player, drop);
            if (left != null && !left.getType().isAir()) afterChest.add(left);
        }
        if (paxel) {
            plugin.paxels().deliver(player, event.getBlock(), afterChest, false); // already smelted above
        } else {
            for (ItemStack drop : afterChest) center.getWorld().dropItemNaturally(loc, drop);
        }

        // Diamond Hour: ~1/5 chance to also yield a bonus diamond (mirrors the island path).
        if (plugin.seasons().active() == SeasonManager.ServerEvent.DIAMOND_HOUR
                && ThreadLocalRandom.current().nextInt(5) == 0) {
            ItemStack dia = new ItemStack(Material.DIAMOND);
            if (paxel) plugin.paxels().deliver(player, event.getBlock(), java.util.List.of(dia), false);
            else center.getWorld().dropItemNaturally(loc, dia);
            Msg.actionBar(player, "<aqua>★ Diamond Hour bonus!");
        }

        // Pick the next block from upcoming queue or roll fresh from the community pool
        // (phase-independent so the community block is a stable resource source).
        Phase phase = plugin.phases().getOrLast(phaseIndex);
        Material next = upcoming.pollFirst();
        if (next == null) next = rollCommunityBlock(ThreadLocalRandom.current());
        // Lush Bloom: ~1/6 chance the next block is a lush block (mirrors the island path).
        if (plugin.seasons().active() == SeasonManager.ServerEvent.LUSH_BLOOM
                && ThreadLocalRandom.current().nextInt(6) == 0) {
            Material[] lush = {Material.MOSS_BLOCK, Material.AZALEA, Material.FLOWERING_AZALEA,
                    Material.BIG_DRIPLEAF, Material.SMALL_DRIPLEAF};
            next = lush[ThreadLocalRandom.current().nextInt(lush.length)];
        }
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
        if (coinRush) contributionThisBreak *= 2; // Coin Rush boosts the shared pool too.
        if (contributionThisBreak > 0) {
            contributionByPlayer.merge(player.getUniqueId(), contributionThisBreak, Long::sum);
        }

        // Refill the prophecy queue lazily from the same community pool.
        while (upcoming.size() < 5) upcoming.addLast(rollCommunityBlock(ThreadLocalRandom.current()));

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
                // Shared phase advanced — bump every online player's paxel tier (community
                // phase folds into PaxelManager.tierFor via max()).
                plugin.paxels().refreshAllTiers();
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
        String sold = autoSellEarned > 0 ? " · <gold>+" + autoSellEarned + "<gray> sold" : "";
        Msg.actionBar(player, "<gray>+<gold>" + contributionThisBreak + "<gray> to pool · <yellow>"
                + pool() + "<gray> total" + sold);
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
        // Only announce server-wide when it's an actual community event (2+ winners).
        // A lone contributor getting their own coins back already gets the personal
        // message above; broadcasting it spammed global chat.
        if (qualifying.size() >= 2) {
            Bukkit.broadcast(Msg.mm("<gold>✦ <yellow>Community pool paid out <white>" + distributed
                    + " <yellow>coins <gray>to " + qualifying.size() + " contributors."));
        }
        return distributed;
    }

    /** Trigger payout if either threshold is met. Returns true if a payout fired. */
    public boolean tickPayoutIfDue() {
        var cfg = plugin.getConfig();
        long blockThreshold = Math.max(1, cfg.getLong("community.block.payout.block-threshold", 500));
        long intervalMs = Math.max(60_000L,
                cfg.getLong("community.block.payout.interval-minutes", 60) * 60_000L);
        long floor = Math.max(0, cfg.getLong("community.block.payout.min-contribution-coins", 25));

        // Trigger on the QUALIFYING pool only — coins from contributors at or above
        // the floor, i.e. what would actually be paid this tick. Sub-floor coins stay
        // parked in the map indefinitely; counting them (as the old pool() check did)
        // kept the threshold permanently satisfied and fired a tiny payout every few
        // breaks, spamming chat.
        long qualifyingPool = 0;
        for (long v : contributionByPlayer.values()) if (v >= floor) qualifyingPool += v;

        long sinceLast = System.currentTimeMillis() - lastPayoutAt;
        if (sinceLast < intervalMs && qualifyingPool < blockThreshold) return false;

        long distributed = payout();
        // Reset the timer whenever a payout was due — even if nothing qualified — so
        // the interval branch can't re-fire on every subsequent break.
        lastPayoutAt = System.currentTimeMillis();
        return distributed > 0;
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

    /** Scaled block requirement for the community block's current phase (for UI/scoreboard). */
    public int requiredForCurrentPhase() {
        Phase phase = plugin.phases().getOrLast(phaseIndex);
        return phase == null ? Integer.MAX_VALUE : scaledRequiredBlocks(phase);
    }

}
