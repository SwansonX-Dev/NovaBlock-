package com.nova.novablock.lootroom.rooms;

import com.nova.novablock.lootroom.LootRoom;
import com.nova.novablock.lootroom.LootRoomRun;
import com.nova.novablock.util.Msg;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.concurrent.ThreadLocalRandom;

public class ParkourRoom implements LootRoom {

    @Override public String id() { return "parkour"; }
    @Override public String displayName() { return "Parkour Rift"; }

    @Override
    public Location build(Location anchor) {
        // Build a 30-jump staircase of floating blocks with random offsets
        var rng = ThreadLocalRandom.current();
        Location cur = anchor.clone();
        cur.getBlock().setType(Material.QUARTZ_BLOCK);
        for (int i = 1; i <= 30; i++) {
            int dx = rng.nextInt(-2, 3);
            int dz = rng.nextInt(-2, 3);
            if (dx == 0 && dz == 0) dx = 2;
            cur.add(dx, rng.nextInt(0, 2) == 0 ? 1 : 0, dz);
            cur.getBlock().setType(i == 30 ? Material.GOLD_BLOCK : Material.QUARTZ_BLOCK);
        }
        // Mark goal with a beacon-style ladder up
        cur.clone().add(0, 1, 0).getBlock().setType(Material.LIGHT);
        return anchor.clone().add(0.5, 1, 0.5);
    }

    @Override
    public void tick(LootRoomRun run) {
        Player p = run.player();
        if (p == null) { run.markFinished(); return; }
        long elapsed = (org.bukkit.Bukkit.getCurrentTick() - run.startTick()) / 20;
        if (elapsed > 90) {
            Msg.actionBar(p, "<red>Time's up!");
            run.markFinished();
            return;
        }
        // Finish condition: standing on the gold block
        Material under = p.getLocation().clone().subtract(0, 1, 0).getBlock().getType();
        if (under == Material.GOLD_BLOCK) {
            run.addScore((int) Math.max(50, 200 - elapsed * 2));
            run.markFinished();
        }
        // Falling check: anchor Y-3
        if (p.getLocation().getY() < run.anchor().getY() - 3) {
            p.teleport(run.anchor().clone().add(0.5, 1, 0.5));
            Msg.actionBar(p, "<gray>Back to start.");
        }
    }
}
