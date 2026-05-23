package com.nova.novablock.storage;

import com.nova.novablock.island.IslandData;
import com.nova.novablock.progression.PlayerProgression;

import java.util.Collection;
import java.util.UUID;

public interface DataStorage {

    void init();
    void shutdown();

    Collection<IslandData> loadAllIslands();
    void saveIsland(IslandData data);
    void deleteIsland(UUID islandId);

    PlayerProgression loadProgression(UUID playerId);
    void saveProgression(PlayerProgression progression);
}
