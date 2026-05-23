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
import org.bukkit.entity.Fireball;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

public class MagmaTyrant extends AbstractBoss {

    public MagmaTyrant(NovaBlock plugin) { super(plugin); }

    @Override public String id() { return "magma_tyrant"; }
    @Override public String displayName() { return "Magma Tyrant"; }
    @Override public String themeColor() { return "#FF4D4D"; }
    @Override protected EntityType entityType() { return EntityType.BLAZE; }
    @Override protected double baseHealth() { return 200; }
    @Override protected double baseDamage() { return 8; }
    @Override protected BossBar.Color barColor() { return BossBar.Color.RED; }

    private int cooldown;
    private int phase;

    @Override
    public void onTick(BossFight fight) {
        LivingEntity e = fight.entity();
        if (e == null) return;
        // Phase 2: at 50% HP gains rapid fireball volleys
        double pct = e.getHealth() / e.getAttribute(Attribute.MAX_HEALTH).getValue();
        if (pct < 0.5 && phase < 2) {
            phase = 2;
            e.getWorld().strikeLightningEffect(e.getLocation());
            for (var uid : fight.participants()) {
                var p = org.bukkit.Bukkit.getPlayer(uid);
                if (p != null) Msg.title(p, "<red>Phase 2!", "<gray>Magma Tyrant ignites the air");
            }
        }
        if (--cooldown > 0) return;
        cooldown = phase >= 2 ? 8 : 20;
        Player target = nearestParticipant(fight, e.getLocation());
        if (target == null) return;
        Fireball fb = (Fireball) e.getWorld().spawnEntity(e.getLocation().add(0, 1, 0), EntityType.SMALL_FIREBALL);
        fb.setShooter(e);
        fb.setYield(2.5f);
        fb.setVelocity(target.getEyeLocation().toVector().subtract(e.getLocation().toVector()).normalize().multiply(1.2));
        e.getWorld().spawnParticle(Particle.LAVA, e.getLocation().add(0, 1, 0), 8);
        e.getWorld().playSound(e.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1f, 0.8f);
    }

    @Override
    public long onDefeat(BossFight fight) { return 3500L; }

    private Player nearestParticipant(BossFight fight, Location loc) {
        Player best = null;
        double dist = Double.MAX_VALUE;
        for (var uid : fight.participants()) {
            var p = org.bukkit.Bukkit.getPlayer(uid);
            if (p == null || !p.getWorld().equals(loc.getWorld())) continue;
            double d = p.getLocation().distance(loc);
            if (d < dist) { dist = d; best = p; }
        }
        return best;
    }
}
