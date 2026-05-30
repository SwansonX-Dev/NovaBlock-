package com.nova.novablock.command;

import com.nova.novablock.NovaBlock;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class SetSpawnCommand implements CommandExecutor {

    private final NovaBlock plugin;

    public SetSpawnCommand(NovaBlock plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        plugin.spawn().setLocation(player.getLocation());
        com.nova.novablock.util.Msg.send(player, "<green>Spawn set to your current location.");
        return true;
    }
}
