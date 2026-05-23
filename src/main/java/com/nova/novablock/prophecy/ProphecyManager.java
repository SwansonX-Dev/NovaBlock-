package com.nova.novablock.prophecy;

import com.nova.novablock.NovaBlock;
import com.nova.novablock.island.Island;
import com.nova.novablock.phase.Phase;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Maintains the next 10 upcoming blocks per island and tracks "prophecy" picks —
 * the player can lock in one rare block from the upcoming queue per cycle, and when
 * that block is reached they get a bonus payout.
 *
 * This is one of the major hook features: it gives the player a short-horizon
 * decision every cycle (~30 seconds) and makes the next blocks feel meaningful.
 */
public class ProphecyManager {

    public static final int QUEUE_SIZE = 10;
    /** Index into the queue the player has chosen as their prophecy. -1 if none. */
    private final Map<UUID, Integer> picks = new HashMap<>();
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

    public int pickSlot(Player p) {
        Integer v = picks.get(p.getUniqueId());
        return v == null ? -1 : v;
    }

    public boolean lockIn(Player p, Island island, int slot) {
        if (slot < 0 || slot >= QUEUE_SIZE) return false;
        if (!island.isMember(p)) return false;
        Material m = upcoming(island, slot);
        if (!isRare(m)) return false;
        picks.put(p.getUniqueId(), slot);
        return true;
    }

    public void clear(Player p) { picks.remove(p.getUniqueId()); }

    /** Called when an island advances by one block — shifts the queue and recomputes picks. */
    public void onAdvance(Island island, Material consumed) {
        // The consumed block was index 0. Anything > 0 shifts down by 1.
        for (Map.Entry<UUID, Integer> e : picks.entrySet()) {
            int slot = e.getValue();
            if (slot == 0) {
                Player p = plugin.getServer().getPlayer(e.getKey());
                if (p != null && island.isMember(p)) {
                    award(p, island, consumed);
                }
                e.setValue(-1);
            } else if (slot > 0) {
                e.setValue(slot - 1);
            }
        }
        // Purge -1 picks
        picks.entrySet().removeIf(en -> en.getValue() == null || en.getValue() < 0);
    }

    private void award(Player p, Island island, Material m) {
        long bonus = 250L + (long) island.data().getPhaseIndex() * 75L;
        plugin.economy().award(island, bonus);
        p.sendActionBar(com.nova.novablock.util.Msg.mm(
                "<gold>★ Prophecy fulfilled! +" + bonus + " coins (" + m.name() + ")"));
        p.playSound(p.getLocation(), org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1.2f);
    }
}
