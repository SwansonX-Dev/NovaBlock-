package com.nova.novablock.listener;

import com.nova.novablock.NovaBlock;
import com.nova.novablock.island.Island;
import com.nova.novablock.phase.Phase;
import com.nova.novablock.progression.SkillType;
import com.nova.novablock.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;

import java.util.concurrent.ThreadLocalRandom;

public class BlockListener implements Listener {

    private final NovaBlock plugin;

    public BlockListener(NovaBlock plugin) { this.plugin = plugin; }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Location loc = event.getBlock().getLocation();
        Island island = plugin.islands().atLocation(loc);
        if (island == null) return;

        Location center = island.centerBlock();
        if (loc.getBlockX() != center.getBlockX()
                || loc.getBlockY() != center.getBlockY()
                || loc.getBlockZ() != center.getBlockZ()) {
            return; // breaking some other block on the island is fine, just don't trigger regen
        }

        if (!island.isMember(player)) {
            event.setCancelled(true);
            Msg.actionBar(player, "<red>This isn't your island.");
            return;
        }

        Material broken = event.getBlock().getType();

        // Drop happens naturally via vanilla; we hijack only special blocks
        handleBlockEvents(player, island, broken);

        Phase phase = plugin.phases().getOrLast(island.data().getPhaseIndex());
        if (phase == null) return;

        // Pull the planned next material from the prophecy queue (so previews are honest)
        Material next = island.pollNext();
        if (next == null) next = phase.rollBlock(ThreadLocalRandom.current());

        // Regen next tick to avoid double-break exploits
        final Material toPlace = next;
        Bukkit.getScheduler().runTask(plugin, () -> center.getBlock().setType(toPlace, true));

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

        // Phase progression
        if (island.data().getPhaseProgress() >= phase.getRequiredBlocks()) {
            advancePhase(player, island, phase);
        }

        // Roll boss / loot room
        rollEncounters(player, island, phase, center);

        // FX
        center.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, center.clone().add(0.5, 0.5, 0.5), 4, 0.2, 0.2, 0.2);
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
        plugin.economy().award(island, 500 + nextIdx * 250L);
        if (old.getBossId() != null) {
            plugin.bosses().spawn(old.getBossId(), island, player);
        }
    }

    private void rollEncounters(Player player, Island island, Phase phase, Location center) {
        long broken = island.data().getBlocksBroken();
        var rng = ThreadLocalRandom.current();
        // Mob spawn ~1/18 chance — once every ~45 seconds of steady mining.
        if (!phase.getMobs().isEmpty() && rng.nextInt(18) == 0) {
            EntityType type = phase.rollMob(rng);
            if (type != null) {
                Entity e = center.getWorld().spawnEntity(center.clone().add(0, 1, 0), type);
                e.setMetadata("nova_natural", new org.bukkit.metadata.FixedMetadataValue(plugin, true));
            }
        }
        // Loot room every ~150 blocks (cooldown protected)
        if (broken - island.data().getLastLootRoomAt() >= 150 && rng.nextInt(30) == 0) {
            if (!phase.getLootRoomIds().isEmpty()) {
                String roomId = phase.getLootRoomIds().get(rng.nextInt(phase.getLootRoomIds().size()));
                plugin.lootRooms().offerEntry(player, island, roomId);
                island.data().setLastLootRoomAt(broken);
            }
        }
        // Mid-phase boss every ~300 blocks
        if (broken - island.data().getLastBossAt() >= 300 && rng.nextInt(120) == 0) {
            if (phase.getBossId() != null) {
                plugin.bosses().spawn(phase.getBossId(), island, player);
                island.data().setLastBossAt(broken);
            }
        }
    }

    private void handleBlockEvents(Player player, Island island, Material broken) {
        switch (broken) {
            case ENDER_CHEST -> {
                plugin.economy().award(island, 50);
                Msg.actionBar(player, "<gold>+50 coins");
            }
            case BEACON -> {
                plugin.economy().award(island, 2500);
                player.getInventory().addItem(new org.bukkit.inventory.ItemStack(Material.NETHER_STAR));
                Msg.title(player, "<gold>★ Beacon!", "<yellow>+2500 coins + Nether Star");
            }
            case CONDUIT -> {
                plugin.economy().award(island, 1500);
                player.getInventory().addItem(new org.bukkit.inventory.ItemStack(Material.HEART_OF_THE_SEA));
                Msg.actionBar(player, "<aqua>+1500 coins + Heart of the Sea");
            }
            case SHULKER_BOX -> {
                plugin.economy().award(island, 1000);
                player.getInventory().addItem(new org.bukkit.inventory.ItemStack(Material.DRAGON_BREATH));
                Msg.actionBar(player, "<light_purple>+1000 coins + Dragon Breath");
            }
            case SCULK_CATALYST -> {
                plugin.economy().award(island, 800);
                player.getInventory().addItem(new org.bukkit.inventory.ItemStack(Material.ECHO_SHARD));
                Msg.actionBar(player, "<dark_purple>+800 coins + Echo Shard");
            }
            case NETHERITE_BLOCK -> {
                plugin.economy().award(island, 8000);
                Msg.title(player, "<gold>✦ NETHERITE ✦", "<yellow>+8000 coins");
            }
            case CAKE -> {
                plugin.economy().award(island, 600);
                Msg.actionBar(player, "<light_purple>The cake was real! +600 coins");
            }
            default -> {}
        }
    }

    /** Stop players placing on top of the center block (would prevent regen). */
    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Island island = plugin.islands().atLocation(event.getBlock().getLocation());
        if (island == null) return;
        Location placed = event.getBlock().getLocation();
        Location center = island.centerBlock();
        if (placed.getBlockX() == center.getBlockX()
                && placed.getBlockY() == center.getBlockY() + 1
                && placed.getBlockZ() == center.getBlockZ()) {
            event.setCancelled(true);
            Msg.actionBar(event.getPlayer(), "<red>Can't place directly above the OneBlock.");
        }
    }
}
