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
        if (!player.hasPermission("novablock.setspawn")) {
            com.nova.novablock.util.Msg.send(player, "<red>You don't have permission.");
            return true;
        }
        plugin.playerSpawns().set(player.getUniqueId(), player.getLocation());
        com.nova.novablock.util.Msg.send(player,
                "<green>Spawn set. You'll respawn here and rejoin here. <gray>(<yellow>/spawn</yellow> to teleport back)");
        return true;
    }
}
