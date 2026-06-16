package com.nova.novablock.gui;

import com.nova.novablock.NovaBlock;
import com.nova.novablock.ability.ActiveAbility;
import com.nova.novablock.progression.Passives;
import com.nova.novablock.progression.PlayerProgression;
import com.nova.novablock.progression.SkillType;
import com.nova.novablock.util.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Overview of all skills; click a skill to view its perks, passive and ability. */
public class SkillsGui extends ChestGui {

    private final NovaBlock plugin;

    // 2 rows of 4 skills, centred.
    private static final int[] SLOTS = {10, 12, 14, 16, 28, 30, 32, 34};

    public SkillsGui(NovaBlock plugin) {
        super("<light_purple><bold>Skills", 6);
        this.plugin = plugin;
    }

    @Override
    protected void build(Player p) {
        PlayerProgression prog = plugin.progression().get(p);
        SkillType[] types = SkillType.values();
        for (int i = 0; i < types.length && i < SLOTS.length; i++) {
            SkillType type = types[i];
            set(SLOTS[i], skillIcon(prog, type), e -> new SkillDetailGui(plugin, type).open(p));
        }

        set(49, ItemBuilder.of(Material.WRITTEN_BOOK)
                .name("<aqua>How skills work")
                .lore("<gray>Doing activities earns XP across <white>8 skills<gray>.",
                        "<gray>Every level raises a <white>passive<gray> chance, and",
                        "<gray>milestones unlock permanent <yellow>perks<gray>.",
                        " ",
                        "<gray>Hold a skill tool and <yellow>right-click<gray> to ready",
                        "<gray>its <gold>active ability<gray>, then strike to unleash it.",
                        " ",
                        "<gray>Cap: <white>Lv " + PlayerProgression.maxLevel(),
                        "<dark_gray>Click a skill for details.")
                .glow().build(), null);

        set(48, ItemBuilder.of(Material.ARROW).name("<gray>Back").build(),
                e -> new MainMenuGui(plugin).open(p));
        fill(Material.BLACK_STAINED_GLASS_PANE, " ");
    }

    private ItemStack skillIcon(PlayerProgression prog, SkillType type) {
        int level = prog.getLevel(type);
        boolean maxed = prog.isMaxLevel(type);
        long xp = prog.getXp(type);
        long need = PlayerProgression.xpForLevel(level);
        double passive = Passives.chance(prog, type);
        ActiveAbility ability = ActiveAbility.forSkill(type);

        List<String> lore = new ArrayList<>();
        lore.add("<gray>Level <white>" + level + "<gray>/<white>" + PlayerProgression.maxLevel());
        lore.add(maxed ? "<gold>★ MAXED" : "<gray>XP: <white>" + xp + "<dark_gray>/" + need);
        lore.add(progressBar(maxed ? 1.0 : (need <= 0 ? 0 : (double) xp / need)));
        if (passive > 0) lore.add("<gray>Passive: <green>" + pct(passive) + "<gray> bonus chance");
        if (ability != null) lore.add("<gray>Ability: <gold>" + ability.displayName);
        lore.add(" ");
        lore.add("<yellow>Click <gray>to view perks & ability");

        return ItemBuilder.of(type.icon())
                .name("<" + type.color() + "><bold>" + type.displayName())
                .lore(lore)
                .glow().build();
    }

    static String progressBar(double frac) {
        int filled = (int) Math.round(Math.max(0, Math.min(1, frac)) * 20);
        return "<green>" + "▰".repeat(filled) + "<dark_gray>" + "▱".repeat(20 - filled);
    }

    static String pct(double v) {
        return String.format("%.1f%%", v * 100);
    }
}
