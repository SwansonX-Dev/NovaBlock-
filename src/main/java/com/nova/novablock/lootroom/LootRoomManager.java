package com.nova.novablock.lootroom;

import com.nova.novablock.NovaBlock;
import com.nova.novablock.island.Island;
import com.nova.novablock.lootroom.rooms.ArenaRoom;
import com.nova.novablock.lootroom.rooms.ParkourRoom;
import com.nova.novablock.lootroom.rooms.PuzzleRoom;
import com.nova.novablock.util.ItemBuilder;
import com.nova.novablock.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class LootRoomManager {

    private final NovaBlock plugin;
    private final Map<String, LootRoom> registry = new HashMap<>();
    private final Map<UUID, LootRoomRun> active = new HashMap<>();
    /** Players who have a rift offered, with the expiration tick. */
    private final Map<UUID, RiftOffer> offers = new HashMap<>();
    private int nextAnchorX = 100_000;
    private BukkitTask tickTask;

    public LootRoomManager(NovaBlock plugin) {
        this.plugin = plugin;
        tickTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickAll, 20L, 20L);
    }

    public void registerDefaultRooms() {
        register(new ParkourRoom());
        register(new ArenaRoom(plugin));
        register(new PuzzleRoom(plugin));
    }

    public void register(LootRoom room) { registry.put(room.id(), room); }
    public LootRoom byId(String id) { return registry.get(id); }
    public int roomCount() { return registry.size(); }

    public void offerEntry(Player player, Island island, String roomId) {
        LootRoom room = registry.get(roomId);
        if (room == null) return;
        Location center = island.centerBlock().clone().add(2, 0, 0);
        // Spawn a temporary "rift" portal (end gateway block as marker is heavy; use end_portal_frame eye-on)
        Location markerLoc = center.clone();
        markerLoc.getWorld().getBlockAt(markerLoc).setType(Material.END_PORTAL_FRAME);
        markerLoc.getWorld().spawnParticle(org.bukkit.Particle.PORTAL, markerLoc.add(0.5, 1, 0.5), 60, 0.3, 1, 0.3, 0.5);
        offers.put(player.getUniqueId(), new RiftOffer(roomId, island.data().getId(), markerLoc.clone(), System.currentTimeMillis() + 30_000));
        Msg.send(player, "<light_purple>★ A <yellow>" + room.displayName() + " <light_purple>rift opened next to your block! Step on it within 30s.");
        player.playSound(player.getLocation(), Sound.BLOCK_PORTAL_AMBIENT, 0.6f, 1.2f);
    }

    private void tickAll() {
        long now = System.currentTimeMillis();
        // Clean up expired offers
        offers.entrySet().removeIf(e -> {
            if (e.getValue().expiresAt < now) {
                e.getValue().marker.getBlock().setType(Material.AIR);
                Player p = Bukkit.getPlayer(e.getKey());
                if (p != null) Msg.actionBar(p, "<gray>Rift fizzled away.");
                return true;
            }
            return false;
        });
        // Trigger entry if standing on the marker
        for (var entry : new HashMap<>(offers).entrySet()) {
            Player p = Bukkit.getPlayer(entry.getKey());
            if (p == null) continue;
            Location below = p.getLocation().clone().subtract(0, 1, 0);
            if (below.getBlock().getLocation().equals(entry.getValue().marker)) {
                enter(p, entry.getValue());
            }
        }
        // Tick runs
        for (var it = active.entrySet().iterator(); it.hasNext(); ) {
            LootRoomRun run = it.next().getValue();
            try { run.room().tick(run); } catch (Throwable t) {
                plugin.getLogger().warning("Loot room tick error: " + t);
            }
            if (run.finished()) {
                complete(run);
                it.remove();
            }
        }
    }

    private void enter(Player p, RiftOffer offer) {
        offers.remove(p.getUniqueId());
        offer.marker.getBlock().setType(Material.AIR);
        LootRoom room = registry.get(offer.roomId);
        Island island = plugin.islands().get(offer.islandId);
        if (room == null || island == null) return;
        Location anchor = nextAnchor();
        Location entry = room.build(anchor);
        Location returnLoc = p.getLocation().clone();
        LootRoomRun run = new LootRoomRun(room, p.getUniqueId(), island, anchor, returnLoc, Bukkit.getCurrentTick());
        active.put(p.getUniqueId(), run);
        p.teleport(entry);
        room.onStart(run, p);
        Msg.title(p, "<light_purple>" + room.displayName(), "<gray>Complete to claim loot");
        p.playSound(p.getLocation(), Sound.ITEM_CHORUS_FRUIT_TELEPORT, 1f, 1f);
    }

    public void finishEarly(Player p) {
        LootRoomRun run = active.remove(p.getUniqueId());
        if (run == null) return;
        p.teleport(run.returnLocation());
        Msg.actionBar(p, "<gray>You forfeited the rift.");
    }

    private void complete(LootRoomRun run) {
        Player p = run.player();
        if (p == null) return;
        Island island = plugin.islands().get(run.islandId());
        int reward = run.room().rewardCoins(island);
        plugin.economy().award(island, reward);
        p.teleport(run.returnLocation());
        // Drop a reward chest item
        var chest = ItemBuilder.of(Material.CHEST)
                .name("<gold>Rift Reward")
                .lore("<gray>+" + reward + " coins added to your island", "<gray>Score: <yellow>" + run.score())
                .build();
        p.getInventory().addItem(chest);
        Msg.title(p, "<gold>Rift Cleared!", "<yellow>+" + reward + " coins");
        p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
    }

    private Location nextAnchor() {
        nextAnchorX += 64;
        return new Location(plugin.worlds().getWorld(), nextAnchorX, 100, 100_000);
    }

    public LootRoomRun getRun(Player p) { return active.get(p.getUniqueId()); }

    public void shutdown() {
        if (tickTask != null) { tickTask.cancel(); tickTask = null; }
        // Send any active runs back to their return location so players aren't stranded.
        for (LootRoomRun run : active.values()) {
            Player p = run.player();
            if (p != null) p.teleport(run.returnLocation());
        }
        active.clear();
        // Remove pending rift markers from the world.
        for (RiftOffer offer : offers.values()) offer.marker.getBlock().setType(Material.AIR);
        offers.clear();
    }

    private record RiftOffer(String roomId, UUID islandId, Location marker, long expiresAt) {}
}
