package com.nova.novablock.boss.bosses;

import com.nova.novablock.NovaBlock;
import com.nova.novablock.boss.AbstractBoss;
import com.nova.novablock.boss.BossFight;
import com.nova.novablock.util.Msg;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Piglin;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;

/**
 * Mid-Nether boss, spawns at Bastion Halls (Nether phase 6). Sits between
 * the early phases and the Ashen Warlord climax to match the Overworld's
 * three-boss rhythm.
 *
 * <p>Crossbow Piglin base with a periodic guard summon. Cooldown is shorter
 * than the Warlord's so the fight reads as "mob marshal" rather than
 * "elite duelist".
 */
public class PiglinChieftain extends AbstractBoss {

    public PiglinChieftain(NovaBlock plugin) { super(plugin); }

    @Override public String id() { return "piglin_chieftain"; }
    @Override public String displayName() { return "Piglin Chieftain"; }
    @Override public String themeColor() { return "#C7A04B"; }
    @Override protected EntityType entityType() { return EntityType.PIGLIN; }
    @Override protected double baseHealth() { return 250; }
    @Override protected double baseDamage() { return 10; }
    @Override protected BossBar.Color barColor() { return BossBar.Color.YELLOW; }

    private int summonCooldown;

    @Override
    public BossFight spawn(com.nova.novablock.island.Island island, Player trigger, Location arenaCenter) {
        BossFight fight = super.spawn(island, trigger, arenaCenter);
        if (fight == null) return null;
        if (fight.entity() instanceof Piglin piglin) {
            EntityEquipment eq = piglin.getEquipment();
            if (eq != null) {
                eq.setItemInMainHand(new ItemStack(Material.GOLDEN_SWORD));
                eq.setItemInMainHandDropChance(0f);
                eq.setHelmet(new ItemStack(Material.GOLDEN_HELMET));
                eq.setHelmetDropChance(0f);
            }
            piglin.setAdult();
            piglin.setImmuneToZombification(true);
        }
        return fight;
    }

    @Override
    public void onTick(BossFight fight) {
        LivingEntity e = fight.entity();
        if (e == null) return;
        if (--summonCooldown > 0) return;
        summonCooldown = 100; // every ~5s

        if (countNearbyGuards(e.getLocation()) >= 3) return;
        Player target = nearestParticipant(fight, e.getLocation());
        for (int i = 0; i < 2; i++) {
            Location at = e.getLocation().add(
                    (Math.random() - 0.5) * 3.5, 0.5, (Math.random() - 0.5) * 3.5);
            var guard = e.getWorld().spawnEntity(at, EntityType.PIGLIN);
            guard.setMetadata("nova_natural",
                    new org.bukkit.metadata.FixedMetadataValue(plugin, true));
            if (guard instanceof Piglin p) {
                p.setAdult();
                p.setImmuneToZombification(true);
                p.customName(Msg.mm("<#C7A04B>Chieftain's Guard"));
                p.setCustomNameVisible(true);
                EntityEquipment eq = p.getEquipment();
                if (eq != null) {
                    eq.setItemInMainHand(new ItemStack(Material.GOLDEN_SWORD));
                    eq.setItemInMainHandDropChance(0f);
                }
            }
            if (guard instanceof Mob mob && target != null) mob.setTarget(target);
            if (guard instanceof LivingEntity le) {
                le.setRemoveWhenFarAway(false);
                if (le.getAttribute(Attribute.MAX_HEALTH) != null) {
                    le.getAttribute(Attribute.MAX_HEALTH).setBaseValue(20.0);
                    le.setHealth(20.0);
                }
            }
        }
        e.getWorld().playSound(e.getLocation(), Sound.ENTITY_PIGLIN_CELEBRATE, 1.0f, 0.8f);
        for (var uid : fight.participants()) {
            Player p = org.bukkit.Bukkit.getPlayer(uid);
            if (p != null) Msg.actionBar(p, "<#C7A04B>The Chieftain summons guards!");
        }
    }

    @Override
    public long onDefeat(BossFight fight) {
        return tunedCoinReward(5000L);
    }

    private int countNearbyGuards(Location at) {
        int count = 0;
        for (var ent : at.getWorld().getNearbyEntities(at, 8, 4, 8)) {
            if (ent.getType() == EntityType.PIGLIN
                    && ent.hasMetadata("nova_natural")
                    && !ent.isDead()) count++;
        }
        return count;
    }

    private Player nearestParticipant(BossFight fight, Location loc) {
        Player best = null;
        double dist = Double.MAX_VALUE;
        for (var uid : fight.participants()) {
            Player p = org.bukkit.Bukkit.getPlayer(uid);
            if (p == null || !p.getWorld().equals(loc.getWorld())) continue;
            double d = p.getLocation().distance(loc);
            if (d < dist) { dist = d; best = p; }
        }
        return best;
    }
}
