package com.nova.novablock.boss.bosses;

import com.nova.novablock.NovaBlock;
import com.nova.novablock.boss.AbstractBoss;
import com.nova.novablock.boss.BossFight;
import com.nova.novablock.util.Msg;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.concurrent.ThreadLocalRandom;

public class VoidHerald extends AbstractBoss {

    public VoidHerald(NovaBlock plugin) { super(plugin); }

    @Override public String id() { return "void_herald"; }
    @Override public String displayName() { return "Void Herald"; }
    @Override public String themeColor() { return "#9C27B0"; }
    @Override protected EntityType entityType() { return EntityType.ENDERMAN; }
    @Override protected double baseHealth() { return 320; }
    @Override protected double baseDamage() { return 10; }
    @Override protected BossBar.Color barColor() { return BossBar.Color.PURPLE; }

    private int cooldown;

    @Override
    public void onTick(BossFight fight) {
        LivingEntity e = fight.entity();
        if (e == null) return;
        if (--cooldown > 0) return;
        cooldown = 25;
        // Random teleport-strike: teleport behind a random participant and deal physical damage
        Player target = randomParticipant(fight);
        if (target == null) return;
        Location dest = target.getLocation().clone().add(target.getLocation().getDirection().multiply(-1.5));
        dest.setY(target.getLocation().getY());
        e.getWorld().spawnParticle(Particle.PORTAL, e.getLocation(), 40, 0.5, 1, 0.5, 0.1);
        e.teleport(dest);
        e.getWorld().spawnParticle(Particle.PORTAL, dest, 40, 0.5, 1, 0.5, 0.1);
        e.getWorld().playSound(dest, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 0.7f);
        target.damage(5.0 + plugin.islands().get(fight.islandId()).data().getPhaseIndex(), e);
        Msg.actionBar(target, "<dark_purple>Void Strike!");
    }

    private Player randomParticipant(BossFight fight) {
        if (fight.participants().isEmpty()) return null;
        var ids = fight.participants().toArray(new java.util.UUID[0]);
        return org.bukkit.Bukkit.getPlayer(ids[ThreadLocalRandom.current().nextInt(ids.length)]);
    }

    @Override
    public long onDefeat(BossFight fight) { return 6000L; }
}
