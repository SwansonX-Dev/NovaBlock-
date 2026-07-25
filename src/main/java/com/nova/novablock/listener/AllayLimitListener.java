package com.nova.novablock.listener;

import com.nova.novablock.NovaBlock;
import com.nova.novablock.island.Dimension;
import com.nova.novablock.island.Island;
import com.nova.novablock.island.IslandWorldManager;
import com.nova.novablock.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Allay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.Locale;
import java.util.UUID;

/**
 * Caps how many allays can live on one island.
 *
 * <p>Allays only multiply one way — hand an amethyst shard to one dancing near a
 * jukebox — so blocking duplication at the cap is enough to bound the population.
 * Nothing already alive is ever removed: allays carry player items, and culling
 * them would destroy property.
 *
 * <p>Two listeners, deliberately: {@link #onFeedAllay} catches the player-facing
 * path so the shard is never consumed for a duplication that won't happen (and the
 * player gets told why), while {@link #onDuplicate} is a silent backstop covering
 * any other route to a {@code DUPLICATION} spawn.
 *
 * <p>The cap belongs to the <em>island</em>, not to whoever is holding the shard —
 * it is read from the owner's permissions, so a visiting donor can't raise a cap on
 * someone else's island and the two listeners can never disagree about the limit.
 *
 * <p>Off-island allays (the community hub, admin worlds) are untouched: without an
 * island there's nothing to charge the allay against.
 */
public class AllayLimitListener implements Listener {

    private static final String LIMIT_PERM_PREFIX = "novablock.allays.limit.";
    /** Half-height of the count box — comfortably covers the full build range at any centre Y. */
    private static final double COUNT_HEIGHT = 512;

    private final NovaBlock plugin;

    public AllayLimitListener(NovaBlock plugin) { this.plugin = plugin; }

    // ---------------- listeners ----------------

    /**
     * Player-facing guard: cancels the interaction that would duplicate an allay once the
     * island is at its cap. Cancelling here (rather than letting {@link #onDuplicate} catch
     * the spawn) means the amethyst shard is not consumed for nothing.
     */
    @EventHandler(ignoreCancelled = true)
    public void onFeedAllay(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Allay allay)) return;

        Player p = event.getPlayer();
        ItemStack hand = event.getHand() == EquipmentSlot.OFF_HAND
                ? p.getInventory().getItemInOffHand()
                : p.getInventory().getItemInMainHand();
        if (hand == null || hand.getType() != Material.AMETHYST_SHARD) return;

        // A shard only duplicates a dancing, off-cooldown allay. Otherwise the player is
        // just handing it an item to carry, which the cap has no business blocking.
        if (!allay.isDancing() || !allay.canDuplicate() || allay.getDuplicationCooldown() > 0) return;

        Island island = plugin.islands().atLocation(allay.getLocation());
        if (island == null) return;

        int limit = limit(island);
        if (count(island) < limit) return;

        event.setCancelled(true);
        Msg.actionBar(p, "<red>Allay limit reached <gray>(" + limit + " per island)<red>. "
                + "<gray>Move some off the island to duplicate more.");
    }

    /** Backstop for any duplication that doesn't come through a player interaction. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDuplicate(CreatureSpawnEvent event) {
        if (event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.DUPLICATION) return;
        if (event.getEntityType() != EntityType.ALLAY) return;

        Island island = plugin.islands().atLocation(event.getLocation());
        if (island == null) return;
        if (count(island) < limit(island)) return;

        event.setCancelled(true);
    }

    // ---------------- cap ----------------

    /**
     * The island's allay cap: the configured default, raised by any
     * {@code novablock.allays.limit.<n>} node on the owner. Read from the owner rather
     * than the acting player so the cap is a stable property of the island; an offline
     * owner's nodes aren't reachable through Bukkit, so the config default applies.
     */
    public int limit(Island island) {
        int limit = Math.max(1, plugin.getConfig().getInt("allays.island-limit", 30));
        if (island == null) return limit;

        Player owner = Bukkit.getPlayer(island.data().getOwner());
        if (owner == null) return limit;
        if (owner.hasPermission("novablock.allays.admin") || owner.hasPermission("novablock.admin")) {
            return Integer.MAX_VALUE;
        }
        for (var info : owner.getEffectivePermissions()) {
            if (!info.getValue()) continue;
            String perm = info.getPermission().toLowerCase(Locale.ROOT);
            if (!perm.startsWith(LIMIT_PERM_PREFIX)) continue;
            try { limit = Math.max(limit, Integer.parseInt(perm.substring(LIMIT_PERM_PREFIX.length()))); }
            catch (NumberFormatException ignored) {}
        }
        return limit;
    }

    /**
     * Live allay count across every loaded dimension of the island. Counting all of them
     * (rather than just the one being fed) stops the nether being used to double the cap.
     *
     * <p>Only runs on a duplication attempt, so the bounded box queries stay off the hot path.
     */
    public int count(Island island) {
        if (island == null) return 0;
        double half = IslandWorldManager.SLOT_SIZE / 2.0;
        UUID islandId = island.data().getId();
        int total = 0;

        for (Dimension d : Dimension.values()) {
            if (d != Dimension.OVERWORLD && !island.isUnlocked(d)) continue;
            Location center = island.centerBlock(d);
            if (center == null || center.getWorld() == null) continue;

            for (Entity e : center.getWorld().getNearbyEntities(
                    center, half, COUNT_HEIGHT, half, x -> x.getType() == EntityType.ALLAY)) {
                // The box matches the grid slot, but re-resolving keeps edge cases honest.
                Island owner = plugin.islands().atLocation(e.getLocation());
                if (owner != null && owner.data().getId().equals(islandId)) total++;
            }
        }
        return total;
    }
}
