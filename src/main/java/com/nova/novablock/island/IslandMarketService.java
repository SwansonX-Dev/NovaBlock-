package com.nova.novablock.island;

import com.nova.novablock.NovaBlock;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Player-to-player island market: an owner lists their island at or above its
 * {@link IslandValuation} floor, another player buys it and takes ownership.
 *
 * <p><b>The one-island constraint.</b> A buyer must currently be islandless, because the
 * owner→island index maps a player to exactly one island. That still leaves a working
 * market — sell yours, then buy a better one — but it means you cannot simply accumulate
 * islands. This restriction is the first thing that lifts when multi-island support lands;
 * {@link #buy} is written so only the guard has to go.
 *
 * <p>Listings live in {@code market.yml}, keyed by island UUID, and are re-validated on
 * load: a listing whose island no longer exists (deleted, purged, wiped) is dropped rather
 * than left as a live offer on a dead island.
 */
public class IslandMarketService {

    /** An island currently offered for sale. */
    public record Listing(UUID islandId, UUID seller, long price, long listedAtMillis) {}

    private final NovaBlock plugin;
    private final Map<UUID, Listing> byIsland = new LinkedHashMap<>();
    private final File file;

    public IslandMarketService(NovaBlock plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "market.yml");
    }

    // ---- lifecycle ---------------------------------------------------------

    public void load() {
        byIsland.clear();
        if (!file.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        var section = y.getConfigurationSection("listings");
        if (section == null) return;
        int dropped = 0;
        for (String key : section.getKeys(false)) {
            UUID islandId;
            try {
                islandId = UUID.fromString(key);
            } catch (IllegalArgumentException e) {
                dropped++;
                continue;
            }
            // Don't resurrect a listing for an island that no longer exists.
            if (plugin.islands().get(islandId) == null) {
                dropped++;
                continue;
            }
            String sellerStr = section.getString(key + ".seller", "");
            long price = section.getLong(key + ".price", 0L);
            long listedAt = section.getLong(key + ".listedAt", 0L);
            try {
                byIsland.put(islandId,
                        new Listing(islandId, UUID.fromString(sellerStr), price, listedAt));
            } catch (IllegalArgumentException e) {
                dropped++;
            }
        }
        if (dropped > 0) {
            plugin.getLogger().info("Island market: dropped " + dropped
                    + " listing(s) for islands that no longer exist.");
            save();
        }
    }

    public void save() {
        YamlConfiguration y = new YamlConfiguration();
        for (Listing l : byIsland.values()) {
            String k = "listings." + l.islandId();
            y.set(k + ".seller", l.seller().toString());
            y.set(k + ".price", l.price());
            y.set(k + ".listedAt", l.listedAtMillis());
        }
        try {
            y.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save market.yml: " + e.getMessage());
        }
    }

    // ---- queries -----------------------------------------------------------

    /** Live listings, cheapest first. */
    public List<Listing> listings() {
        List<Listing> out = new ArrayList<>(byIsland.values());
        out.sort(Comparator.comparingLong(Listing::price));
        return out;
    }

    public Listing listingFor(UUID islandId) { return byIsland.get(islandId); }

    public boolean isListed(UUID islandId) { return byIsland.containsKey(islandId); }

    public long floorPrice(@NotNull IslandData data) {
        return plugin.valuations().floorPrice(data);
    }

    // ---- mutations ---------------------------------------------------------

    /** Why a list/buy attempt was refused, so callers can explain rather than fail silently. */
    public enum Result {
        OK,
        NOT_OWNER,
        BELOW_FLOOR,
        ALREADY_LISTED,
        NOT_LISTED,
        OWN_LISTING,
        BUYER_HAS_ISLAND,
        CANT_AFFORD,
        ISLAND_GONE,
        TRANSFER_FAILED
    }

    /** Lists the seller's island at {@code price}, which must be at or above the floor. */
    public Result list(@NotNull Player seller, long price) {
        Island island = plugin.islands().ofPlayer(seller);
        if (island == null) return Result.ISLAND_GONE;
        IslandData data = island.data();
        if (!data.getOwner().equals(seller.getUniqueId())) return Result.NOT_OWNER;
        if (byIsland.containsKey(data.getId())) return Result.ALREADY_LISTED;
        if (price < floorPrice(data)) return Result.BELOW_FLOOR;

        byIsland.put(data.getId(),
                new Listing(data.getId(), seller.getUniqueId(), price, System.currentTimeMillis()));
        save();
        return Result.OK;
    }

    /** Removes a listing. Used by the seller, by admins, and by island deletion/purge. */
    public boolean unlist(UUID islandId) {
        if (byIsland.remove(islandId) == null) return false;
        save();
        return true;
    }

    /**
     * Buys a listed island. On success the price moves from buyer to seller, the buyer
     * becomes the owner, and the seller is left islandless (they get a fresh island on
     * their next join).
     *
     * <p>Coins are only withdrawn once every check has passed, and are refunded if the
     * ownership transfer itself fails — so a failed buy can never eat the buyer's money.
     */
    public Result buy(@NotNull Player buyer, @NotNull UUID islandId) {
        Listing listing = byIsland.get(islandId);
        if (listing == null) return Result.NOT_LISTED;
        if (listing.seller().equals(buyer.getUniqueId())) return Result.OWN_LISTING;

        Island island = plugin.islands().get(islandId);
        if (island == null) {
            // Island vanished under the listing — clean up rather than leave it live.
            unlist(islandId);
            return Result.ISLAND_GONE;
        }
        // Multi-island: the buyer needs room under their cap, not zero islands.
        if (!plugin.islands().canCreateAnother(buyer)) return Result.BUYER_HAS_ISLAND;
        if (plugin.economy().balance(buyer) < listing.price()) return Result.CANT_AFFORD;

        if (!plugin.economy().spend(buyer, listing.price())) return Result.CANT_AFFORD;
        if (!plugin.islands().transferOwnership(island, buyer.getUniqueId())) {
            plugin.economy().deposit(buyer, listing.price());   // refund; nothing moved
            return Result.TRANSFER_FAILED;
        }

        OfflinePlayer seller = Bukkit.getOfflinePlayer(listing.seller());
        plugin.economy().deposit(seller, listing.price());
        unlist(islandId);
        island.data().touchActivity();
        return Result.OK;
    }
}
