package com.nova.novablock.pet;

import org.bukkit.Material;
import org.bukkit.entity.EntityType;

/**
 * Each pet has a unique passive (always on while summoned) and a unique active
 * (triggered by sneak-right-clicking the pet). The PetTask field is the loop
 * the manager runs every tick.
 */
public enum PetType {
    DELVER("delver", "Delver", EntityType.ALLAY, Material.AMETHYST_SHARD,
            "Mining helper — auto-collects drops and pings rare ores.",
            "Passive: pulls dropped items to you within 10 blocks.",
            "Active: marks the closest rare ore with a beam for 8s.",
            PetTask.MINE_ASSIST, true),

    BLAZER("blazer", "Blazer", EntityType.BLAZE, Material.BLAZE_POWDER,
            "Combat companion — sets your foes on fire.",
            "Passive: lobs fireballs at hostiles attacking you.",
            "Active: 8s of Fire Resistance + ignites mobs within 6 blocks.",
            PetTask.COMBAT, false),

    AXOLITE("axolite", "Axolite", EntityType.AXOLOTL, Material.AXOLOTL_BUCKET,
            "Support pet — keeps you healthy.",
            "Passive: Regeneration I + cleanses poison/wither every 4s.",
            "Active: Resistance II for 6s and full heal.",
            PetTask.SUPPORT, false),

    SCOUT_FOX("scout_fox", "Scout Fox", EntityType.FOX, Material.SWEET_BERRIES,
            "Scout — finds loot rooms and rare blocks.",
            "Passive: 25% more loot-room rolls; pings rare upcoming blocks.",
            "Active: reveal next 10 prophecy blocks via title.",
            PetTask.SCOUT, false),

    GUARDIAN_WOLF("guardian_wolf", "Guardian Wolf", EntityType.WOLF, Material.BONE,
            "Tank pet — soaks damage so you don't.",
            "Passive: 30% of damage taken by owner is redirected to wolf.",
            "Active: Howl — Strength I to owner, 6s of Glow on nearby enemies.",
            PetTask.COMBAT, false),

    CHEST_PIG("chest_pig", "Pack Pig", EntityType.PIG, Material.CARROT_ON_A_STICK,
            "Storage pet — carries an extra 27-slot inventory.",
            "Passive: never despawns; carries 27-slot shared chest.",
            "Active: opens the storage chest from anywhere.",
            PetTask.STORAGE, false),

    GHASTLING("ghastling", "Ghastling", EntityType.GHAST, Material.GHAST_TEAR,
            "Flight buddy — lifts you across gaps.",
            "Passive: Slow Falling permanently while it's nearby.",
            "Active: launch upward with Levitation II for 5s.",
            PetTask.SUPPORT, false);

    public final String id;
    public final String displayName;
    public final EntityType entityType;
    public final Material icon;
    public final String description;
    public final String passiveText;
    public final String activeText;
    public final PetTask defaultTask;
    /** True if this pet is the starter pet (granted free to every player). */
    public final boolean starter;

    PetType(String id, String name, EntityType type, Material icon, String desc,
            String passive, String active, PetTask task, boolean starter) {
        this.id = id;
        this.displayName = name;
        this.entityType = type;
        this.icon = icon;
        this.description = desc;
        this.passiveText = passive;
        this.activeText = active;
        this.defaultTask = task;
        this.starter = starter;
    }

    public static PetType byId(String id) {
        for (PetType t : values()) if (t.id.equalsIgnoreCase(id)) return t;
        return null;
    }

    /** Cost in coins to buy from the pet store. Starters are free. */
    public long storeCost() {
        return switch (this) {
            case DELVER -> 0;
            case BLAZER -> 3500L;
            case AXOLITE -> 5000L;
            case SCOUT_FOX -> 6000L;
            case GUARDIAN_WOLF -> 8000L;
            case CHEST_PIG -> 10_000L;
            case GHASTLING -> 15_000L;
        };
    }
}
