package com.nova.novablock.minion;

import org.bukkit.Color;
import org.bukkit.Material;

import java.util.Locale;

public enum MinionSkin {
    DEFAULT("Default", null, "<gold>", null),
    ROYAL("Royal", Material.GOLD_BLOCK, "<yellow>", Color.fromRGB(255, 199, 44)),
    EMERALD("Emerald", Material.EMERALD_BLOCK, "<green>", Color.fromRGB(80, 220, 110)),
    AMETHYST("Amethyst", Material.AMETHYST_BLOCK, "<light_purple>", Color.fromRGB(176, 108, 255)),
    FROST("Frost", Material.PACKED_ICE, "<aqua>", Color.fromRGB(92, 220, 255)),
    NETHER("Nether", Material.NETHERITE_BLOCK, "<red>", Color.fromRGB(230, 62, 62)),
    VOID("Void", Material.SCULK_CATALYST, "<dark_purple>", Color.fromRGB(116, 54, 180));

    private final String displayName;
    private final Material overrideMaterial;
    private final String nameColor;
    private final Color glowColor;

    MinionSkin(String displayName, Material overrideMaterial, String nameColor, Color glowColor) {
        this.displayName = displayName;
        this.overrideMaterial = overrideMaterial;
        this.nameColor = nameColor;
        this.glowColor = glowColor;
    }

    public String id() { return name().toLowerCase(Locale.ROOT); }
    public String displayName() { return displayName; }
    public String nameColor() { return nameColor; }
    public Color glowColor() { return glowColor; }
    public Material displayMaterial(MinionType type) { return overrideMaterial == null ? type.displayMaterial() : overrideMaterial; }

    public static MinionSkin byId(String id) {
        if (id == null) return DEFAULT;
        for (MinionSkin skin : values()) if (skin.id().equalsIgnoreCase(id) || skin.name().equalsIgnoreCase(id)) return skin;
        return DEFAULT;
    }
}
