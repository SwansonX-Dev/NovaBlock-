package com.nova.novablock.island;

import com.nova.novablock.NovaBlock;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Transient invites — kept in memory only, expire after 60 seconds.
 * Invitee → (islandId, expiresAt). One pending invite per recipient at a time;
 * sending a new one overwrites the old. Stays simple on purpose.
 */
public class InviteManager {

    private static final long TTL_MS = 60_000L;

    private final NovaBlock plugin;
    private final Map<UUID, Invite> pending = new HashMap<>();

    public InviteManager(NovaBlock plugin) { this.plugin = plugin; }

    public void invite(UUID inviteeId, UUID islandId) {
        pending.put(inviteeId, new Invite(islandId, System.currentTimeMillis() + TTL_MS));
    }

    public UUID resolve(UUID inviteeId) {
        Invite inv = pending.remove(inviteeId);
        if (inv == null || System.currentTimeMillis() > inv.expiresAt) return null;
        return inv.islandId;
    }

    public boolean has(UUID inviteeId) {
        Invite inv = pending.get(inviteeId);
        if (inv == null) return false;
        if (System.currentTimeMillis() > inv.expiresAt) { pending.remove(inviteeId); return false; }
        return true;
    }

    private record Invite(UUID islandId, long expiresAt) {}
}
