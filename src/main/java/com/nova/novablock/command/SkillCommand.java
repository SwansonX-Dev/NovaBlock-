package com.nova.novablock.command;

import com.nova.novablock.NovaBlock;
import com.nova.novablock.gui.SkillDetailGui;
import com.nova.novablock.gui.SkillsGui;
import com.nova.novablock.progression.SkillType;
import com.nova.novablock.util.Msg;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/** {@code /skill} — opens the skills GUI; {@code /skill <skill>} jumps to a detail view. */
public class SkillCommand implements CommandExecutor, TabCompleter {

    private final NovaBlock plugin;

    public SkillCommand(NovaBlock plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Players only.");
            return true;
        }
        if (!p.hasPermission("novablock.skills")) {
            Msg.send(p, "<red>You don't have permission to view skills.");
            return true;
        }
        if (args.length == 0) {
            new SkillsGui(plugin).open(p);
            return true;
        }
        if (args[0].equalsIgnoreCase("help")) {
            Msg.send(p, "<light_purple><bold>Skills");
            Msg.raw(p, "<gray>/skill <gray>— open the skills menu");
            Msg.raw(p, "<gray>/skill <type> <gray>— view a skill's perks & ability");
            Msg.raw(p, "<gray>Hold a skill tool and <yellow>right-click<gray> to ready its ability, then strike.");
            return true;
        }
        SkillType type = parse(args[0]);
        if (type == null) {
            Msg.send(p, "<red>Unknown skill '<white>" + args[0] + "<red>'. Try /skill help.");
            return true;
        }
        new SkillDetailGui(plugin, type).open(p);
        return true;
    }

    private SkillType parse(String s) {
        for (SkillType t : SkillType.values()) {
            if (t.name().equalsIgnoreCase(s) || t.displayName().equalsIgnoreCase(s)) return t;
        }
        return null;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            String pre = args[0].toLowerCase();
            out.add("help");
            for (SkillType t : SkillType.values()) out.add(t.name().toLowerCase());
            out.removeIf(s -> !s.startsWith(pre));
        }
        return out;
    }
}
