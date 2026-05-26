package com.nova.novablock.boss.bosses;

import com.nova.novablock.NovaBlock;
import com.nova.novablock.boss.AbstractBoss;
import com.nova.novablock.boss.BossFight;
import com.nova.novablock.util.Msg;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class FrostbornSentinel extends AbstractBoss {

    public FrostbornSentinel(NovaBlock plugin) { super(plugin); }

    @Override public String id() { return "frostborn_sentinel"; }
    @Override public String displayName() { return "Frostborn Sentinel"; }
    @Override public String themeColor() { return "#A0E7FF"; }
    @Override protected EntityType entityType() { return EntityType.STRAY; }
    @Override protected double baseHealth() { return 180; }
    @Override protected double baseDamage() { return 6; }
    @Override protected BossBar.Color barColor() { return BossBar.Color.BLUE; }

    private int cooldown;

    @Override
    public void onTick(BossFight fight) {
        LivingEntity e = fight.entity();
        if (e == null) return;
        if (--cooldown > 0) return;
        cooldown = 30;
        // Frost nova: slow + mining-fatigue all participants within 10
        for (var uid : fight.participants()) {
            Player p = org.bukkit.Bukkit.getPlayer(uid);
            if (p == null) continue;
            if (p.getLocation().distance(e.getLocation()) > 12) continue;
            p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 1, true, false));
            p.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, 60, 0, true, false));
            Msg.actionBar(p, "<aqua>Frost Nova!");
        }
        e.getWorld().spawnParticle(Particle.SNOWFLAKE, e.getLocation(), 60, 3, 1, 3, 0.05);
        e.getWorld().playSound(e.getLocation(), Sound.BLOCK_GLASS_BREAK, 1f, 0.6f);
    }

    @Override
    public long onDefeat(BossFight fight) { return tunedCoinReward(4000L); }
}
