package com.nova.novablock.boss;

import com.nova.novablock.NovaBlock;
import com.nova.novablock.boss.bosses.FrostbornSentinel;
import com.nova.novablock.boss.bosses.MagmaTyrant;
import com.nova.novablock.boss.bosses.VoidHerald;
import com.nova.novablock.island.Island;
import com.nova.novablock.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class BossManager implements Listener {

    public static final NamespacedKey BOSS_KEY = new NamespacedKey("novablock", "boss_id");
    public static final NamespacedKey FIGHT_KEY = new NamespacedKey("novablock", "boss_fight");

    private final NovaBlock plugin;
    private final Map<String, Boss> registry = new HashMap<>();
    private final Map<UUID, BossFight> active = new HashMap<>();
    private BukkitTask tickTask;

    public BossManager(NovaBlock plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        tickTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickAll, 10L, 10L);
    }

    public void registerDefaultBosses() {
        register(new MagmaTyrant(plugin));
        register(new FrostbornSentinel(plugin));
        register(new VoidHerald(plugin));
    }

    public void register(Boss boss) { registry.put(boss.id(), boss); }

    public Boss byId(String id) { return registry.get(id); }
    public int bossCount() { return registry.size(); }

    public BossFight spawn(String id, Island island, Player triggering) {
        Boss boss = registry.get(id);
        if (boss == null) return null;
        BossFight fight = boss.spawn(island, triggering);
        if (fight == null) return null;
        active.put(fight.entityId(), fight);
        fight.addParticipant(triggering);
        triggering.playSound(triggering.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1f, 0.6f);
        Msg.title(triggering, "<" + boss.themeColor() + ">⚔ " + boss.displayName(), "<gray>Boss encounter!");
        return fight;
    }

    private void tickAll() {
        for (var it = active.entrySet().iterator(); it.hasNext(); ) {
            BossFight fight = it.next().getValue();
            LivingEntity entity = fight.entity();
            if (entity == null || entity.isDead()) { fight.clearBar(); it.remove(); continue; }
            fight.syncBar();
            try { fight.boss().onTick(fight); } catch (Throwable t) {
                plugin.getLogger().warning("Boss tick error " + fight.boss().id() + ": " + t);
            }
            // Tether: pull bosses back to spawn if they get punched off the platform
            // or fall into the void. Combined with maxed knockback resistance this
            // keeps the fight in one place.
            var center = fight.arenaCenter();
            if (center != null && entity.getWorld().equals(center.getWorld())) {
                double horizDist = Math.hypot(
                        entity.getLocation().getX() - center.getX(),
                        entity.getLocation().getZ() - center.getZ());
                boolean tooFar = horizDist > 14;
                boolean tooLow = entity.getLocation().getY() < center.getY() - 8;
                if (tooFar || tooLow) {
                    entity.teleport(center);
                    entity.setFallDistance(0);
                }
            }
            // Auto-add nearby owners to bossbar
            Island island = plugin.islands().get(fight.islandId());
            if (island != null) {
                for (UUID memberId : island.data().getMembers()) {
                    Player m = Bukkit.getPlayer(memberId);
                    if (m != null && m.getLocation().distance(entity.getLocation()) < 32) {
                        fight.addParticipant(m);
                    }
                }
            }
        }
    }

    @EventHandler
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!event.getEntity().getPersistentDataContainer().has(FIGHT_KEY, PersistentDataType.STRING)) return;
        String fid = event.getEntity().getPersistentDataContainer().get(FIGHT_KEY, PersistentDataType.STRING);
        BossFight fight = active.get(UUID.fromString(fid));
        if (fight == null) return;
        Player attacker = resolvePlayer(event.getDamager());
        if (attacker != null) {
            fight.addParticipant(attacker);
            fight.boss().onDamaged(fight, attacker, event.getFinalDamage());
            plugin.progression().addXp(attacker, com.nova.novablock.progression.SkillType.COMBAT, 3L);
        }
    }

    @EventHandler
    public void onDeath(EntityDeathEvent event) {
        if (!event.getEntity().getPersistentDataContainer().has(FIGHT_KEY, PersistentDataType.STRING)) return;
        String fid = event.getEntity().getPersistentDataContainer().get(FIGHT_KEY, PersistentDataType.STRING);
        BossFight fight = active.remove(UUID.fromString(fid));
        if (fight == null) return;
        fight.clearBar();
        long coins = fight.boss().onDefeat(fight);
        Island island = plugin.islands().get(fight.islandId());
        if (island != null) plugin.economy().award(island, coins);
        for (UUID id : fight.participants()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                Msg.title(p, "<gold>★ " + fight.boss().displayName() + " defeated!", "<yellow>+" + coins + " coins");
                p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
                plugin.progression().addXp(p, com.nova.novablock.progression.SkillType.COMBAT, 200L);
                plugin.quests().onBossKilled(p);
            }
        }
    }

    private Player resolvePlayer(org.bukkit.entity.Entity damager) {
        if (damager instanceof Player p) return p;
        if (damager instanceof org.bukkit.entity.Projectile proj
                && proj.getShooter() instanceof Player p) return p;
        return null;
    }

    public void shutdown() {
        if (tickTask != null) tickTask.cancel();
        for (BossFight f : active.values()) {
            f.clearBar();
            LivingEntity e = f.entity();
            if (e != null) e.remove();
        }
        active.clear();
    }

    /**
     * Escape valve: if any single participant dies 3 times during a fight, the
     * boss withdraws and everyone who took part gets a participation reward
     * (40% of full kill coins). Prevents a too-hard encounter from grinding the
     * island to a halt.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        for (BossFight fight : new java.util.ArrayList<>(active.values())) {
            if (!fight.participants().contains(victim.getUniqueId())) continue;
            int deaths = fight.recordDeath(victim.getUniqueId());
            if (deaths < 3) {
                org.bukkit.entity.LivingEntity entity = fight.entity();
                int hp = entity == null ? 0 : (int) entity.getHealth();
                Msg.title(victim, "<red>Death " + deaths + "/3",
                        "<gray>One more and the boss withdraws.");
                continue;
            }
            // 3rd death — despawn boss, give participation rewards, end fight.
            active.remove(fight.entityId());
            org.bukkit.entity.LivingEntity entity = fight.entity();
            if (entity != null) entity.remove();
            fight.clearBar();

            com.nova.novablock.island.Island island = plugin.islands().get(fight.islandId());
            long fullReward = fight.boss().onDefeat(fight);
            long participation = Math.max(50, (long) (fullReward * 0.4));
            if (island != null) plugin.economy().award(island, participation);

            for (UUID id : fight.participants()) {
                Player p = Bukkit.getPlayer(id);
                if (p == null) continue;
                Msg.title(p, "<gray>" + fight.boss().displayName() + " withdrew",
                        "<yellow>+" + participation + " coins <gray>(participation)");
                p.playSound(p.getLocation(), Sound.ENTITY_WITHER_HURT, 0.6f, 0.7f);
                plugin.progression().addXp(p, com.nova.novablock.progression.SkillType.COMBAT, 50L);
            }
        }
    }
}
