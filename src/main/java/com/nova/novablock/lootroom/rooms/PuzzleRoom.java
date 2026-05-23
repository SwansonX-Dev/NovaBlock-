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

    @Override
    public void onStart(LootRoomRun run, Player p) {
        long sequence = 0;
        var rng = ThreadLocalRandom.current();
        for (int i = 0; i < 4; i++) {
            sequence |= ((long) rng.nextInt(9)) << (i * 4); // 4 bits per step, 4 steps
        }
        run.setState(sequence);
        Msg.send(p, "<aqua>Watch the lights — then repeat by walking on the matching wool blocks!");
        showSequence(run, p);
    }

    private void showSequence(LootRoomRun run, Player p) {
        long seq = run.state();
        for (int i = 0; i < 4; i++) {
            int idx = (int) ((seq >> (i * 4)) & 0xF);
            int dx = (idx % 3) - 1;
            int dz = (idx / 3) - 1;
            Location at = run.anchor().clone().add(dx, 1.2, dz);
            org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> {
                at.getWorld().spawnParticle(org.bukkit.Particle.END_ROD, at, 30, 0.2, 0.2, 0.2, 0.02);
                p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_BELL, 1f, 0.6f + idx * 0.15f);
            }, 20L + i * 20L);
        }
    }

    @Override
    public void tick(LootRoomRun run) {
        Player p = run.player();
        if (p == null) { run.markFinished(); return; }
        // Determine which wool block the player is standing on
        Location below = p.getLocation().clone().subtract(0, 1, 0);
        Material mat = below.getBlock().getType();
        if (!mat.name().endsWith("_WOOL")) return;

        int dx = below.getBlockX() - run.anchor().getBlockX();
        int dz = below.getBlockZ() - run.anchor().getBlockZ();
        if (Math.abs(dx) > 1 || Math.abs(dz) > 1) return;
        int padIdx = (dx + 1) + (dz + 1) * 3;

        long seq = run.state();
        // We re-use bit 60 onward as "step pointer"
        int step = (int) ((seq >>> 60) & 0xF);
        int expected = (int) ((seq >> (step * 4)) & 0xF);
        if (padIdx == expected) {
            step++;
            p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1f);
            if (step >= 4) {
                run.addScore(400);
                run.markFinished();
                return;
            }
            // Clear pointer and rewrite
            seq = (seq & ~(0xFL << 60)) | ((long) step << 60);
            run.setState(seq);
        } else if (padIdx != expected) {
            // wrong — reset to start
            seq = seq & ~(0xFL << 60);
            run.setState(seq);
            p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.5f);
            Msg.actionBar(p, "<red>Wrong! Restarting sequence.");
        }
    }
}
