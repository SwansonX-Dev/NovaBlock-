package com.nova.novablock.listener;

import com.nova.novablock.NovaBlock;
import com.nova.novablock.island.Island;
import com.nova.novablock.phase.Phase;
import com.nova.novablock.progression.Perk;
import com.nova.novablock.progression.SkillType;
import com.nova.novablock.season.SeasonManager;
import com.nova.novablock.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class BlockListener implements Listener {

    private final NovaBlock plugin;
    private static final Set<Material> UNSAFE_ONEBLOCK_MATERIALS = EnumSet.of(
            Material.BEDROCK,
            Material.BARRIER,
            Material.COMMAND_BLOCK,
            Material.CHAIN_COMMAND_BLOCK,
            Material.REPEATING_COMMAND_BLOCK,
            Material.STRUCTURE_BLOCK,
            Material.STRUCTURE_VOID,
            Material.JIGSAW,
            Material.LIGHT,
            Material.END_PORTAL,
            Material.END_PORTAL_FRAME,
            Material.NETHER_PORTAL,
            Material.REINFORCED_DEEPSLATE,
            Material.MOVING_PISTON,
            Material.PISTON_HEAD
    );
    /** Per-island guard so a Bedrock double-break (two BREAK packets within the same tick) collapses to one. */
    private final Map<UUID, Long> recentBreakTick = new HashMap<>();
    private final NamespacedKey oneBlockLootKey;

    public BlockListener(NovaBlock plugin) {
        this.plugin = plugin;
        this.oneBlockLootKey = new NamespacedKey(plugin, "oneblock_loot_filled");
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCommunityBreak(BlockBreakEvent event) {
        if (plugin.community() == null) return;
        Player player = event.getPlayer();
        Location loc = event.getBlock().getLocation();
        if (plugin.community().isAnchorBlock(loc)) {
            event.setCancelled(true);
            Msg.actionBar(player, "<red>Can't break the community block's bedrock anchor.");
            return;
        }
        if (plugin.community().isCommunityBlock(loc)) {
            event.setCancelled(true);
            plugin.community().handleBreak(player, event);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        Location loc = block.getLocation();

        // Community hub branch — handled before island lookup since the hub sits in
        // the main spawn world, not an island slot.
        if (plugin.community() != null) {
            if (plugin.community().isAnchorBlock(loc)) {
                event.setCancelled(true);
                Msg.actionBar(player, "<red>Can't break the community block's bedrock anchor.");
                return;
            }
            if (plugin.community().isCommunityBlock(loc)) {
                event.setCancelled(true);
                plugin.community().handleBreak(player, event);
                return;
            }
        }

        Island island = plugin.islands().atLocation(loc);
        if (island == null) return;

        boolean nether = loc.getWorld() != null
                && loc.getWorld().getName().equals(plugin.worlds().netherWorldName())
                && island.isNetherUnlocked();
        Location center = nether ? island.netherCenterBlock() : island.centerBlock();
        // Always keep a bedrock anchor under the center — replace anything that isn't bedrock there.
        if (loc.getBlockX() == center.getBlockX()
                && loc.getBlockY() == center.getBlockY() - 1
                && loc.getBlockZ() == center.getBlockZ()) {
            event.setCancelled(true);
            Msg.actionBar(player, "<red>Can't break the bedrock anchor.");
            return;
        }
        if (loc.getBlockX() != center.getBlockX()
                || loc.getBlockY() != center.getBlockY()
                || loc.getBlockZ() != center.getBlockZ()) {
            return; // some other block on the island — fine to break
        }

        if (!island.isMember(player)) {
            event.setCancelled(true);
            Msg.actionBar(player, "<red>This isn't your island.");
            return;
        }

        // Double-break debounce: if we already handled a break for this island in the same tick,
        // suppress the duplicate (Bedrock/Geyser sometimes sends the packet twice).
        long tick = Bukkit.getServer().getCurrentTick();
        UUID islandId = island.data().getId();
        Long last = recentBreakTick.get(islandId);
        if (last != null && last == tick) {
            event.setCancelled(true);
            return;
        }
        // Evict stale entries (older than ~5s) so the map doesn't grow unbounded over server uptime.
        if (recentBreakTick.size() > 64) {
            long cutoff = tick - 100L;
            recentBreakTick.values().removeIf(t -> t < cutoff);
        }
        recentBreakTick.put(islandId, tick);

        Material broken = block.getType();

        // FATE_THIEF (Luck 20): 1% chance any block converts into a diamond drop.
        boolean fateThief = Perk.hasPerk(plugin.progression().get(player), Perk.FATE_THIEF)
                && ThreadLocalRandom.current().nextInt(100) == 0;
        // LUCKY_BREAK (Mining 5): 5% chance to double drops.
        boolean luckyBreak = Perk.hasPerk(plugin.progression().get(player), Perk.LUCKY_BREAK)
                && ThreadLocalRandom.current().nextInt(20) == 0;
        // DEEP_VEIN (Mining 10): +1 of the same item when the block is an ore.
        boolean deepVein = Perk.hasPerk(plugin.progression().get(player), Perk.DEEP_VEIN)
                && isOreLike(broken);

        // Take over the break ourselves: cancel vanilla, drop manually, then replace immediately
        // in the same tick so there's never a moment when the block is AIR. This fixes
        // "Bedrock has to break it twice" (player saw the gap and swung again).
        event.setCancelled(true);
        if (fateThief) {
            // Convert the natural drop to a diamond and skip the regular drop path.
            block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.5, 0.5),
                    new ItemStack(Material.DIAMOND));
            Msg.actionBar(player, "<aqua>★ Fate Thief! <gray>The block crystallised.");
            plugin.progression().addXp(player, SkillType.LUCK, 8L);
        } else {
            dropNaturally(block, player, player.getInventory().getItemInMainHand());
            if (luckyBreak) {
                dropNaturally(block, player, player.getInventory().getItemInMainHand());
                Msg.actionBar(player, "<green>✦ Lucky Break! <gray>(double drops)");
            }
            if (deepVein) {
                block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.5, 0.5),
                        new ItemStack(broken));
            }
        }
        playBreakSound(block);

        // Phase-specific drops & bonuses
        handleBlockEvents(player, island, broken);

        Phase phase = nether
                ? plugin.phases().getNetherOrLast(island.data().getNetherPhaseIndex())
                : plugin.phases().getOrLast(island.data().getPhaseIndex());
        if (phase == null) return;

        // Pull planned next material from the prophecy queue so previews are honest.
        // Nether breaks bypass the queue (prophecy is Overworld-themed in v1).
        Material next = nether ? null : island.pollNext();
        if (next == null) next = phase.rollBlock(ThreadLocalRandom.current());
        // FOUR_LEAF (Luck 5): +5% chance to reroll a non-rare next-block into a rare one.
        if (!plugin.prophecies().isRare(next)
                && Perk.hasPerk(plugin.progression().get(player), Perk.FOUR_LEAF)
                && ThreadLocalRandom.current().nextInt(20) == 0) {
            for (int attempt = 0; attempt < 6; attempt++) {
                Material candidate = phase.rollBlock(ThreadLocalRandom.current());
                if (plugin.prophecies().isRare(candidate)) { next = candidate; break; }
            }
        }
        next = safeOneBlockMaterial(next, phase);
        // Lush Bloom event: occasionally substitute in a lush block regardless of phase
        if (plugin.seasons().active() == SeasonManager.ServerEvent.LUSH_BLOOM
                && ThreadLocalRandom.current().nextInt(6) == 0) {
            Material[] lush = {Material.MOSS_BLOCK, Material.AZALEA, Material.FLOWERING_AZALEA,
                    Material.BIG_DRIPLEAF, Material.SMALL_DRIPLEAF};
            next = lush[ThreadLocalRandom.current().nextInt(lush.length)];
        }

        // Set the new block immediately — no scheduler hop, no air gap.
        // applyPhysics=false: keeps physics-dependent blocks (cactus, poppy, wheat_seeds,
        // sweet_berry_bush, dead_bush, flowering_azalea, bamboo_block, sculk_shrieker, etc.)
        // from being destroyed for "missing support" the instant they're placed onto bedrock.
        center.getBlock().setType(next, false);
        if (next == Material.CHEST) {
            fillPhaseChest(center.getBlock(), phase);
            Bukkit.getScheduler().runTask(plugin, () -> fillPhaseChest(center.getBlock(), phase));
        }
        playPlaceSound(center.getBlock());
        // Maintain the bedrock anchor underneath at all times.
        Location anchor = center.clone().add(0, -1, 0);
        if (anchor.getBlock().getType() != Material.BEDROCK) {
            anchor.getBlock().setType(Material.BEDROCK, false);
        }

        // Diamond Hour: chance to also drop an extra diamond when breaking anything.
        if (plugin.seasons().active() == SeasonManager.ServerEvent.DIAMOND_HOUR
                && ThreadLocalRandom.current().nextInt(5) == 0) {
            center.getWorld().dropItemNaturally(center.clone().add(0, 1, 0),
                    new ItemStack(Material.DIAMOND));
            Msg.actionBar(player, "<aqua>★ Diamond Hour bonus!");
        }

        if (nether) {
            island.data().incrementNetherBlocksBroken();
            island.data().incrementNetherPhaseProgress();
        } else {
            island.data().incrementBlocksBroken();
            island.data().incrementPhaseProgress();
        }
        island.recordBreak(broken);
        plugin.claimBlockRewards().recordPersonalBreak(player);
        plugin.sprint().recordBlocksBroken(island.data().getId(), 1L);
        if (plugin.community() != null) plugin.community().recordIslandBreak(player);
        // Block-break milestones — fire once at exact thresholds. Existing islands past
        // a threshold won't retroactively claim; the milestone is the act of crossing.
        // Overworld-only milestone in v1.
        if (!nether && island.data().getBlocksBroken() == 1000L) {
            plugin.economy().award(island, 5000L);
            player.getInventory().addItem(new ItemStack(Material.TOTEM_OF_UNDYING));
            plugin.progression().addXp(player, SkillType.MINING, 100L);
            Msg.title(player, "<gold>✦ 1,000 Blocks ✦",
                    "<yellow>+5000 coins · <green>Totem of Undying · <aqua>+100 XP");
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 0.8f);
            Bukkit.broadcast(Msg.mm("<gold>✦ <yellow>" + player.getName()
                    + " <gray>has mined their <gold>1,000th block<gray>!"));
        }
        double xpMult = plugin.prestige().xpMultiplier(island);
        if (island.getComboCount() >= 5) {
            int combo = island.getComboCount();
            long xp = Math.max(1L, Math.round(combo * 2L * xpMult));
            Msg.actionBar(player, "<aqua>Combo x" + combo + "! <gray>+" + xp + " XP");
            plugin.progression().addXp(player, SkillType.MINING, xp);
        }
        int xpBoost = island.data().getUpgradeLevel(com.nova.novablock.island.IslandUpgrade.XP_BOOST);
        plugin.progression().addXp(player, SkillType.MINING, (1.0 + xpBoost) * xpMult);
        if (!nether) {
            plugin.prophecies().onAdvance(island, broken);
            island.refillUpcoming(phase, com.nova.novablock.prophecy.ProphecyManager.QUEUE_SIZE);
        }

        // Quest tick (dimension-agnostic)
        plugin.quests().onBlockBroken(player, broken);
        plugin.seasonalPaths().award(player, com.nova.novablock.season.SeasonalPathManager.PathSource.MINING, 1);

        // Paxel XP — progresses tool tier as the player levels Mining
        plugin.paxels().onMine(player, broken);

        // Anti-AFK tracker — counts OneBlock activity, prompts a chat captcha every 30 min.
        plugin.antiAfk().recordMineActivity(player);

        // Phase progression
        int progressAfter = nether ? island.data().getNetherPhaseProgress() : island.data().getPhaseProgress();
        if (progressAfter >= phase.getRequiredBlocks()) {
            if (nether) advanceNetherPhase(player, island, phase);
            else advancePhase(player, island, phase);
        }

        // Roll boss / loot room
        rollEncounters(player, island, phase, center, nether);

        // FX
        center.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, center.clone().add(0.5, 0.5, 0.5), 4, 0.2, 0.2, 0.2);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCenterInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.LEFT_CLICK_BLOCK) return;
        Block block = event.getClickedBlock();
        if (block == null || !isUnsafeOneBlockMaterial(block.getType())) return;
        Island island = plugin.islands().atLocation(block.getLocation());
        if (island == null || !isCenterBlock(island, block.getLocation())) return;
        if (!island.isMember(event.getPlayer())) return;

        event.setCancelled(true);
        Phase phase = plugin.phases().getOrLast(island.data().getPhaseIndex());
        Material replacement = safeOneBlockMaterial(Material.DEEPSLATE, phase);
        block.setType(replacement, false);
        playPlaceSound(block);
        Msg.actionBar(event.getPlayer(), "<yellow>Fixed an invalid OneBlock material.");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCenterChestOpen(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.CHEST) return;
        Island island = plugin.islands().atLocation(block.getLocation());
        if (island == null || !isCenterBlock(island, block.getLocation())) return;
        if (!island.isMember(event.getPlayer())) return;
        if (block.getState() instanceof Container container && !isLootMarked(container)) {
            Phase phase = plugin.phases().getOrLast(island.data().getPhaseIndex());
            if (phase != null) fillPhaseChest(block, phase);
        }
    }

    /**
     * Last-resort fill before the chest UI renders to the client. Catches the
     * Bedrock/Geyser case where the inventory packet beats our RIGHT_CLICK_BLOCK
     * handler and the player would otherwise see an empty chest.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChestInventoryOpen(InventoryOpenEvent event) {
        var location = event.getInventory().getLocation();
        if (location == null) return;
        Block block = location.getBlock();
        if (block.getType() != Material.CHEST) return;
        Island island = plugin.islands().atLocation(location);
        if (island == null || !isCenterBlock(island, location)) return;
        if (block.getState() instanceof Container container && !isLootMarked(container)) {
            Phase phase = plugin.phases().getOrLast(island.data().getPhaseIndex());
            if (phase != null) fillPhaseChest(block, phase);
        }
    }

    private boolean isCenterBlock(Island island, Location loc) {
        Location center = island.centerBlock();
        return loc.getBlockX() == center.getBlockX()
                && loc.getBlockY() == center.getBlockY()
                && loc.getBlockZ() == center.getBlockZ();
    }

    private Material safeOneBlockMaterial(Material material, Phase phase) {
        if (!isUnsafeOneBlockMaterial(material)) return material;
        if (phase != null) {
            for (var phaseBlock : phase.getBlocks()) {
                Material candidate = phaseBlock.material();
                if (!isUnsafeOneBlockMaterial(candidate)) return candidate;
            }
        }
        return Material.STONE;
    }

    private boolean isUnsafeOneBlockMaterial(Material material) {
        return material == null || UNSAFE_ONEBLOCK_MATERIALS.contains(material);
    }

    private void playBreakSound(Block block) {
        var group = block.getBlockData().getSoundGroup();
        block.getWorld().playSound(block.getLocation().add(0.5, 0.5, 0.5),
                group.getBreakSound(), group.getVolume(), group.getPitch());
    }

    private void playPlaceSound(Block block) {
        var group = block.getBlockData().getSoundGroup();
        // Match vanilla SoundType: volume = (raw + 1) / 2, pitch = raw * 0.8.
        float volume = (group.getVolume() + 1.0f) / 2.0f;
        float pitch = group.getPitch() * 0.8f;
        block.getWorld().playSound(block.getLocation().add(0.5, 0.5, 0.5),
                group.getPlaceSound(), volume, pitch);
    }

    /**
     * Drop the OneBlock's natural drops at its location using the tool the player
     * has. If the player is holding their paxel, auto-smelt and telekinesis the
     * drops straight into their inventory (overflow falls at the block).
     */
    private void dropNaturally(Block block, Player player, ItemStack tool) {
        var loc = block.getLocation().add(0.5, 0.5, 0.5);
        boolean paxel = plugin.paxels().isPaxel(tool);
        // If this is the OneBlock center chest and it never got filled (spawn-time
        // fill raced with tile-entity init), fill it now so breaking it always pays out.
        if (block.getType() == Material.CHEST) {
            Island island = plugin.islands().atLocation(block.getLocation());
            if (island != null && isCenterBlock(island, block.getLocation())) {
                Phase phase = plugin.phases().getOrLast(island.data().getPhaseIndex());
                if (phase != null) fillPhaseChest(block, phase);
            }
        }
        if (block.getState() instanceof Container container) {
            for (ItemStack content : container.getInventory().getContents()) {
                if (content == null || content.getType().isAir()) continue;
                giveOrDrop(player, block, loc, content, paxel);
            }
            container.getInventory().clear();
        }
        for (ItemStack drop : block.getDrops(tool)) {
            if (paxel) drop = plugin.paxels().maybeSmelt(drop);
            giveOrDrop(player, block, loc, drop, paxel);
        }
    }

    private void giveOrDrop(Player player, Block block, Location loc, ItemStack item, boolean paxel) {
        if (paxel) {
            var overflow = player.getInventory().addItem(item);
            for (ItemStack leftover : overflow.values()) {
                block.getWorld().dropItemNaturally(loc, leftover);
            }
        } else {
            block.getWorld().dropItemNaturally(loc, item);
        }
    }

    private void fillPhaseChest(Block block, Phase phase) {
        if (block.getType() != Material.CHEST || !(block.getState() instanceof Container container)) return;
        if (isLootMarked(container)) return;
        var inventory = container.getSnapshotInventory();
        inventory.clear();
        List<ItemStack> loot = phaseLoot(phase);
        var rng = ThreadLocalRandom.current();
        for (ItemStack item : loot) {
            if (item == null || item.getAmount() <= 0) continue;
            int slot;
            int guard = 0;
            do {
                slot = rng.nextInt(inventory.getSize());
                guard++;
            } while (inventory.getItem(slot) != null && guard < 40);
            inventory.setItem(slot, item);
        }
        if (container instanceof org.bukkit.block.TileState tile) {
            tile.getPersistentDataContainer().set(oneBlockLootKey, PersistentDataType.BYTE, (byte) 1);
        }
        container.update(true, false);
    }

    private boolean isLootMarked(Container container) {
        return container instanceof org.bukkit.block.TileState tile
                && tile.getPersistentDataContainer().has(oneBlockLootKey, PersistentDataType.BYTE);
    }

    private List<ItemStack> phaseLoot(Phase phase) {
        int idx = phase.getIndex();
        String id = phase.getId();
        var rng = ThreadLocalRandom.current();
        List<ItemStack> loot = new ArrayList<>();
        loot.add(stack(Material.BREAD, 2 + rng.nextInt(3)));
        loot.add(stack(Material.TORCH, 8 + rng.nextInt(9)));
        switch (id) {
            case "plains" -> {
                loot.add(stack(Material.OAK_SAPLING, 2 + rng.nextInt(3)));
                loot.add(stack(Material.WHEAT_SEEDS, 4 + rng.nextInt(5)));
                loot.add(stack(Material.CARROT, 2 + rng.nextInt(4)));
                loot.add(stack(Material.POTATO, 2 + rng.nextInt(4)));
                loot.add(stack(Material.PUMPKIN_SEEDS, 2 + rng.nextInt(3)));
                loot.add(stack(Material.MELON_SEEDS, 2 + rng.nextInt(3)));
                loot.add(stack(Material.WATER_BUCKET, 1));
                loot.add(stack(Material.BONE_MEAL, 6 + rng.nextInt(7)));
            }
            case "underground" -> {
                loot.add(stack(Material.IRON_INGOT, 6 + rng.nextInt(7)));
                loot.add(stack(Material.RAW_IRON, 8 + rng.nextInt(9)));
                loot.add(stack(Material.COAL, 12 + rng.nextInt(13)));
                loot.add(stack(Material.DIRT, 12 + rng.nextInt(9)));
                loot.add(stack(Material.BONE_MEAL, 8 + rng.nextInt(9)));
                loot.add(stack(Material.SUGAR_CANE, 2 + rng.nextInt(4)));
                loot.add(stack(Material.WATER_BUCKET, 1));
                loot.add(stack(Material.LAVA_BUCKET, 1));
            }
            case "snow" -> {
                loot.add(stack(Material.IRON_INGOT, 4 + rng.nextInt(7)));
                loot.add(stack(Material.WATER_BUCKET, 1));
                loot.add(stack(Material.LAVA_BUCKET, 1));
                loot.add(stack(Material.SPRUCE_SAPLING, 2 + rng.nextInt(3)));
                loot.add(stack(Material.BEETROOT_SEEDS, 3 + rng.nextInt(4)));
                loot.add(stack(Material.SWEET_BERRIES, 3 + rng.nextInt(5)));
                loot.add(stack(Material.SNOWBALL, 8 + rng.nextInt(9)));
            }
            case "desert" -> {
                loot.add(stack(Material.SAND, 16 + rng.nextInt(17)));
                loot.add(stack(Material.CACTUS, 2 + rng.nextInt(4)));
                loot.add(stack(Material.SUGAR_CANE, 4 + rng.nextInt(5)));
                loot.add(stack(Material.MELON_SEEDS, 2 + rng.nextInt(3)));
                loot.add(stack(Material.LAVA_BUCKET, 1));
                loot.add(stack(Material.GOLD_INGOT, 3 + rng.nextInt(5)));
            }
            case "ocean" -> {
                loot.add(stack(Material.WATER_BUCKET, 1));
                loot.add(stack(Material.KELP, 8 + rng.nextInt(9)));
                loot.add(stack(Material.PRISMARINE_SHARD, 6 + rng.nextInt(7)));
                loot.add(stack(Material.IRON_INGOT, 3 + rng.nextInt(5)));
            }
            case "nether" -> {
                loot.add(stack(Material.LAVA_BUCKET, 1));
                loot.add(stack(Material.QUARTZ, 8 + rng.nextInt(9)));
                loot.add(stack(Material.GLOWSTONE_DUST, 8 + rng.nextInt(9)));
                loot.add(stack(Material.GOLD_INGOT, 4 + rng.nextInt(5)));
            }
            case "ancient" -> {
                loot.add(stack(Material.IRON_INGOT, 10 + rng.nextInt(9)));
                loot.add(stack(Material.DIAMOND, 1 + rng.nextInt(3)));
                loot.add(stack(Material.REDSTONE, 12 + rng.nextInt(13)));
                loot.add(stack(Material.LAPIS_LAZULI, 8 + rng.nextInt(9)));
            }
            case "garden" -> {
                loot.add(stack(Material.BAMBOO, 8 + rng.nextInt(9)));
                loot.add(stack(Material.BIG_DRIPLEAF, 2 + rng.nextInt(3)));
                loot.add(stack(Material.CLAY_BALL, 8 + rng.nextInt(9)));
                loot.add(stack(Material.WATER_BUCKET, 1));
            }
            case "stronghold" -> {
                loot.add(stack(Material.ENDER_PEARL, 2 + rng.nextInt(3)));
                loot.add(stack(Material.BOOK, 3 + rng.nextInt(4)));
                loot.add(stack(Material.IRON_INGOT, 6 + rng.nextInt(7)));
                loot.add(stack(Material.OBSIDIAN, 3 + rng.nextInt(4)));
            }
            case "end" -> {
                loot.add(stack(Material.ENDER_PEARL, 4 + rng.nextInt(5)));
                loot.add(stack(Material.CHORUS_FRUIT, 6 + rng.nextInt(7)));
                loot.add(stack(Material.OBSIDIAN, 4 + rng.nextInt(5)));
                loot.add(stack(Material.PURPUR_BLOCK, 8 + rng.nextInt(9)));
            }
            case "celestial" -> {
                loot.add(stack(Material.AMETHYST_SHARD, 8 + rng.nextInt(9)));
                loot.add(stack(Material.GLOWSTONE_DUST, 12 + rng.nextInt(13)));
                loot.add(stack(Material.EXPERIENCE_BOTTLE, 4 + rng.nextInt(5)));
                loot.add(stack(Material.DIAMOND, 2 + rng.nextInt(3)));
            }
            case "void" -> {
                loot.add(stack(Material.ECHO_SHARD, 1 + rng.nextInt(2)));
                loot.add(stack(Material.ENDER_PEARL, 6 + rng.nextInt(7)));
                loot.add(stack(Material.OBSIDIAN, 8 + rng.nextInt(9)));
                loot.add(stack(Material.NETHERITE_SCRAP, 1));
            }
            default -> {}
        }
        if (idx >= 1) loot.add(stack(Material.IRON_INGOT, 3 + rng.nextInt(4)));
        return loot;
    }

    private static ItemStack stack(Material material, int amount) {
        return new ItemStack(material, Math.max(1, amount));
    }

    private void advancePhase(Player player, Island island, Phase old) {
        int nextIdx = old.getIndex() + 1;
        Phase next = plugin.phases().get(nextIdx);
        if (next == null) {
            Msg.title(player, "<gold>You've completed all phases!", "<gray>Use <yellow>/ob prestige</yellow> to keep going.");
            island.data().setPhaseProgress(old.getRequiredBlocks());
            return;
        }
        island.data().setPhaseIndex(nextIdx);
        island.data().setPhaseProgress(0);
        island.upcomingBlocks().clear();
        island.refillUpcoming(next, com.nova.novablock.prophecy.ProphecyManager.QUEUE_SIZE);
        Msg.title(player, "<" + next.getThemeColor() + ">▶ " + next.getDisplayName(),
                "<gray>Phase " + (nextIdx + 1) + " unlocked");
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        long reward = 500 + nextIdx * 250L;
        if (plugin.seasons().active() == SeasonManager.ServerEvent.DOUBLE_COINS) reward *= 2;
        reward = Math.round(reward * plugin.prestige().coinMultiplier(island));
        plugin.economy().award(island, reward);
        if (old.getBossId() != null) {
            plugin.bosses().spawn(old.getBossId(), island, player);
        }
        // Paxel tier upgrade — phase-up is the natural milestone
        plugin.paxels().refreshTier(player);
        plugin.quests().onPhaseAdvanced(player);
        plugin.seasonalPaths().award(player, com.nova.novablock.season.SeasonalPathManager.PathSource.PHASE, 125);

        // Crossing into Phase 7 unlocks the Nether dimension.
        if (nextIdx == 6 && plugin.worlds().isNetherEnabled() && !island.data().isNetherUnlocked()) {
            island.data().setNetherUnlocked(true);
            island.ensureNetherPlatform();
            String ownerName = Bukkit.getOfflinePlayer(island.data().getOwner()).getName();
            if (ownerName == null) ownerName = player.getName();
            Bukkit.broadcast(Msg.mm("<gold>✦ <yellow>" + ownerName
                    + "<gray>'s island has <red>breached the Nether<gray>! <dark_gray>(/ob home nether)"));
            Msg.title(player, "<red>The Nether is open",
                    "<gray>Use <yellow>/ob home nether</yellow> to enter.");
        }
    }

    private void advanceNetherPhase(Player player, Island island, Phase old) {
        int nextIdx = old.getIndex() + 1;
        Phase next = plugin.phases().getNether(nextIdx);
        if (next == null) {
            Msg.title(player, "<gold>You've conquered the Nether!", "<gray>Return to the Overworld for prestige.");
            island.data().setNetherPhaseProgress(old.getRequiredBlocks());
            return;
        }
        island.data().setNetherPhaseIndex(nextIdx);
        island.data().setNetherPhaseProgress(0);
        Msg.title(player, "<" + next.getThemeColor() + ">▶ " + next.getDisplayName(),
                "<gray>Nether Phase " + (nextIdx + 1) + " unlocked");
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        long reward = 500 + nextIdx * 250L;
        if (plugin.seasons().active() == SeasonManager.ServerEvent.DOUBLE_COINS) reward *= 2;
        reward = Math.round(reward * plugin.prestige().coinMultiplier(island));
        plugin.economy().award(island, reward);
        if (old.getBossId() != null) {
            plugin.bosses().spawn(old.getBossId(), island, player, true);
        }
        plugin.paxels().refreshTier(player);
        plugin.quests().onPhaseAdvanced(player);
        plugin.seasonalPaths().award(player, com.nova.novablock.season.SeasonalPathManager.PathSource.PHASE, 125);
    }

    private void rollEncounters(Player player, Island island, Phase phase, Location center, boolean nether) {
        long broken = nether ? island.data().getNetherBlocksBroken() : island.data().getBlocksBroken();
        var rng = ThreadLocalRandom.current();
        SeasonManager.ServerEvent ev = plugin.seasons().active();
        var cfg = plugin.getConfig();
        int lootCd = Math.max(1, cfg.getInt(
                nether ? "cooldowns.netherLootRoomMinBlocks" : "cooldowns.lootRoomMinBlocks",
                nether ? 400 : 150));
        int bossCd = Math.max(1, cfg.getInt(
                nether ? "cooldowns.netherBossMinBlocks" : "cooldowns.bossMinBlocks",
                300));
        long lastLootRoom = nether ? island.data().getNetherLastLootRoomAt() : island.data().getLastLootRoomAt();
        long lastBoss = nether ? island.data().getNetherLastBossAt() : island.data().getLastBossAt();

        // Mob spawn ~1/18 chance (~every ~45s of steady mining)
        if (!phase.getMobs().isEmpty() && rng.nextInt(18) == 0) {
            EntityType type = phase.rollMob(rng);
            if (type != null) {
                Entity e = center.getWorld().spawnEntity(center.clone().add(0, 1, 0), type);
                e.setMetadata("nova_natural", new org.bukkit.metadata.FixedMetadataValue(plugin, true));
            }
        }
        // Loot room every ~lootCd + denom blocks on average (config-tunable).
        // Baseline tuned for ~1 rift per ~460 blocks (cd 400 + 1/60 roll) so they
        // stay an event, not a regular interruption. Rift Storm keeps the 5x ratio.
        if (broken - lastLootRoom >= lootCd) {
            int denom = 60;
            if (ev == SeasonManager.ServerEvent.RIFT_STORM) denom = 12;
            // RIFTWALKER (Magic 10): loot rooms appear 20% more often.
            if (Perk.hasPerk(plugin.progression().get(player), Perk.RIFTWALKER)) {
                denom = Math.max(1, (int) Math.round(denom / 1.20));
            }
            // LOOT_ROOM_RATE upgrade: +10% rate per level.
            int rateLevel = island.data().getUpgradeLevel(com.nova.novablock.island.IslandUpgrade.LOOT_ROOM_RATE);
            if (rateLevel > 0) {
                denom = Math.max(1, (int) Math.round(denom / (1.0 + 0.10 * rateLevel)));
            }
            if (rng.nextInt(denom) == 0 && !phase.getLootRoomIds().isEmpty()) {
                String roomId = phase.getLootRoomIds().get(rng.nextInt(phase.getLootRoomIds().size()));
                plugin.lootRooms().offerEntry(player, island, roomId);
                if (nether) island.data().setNetherLastLootRoomAt(broken);
                else island.data().setLastLootRoomAt(broken);
            }
        }
        // Mid-phase boss every ~bossCd blocks (config-tunable). Blood Moon = 4x rolls.
        if (broken - lastBoss >= bossCd) {
            int denom = 120;
            if (ev == SeasonManager.ServerEvent.BLOOD_MOON) denom = 30;
            if (rng.nextInt(denom) == 0 && phase.getBossId() != null) {
                plugin.bosses().spawn(phase.getBossId(), island, player, nether);
                if (nether) island.data().setNetherLastBossAt(broken);
                else island.data().setLastBossAt(broken);
            }
        }
    }

    private void handleBlockEvents(Player player, Island island, Material broken) {
        long coins = 0;
        switch (broken) {
            case ENDER_CHEST -> coins = 50;
            case BEACON -> {
                // Nether Star used to be a guaranteed drop, but the vanilla beacon recipe
                // takes 1 star + 3 obsidian + 5 glass, so every beacon broken funded another
                // crafted beacon — players were stockpiling them in phase 10. Cap the star
                // drop at 20% so it stays exciting without being a self-replenishing loop.
                coins = 2500;
                boolean gotStar = ThreadLocalRandom.current().nextInt(5) == 0;
                if (gotStar) {
                    player.getInventory().addItem(new ItemStack(Material.NETHER_STAR));
                    Msg.title(player, "<gold>★ Beacon!", "<yellow>+2500 coins + Nether Star");
                } else {
                    Msg.title(player, "<gold>★ Beacon!", "<yellow>+2500 coins");
                }
            }
            case CONDUIT -> {
                coins = 1500;
                player.getInventory().addItem(new ItemStack(Material.HEART_OF_THE_SEA));
                Msg.actionBar(player, "<aqua>+1500 coins + Heart of the Sea");
            }
            case SHULKER_BOX -> {
                coins = 1000;
                player.getInventory().addItem(new ItemStack(Material.DRAGON_BREATH));
                Msg.actionBar(player, "<light_purple>+1000 coins + Dragon Breath");
            }
            case SCULK_CATALYST -> {
                coins = 800;
                // Silk touch keeps the catalyst block itself (dropped by the natural
                // drop path). The echo shard is the consolation prize for non-silk
                // mining — giving both would make silk-touch a strict upgrade and
                // players would never use it, which is the opposite of intended.
                ItemStack tool = player.getInventory().getItemInMainHand();
                boolean silkTouch = tool != null
                        && tool.containsEnchantment(org.bukkit.enchantments.Enchantment.SILK_TOUCH);
                if (silkTouch) {
                    Msg.actionBar(player, "<dark_purple>+800 coins · <gray>Silk-touched the catalyst");
                } else {
                    player.getInventory().addItem(new ItemStack(Material.ECHO_SHARD));
                    Msg.actionBar(player, "<dark_purple>+800 coins + Echo Shard");
                }
            }
            case NETHERITE_BLOCK -> {
                coins = 8000;
                Msg.title(player, "<gold>✦ NETHERITE ✦", "<yellow>+8000 coins");
            }
            case CAKE -> {
                coins = 600;
                Msg.actionBar(player, "<light_purple>The cake was real! +600 coins");
            }
            default -> {}
        }
        if (coins > 0) {
            // JACKPOT (Luck 10): +25% coin from rare/chest blocks.
            if (Perk.hasPerk(plugin.progression().get(player), Perk.JACKPOT)) {
                coins = Math.round(coins * 1.25);
            }
            // QUARRY (Mining 20): +10% coin reward per coin-yielding block.
            if (Perk.hasPerk(plugin.progression().get(player), Perk.QUARRY)) {
                coins = Math.round(coins * 1.10);
            }
            if (plugin.seasons().active() == SeasonManager.ServerEvent.DOUBLE_COINS) coins *= 2;
            coins = Math.round(coins * plugin.prestige().coinMultiplier(island));
            plugin.economy().award(island, coins);
            // Rare-block breaks reward LUCK XP — that's how the LUCK tree levels.
            plugin.progression().addXp(player, SkillType.LUCK, 5L);
            if (broken == Material.ENDER_CHEST) {
                Msg.actionBar(player, "<gold>+" + coins + " coins");
            }
        }
    }

    private static boolean isOreLike(Material m) {
        return switch (m) {
            case COAL_ORE, DEEPSLATE_COAL_ORE,
                 IRON_ORE, DEEPSLATE_IRON_ORE,
                 GOLD_ORE, DEEPSLATE_GOLD_ORE, NETHER_GOLD_ORE,
                 DIAMOND_ORE, DEEPSLATE_DIAMOND_ORE,
                 EMERALD_ORE, DEEPSLATE_EMERALD_ORE,
                 LAPIS_ORE, DEEPSLATE_LAPIS_ORE,
                 REDSTONE_ORE, DEEPSLATE_REDSTONE_ORE,
                 COPPER_ORE, DEEPSLATE_COPPER_ORE,
                 NETHER_QUARTZ_ORE, ANCIENT_DEBRIS -> true;
            default -> false;
        };
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        if (plugin.community() == null) return;
        Location loc = event.getBlock().getLocation();
        if (plugin.community().isCommunityBlock(loc) || plugin.community().isAnchorBlock(loc)) {
            event.setCancelled(true);
        }
    }

    /** Stop players placing on top of the center block (would prevent regen). */
    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Location placedLoc = event.getBlock().getLocation();
        if (plugin.community() != null && plugin.community().isInRegenColumn(placedLoc)) {
            event.setCancelled(true);
            Msg.actionBar(event.getPlayer(), "<red>Keep the community block column clear.");
            return;
        }
        Island island = plugin.islands().atLocation(placedLoc);
        if (island == null) return;
        if (!island.isMember(event.getPlayer())) return;

        Location center = island.centerBlock();

        boolean inRegenColumn = placedLoc.getBlockX() == center.getBlockX()
                && placedLoc.getBlockZ() == center.getBlockZ()
                && placedLoc.getBlockY() >= center.getBlockY() - 1
                && placedLoc.getBlockY() <= center.getBlockY() + 1;

        if (inRegenColumn) {
            event.setCancelled(true);
            Msg.actionBar(event.getPlayer(), "<red>Keep the OneBlock column clear.");
            return;
        }

        playPlaceSound(event.getBlockPlaced());
    }
}
