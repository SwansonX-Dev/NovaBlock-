package com.nova.novablock.command;

import com.nova.novablock.NovaBlock;
import com.nova.novablock.island.Island;
import com.nova.novablock.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * {@code /trust <player>} and {@code /untrust <player>} — grant or revoke
 * build/break and container access on the sender's island. Only the owner or a
 * co-owner (anyone who can manage the roster) may change trust. Trusted players
 * are persisted per-island and checked by
 * {@link com.nova.novablock.island.IslandManager#canBuild}. {@code /trust list}
 * shows everyone currently trusted.
 */
public class TrustCommand implements CommandExecutor, TabCompleter {

    private final NovaBlock plugin;

    public TrustCommand(NovaBlock plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player p)) { sender.sendMessage("Players only."); return true; }
        boolean untrust = command.getName().equalsIgnoreCase("untrust");

        Island island = plugin.islands().ofPlayer(p);
        if (island == null) {
            Msg.send(p, "<red>You need an island first. Try <yellow>/ob create</yellow>.");
            return true;
        }
        if (!island.roleOf(p).canManageRoster()) {
            Msg.send(p, "<red>Only the owner or a co-owner can " + (untrust ? "untrust" : "trust") + " players.");
            return true;
        }
        if (!untrust && args.length >= 1 && args[0].equalsIgnoreCase("list")) {
            listTrusted(p, island);
            return true;
        }
        if (args.length < 1) {
            Msg.send(p, "<gray>Usage: <yellow>/" + (untrust ? "untrust <player>" : "trust <player|list>"));
            return true;
        }

        OfflinePlayer target = resolve(args[0]);
        if (target == null) { Msg.send(p, "<red>Never seen a player named " + args[0] + "."); return true; }
        UUID targetId = target.getUniqueId();
        String name = target.getName() != null ? target.getName() : args[0];

        if (targetId.equals(p.getUniqueId())) {
            Msg.send(p, "<red>You can't " + (untrust ? "untrust" : "trust") + " yourself.");
            return true;
        }

        if (untrust) {
            if (!plugin.islands().removeTrusted(island, targetId)) {
                Msg.send(p, "<gray>" + name + " isn't trusted on your island.");
                return true;
            }
            Msg.send(p, "<gray>Revoked <yellow>" + name + "<gray>'s build access on your island.");
            Player online = target.getPlayer();
            if (online != null) {
                Msg.send(online, "<gray>" + p.getName() + " removed your build access on their island.");
            }
            return true;
        }

        if (island.isMember(targetId)) {
            Msg.send(p, "<gray>" + name + " is already a member of your island.");
            return true;
        }
        if (!plugin.islands().addTrusted(island, targetId)) {
            Msg.send(p, "<gray>" + name + " is already trusted on your island.");
            return true;
        }
        Msg.send(p, "<green>Trusted <yellow>" + name
                + "<green> — they can now build and break on your island.");
        Player online = target.getPlayer();
        if (online != null) {
            Msg.send(online, "<green>" + p.getName()
                    + " trusted you — you can now build on their island.");
            online.playSound(online.getLocation(), org.bukkit.Sound.UI_TOAST_IN, 1f, 1.4f);
        }
        return true;
    }

    private void listTrusted(Player p, Island island) {
        var trusted = island.data().getTrusted();
        if (trusted.isEmpty()) {
            Msg.send(p, "<gray>No one is trusted on your island. Add someone with <yellow>/trust <player>.");
            return;
        }
        Msg.send(p, "<gold><bold>Trusted players");
        for (UUID u : trusted) {
            OfflinePlayer op = Bukkit.getOfflinePlayer(u);
            String name = op.getName() != null ? op.getName() : u.toString().substring(0, 8);
            Msg.send(p, "<gray>· " + (op.isOnline() ? "<green>" : "<dark_gray>") + name);
        }
    }

    /** Resolve a name to an OfflinePlayer: online first, then cache, then a played-before lookup. */
    private OfflinePlayer resolve(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) return online;
        OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(name);
        if (cached != null) return cached;
        OfflinePlayer op = Bukkit.getOfflinePlayer(name);
        return op.hasPlayedBefore() ? op : null;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (args.length != 1) return Collections.emptyList();
        boolean untrust = command.getName().equalsIgnoreCase("untrust");
        String prefix = args[0].toLowerCase();

        // /untrust suggests only the players already trusted on the sender's island.
        if (untrust && sender instanceof Player p) {
            Island island = plugin.islands().ofPlayer(p);
            if (island == null) return Collections.emptyList();
            List<String> names = new ArrayList<>();
            for (UUID u : island.data().getTrusted()) {
                OfflinePlayer op = Bukkit.getOfflinePlayer(u);
                if (op.getName() != null && op.getName().toLowerCase().startsWith(prefix)) names.add(op.getName());
            }
            return names;
        }

        List<String> out = Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(n -> n.toLowerCase().startsWith(prefix))
                .collect(Collectors.toList());
        if (!untrust && "list".startsWith(prefix)) out.add("list");
        return out;
    }
}
