package com.nova.novablock.boss;

import com.nova.novablock.NovaBlock;
import com.nova.novablock.island.Island;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

public abstract class AbstractBoss implements Boss {

    protected final NovaBlock plugin;

    protected AbstractBoss(NovaBlock plugin) { this.plugin = plugin; }

    protected abstract EntityType entityType();
    protected abstract double baseHealth();
    protected abstract double baseDamage();
    protected abstract BossBar.Color barColor();

    @Override
    public BossFight spawn(Island island, Player trigger) {
        Location loc = island.centerBlock().clone().add(0, 3, 0);
        var entity = loc.getWorld().spawnEntity(loc, entityType());
        if (!(entity instanceof LivingEntity le)) {
            entity.remove();
            return null;
        }
        // Scaling: phase 5 boss is ~2.25x stats, phase 11 boss ~3.75x.
        // Tuned to stay challenging but not chip-fest at high phases.
        double scaling = 1.0 + island.data().getPhaseIndex() * 0.25;
        if (le.getAttribute(Attribute.MAX_HEALTH) != null) {
            le.getAttribute(Attribute.MAX_HEALTH).setBaseValue(baseHealth() * scaling);
            le.setHealth(baseHealth() * scaling);
        }
        if (le.getAttribute(Attribute.ATTACK_DAMAGE) != null) {
            le.getAttribute(Attribute.ATTACK_DAMAGE).setBaseValue(baseDamage() * scaling);
        }
        le.setRemoveWhenFarAway(false);
        le.setPersistent(true);
        le.customName(com.nova.novablock.util.Msg.mm("<" + themeColor() + "><bold>" + displayName()));
        le.setCustomNameVisible(true);
        if (le instanceof Mob mob) mob.setTarget(trigger);

        le.getPersistentDataContainer().set(BossManager.BOSS_KEY, PersistentDataType.STRING, id());

        BossBar bar = BossBar.bossBar(
                com.nova.novablock.util.Msg.mm("<" + themeColor() + "><bold>" + displayName()),
                1.0f, barColor(), BossBar.Overlay.PROGRESS);

        BossFight fight = new BossFight(this, island, le, bar);
        le.getPersistentDataContainer().set(BossManager.FIGHT_KEY, PersistentDataType.STRING, le.getUniqueId().toString());
        return fight;
    }
}
