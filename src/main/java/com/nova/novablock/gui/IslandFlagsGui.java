package com.nova.novablock.gui;

import com.nova.novablock.NovaBlock;
import com.nova.novablock.island.Island;
import com.nova.novablock.island.IslandFlag;
import com.nova.novablock.util.ItemBuilder;
import com.nova.novablock.util.Msg;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Per-island toggle settings. Each flag is one icon; click toggles the value
 * if the clicking player has {@link IslandFlag#permission}. State indicated
 * by glow + the lore line at the top of the tooltip.
 *
 * <p>Two constructors: the no-arg one targets the opener's own island; the
 * explicit-island one is for admins editing someone else's island. In admin
 * mode every flag is toggleable regardless of per-flag perms — admins are
 * expected to have {@code novablock.admin}.
 */
public class IslandFlagsGui extends ChestGui {

    private final NovaBlock plugin;
    private final Island target;          // null = "viewer's own island, resolved on open"
    private final boolean adminOverride;

    /** Default — opens the viewer's own island, enforces per-flag perms. */
    public IslandFlagsGui(NovaBlock plugin) {
        super("<gradient:#9C27B0:#03A9F4><bold>Island Flags", 5);
        this.plugin = plugin;
        this.target = null;
        this.adminOverride = false;
    }

    /** Admin mode — opens the given island, viewer can toggle any flag. */
    public IslandFlagsGui(NovaBlock plugin, Island target) {
        super("<gradient:#FF1744:#FF8A65><bold>Island Flags (admin)", 5);
        this.plugin = plugin;
        this.target = target;
        this.adminOverride = true;
    }

    @Override
    protected void build(Player viewer) {
        Island island = (target != null) ? target : plugin.islands().ofPlayer(viewer);
        if (island == null) {
            set(13, ItemBuilder.of(Material.BARRIER).name("<red>No island").build(), null);
            return;
        }

        IslandFlag[] flags = IslandFlag.values();
        int[] slots = {10, 11, 12, 13, 14, 15, 16,
                       19, 20, 21, 22, 23, 24, 25,
                       28, 29, 30, 31, 32, 33, 34};
        for (int i = 0; i < flags.length && i < slots.length; i++) {
            IslandFlag flag = flags[i];
            boolean on = island.data().isFlag(flag);
            boolean allowed = adminOverride || viewer.hasPermission(flag.permission);

            List<String> lore = new ArrayList<>();
            lore.add(on ? "<green>● Enabled" : "<red>○ Disabled");
            lore.add("");
            for (String line : flag.description) lore.add("<gray>" + line);
            lore.add("");
            if (allowed) {
                lore.add("<yellow>Click to toggle.");
            } else {
                lore.add("<dark_red>You don't have permission to toggle this flag.");
                lore.add("<dark_gray>" + flag.permission);
            }

            ItemBuilder ib = ItemBuilder.of(flag.icon)
                    .name((on ? "<green>" : "<red>") + flag.displayName)
                    .lore(lore.toArray(new String[0]));
            if (on) ib.glow();

            set(slots[i], ib.build(), e -> {
                if (!allowed) {
                    Msg.actionBar(viewer, "<red>You don't have permission for that flag.");
                    return;
                }
                // Nightmare Mode is one-way (per the flag description). Only admins can disable it.
                if (flag == com.nova.novablock.island.IslandFlag.NIGHTMARE_MODE
                        && island.data().isFlag(flag) && !adminOverride) {
                    Msg.actionBar(viewer, "<red>Nightmare Mode cannot be disabled. Prestige to reset.");
                    return;
                }
                boolean next = !island.data().isFlag(flag);
                island.data().setFlag(flag, next);
                plugin.storage().saveIsland(island.data());
                viewer.playSound(viewer.getLocation(),
                        next ? Sound.UI_BUTTON_CLICK : Sound.BLOCK_LEVER_CLICK, 0.6f, next ? 1.4f : 0.8f);
                open(viewer);
            });
        }

        set(40, ItemBuilder.of(Material.ARROW).name("<gray>Back").build(),
                e -> { if (!adminOverride) new MainMenuGui(plugin).open(viewer); else viewer.closeInventory(); });

        fill(Material.GRAY_STAINED_GLASS_PANE, " ");
    }
}
