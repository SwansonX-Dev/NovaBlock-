package com.nova.novablock.gui;

import com.nova.novablock.NovaBlock;
import com.nova.novablock.progression.Perk;
import com.nova.novablock.progression.PlayerProgression;
import com.nova.novablock.progression.SkillType;
import com.nova.novablock.util.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class SkillsGui extends ChestGui {

    private final NovaBlock plugin;

    public SkillsGui(NovaBlock plugin) {
        super("<light_purple><bold>Skills", 6);
        this.plugin = plugin;
    }

    @Override
    protected void build(Player p) {
        PlayerProgression prog = plugin.progression().get(p);
        SkillType[] types = SkillType.values();
        for (int row = 0; row < 4; row++) {
            SkillType type = types[row];
            int level = prog.getLevel(type);
            long xp = prog.getXp(type);
            long need = PlayerProgression.xpForLevel(level);
            set(row * 9, ItemBuilder.of(type.icon())
                            .name("<" + type.color() + ">" + type.displayName() + " <gray>Lv <white>" + level)
                            .lore("<gray>XP: <white>" + xp + "/" + need,
                                    "<gray>Earn " + type.displayName() + " XP to level up.",
                                    "<gray>Next perk: <white>" + nextPerkLine(prog, type, level))
                            .glow().build(), null);
            int col = 1;
            for (Perk perk : Perk.values()) {
                if (perk.skill != type) continue;
                boolean unlocked = Perk.hasPerk(prog, perk);
                List<String> lore = new ArrayList<>();
                lore.add("<gray>" + perk.description);
                lore.add(unlocked ? "<green>✔ Unlocked" : "<red>✘ Requires Lv " + perk.requiredLevel);
                set(row * 9 + col, ItemBuilder.of(unlocked ? Material.LIME_DYE : Material.GRAY_DYE)
                        .name((unlocked ? "<green>" : "<dark_gray>") + perk.name)
                        .lore(lore).build(), null);
                col++;
                if (col >= 9) break;
            }
        }
        set(8, ItemBuilder.of(Material.WRITTEN_BOOK)
                        .name("<aqua>How skills work")
                        .lore("<gray>Doing activities earns XP in four skills:",
                                "<gray>• <green>Mining <gray>— breaking your OneBlock",
                                "<gray>• <red>Combat <gray>— fighting bosses",
                                "<gray>• <#9C66FF>Magic <gray>— prophecy & rifts",
                                "<gray>• <#FFD24D>Luck <gray>— rare finds",
                                " ",
                                "<gray>Enough XP raises a skill's <white>level<gray>, and",
                                "<gray>levels <yellow>5 / 10 / 20 / 30 <gray>each unlock a",
                                "<gray>permanent <yellow>perk <gray>(shown on each row).",
                                " ",
                                "<dark_gray>Island upgrades like XP Boost speed this up.")
                        .glow().build(), null);

        // back to main
        set(45, ItemBuilder.of(Material.ARROW).name("<gray>Back").build(),
                e -> new MainMenuGui(plugin).open(p));
        fill(Material.BLACK_STAINED_GLASS_PANE, " ");
    }

    /** "Berserker at Lv 5 (4 to go)" for the next locked perk, or "All unlocked". */
    private String nextPerkLine(PlayerProgression prog, SkillType type, int level) {
        Perk next = null;
        for (Perk perk : Perk.values()) {
            if (perk.skill != type || Perk.hasPerk(prog, perk)) continue;
            if (next == null || perk.requiredLevel < next.requiredLevel) next = perk;
        }
        if (next == null) return "All unlocked";
        int toGo = next.requiredLevel - level;
        return next.name + " at Lv " + next.requiredLevel + " <dark_gray>(" + toGo + " to go)";
    }
}
