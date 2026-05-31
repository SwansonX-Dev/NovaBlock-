package com.nova.novablock.island;

import com.nova.novablock.NovaBlock;
import org.bukkit.entity.Player;

/**
 * Single source of truth for "can A visit B's island?" — used by /ob visit and
 * FriendsGui's Join button. Centralizes the membership + flag + permission
 * checks so they can never diverge between command and GUI paths.
 */
public final class IslandVisitService {

    private final NovaBlock plugin;

    public IslandVisitService(NovaBlock plugin) {
        this.plugin = plugin;
    }

    /**
     * Whether {@code viewer} is allowed to teleport to {@code target}.
     * Allowed if: viewer is a member; viewer has the admin bypass; the island
     * has the OPEN_VISITS flag enabled AND the global allow-open-visits switch
     * is on; or VISITOR_BUILD is enabled (carried for back-compat with the
     * existing visit command).
     */
    public boolean canVisit(Player viewer, Island target) {
        if (viewer == null || target == null) return false;
        if (target.isMember(viewer)) return true;
        if (viewer.hasPermission("novablock.admin")) return true;
        if (viewer.hasPermission("novablock.visit.bypass")) return true;
        boolean globalAllow = plugin.getConfig()
                .getBoolean("community.coop.allow-open-visits", true);
        if (globalAllow && target.data().isFlag(IslandFlag.OPEN_VISITS)) return true;
        if (target.data().isFlag(IslandFlag.VISITOR_BUILD)) return true;
        return false;
    }
}
