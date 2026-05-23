package com.nova.novablock.gui;

import com.nova.novablock.NovaBlock;
import com.nova.novablock.pet.Pet;
import com.nova.novablock.pet.PetType;
import com.nova.novablock.progression.PlayerProgression;
import com.nova.novablock.util.ItemBuilder;
import com.nova.novablock.util.Msg;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class PetSelectGui extends ChestGui {

    private final NovaBlock plugin;

    public PetSelectGui(NovaBlock plugin) {
        super("<gold><bold>Pets", 4);
        this.plugin = plugin;
    }

    @Override
    protected void build(Player p) {
        PlayerProgression prog = plugin.progression().get(p);
        Pet active = plugin.pets().getActive(p);
        PetType[] all = PetType.values();
        int[] slots = {10, 11, 12, 13, 14, 15, 16};
        for (int i = 0; i < all.length && i < slots.length; i++) {
            PetType type = all[i];
            int level = prog.getPetLevel(type.id);
            List<String> lore = new ArrayList<>();
            lore.add("<gray>" + type.description);
            lore.add(" ");
            lore.add(level == 0 ? "<red>Locked — find an egg or buy from shop" : "<aqua>Pet Lv <white>" + level);
            lore.add(active != null && active.type() == type ? "<green>Currently summoned" : "<gray>Click to summon");
            ItemBuilder ib = ItemBuilder.of(type.icon).name("<gold>" + type.displayName).lore(lore);
            if (active != null && active.type() == type) ib.glow();
            set(slots[i], ib.build(), e -> {
                if (level == 0) {
                    // Auto-unlock first 3 for free, others gate behind coin cost
                    var island = plugin.islands().ofPlayer(p);
                    long cost = freeUnlock(type) ? 0 : 5000L;
                    if (cost > 0 && !plugin.economy().spend(island, cost)) {
                        Msg.actionBar(p, "<red>Need " + cost + " coins to unlock.");
                        return;
                    }
                    prog.unlockPet(type.id);
                    Msg.send(p, "<gold>Unlocked: <yellow>" + type.displayName);
                }
                plugin.pets().summon(p, type);
                open(p);
            });
        }
        set(31, ItemBuilder.of(Material.BARRIER).name("<red>Dismiss Active Pet").build(),
                e -> { plugin.pets().despawn(p); open(p); });
        set(27, ItemBuilder.of(Material.ARROW).name("<gray>Back").build(),
                e -> new MainMenuGui(plugin).open(p));
        fill(Material.BLACK_STAINED_GLASS_PANE, " ");
    }

    private boolean freeUnlock(PetType type) {
        return type == PetType.BLAZER || type == PetType.AXOLITE || type == PetType.DELVER;
    }
}
