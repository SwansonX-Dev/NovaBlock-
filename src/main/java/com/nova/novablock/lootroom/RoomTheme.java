package com.nova.novablock.lootroom;

import org.bukkit.block.Biome;
import org.bukkit.entity.EntityType;

import java.util.List;

/**
 * Dimension theming applied to the three reusable loot rooms. Each room class
 * keeps its own per-theme palette but consults {@code biome()} for the instance
 * world and {@code mobPool()} for arena waves so a Nether loot room doesn't
 * render in Plains biome with overworld zombies.
 */
public record RoomTheme(String suffix, String displayPrefix, Biome biome, List<EntityType> mobPool) {

    public static final RoomTheme OVERWORLD = new RoomTheme(
            "overworld",
            "",
            Biome.PLAINS,
            List.of(EntityType.ZOMBIE, EntityType.SKELETON, EntityType.SPIDER, EntityType.HUSK)
    );

    public static final RoomTheme NETHER = new RoomTheme(
            "nether",
            "Nether ",
            Biome.CRIMSON_FOREST,
            List.of(EntityType.PIGLIN, EntityType.WITHER_SKELETON, EntityType.BLAZE, EntityType.MAGMA_CUBE)
    );
}
