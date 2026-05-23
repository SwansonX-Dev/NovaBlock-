package com.nova.novablock.island;

import com.nova.novablock.NovaBlock;
import com.nova.novablock.phase.Phase;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class IslandManager {

    private final NovaBlock plugin;
    private final Map<UUID, Island> byId = new HashMap<>();
    private final Map<UUID, UUID> playerToIsland = new HashMap<>();
    private int nextSlotX;
    private int nextSlotZ;

    public IslandManager(NovaBlock plugin) {
        this.plugin = plugin;
    }

    public void loadAll() {
        for (IslandData data : plugin.storage().loadAllIslands()) {
            register(new Island(plugin, data));
        }
        recalculateNextSlot();
    }

    public void saveAll() {
        for (Island i : byId.values()) plugin.storage().saveIsland(i.data());
    }

    private void register(Island island) {
        byId.put(island.data().getId(), island);
        for (UUID m : island.data().getMembers()) playerToIsland.put(m, island.data().getId());
    }

    private void recalculateNextSlot() {
        int max = 0;
        for (Island i : byId.values()) {
            max = Math.max(max, Math.max(Math.abs(i.data().getSlotX()), Math.abs(i.data().getSlotZ())));
        }
        nextSlotX = max + 1;
        nextSlotZ = 0;
    }

    public Island get(UUID islandId) { return byId.get(islandId); }
    public Island ofPlayer(UUID playerId) {
        UUID id = playerToIsland.get(playerId);
        return id == null ? null : byId.get(id);
    }
    public Island ofPlayer(Player p) { return ofPlayer(p.getUniqueId()); }

    /** Resolve which island a location belongs to by checking grid slot. */
    public Island atLocation(Location loc) {
        if (loc.getWorld() == null) return null;
        if (!loc.getWorld().getName().equals(IslandWorldManager.WORLD_NAME)) return null;
        int slotX = Math.floorDiv(loc.getBlockX(), IslandWorldManager.SLOT_SIZE);
        int slotZ = Math.floorDiv(loc.getBlockZ(), IslandWorldManager.SLOT_SIZE);
        for (Island i : byId.values()) {
            if (i.data().getSlotX() == slotX && i.data().getSlotZ() == slotZ) return i;
        }
        return null;
    }

    public Island create(Player owner) {
        if (ofPlayer(owner) != null) return ofPlayer(owner);
        int[] slot = nextSlot();
        IslandData data = new IslandData(Island.newId(), owner.getUniqueId(),
                IslandWorldManager.WORLD_NAME, slot[0], slot[1]);
        Island island = new Island(plugin, data);
        island.ensureSpawnPlatform();
        Phase first = plugin.phases().get(0);
        if (first != null) island.refillUpcoming(first, 10);
        register(island);
        plugin.storage().saveIsland(data);
        return island;
    }

    public void delete(Island island) {
        for (UUID m : island.data().getMembers()) playerToIsland.remove(m);
        byId.remove(island.data().getId());
        plugin.storage().deleteIsland(island.data().getId());
    }

    public void addMember(Island island, UUID playerId) {
        island.data().getMembers().add(playerId);
        playerToIsland.put(playerId, island.data().getId());
        plugin.storage().saveIsland(island.data());
    }

    private int[] nextSlot() {
        int[] slot = {nextSlotX, nextSlotZ};
        // simple advancing pattern (spiral-lite)
        nextSlotZ++;
        if (nextSlotZ > nextSlotX) { nextSlotX++; nextSlotZ = 0; }
        return slot;
    }

    public Map<UUID, Island> all() { return byId; }
}
