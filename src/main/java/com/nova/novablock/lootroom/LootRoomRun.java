package com.nova.novablock.lootroom;

import com.nova.novablock.island.Island;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.UUID;

public class LootRoomRun {

    private final LootRoom room;
    private final UUID playerId;
    private final UUID islandId;
    private final Location anchor;
    private final Location returnLocation;
    private final long startTick;
    private boolean finished;
    private int score;
    private long state; // free use bitfield for room implementations

    public LootRoomRun(LootRoom room, UUID playerId, Island island, Location anchor, Location returnLocation, long startTick) {
        this.room = room;
        this.playerId = playerId;
        this.islandId = island.data().getId();
        this.anchor = anchor;
        this.returnLocation = returnLocation;
        this.startTick = startTick;
    }

    public LootRoom room() { return room; }
    public UUID playerId() { return playerId; }
    public UUID islandId() { return islandId; }
    public Location anchor() { return anchor; }
    public Location returnLocation() { return returnLocation; }
    public long startTick() { return startTick; }
    public boolean finished() { return finished; }
    public void markFinished() { this.finished = true; }
    public int score() { return score; }
    public void addScore(int s) { this.score += s; }
    public long state() { return state; }
    public void setState(long s) { this.state = s; }

    public Player player() { return org.bukkit.Bukkit.getPlayer(playerId); }
}
