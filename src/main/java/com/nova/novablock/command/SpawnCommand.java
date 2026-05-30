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
        Location personal = plugin.playerSpawns().get(player.getUniqueId());
        if (personal != null) {
            player.teleport(personal);
            com.nova.novablock.util.Msg.send(player, "<gray>Teleported to your spawn.");
            return true;
        }
        Location server = plugin.spawn().location();
        if (server == null) {
            com.nova.novablock.util.Msg.send(player, "<red>No spawn set. Use <yellow>/ob setspawn</yellow> to set yours.");
            return true;
        }
        player.teleport(server);
        com.nova.novablock.util.Msg.send(player, "<gray>Teleported to server spawn.");
        return true;
    }
}
