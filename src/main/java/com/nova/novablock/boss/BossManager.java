package com.nova.novablock.boss;

import com.nova.novablock.NovaBlock;
import com.nova.novablock.boss.bosses.FrostbornSentinel;
import com.nova.novablock.boss.bosses.MagmaTyrant;
import com.nova.novablock.boss.bosses.VoidHerald;
import com.nova.novablock.island.Island;
import com.nova.novablock.progression.Perk;
import com.nova.novablock.util.Msg;
import org.bukkit.attribute.Attribute;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
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
    private static final double ARENA_RADIUS = 8.0;
    private static final double MIN_Y_OFFSET = -4.0;
    private static final double MAX_Y_OFFSET = 6.0;

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
        register(new com.nova.novablock.boss.bosses.AshenWarlord(plugin));
        register(new com.nova.novablock.boss.bosses.PiglinChieftain(plugin));
    }

    /**
     * Sweep for orphan boss entities left over from a crash — any entity carrying
     * our {@link #BOSS_KEY} PDC but not in the live {@code active} map is removed
     * so the player doesn't fight an unrewarding zombie boss after a restart.
     */
    public void cleanupOrphans() {
        int removed = 0;
        for (org.bukkit.World w : Bukkit.getWorlds()) {
            for (org.bukkit.entity.Entity e : w.getEntities()) {
                if (!e.getPersistentDataContainer().has(BOSS_KEY, PersistentDataType.STRING)) continue;
                String fid = e.getPersistentDataContainer().get(FIGHT_KEY, PersistentDataType.STRING);
                if (fid != null && active.containsKey(java.util.UUID.fromString(fid))) continue;
                e.remove();
                removed++;
            }
        }
        if (removed > 0) plugin.getLogger().info("Removed " + removed + " orphan boss entities from prior session.");
    }

    public void register(Boss boss) { registry.put(boss.id(), boss); }

    public Boss byId(String id) { return registry.get(id); }
    public int bossCount() { return registry.size(); }
    public java.util.Set<String> bossIds() { return java.util.Collections.unmodifiableSet(registry.keySet()); }

    public BossFight spawn(String id, Island island, Player triggering) {
        return spawn(id, island, triggering, false);
    }

    public BossFight spawn(String id, Island island, Player triggering, boolean inNether) {
        Boss boss = registry.get(id);
        if (boss == null) return null;
        org.bukkit.Location arenaCenter = inNether ? island.netherCenterBlock() : island.centerBlock();
        BossFight fight = boss.spawn(island, triggering, arenaCenter);
        if (fight == null) return null;
        active.put(fight.entityId(), fight);
        fight.addParticipant(triggering);
        triggering.playSound(triggering.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1f, 0.6f);
        Msg.title(triggering, "<" + boss.themeColor() + ">⚔ " + boss.displayName(), "<gray>Boss encounter!");
        return fight;
    }

    /**
     * Location-driven spawn for fights that aren't tied to an island (e.g. the
     * community raid at /warp spawn). {@code triggering} may be null when the
     * spawn is purely scheduled (no specific player to acquire as initial target).
     */
    public BossFight spawnAtLocation(String id, org.bukkit.Location at, Player triggering,
                                     double scaling, double nightmareMult) {
        Boss boss = registry.get(id);
        if (!(boss instanceof AbstractBoss ab)) return null;
        BossFight fight = ab.spawnAt(at, triggering, scaling, nightmareMult, null);
        if (fight == null) return null;
        active.put(fight.entityId(), fight);
        if (triggering != null) {
            fight.addParticipant(triggering);
            triggering.playSound(triggering.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1f, 0.6f);
        }
        return fight;
    }

    public BossFight fightOf(UUID entityId) { return active.get(entityId); }

    private void tickAll() {
        for (var it = active.entrySet().iterator(); it.hasNext(); ) {
            BossFight fight = it.next().getValue();
            LivingEntity entity = fight.entity();
            if (entity == null || entity.isDead()) { fight.clearBar(); it.remove(); continue; }
            fight.syncBar();
            try { fight.boss().onTick(fight); } catch (Throwable t) {
                plugin.getLogger().warning("Boss tick error " + fight.boss().id() + ": " + t);
            }
            // Arena boundary: bosses may move around the fight area, but never leave it.
            var center = fight.arenaCenter();
            if (center != null) keepInsideArena(entity, center);
            // Auto-add nearby owners to bossbar
            if (fight.islandId() != null) {
                Island island = plugin.islands().get(fight.islandId());
                if (island != null) {
                    for (UUID memberId : island.data().getMembers()) {
                        Player m = Bukkit.getPlayer(memberId);
                        if (m != null && m.getLocation().distance(entity.getLocation()) < 32) {
                            fight.addParticipant(m);
                        }
                    }
                }
            } else {
                // Raid fight: any online player within 32 blocks joins the bossbar (sightseers).
                // Damage is still required to share rewards — that's enforced in RaidScheduler.
                for (Player m : Bukkit.getOnlinePlayers()) {
                    if (m.getWorld().equals(entity.getWorld())
                            && m.getLocation().distance(entity.getLocation()) < 32) {
                        fight.addParticipant(m);
                    }
                }
            }
        }
    }

    private void keepInsideArena(LivingEntity entity, org.bukkit.Location center) {
        if (center.getWorld() == null) return;
        boolean wrongWorld = !entity.getWorld().equals(center.getWorld());
        double horizDist = wrongWorld ? Double.MAX_VALUE : Math.hypot(
                entity.getLocation().getX() - center.getX(),
                entity.getLocation().getZ() - center.getZ());
        double dy = wrongWorld ? 0.0 : entity.getLocation().getY() - center.getY();
        boolean outOfBounds = wrongWorld
                || horizDist > ARENA_RADIUS
                || dy < MIN_Y_OFFSET
                || dy > MAX_Y_OFFSET;
        if (!outOfBounds) return;

        entity.teleport(center);
        entity.setVelocity(new org.bukkit.util.Vector(0, 0, 0));
        entity.setFallDistance(0);
        if (entity instanceof org.bukkit.entity.Mob mob) {
            mob.setTarget(null);
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
            applyCombatPerks(event, fight, attacker);
            fight.boss().onDamaged(fight, attacker, event.getFinalDamage());
            // Per-fight damage hook — raid fights override this to record contribution.
            fight.recordDamage(attacker, event.getFinalDamage());
            plugin.progression().addXp(attacker, com.nova.novablock.progression.SkillType.COMBAT, 3L);
        }
    }

    /** BERSERKER, EXECUTIONER (damage), STAGGER (slowness). */
    private void applyCombatPerks(EntityDamageByEntityEvent event, BossFight fight, Player attacker) {
        var prog = plugin.progression().get(attacker);
        double mult = 1.0;
        if (Perk.hasPerk(prog, Perk.BERSERKER)) mult *= 1.15;
        if (Perk.hasPerk(prog, Perk.EXECUTIONER)) {
            LivingEntity e = fight.entity();
            if (e != null) {
                var maxAttr = e.getAttribute(Attribute.MAX_HEALTH);
                double max = maxAttr == null ? 1.0 : maxAttr.getValue();
                if (max > 0 && e.getHealth() / max < 0.25) mult *= 1.50;
            }
        }
        if (mult > 1.0) event.setDamage(event.getDamage() * mult);
        if (Perk.hasPerk(prog, Perk.STAGGER) && event.getEntity() instanceof LivingEntity le) {
            // Brief slowness so the boss visibly hitches between strikes.
            le.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 20, 1, true, false, false));
        }
    }

    @EventHandler
    public void onDeath(EntityDeathEvent event) {
        if (!event.getEntity().getPersistentDataContainer().has(FIGHT_KEY, PersistentDataType.STRING)) return;
        String fid = event.getEntity().getPersistentDataContainer().get(FIGHT_KEY, PersistentDataType.STRING);
        BossFight fight = active.remove(UUID.fromString(fid));
        if (fight == null) return;
        restoreArena(fight);
        long coins = fight.boss().onDefeat(fight);
        // Raid path: fight has no island, distribution is handled externally.
        if (fight.distributeRewardsOnDeath(coins)) return;
        if (fight.islandId() == null) return;

        Island island = plugin.islands().get(fight.islandId());
        if (island != null) {
            // BOSS_LOOT upgrade: +20% coin per level.
            int lvl = island.data().getUpgradeLevel(com.nova.novablock.island.IslandUpgrade.BOSS_LOOT);
            if (lvl > 0) coins = Math.round(coins * (1.0 + 0.20 * lvl));
            plugin.economy().award(island, coins);
        }
        for (UUID id : fight.participants()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                Msg.title(p, "<gold>★ " + fight.boss().displayName() + " defeated!", "<yellow>+" + coins + " coins");
                p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
                plugin.progression().addXp(p, com.nova.novablock.progression.SkillType.COMBAT, 200L);
                // SECOND_WIND (Combat 20): heal 4HP on boss kill.
                if (Perk.hasPerk(plugin.progression().get(p), Perk.SECOND_WIND)) {
                    var maxAttr = p.getAttribute(Attribute.MAX_HEALTH);
                    double max = maxAttr == null ? 20.0 : maxAttr.getValue();
                    p.setHealth(Math.min(max, p.getHealth() + 4.0));
                    Msg.actionBar(p, "<red>♥ Second Wind <gray>(+4HP)");
                }
                plugin.quests().onBossKilled(p);
                plugin.seasonalPaths().award(p, com.nova.novablock.season.SeasonalPathManager.PathSource.BOSS, 100);
            }
        }
        fight.clearBar();
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
            restoreArena(f);
            LivingEntity e = f.entity();
            if (e != null) e.remove();
        }
        active.clear();
    }

    /** Restore the cobblestone arena footprint back to whatever was there before. */
    private void restoreArena(BossFight fight) {
        for (org.bukkit.block.BlockState st : fight.arenaSnapshot()) {
            // Force-restore so a player who broke the cobble doesn't leave a hole.
            st.update(true, false);
        }
        fight.arenaSnapshot().clear();
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
            restoreArena(fight);

            long fullReward = fight.boss().onDefeat(fight);
            long participation = Math.max(50, (long) (fullReward * 0.4));
            com.nova.novablock.island.Island island = fight.islandId() == null
                    ? null : plugin.islands().get(fight.islandId());
            if (island != null) plugin.economy().award(island, participation);

            for (UUID id : fight.participants()) {
                Player p = Bukkit.getPlayer(id);
                if (p == null) continue;
                if (island != null) {
                    Msg.title(p, "<gray>" + fight.boss().displayName() + " withdrew",
                            "<yellow>+" + participation + " coins <gray>(participation)");
                } else {
                    Msg.title(p, "<gray>" + fight.boss().displayName() + " withdrew",
                            "<gray>The raid ended without a winner.");
                }
                p.playSound(p.getLocation(), Sound.ENTITY_WITHER_HURT, 0.6f, 0.7f);
                plugin.progression().addXp(p, com.nova.novablock.progression.SkillType.COMBAT, 50L);
            }
            fight.clearBar();
        }
    }
}
