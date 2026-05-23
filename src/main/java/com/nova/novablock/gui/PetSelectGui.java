package com.nova.novablock.gui;

import com.nova.novablock.NovaBlock;
import com.nova.novablock.pet.Pet;
import com.nova.novablock.pet.PetType;
import com.nova.novablock.progression.PlayerProgression;
import com.nova.novablock.util.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Shows only the pets the player has unlocked. Locked pets live in
 * the separate PetStoreGui (Pet Store).
 */
public class PetSelectGui extends ChestGui {

    private final NovaBlock plugin;

    public PetSelectGui(NovaBlock plugin) {
        super("<gold><bold>Your Pets", 4);
        this.plugin = plugin;
    }

    @Override
    protected void build(Player p) {
        PlayerProgression prog = plugin.progression().get(p);
        Pet active = plugin.pets().getActive(p);
        int slot = 10;
        boolean anyOwned = false;
        for (PetType type : PetType.values()) {
            int level = prog.getPetLevel(type.id);
            if (level <= 0) continue;
            anyOwned = true;
            List<String> lore = new ArrayList<>();
            lore.add("<gray>" + type.description);
            lore.add(" ");
            lore.add("<dark_aqua>Passive: <gray>" + type.passiveText);
            lore.add("<gold>Active: <gray>" + type.activeText);
            lore.add(" ");
            lore.add("<aqua>Pet Lv <white>" + level);
            lore.add(active != null && active.type() == type ? "<green>Currently summoned" : "<gray>Click to summon");
            ItemBuilder ib = ItemBuilder.of(type.icon).name("<gold>" + type.displayName).lore(lore);
            if (active != null && active.type() == type) ib.glow();
            final PetType pt = type;
            set(slot++, ib.build(), e -> { plugin.pets().summon(p, pt); open(p); });
            if (slot == 17 || slot == 26) slot++; // skip border columns
        }
        if (!anyOwned) {
            set(13, ItemBuilder.of(Material.BARRIER)
                    .name("<red>No pets yet")
                    .lore("<gray>Visit the Pet Store to unlock one.").build(), null);
        }
        set(29, ItemBuilder.of(Material.EMERALD)
                        .name("<green>Pet Store")
                        .lore("<gray>Browse and buy more pets.").build(),
                e -> new PetStoreGui(plugin).open(p));
        set(31, ItemBuilder.of(Material.BARRIER).name("<red>Dismiss Active Pet").build(),
                e -> { plugin.pets().despawn(p); open(p); });
        set(27, ItemBuilder.of(Material.ARROW).name("<gray>Back").build(),
                e -> new MainMenuGui(plugin).open(p));
        fill(Material.BLACK_STAINED_GLASS_PANE, " ");
    }
}
