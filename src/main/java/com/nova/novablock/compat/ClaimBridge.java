package com.nova.novablock.compat;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Reflection bridge to xGuard's claim API. Lets NovaBlock check whether a player
 * owns the claim at a location without a compile-time dependency on xGuard.
 *
 * <p>Used to gate community-claim minion placement: minions place via an
 * interact event that xGuard's BlockPlaceEvent protection never sees, so we must
 * verify claim ownership explicitly. Returns {@code false} (deny) whenever xGuard
 * is absent or anything goes wrong — the safe default for a claim-gated action.
 */
public final class ClaimBridge {

    private static Method registryMethod;   // XGuardPaperPlugin#registry()
    private static Method atMethod;          // ClaimRegistry#at(String, int, int)
    private static Method isOwnerMethod;     // Claim#isOwner(UUID)
    private static Plugin xguard;

    private ClaimBridge() {}

    /** True if xGuard reports {@code player} as the owner of a claim covering {@code loc}. */
    public static boolean ownsClaimAt(Player player, Location loc) {
        if (player == null || loc == null || loc.getWorld() == null) return false;
        try {
            if (!resolve()) return false;
            Object registry = registryMethod.invoke(xguard);
            if (registry == null) return false;
            if (atMethod == null) {
                atMethod = registry.getClass().getMethod("at", String.class, int.class, int.class);
            }
            Object claim = atMethod.invoke(registry, loc.getWorld().getName(), loc.getBlockX(), loc.getBlockZ());
            if (claim == null) return false;
            if (isOwnerMethod == null) {
                isOwnerMethod = claim.getClass().getMethod("isOwner", UUID.class);
            }
            Object owns = isOwnerMethod.invoke(claim, player.getUniqueId());
            return owns instanceof Boolean b && b;
        } catch (Throwable t) {
            return false;
        }
    }

    /** True if a claim exists at the location at all (owned by anyone). */
    public static boolean hasClaimAt(Location loc) {
        if (loc == null || loc.getWorld() == null) return false;
        try {
            if (!resolve()) return false;
            Object registry = registryMethod.invoke(xguard);
            if (registry == null) return false;
            if (atMethod == null) {
                atMethod = registry.getClass().getMethod("at", String.class, int.class, int.class);
            }
            return atMethod.invoke(registry, loc.getWorld().getName(), loc.getBlockX(), loc.getBlockZ()) != null;
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean resolve() throws NoSuchMethodException {
        if (xguard != null && xguard.isEnabled() && registryMethod != null) return true;
        Plugin xg = Bukkit.getPluginManager().getPlugin("xGuard");
        if (xg == null || !xg.isEnabled()) { xguard = null; registryMethod = null; return false; }
        xguard = xg;
        registryMethod = xg.getClass().getMethod("registry");
        return true;
    }
}
