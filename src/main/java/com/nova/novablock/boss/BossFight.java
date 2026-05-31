package com.nova.novablock.boss;

import com.nova.novablock.island.Island;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class BossFight {

    private final Boss boss;
    /** Null for raid fights at the community hub — they're not tied to an island. */
    private final @Nullable UUID islandId;
    private final UUID entityId;
    private final BossBar bar;
    private final Set<UUID> participants = new HashSet<>();
    /** Per-player death count during this fight; used by the 3-strike escape valve. */
    private final java.util.Map<UUID, Integer> deathsByPlayer = new java.util.HashMap<>();
    private final long startedAt = System.currentTimeMillis();
    private int phase = 1;
    /** Spawn point for tethering — bosses get teleported back if they wander too far. */
    private org.bukkit.Location arenaCenter;
    /** Block snapshots placed for the arena platform; restored on fight end. */
    private final java.util.List<org.bukkit.block.BlockState> arenaSnapshot = new java.util.ArrayList<>();

    public BossFight(Boss boss, Island island, LivingEntity entity, BossBar bar) {
        this(boss, island == null ? null : island.data().getId(), entity, bar);
    }

    public BossFight(Boss boss, @Nullable UUID islandId, LivingEntity entity, BossBar bar) {
        this.boss = boss;
        this.islandId = islandId;
        this.entityId = entity.getUniqueId();
        this.bar = bar;
    }

    public Boss boss() { return boss; }
    public @Nullable UUID islandId() { return islandId; }

    /** Per-attacker damage hook — overridden by RaidFight to track contribution. */
    public void recordDamage(Player attacker, double amount) {}

    /**
     * Reward distribution hook. Default returns false → BossManager handles via
     * the standard per-island award. Override to take over (e.g. raid pool split).
     */
    public boolean distributeRewardsOnDeath(long baseCoins) { return false; }
    public UUID entityId() { return entityId; }
    public BossBar bar() { return bar; }
    public Set<UUID> participants() { return participants; }
    public long startedAt() { return startedAt; }
    public int phase() { return phase; }
    public void setPhase(int p) { this.phase = p; }

    public org.bukkit.Location arenaCenter() { return arenaCenter; }
    public void setArenaCenter(org.bukkit.Location l) { this.arenaCenter = l; }

    public java.util.List<org.bukkit.block.BlockState> arenaSnapshot() { return arenaSnapshot; }

    /** Returns the player's new death count after incrementing. */
    public int recordDeath(UUID playerId) {
        int v = deathsByPlayer.getOrDefault(playerId, 0) + 1;
        deathsByPlayer.put(playerId, v);
        return v;
    }
    public int deathsBy(UUID playerId) { return deathsByPlayer.getOrDefault(playerId, 0); }

    public LivingEntity entity() {
        var e = org.bukkit.Bukkit.getEntity(entityId);
        return e instanceof LivingEntity le ? le : null;
    }

    public void addParticipant(Player p) {
        if (participants.add(p.getUniqueId())) p.showBossBar(bar);
    }

    public void removeParticipant(UUID id) {
        participants.remove(id);
        Player p = org.bukkit.Bukkit.getPlayer(id);
        if (p != null) p.hideBossBar(bar);
    }

    public void clearBar() {
        for (UUID id : participants) {
            Player p = org.bukkit.Bukkit.getPlayer(id);
            if (p != null) p.hideBossBar(bar);
        }
        participants.clear();
    }

    public void syncBar() {
        LivingEntity e = entity();
        if (e == null) return;
        double max = e.getAttribute(Attribute.MAX_HEALTH) == null
                ? 100 : e.getAttribute(Attribute.MAX_HEALTH).getValue();
        bar.progress((float) Math.max(0, Math.min(1, e.getHealth() / max)));
    }
}
