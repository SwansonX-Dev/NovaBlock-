package com.nova.novablock.api;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Fired when a player breaks a OneBlock that NovaBlock handles itself, after the
 * drops and rewards have been applied and the replacement block has been set.
 *
 * <p>NovaBlock takes over these breaks and cancels the vanilla BlockBreakEvent
 * (see {@code BlockListener#onBreak} and {@code BlockListener#onCommunityBreak}),
 * so external plugins listening on BlockBreakEvent with {@code ignoreCancelled = true}
 * never observe them — this is the supported way to see a OneBlock break.
 *
 * <p>Note this covers only the blocks NovaBlock intercepts: the island OneBlock
 * centre and the community hub block. Personal OneBlock nodes
 * ({@code CommunityNodeManager}) are NOT reported here because their break is
 * left uncancelled and is already visible as an ordinary BlockBreakEvent —
 * firing this for them too would double-count.
 *
 * <p>Purely a notification: the break is already resolved by the time it fires,
 * so it is not cancellable. Fired synchronously on the main thread.
 */
public final class NovaBlockBreakEvent extends PlayerEvent {

    /** Which OneBlock the break came from. */
    public enum Source {
        /** An island's own OneBlock centre, in any dimension. */
        ISLAND_CENTRE,
        /** The shared community hub OneBlock. */
        COMMUNITY_HUB
    }

    private static final HandlerList HANDLERS = new HandlerList();

    private final Material material;
    private final Location location;
    private final Source source;

    public NovaBlockBreakEvent(@NotNull Player player,
                               @NotNull Material material,
                               @NotNull Location location,
                               @NotNull Source source) {
        super(player);
        this.material = material;
        this.location = location;
        this.source = source;
    }

    /**
     * The material that was broken — captured before the replacement block was
     * set, so this is what the player actually mined.
     */
    public @NotNull Material getMaterial() {
        return material;
    }

    /** Where the break happened. */
    public @NotNull Location getLocation() {
        return location;
    }

    /** Whether this was an island OneBlock centre or the community hub block. */
    public @NotNull Source getSource() {
        return source;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static @NotNull HandlerList getHandlerList() {
        return HANDLERS;
    }
}
