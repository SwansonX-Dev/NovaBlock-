package com.nova.novablock.island;

/**
 * Membership rank within an island team. The owner is always {@link #OWNER}
 * and cannot be demoted or removed except by deleting the island. Co-owners
 * help run the roster and spend the island bank; members can play and deposit.
 *
 * <p>Capabilities (see {@link #canManageRoster()} / {@link #canSpendBank()} /
 * {@link #canWithdrawBank()}):
 * <ul>
 *   <li><b>OWNER</b> — everything: promote/demote, invite, kick, deposit,
 *       spend bank on upgrades, withdraw bank to wallet, delete island.</li>
 *   <li><b>CO_OWNER</b> — invite, kick members, deposit, spend bank on
 *       upgrades. Cannot withdraw, promote/demote, or delete.</li>
 *   <li><b>MEMBER</b> — play and deposit into the bank. No management.</li>
 * </ul>
 */
public enum IslandRole {

    OWNER("Owner", "#FFD24D"),
    CO_OWNER("Co-Owner", "#7FFFE0"),
    MEMBER("Member", "#A0A0A0");

    public final String displayName;
    public final String color;

    IslandRole(String displayName, String color) {
        this.displayName = displayName;
        this.color = color;
    }

    /** Owner and co-owners may invite and kick members. */
    public boolean canManageRoster() { return this == OWNER || this == CO_OWNER; }

    /** Owner and co-owners may spend the island bank on upgrades. */
    public boolean canSpendBank() { return this == OWNER || this == CO_OWNER; }

    /** Only the owner may withdraw bank coins back to a personal wallet. */
    public boolean canWithdrawBank() { return this == OWNER; }

    /** Only the owner may promote/demote and delete the island. */
    public boolean canManageRoles() { return this == OWNER; }

    public static IslandRole byKey(String key) {
        if (key == null) return MEMBER;
        try { return valueOf(key.toUpperCase()); }
        catch (IllegalArgumentException ex) { return MEMBER; }
    }
}
