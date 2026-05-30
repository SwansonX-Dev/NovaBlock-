package com.nova.novablock.boss.bosses;

import com.nova.novablock.NovaBlock;
import com.nova.novablock.boss.AbstractBoss;
import com.nova.novablock.boss.BossFight;
import com.nova.novablock.util.Msg;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;

/**
 * Climax boss of the Nether OneBlock. Piglin Brute base — heavy melee with
 * Zombified Piglin minion summons every ~6 seconds. At &lt; 40 % HP enters
 * "ashen" phase: equips a Netherite Axe and gains a +15 % melee multiplier.
 */
public class AshenWarlord extends AbstractBoss {

    public AshenWarlord(NovaBlock plugin) { super(plugin); }

    @Override public String id() { return "ashen_warlord"; }
    @Override public String displayName() { return "Ashen Warlord"; }
    @Override public String themeColor() { return "#FF6347"; }
    @Override protected EntityType entityType() { return EntityType.PIGLIN_BRUTE; }
    @Override protected double baseHealth() { return 400; }
    @Override protected double baseDamage() { return 14; }
    @Override protected BossBar.Color barColor() { return BossBar.Color.PURPLE; }

    private int summonCooldown;
    private int phase;

    @Override
    public void onTick(BossFight fight) {
        LivingEntity e = fight.entity();
        if (e == null) return;

        var maxHpAttr = e.getAttribute(Attribute.MAX_HEALTH);
        double maxHp = maxHpAttr == null ? baseHealth() : maxHpAttr.getValue();
        double pct = e.getHealth() / maxHp;
        if (pct < 0.40 && phase < 2) {
            phase = 2;
            EntityEquipment eq = e.getEquipment();
            if (eq != null) {
                eq.setItemInMainHand(new ItemStack(org.bukkit.Material.NETHERITE_AXE));
                eq.setItemInMainHandDropChance(0f);
            }
            var attackAttr = e.getAttribute(Attribute.ATTACK_DAMAGE);
            if (attackAttr != null) attackAttr.setBaseValue(attackAttr.getValue() * 1.15);
            e.getWorld().strikeLightningEffect(e.getLocation());
            e.getWorld().spawnParticle(Particle.FLAME, e.getLocation().add(0, 1, 0), 60, 0.5, 1.0, 0.5, 0.05);
            for (var uid : fight.participants()) {
                Player p = org.bukkit.Bukkit.getPlayer(uid);
                if (p != null) Msg.title(p, "<#FF6347>Ashen Phase",
                        "<gray>The Warlord wields his Netherite axe.");
            }
        }

        if (--summonCooldown > 0) return;
        summonCooldown = 120; // every 6s
        if (countNearbyMinions(fight, e.getLocation()) >= 2) return;
        for (int i = 0; i < 2; i++) {
            Location at = e.getLocation().add(
                    (Math.random() - 0.5) * 4.0, 0.5, (Math.random() - 0.5) * 4.0);
            var minion = e.getWorld().spawnEntity(at, EntityType.ZOMBIFIED_PIGLIN);
            minion.setMetadata("nova_natural",
                    new org.bukkit.metadata.FixedMetadataValue(plugin, true));
            if (minion instanceof Mob mob) {
                Player target = nearestParticipant(fight, e.getLocation());
                if (target != null) mob.setTarget(target);
            }
            if (minion instanceof LivingEntity le) {
                le.setRemoveWhenFarAway(false);
                if (le.getAttribute(Attribute.MAX_HEALTH) != null) {
                    le.getAttribute(Attribute.MAX_HEALTH).setBaseValue(28.0);
                    le.setHealth(28.0);
                }
            }
        }
        e.getWorld().playSound(e.getLocation(), Sound.ENTITY_PIGLIN_BRUTE_ANGRY, 1.2f, 0.7f);
    }

    @Override
    public long onDefeat(BossFight fight) {
        // Reward broadcast handled by BossManager.onDeath; this is the coin lump.
        return tunedCoinReward(8000L);
    }

    private int countNearbyMinions(BossFight fight, Location at) {
        int count = 0;
        for (var ent : at.getWorld().getNearbyEntities(at, 8, 4, 8)) {
            if (ent.getType() == EntityType.ZOMBIFIED_PIGLIN
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
