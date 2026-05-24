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
        int start = page * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, MATERIALS.size());
        for (int i = start; i < end; i++) {
            Material material = MATERIALS.get(i);
            String node = "novablock.companion.gather." + material.name().toLowerCase(Locale.ROOT);
            boolean allowed = p.hasPermission("novablock.companion.gather.*") || p.hasPermission(node);
            ItemBuilder item = ItemBuilder.of(material)
                    .name((allowed ? "<yellow>" : "<red>") + material.name().toLowerCase(Locale.ROOT))
                    .lore(allowed
                            ? new String[]{"<gray>Click to gather this material."}
                            : new String[]{"<dark_red>Missing permission.", "<dark_gray>" + node});
            set(i - start, item.build(), e -> {
                if (!allowed) return;
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
                        .name("<aqua>Page " + (page + 1) + "/" + pageCount())
                        .lore("<gray>Every item material is available here.",
                                "<gray>Access is controlled by permission.")
                        .build(),
                null);
        if (end < MATERIALS.size()) {
            set(53, ItemBuilder.of(Material.ARROW).name("<gray>Next").build(),
                    e -> new CompanionMaterialGui(plugin, page + 1).open(p));
        }
        set(48, ItemBuilder.of(Material.BARRIER).name("<gray>Back").build(),
                e -> new CompanionGui(plugin).open(p));

        fill(Material.GRAY_STAINED_GLASS_PANE, " ");
    }

    private int pageCount() {
        return Math.max(1, (int) Math.ceil(MATERIALS.size() / (double) PAGE_SIZE));
    }
}
