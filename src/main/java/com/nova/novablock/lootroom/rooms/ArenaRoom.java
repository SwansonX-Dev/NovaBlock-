package com.nova.novablock.lootroom.rooms;

import com.nova.novablock.NovaBlock;
import com.nova.novablock.lootroom.LootRoom;
import com.nova.novablock.lootroom.LootRoomRun;
import com.nova.novablock.lootroom.RoomTheme;
import com.nova.novablock.util.Msg;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.concurrent.ThreadLocalRandom;

public class ArenaRoom implements LootRoom {

    private final NovaBlock plugin;
    private final RoomTheme theme;
    private static final int RADIUS = 10;

    public ArenaRoom(NovaBlock plugin, RoomTheme theme) {
        this.plugin = plugin;
        this.theme = theme;
    }

    @Override public String id() { return "arena_" + theme.suffix(); }
    @Override public String displayName() { return theme.displayPrefix() + "Arena Rift"; }

    private Material floorMaterial() {
        return "nether".equals(theme.suffix()) ? Material.NETHER_BRICKS : Material.POLISHED_BLACKSTONE_BRICKS;
    }

    @Override
    public Location build(Location anchor) {
        // Solid stone-brick disc floor + 4-block wall
        for (int dx = -RADIUS; dx <= RADIUS; dx++) {
            for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                if (dx * dx + dz * dz <= RADIUS * RADIUS) {
                    anchor.clone().add(dx, 0, dz).getBlock().setType(floorMaterial());
                    for (int dy = 1; dy <= 6; dy++) {
                        anchor.clone().add(dx, dy, dz).getBlock().setType(Material.AIR);
                    }
                }
            }
        }
        // Ring of barrier-like walls
        for (int dx = -RADIUS - 1; dx <= RADIUS + 1; dx++) {
            for (int dz = -RADIUS - 1; dz <= RADIUS + 1; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d > RADIUS && d <= RADIUS + 1) {
                    for (int dy = 1; dy <= 5; dy++) {
                        anchor.clone().add(dx, dy, dz).getBlock().setType(Material.BARRIER);
                    }
                }
            }
        }
        return anchor.clone().add(0.5, 1, 0.5);
    }

    @Override
    public void onStart(LootRoomRun run, Player p) {
        // Encode wave number + mobs remaining in state high/low halves
        run.setState((1L << 32));   // wave=1, remaining=0 (computed on tick spawn)
    }

    @Override
    public void tick(LootRoomRun run) {
        Player p = run.player();
        if (p == null) { run.markFinished(); return; }
        int wave = (int) (run.state() >>> 32);
        int remaining = (int) (run.state() & 0xFFFFFFFFL);

        // Count nearby alive mobs spawned for this run
        int alive = 0;
        for (var e : p.getWorld().getNearbyEntities(run.anchor(), RADIUS + 2, 8, RADIUS + 2)) {
            if (e instanceof LivingEntity le && !(le instanceof Player) && !le.isDead()) alive++;
        }
        // If we should spawn this wave
        if (remaining == 0 && alive == 0) {
            if (wave > 3) {
                run.addScore(300);
                run.markFinished();
                return;
            }
            int toSpawn = 3 + wave * 2;
            var pool = theme.mobPool();
            var rng = ThreadLocalRandom.current();
            for (int i = 0; i < toSpawn; i++) {
                Location at = run.anchor().clone().add(rng.nextInt(-RADIUS + 2, RADIUS - 2), 1, rng.nextInt(-RADIUS + 2, RADIUS - 2));
                var ent = p.getWorld().spawnEntity(at, pool.get(rng.nextInt(pool.size())));
                if (ent instanceof LivingEntity le) {
                    le.setRemoveWhenFarAway(false);
                    if (ent instanceof org.bukkit.entity.Mob mob) mob.setTarget(p);
                }
            }
            Msg.title(p, "<red>Wave " + wave, "<gray>" + toSpawn + " enemies incoming");
            p.playSound(p.getLocation(), Sound.EVENT_RAID_HORN, 0.5f, 0.7f);
            run.setState(((long) (wave + 1) << 32) | toSpawn);
            return;
        }
        // Update remaining count to actual alive count
        run.setState(((long) wave << 32) | Math.max(0, alive));
    }

    @Override
    public int rewardCoins(com.nova.novablock.island.Island island) {
        return 1500 + island.data().getPhaseIndex() * 300;
    }

    @Override
    public java.util.List<org.bukkit.inventory.ItemStack> rewardItems(com.nova.novablock.island.Island island) {
        int phase = island.data().getPhaseIndex();
        java.util.List<org.bukkit.inventory.ItemStack> out = new java.util.ArrayList<>(LootRoom.super.rewardItems(island));
        out.add(new org.bukkit.inventory.ItemStack(Material.IRON_INGOT, 4 + phase));
        out.add(new org.bukkit.inventory.ItemStack(Material.GOLD_INGOT, 2 + phase / 2));
        out.add(LootRoom.enchantedBook(org.bukkit.enchantments.Enchantment.SHARPNESS, Math.min(5, 1 + phase / 2)));
        out.add(LootRoom.enchantedBook(org.bukkit.enchantments.Enchantment.UNBREAKING, Math.min(3, 1 + phase / 3)));
        if (phase >= 4) out.add(new org.bukkit.inventory.ItemStack(Material.DIAMOND, 1 + phase / 4));
        if (phase >= 6) out.add(new org.bukkit.inventory.ItemStack(Material.ENDER_PEARL, 2 + phase / 3));
        if (phase >= 8) out.add(new org.bukkit.inventory.ItemStack(Material.NETHERITE_SCRAP, 1));
        return out;
    }
}
