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

        set(10, ItemBuilder.of(Material.BOOK)
                .name("<yellow>What's in it?")
                .lore("<gray>• 12 themed phases",
                        "<gray>• Boss fights & loot rooms",
                        "<gray>• Pets that fight, mine, and follow you",
                        "<gray>• Skills, prophecies, daily quests, shop")
                .build(), null);

        set(16, ItemBuilder.of(Material.PAPER)
                .name("<aqua>Tip: open the menu anytime")
                .lore("<gray>Run <yellow>/ob menu</yellow> or <yellow>/ob</yellow>",
                        "<gray>to see everything you've unlocked.")
                .build(), null);

        fill(Material.GRAY_STAINED_GLASS_PANE, " ");
    }
}
