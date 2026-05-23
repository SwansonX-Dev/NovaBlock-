package com.nova.novablock.lootroom.rooms;

import com.nova.novablock.NovaBlock;
import com.nova.novablock.lootroom.LootRoom;
import com.nova.novablock.lootroom.LootRoomRun;
import com.nova.novablock.util.Msg;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 3x3 pressure-plate "Simon Says" puzzle.
 * Sequence is encoded into LootRoomRun.state — high 8 bits = round, low 24 bits = sequence (8 steps × 3 bits each).
 */
public class PuzzleRoom implements LootRoom {

    private final NovaBlock plugin;

    public PuzzleRoom(NovaBlock plugin) { this.plugin = plugin; }

    @Override public String id() { return "puzzle"; }
    @Override public String displayName() { return "Echo Vault"; }

    @Override
    public java.util.List<org.bukkit.inventory.ItemStack> rewardItems(com.nova.novablock.island.Island island) {
        int phase = island.data().getPhaseIndex();
        java.util.List<org.bukkit.inventory.ItemStack> out = new java.util.ArrayList<>(LootRoom.super.rewardItems(island));
        out.add(new org.bukkit.inventory.ItemStack(Material.AMETHYST_SHARD, 4 + phase));
        out.add(LootRoom.enchantedBook(org.bukkit.enchantments.Enchantment.MENDING, 1));
        if (phase >= 5) out.add(new org.bukkit.inventory.ItemStack(Material.ECHO_SHARD, 1 + phase / 5));
        if (phase >= 7) out.add(new org.bukkit.inventory.ItemStack(Material.ENDER_PEARL, 2 + phase / 4));
        if (phase >= 9) out.add(LootRoom.enchantedBook(org.bukkit.enchantments.Enchantment.LUCK_OF_THE_SEA, 3));
        return out;
    }

    @Override
    public Location build(Location anchor) {
        // Floor 7x7 polished deepslate
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                anchor.clone().add(dx, 0, dz).getBlock().setType(Material.POLISHED_DEEPSLATE);
            }
        }
        // Coloured wool 3x3 pad in the center
        Material[] wools = {
                Material.RED_WOOL, Material.ORANGE_WOOL, Material.YELLOW_WOOL,
                Material.GREEN_WOOL, Material.CYAN_WOOL, Material.LIGHT_BLUE_WOOL,
                Material.BLUE_WOOL, Material.PURPLE_WOOL, Material.MAGENTA_WOOL
        };
        int i = 0;
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                anchor.clone().add(dx, 0, dz).getBlock().setType(wools[i++]);
            }
        }
        return anchor.clone().add(0.5, 1, -2.5);
    }

    // State layout (long, 64 bits):
    //   bits  0-15: 4-step sequence (4 bits per step, value 0-8 = pad index)
    //   bits 16-19: current step counter (0-4; 4 = won)
    //   bits 20-23: lastPad + 1 (0 = "not standing on any pad")
    private static long seqOf(long s)      { return s & 0xFFFFL; }
    private static int  stepOf(long s)     { return (int)((s >>> 16) & 0xF); }
    private static int  lastPadOf(long s)  { return ((int)((s >>> 20) & 0xF)) - 1; }
    private static long pack(long seq, int step, int lastPad) {
        return (seq & 0xFFFFL) | ((long)(step & 0xF) << 16) | ((long)((lastPad + 1) & 0xF) << 20);
    }
    private static int  padAt(long seq, int step) { return (int)((seq >> (step * 4)) & 0xF); }

    @Override
    public void onStart(LootRoomRun run, Player p) {
        long sequence = 0;
        var rng = ThreadLocalRandom.current();
        for (int i = 0; i < 4; i++) {
            sequence |= ((long) rng.nextInt(9)) << (i * 4);
        }
        run.setState(pack(sequence, 0, -1));
        Msg.send(p, "<aqua>Watch the lights — then repeat by walking on the matching wool blocks!");
        showSequence(run, p);
    }

    private void showSequence(LootRoomRun run, Player p) {
        long seq = seqOf(run.state());
        for (int i = 0; i < 4; i++) {
            int idx = (int)((seq >> (i * 4)) & 0xF);
            int dx = (idx % 3) - 1;
            int dz = (idx / 3) - 1;
            Location at = run.anchor().clone().add(dx, 1.2, dz);
            org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> {
                at.getWorld().spawnParticle(org.bukkit.Particle.END_ROD, at, 30, 0.2, 0.2, 0.2, 0.02);
                p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_BELL, 1f, 0.6f + idx * 0.15f);
            }, 20L + i * 20L);
        }
        // Tell the player it's their turn after the sequence finishes.
        org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline()) return;
            Msg.actionBar(p, "<yellow>Your turn — step onto the pads in order!");
        }, 20L + 4 * 20L + 10L);
    }

    @Override
    public void tick(LootRoomRun run) {
        Player p = run.player();
        if (p == null) { run.markFinished(); return; }

        long state = run.state();
        int lastPad = lastPadOf(state);

        // Where are we standing? Compute padIdx (-1 if off-grid).
        Location below = p.getLocation().clone().subtract(0, 1, 0);
        Material mat = below.getBlock().getType();
        int padIdx = -1;
        if (mat.name().endsWith("_WOOL")) {
            int dx = below.getBlockX() - run.anchor().getBlockX();
            int dz = below.getBlockZ() - run.anchor().getBlockZ();
            if (Math.abs(dx) <= 1 && Math.abs(dz) <= 1) {
                padIdx = (dx + 1) + (dz + 1) * 3;
            }
        }

        // Debounce: only act when the player CHANGES pads. Standing still
        // (or sliding around off-pad) shouldn't keep firing reset / advance.
        if (padIdx == lastPad) return;

        // Update lastPad regardless of correctness so the next tick has accurate state.
        long seq = seqOf(state);
        int step = stepOf(state);

        if (padIdx == -1) {
            // Stepped off the pad area — just remember that.
            run.setState(pack(seq, step, -1));
            return;
        }

        int expected = padAt(seq, step);
        if (padIdx == expected) {
            step++;
            p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 0.8f + step * 0.15f);
            below.getWorld().spawnParticle(org.bukkit.Particle.HAPPY_VILLAGER,
                    below.clone().add(0.5, 1.2, 0.5), 12, 0.3, 0.3, 0.3);
            if (step >= 4) {
                run.addScore(400);
                run.markFinished();
                return;
            }
            Msg.actionBar(p, "<green>" + step + "/4");
            run.setState(pack(seq, step, padIdx));
        } else {
            p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.5f);
            Msg.actionBar(p, "<red>Wrong pad! Restarting — watch the sequence again.");
            run.setState(pack(seq, 0, padIdx));
            showSequence(run, p);
        }
    }
}
