package com.nova.novablock.community;

import com.nova.novablock.NovaBlock;
import com.nova.novablock.boss.BossFight;
import com.nova.novablock.boss.BossManager;
import com.nova.novablock.util.Msg;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Scheduler for periodic community raid bosses spawned at the hub. Maintains
 * a parallel damage-tracking map so we can distribute loot on death; piggybacks
 * on {@link BossManager}'s spawn/tick/death flow but routes rewards externally.
 */
public class RaidScheduler implements Listener {

    private final NovaBlock plugin;

    /** Active raid context, keyed by boss entity UUID. */
    private final Map<UUID, RaidContext> active = new HashMap<>();
    /** Wall-clock timestamp of the next planned raid attempt. */
    private long nextRaidAt;
    /** Last completed/ended raid timestamp — persisted so restarts respect cooldown. */
    private long lastRaidEndedAt;
    private boolean warnedFiveMinutes;
    private boolean warnedOneMinute;

    public RaidScheduler(NovaBlock plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        this.nextRaidAt = computeNextRaidTime(System.currentTimeMillis());
    }

    public long lastRaidEndedAt() { return lastRaidEndedAt; }
    public void setLastRaidEndedAt(long v) {
        this.lastRaidEndedAt = v;
        if (v > 0) this.nextRaidAt = computeNextRaidTime(v);
    }
    public long nextRaidAt() { return nextRaidAt; }

    /** Called by EventManager every minute. Spawns/expires raids. */
    public void tick() {
        if (!isEnabled()) return;
        long now = System.currentTimeMillis();

        // Despawn no-shows.
        long despawnAfterMs = plugin.getConfig().getLong("community.raids.despawn-after-minutes", 10) * 60_000L;
        for (var it = active.entrySet().iterator(); it.hasNext(); ) {
            var entry = it.next();
            RaidContext ctx = entry.getValue();
            Entity e = Bukkit.getEntity(entry.getKey());
            if (e == null || e.isDead()) {
                it.remove();
                continue;
            }
            if (now - ctx.spawnedAt > despawnAfterMs && ctx.damageByPlayer.isEmpty()) {
                e.remove();
                it.remove();
                lastRaidEndedAt = now;
                Bukkit.broadcast(Msg.mm("<gray>The community raid boss withdrew — nobody showed up."));
                nextRaidAt = computeNextRaidTime(now);
                resetWarnings();
                markDirty();
            }
        }

        // Only one active raid at a time. Spawn next when due.
        if (active.isEmpty() && now >= nextRaidAt) {
            spawnRaid();
            nextRaidAt = computeNextRaidTime(now);
            resetWarnings();
        } else if (active.isEmpty()) {
            tickCountdownWarnings(now);
        }
    }

    /** Manually trigger a raid right now (admin command path). */
    public BossFight spawnRaidNow() {
        BossFight fight = spawnRaid();
        if (fight != null) {
            nextRaidAt = computeNextRaidTime(System.currentTimeMillis());
            resetWarnings();
        }
        return fight;
    }

    private BossFight spawnRaid() {
        Location at = plugin.community() == null ? null : plugin.community().hubSpawnLocation();
        if (at == null) {
            plugin.getLogger().warning("Raid skipped: no community hub location available.");
            return null;
        }
        at = at.clone().add(0, 2, 0);

        String bossId = plugin.getConfig().getString("community.raids.boss-id", "random");
        if (bossId.equalsIgnoreCase("random")) {
            var ids = new ArrayList<>(plugin.bosses().bossIds());
            if (ids.isEmpty()) {
                plugin.getLogger().warning("Raid skipped: no bosses registered.");
                return null;
            }
            bossId = ids.get(ThreadLocalRandom.current().nextInt(ids.size()));
        }

        int online = Math.max(1, Bukkit.getOnlinePlayers().size());
        var cfg = plugin.getConfig();
        double baseMult = cfg.getDouble("community.raids.scaling.base-multiplier", 2.5);
        double perPlayer = cfg.getDouble("community.raids.scaling.per-online-player", 0.10);
        double maxOnline = cfg.getDouble("community.raids.scaling.max-online-player-multiplier", 4.0);
        double onlineMult = Math.min(maxOnline, 1.0 + perPlayer * (online - 1));
        int communityPhase = plugin.community() == null ? 0 : plugin.community().block().phaseIndex();
        double phaseMult = 1.0 + communityPhase * 0.25;
        double scaling = baseMult * onlineMult * phaseMult;

        BossFight fight = plugin.bosses().spawnAtLocation(bossId, at, null, scaling, 1.0);
        if (fight == null) return null;

        RaidContext ctx = new RaidContext(bossId, System.currentTimeMillis());
        active.put(fight.entityId(), ctx);

        announceRaid(fight, at);
        return fight;
    }

