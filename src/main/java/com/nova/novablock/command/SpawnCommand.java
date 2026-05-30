package com.nova.novablock.command;

import com.nova.novablock.NovaBlock;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class SpawnCommand implements CommandExecutor {

    private final NovaBlock plugin;

    public SpawnCommand(NovaBlock plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        if (!player.hasPermission("novablock.spawn")) {
            com.nova.novablock.util.Msg.send(player, "<red>You don't have permission.");
            return true;
        }
        Location dest = plugin.spawn().location();
        if (dest == null) {
            com.nova.novablock.util.Msg.send(player, "<red>Spawn is not set. Ask an admin to run /setspawn.");
            return true;
        }
        player.teleport(dest);
        com.nova.novablock.util.Msg.send(player, "<gray>Teleported to spawn.");
        return true;
    }
}
