package com.nova.novablock.gui;

import com.nova.novablock.NovaBlock;
import com.nova.novablock.util.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.Locale;

public class CompanionGui extends ChestGui {

    private final NovaBlock plugin;

    public CompanionGui(NovaBlock plugin) {
        super("<gradient:#4FC3F7:#7B61FF><bold>Companion", 4);
        this.plugin = plugin;
    }

    @Override
    protected void build(Player p) {
        boolean active = plugin.companions().isActive(p);
        Material material = plugin.companions().material(p);
        org.bukkit.Sound music = plugin.companions().music(p);

        set(11, ItemBuilder.of(active ? Material.ALLAY_SPAWN_EGG : Material.GRAY_DYE)
                        .name(active ? "<aqua>Companion Active" : "<gray>Companion Inactive")
                        .lore(active
                                ? "<gray>Currently helping you gather and recover items."
                                : "<gray>Choose a material to summon your companion.")
                        .build(),
                null);

        set(13, ItemBuilder.of(material == null ? Material.CHEST : material)
                        .name("<yellow>Gather Material")
                        .lore(material == null
                                ? new String[]{"<gray>Select any Minecraft item material.", "<dark_gray>Permission checked per material."}
                                : new String[]{"<gray>Current: <white>" + material.name().toLowerCase(Locale.ROOT),
                                "<dark_gray>Permission checked per material."})
                        .build(),
                e -> new CompanionMaterialGui(plugin, 0).open(p));

        set(15, ItemBuilder.of(Material.MUSIC_DISC_CAT)
                        .name("<light_purple>Music Disc")
                        .lore(music == null
                                ? new String[]{"<gray>No music selected."}
                                : new String[]{"<gray>Current: <white>" + music.name().replace("MUSIC_DISC_", "").toLowerCase(Locale.ROOT)})
                        .build(),
                e -> new CompanionMusicGui(plugin).open(p));

        set(21, ItemBuilder.of(Material.HOPPER)
                        .name("<aqua>Item Recovery")
                        .lore("<gray>Picks up nearby ground items.",
                                "<gray>Recovers owned items falling into the void.",
                                "<dark_gray>Uses the same island build checks.")
                        .build(),
                null);

        set(23, ItemBuilder.of(Material.BARRIER)
                        .name("<red>Dismiss Companion")
                        .lore("<gray>Stops gathering, pickup, recovery, and music.")
                        .build(),
                e -> {
                    plugin.companions().stop(p);
                    open(p);
                });

        set(31, ItemBuilder.of(Material.ARROW).name("<gray>Back").build(),
                e -> new MainMenuGui(plugin).open(p));

        fill(Material.GRAY_STAINED_GLASS_PANE, " ");
    }
}
