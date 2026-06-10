package com.nova.novablock.command;

import com.nova.novablock.NovaBlock;
import com.nova.novablock.backpack.BackpackManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/** Opens the player's personal backpack. */
public class BackpackCommand implements CommandExecutor {

    private final NovaBlock plugin;

    public BackpackCommand(NovaBlock plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Players only.");
            return true;
        }
        if (args.length >= 1 && (args[0].equalsIgnoreCase("toggle") || args[0].equalsIgnoreCase("item"))) {
            if (!p.hasPermission("novablock.backpack")) {
                com.nova.novablock.util.Msg.send(p, "<red>You don't have permission to use the backpack.");
                return true;
            }
            plugin.backpacks().toggleItem(p);
            return true;
        }
        BackpackManager.tryOpen(plugin, p);
        return true;
    }
}
