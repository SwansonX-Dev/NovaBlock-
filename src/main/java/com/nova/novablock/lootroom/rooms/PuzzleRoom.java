package com.nova.novablock.lootroom.rooms;

import com.nova.novablock.lootroom.LootRoom;
import com.nova.novablock.lootroom.LootRoomRun;
import com.nova.novablock.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Replacement for the old Echo Vault Simon-Says room.
 *
 * <p>Crystal Cache is intentionally simple: the room places eight glowing
 * amethyst targets around a small arena. The player clears the rift by breaking
 * all targets before the timer expires. This keeps the "puzzle" room slot in
 * phase configs while avoiding pressure-plate debounce and delayed-sequence
 * edge cases.
 */
public class PuzzleRoom implements LootRoom {

    private static final int TARGETS = 8;
    private static final int TIME_LIMIT_SECONDS = 75;
    private static final int[] TARGETS_OFFSET = {-3, -2, -1, 0, 1, 2, 3};

    @Override public String id() { return "puzzle"; }
    @Override public String displayName() { return "Crystal Cache"; }

    @Override
    public List<ItemStack> rewardItems(com.nova.novablock.island.Island island) {
        int phase = island.data().getPhaseIndex();
        List<ItemStack> out = new ArrayList<>(LootRoom.super.rewardItems(island));
        out.add(new ItemStack(Material.AMETHYST_SHARD, 6 + phase));
        out.add(new ItemStack(Material.LAPIS_LAZULI, 8 + phase * 2));
        out.add(LootRoom.enchantedBook(org.bukkit.enchantments.Enchantment.FORTUNE, Math.min(3, 1 + phase / 4)));
        if (phase >= 5) out.add(new ItemStack(Material.ECHO_SHARD, 1 + phase / 5));
        if (phase >= 7) out.add(new ItemStack(Material.ENDER_PEARL, 2 + phase / 4));
        if (phase >= 9) out.add(LootRoom.enchantedBook(org.bukkit.enchantments.Enchantment.LUCK_OF_THE_SEA, 3));
        return out;
    }

    @Override
    public Location build(Location anchor) {
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                anchor.clone().add(dx, 0, dz).getBlock().setType(Material.SMOOTH_BASALT);
                for (int dy = 1; dy <= 5; dy++) {
                    anchor.clone().add(dx, dy, dz).getBlock().setType(Material.AIR);
                }
            }
        }

        for (int dx = -5; dx <= 5; dx++) {
            for (int dz = -5; dz <= 5; dz++) {
                if (Math.abs(dx) == 5 || Math.abs(dz) == 5) {
                    for (int dy = 1; dy <= 3; dy++) {
                        anchor.clone().add(dx, dy, dz).getBlock().setType(Material.TINTED_GLASS);
                    }
                }
            }
        }

        target(anchor, -3, 1, -3);
        target(anchor, 0, 1, -3);
        target(anchor, 3, 1, -3);
        target(anchor, -3, 2, 0);
        target(anchor, 3, 2, 0);
        target(anchor, -3, 1, 3);
        target(anchor, 0, 2, 3);
        target(anchor, 3, 1, 3);

        anchor.clone().add(0, 4, 0).getBlock().setType(Material.SEA_LANTERN);
        return anchor.clone().add(0.5, 1, 0.5);
    }

    private void target(Location anchor, int dx, int dy, int dz) {
        Location at = anchor.clone().add(dx, dy, dz);
        at.getBlock().setType(Material.AMETHYST_BLOCK);
        // Use AMETHYST_CLUSTER as a visible "pip" instead of LIGHT (which needs a
        // level data value to render and is invisible when placed bare).
        at.clone().add(0, 1, 0).getBlock().setType(Material.AMETHYST_CLUSTER);
    }

    @Override
    public void onStart(LootRoomRun run, Player p) {
        run.setState(TARGETS);
        Msg.send(p, "<aqua>Break all <light_purple>" + TARGETS + " crystal caches <aqua>before time runs out.");
        p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1f);
    }

    @Override
    public void tick(LootRoomRun run) {
        Player p = run.player();
        if (p == null) { run.markFinished(); return; }

        long elapsed = (Bukkit.getCurrentTick() - run.startTick()) / 20;
        if (elapsed > TIME_LIMIT_SECONDS) {
            Msg.actionBar(p, "<red>Time's up!");
            run.markFinished();
            return;
        }

        int remaining = countTargets(run.anchor());
        if (remaining <= 0) {
            run.addScore(350 + (int) Math.max(0, TIME_LIMIT_SECONDS - elapsed) * 2);
            run.markFinished();
            return;
        }

        if (remaining != run.state()) {
            run.setState(remaining);
            p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_CLUSTER_BREAK, 0.9f, 1.25f);
        }
        Msg.actionBar(p, "<aqua>Crystal caches: <yellow>" + remaining
                + "<gray> · <yellow>" + Math.max(0, TIME_LIMIT_SECONDS - elapsed) + "s");
    }

    private int countTargets(Location anchor) {
        int count = 0;
        for (int x : TARGETS_OFFSET) {
            for (int y = 1; y <= 2; y++) {
                for (int z : TARGETS_OFFSET) {
                    if (anchor.clone().add(x, y, z).getBlock().getType() == Material.AMETHYST_BLOCK) {
                        count++;
                    }
                }
            }
        }
        return count;
    }
}
