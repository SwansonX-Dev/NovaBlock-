package com.nova.novablock.gui;

import com.nova.novablock.NovaBlock;
import com.nova.novablock.util.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Anti-auto-mine challenge: a grid of coloured wool where the player must click
 * the one GREEN wool to prove a human is at the keyboard. An auto-mine /
 * modded client that just holds left-click never interacts with a chest GUI, so
 * it fails by timeout. Clicking the wrong wool fails immediately; the green wool
 * (and its slot) is randomised every open so a slot can't be memorised/scripted.
 *
 * <p>Driven by {@link com.nova.novablock.antiafk.AntiAfkManager}: green-wool
 * clicks call {@code guiPass}, wrong clicks call {@code guiWrong}.
 */
public class AfkCheckGui extends ChestGui {

    private static final int ROWS = 3;
    private static final int SLOTS = ROWS * 9;
    private static final int DISTRACTORS = 8;

    // Wool colours used as distractors. LIME is excluded so "green" is unambiguous.
    private static final Material[] DISTRACTOR_WOOLS = {
            Material.WHITE_WOOL, Material.ORANGE_WOOL, Material.MAGENTA_WOOL, Material.LIGHT_BLUE_WOOL,
            Material.YELLOW_WOOL, Material.PINK_WOOL, Material.GRAY_WOOL, Material.LIGHT_GRAY_WOOL,
            Material.CYAN_WOOL, Material.PURPLE_WOOL, Material.BLUE_WOOL, Material.BROWN_WOOL,
            Material.RED_WOOL, Material.BLACK_WOOL
    };

    private final NovaBlock plugin;

    public AfkCheckGui(NovaBlock plugin) {
        super("<red><bold>Click the GREEN wool", ROWS);
        this.plugin = plugin;
    }

    @Override
    protected void build(Player p) {
        ThreadLocalRandom rng = ThreadLocalRandom.current();

        // Pick distinct slots: one for green, the rest for distractors.
        List<Integer> slots = new ArrayList<>(SLOTS);
        for (int i = 0; i < SLOTS; i++) slots.add(i);
        Collections.shuffle(slots, rng);

        int greenSlot = slots.get(0);
        set(greenSlot, ItemBuilder.of(Material.GREEN_WOOL)
                .name("<green><bold>GREEN wool")
                .lore("<gray>Click me to prove you're not auto-mining.")
                .glow().build(),
                e -> plugin.antiAfk().guiPass(p));

        // Distractors — random non-green wools the player must NOT click.
        List<Material> palette = new ArrayList<>(List.of(DISTRACTOR_WOOLS));
        Collections.shuffle(palette, rng);
        int count = Math.min(DISTRACTORS, palette.size());
        for (int i = 0; i < count; i++) {
            int slot = slots.get(1 + i);
            set(slot, ItemBuilder.of(palette.get(i))
                    .name("<gray>" + pretty(palette.get(i)))
                    .lore("<dark_gray>Not this one.").build(),
                    e -> plugin.antiAfk().guiWrong(p));
        }

        fill(Material.BLACK_STAINED_GLASS_PANE, " ");
    }

    private static String pretty(Material m) {
        StringBuilder out = new StringBuilder();
        for (String word : m.name().toLowerCase().replace("_wool", "").split("_")) {
            if (out.length() > 0) out.append(' ');
            out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return out.toString();
    }
}
