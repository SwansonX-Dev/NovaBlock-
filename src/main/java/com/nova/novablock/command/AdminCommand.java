package com.nova.novablock.command;

import com.nova.novablock.NovaBlock;
import com.nova.novablock.island.Island;
import com.nova.novablock.season.SeasonManager;
import com.nova.novablock.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class AdminCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBS = List.of(
            "reload", "setphase", "spawnboss", "givecoins", "event", "wipe", "givepaxel");
    private static final List<String> BOSSES = List.of(
            "magma_tyrant", "frostborn_sentinel", "void_herald");
    private static final List<String> EVENTS = List.of(
            "diamond_hour", "double_coins", "blood_moon", "lush_bloom", "rift_storm", "stop");

    private final NovaBlock plugin;

    public AdminCommand(NovaBlock plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        // Per-subcommand permission is checked below; allow entry if the user has
        // any admin permission at all (or the wildcard).
        if (args.length == 0) {
            Msg.send(sender, "<yellow>/obadmin <reload|setphase|spawnboss|givecoins|event|wipe>");
            return true;
        }
        String sub = args[0].toLowerCase();
        if (!sender.hasPermission("novablock.admin." + sub) && !sender.hasPermission("novablock.admin")) {
            Msg.send(sender, "<red>You don't have permission for that subcommand.");
            return true;
        }
        switch (sub) {
            case "reload" -> {
                plugin.configs().loadAll();
                plugin.phases().loadPhases();
                plugin.quests().loadDailyQuests();
                Msg.send(sender, "<green>Reloaded.");
            }
            case "setphase" -> {
                if (args.length < 3 || !(sender instanceof Player) && args.length < 3) {
                    Msg.send(sender, "<red>/obadmin setphase <player> <index>");
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) { Msg.send(sender, "<red>Player not online."); return true; }
                Island island = plugin.islands().ofPlayer(target);
                if (island == null) { Msg.send(sender, "<red>Target has no island."); return true; }
                int idx = Math.max(0, Math.min(plugin.phases().phaseCount() - 1, Integer.parseInt(args[2])));
                island.data().setPhaseIndex(idx);
                island.data().setPhaseProgress(0);
                island.upcomingBlocks().clear();
                Msg.send(sender, "<green>Phase set to " + idx);
            }
            case "spawnboss" -> {
                if (!(sender instanceof Player p)) return true;
                if (args.length < 2) { Msg.send(sender, "<red>/obadmin spawnboss <id>"); return true; }
                Island island = plugin.islands().ofPlayer(p);
                if (island == null) { Msg.send(sender, "<red>No island here."); return true; }
                if (plugin.bosses().spawn(args[1].toLowerCase(), island, p) == null) {
                    Msg.send(sender, "<red>Unknown boss id.");
                }
            }
            case "givecoins" -> {
                if (args.length < 3) { Msg.send(sender, "<red>/obadmin givecoins <player> <amount>"); return true; }
                Player target = Bukkit.getPlayerExact(args[1]);
                Island island = target == null ? null : plugin.islands().ofPlayer(target);
                if (island == null) { Msg.send(sender, "<red>Target has no island."); return true; }
                long amt = Long.parseLong(args[2]);
                plugin.economy().award(island, amt);
                Msg.send(sender, "<green>Gave " + amt + " coins.");
            }
            case "event" -> {
                if (args.length < 2) { Msg.send(sender, "<red>/obadmin event <name|stop>"); return true; }
                if (args[1].equalsIgnoreCase("stop")) { plugin.seasons().endEvent(); return true; }
                try {
                    var e = SeasonManager.ServerEvent.valueOf(args[1].toUpperCase());
                    plugin.seasons().startEvent(e, args.length >= 3 ? Integer.parseInt(args[2]) : 10);
                } catch (IllegalArgumentException ex) {
                    Msg.send(sender, "<red>Unknown event.");
                }
            }
            case "wipe" -> {
                if (args.length < 2) { Msg.send(sender, "<red>/obadmin wipe <player>"); return true; }
                Player target = Bukkit.getPlayerExact(args[1]);
                Island island = target == null ? null : plugin.islands().ofPlayer(target);
                if (island == null) { Msg.send(sender, "<red>Target has no island."); return true; }
                plugin.islands().delete(island);
                Msg.send(sender, "<green>Wiped.");
            }
            case "givepaxel" -> {
                if (args.length < 2) { Msg.send(sender, "<red>/obadmin givepaxel <player>"); return true; }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) { Msg.send(sender, "<red>Player not online."); return true; }
                plugin.paxels().give(target);
                plugin.paxels().refreshTier(target);
                Msg.send(sender, "<green>Gave a paxel to " + target.getName() + ".");
            }
            default -> Msg.send(sender, "<red>Unknown subcommand.");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (!sender.hasPermission("novablock.admin")) return Collections.emptyList();
        if (args.length == 1) {
            String p = args[0].toLowerCase();
            return SUBS.stream().filter(s -> s.startsWith(p)).collect(Collectors.toList());
        }
        if (args.length == 2) {
            return switch (args[0].toLowerCase()) {
                case "setphase", "givecoins", "wipe" -> Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(n -> n.toLowerCase().startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
                case "spawnboss" -> BOSSES.stream().filter(b -> b.startsWith(args[1].toLowerCase())).collect(Collectors.toList());
                case "event" -> EVENTS.stream().filter(e -> e.startsWith(args[1].toLowerCase())).collect(Collectors.toList());
                default -> Collections.emptyList();
            };
        }
        return Collections.emptyList();
    }
}
