package com.nova.novablock.gui;

import com.nova.novablock.NovaBlock;
import com.nova.novablock.pet.PetType;
import com.nova.novablock.progression.PlayerProgression;
import com.nova.novablock.util.ItemBuilder;
import com.nova.novablock.util.Msg;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Pet purchasing GUI. Lists every non-starter pet with its cost.
 * Owned pets are marked but still shown so the player sees the whole roster.
 */
public class PetStoreGui extends ChestGui {

    private final NovaBlock plugin;

    public PetStoreGui(NovaBlock plugin) {
        super("<green><bold>Pet Store", 4);
        this.plugin = plugin;
    }

    @Override
    protected void build(Player p) {
        PlayerProgression prog = plugin.progression().get(p);
        var island = plugin.islands().ofPlayer(p);
        long balance = plugin.economy().balance(p);

        int[] slots = {10, 11, 12, 13, 14, 15, 16};
        PetType[] all = PetType.values();
        int i = 0;
        for (PetType type : all) {
            if (i >= slots.length) break;
            int level = prog.getPetLevel(type.id);
            boolean owned = level > 0;
            long cost = type.storeCost();
            List<String> lore = new ArrayList<>();
            lore.add("<gray>" + type.description);
            lore.add(" ");
            lore.add("<dark_aqua>Passive: <gray>" + type.passiveText);
            lore.add("<gold>Active: <gray>" + type.activeText);
            lore.add(" ");
            if (owned) lore.add("<green>✔ Owned");
            else if (cost <= 0) lore.add("<aqua>Free starter pet");
            else lore.add("<yellow>" + String.format(Locale.US, "%,d", cost) + " coins");
            ItemBuilder ib = ItemBuilder.of(type.icon).name((owned ? "<green>" : "<gold>") + type.displayName).lore(lore);
            if (owned) ib.glow();
            set(slots[i++], ib.build(), e -> {
                if (owned) {
                    plugin.pets().summon(p, type);
                    p.closeInventory();
                    return;
                }
                if (cost > 0 && !plugin.economy().spend(p, cost)) {
                    Msg.actionBar(p, "<red>Need " + String.format(Locale.US, "%,d", cost) + " coins.");
                    return;
                }
                prog.unlockPet(type.id);
                plugin.progression().save(p.getUniqueId());
                Msg.title(p, "<gold>★ " + type.displayName, "<gray>Pet unlocked!");
                p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.4f);
                plugin.pets().summon(p, type);
                open(p);
            });
        }

        set(31, ItemBuilder.of(Material.SUNFLOWER)
                .name("<gold>Balance")
                .lore("<yellow>" + String.format(Locale.US, "%,d", balance) + " coins").glow().build(), null);

        set(27, ItemBuilder.of(Material.ARROW).name("<gray>Back").build(),
                e -> new PetSelectGui(plugin).open(p));
        fill(Material.BLACK_STAINED_GLASS_PANE, " ");
    }
}
