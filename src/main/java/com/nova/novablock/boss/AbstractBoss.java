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

    /** bosses.yml `<id>.health` (or coded default). */
    public final double tunedHealth() {
        return plugin.configs().bosses().getDouble(id() + ".health", baseHealth());
    }
    /** bosses.yml `<id>.damage` (or coded default). */
    public final double tunedDamage() {
        return plugin.configs().bosses().getDouble(id() + ".damage", baseDamage());
    }
    /** bosses.yml `<id>.coins` (or coded default from onDefeat). */
    public final long tunedCoinReward(long defaultValue) {
        return plugin.configs().bosses().getLong(id() + ".coins", defaultValue);
    }
    /** bosses.yml `<id>.scaling-per-phase` (or 0.25). */
    public final double tunedScalingPerPhase() {
        return plugin.configs().bosses().getDouble(id() + ".scaling-per-phase", 0.25);
    }

    @Override
    public BossFight spawn(Island island, Player trigger) {
        return spawn(island, trigger, island.centerBlock());
    }

    @Override
    public BossFight spawn(Island island, Player trigger, Location arenaCenter) {
        Location base = arenaCenter == null ? island.centerBlock() : arenaCenter;
        Location loc = base.clone().add(0, 3, 0);
        if (loc.getWorld() == null) return null;
        double scaling = 1.0 + island.data().getPhaseIndex() * tunedScalingPerPhase();
        double nightmareMult = island.data().isFlag(com.nova.novablock.island.IslandFlag.NIGHTMARE_MODE) ? 2.0 : 1.0;
        return spawnAt(loc, trigger, scaling, nightmareMult, island, base.clone());
    }

    /**
     * Location-based spawn used by community raids. Caller computes scaling
     * externally; no island arena platform is created for this path.
     */
    public BossFight spawnAt(Location loc, Player trigger, double scaling, double nightmareMult,
                             Island contextIsland) {
        return spawnAt(loc, trigger, scaling, nightmareMult, contextIsland, null);
    }

    private BossFight spawnAt(Location loc, Player trigger, double scaling, double nightmareMult,
                              Island contextIsland, Location platformCenter) {
        if (loc.getWorld() == null) return null;
        var entity = loc.getWorld().spawnEntity(loc, entityType());
        if (!(entity instanceof LivingEntity le)) {
            entity.remove();
            return null;
        }
        double health = tunedHealth() * nightmareMult;
        double damage = tunedDamage() * nightmareMult;
        if (le.getAttribute(Attribute.MAX_HEALTH) != null) {
            le.getAttribute(Attribute.MAX_HEALTH).setBaseValue(health * scaling);
            le.setHealth(health * scaling);
        }
        if (le.getAttribute(Attribute.ATTACK_DAMAGE) != null) {
            le.getAttribute(Attribute.ATTACK_DAMAGE).setBaseValue(damage * scaling);
        }
        if (le.getAttribute(Attribute.KNOCKBACK_RESISTANCE) != null) {
            le.getAttribute(Attribute.KNOCKBACK_RESISTANCE).setBaseValue(1.0);
        }
        le.setRemoveWhenFarAway(false);
        le.setPersistent(true);
        le.customName(com.nova.novablock.util.Msg.mm("<" + themeColor() + "><bold>" + displayName()));
        le.setCustomNameVisible(true);
        if (le instanceof Mob mob && trigger != null) mob.setTarget(trigger);

        le.getPersistentDataContainer().set(BossManager.BOSS_KEY, PersistentDataType.STRING, id());

        BossBar bar = BossBar.bossBar(
                com.nova.novablock.util.Msg.mm("<" + themeColor() + "><bold>" + displayName()),
                1.0f, barColor(), BossBar.Overlay.PROGRESS);

        BossFight fight = new BossFight(this, contextIsland, le, bar);
        fight.setArenaCenter(loc.clone());
        if (platformCenter != null) {
            buildArenaPlatform(fight, platformCenter);
        }
        le.getPersistentDataContainer().set(BossManager.FIGHT_KEY, PersistentDataType.STRING, le.getUniqueId().toString());
        return fight;
    }

    /**
     * Place a temporary 5x5 cobblestone arena at the OneBlock platform level so
     * island boss fights have guaranteed footing. Original blocks are restored
     * when the fight ends.
     */
    private void buildArenaPlatform(BossFight fight, Location center) {
        if (center.getWorld() == null) return;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (dx == 0 && dz == 0) continue;
                org.bukkit.block.Block b = center.clone().add(dx, 0, dz).getBlock();
                if (b.getType() != org.bukkit.Material.AIR) continue;
                fight.arenaSnapshot().add(b.getState());
                b.setType(org.bukkit.Material.COBBLESTONE, false);
            }
        }
    }
}
