package com.nova.novablock.gui;

import com.nova.novablock.NovaBlock;
import com.nova.novablock.island.Island;
import com.nova.novablock.util.ItemBuilder;
import com.nova.novablock.util.Msg;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

/**
 * Big-button onboarding menu for first-time players. Bedrock/mobile-friendly:
 * single oversized "Start" target, no chat input, no text fields.
 */
public class WelcomeGui extends ChestGui {

    private final NovaBlock plugin;

    public WelcomeGui(NovaBlock plugin) {
        super("<gradient:#7B61FF:#4FC3F7><bold>Welcome to NovaBlock", 3);
        this.plugin = plugin;
    }

    @Override
    protected void build(Player p) {
        set(13, ItemBuilder.of(Material.GRASS_BLOCK)
                .name("<green><bold>Create my OneBlock!")
                .lore("<gray>Spawns your own island in the sky.",
                        "<gray>Mine the block — it always comes back.",
                        " ",
                        "<yellow>Tap to start.")
                .glow().build(),
                e -> {
                    p.closeInventory();
                    if (plugin.islands().ofPlayer(p) != null) {
                        plugin.islands().ofPlayer(p).teleportHome(p);
                        return;
                    }
                    Island island = plugin.islands().create(p);
                    Msg.title(p, "<gradient:#7B61FF:#4FC3F7>NovaBlock", "<gray>Your island is ready");
                    p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
                    island.teleportHome(p);
                });

        Island island = plugin.islands().ofPlayer(p);
        long blocksBroken = island == null ? 0 : island.data().getBlocksBroken();
        int phaseIndex = island == null ? 0 : island.data().getPhaseIndex();
        int questTarget = plugin.quests().today() == null ? 0 : plugin.quests().today().requiredAmount();
        int questProgress = questTarget == 0 ? 0 : plugin.quests().progressOf(p);

        set(10, checklistItem("Break 100 blocks",
                "<gray>Just mine the OneBlock. <yellow>(" + Math.min(100, blocksBroken) + "/100<yellow>)",
                blocksBroken >= 100));

        set(11, checklistItem("Reach Phase 2",
                "<gray>Mine through the first phase to unlock new themes.",
                phaseIndex >= 1));

        set(12, checklistItem("Finish today's quest",
                questTarget == 0
                        ? "<gray>Open <yellow>/ob quest<gray> when ready."
                        : "<gray>Open <yellow>/ob quest<gray> — <yellow>" + questProgress + "/" + questTarget + "<gray>.",
                questTarget > 0 && questProgress >= questTarget));

        set(16, ItemBuilder.of(Material.PAPER)
                .name("<aqua>Tip: open the menu anytime")
                .lore("<gray>Run <yellow>/ob menu</yellow> or <yellow>/ob</yellow>",
                        "<gray>to see everything you've unlocked.",
                        "<gray>Reopen this checklist with <yellow>/novahelp</yellow>.")
                .build(), null);

        fill(Material.GRAY_STAINED_GLASS_PANE, " ");
    }

    private static org.bukkit.inventory.ItemStack checklistItem(String label, String detail, boolean done) {
        Material icon = done ? Material.LIME_DYE : Material.GRAY_DYE;
        String box = done ? "<green>☑" : "<gray>☐";
        String labelColor = done ? "<green>" : "<white>";
        return ItemBuilder.of(icon)
                .name(box + " " + labelColor + label)
                .lore(detail)
                .build();
    }
}
