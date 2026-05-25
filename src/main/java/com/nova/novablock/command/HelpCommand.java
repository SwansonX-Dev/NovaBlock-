package com.nova.novablock.command;

import com.nova.novablock.NovaBlock;
import com.nova.novablock.gui.HelpGui;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class HelpCommand implements CommandExecutor {

    private final NovaBlock plugin;

    public HelpCommand(NovaBlock plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (sender instanceof Player player) {
            new HelpGui(plugin).open(player);
            return true;
        }
        sender.sendMessage("NovaBlock help is available in-game with /help or /ob help.");
        return true;
    }
}
