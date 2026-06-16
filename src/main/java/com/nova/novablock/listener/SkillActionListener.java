package com.nova.novablock.listener;

import com.nova.novablock.NovaBlock;
import com.nova.novablock.ability.ActiveAbility;
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
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.ItemStack;

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

    private static final int TREE_FELLER_MAX = 80;

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

    // ---- Farming -----------------------------------------------------------

    private boolean isCrop(Block block) {
        if (!(block.getBlockData() instanceof Ageable age)) return false;
        // Only fully-grown crops count, mcMMO-style.
        return age.getAge() >= age.getMaximumAge() && age.getMaximumAge() > 0;
    }

    private void handleFarming(Player p, PlayerProgression prog, Block block, Material m) {
        plugin.progression().addXp(p, SkillType.FARMING, SkillEffects.xpPerAction(SkillType.FARMING));

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

    private void fellTree(Player p, PlayerProgression prog, Block origin, Material tool) {
        Set<Block> logs = new HashSet<>();
        Deque<Block> frontier = new ArrayDeque<>();
        frontier.add(origin);
        while (!frontier.isEmpty() && logs.size() < TREE_FELLER_MAX) {
            Block b = frontier.poll();
            if (!logs.add(b)) continue;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) continue;
                        Block n = b.getRelative(dx, dy, dz);
                        if (Tag.LOGS.isTagged(n.getType()) && !logs.contains(n)) frontier.add(n);
                    }
                }
            }
        }
        ItemStack toolItem = p.getInventory().getItemInMainHand();
        for (Block log : logs) {
            if (log.equals(origin)) continue; // origin is broken by the vanilla event
            log.breakNaturally(toolItem);
        }
        plugin.progression().addXp(p, SkillType.WOODCUTTING,
                SkillEffects.xpPerAction(SkillType.WOODCUTTING) * Math.max(0, logs.size() - 1));
        if (Perk.hasPerk(prog, Perk.ARBORIST)) {
            origin.getWorld().dropItemNaturally(origin.getLocation().add(0.5, 0.5, 0.5),
                    new ItemStack(saplingFor(origin.getType())));
        }
        Msg.actionBar(p, "<#D9A066>Tree Feller! <gray>" + logs.size() + " logs");
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
