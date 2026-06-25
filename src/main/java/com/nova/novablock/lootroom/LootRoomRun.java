package com.nova.novablock.lootroom;

import com.nova.novablock.island.Island;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class LootRoomRun {

    private final LootRoom room;
    /** The player who opened the rift — owns reward attribution (island, JACKPOT). */
    private final UUID playerId;
    /** Everyone currently inside this rift, opener included. Insertion-ordered. */
    private final Set<UUID> participants = new LinkedHashSet<>();
    private final UUID islandId;
    private final Location anchor;
    private final Location entryLocation;
    private final Location returnLocation;
    private final String worldName;
    private final long startTick;
    private boolean finished;
    private int score;
    private long state; // free use bitfield for room implementations

    public LootRoomRun(LootRoom room, UUID playerId, Island island, Location anchor, Location entryLocation,
                       Location returnLocation, String worldName, long startTick) {
        this.room = room;
        this.playerId = playerId;
        this.participants.add(playerId);
        this.islandId = island.data().getId();
        this.anchor = anchor;
        this.entryLocation = entryLocation;
        this.returnLocation = returnLocation;
        this.worldName = worldName;
        this.startTick = startTick;
    }

    public LootRoom room() { return room; }
    public UUID playerId() { return playerId; }
    public UUID islandId() { return islandId; }
    public Location anchor() { return anchor; }
    public Location entryLocation() { return entryLocation; }
    public Location returnLocation() { return returnLocation; }
    public String worldName() { return worldName; }
    public long startTick() { return startTick; }
    public boolean finished() { return finished; }
    public void markFinished() { this.finished = true; }
    public int score() { return score; }
    public void addScore(int s) { this.score += s; }
    public long state() { return state; }
    public void setState(long s) { this.state = s; }

    /** The opener, if online. Use {@link #players()} for room logic that should include everyone. */
    public Player player() { return Bukkit.getPlayer(playerId); }

    // ---- participants ----

    public Set<UUID> participants() { return participants; }
    public void addParticipant(UUID id) { participants.add(id); }
    public void removeParticipant(UUID id) { participants.remove(id); }
    public boolean hasParticipant(UUID id) { return participants.contains(id); }

    /** Online participants who are actually inside the rift world right now. */
    public List<Player> players() {
        World world = anchor.getWorld();
        List<Player> out = new ArrayList<>();
        for (UUID id : participants) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && p.isOnline() && world != null && p.getWorld().equals(world)) out.add(p);
        }
        return out;
    }
}
