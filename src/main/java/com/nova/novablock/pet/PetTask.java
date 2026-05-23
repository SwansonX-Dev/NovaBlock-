package com.nova.novablock.pet;

import org.bukkit.Material;

public enum PetTask {
    FOLLOW("Follow", Material.LEAD, "Stay near the owner. No active behavior."),
    COMBAT("Combat", Material.IRON_SWORD, "Attack nearby hostile mobs."),
    MINE_ASSIST("Mine Assist", Material.IRON_PICKAXE, "Pick up dropped items and bring them to the owner."),
    SCOUT("Scout", Material.SPYGLASS, "Ping rare blocks and loot rooms nearby."),
    SUPPORT("Support", Material.GOLDEN_APPLE, "Regenerate owner and remove negative effects."),
    STORAGE("Storage", Material.CHEST, "Hold a 27-slot inventory accessible via right-click."),
    MOUNT("Mount", Material.SADDLE, "Mountable. Sneak + jump near it to mount."),
    REST("Resting", Material.RED_BED, "Pet is dismissed but unlocked for re-summoning.");

    public final String displayName;
    public final Material icon;
    public final String description;

    PetTask(String name, Material icon, String desc) {
        this.displayName = name;
        this.icon = icon;
        this.description = desc;
    }
}
