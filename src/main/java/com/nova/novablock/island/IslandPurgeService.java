package com.nova.novablock.island;

import com.nova.novablock.NovaBlock;
import org.bukkit.Bukkit;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Finds and retires islands nobody has played on for a configured number of days.
 *
 * <p><b>What a purge actually reclaims.</b> Islands are not separate worlds — they are
 * 256-block slots inside three shared worlds ({@code oneblock}, {@code _nether},
 * {@code _end}). Purging therefore frees the registry entry and the owner's island slot;
 * it does <b>not</b> free disk, because the slot's blocks stay in the shared world's region
 * files. The abandoned build remains in place until it is manually cleared or the slot is
 * reused. Do not expect a purge to shrink the server.
 *
 * <p><b>Safety.</b> Every entry point is preview-first: {@link #findPurgeable()} only
 * reports, and {@link #purge} must be called explicitly per island. Islands whose activity
 * has never been stamped report 0 days inactive and are never selected, so islands that
 * predate activity tracking are safe until their members log in once.
 */
public class IslandPurgeService {

    private final NovaBlock plugin;

    public IslandPurgeService(NovaBlock plugin) { this.plugin = plugin; }

    /** Inactivity threshold in days, from {@code islands.purge.after-days} (default 30). */
    public int thresholdDays() {
        return Math.max(1, plugin.getConfig().getInt("islands.purge.after-days", 30));
    }

    /** One island that qualifies for purging, with the numbers an admin needs to judge it. */
    public record Candidate(UUID islandId, UUID owner, String ownerName,
                            long daysInactive, long blocksBroken, int level) {}

    /**
     * Every island idle for at least {@link #thresholdDays()}, most-idle first.
     * Islands with an online member are excluded outright — an island someone is
     * standing on is active regardless of what the stored timestamp says.
     */
    public List<Candidate> findPurgeable() {
        int threshold = thresholdDays();
        List<Candidate> out = new ArrayList<>();
        for (Island island : plugin.islands().all().values()) {
            IslandData data = island.data();
            if (hasOnlineMember(data)) continue;
            long idle = data.daysInactive();
            if (idle < threshold) continue;
            out.add(toCandidate(data, idle));
        }
        out.sort(Comparator.comparingLong(Candidate::daysInactive).reversed());
        return out;
    }

    /**
     * The purge candidacy of one specific player's island, whether or not it qualifies —
     * so {@code /obadmin purge <player>} can explain a refusal instead of silently doing
     * nothing. Returns null when the player has no island at all.
     */
    public Candidate inspect(@NotNull UUID playerId) {
        Island island = plugin.islands().ofPlayer(playerId);
        if (island == null) return null;
        return toCandidate(island.data(), island.data().daysInactive());
    }

    private Candidate toCandidate(IslandData data, long idle) {
        String name = Bukkit.getOfflinePlayer(data.getOwner()).getName();
        return new Candidate(data.getId(), data.getOwner(),
                name == null ? data.getOwner().toString() : name,
                idle, data.getBlocksBroken(), data.getLevel());
    }

    public boolean hasOnlineMember(@NotNull IslandData data) {
        for (UUID member : data.getMembers()) {
            if (Bukkit.getPlayer(member) != null) return true;
        }
        return false;
    }

    /**
     * Retires one island: unregisters it and deletes its data file, leaving the slot's
     * blocks untouched in the shared world. Former members become islandless and are given
     * a fresh island the next time they join (the existing auto-create on join).
     *
     * <p>Refuses while any member is online, so an admin can't purge an island out from
     * under someone mid-session.
     *
     * @return true if the island was purged
     */
    public boolean purge(@NotNull UUID islandId) {
        Island island = plugin.islands().get(islandId);
        if (island == null) return false;
        if (hasOnlineMember(island.data())) return false;
        plugin.islands().delete(island);
        return true;
    }

    /**
     * Purges every island returned by {@link #findPurgeable()}.
     *
     * @return the number actually purged, which can be lower than the candidate count if a
     *         member logged in between the preview and the confirmation
     */
    public int purgeAll() {
        int purged = 0;
        for (Candidate c : findPurgeable()) {
            if (purge(c.islandId())) purged++;
        }
        return purged;
    }
}
