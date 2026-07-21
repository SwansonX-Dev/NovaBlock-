package com.nova.novablock.island;

import com.nova.novablock.NovaBlock;
import com.nova.novablock.phase.Phase;
import com.nova.novablock.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class IslandManager {

    private final NovaBlock plugin;
    private final Map<UUID, Island> byId = new HashMap<>();
    /**
     * Every island a player belongs to, in join order. Replaces the old
     * {@code Map<UUID,UUID>} one-island index. Insertion-ordered so "first island" is
     * stable — it's the fallback when no active island has been chosen.
     */
    private final Map<UUID, LinkedHashSet<UUID>> playerToIslands = new HashMap<>();
    /**
     * Which of a player's islands their player-scoped commands act on. Not persisted on
     * the island (it's a per-player preference) — see {@link #setActiveIsland}. Absent
     * means "use the first island", so a single-island player never has to choose.
     */
    private final Map<UUID, UUID> activeIsland = new HashMap<>();
    /** Slot-keyed index so {@link #atLocation} is O(1) instead of scanning every island. */
    private final Map<Long, UUID> bySlot = new HashMap<>();
    private int nextSlotX;
    private int nextSlotZ;

    private static long slotKey(int slotX, int slotZ) {
        return ((long) slotX << 32) ^ (slotZ & 0xFFFFFFFFL);
    }

    public IslandManager(NovaBlock plugin) {
        this.plugin = plugin;
    }

    public void loadAll() {
        for (IslandData data : plugin.storage().loadAllIslands()) {
            // Freshly hydrated data already matches its file; the load-time setters
            // flipped the dirty flag, so reset it. Any genuine change below (e.g.
            // the Nether migration) re-marks it.
            data.clearDirty();
            plugin.worlds().ensureWorld(data.getWorldName());
            Island island = new Island(plugin, data);
            // Silent migration: any island already past the Nether Gates phase
            // (phaseIndex >= 6, i.e. has cleared Phase 6) gets its Nether
            // platform built on first load. Brand-new islands flow through
            // BlockListener.advancePhase when they cross the unlock point.
            if (data.getPhaseIndex() >= 6 && plugin.worlds().isNetherEnabled()) {
                if (!data.isNetherUnlocked()) data.setNetherUnlocked(true);
                island.ensureNetherPlatform();
            }
            // Silent migration: the End unlock hook fires from the Nether's
            // final-phase advance, which only exists from 0.35.0. An island that
            // conquered the Nether under an earlier build never ran it, and a
            // Nether prestige resets that track to phase 1 — putting the unlock
            // permanently out of reach. Backfill from durable evidence instead:
            // a Nether prestige, or a Nether track sitting complete.
            if (plugin.worlds().isEndEnabled() && !data.isEndUnlocked()
                    && (data.getNetherPrestigeLevel() > 0 || netherTrackComplete(data))) {
                data.setEndUnlocked(true);
            }
            // Rebuild the End pad for islands that have it open.
            if (data.isEndUnlocked() && plugin.worlds().isEndEnabled()) {
                island.ensureEndPlatform();
            }
            register(island);
        }
        recalculateNextSlot();
    }

    /** True if the Nether track is parked on its final phase with the block quota met. */
    private boolean netherTrackComplete(IslandData data) {
        int lastIdx = plugin.phases().phaseCount(Dimension.NETHER) - 1;
        if (lastIdx < 0) return false;
        if (data.getPhaseIndex(Dimension.NETHER) < lastIdx) return false;
        Phase last = plugin.phases().get(Dimension.NETHER, lastIdx);
        return last != null && data.getPhaseProgress(Dimension.NETHER) >= last.getRequiredBlocks();
    }

    /**
     * Tear open the End for an island: flips the flag, builds the pad and
     * announces it. No-op (false) when the End is disabled or already open.
     * Called both from the Nether's final-phase advance and from a Nether
     * prestige — either one is proof the Nether was conquered, and the prestige
     * path is what keeps the unlock reachable for an island that cleared the
     * Nether before this hook existed.
     */
    public boolean unlockEnd(Island island, Player fallbackName) {
        if (!plugin.worlds().isEndEnabled() || island.data().isEndUnlocked()) return false;
        island.data().setEndUnlocked(true);
        island.ensureEndPlatform();
        String ownerName = Bukkit.getOfflinePlayer(island.data().getOwner()).getName();
        if (ownerName == null && fallbackName != null) ownerName = fallbackName.getName();
        if (ownerName == null) return true;
        Bukkit.broadcast(Msg.mm("<#9C27B0>✦ <light_purple>" + ownerName
                + "<gray>'s island has <#B47BFF>torn open the End<gray>! <dark_gray>(/ob home end)"));
        return true;
    }

    /** Persist every island. Used on shutdown; storage drains its IO queue afterwards. */
    public void saveAll() {
        for (Island i : byId.values()) plugin.storage().saveIsland(i.data());
    }

    /**
     * Autosave pass: persist only islands with unsaved changes. saveIsland builds
     * the YAML snapshot on this (main) thread, clears the dirty flag, and writes to
     * disk on a background thread — so a quiet server does almost no work here and
     * a busy one never blocks the tick on file I/O. Returns the number written.
     */
    public int saveDirty() {
        int saved = 0;
        for (Island i : byId.values()) {
            if (!i.data().isDirty()) continue;
            plugin.storage().saveIsland(i.data());
            saved++;
        }
        return saved;
    }

    /** Current computed island level (always accurate; derived from progress). */
    public int levelOf(Island island) {
        return island == null ? 1 : IslandLevel.levelOf(island.data());
    }

    /**
     * Recompute the island level and, if it has risen since we last announced,
     * notify every online member and persist the new level. Cheap to call often
     * — only does I/O on an actual level-up.
     */
    public void checkLevelUp(Island island) {
        if (island == null) return;
        IslandData d = island.data();
        int computed = IslandLevel.levelOf(d);
        int known = Math.max(1, d.getLevel());
        if (computed <= known) return;
        d.setLevel(computed);
        plugin.storage().saveIsland(d);
        for (UUID id : d.getMembers()) {
            Player m = org.bukkit.Bukkit.getPlayer(id);
            if (m == null) continue;
            com.nova.novablock.util.Msg.title(m, "<gold>★ Island Level " + computed,
                    "<yellow>Your island grew stronger!");
            com.nova.novablock.util.Msg.send(m,
                    "<gold>Island Level Up <gray>— your island is now <yellow>Level " + computed + "<gray>.");
            m.playSound(m.getLocation(), org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1.1f);
        }
    }

    /**
     * Checks the in-memory indexes against the islands themselves, which are the source of
     * truth (each island's member list is what's on disk). The multi-island change replaced
     * a one-entry-per-player index with several derived maps, and a bug there wouldn't throw
     * — it would quietly hand someone the wrong island, or none. This makes that visible.
     *
     * @return human-readable problems; empty means every index agrees with the island data
     */
    public List<String> verifyIntegrity() {
        List<String> problems = new ArrayList<>();

        // Two islands must never occupy one slot — that would make atLocation ambiguous
        // and let one island's breaks be credited to the other.
        Map<Long, UUID> seenSlots = new HashMap<>();
        for (Island i : byId.values()) {
            IslandData d = i.data();
            long key = slotKey(d.getSlotX(), d.getSlotZ());
            UUID clash = seenSlots.put(key, d.getId());
            if (clash != null) {
                problems.add("slot (" + d.getSlotX() + "," + d.getSlotZ() + ") shared by islands "
                        + clash + " and " + d.getId());
            }
            if (!d.getId().equals(bySlot.get(key))) {
                problems.add("island " + d.getId() + " missing or wrong in the slot index");
            }
            if (!d.getMembers().contains(d.getOwner())) {
                problems.add("island " + d.getId() + " owner " + d.getOwner() + " is not in its member list");
            }
            // Every member must be able to find this island through the player index.
            for (UUID m : d.getMembers()) {
                LinkedHashSet<UUID> owned = playerToIslands.get(m);
                if (owned == null || !owned.contains(d.getId())) {
                    problems.add("member " + m + " is not indexed against island " + d.getId());
                }
            }
        }

        // And the reverse: nothing in the player index may point at a stale island.
        for (var e : playerToIslands.entrySet()) {
            for (UUID islandId : e.getValue()) {
                Island i = byId.get(islandId);
                if (i == null) {
                    problems.add("player " + e.getKey() + " indexed against missing island " + islandId);
                } else if (!i.data().getMembers().contains(e.getKey())) {
                    problems.add("player " + e.getKey() + " indexed against island " + islandId
                            + " they are not a member of");
                }
            }
        }

        // An active pointer aimed at an island they left would silently strand them.
        for (var e : activeIsland.entrySet()) {
            LinkedHashSet<UUID> owned = playerToIslands.get(e.getKey());
            if (owned == null || !owned.contains(e.getValue())) {
                problems.add("player " + e.getKey() + " has an active island they don't belong to");
            }
        }

        // Stale slot entries pointing at islands that no longer exist.
        for (var e : bySlot.entrySet()) {
            if (!byId.containsKey(e.getValue())) {
                problems.add("slot index holds deleted island " + e.getValue());
            }
        }
        return problems;
    }

    /**
     * Rebuilds every derived index from the islands themselves. Safe to run at any time —
     * it only touches in-memory lookups, never island data or files, so a bad index can be
     * repaired without a restart and without risking the data behind it.
     *
     * @return the number of islands reindexed
     */
    public int rebuildIndexes() {
        playerToIslands.clear();
        bySlot.clear();
        for (Island i : byId.values()) {
            IslandData d = i.data();
            bySlot.put(slotKey(d.getSlotX(), d.getSlotZ()), d.getId());
            for (UUID m : d.getMembers()) {
                playerToIslands.computeIfAbsent(m, k -> new LinkedHashSet<>()).add(d.getId());
            }
        }
        // Drop active pointers that no longer resolve.
        activeIsland.entrySet().removeIf(e -> {
            LinkedHashSet<UUID> owned = playerToIslands.get(e.getKey());
            return owned == null || !owned.contains(e.getValue());
        });
        recalculateNextSlot();
        return byId.size();
    }

    private void register(Island island) {
        IslandData data = island.data();
        byId.put(data.getId(), island);
        bySlot.put(slotKey(data.getSlotX(), data.getSlotZ()), data.getId());
        for (UUID m : data.getMembers()) {
            playerToIslands.computeIfAbsent(m, k -> new LinkedHashSet<>()).add(data.getId());
        }
    }

    private void unregisterMember(UUID playerId, UUID islandId) {
        LinkedHashSet<UUID> owned = playerToIslands.get(playerId);
        if (owned == null) return;
        owned.remove(islandId);
        if (owned.isEmpty()) playerToIslands.remove(playerId);
        // Never leave the active pointer aimed at an island they no longer belong to.
        if (islandId.equals(activeIsland.get(playerId))) activeIsland.remove(playerId);
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

    /**
     * The player's <b>active</b> island — the one their player-scoped commands and UI act
     * on. Falls back to their first island when they haven't chosen, so a single-island
     * player behaves exactly as before multi-island existed.
     *
     * <p>This deliberately keeps the old signature and old meaning for the ~78 existing
     * call sites: "the player's island" is now "the island they're currently working with".
     * Anything that should follow the player's <em>feet</em> instead — block breaks, quest
     * credit, scoreboards — must use {@link #contextIsland} rather than this.
     */
    public Island ofPlayer(UUID playerId) {
        LinkedHashSet<UUID> owned = playerToIslands.get(playerId);
        if (owned == null || owned.isEmpty()) return null;
        UUID active = activeIsland.get(playerId);
        if (active != null && owned.contains(active)) return byId.get(active);
        return byId.get(owned.iterator().next());
    }
    public Island ofPlayer(Player p) { return ofPlayer(p.getUniqueId()); }

    /** Every island the player belongs to, in join order. Never null. */
    public List<Island> islandsOf(UUID playerId) {
        LinkedHashSet<UUID> owned = playerToIslands.get(playerId);
        if (owned == null) return List.of();
        List<Island> out = new ArrayList<>(owned.size());
        for (UUID id : owned) {
            Island i = byId.get(id);
            if (i != null) out.add(i);
        }
        return out;
    }
    public List<Island> islandsOf(Player p) { return islandsOf(p.getUniqueId()); }

    /**
     * The island the player is actually standing on, if they belong to it; otherwise their
     * active island. This is the correct lookup for anything that should be attributed to
     * where the player is — quest credit, questline progress, scoreboard contents — because
     * with several islands "the player's island" and "the island under their feet" diverge.
     */
    public Island contextIsland(Player p) {
        Island here = atLocation(p.getLocation());
        if (here != null && here.data().getMembers().contains(p.getUniqueId())) return here;
        return ofPlayer(p);
    }

    /**
     * Points the player's player-scoped commands at one of their islands.
     *
     * @return false if they don't belong to that island
     */
    public boolean setActiveIsland(UUID playerId, UUID islandId) {
        LinkedHashSet<UUID> owned = playerToIslands.get(playerId);
        if (owned == null || !owned.contains(islandId)) return false;
        activeIsland.put(playerId, islandId);
        // Persist the choice so it survives a restart — otherwise everyone silently
        // reverts to their first island whenever the server comes back up.
        var prog = plugin.progression().get(playerId);
        if (prog != null) {
            prog.setActiveIslandId(islandId);
            plugin.progression().save(playerId);
        }
        return true;
    }

    /**
     * Restores a player's saved active-island choice into the live index. Called on join,
     * once their progression is loaded. Silently ignores a saved id they no longer belong
     * to (island sold, purged, or they were kicked).
     */
    public void restoreActiveIsland(UUID playerId) {
        var prog = plugin.progression().get(playerId);
        if (prog == null || prog.getActiveIslandId() == null) return;
        LinkedHashSet<UUID> owned = playerToIslands.get(playerId);
        if (owned != null && owned.contains(prog.getActiveIslandId())) {
            activeIsland.put(playerId, prog.getActiveIslandId());
        }
    }

    /** How many islands this player may own, from config plus {@code novablock.islands.max.<n>}. */
    public int maxIslands(Player p) {
        int max = Math.max(1, plugin.getConfig().getInt("islands.max-owned", 2));
        // Highest matching permission wins, mirroring the community-minion limit nodes.
        for (int n = 64; n > max; n--) {
            if (p.hasPermission("novablock.islands.max." + n)) return n;
        }
        return max;
    }

    /** Islands this player OWNS (excludes ones they're only a member of), for the cap. */
    public int ownedCount(UUID playerId) {
        int n = 0;
        for (Island i : islandsOf(playerId)) {
            if (i.data().getOwner().equals(playerId)) n++;
        }
        return n;
    }

    public boolean canCreateAnother(Player p) {
        return ownedCount(p.getUniqueId()) < maxIslands(p);
    }

    /**
     * Resolve which island a location belongs to by checking grid slot. The
     * grid is shared between the Overworld and Nether OneBlock worlds — an
     * island has the same {@code (slotX, slotZ)} in both.
     */
    public Island atLocation(Location loc) {
        if (loc.getWorld() == null) return null;
        int slotX = nearestSlot(loc.getBlockX());
        int slotZ = nearestSlot(loc.getBlockZ());
        String worldName = loc.getWorld().getName();
        String netherWorldName = plugin.worlds().netherWorldName();
        String endWorldName = plugin.worlds().endWorldName();
        // Slot-keyed lookup — this runs on every block break, and multi-island multiplies
        // the island count, so the old linear scan over every island is not affordable.
        UUID id = bySlot.get(slotKey(slotX, slotZ));
        if (id == null) return null;
        Island i = byId.get(id);
        if (i == null) return null;
        String islandWorld = i.data().getWorldName();
        boolean worldMatches = worldName.equals(islandWorld)
                || worldName.equals(netherWorldName)
                || worldName.equals(endWorldName);
        return worldMatches ? i : null;
    }

    private int nearestSlot(int blockCoord) {
        int halfSlot = IslandWorldManager.SLOT_SIZE / 2;
        return Math.floorDiv(blockCoord + halfSlot, IslandWorldManager.SLOT_SIZE);
    }

    /**
     * True if any island's slot region intersects the rectangle [minX..maxX]×[minZ..maxZ]
     * in an island world. A slot owns {@code [center-128 .. center+127]} on each axis,
     * matching {@link #nearestSlot}. Both the island overworld and nether share slot
     * coordinates, so a claim in either world is tested against every island.
     *
     * <p>Called reflectively by xGuard's claim guard to stop players claiming over an island.
     */
    public boolean intersectsAnyIsland(String worldName, int minX, int minZ, int maxX, int maxZ) {
        if (worldName == null) return false;
        boolean islandWorld = worldName.equals(plugin.worlds().worldName())
                || worldName.equals(plugin.worlds().netherWorldName())
                || worldName.equals(plugin.worlds().endWorldName());
        if (!islandWorld) return false;
        int half = IslandWorldManager.SLOT_SIZE / 2;
        for (Island i : byId.values()) {
            int cx = i.data().getSlotX() * IslandWorldManager.SLOT_SIZE;
            int cz = i.data().getSlotZ() * IslandWorldManager.SLOT_SIZE;
            int iMinX = cx - half, iMaxX = cx + half - 1;
            int iMinZ = cz - half, iMaxZ = cz + half - 1;
            if (minX <= iMaxX && maxX >= iMinX && minZ <= iMaxZ && maxZ >= iMinZ) return true;
        }
        return false;
    }

    public boolean canBuild(Player player, Location loc) {
        if (player.hasPermission("novablock.build.bypass") || player.hasPermission("novablock.admin")) return true;
        Island island = atLocation(loc);
        if (island == null) return true;
        return island.isMember(player) || island.isTrusted(player)
                || island.data().isFlag(IslandFlag.VISITOR_BUILD);
    }

    /** True if the player may open containers (chests, barrels, hoppers, etc.) at this location. */
    public boolean canAccessContainers(Player player, Location loc) {
        if (player.hasPermission("novablock.build.bypass") || player.hasPermission("novablock.admin")) return true;
        Island island = atLocation(loc);
        if (island == null) return true;
        return island.isMember(player) || island.isTrusted(player)
                || island.data().isFlag(IslandFlag.VISITOR_CONTAINER_ACCESS);
    }

    /** True if the player may use doors, trapdoors, gates, levers and buttons at this location. */
    public boolean canUseDoors(Player player, Location loc) {
        if (player.hasPermission("novablock.build.bypass") || player.hasPermission("novablock.admin")) return true;
        Island island = atLocation(loc);
        if (island == null) return true;
        return island.isMember(player) || island.isTrusted(player)
                || island.data().isFlag(IslandFlag.VISITOR_USE_DOORS);
    }

    /** Starter bank gift for new island owners. 100 coins, or the bank's min deposit — whichever is larger. */
    private static final long STARTER_BANK_COINS = 100L;

    /**
     * Creates an island for {@code owner} and makes it their active one.
     *
     * <p>Returns the existing active island when they're already at their cap, so the
     * auto-create on join stays a no-op for established players. Use
     * {@link #canCreateAnother} to tell "made a new one" from "hit the cap".
     */
    public Island create(Player owner) {
        if (!canCreateAnother(owner)) {
            Island existing = ofPlayer(owner);
            if (existing != null) return existing;
        }
        String islandWorldName = plugin.worlds().worldName();
        if (plugin.worlds().ensureWorld(islandWorldName) == null) {
            throw new IllegalStateException("OneBlock island world is not loaded: " + islandWorldName);
        }
        int[] slot = nextSlot();
        IslandData data = new IslandData(Island.newId(), owner.getUniqueId(),
                islandWorldName, slot[0], slot[1]);
        Island island = new Island(plugin, data);
        island.ensureSpawnPlatform();
        Phase first = plugin.phases().get(0);
        if (first != null) island.refillUpcoming(first, 10);
        register(island);
        // A freshly made island becomes the one they're working with.
        activeIsland.put(owner.getUniqueId(), data.getId());
        plugin.storage().saveIsland(data);
        seedStarterBank(owner);
        return island;
    }

    /**
     * Welcome gift: credit the new owner's wallet and immediately bank-deposit
     * a starter sum so they have a non-zero bank balance to play with. No-op if
     * xEconomy's bank service isn't installed.
     */
    private void seedStarterBank(Player owner) {
        dev.xsuite.economy.api.Bank bank = dev.xsuite.economy.api.XEconomy.bank();
        dev.xsuite.economy.api.Economy eco = dev.xsuite.economy.api.XEconomy.get();
        if (bank == null || eco == null) return;
        long cents = Math.max(STARTER_BANK_COINS * 100L, bank.minDepositCents());
        // Wallet credit first so the bank deposit can debit cleanly.
        eco.deposit(owner.getUniqueId(), owner.getName(), cents);
        String err = bank.deposit(owner.getUniqueId(), owner.getName(), cents);
        if (err == null) {
            com.nova.novablock.util.Msg.send(owner,
                    "<gold>★ Welcome — a starter bank account with <yellow>"
                            + (cents / 100) + " coins<gold> has been opened for you.");
        } else {
            plugin.getLogger().fine("Starter bank deposit failed for " + owner.getName() + ": " + err);
        }
    }

    public void delete(Island island) {
        // Drop any market listing first, or a purge/wipe leaves a live offer for an
        // island that no longer exists.
        if (plugin.market() != null) plugin.market().unlist(island.data().getId());
        // Unregister each member from THIS island only — a member with other islands
        // must keep them (the old index removed the player wholesale).
        for (UUID m : island.data().getMembers()) unregisterMember(m, island.data().getId());
        bySlot.remove(slotKey(island.data().getSlotX(), island.data().getSlotZ()));
        byId.remove(island.data().getId());
        if (plugin.minions() != null) plugin.minions().removeIsland(island.data().getId());
        plugin.storage().deleteIsland(island.data().getId());
    }

    public void resetPlayer(UUID playerId) {
        Island island = ofPlayer(playerId);
        if (island == null) return;
        if (island.data().getOwner().equals(playerId)) {
            delete(island);
        } else {
            removeMember(island, playerId);
        }
    }

    /**
     * Hands an island to a new owner, keeping every index consistent.
     *
     * <p>The old owner is removed from the island entirely (members, roles and the
     * owner→island index) and becomes islandless — they get a fresh island the next time
     * they join. The buyer is added as a member and takes the OWNER role. Other members
     * are left in place, so a co-op island survives a sale with its roster intact.
     *
     * <p>Refuses if the buyer already has an island, since the current index maps a player
     * to exactly one island; that restriction lifts with multi-island support.
     *
     * @return true if ownership moved
     */
    public boolean transferOwnership(Island island, UUID newOwner) {
        IslandData data = island.data();
        UUID oldOwner = data.getOwner();
        if (oldOwner.equals(newOwner)) return false;
        // The buyer must have room under their own cap — they're gaining an island, not
        // swapping one. (Offline buyers can't be permission-checked, so they get the
        // config default via ownedCount vs. the base cap.)
        Player buyer = Bukkit.getPlayer(newOwner);
        int cap = buyer != null
                ? maxIslands(buyer)
                : Math.max(1, plugin.getConfig().getInt("islands.max-owned", 2));
        if (ownedCount(newOwner) >= cap) return false;

        data.getMembers().remove(oldOwner);
        data.getRoles().remove(oldOwner);
        unregisterMember(oldOwner, data.getId());

        data.setOwner(newOwner);
        data.getMembers().add(newOwner);
        data.getRoles().put(newOwner, IslandRole.OWNER);
        playerToIslands.computeIfAbsent(newOwner, k -> new LinkedHashSet<>()).add(data.getId());
        activeIsland.put(newOwner, data.getId());

        plugin.storage().saveIsland(data);
        return true;
    }

    public void addMember(Island island, UUID playerId) {
        island.data().getMembers().add(playerId);
        playerToIslands.computeIfAbsent(playerId, k -> new LinkedHashSet<>()).add(island.data().getId());
        plugin.storage().saveIsland(island.data());
    }

    /** Remove a non-owner member from an island. Owners must use /obadmin wipe. */
    public boolean removeMember(Island island, UUID playerId) {
        if (island.data().getOwner().equals(playerId)) return false;
        if (!island.data().getMembers().remove(playerId)) return false;
        island.data().getRoles().remove(playerId);
        unregisterMember(playerId, island.data().getId());
        plugin.storage().saveIsland(island.data());
        return true;
    }

    /** Set a member's role and persist. No-op for the owner or non-members. */
    public void setMemberRole(Island island, UUID playerId, IslandRole role) {
        island.data().setRole(playerId, role);
        plugin.storage().saveIsland(island.data());
    }

    // --- trusted players -----------------------------------------------------

    /**
     * Grant a non-member build + container access on this island and persist.
     * Returns false (no change) if they're the owner, an existing member, or
     * already trusted.
     */
    public boolean addTrusted(Island island, UUID playerId) {
        if (!island.data().addTrusted(playerId)) return false;
        plugin.storage().saveIsland(island.data());
        return true;
    }

    /** Revoke a player's trust and persist. Returns false if they weren't trusted. */
    public boolean removeTrusted(Island island, UUID playerId) {
        if (!island.data().removeTrusted(playerId)) return false;
        plugin.storage().saveIsland(island.data());
        return true;
    }

    // --- island bank ---------------------------------------------------------

    /**
     * Move {@code coins} from {@code payer}'s personal wallet into the island
     * bank. Returns false (and changes nothing) if the wallet can't cover it.
     */
    public boolean bankDeposit(Island island, Player payer, long coins) {
        if (coins <= 0) return false;
        if (!plugin.economy().spend(payer, coins)) return false;
        island.data().setBankBalance(island.data().getBankBalance() + coins);
        plugin.storage().saveIsland(island.data());
        return true;
    }

    /**
     * Move {@code coins} from the island bank into {@code payee}'s wallet.
     * Returns false if the bank balance is insufficient.
     */
    public boolean bankWithdraw(Island island, org.bukkit.OfflinePlayer payee, long coins) {
        if (coins <= 0 || island.data().getBankBalance() < coins) return false;
        island.data().setBankBalance(island.data().getBankBalance() - coins);
        plugin.economy().deposit(payee, coins);
        plugin.storage().saveIsland(island.data());
        return true;
    }

    /**
     * Debit {@code coins} from the island bank for an in-plugin purchase
     * (upgrades). Returns false if the balance is insufficient. No wallet
     * movement — the coins are consumed.
     */
    public boolean bankSpend(Island island, long coins) {
        if (coins <= 0 || island.data().getBankBalance() < coins) return false;
        island.data().setBankBalance(island.data().getBankBalance() - coins);
        plugin.storage().saveIsland(island.data());
        return true;
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
