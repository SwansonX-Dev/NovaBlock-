package com.nova.novablock.gui;

import com.nova.novablock.NovaBlock;
import com.nova.novablock.quest.Quest;
import com.nova.novablock.util.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;

public class QuestGui extends ChestGui {

    /** Centre three slots of a 3-row chest, one per daily quest. */
    private static final int[] SLOTS = {11, 13, 15};

    private final NovaBlock plugin;

    public QuestGui(NovaBlock plugin) {
        super("<yellow><bold>Daily Quests", 3);
        this.plugin = plugin;
    }

    @Override
    protected void build(Player p) {
        List<Quest> quests = plugin.quests().todayQuests();

        set(4, ItemBuilder.of(Material.CLOCK)
                .name("<yellow>Daily Quests")
                .lore("<gray>A fresh set of " + quests.size() + " quests every day.",
                        "<gray>Rewards are paid to your island bank.",
                        "<dark_gray>Resets at midnight (server time).")
                .build(), null);

        for (int i = 0; i < SLOTS.length; i++) {
            if (i >= quests.size()) {
                set(SLOTS[i], ItemBuilder.of(Material.GRAY_STAINED_GLASS_PANE).name(" ").build(), null);
                continue;
            }
            Quest q = quests.get(i);
            int prog = plugin.quests().progressOf(p, q);
            boolean done = prog >= q.requiredAmount();
            Material icon = q.icon() == null ? Material.PAPER : q.icon();

            ItemBuilder ib = ItemBuilder.of(done ? Material.LIME_DYE : icon)
                    .name((done ? "<green>" : "<yellow>") + q.displayName())
                    .lore(q.category().label,
                            " ",
                            "<gray>" + q.description(),
                            " ",
                            done ? "<green>✔ Complete!"
                                    : "<aqua>Progress: <white>" + prog + " <gray>/ <white>" + q.requiredAmount(),
                            "<gold>Reward: <yellow>" + q.coinReward() + " coins <gray>+ <aqua>" + q.xpReward() + " XP");
            if (done) ib.glow();
            set(SLOTS[i], ib.build(), null);
        }

        set(18, ItemBuilder.of(Material.ARROW).name("<gray>Back").build(),
                e -> new MainMenuGui(plugin).open(p));
        fill(Material.BLACK_STAINED_GLASS_PANE, " ");
    }
}
