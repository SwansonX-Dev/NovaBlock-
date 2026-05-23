package com.nova.novablock.gui;

import com.nova.novablock.NovaBlock;
import com.nova.novablock.pet.Pet;
import com.nova.novablock.pet.PetTask;
import com.nova.novablock.util.ItemBuilder;
import com.nova.novablock.util.Msg;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * Per-pet interaction menu opened by right-clicking the pet.
 * Bedrock-friendly: chest layout with big icon targets, no chat prompts.
 */
public class PetGui extends ChestGui {

    private final NovaBlock plugin;
    private final Pet pet;

    public PetGui(NovaBlock plugin, Pet pet) {
        super("<gold><bold>" + pet.type().displayName, 3);
        this.plugin = plugin;
        this.pet = pet;
    }

    @Override
    protected void build(Player p) {
        set(4, ItemBuilder.of(pet.type().icon)
                .name("<gold>" + pet.type().displayName + " <gray>Lv <white>" + pet.level())
                .lore("<gray>" + pet.type().description,
                        " ",
                        "<aqua>XP: <white>" + pet.xp() + "/" + Pet.xpForLevel(pet.level()),
                        "<aqua>Task: <white>" + pet.task().displayName)
                .glow().build(), null);

        PetTask[] tasks = PetTask.values();
        int[] slots = {9, 10, 11, 12, 13, 14, 15, 16, 17};
        for (int i = 0; i < tasks.length && i < slots.length; i++) {
            PetTask t = tasks[i];
            ItemBuilder ib = ItemBuilder.of(t.icon)
                    .name((pet.task() == t ? "<green>" : "<gray>") + t.displayName)
                    .lore("<gray>" + t.description);
            if (pet.task() == t) ib.glow();
            set(slots[i], ib.build(), e -> { plugin.pets().setTask(pet, t); open(p); });
        }

        set(22, ItemBuilder.of(Material.NAME_TAG)
                .name("<yellow>Rename")
                .lore("<gray>Drop a name tag in your hand onto the pet.").build(), null);

        set(24, ItemBuilder.of(Material.BARRIER)
                .name("<red>Dismiss")
                .lore("<gray>Send the pet to rest.").build(),
                e -> { plugin.pets().despawn(p); p.closeInventory(); Msg.actionBar(p, "<gray>Pet dismissed."); });

        fill(Material.BLACK_STAINED_GLASS_PANE, " ");
    }
}