    private void announceRaid(BossFight fight, Location at) {
        var bossName = fight.boss().displayName();
        var color = fight.boss().themeColor();
        Bukkit.broadcast(Msg.mm("<gold>✦ <" + color + "><bold>COMMUNITY RAID<reset><gold> ✦"));
        Bukkit.broadcast(Msg.mm("<" + color + ">" + bossName + " <gray>has appeared at <yellow>/warp community<gray>!"));
        Component tp = Component.text("[Teleport to raid]", NamedTextColor.YELLOW)
                .clickEvent(ClickEvent.runCommand("/warp community"));
        Bukkit.broadcast(tp);
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.playSound(p.getLocation(), org.bukkit.Sound.EVENT_RAID_HORN, 1f, 1f);
        }
    }

    private long computeNextRaidTime(long fromMillis) {
        long intervalMin = Math.max(5, plugin.getConfig().getLong("community.raids.interval-minutes", 120));
        int jitterPct = Math.max(0, plugin.getConfig().getInt("community.raids.interval-jitter-percent", 20));
        long base = intervalMin * 60_000L;
        long jitter = base * jitterPct / 100L;
        long offset = jitter == 0 ? 0L
                : ThreadLocalRandom.current().nextLong(-jitter, jitter + 1);
        return Math.max(fromMillis + (intervalMin * 60_000L / 2), fromMillis + base + offset);
    }

    private void tickCountdownWarnings(long now) {
        long remaining = nextRaidAt - now;
        if (!warnedFiveMinutes && remaining <= 5L * 60_000L && remaining > 60_000L) {
            warnedFiveMinutes = true;
            Bukkit.broadcast(Msg.mm("<gold>✦ <yellow>Community raid in <white>5 minutes<yellow>. <gray>Meet at <yellow>/warp community<gray>."));
            return;
        }
        if (!warnedOneMinute && remaining <= 60_000L && remaining > 0L) {
            warnedOneMinute = true;
            Bukkit.broadcast(Msg.mm("<gold>✦ <yellow>Community raid in <white>1 minute<yellow>. <gray>Meet at <yellow>/warp community<gray>."));
        }
    }

    private void resetWarnings() {
        warnedFiveMinutes = false;
        warnedOneMinute = false;
    }

    // ---- damage / death routing ----

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        RaidContext ctx = active.get(event.getEntity().getUniqueId());
        if (ctx == null) return;
        Player attacker = resolvePlayer(event.getDamager());
        if (attacker == null) return;
        ctx.damageByPlayer.merge(attacker.getUniqueId(), event.getFinalDamage(), Double::sum);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(EntityDeathEvent event) {
        RaidContext ctx = active.remove(event.getEntity().getUniqueId());
        if (ctx == null) return;
        // The vanilla loot drop is suppressed; loot is distributed manually.
        event.getDrops().clear();
        distributeLoot(ctx, event.getEntity());
        lastRaidEndedAt = System.currentTimeMillis();
        nextRaidAt = computeNextRaidTime(lastRaidEndedAt);
        resetWarnings();
        markDirty();
    }

    private void markDirty() {
        if (plugin.community() != null) plugin.community().markDirty();
    }

    private void distributeLoot(RaidContext ctx, LivingEntity entity) {
        var cfg = plugin.getConfig();
        int online = Math.max(1, Bukkit.getOnlinePlayers().size());
        long basePool = cfg.getLong("community.raids.rewards.base-coin-pool", 50_000);
        long perPlayer = cfg.getLong("community.raids.rewards.pool-per-online-player", 2_500);
        long totalPool = basePool + perPlayer * (online - 1);
        double minFraction = cfg.getDouble("community.raids.rewards.min-damage-fraction", 0.02);

        double totalDamage = ctx.damageByPlayer.values().stream().mapToDouble(Double::doubleValue).sum();
        if (totalDamage <= 0) {
            Bukkit.broadcast(Msg.mm("<gold>✦ <gray>Raid boss defeated, but nobody contributed damage."));
            return;
        }

        List<Map.Entry<UUID, Double>> ranked = new ArrayList<>(ctx.damageByPlayer.entrySet());
        ranked.sort(Comparator.comparingDouble((Map.Entry<UUID, Double> e) -> e.getValue()).reversed());

        Bukkit.broadcast(Msg.mm("<gold>✦ <yellow>Community raid boss defeated! <gray>Loot distributing..."));
        boolean topGetsSpecial = true;
        for (var e : ranked) {
            double share = e.getValue() / totalDamage;
            if (share < minFraction) continue;
            long coins = Math.round(totalPool * share);
            OfflinePlayer op = Bukkit.getOfflinePlayer(e.getKey());
            plugin.economy().deposit(op, coins);
            Player online2 = op.getPlayer();
            if (online2 != null) {
                Msg.send(online2, "<gold>★ Raid reward: <yellow>+" + coins
                        + " <gold>coins <gray>(" + Math.round(share * 100) + "% damage)");
            }
            if (topGetsSpecial) {
                topGetsSpecial = false;
                ItemStack drop = parseTopDrop();
                if (drop != null) {
                    if (online2 != null && online2.isOnline()) {
                        var overflow = online2.getInventory().addItem(drop);
                        for (ItemStack leftover : overflow.values()) {
                            online2.getWorld().dropItemNaturally(online2.getLocation(), leftover);
                        }
                        Msg.send(online2, "<gold>✦ Top damage bonus: <yellow>"
                                + drop.getAmount() + "x " + drop.getType().name());
                    } else {
                        entity.getWorld().dropItemNaturally(entity.getLocation(), drop);
                    }
                }
            }
        }
    }

    private ItemStack parseTopDrop() {
        String raw = plugin.getConfig().getString("community.raids.rewards.top-damage-drop", "NETHER_STAR:1");
        if (raw == null || raw.isBlank()) return null;
        String[] parts = raw.split(":");
        Material mat = Material.matchMaterial(parts[0]);
        if (mat == null) return null;
        int amount = 1;
        if (parts.length > 1) {
            try { amount = Math.max(1, Integer.parseInt(parts[1])); }
            catch (NumberFormatException ignored) {}
        }
        return new ItemStack(mat, amount);
    }

    private Player resolvePlayer(Entity damager) {
        if (damager instanceof Player p) return p;
        if (damager instanceof org.bukkit.entity.Projectile proj
                && proj.getShooter() instanceof Player p) return p;
        return null;
    }

    private boolean isEnabled() {
        return plugin.getConfig().getBoolean("community.enabled", true)
                && plugin.getConfig().getBoolean("community.raids.enabled", true);
    }

    /** Stop the scheduler + despawn any in-flight raid bosses. */
    public void shutdown() {
        for (UUID entityId : active.keySet()) {
            Entity e = Bukkit.getEntity(entityId);
            if (e != null) e.remove();
        }
        active.clear();
    }

    private static final class RaidContext {
        final String bossId;
        final long spawnedAt;
        final Map<UUID, Double> damageByPlayer = new HashMap<>();

        RaidContext(String bossId, long spawnedAt) {
            this.bossId = bossId;
            this.spawnedAt = spawnedAt;
        }
    }
}
