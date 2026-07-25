package com.nova.novablock.command;

import com.nova.novablock.NovaBlock;
import com.nova.novablock.chat.ChatTagManager;
import com.nova.novablock.chat.SnapshotGui;
import com.nova.novablock.util.Msg;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Opens a chat-tag snapshot. Not meant to be typed — it exists so the {@code [inv]} and
 * {@code [ender]} tags have a click target that survives being re-rendered by whatever
 * plugin owns the chat format (a click callback bound to a component instance would not).
 */
public class ChatViewCommand implements CommandExecutor {

    private final NovaBlock plugin;

    public ChatViewCommand(NovaBlock plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Players only.");
            return true;
        }
        if (args.length < 1) return true; // clicked-only; no usage text to leak

        UUID token;
        try { token = UUID.fromString(args[0]); }
        catch (IllegalArgumentException ex) {
            Msg.send(p, "<red>That preview link isn't valid.");
            return true;
        }

        ChatTagManager.Snapshot snapshot = plugin.chatTags().snapshot(token);
        if (snapshot == null) {
            Msg.send(p, "<gray>That preview has expired.");
            return true;
        }
        new SnapshotGui(snapshot).open(p);
        return true;
    }
}
