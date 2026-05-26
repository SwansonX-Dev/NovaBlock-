package com.nova.novablock.prophecy;

import com.nova.novablock.NovaBlock;
import com.nova.novablock.island.Island;
import com.nova.novablock.phase.Phase;
import com.nova.novablock.progression.Perk;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Maintains the next 10 upcoming blocks per island and tracks "prophecy" picks —
 * the player can lock in one rare block from the upcoming queue per cycle, and when
 * that block is reached they get a bonus payout.
 *
 * This is one of the major hook features: it gives the player a short-horizon
 * decision every cycle (~30 seconds) and makes the next blocks feel meaningful.
 */
public class ProphecyManager {

    public static final int QUEUE_SIZE = 12;
    public static final int DEFAULT_VISIBLE = 10;
    public static final int AETHER_VISIBLE = 12;
    /** Picks per player. The Set is mutated in place as the queue shifts. */
    private final Map<UUID, Set<Integer>> picks = new HashMap<>();
    /** Materials considered "rare" — only these may be locked in. */
    private final Set<Material> rares = new HashSet<>(List.of(
            Material.DIAMOND_ORE, Material.DEEPSLATE_DIAMOND_ORE,
            Material.EMERALD_ORE, Material.DEEPSLATE_EMERALD_ORE,
            Material.ANCIENT_DEBRIS, Material.NETHERITE_BLOCK,
            Material.CONDUIT, Material.BEACON,
            Material.ENDER_CHEST, Material.SHULKER_BOX,
            Material.BUDDING_AMETHYST, Material.SCULK_CATALYST,
            Material.RAW_GOLD_BLOCK, Material.REINFORCED_DEEPSLATE,
            Material.SPONGE, Material.CAKE
    ));

    private final NovaBlock plugin;

    public ProphecyManager(NovaBlock plugin) { this.plugin = plugin; }

    public void ensureQueue(Island island) {
        Phase phase = plugin.phases().getOrLast(island.data().getPhaseIndex());
        if (phase == null) return;
        island.refillUpcoming(phase, QUEUE_SIZE);
    }

    public Material upcoming(Island island, int slot) {
        ensureQueue(island);
        int i = 0;
        for (Material m : island.upcomingBlocks()) {
            if (i++ == slot) return m;
        }
        return Material.STONE;
    }

    public boolean isRare(Material m) { return rares.contains(m); }

    public Set<Integer> picks(Player p) {
        return picks.getOrDefault(p.getUniqueId(), Set.of());
    }

    public int pickSlot(Player p) {
        Set<Integer> s = picks.get(p.getUniqueId());
        return (s == null || s.isEmpty()) ? -1 : s.iterator().next();
    }

    /** Base 1 pick; PROPHET (Mining 30) adds 1; PROPHECY_SLOTS upgrade adds up to 3 more. */
    public int maxPicks(Player p) {
        int base = Perk.hasPerk(plugin.progression().get(p), Perk.PROPHET) ? 2 : 1;
        var island = plugin.islands().ofPlayer(p);
        int upgradeLevel = island == null ? 0
                : island.data().getUpgradeLevel(com.nova.novablock.island.IslandUpgrade.PROPHECY_SLOTS);
        return base + upgradeLevel;
    }

    /** AETHER_SIGHT (Magic 5) shows two extra upcoming slots in the GUI. */
    public int visibleCount(Player p) {
        return Perk.hasPerk(plugin.progression().get(p), Perk.AETHER_SIGHT)
                ? AETHER_VISIBLE : DEFAULT_VISIBLE;
    }

    /** Result of a click on a prophecy slot, returned for the GUI to render. */
    public enum LockResult { LOCKED, UNLOCKED, NOT_RARE, NOT_MEMBER, OUT_OF_RANGE }

