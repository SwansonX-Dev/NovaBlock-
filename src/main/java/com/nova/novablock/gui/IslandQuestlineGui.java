package com.nova.novablock.gui;

import com.nova.novablock.NovaBlock;
import com.nova.novablock.island.Island;
import com.nova.novablock.questline.IslandQuestStage;
import com.nova.novablock.util.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.entity.Player;

public class IslandQuestlineGui extends ChestGui {

    private final NovaBlock plugin;

    public IslandQuestlineGui(NovaBlock plugin) {
        super("<gold><bold>Island Questline", 3);
        this.plugin = plugin;
    }

    @Override
    protected void build(Player p) {
        Island island = plugin.islands().ofPlayer(p);
        if (island == null) {
            set(13, ItemBuilder.of(Material.BARRIER)
                    .name("<red>No island")
                    .lore("<gray>Create or join an island to start the questline.").build(), null);
            set(18, ItemBuilder.of(Material.ARROW).name("<gray>Back").build(),
                    e -> new MainMenuGui(plugin).open(p));
            fill(Material.BLACK_STAINED_GLASS_PANE, " ");
            return;
        }

        IslandQuestStage stage = plugin.islandQuestline().stageOf(island);
        int progress = plugin.islandQuestline().progressOf(island);
        int interval = plugin.islandQuestline().milestoneInterval();
        int nextMilestone = ((stage.stage() / interval) + 1) * interval;
        int toMilestone = nextMilestone - stage.stage();

        // Current stage objective.
        set(13, ItemBuilder.of(stage.milestone() ? Material.NETHER_STAR : stage.objective().icon)
                .name((stage.milestone() ? "<gold><bold>★ Stage " + stage.stage() + " ★" : "<gold>Stage " + stage.stage())
                        + " <gray>— <white>" + stage.describe())
                .lore(" ",
                        "<aqua>Progress: <white>" + progress + " <gray>/ <white>" + stage.required(),
                        bar(progress, stage.required()),
                        " ",
                        "<gold>Reward: <yellow>" + stage.rewardCoins() + " coins <gray>→ island bank",
                        stage.milestone() ? "<light_purple>✦ Milestone bonus included!" : "<dark_gray>Shared by all island members.")
                .glow().build(), null);

        // Reward summary.
        set(11, ItemBuilder.of(Material.GOLD_INGOT)
                .name("<yellow>Stage Reward")
                .lore("<gray>Coins are paid into the <white>island bank<gray>,",
                        "<gray>spendable on upgrades by the owner & co-owners.",
                        " ",
                        "<gray>Rewards grow every stage — it never ends.").build(), null);

        // Next milestone.
        set(15, ItemBuilder.of(Material.NETHER_STAR)
                .name("<light_purple>Next Milestone")
                .lore("<gray>Milestone at stage <white>" + nextMilestone,
                        "<gray>(<white>" + toMilestone + "<gray> stage" + (toMilestone == 1 ? "" : "s") + " to go)",
                        " ",
                        "<gray>Milestones pay a big bank bonus",
                        "<gray>and trigger special rewards.").build(), null);

        set(18, ItemBuilder.of(Material.ARROW).name("<gray>Back").build(),
                e -> new MainMenuGui(plugin).open(p));
        fill(Material.BLACK_STAINED_GLASS_PANE, " ");
    }

    /** A 20-segment progress bar. */
    private static String bar(int progress, int required) {
        int segments = 20;
        int filled = required <= 0 ? segments : (int) Math.round((double) progress / required * segments);
        filled = Math.max(0, Math.min(segments, filled));
        StringBuilder sb = new StringBuilder("<dark_gray>[");
        sb.append("<green>");
        for (int i = 0; i < segments; i++) {
            if (i == filled) sb.append("<gray>");
            sb.append('|');
        }
        sb.append("<dark_gray>]");
        return sb.toString();
    }
}
