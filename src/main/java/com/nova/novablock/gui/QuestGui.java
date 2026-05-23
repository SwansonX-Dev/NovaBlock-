package com.nova.novablock.gui;

import com.nova.novablock.NovaBlock;
import com.nova.novablock.quest.Quest;
import com.nova.novablock.util.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.entity.Player;

public class QuestGui extends ChestGui {

    private final NovaBlock plugin;

    public QuestGui(NovaBlock plugin) {
        super("<yellow><bold>Daily Quest", 3);
        this.plugin = plugin;
    }

    @Override
    protected void build(Player p) {
        Quest q = plugin.quests().today();
        int prog = plugin.quests().progressOf(p);
        boolean done = prog >= q.requiredAmount();
        set(13, ItemBuilder.of(q.targetMaterial() == Material.AIR ? Material.PAPER : q.targetMaterial())
                .name("<yellow>" + q.displayName())
                .lore("<gray>" + q.description(),
                        " ",
                        (done ? "<green>✔ Complete!" : "<aqua>Progress: <white>" + prog + " / " + q.requiredAmount()),
                        "<gold>Reward: <yellow>" + q.coinReward() + " coins <gray>+ <aqua>" + q.xpReward() + " XP")
                .glow().build(), null);
        set(18, ItemBuilder.of(Material.ARROW).name("<gray>Back").build(),
                e -> new MainMenuGui(plugin).open(p));
        fill(Material.BLACK_STAINED_GLASS_PANE, " ");
    }
}