    /** Toggle the lock state for {@code slot}. New locks evict the oldest when over capacity. */
    public LockResult toggleLock(Player p, Island island, int slot) {
        if (slot < 0 || slot >= QUEUE_SIZE) return LockResult.OUT_OF_RANGE;
        if (!island.isMember(p)) return LockResult.NOT_MEMBER;
        Set<Integer> set = picks.computeIfAbsent(p.getUniqueId(), id -> new LinkedHashSet<>());
        if (set.contains(slot)) {
            set.remove(slot);
            return LockResult.UNLOCKED;
        }
        Material m = upcoming(island, slot);
        if (!isRare(m)) return LockResult.NOT_RARE;
        // If at capacity, drop the oldest pick to make room for the new one.
        while (set.size() >= maxPicks(p) && !set.isEmpty()) {
            set.remove(set.iterator().next());
        }
        set.add(slot);
        // Locking a prophecy is a magic act — give the player a sliver of MAGIC XP.
        plugin.progression().addXp(p, com.nova.novablock.progression.SkillType.MAGIC, 2L);
        return LockResult.LOCKED;
    }

    /** Back-compat shim that returns true only when a new pick was added. */
    public boolean lockIn(Player p, Island island, int slot) {
        return toggleLock(p, island, slot) == LockResult.LOCKED;
    }

    public void clear(Player p) { picks.remove(p.getUniqueId()); }

    /**
     * TIMESHIFT (Magic 30): reroll the entire upcoming queue for this island,
     * once per UTC day. Returns true if the reroll happened.
     */
    public boolean reroll(Player p, Island island) {
        if (!Perk.hasPerk(plugin.progression().get(p), Perk.TIMESHIFT)) return false;
        var prog = plugin.progression().get(p);
        long today = System.currentTimeMillis() / 86_400_000L;
        if (prog.getLastRerollDay() == today) return false;
        Phase phase = plugin.phases().getOrLast(island.data().getPhaseIndex());
        if (phase == null) return false;
        island.upcomingBlocks().clear();
        picks.remove(p.getUniqueId());
        island.refillUpcoming(phase, QUEUE_SIZE);
        prog.setLastRerollDay(today);
        plugin.progression().save(p.getUniqueId());
        return true;
    }

    public boolean canReroll(Player p) {
        if (!Perk.hasPerk(plugin.progression().get(p), Perk.TIMESHIFT)) return false;
        var prog = plugin.progression().get(p);
        long today = System.currentTimeMillis() / 86_400_000L;
        return prog.getLastRerollDay() != today;
    }

    /** Called when an island advances by one block — shifts the queue and recomputes picks. */
    public void onAdvance(Island island, Material consumed) {
        for (Map.Entry<UUID, Set<Integer>> entry : picks.entrySet()) {
            Set<Integer> set = entry.getValue();
            if (set.isEmpty()) continue;
            Set<Integer> shifted = new LinkedHashSet<>(set.size());
            boolean fulfilled = false;
            for (int slot : set) {
                if (slot == 0) fulfilled = true;
                else if (slot > 0) shifted.add(slot - 1);
            }
            if (fulfilled) {
                Player p = plugin.getServer().getPlayer(entry.getKey());
                if (p != null && island.isMember(p)) award(p, island, consumed);
            }
            entry.setValue(shifted);
        }
        picks.entrySet().removeIf(en -> en.getValue() == null || en.getValue().isEmpty());
    }

    private void award(Player p, Island island, Material m) {
        long bonus = 250L + (long) island.data().getPhaseIndex() * 75L;
        // JACKPOT (Luck 10): +25% coin from rare events — prophecies count.
        if (Perk.hasPerk(plugin.progression().get(p), Perk.JACKPOT)) {
            bonus = Math.round(bonus * 1.25);
        }
        plugin.economy().award(island, bonus);
        // Prophecy fulfillment rewards MAGIC and LUCK XP.
        plugin.progression().addXp(p, com.nova.novablock.progression.SkillType.MAGIC, 25L);
        plugin.progression().addXp(p, com.nova.novablock.progression.SkillType.LUCK, 15L);
        p.sendActionBar(com.nova.novablock.util.Msg.mm(
                "<gold>★ Prophecy fulfilled! +" + bonus + " coins (" + m.name() + ")"));
        p.playSound(p.getLocation(), org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1.2f);
    }
}
