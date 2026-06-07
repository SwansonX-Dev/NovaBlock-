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
            register(island);
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
        for (Island i : byId.values()) {
            String islandWorld = i.data().getWorldName();
            boolean worldMatches = worldName.equals(islandWorld) || worldName.equals(netherWorldName);
            if (!worldMatches) continue;
            if (i.data().getSlotX() == slotX && i.data().getSlotZ() == slotZ) return i;
        }
        return null;
    }

    private int nearestSlot(int blockCoord) {
        int halfSlot = IslandWorldManager.SLOT_SIZE / 2;
        return Math.floorDiv(blockCoord + halfSlot, IslandWorldManager.SLOT_SIZE);
    }

    public boolean canBuild(Player player, Location loc) {
        if (player.hasPermission("novablock.build.bypass") || player.hasPermission("novablock.admin")) return true;
        Island island = atLocation(loc);
        if (island == null) return true;
        return island.isMember(player) || island.data().isFlag(IslandFlag.VISITOR_BUILD);
    }

    /** True if the player may open containers (chests, barrels, hoppers, etc.) at this location. */
    public boolean canAccessContainers(Player player, Location loc) {
        if (player.hasPermission("novablock.build.bypass") || player.hasPermission("novablock.admin")) return true;
        Island island = atLocation(loc);
        if (island == null) return true;
        return island.isMember(player) || island.data().isFlag(IslandFlag.VISITOR_CONTAINER_ACCESS);
    }

    /** Starter bank gift for new island owners. 100 coins, or the bank's min deposit — whichever is larger. */
    private static final long STARTER_BANK_COINS = 100L;

    public Island create(Player owner) {
        if (ofPlayer(owner) != null) return ofPlayer(owner);
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
        for (UUID m : island.data().getMembers()) playerToIsland.remove(m);
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

    public void addMember(Island island, UUID playerId) {
        island.data().getMembers().add(playerId);
        playerToIsland.put(playerId, island.data().getId());
        plugin.storage().saveIsland(island.data());
    }

    /** Remove a non-owner member from an island. Owners must use /obadmin wipe. */
    public boolean removeMember(Island island, UUID playerId) {
        if (island.data().getOwner().equals(playerId)) return false;
        if (!island.data().getMembers().remove(playerId)) return false;
        island.data().getRoles().remove(playerId);
        playerToIsland.remove(playerId);
        plugin.storage().saveIsland(island.data());
        return true;
    }

    /** Set a member's role and persist. No-op for the owner or non-members. */
    public void setMemberRole(Island island, UUID playerId, IslandRole role) {
        island.data().setRole(playerId, role);
        plugin.storage().saveIsland(island.data());
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
