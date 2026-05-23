package com.nova.novablock.lootroom;

import com.nova.novablock.island.Island;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public interface LootRoom {

    String id();
    String displayName();

    /** Build the room into the world at the given anchor (north-west bottom corner). Return entry teleport. */
    Location build(Location anchor);

    /** Called once per second during the run; should award & finish via finish(). */
    void tick(LootRoomRun run);

    /** Called when the player enters the room — initialize state. */
    default void onStart(LootRoomRun run, Player player) {}

    /** Reward when the player completes. */
    default int rewardCoins(Island island) { return 800 + island.data().getPhaseIndex() * 200; }
}
