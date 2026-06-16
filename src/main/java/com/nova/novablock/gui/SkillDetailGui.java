package com.nova.novablock.gui;

import com.nova.novablock.NovaBlock;
import com.nova.novablock.ability.ActiveAbility;
import com.nova.novablock.progression.Passives;
import com.nova.novablock.progression.Perk;
import com.nova.novablock.progression.PlayerProgression;
import com.nova.novablock.progression.SkillEffects;
import com.nova.novablock.progression.SkillType;
import com.nova.novablock.util.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/** Detail view for one skill: progress, scaling passive, active ability and perks. */
public class SkillDetailGui extends ChestGui {

    private final NovaBlock plugin;
    private final SkillType skill;

    public SkillDetailGui(NovaBlock plugin, SkillType skill) {
        super("<" + skill.color() + "><bold>" + skill.displayName(), 6);
        this.plugin = plugin;
        this.skill = skill;
    }

    @Override
    protected void build(Player p) {
        PlayerProgression prog = plugin.progression().get(p);
        int level = prog.getLevel(skill);
        boolean maxed = prog.isMaxLevel(skill);
        long xp = prog.getXp(skill);
        long need = PlayerProgression.xpForLevel(level);

        // Header: level + progress.
        List<String> head = new ArrayList<>();
        head.add("<gray>Level <white>" + level + "<gray>/<white>" + PlayerProgression.maxLevel());
        head.add(maxed ? "<gold>★ MAXED" : "<gray>XP: <white>" + xp + "<dark_gray>/" + need);
        head.add(SkillsGui.progressBar(maxed ? 1.0 : (need <= 0 ? 0 : (double) xp / need)));
        set(4, ItemBuilder.of(skill.icon())
                .name("<" + skill.color() + "><bold>" + skill.displayName())
                .lore(head).glow().build(), null);

        // Scaling passive.
        double passive = Passives.chance(prog, skill);
        double cap = SkillEffects.passiveChance(skill, PlayerProgression.maxLevel());
        set(20, ItemBuilder.of(Material.NETHER_STAR)
                .name("<green>Passive bonus")
                .lore("<gray>Current: <green>" + SkillsGui.pct(passive),
                        "<gray>At cap: <green>" + SkillsGui.pct(cap),
                        " ",
                        "<gray>Climbs every level — bonus drops,",
                        "<gray>yield or treasure for this skill.")
                .glow().build(), null);

        // Active ability.
        ActiveAbility ability = ActiveAbility.forSkill(skill);
        if (ability != null) {
            SkillEffects.AbilityCfg cfg = ability.cfg();
            long cd = plugin.abilities().cooldownSeconds(p, ability);
            List<String> lore = new ArrayList<>();
            lore.add("<gray>Duration now: <white>" + (cfg.durationTicks(level) / 20) + "s");
            lore.add("<gray>Cooldown: <white>" + (cfg.cooldownTicks() / 20) + "s");
            if (cfg.dropMultiplier() > 1) lore.add("<gray>Drops: <white>x" + cfg.dropMultiplier());
            lore.add(" ");
            lore.add("<gray>Hold a <white>" + ability.tool.name().toLowerCase() + "<gray>,");
            lore.add("<yellow>right-click<gray> to ready, then strike.");
            lore.add(cd > 0 ? "<red>On cooldown: " + cd + "s" : "<green>Ready to use");
            set(24, ItemBuilder.of(Material.BLAZE_POWDER)
                    .name("<gold>" + ability.displayName + " <dark_gray>(ability)")
                    .lore(lore).glow().build(), null);
        }

        // Perks for this skill along the bottom.
        int col = 0;
        for (Perk perk : Perk.values()) {
            if (perk.skill != skill) continue;
            boolean unlocked = Perk.hasPerk(prog, perk);
            set(36 + col, ItemBuilder.of(unlocked ? Material.LIME_DYE : Material.GRAY_DYE)
                    .name((unlocked ? "<green>" : "<dark_gray>") + perk.name)
                    .lore("<gray>" + perk.description,
                            unlocked ? "<green>✔ Unlocked" : "<red>✘ Requires Lv " + perk.requiredLevel)
                    .build(), null);
            if (++col >= 9) break;
        }

        set(45, ItemBuilder.of(Material.ARROW).name("<gray>Back").build(),
                e -> new SkillsGui(plugin).open(p));
        fill(Material.BLACK_STAINED_GLASS_PANE, " ");
    }
}
