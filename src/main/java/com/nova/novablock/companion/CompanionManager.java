package com.nova.novablock.companion;

import com.nova.novablock.NovaBlock;
import com.nova.novablock.island.Island;
import com.nova.novablock.island.IslandWorldManager;
import com.nova.novablock.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Allay;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class CompanionManager implements Listener {

    private static final long TICK_PERIOD = 20L;
    private static final int GATHER_EVERY_TICKS = 200;
    private static final double GROUND_PICKUP_RADIUS = 6.0;
    private static final double VOID_RECOVERY_RADIUS = 48.0;
    private static final int MAX_STACK_GATHER = 16;

    private final NovaBlock plugin;
    private final Map<UUID, CompanionSession> sessions = new HashMap<>();
    private BukkitTask task;

    public CompanionManager(NovaBlock plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        this.task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, TICK_PERIOD, TICK_PERIOD);
    }

    public void shutdown() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        for (CompanionSession session : sessions.values()) {
            session.removeEntity();
        }
        sessions.clear();
    }

    public void summon(Player player, Material material, Sound discSound) {
        if (!player.hasPermission("novablock.companion.use")) {
            denied(player);
            return;
        }
        if (!canGather(player, material)) return;
        if (!canWorkAt(player, player.getLocation())) {
            Msg.send(player, "<red>Your companion cannot gather where you cannot build.");
            return;
        }

        CompanionSession old = sessions.remove(player.getUniqueId());
        if (old != null) old.removeEntity();

        Allay allay = (Allay) player.getWorld().spawnEntity(player.getLocation().add(1.2, 0.4, 1.2), EntityType.ALLAY);
        allay.customName(Msg.mm("<aqua>" + player.getName() + "'s Companion"));
        allay.setCustomNameVisible(true);
        allay.setPersistent(false);
        allay.setRemoveWhenFarAway(false);
        allay.setInvulnerable(true);
        allay.setSilent(true);
        allay.getEquipment().setItemInMainHand(new ItemStack(material));

        CompanionSession session = new CompanionSession(allay, material, discSound, musicReplayTicks(discSound));
        sessions.put(player.getUniqueId(), session);
        if (discSound != null) playDisc(player, session);
        Msg.send(player, "<green>Companion gathering <yellow>" + material.name().toLowerCase(Locale.ROOT) + "<green>.");
    }

    public void stop(Player player) {
        CompanionSession session = sessions.remove(player.getUniqueId());
        if (session == null) {
            Msg.send(player, "<gray>You do not have an active companion.");
            return;
        }
        session.removeEntity();
        if (session.discSound != null) player.stopSound(session.discSound, SoundCategory.RECORDS);
        Msg.send(player, "<gray>Your companion returned home.");
    }

    public void setMaterial(Player player, Material material) {
        CompanionSession session = sessions.get(player.getUniqueId());
        if (session == null) {
            Msg.send(player, "<gray>Use <yellow>/ob companion summon " + material.name().toLowerCase(Locale.ROOT) + "</yellow> first.");
            return;
        }
        if (!canGather(player, material)) return;
        session.material = material;
        if (session.entity != null && !session.entity.isDead()) {
            session.entity.getEquipment().setItemInMainHand(new ItemStack(material));
        }
        Msg.send(player, "<green>Companion now gathering <yellow>" + material.name().toLowerCase(Locale.ROOT) + "<green>.");
    }

    public void setMusic(Player player, Sound discSound) {
        CompanionSession session = sessions.get(player.getUniqueId());
        if (session == null) {
            Msg.send(player, "<gray>Summon your companion first.");
            return;
        }
        Sound old = session.discSound;
        session.discSound = discSound;
        session.musicReplayTicks = musicReplayTicks(discSound);
        session.musicTicks = 0;
        if (discSound == null) {
            if (old != null) player.stopSound(old, SoundCategory.RECORDS);
            Msg.send(player, "<gray>Companion music stopped.");
            return;
        }
        if (old != null) player.stopSound(old, SoundCategory.RECORDS);
        playDisc(player, session);
        Msg.send(player, "<green>Companion music loop enabled.");
    }

    public boolean isActive(Player player) {
        CompanionSession session = sessions.get(player.getUniqueId());
        return session != null && session.entity != null && !session.entity.isDead();
    }

    public Material material(Player player) {
        CompanionSession session = sessions.get(player.getUniqueId());
        return session == null ? null : session.material;
    }

    public Sound music(Player player) {
        CompanionSession session = sessions.get(player.getUniqueId());
        return session == null ? null : session.discSound;
    }

    public boolean canGather(Player player, Material material) {
        if (material == null || !material.isItem() || material == Material.AIR) {
            Msg.send(player, "<red>That material cannot be gathered as an item.");
            return false;
        }
        String node = "novablock.companion.gather." + material.name().toLowerCase(Locale.ROOT);
        if (player.hasPermission("novablock.companion.gather.*") || player.hasPermission(node)) return true;
        Msg.send(player, "<red>You need <yellow>" + node + "</yellow> to gather that material.");
        return false;
    }

    private void tick() {
        sessions.entrySet().removeIf(entry -> {
            Player player = Bukkit.getPlayer(entry.getKey());
            CompanionSession session = entry.getValue();
            if (player == null || !player.isOnline()) {
                session.removeEntity();
                return true;
            }
            if (session.entity == null || session.entity.isDead()) {
                return true;
            }

            Location target = player.getLocation().add(player.getLocation().getDirection().normalize().multiply(-1.5)).add(0, 0.8, 0);
            if (!session.entity.getWorld().equals(player.getWorld())
                    || session.entity.getLocation().distanceSquared(player.getLocation()) > 256) {
                session.entity.teleport(target);
            } else {
                session.entity.teleport(target);
            }

            session.gatherTicks += TICK_PERIOD;
            if (session.gatherTicks >= GATHER_EVERY_TICKS) {
                session.gatherTicks = 0;
                gather(player, session);
            }
            collectNearbyItems(player, session);

            if (session.discSound != null) {
                session.musicTicks += TICK_PERIOD;
                if (session.musicTicks >= session.musicReplayTicks) {
                    playDisc(player, session);
                }
            }
            return false;
        });
    }

    private void collectNearbyItems(Player player, CompanionSession session) {
        for (Item item : session.entity.getLocation().getNearbyEntitiesByType(Item.class, GROUND_PICKUP_RADIUS)) {
            if (!canWorkAt(player, item.getLocation())) continue;
            if (!canCollect(player, item)) continue;
            deliverItem(player, item, "<aqua>Companion picked up <yellow>");
        }
    }

    private void gather(Player player, CompanionSession session) {
        if (!canWorkAt(player, player.getLocation())) {
            Msg.actionBar(player, "<red>Companion paused: no build permission here.");
            return;
        }
        ItemStack item = new ItemStack(session.material, amountFor(session.material));
        Map<Integer, ItemStack> overflow = player.getInventory().addItem(item);
        for (ItemStack left : overflow.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), left);
        }
        Msg.actionBar(player, "<aqua>Companion gathered <yellow>" + item.getAmount() + "x "
                + session.material.name().toLowerCase(Locale.ROOT));
    }

    private boolean canCollect(Player player, Item item) {
        if (item == null || item.isDead() || !item.isValid()) return false;
        UUID owner = item.getOwner();
        if (owner != null && !owner.equals(player.getUniqueId())) return false;
        UUID thrower = item.getThrower();
        if (owner == null && thrower != null && !thrower.equals(player.getUniqueId())) return false;
        return true;
    }

    private void deliverItem(Player player, Item item, String messagePrefix) {
        ItemStack stack = item.getItemStack();
        Map<Integer, ItemStack> overflow = player.getInventory().addItem(stack);
        if (overflow.isEmpty()) {
            item.remove();
            Msg.actionBar(player, messagePrefix + stack.getAmount() + "x "
                    + stack.getType().name().toLowerCase(Locale.ROOT));
            return;
        }

        ItemStack leftover = overflow.values().iterator().next();
        int moved = stack.getAmount() - leftover.getAmount();
        if (moved > 0) {
            item.setItemStack(leftover);
            Msg.actionBar(player, messagePrefix + moved + "x "
                    + stack.getType().name().toLowerCase(Locale.ROOT));
        }
    }

    private int amountFor(Material material) {
        int max = Math.max(1, material.getMaxStackSize());
        return Math.min(MAX_STACK_GATHER, max);
    }

    private boolean canWorkAt(Player player, Location location) {
        if (player.hasPermission("novablock.companion.build.bypass")) return true;
        Island island = plugin.islands().atLocation(location);
        if (island == null) {
            return location.getWorld() == null || !location.getWorld().getName().equals(IslandWorldManager.WORLD_NAME);
        }
        if (isProtectedOneBlockColumn(island, location)) return false;
        return plugin.islands().canBuild(player, location);
    }

    private boolean isProtectedOneBlockColumn(Island island, Location location) {
        Location center = island.centerBlock();
        return location.getBlockX() == center.getBlockX()
                && location.getBlockZ() == center.getBlockZ()
                && location.getBlockY() >= center.getBlockY() - 1
                && location.getBlockY() <= center.getBlockY() + 1;
    }

    private void playDisc(Player player, CompanionSession session) {
        session.musicTicks = 0;
        player.stopSound(session.discSound, SoundCategory.RECORDS);
        player.playSound(player.getLocation(), session.discSound, SoundCategory.RECORDS, 0.8f, 1f);
    }

    private int musicReplayTicks(Sound sound) {
        if (sound == null) return 0;
        return switch (sound.name()) {
            case "MUSIC_DISC_11" -> 71 * 20;
            case "MUSIC_DISC_13", "MUSIC_DISC_5" -> 178 * 20;
            case "MUSIC_DISC_BLOCKS" -> 345 * 20;
            case "MUSIC_DISC_CAT", "MUSIC_DISC_CHIRP" -> 185 * 20;
            case "MUSIC_DISC_FAR" -> 174 * 20;
            case "MUSIC_DISC_MALL" -> 197 * 20;
            case "MUSIC_DISC_MELLOHI" -> 96 * 20;
            case "MUSIC_DISC_STAL" -> 150 * 20;
            case "MUSIC_DISC_STRAD" -> 188 * 20;
            case "MUSIC_DISC_WARD" -> 251 * 20;
            case "MUSIC_DISC_WAIT" -> 238 * 20;
            case "MUSIC_DISC_PIGSTEP" -> 149 * 20;
            case "MUSIC_DISC_OTHERSIDE" -> 195 * 20;
            case "MUSIC_DISC_RELIC" -> 218 * 20;
            case "MUSIC_DISC_CREATOR" -> 176 * 20;
            case "MUSIC_DISC_CREATOR_MUSIC_BOX" -> 73 * 20;
            case "MUSIC_DISC_PRECIPICE" -> 299 * 20;
            default -> 180 * 20;
        };
    }

    private void denied(Player player) {
        Msg.send(player, "<red>You don't have permission.");
    }

    @EventHandler(ignoreCancelled = true)
    public void onItemVoid(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Item item)) return;
        if (event.getCause() != EntityDamageEvent.DamageCause.VOID) return;

        Player player = ownerFor(item);
        if (player == null) return;
        CompanionSession session = sessions.get(player.getUniqueId());
        if (session == null || session.entity == null || session.entity.isDead()) return;
        if (!session.entity.getWorld().equals(item.getWorld())) return;
        if (session.entity.getLocation().distanceSquared(item.getLocation()) > VOID_RECOVERY_RADIUS * VOID_RECOVERY_RADIUS) return;
        if (!canWorkAt(player, item.getLocation())) return;

        event.setCancelled(true);
        deliverItem(player, item, "<aqua>Companion recovered from void <yellow>");
    }

    private Player ownerFor(Item item) {
        UUID owner = item.getOwner();
        if (owner != null) return Bukkit.getPlayer(owner);
        UUID thrower = item.getThrower();
        if (thrower != null) return Bukkit.getPlayer(thrower);

        Player nearest = null;
        double best = VOID_RECOVERY_RADIUS * VOID_RECOVERY_RADIUS;
        for (UUID id : sessions.keySet()) {
            Player player = Bukkit.getPlayer(id);
            if (player == null || !player.getWorld().equals(item.getWorld())) continue;
            double dist = player.getLocation().distanceSquared(item.getLocation());
            if (dist < best) {
                best = dist;
                nearest = player;
            }
        }
        return nearest;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        CompanionSession session = sessions.remove(event.getPlayer().getUniqueId());
        if (session != null) session.removeEntity();
    }

    private static final class CompanionSession {
        private Allay entity;
        private Material material;
        private Sound discSound;
        private int gatherTicks;
        private int musicTicks;
        private int musicReplayTicks;

        private CompanionSession(Allay entity, Material material, Sound discSound, int musicReplayTicks) {
            this.entity = entity;
            this.material = material;
            this.discSound = discSound;
            this.musicReplayTicks = musicReplayTicks;
        }

        private void removeEntity() {
            if (entity != null && !entity.isDead()) entity.remove();
            entity = null;
        }
    }
}
