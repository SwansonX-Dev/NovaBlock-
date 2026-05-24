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
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class BlockListener implements Listener {

    private final NovaBlock plugin;
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

        // Phase-specific drops & bonuses
        handleBlockEvents(player, island, broken);

        Phase phase = plugin.phases().getOrLast(island.data().getPhaseIndex());
        if (phase == null) return;

        // Pull planned next material from the prophecy queue so previews are honest
        Material next = island.pollNext();
        if (next == null) next = phase.rollBlock(ThreadLocalRandom.current());
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

    /**
     * Drop the OneBlock's natural drops at its location using the tool the player
     * has. If the player is holding their paxel, auto-smelt and telekinesis the
     * drops straight into their inventory (overflow falls at the block).
     */
    private void dropNaturally(Block block, Player player, ItemStack tool) {
        var loc = block.getLocation().add(0.5, 0.5, 0.5);
        boolean paxel = plugin.paxels().isPaxel(tool);
        for (ItemStack drop : block.getDrops(tool)) {
            if (paxel) drop = plugin.paxels().maybeSmelt(drop);
            if (paxel) {
                var overflow = player.getInventory().addItem(drop);
                for (ItemStack leftover : overflow.values()) {
                    block.getWorld().dropItemNaturally(loc, leftover);
                }
            } else {
                block.getWorld().dropItemNaturally(loc, drop);
            }
        }
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
