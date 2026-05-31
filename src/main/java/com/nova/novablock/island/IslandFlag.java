package com.nova.novablock.island;

import org.bukkit.Material;

/**
 * Per-island toggleable settings. Each flag has a default value, a permission
 * node that controls who can toggle it (set per-rank in LuckPerms), and GUI
 * presentation metadata.
 *
 * <p>To add a new flag: add an entry here and a handler in
 * {@link IslandFlagsManager}. Storage and the toggle GUI pick it up
 * automatically by iterating {@code values()}.
 */
public enum IslandFlag {

    // ---- safety / vanilla-difficulty toggles ----

    NATURAL_MOB_SPAWNING(
            true,
            "novablock.flag.natural_mob_spawning",
            Material.ZOMBIE_HEAD,
            "Natural Mob Spawning",
            "Vanilla hostile mobs spawning on the island.",
            "OneBlock-driven encounters still work either way."),

    CREEPER_BLOCK_DAMAGE(
            true,
            "novablock.flag.creeper_block_damage",
            Material.GUNPOWDER,
            "Creeper Block Damage",
            "Creeper explosions break blocks on the island.",
            "Entity damage is unaffected."),

    TNT_BLOCK_DAMAGE(
            true,
            "novablock.flag.tnt_block_damage",
            Material.TNT,
            "TNT Block Damage",
            "TNT, end crystals, and respawn anchors break blocks.",
            "Entity damage is unaffected."),

    MOB_GRIEFING(
            false,
            "novablock.flag.mob_griefing",
            Material.ENDERMAN_SPAWN_EGG,
            "Mob Griefing",
            "Endermen pick up blocks; withers and ravagers break them.",
            "Default off — protects your build."),

    FIRE_SPREAD(
            false,
            "novablock.flag.fire_spread",
            Material.FLINT_AND_STEEL,
            "Fire Spread",
            "Fire spreads and burns blocks on the island.",
            "Default off — fewer surprise burns."),

    PVP(
            false,
            "novablock.flag.pvp",
            Material.IRON_SWORD,
            "PVP",
            "Players can damage each other on the island.",
            "Default off."),

    VISITOR_BUILD(
            false,
            "novablock.flag.visitor_build",
            Material.OAK_DOOR,
            "Visitors Can Build",
            "Non-members can break and place blocks.",
            "The OneBlock center is always members-only."),

    VISITOR_CONTAINER_ACCESS(
            false,
            "novablock.flag.visitor_container_access",
            Material.CHEST,
            "Visitors Can Open Containers",
            "Non-members can open chests, barrels, hoppers, etc.",
            "Default off — protects your loot from passers-by."),

    OPEN_VISITS(
            false,
            "novablock.flag.open_visits",
            Material.OAK_SIGN,
            "Open Visits",
            "Anyone can teleport to your island via /ob visit.",
            "Members and admins always allowed regardless."),

    // ---- QOL / donor-rank perks ----

    ISLAND_FLY(
            false,
            "novablock.flag.island_fly",
            Material.ELYTRA,
            "Island Fly",
            "Members can fly while on the island.",
            "Flight ends the moment they leave the build area."),

    KEEP_INVENTORY(
            false,
            "novablock.flag.keep_inventory",
            Material.TOTEM_OF_UNDYING,
            "Keep Inventory On Death",
            "Members keep their inventory + XP if they die on the island."),

    ALWAYS_DAY(
            false,
            "novablock.flag.always_day",
            Material.CLOCK,
            "Always Day",
            "Members see permanent daytime while on the island.",
            "Other players' sky is unaffected — this is client-only."),

    NO_HUNGER_DRAIN(
            false,
            "novablock.flag.no_hunger_drain",
            Material.COOKED_BEEF,
            "No Hunger Drain",
            "Members don't lose hunger while on the island."),

    PHASE_AMBIENCE(
            true,
            "novablock.flag.phase_ambience",
            Material.SUNFLOWER,
            "Phase Ambience",
            "Push client time + weather based on the current phase.",
            "Default on — Snow looks snowy, Void looks midnight."),

    NIGHTMARE_MODE(
            false,
            "novablock.flag.nightmare_mode",
            Material.WITHER_SKELETON_SKULL,
            "Nightmare Mode",
            "Doubles boss stats and halves XP. Disables keep-inventory.",
            "<red>Cannot be undone within the same prestige cycle.");

    public final boolean defaultValue;
    public final String permission;
    public final Material icon;
    public final String displayName;
    public final String[] description;

    IslandFlag(boolean defaultValue, String permission, Material icon,
               String displayName, String... description) {
        this.defaultValue = defaultValue;
        this.permission = permission;
        this.icon = icon;
        this.displayName = displayName;
        this.description = description;
    }

    /** Stable storage key (lowercase enum name). */
    public String storageKey() { return name().toLowerCase(); }

    public static IslandFlag byKey(String storageKey) {
        if (storageKey == null) return null;
        try { return valueOf(storageKey.toUpperCase()); }
        catch (IllegalArgumentException ex) { return null; }
    }
}
