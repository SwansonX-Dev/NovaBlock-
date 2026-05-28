package com.nova.novablock.lootroom.rooms;

import com.nova.novablock.island.Island;
import com.nova.novablock.lootroom.LootRoom;
import com.nova.novablock.lootroom.LootRoomRun;
import com.nova.novablock.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Deterministic 18-jump parkour course. The previous random generator could
 * produce diagonal +1y jumps with a 2-block gap, which isn't physically
 * possible in vanilla Minecraft — so some players literally couldn't finish.
 *
 * <p>Each entry is a relative {dx, dy, dz} from the previous block. The
 * jumps are tuned for the standard 1-block-gap straight jump (dx=2),
 * 1-block-gap turn (dz=2 after dx=2), and 2-block-gap running jumps
 * (dx=3 or dx=4) on flat ground only.
 */
public class ParkourRoom implements LootRoom {

    /** {dx, dy, dz} from the previous block. Designed by hand, all confirmed jumpable. */
    private static final int[][] COURSE = {
            {2, 0, 0},   // 1: simple straight
            {2, 0, 0},   // 2: another straight
            {0, 0, 2},   // 3: 90° turn
            {2, 0, 0},   // 4: straight again
            {2, 1, 0},   // 5: jump up one
            {0, 0, 2},   // 6: turn after climbing
            {3, 0, 0},   // 7: 2-block running gap (flat)
            {0, -1, 2},  // 8: down one + turn
            {2, 0, 0},   // 9: straight
            {2, 0, 0},   // 10: straight
            {0, 1, 2},   // 11: turn + up one
            {2, 0, 0},   // 12: straight
            {3, 0, 0},   // 13: running gap (flat)
            {0, 0, 2},   // 14: turn
            {2, -1, 0},  // 15: down one
            {2, 0, 0},   // 16: straight
            {0, 0, 2},   // 17: turn
            {2, 0, 0},   // 18: final approach to gold
    };

    @Override public String id() { return "parkour"; }
    @Override public String displayName() { return "Parkour Rift"; }

    @Override
    public List<ItemStack> rewardItems(Island island) {
        int phase = island.data().getPhaseIndex();
        List<ItemStack> out = new ArrayList<>(LootRoom.super.rewardItems(island));
        out.add(new ItemStack(Material.SUGAR, 8 + phase * 2));
        out.add(new ItemStack(Material.FEATHER, 4 + phase));
        out.add(LootRoom.enchantedBook(Enchantment.FEATHER_FALLING, Math.min(4, 1 + phase / 3)));
        if (phase >= 3) out.add(new ItemStack(Material.GOLDEN_CARROT, 4 + phase));
        if (phase >= 6) out.add(new ItemStack(Material.SLIME_BLOCK, 4));
        if (phase >= 9) out.add(new ItemStack(Material.ELYTRA, 1));
        return out;
    }

    @Override
    public Location build(Location anchor) {
        // Start block at the anchor.
        Location cur = anchor.clone();
        cur.getBlock().setType(Material.QUARTZ_BLOCK);
        for (int i = 0; i < COURSE.length; i++) {
            int[] step = COURSE[i];
            cur.add(step[0], step[1], step[2]);
            // Last block is gold (goal); rest are quartz.
            cur.getBlock().setType(i == COURSE.length - 1 ? Material.GOLD_BLOCK : Material.QUARTZ_BLOCK);
        }
        return anchor.clone().add(0.5, 1, 0.5);
    }

    @Override
    public void tick(LootRoomRun run) {
        Player p = run.player();
        if (p == null) { run.markFinished(); return; }
        long elapsed = (Bukkit.getCurrentTick() - run.startTick()) / 20;
        if (elapsed > 120) {
            Msg.actionBar(p, "<red>Time's up!");
            run.markFinished();
            return;
        }
        // Finish condition: standing on the gold block
        Material under = p.getLocation().clone().subtract(0, 1, 0).getBlock().getType();
        if (under == Material.GOLD_BLOCK) {
            run.addScore((int) Math.max(50, 250 - elapsed * 2));
            run.markFinished();
            return;
        }
        // Falling check — send the player back to start instead of forfeiting.
        if (p.getLocation().getY() < run.anchor().getY() - 3) {
            p.teleport(run.anchor().clone().add(0.5, 1, 0.5));
            Msg.actionBar(p, "<gray>Back to start.");
        }
    }
}
