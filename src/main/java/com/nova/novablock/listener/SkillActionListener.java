package com.nova.novablock.listener;

import com.nova.novablock.NovaBlock;
import com.nova.novablock.ability.ActiveAbility;
import com.nova.novablock.boss.BossManager;
import com.nova.novablock.progression.Passives;
import com.nova.novablock.progression.Perk;
import com.nova.novablock.progression.PlayerProgression;
import com.nova.novablock.progression.SkillEffects;
import com.nova.novablock.progression.SkillType;
import com.nova.novablock.util.Msg;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Awards XP and applies passives/abilities for the gathering skills (Farming,
 * Woodcutting, Excavation, Mining-off-OneBlock) and Fishing. Runs at MONITOR with
 * ignoreCancelled, so the cancelled OneBlock-centre break (handled in
 * {@link BlockListener}) is skipped entirely — no interference with that flow.
 *
 * <p>Also awards Combat XP on any player kill — hostile mobs, animals, naturally
 * spawned and OneBlock-spawned alike, plus PvP — via {@link #onKill}. Bosses are
 * left to {@link BossManager}, which grants their own (larger) Combat reward.
 */
public class SkillActionListener implements Listener {

    private final NovaBlock plugin;

    /** Blocks that count as Excavation (dug with a shovel). */
    private static final Set<Material> EXCAVATION = EnumSet.of(
            Material.DIRT, Material.GRASS_BLOCK, Material.COARSE_DIRT, Material.ROOTED_DIRT,
            Material.PODZOL, Material.MYCELIUM, Material.DIRT_PATH, Material.FARMLAND,
            Material.SAND, Material.RED_SAND, Material.GRAVEL, Material.CLAY, Material.MUD,
            Material.SOUL_SAND, Material.SOUL_SOIL, Material.SNOW_BLOCK, Material.SNOW,
            Material.MOSS_BLOCK, Material.MUDDY_MANGROVE_ROOTS);

    /** Core stone/ore set that counts as Mining (off the OneBlock centre). */
    private static final Set<Material> MINING_EXTRA = EnumSet.of(
            Material.STONE, Material.COBBLESTONE, Material.DEEPSLATE, Material.COBBLED_DEEPSLATE,
            Material.GRANITE, Material.DIORITE, Material.ANDESITE, Material.TUFF, Material.CALCITE,
            Material.NETHERRACK, Material.BASALT, Material.BLACKSTONE, Material.END_STONE,
            Material.OBSIDIAN, Material.ANCIENT_DEBRIS, Material.AMETHYST_BLOCK);

    // Tree Feller now chops the ENTIRE connected structure — a whole tree plus any
    // neighbouring trees whose logs or canopies touch it, leaves included — rather
    // than a single trunk. Because that can be a lot of blocks, the fell is spread
    // over ticks: a repeating task breaks a small batch each tick (≈50 ms) instead of
    // everything in one server frame, so felling a stand of trees doesn't stall the server.
    //
    // TREE_FELLER_MAX  — hard ceiling on logs felled in one activation (grief/lag guard).
    // FELL_BATCH       — blocks (logs or leaves) broken per tick (a couple dozen).
    // FELL_STEP_LIMIT  — max blocks examined per tick (bounds work through big leaf canopies
    //                    even when few of them turn out to be breakable logs).
    // FELL_MAX_VISITED — cap on total blocks the flood-fill will look at (memory/CPU guard
    //                    against a continuous jungle canopy chaining forever).
    private static final int TREE_FELLER_MAX = 2000;
    private static final int FELL_BATCH = 24;
    private static final int FELL_STEP_LIMIT = 512;
    private static final int FELL_MAX_VISITED = 40000;

    /** A player kill is worth this many times a mob kill's Combat XP. */
    private static final long PLAYER_KILL_XP_MULTIPLIER = 5L;

    public SkillActionListener(NovaBlock plugin) { this.plugin = plugin; }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Player p = event.getPlayer();
        if (p.getGameMode() == GameMode.CREATIVE) return;
        if (!p.hasPermission("novablock.skills")) return;

        Block block = event.getBlock();
        Material m = block.getType();
        Material tool = p.getInventory().getItemInMainHand().getType();
        PlayerProgression prog = plugin.progression().get(p);

        if (isCrop(block)) {
            handleFarming(p, prog, block, m);
        } else if (Tag.LOGS.isTagged(m) && isAxe(tool)) {
            handleWoodcutting(p, prog, block, tool);
        } else if (EXCAVATION.contains(m) && isShovel(tool)) {
            handleExcavation(p, prog, block, tool);
        } else if ((m.name().endsWith("_ORE") || MINING_EXTRA.contains(m)) && isPickaxe(tool)) {
            handleMiningExtra(p, prog, block, tool);
        }
    }

    // ---- Combat ------------------------------------------------------------

    /**
     * Grants Combat XP whenever a player gets a kill. {@link EntityDeathEvent} also
     * fires for player deaths (PvP), so this single handler covers mob kills and
     * PvP alike — players are worth {@link #PLAYER_KILL_XP_MULTIPLIER}× a mob.
     * Bosses are skipped so {@link BossManager}'s dedicated reward isn't doubled.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onKill(EntityDeathEvent event) {
        LivingEntity dead = event.getEntity();
        Player killer = dead.getKiller();
        if (killer == null) return;
        if (killer.getGameMode() == GameMode.CREATIVE) return;
        if (!killer.hasPermission("novablock.skills")) return;
        // The boss itself awards its own Combat XP in BossManager; don't stack on top.
        if (dead.getPersistentDataContainer().has(BossManager.BOSS_KEY, PersistentDataType.STRING)) return;

        long xp = SkillEffects.xpPerAction(SkillType.COMBAT);
        if (dead instanceof Player) xp *= PLAYER_KILL_XP_MULTIPLIER;
        plugin.progression().addXp(killer, SkillType.COMBAT, xp);
    }

    // ---- Farming -----------------------------------------------------------

    private boolean isCrop(Block block) {
        if (!(block.getBlockData() instanceof Ageable age)) return false;
        // Only fully-grown crops count, mcMMO-style.
        return age.getAge() >= age.getMaximumAge() && age.getMaximumAge() > 0;
    }

    private void handleFarming(Player p, PlayerProgression prog, Block block, Material m) {
        plugin.progression().addXp(p, SkillType.FARMING, SkillEffects.xpPerAction(SkillType.FARMING));

        // Manual-farming rewards: build the harvest combo (sell boost + bonus XP)
        // and advance any "harvest crops" daily quest. Both fire only here — i.e.
        // only on a real hand-harvest, never for minion output.
        plugin.farmingCombo().recordHarvest(p);
        plugin.quests().onCropHarvested(p);

        boolean green = plugin.abilities().tryActivate(p, ActiveAbility.GREEN_TERRA);
        int extra = 0;
        if (plugin.abilities().isActive(p, ActiveAbility.GREEN_TERRA)) {
            extra += ActiveAbility.GREEN_TERRA.cfg().dropMultiplier() - 1;
        }
        if (Passives.roll(prog, SkillType.FARMING)) {
            extra += 1;
            if (Perk.hasPerk(prog, Perk.HARVESTER)) extra += 1;
        }
        dropExtra(block, block.getDrops(p.getInventory().getItemInMainHand()), extra);
        if (green) Msg.actionBar(p, "<green>Green Terra!");

        // GREEN_THUMB: replant the crop next tick if its soil is intact.
        if (Perk.hasPerk(prog, Perk.GREEN_THUMB)) {
            scheduleReplant(block, m);
        }
    }

    private void scheduleReplant(Block block, Material crop) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!block.getType().isAir()) return;
            Material below = block.getRelative(0, -1, 0).getType();
            boolean nether = crop == Material.NETHER_WART;
            if (nether ? below == Material.SOUL_SAND : below == Material.FARMLAND) {
                block.setType(crop, false);
            }
        });
    }

    // ---- Woodcutting -------------------------------------------------------

    private void handleWoodcutting(Player p, PlayerProgression prog, Block block, Material tool) {
        // Player-placed logs don't feed the skill: no XP, no passives, and no Tree
        // Feller — otherwise placing logs farms the skill and felling wipes builds.
        // The block is being broken now, so forget it (its slot is free again).
        if (plugin.placedLogs().isPlaced(block)) {
            plugin.placedLogs().clearPlaced(block);
            return;
        }
        plugin.progression().addXp(p, SkillType.WOODCUTTING, SkillEffects.xpPerAction(SkillType.WOODCUTTING));

        if (plugin.abilities().tryActivate(p, ActiveAbility.TREE_FELLER)
                || plugin.abilities().isActive(p, ActiveAbility.TREE_FELLER)) {
            fellTree(p, prog, block, tool);
            return;
        }

        int extra = 0;
        if (Passives.roll(prog, SkillType.WOODCUTTING)) {
            extra += 1;
            if (Perk.hasPerk(prog, Perk.LUMBERJACK)) extra += 1;
        }
        dropExtra(block, block.getDrops(p.getInventory().getItemInMainHand()), extra);
    }

    /**
     * Fells the whole connected tree structure — every naturally-grown log reachable
     * through logs and touching leaves, so trees standing next to each other come down
     * together. Player-placed logs are never broken and act as a hard boundary the fell
     * won't cross, so log builds beside a tree are safe. The work is chunked across ticks
     * ({@link #FELL_BATCH} logs per ≈50 ms) so a big stand of trees never stalls the tick.
     */
    private void fellTree(Player p, PlayerProgression prog, Block origin, Material tool) {
        final ItemStack toolItem = p.getInventory().getItemInMainHand().clone();
        final boolean arborist = Perk.hasPerk(prog, Perk.ARBORIST);
        final Material originType = origin.getType(); // still the log at MONITOR time
        final long xpPerLog = SkillEffects.xpPerAction(SkillType.WOODCUTTING);

        // Flood-fill state, seeded from the origin's neighbours (origin itself is
        // being removed by the vanilla break event that triggered this).
        final Deque<Block> frontier = new ArrayDeque<>();
        final Set<Block> seen = new HashSet<>();
        seen.add(origin);
        queueTreeNeighbors(origin, frontier, seen);

        new BukkitRunnable() {
            int felled = 0;

            @Override public void run() {
                int brokeThisTick = 0;   // logs + leaves broken this tick (throttle)
                int logsThisTick = 0;    // logs only (drives XP)
                int steps = 0;
                while (!frontier.isEmpty()
                        && brokeThisTick < FELL_BATCH
                        && felled < TREE_FELLER_MAX
                        && steps < FELL_STEP_LIMIT) {
                    steps++;
                    Block b = frontier.poll();
                    Material t = b.getType();
                    boolean isLog = Tag.LOGS.isTagged(t);
                    boolean isLeaf = Tag.LEAVES.isTagged(t);
                    if (!isLog && !isLeaf) continue;            // block changed since queued
                    // A player-placed log is a build: don't break it and don't fell
                    // past it, so structures behind it are protected too.
                    if (isLog && plugin.placedLogs().isPlaced(b)) continue;

                    // Spread through both logs and touching leaves — leaves bridge the
                    // canopies of neighbouring trees so an adjacent stand comes down too.
                    queueTreeNeighbors(b, frontier, seen);

                    // Break the block (leaves too, for a clean fell) and throttle on both.
                    b.breakNaturally(toolItem);
                    brokeThisTick++;
                    if (isLog) { felled++; logsThisTick++; }
                }

                if (logsThisTick > 0 && p.isOnline()) {
                    plugin.progression().addXp(p, SkillType.WOODCUTTING, xpPerLog * logsThisTick);
                }

                if (frontier.isEmpty() || felled >= TREE_FELLER_MAX) {
                    if (felled > 0) {
                        if (arborist) {
                            // Roughly one sapling back per tree felled.
                            int saplings = Math.max(1, felled / 8);
                            Material sap = saplingFor(originType);
                            for (int i = 0; i < saplings; i++) {
                                origin.getWorld().dropItemNaturally(
                                        origin.getLocation().add(0.5, 0.5, 0.5), new ItemStack(sap));
                            }
                        }
                        if (p.isOnline()) {
                            Msg.actionBar(p, "<#D9A066>Tree Feller! <gray>" + felled + " logs");
                        }
                    }
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /**
     * Queues the log/leaf neighbours of {@code b} (26-neighbourhood) that haven't been
     * seen yet, up to {@link #FELL_MAX_VISITED} total, so the fill can't run away across
     * a continuous forest canopy.
     */
    private void queueTreeNeighbors(Block b, Deque<Block> frontier, Set<Block> seen) {
        if (seen.size() >= FELL_MAX_VISITED) return;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    Block n = b.getRelative(dx, dy, dz);
                    if (seen.contains(n)) continue;
                    Material t = n.getType();
                    if (Tag.LOGS.isTagged(t) || Tag.LEAVES.isTagged(t)) {
                        seen.add(n);
                        frontier.add(n);
                        if (seen.size() >= FELL_MAX_VISITED) return;
                    }
                }
            }
        }
    }

    private Material saplingFor(Material log) {
        String n = log.name();
        if (n.contains("SPRUCE")) return Material.SPRUCE_SAPLING;
        if (n.contains("BIRCH")) return Material.BIRCH_SAPLING;
        if (n.contains("JUNGLE")) return Material.JUNGLE_SAPLING;
        if (n.contains("ACACIA")) return Material.ACACIA_SAPLING;
        if (n.contains("DARK_OAK")) return Material.DARK_OAK_SAPLING;
        if (n.contains("CHERRY")) return Material.CHERRY_SAPLING;
        if (n.contains("MANGROVE")) return Material.MANGROVE_PROPAGULE;
        return Material.OAK_SAPLING;
    }

    // ---- Excavation --------------------------------------------------------

    private void handleExcavation(Player p, PlayerProgression prog, Block block, Material tool) {
        plugin.progression().addXp(p, SkillType.EXCAVATION, SkillEffects.xpPerAction(SkillType.EXCAVATION));

        plugin.abilities().tryActivate(p, ActiveAbility.GIGA_DRILL);
        int extra = 0;
        if (plugin.abilities().isActive(p, ActiveAbility.GIGA_DRILL)) {
            extra += ActiveAbility.GIGA_DRILL.cfg().dropMultiplier() - 1;
        }
        if (Passives.roll(prog, SkillType.EXCAVATION)) {
            extra += 1;
            if (Perk.hasPerk(prog, Perk.EXCAVATOR)) extra += 1;
        }
        dropExtra(block, block.getDrops(p.getInventory().getItemInMainHand()), extra);

        // ARCHAEOLOGY: small chance to unearth treasure while digging.
        if (Perk.hasPerk(prog, Perk.ARCHAEOLOGY) && ThreadLocalRandom.current().nextInt(40) == 0) {
            block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.5, 0.5),
                    new ItemStack(excavationTreasure()));
            Msg.actionBar(p, "<#E0C068>Archaeology! <gray>You unearthed treasure.");
        }
    }

    private Material excavationTreasure() {
        Material[] pool = {Material.GOLD_NUGGET, Material.EMERALD, Material.DIAMOND,
                Material.IRON_NUGGET, Material.AMETHYST_SHARD, Material.NAME_TAG};
        return pool[ThreadLocalRandom.current().nextInt(pool.length)];
    }

    // ---- Mining (off the OneBlock centre) ----------------------------------

    private void handleMiningExtra(Player p, PlayerProgression prog, Block block, Material tool) {
        plugin.progression().addXp(p, SkillType.MINING, SkillEffects.xpPerAction(SkillType.MINING));

        plugin.abilities().tryActivate(p, ActiveAbility.SUPER_BREAKER);
        int extra = 0;
        if (plugin.abilities().isActive(p, ActiveAbility.SUPER_BREAKER)) {
            extra += ActiveAbility.SUPER_BREAKER.cfg().dropMultiplier() - 1;
        }
        if (Passives.roll(prog, SkillType.MINING)) extra += 1;
        dropExtra(block, block.getDrops(p.getInventory().getItemInMainHand()), extra);
    }

    // ---- Fishing -----------------------------------------------------------

    @EventHandler(ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) return;
        Player p = event.getPlayer();
        if (!p.hasPermission("novablock.skills")) return;
        PlayerProgression prog = plugin.progression().get(p);
        plugin.progression().addXp(p, SkillType.FISHING, SkillEffects.xpPerAction(SkillType.FISHING));

        double chance = Passives.chance(prog, SkillType.FISHING);
        if (Perk.hasPerk(prog, Perk.ANGLERS_LUCK)) chance *= 2;
        if (chance > 0 && ThreadLocalRandom.current().nextDouble() < chance
                && event.getCaught() instanceof Item caughtItem) {
            int bonus = Perk.hasPerk(prog, Perk.TREASURE_HUNTER) ? 2 : 1;
            for (int i = 0; i < bonus; i++) {
                p.getWorld().dropItemNaturally(caughtItem.getLocation(),
                        new ItemStack(fishingTreasure()));
            }
            Msg.actionBar(p, "<#38BDF8>Treasure! <gray>Your line snagged something extra.");
        }
    }

    private Material fishingTreasure() {
        Material[] pool = {Material.NAUTILUS_SHELL, Material.PRISMARINE_SHARD, Material.PRISMARINE_CRYSTALS,
                Material.INK_SAC, Material.LILY_PAD, Material.EMERALD, Material.HEART_OF_THE_SEA};
        return pool[ThreadLocalRandom.current().nextInt(pool.length)];
    }

    // ---- helpers -----------------------------------------------------------

    private void dropExtra(Block block, Collection<ItemStack> drops, int sets) {
        if (sets <= 0 || drops.isEmpty()) return;
        var loc = block.getLocation().add(0.5, 0.5, 0.5);
        for (int i = 0; i < sets; i++) {
            for (ItemStack drop : drops) {
                if (drop != null && !drop.getType().isAir()) {
                    block.getWorld().dropItemNaturally(loc, drop.clone());
                }
            }
        }
    }

    private boolean isPickaxe(Material m) { return m.name().endsWith("_PICKAXE"); }
    private boolean isShovel(Material m) { return m.name().endsWith("_SHOVEL"); }
    private boolean isAxe(Material m) { return m.name().endsWith("_AXE"); }
}
