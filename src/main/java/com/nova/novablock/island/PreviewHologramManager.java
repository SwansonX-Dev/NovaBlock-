package com.nova.novablock.island;

import com.nova.novablock.NovaBlock;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Floating ItemDisplay above each OneBlock that shows the very next block in
 * the prophecy queue. Reuses ProphecyManager queue data, refreshed every
 * second per online island.
 */
public class PreviewHologramManager {

    private static final long TICK_PERIOD = 20L; // 1 Hz is plenty

    private final NovaBlock plugin;
    private final Map<UUID, UUID> hologramByIsland = new HashMap<>();
    private BukkitTask tickTask;

    public PreviewHologramManager(NovaBlock plugin) { this.plugin = plugin; }

    public void start() {
        tickTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickAll, TICK_PERIOD, TICK_PERIOD);
    }

    public void shutdown() {
        if (tickTask != null) { tickTask.cancel(); tickTask = null; }
        for (UUID entityId : hologramByIsland.values()) {
            var e = Bukkit.getEntity(entityId);
            if (e != null) e.remove();
        }
        hologramByIsland.clear();
    }

    private void tickAll() {
        // Only animate holograms for islands with a member online — keeps entity count low.
        java.util.Set<UUID> activeIslands = new java.util.HashSet<>();
        for (var p : Bukkit.getOnlinePlayers()) {
            var island = plugin.islands().ofPlayer(p);
            if (island != null) activeIslands.add(island.data().getId());
        }
        // Despawn holograms whose island has no online member.
        hologramByIsland.entrySet().removeIf(entry -> {
            if (!activeIslands.contains(entry.getKey())) {
                var e = Bukkit.getEntity(entry.getValue());
                if (e != null) e.remove();
                return true;
            }
            return false;
        });
        for (UUID islandId : activeIslands) {
            Island island = plugin.islands().get(islandId);
            if (island == null) continue;
            Material next = island.upcomingBlocks().peek();
            if (next == null) continue;
            ItemDisplay display = ensureHologram(island);
            if (display == null) continue;
            ItemStack stack = displayItem(next);
            display.setItemStack(stack);
        }
    }

    private ItemDisplay ensureHologram(Island island) {
        UUID existing = hologramByIsland.get(island.data().getId());
        if (existing != null) {
            var e = Bukkit.getEntity(existing);
            if (e instanceof ItemDisplay disp && !disp.isDead()) return disp;
        }
        Location anchor = island.centerBlock().clone().add(0.5, 2.2, 0.5);
        if (anchor.getWorld() == null) return null;
        ItemDisplay display = anchor.getWorld().spawn(anchor, ItemDisplay.class, d -> {
            d.setBillboard(Display.Billboard.CENTER);
            d.setPersistent(false);
            d.setViewRange(0.6f); // ~30 blocks
            d.setShadowRadius(0f);
            Transformation t = d.getTransformation();
            d.setTransformation(new Transformation(
                    t.getTranslation(),
                    new AxisAngle4f(0f, 0f, 0f, 0f),
                    new Vector3f(0.55f, 0.55f, 0.55f),
                    new AxisAngle4f(0f, 0f, 0f, 0f)));
        });
        hologramByIsland.put(island.data().getId(), display.getUniqueId());
        return display;
    }

    private static ItemStack displayItem(Material m) {
        if (m.isItem()) return new ItemStack(m);
        return switch (m) {
            case SWEET_BERRY_BUSH -> new ItemStack(Material.SWEET_BERRIES);
            case TALL_GRASS -> new ItemStack(Material.SHORT_GRASS);
            case TALL_SEAGRASS -> new ItemStack(Material.SEAGRASS);
            case LARGE_FERN -> new ItemStack(Material.FERN);
            case PISTON_HEAD, MOVING_PISTON -> new ItemStack(Material.PISTON);
            case REDSTONE_WIRE -> new ItemStack(Material.REDSTONE);
            case TRIPWIRE -> new ItemStack(Material.STRING);
            default -> new ItemStack(Material.PAPER);
        };
    }
}
