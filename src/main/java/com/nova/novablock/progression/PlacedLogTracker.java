package com.nova.novablock.progression;

import com.nova.novablock.NovaBlock;
import org.bukkit.Chunk;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.Arrays;

/**
 * Remembers which log blocks were placed by a player so the Woodcutting skill can
 * ignore them — no XP, no passives, and Tree Feller won't chop through them. This
 * stops players from farming the skill on placed logs and, more importantly, stops
 * Tree Feller from wiping out log-built structures next to a natural tree.
 *
 * <p>State lives in each chunk's {@link PersistentDataContainer} as a packed
 * int-array of positions, so it survives restarts and saves/loads with the chunk.
 * Only logs are tracked (the only thing Tree Feller touches), keeping the store tiny.
 * Builds placed before this feature shipped aren't marked and remain fellable.
 */
public class PlacedLogTracker implements Listener {

    private final NamespacedKey key;

    public PlacedLogTracker(NovaBlock plugin) {
        this.key = new NamespacedKey(plugin, "placed_logs");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Block b = event.getBlockPlaced();
        if (Tag.LOGS.isTagged(b.getType())) markPlaced(b);
    }

    /** Pack a block into a per-chunk id: 9 bits world-Y (offset 64) + 4 bits relX + 4 bits relZ. */
    private static int encode(Block b) {
        int relX = b.getX() & 0xF;
        int relZ = b.getZ() & 0xF;
        return ((b.getY() + 64) << 8) | (relX << 4) | relZ;
    }

    private int[] read(Chunk chunk) {
        int[] arr = chunk.getPersistentDataContainer().get(key, PersistentDataType.INTEGER_ARRAY);
        return arr == null ? new int[0] : arr;
    }

    private void write(Chunk chunk, int[] arr) {
        PersistentDataContainer pdc = chunk.getPersistentDataContainer();
        if (arr.length == 0) pdc.remove(key);
        else pdc.set(key, PersistentDataType.INTEGER_ARRAY, arr);
    }

    public void markPlaced(Block b) {
        int id = encode(b);
        Chunk chunk = b.getChunk();
        int[] arr = read(chunk);
        for (int v : arr) if (v == id) return; // already tracked
        int[] next = Arrays.copyOf(arr, arr.length + 1);
        next[arr.length] = id;
        write(chunk, next);
    }

    public boolean isPlaced(Block b) {
        int id = encode(b);
        for (int v : read(b.getChunk())) if (v == id) return true;
        return false;
    }

    /** Forget a block (it was broken, so its slot is free again). */
    public void clearPlaced(Block b) {
        int id = encode(b);
        Chunk chunk = b.getChunk();
        int[] arr = read(chunk);
        int idx = -1;
        for (int i = 0; i < arr.length; i++) if (arr[i] == id) { idx = i; break; }
        if (idx < 0) return;
        int[] next = new int[arr.length - 1];
        System.arraycopy(arr, 0, next, 0, idx);
        System.arraycopy(arr, idx + 1, next, idx, arr.length - idx - 1);
        write(chunk, next);
    }
}
