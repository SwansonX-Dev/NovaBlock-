package com.nova.novablock.social;

import com.nova.novablock.NovaBlock;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * Persistent friends list + pending requests. Each player has three UUID
 * sets — confirmed friends, incoming requests, outgoing requests — plus a
 * notify-on-join opt-out.
 *
 * <p>Persisted to {@code friends.yml} via the debounced-async-save pattern
 * used elsewhere (FlagManager, PlayerSpawnManager). Reads are concurrent;
 * writes synchronise on the affected records.
 */
public class FriendManager {

    public enum AddResult {
        SENT,
        ALREADY_FRIENDS,
        ALREADY_SENT,
        ACCEPTED_INCOMING,   // The "request" they tried to send was already incoming from the other side
        SELF
    }

    public enum AcceptResult { OK, NO_REQUEST, ALREADY_FRIENDS }

    private final NovaBlock plugin;
    private final File file;
    private FileConfiguration data;
    private final ConcurrentMap<UUID, Record> cache = new ConcurrentHashMap<>();
    private final AtomicBoolean dirty = new AtomicBoolean(false);
    private static final long SAVE_DEBOUNCE_TICKS = 60L;

    public FriendManager(NovaBlock plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "friends.yml");
        load();
    }

    private void load() {
        if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
        if (!file.exists()) {
            try { file.createNewFile(); }
            catch (IOException e) { plugin.getLogger().severe("Could not create friends.yml: " + e.getMessage()); }
        }
        data = YamlConfiguration.loadConfiguration(file);
        cache.clear();
        ConfigurationSection root = data.getConfigurationSection("players");
        if (root == null) return;
        for (String key : root.getKeys(false)) {
            UUID uuid;
            try { uuid = UUID.fromString(key); } catch (IllegalArgumentException e) { continue; }
            ConfigurationSection section = root.getConfigurationSection(key);
            if (section == null) continue;
            Record record = new Record();
            record.friends.addAll(readUuids(section.getStringList("friends")));
            record.incoming.addAll(readUuids(section.getStringList("incoming")));
            record.outgoing.addAll(readUuids(section.getStringList("outgoing")));
            record.notifyJoin = section.getBoolean("notify-join", true);
            cache.put(uuid, record);
        }
    }

    private static List<UUID> readUuids(List<String> raw) {
        List<UUID> out = new ArrayList<>(raw.size());
        for (String s : raw) {
            try { out.add(UUID.fromString(s)); } catch (IllegalArgumentException ignored) {}
        }
        return out;
    }

    private @NotNull Record get(UUID uuid) {
        return cache.computeIfAbsent(uuid, u -> new Record());
    }

    public Set<UUID> friends(UUID uuid) {
        Record r = cache.get(uuid);
        return r == null ? Set.of() : Collections.unmodifiableSet(r.friends);
    }

    public Set<UUID> incoming(UUID uuid) {
        Record r = cache.get(uuid);
        return r == null ? Set.of() : Collections.unmodifiableSet(r.incoming);
    }

    public Set<UUID> outgoing(UUID uuid) {
        Record r = cache.get(uuid);
        return r == null ? Set.of() : Collections.unmodifiableSet(r.outgoing);
    }

    public boolean areFriends(UUID a, UUID b) {
        Record r = cache.get(a);
        return r != null && r.friends.contains(b);
    }

    public boolean wantsJoinNotify(UUID uuid) {
        Record r = cache.get(uuid);
        return r == null || r.notifyJoin;
    }

    public void setNotifyJoin(UUID uuid, boolean enabled) {
        get(uuid).notifyJoin = enabled;
        scheduleSave();
    }

    public List<Player> onlineFriends(UUID uuid) {
        Set<UUID> ids = friends(uuid);
        if (ids.isEmpty()) return List.of();
        return ids.stream()
                .map(Bukkit::getPlayer)
                .filter(p -> p != null && p.isOnline())
                .collect(Collectors.toList());
    }

    /** Result drives the command's response message. */
    public AddResult addRequest(UUID from, UUID to) {
        if (from.equals(to)) return AddResult.SELF;
        Record me = get(from);
        Record other = get(to);
        synchronized (me) {
            synchronized (other) {
                if (me.friends.contains(to)) return AddResult.ALREADY_FRIENDS;
                if (me.incoming.contains(to)) {
                    // They already requested — accept it instead of double-sending.
                    me.incoming.remove(to);
                    me.outgoing.remove(to);
                    other.outgoing.remove(from);
                    other.incoming.remove(from);
                    me.friends.add(to);
                    other.friends.add(from);
                    scheduleSave();
                    return AddResult.ACCEPTED_INCOMING;
                }
                if (me.outgoing.contains(to)) return AddResult.ALREADY_SENT;
                me.outgoing.add(to);
                other.incoming.add(from);
                scheduleSave();
                return AddResult.SENT;
            }
        }
    }

    public AcceptResult acceptRequest(UUID receiver, UUID sender) {
        if (receiver.equals(sender)) return AcceptResult.NO_REQUEST;
        Record me = get(receiver);
        Record other = get(sender);
        synchronized (me) {
            synchronized (other) {
                if (me.friends.contains(sender)) return AcceptResult.ALREADY_FRIENDS;
                if (!me.incoming.contains(sender)) return AcceptResult.NO_REQUEST;
                me.incoming.remove(sender);
                other.outgoing.remove(receiver);
                me.friends.add(sender);
                other.friends.add(receiver);
                scheduleSave();
                return AcceptResult.OK;
            }
        }
    }

    public boolean denyRequest(UUID receiver, UUID sender) {
        Record me = get(receiver);
        Record other = get(sender);
        synchronized (me) {
            synchronized (other) {
                boolean removed = me.incoming.remove(sender);
                other.outgoing.remove(receiver);
                if (removed) scheduleSave();
                return removed;
            }
        }
    }

    public boolean removeFriend(UUID a, UUID b) {
        Record ra = get(a);
        Record rb = get(b);
        synchronized (ra) {
            synchronized (rb) {
                boolean removed = ra.friends.remove(b);
                rb.friends.remove(a);
                if (removed) scheduleSave();
                return removed;
            }
        }
    }

    private void scheduleSave() {
        markPersist();
        if (!dirty.compareAndSet(false, true)) return;
        Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, this::flush, SAVE_DEBOUNCE_TICKS);
    }

    private void markPersist() {
        // Rebuild yaml from cache lazily on flush; nothing to do per-mutation.
    }

    private synchronized void flush() {
        if (!dirty.compareAndSet(true, false)) return;
        writeSnapshot();
        try { data.save(file); }
        catch (IOException e) { plugin.getLogger().severe("Could not save friends.yml: " + e.getMessage()); }
    }

    public synchronized void saveNow() {
        if (data == null) return;
        dirty.set(false);
        writeSnapshot();
        try { data.save(file); }
        catch (IOException e) { plugin.getLogger().severe("Could not save friends.yml: " + e.getMessage()); }
    }

    private void writeSnapshot() {
        data.set("players", null);
        ConfigurationSection root = data.createSection("players");
        for (var entry : cache.entrySet()) {
            Record r = entry.getValue();
            if (r.isEmpty() && r.notifyJoin) continue;
            ConfigurationSection section = root.createSection(entry.getKey().toString());
            section.set("friends", r.friends.stream().map(UUID::toString).toList());
            section.set("incoming", r.incoming.stream().map(UUID::toString).toList());
            section.set("outgoing", r.outgoing.stream().map(UUID::toString).toList());
            section.set("notify-join", r.notifyJoin);
        }
    }

    private static final class Record {
        final Set<UUID> friends = new HashSet<>();
        final Set<UUID> incoming = new HashSet<>();
        final Set<UUID> outgoing = new HashSet<>();
        boolean notifyJoin = true;

        boolean isEmpty() {
            return friends.isEmpty() && incoming.isEmpty() && outgoing.isEmpty();
        }
    }
}
