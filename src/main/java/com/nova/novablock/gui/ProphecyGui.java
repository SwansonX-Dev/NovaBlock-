package com.nova.novablock.gui;

import com.nova.novablock.NovaBlock;
import com.nova.novablock.island.Island;
import com.nova.novablock.util.ItemBuilder;
import com.nova.novablock.util.Msg;
import org.bukkit.Material;
import org.bukkit.entity.Player;

public class ProphecyGui extends ChestGui {

    private final NovaBlock plugin;

    public ProphecyGui(NovaBlock plugin) {
        super("<aqua><bold>Prophecy", 3);
        this.plugin = plugin;
    }

    @Override
    protected void build(Player p) {
        Island island = plugin.islands().ofPlayer(p);
        if (island == null) return;
        plugin.prophecies().ensureQueue(island);
        int picked = plugin.prophecies().pickSlot(p);
        int[] slots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21};
        for (int i = 0; i < 10; i++) {
            Material mat = plugin.prophecies().upcoming(island, i);
            boolean rare = plugin.prophecies().isRare(mat);
            int idx = i;
            String name = (rare ? "<gold>★ " : "<gray>") + (i + 1) + ". " + prettyName(mat);
            // Some phase blocks (SWEET_BERRY_BUSH, TALL_GRASS, etc.) are block-only
            // Materials with no ItemStack form; substitute a visually-equivalent
            // item for the icon while keeping the real Material for naming/locking.
            ItemBuilder ib = ItemBuilder.of(displayItem(mat)).name(name);
            if (rare) ib.lore("<yellow>Tap to lock as your prophecy.", "<gray>Bonus coins when reached.");
            else ib.lore("<dark_gray>Not a rare block.");
            if (picked == i) ib.lore("<green>(locked in)").glow();
            set(slots[i], ib.build(), e -> {
                if (plugin.prophecies().lockIn(p, island, idx)) {
                    Msg.send(p, "<aqua>Prophecy locked: <yellow>" + prettyName(mat));
                    open(p);
                } else {
                    Msg.actionBar(p, "<red>Only rare blocks can be locked.");
                }
            });
        }
        set(22, ItemBuilder.of(Material.BARRIER).name("<red>Clear").build(),
                e -> { plugin.prophecies().clear(p); open(p); });
        fill(Material.BLACK_STAINED_GLASS_PANE, " ");
    }

    private String prettyName(Material m) {
        StringBuilder out = new StringBuilder();
        for (String word : m.name().toLowerCase().split("_")) {
            if (out.length() > 0) out.append(' ');
            out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return out.toString();
    }

    /**
     * Map a (possibly block-only) Material to something we can safely put in an
     * {@link org.bukkit.inventory.ItemStack}. {@link Material#isItem()} catches
     * anything not enumerated below; we fall back to PAPER as a last resort.
     */
    private static Material displayItem(Material m) {
        if (m.isItem()) return m;
        return switch (m) {
            case SWEET_BERRY_BUSH -> Material.SWEET_BERRIES;
            case TALL_GRASS -> Material.SHORT_GRASS;
            case TALL_SEAGRASS -> Material.SEAGRASS;
            case LARGE_FERN -> Material.FERN;
            case PISTON_HEAD, MOVING_PISTON -> Material.PISTON;
            case REDSTONE_WIRE -> Material.REDSTONE;
            case TRIPWIRE -> Material.STRING;
            case POTTED_AZALEA_BUSH -> Material.AZALEA;
            default -> Material.PAPER;
        };
    }
}
