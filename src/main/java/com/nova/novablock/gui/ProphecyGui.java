package com.nova.novablock.gui;

import com.nova.novablock.NovaBlock;
import com.nova.novablock.island.Island;
import com.nova.novablock.prophecy.ProphecyManager;
import com.nova.novablock.util.ItemBuilder;
import com.nova.novablock.util.Msg;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.Set;

public class ProphecyGui extends ChestGui {

    /** Top two rows reserved for queue items (0-16). Bottom row holds the action bar. */
    private static final int[] SLOTS_10 = {9, 10, 11, 12, 13, 14, 15, 16, 17, 18};
    private static final int[] SLOTS_12 = {9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20};

    private final NovaBlock plugin;

    public ProphecyGui(NovaBlock plugin) {
        super("<aqua><bold>Prophecy", 4);
        this.plugin = plugin;
    }

    @Override
    protected void build(Player p) {
        Island island = plugin.islands().ofPlayer(p);
        if (island == null) return;
        plugin.prophecies().ensureQueue(island);
        Set<Integer> picks = plugin.prophecies().picks(p);
        int visible = plugin.prophecies().visibleCount(p);
        int[] slots = visible == 12 ? SLOTS_12 : SLOTS_10;
        long bonus = 600L + (long) island.data().getPhaseIndex() * 150L;

        for (int i = 0; i < slots.length; i++) {
            Material mat = plugin.prophecies().upcoming(island, i);
            boolean rare = plugin.prophecies().isRare(mat);
            boolean locked = picks.contains(i);
            int idx = i;
            String name = (rare ? "<gold>★ " : "<gray>") + (i + 1) + ". " + prettyName(mat);
            ItemBuilder ib = ItemBuilder.of(displayItem(mat)).name(name);
            if (locked) ib.lore("<green>✔ Locked. <gray>Mine it to earn <gold>+" + bonus + " coins<gray>.",
                    "<dark_gray>Tap to unlock.").glow();
            else if (rare) ib.lore("<yellow>Tap to lock this block as your prophecy.",
                    "<gray>When you mine it you'll earn <gold>+" + bonus + " coins<gray>.");
            else ib.lore("<dark_gray>Only rare blocks (★) can be locked.");
            set(slots[i], ib.build(), e -> handleClick(p, island, idx));
        }

        // Action bar — row 4 (slots 27-35) so it never overlaps queue slots.
        set(27, ItemBuilder.of(Material.ARROW)
                        .name("<gray>← Back to menu").build(),
                e -> new MainMenuGui(plugin).open(p));

        set(35, ItemBuilder.of(Material.WRITTEN_BOOK)
                        .name("<aqua>How prophecy works")
                        .lore("<gray>These are the next blocks your OneBlock",
                                "<gray>will turn into as you mine.",
                                " ",
                                "<yellow>★ <gray>marks a rare block. <yellow>Tap one to",
                                "<gray>lock it as your prophecy.",
                                " ",
                                "<gray>Mine a locked block and your island",
                                "<gray>earns <gold>+" + bonus + " coins<gray> (plus Magic XP).",
                                " ",
                                "<dark_gray>The queue scrolls up as you mine.").glow().build(), null);

        int maxPicks = plugin.prophecies().maxPicks(p);
        set(29, ItemBuilder.of(Material.PAPER)
                        .name("<gray>Picks: <white>" + picks.size() + "/" + maxPicks)
                        .lore("<gray>How many blocks you can lock at once.",
                                "<dark_gray>PROPHET (Mining 30) and the Prophecy Slots",
                                "<dark_gray>island upgrade grant more picks.").build(), null);

        set(31, ItemBuilder.of(Material.BARRIER)
                        .name("<red>Clear all picks")
                        .lore("<gray>Removes every locked prophecy.").build(),
                e -> { plugin.prophecies().clear(p); open(p); });

        if (plugin.prophecies().canReroll(p)) {
            set(33, ItemBuilder.of(Material.CLOCK)
                            .name("<#9C66FF>Timeshift Reroll")
                            .lore("<gray>Replace the entire queue with a fresh roll.",
                                    "<dark_gray>Once per day (Magic 30 perk).").glow().build(),
                    e -> {
                        if (plugin.prophecies().reroll(p, island)) {
                            Msg.send(p, com.nova.novablock.util.Messages.of(
                                    "prophecy-reroll-done", "<#9C66FF>The future shifts. New blocks await."));
                            open(p);
                        } else {
                            Msg.actionBar(p, com.nova.novablock.util.Messages.of(
                                    "prophecy-reroll-spent", "<gray>You've already rerolled today."));
                        }
                    });
        }

        fill(Material.BLACK_STAINED_GLASS_PANE, " ");
    }

    private void handleClick(Player p, Island island, int slot) {
        ProphecyManager.LockResult result = plugin.prophecies().toggleLock(p, island, slot);
        switch (result) {
            case LOCKED -> {
                Msg.send(p, com.nova.novablock.util.Messages.format(
                        "prophecy-locked",
                        "<aqua>Prophecy locked: <yellow>{material}",
                        "material", prettyName(plugin.prophecies().upcoming(island, slot))));
                open(p);
            }
            case UNLOCKED -> {
                Msg.actionBar(p, com.nova.novablock.util.Messages.of("prophecy-released", "<gray>Prophecy released."));
                open(p);
            }
            case NOT_RARE -> Msg.actionBar(p, com.nova.novablock.util.Messages.of(
                    "prophecy-not-rare", "<red>Only rare blocks can be locked."));
            case NOT_MEMBER -> Msg.actionBar(p, "<red>Not your island.");
            case OUT_OF_RANGE -> {}
        }
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
