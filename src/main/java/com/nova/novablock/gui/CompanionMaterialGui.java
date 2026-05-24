package com.nova.novablock.gui;

import com.nova.novablock.NovaBlock;
import com.nova.novablock.util.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class CompanionMaterialGui extends ChestGui {

    private static final int PAGE_SIZE = 45;
    private static final List<Material> MATERIALS = Arrays.stream(Material.values())
            .filter(m -> m != Material.AIR)
            .filter(Material::isItem)
            .sorted(Comparator.comparing(Material::name))
            .toList();

    private final NovaBlock plugin;
    private final int page;

    public CompanionMaterialGui(NovaBlock plugin, int page) {
        super("<gradient:#4FC3F7:#7B61FF><bold>Companion Materials", 6);
        this.plugin = plugin;
        this.page = Math.max(0, page);
    }

    @Override
    protected void build(Player p) {
        List<Material> allowedMaterials = MATERIALS.stream()
                .filter(material -> plugin.companions().hasGatherPermission(p, material))
                .toList();

        if (allowedMaterials.isEmpty()) {
            set(22, ItemBuilder.of(Material.BARRIER)
                            .name("<red>No Materials Available")
                            .lore("<gray>You do not have permission to gather any materials.")
                            .build(),
                    null);
            set(49, ItemBuilder.of(Material.ARROW).name("<gray>Back").build(),
                    e -> new CompanionGui(plugin).open(p));
            fill(Material.GRAY_STAINED_GLASS_PANE, " ");
            return;
        }

        int start = page * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, allowedMaterials.size());
        for (int i = start; i < end; i++) {
            Material material = allowedMaterials.get(i);
            ItemBuilder item = ItemBuilder.of(material)
                    .name("<yellow>" + material.name().toLowerCase(Locale.ROOT))
                    .lore("<gray>Click to gather this material.");
            set(i - start, item.build(), e -> {
                if (plugin.companions().isActive(p)) {
                    plugin.companions().setMaterial(p, material);
                } else {
                    plugin.companions().summon(p, material, Sound.MUSIC_DISC_CAT);
                }
                new CompanionGui(plugin).open(p);
            });
        }

        if (page > 0) {
            set(45, ItemBuilder.of(Material.ARROW).name("<gray>Previous").build(),
                    e -> new CompanionMaterialGui(plugin, page - 1).open(p));
        }
        set(49, ItemBuilder.of(Material.COMPASS)
                        .name("<aqua>Page " + (page + 1) + "/" + pageCount(allowedMaterials))
                        .lore("<gray>Only materials you can gather are shown.")
                        .build(),
                null);
        if (end < allowedMaterials.size()) {
            set(53, ItemBuilder.of(Material.ARROW).name("<gray>Next").build(),
                    e -> new CompanionMaterialGui(plugin, page + 1).open(p));
        }
        set(48, ItemBuilder.of(Material.BARRIER).name("<gray>Back").build(),
                e -> new CompanionGui(plugin).open(p));

        fill(Material.GRAY_STAINED_GLASS_PANE, " ");
    }

    private int pageCount(List<Material> materials) {
        return Math.max(1, (int) Math.ceil(materials.size() / (double) PAGE_SIZE));
    }
}
