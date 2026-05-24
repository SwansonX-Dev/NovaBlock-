package com.nova.novablock.listener;

import com.nova.novablock.NovaBlock;
import com.nova.novablock.island.Island;
import com.nova.novablock.phase.Phase;
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
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

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

    public BlockListener(NovaBlock plugin) { this.plugin = plugin; }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        Location loc = block.getLocation();
        Island island = plugin.islands().atLocation(loc);
        if (island == null) return;

        Location center = island.centerBlock();
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
        Long last = recentBreakTick.get(island.data().getId());
        if (last != null && last == tick) {
            event.setCancelled(true);
            return;
        }
        recentBreakTick.put(island.data().getId(), tick);

        Material broken = block.getType();

        // Take over the break ourselves: cancel vanilla, drop manually, then replace immediately
        // in the same tick so there's never a moment when the block is AIR. This fixes
        // "Bedrock has to break it twice" (player saw the gap and swung again).
        event.setCancelled(true);
        dropNaturally(block, player, player.getInventory().getItemInMainHand());
        playBreakSound(block);

        // Phase-specific drops & bonuses
        handleBlockEvents(player, island, broken);

        Phase phase = plugin.phases().getOrLast(island.data().getPhaseIndex());
        if (phase == null) return;

        // Pull planned next material from the prophecy queue so previews are honest
        Material next = island.pollNext();
        if (next == null) next = phase.rollBlock(ThreadLocalRandom.current());
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
        fillPhaseChest(center.getBlock(), phase);
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

        island.data().incrementBlocksBroken();
        island.data().incrementPhaseProgress();
        island.recordBreak(broken);
        if (island.getComboCount() >= 5) {
            int combo = island.getComboCount();
            Msg.actionBar(player, "<aqua>Combo x" + combo + "! <gray>+" + (combo * 2) + " XP");
            plugin.progression().addXp(player, SkillType.MINING, combo * 2L);
        }
        plugin.progression().addXp(player, SkillType.MINING, 1L);
        plugin.prophecies().onAdvance(island, broken);
        island.refillUpcoming(phase, com.nova.novablock.prophecy.ProphecyManager.QUEUE_SIZE);

        // Quest tick
        plugin.quests().onBlockBroken(player, broken);

        // Paxel XP — progresses tool tier as the player levels Mining
        plugin.paxels().onMine(player, broken);

        // Anti-AFK tracker — counts OneBlock activity, prompts a chat captcha every 30 min.
        plugin.antiAfk().recordMineActivity(player);

        // Phase progression
        if (island.data().getPhaseProgress() >= phase.getRequiredBlocks()) {
            advancePhase(player, island, phase);
        }

        // Roll boss / loot room
        rollEncounters(player, island, phase, center);

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
        block.getWorld().playSound(block.getLocation().add(0.5, 0.5, 0.5),
                group.getPlaceSound(), group.getVolume(), group.getPitch());
    }

    /**
     * Drop the OneBlock's natural drops at its location using the tool the player
     * has. If the player is holding their paxel, auto-smelt and telekinesis the
     * drops straight into their inventory (overflow falls at the block).
     */
    private void dropNaturally(Block block, Player player, ItemStack tool) {
        var loc = block.getLocation().add(0.5, 0.5, 0.5);
        boolean paxel = plugin.paxels().isPaxel(tool);
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
        var inventory = container.getInventory();
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
        container.update(true, false);
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
            Msg.title(player, "<gold>You've completed all phases!", "<gray>Prestige to keep going.");
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
        plugin.economy().award(island, reward);
        if (old.getBossId() != null) {
            plugin.bosses().spawn(old.getBossId(), island, player);
        }
        // Paxel tier upgrade — phase-up is the natural milestone
        plugin.paxels().refreshTier(player);
    }

    private void rollEncounters(Player player, Island island, Phase phase, Location center) {
        long broken = island.data().getBlocksBroken();
        var rng = ThreadLocalRandom.current();
        SeasonManager.ServerEvent ev = plugin.seasons().active();

        // Mob spawn ~1/18 chance (~every ~45s of steady mining)
        if (!phase.getMobs().isEmpty() && rng.nextInt(18) == 0) {
            EntityType type = phase.rollMob(rng);
            if (type != null) {
                Entity e = center.getWorld().spawnEntity(center.clone().add(0, 1, 0), type);
                e.setMetadata("nova_natural", new org.bukkit.metadata.FixedMetadataValue(plugin, true));
            }
        }
        // Loot room every ~150 blocks (cooldown protected). Rift Storm = 5x rolls.
        if (broken - island.data().getLastLootRoomAt() >= 150) {
            int denom = 30;
            if (ev == SeasonManager.ServerEvent.RIFT_STORM) denom = 6;
            if (rng.nextInt(denom) == 0 && !phase.getLootRoomIds().isEmpty()) {
                String roomId = phase.getLootRoomIds().get(rng.nextInt(phase.getLootRoomIds().size()));
                plugin.lootRooms().offerEntry(player, island, roomId);
                island.data().setLastLootRoomAt(broken);
            }
        }
        // Mid-phase boss every ~300 blocks. Blood Moon = 4x rolls.
        if (broken - island.data().getLastBossAt() >= 300) {
            int denom = 120;
            if (ev == SeasonManager.ServerEvent.BLOOD_MOON) denom = 30;
            if (rng.nextInt(denom) == 0 && phase.getBossId() != null) {
                plugin.bosses().spawn(phase.getBossId(), island, player);
                island.data().setLastBossAt(broken);
            }
        }
    }

    private void handleBlockEvents(Player player, Island island, Material broken) {
        long coins = 0;
        switch (broken) {
            case ENDER_CHEST -> coins = 50;
            case BEACON -> {
                coins = 2500;
                player.getInventory().addItem(new ItemStack(Material.NETHER_STAR));
                Msg.title(player, "<gold>★ Beacon!", "<yellow>+2500 coins + Nether Star");
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
                player.getInventory().addItem(new ItemStack(Material.ECHO_SHARD));
                Msg.actionBar(player, "<dark_purple>+800 coins + Echo Shard");
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
            if (plugin.seasons().active() == SeasonManager.ServerEvent.DOUBLE_COINS) coins *= 2;
            plugin.economy().award(island, coins);
            if (broken == Material.ENDER_CHEST) {
                Msg.actionBar(player, "<gold>+" + coins + " coins");
            }
        }
    }

    /** Stop players placing on top of the center block (would prevent regen). */
    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Island island = plugin.islands().atLocation(event.getBlock().getLocation());
        if (island == null) return;
        if (!island.isMember(event.getPlayer())) return;

        Location placed = event.getBlock().getLocation();
        Location center = island.centerBlock();

        boolean inRegenColumn = placed.getBlockX() == center.getBlockX()
                && placed.getBlockZ() == center.getBlockZ()
                && placed.getBlockY() >= center.getBlockY() - 1
                && placed.getBlockY() <= center.getBlockY() + 1;

        if (inRegenColumn) {
            event.setCancelled(true);
            Msg.actionBar(event.getPlayer(), "<red>Keep the OneBlock column clear.");
        }
    }
}
