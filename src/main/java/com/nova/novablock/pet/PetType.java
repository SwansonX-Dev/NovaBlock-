package com.nova.novablock.pet;

import org.bukkit.Material;
import org.bukkit.entity.EntityType;

public enum PetType {
    BLAZER("blazer", "Blazer", EntityType.BLAZE, Material.BLAZE_POWDER,
            "Combat-focused. Lobs fire at hostile mobs near its owner.",
            PetTask.COMBAT),
    AXOLITE("axolite", "Axolite", EntityType.AXOLOTL, Material.AXOLOTL_BUCKET,
            "Healing pet. Regenerates owner over time.",
            PetTask.SUPPORT),
    DELVER("delver", "Delver", EntityType.ALLAY, Material.AMETHYST_SHARD,
            "Mining helper. Auto-collects drops within 8 blocks.",
            PetTask.MINE_ASSIST),
    SCOUT_FOX("scout_fox", "Scout Fox", EntityType.FOX, Material.SWEET_BERRIES,
            "Scout pet. Marks nearby rare blocks and loot rooms.",
            PetTask.SCOUT),
    GUARDIAN_WOLF("guardian_wolf", "Guardian Wolf", EntityType.WOLF, Material.BONE,
            "Loyal defender. Attacks anything that attacks you first.",
            PetTask.COMBAT),
    CHEST_PIG("chest_pig", "Pack Pig", EntityType.PIG, Material.CARROT_ON_A_STICK,
            "Carries an extra storage chest. Right-click to open.",
            PetTask.STORAGE),
    GHASTLING("ghastling", "Ghastling", EntityType.GHAST, Material.GHAST_TEAR,
            "Floating companion. Sneak-tap to launch yourself skyward with slow-falling.",
            PetTask.COMBAT);

    public final String id;
    public final String displayName;
    public final EntityType entityType;
    public final Material icon;
    public final String description;
    public final PetTask defaultTask;

    PetType(String id, String name, EntityType type, Material icon, String desc, PetTask task) {
        this.id = id;
        this.displayName = name;
        this.entityType = type;
        this.icon = icon;
        this.description = desc;
        this.defaultTask = task;
    }

    public static PetType byId(String id) {
        for (PetType t : values()) if (t.id.equalsIgnoreCase(id)) return t;
        return null;
    }
}
