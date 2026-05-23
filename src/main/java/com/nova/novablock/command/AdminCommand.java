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
            "reload", "setphase", "spawnboss", "givecoins", "event", "wipe", "givepaxel",
            "flags", "storage", "menu");
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
                plugin.paxels().replace(target);
                Msg.send(sender, "<green>Re-issued paxel to " + target.getName() + ".");
            }
            case "flags" -> {
                if (!(sender instanceof Player viewer)) { Msg.send(sender, "<red>Players only."); return true; }
                if (args.length < 2) { Msg.send(sender, "<red>/obadmin flags <player>"); return true; }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) { Msg.send(sender, "<red>Player not online."); return true; }
                Island island = plugin.islands().ofPlayer(target);
                if (island == null) { Msg.send(sender, "<red>Target has no island."); return true; }
                // Admin override is implicit: viewer has novablock.admin, IslandFlagsGui
                // respects per-flag perms via hasPermission so admins (who hold *) toggle freely.
                new com.nova.novablock.gui.IslandFlagsGui(plugin, island).open(viewer);
            }
            case "storage" -> {
                if (!(sender instanceof Player viewer)) { Msg.send(sender, "<red>Players only."); return true; }
                if (args.length < 2) { Msg.send(sender, "<red>/obadmin storage <player>"); return true; }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) { Msg.send(sender, "<red>Player not online."); return true; }
                Island island = plugin.islands().ofPlayer(target);
                if (island == null) { Msg.send(sender, "<red>Target has no island."); return true; }
                plugin.islandStorage().openFor(viewer, island);
            }
            case "menu" -> handleMenuEdit(sender, args);
            default -> Msg.send(sender, "<red>Unknown subcommand.");
        }
        return true;
    }

    private void handleMenuEdit(CommandSender sender, String[] args) {
        if (args.length < 2) {
            Msg.send(sender, "<yellow>/obadmin menu <add|remove|rename|list>");
            return;
        }
        switch (args[1].toLowerCase()) {
            case "list" -> {
                var entries = plugin.menuConfig().all();
                if (entries.isEmpty()) { Msg.send(sender, "<gray>No custom menu items yet."); return; }
                Msg.send(sender, "<gold>Custom menu items:");
                for (var e : entries.values()) {
                    Msg.send(sender, "<yellow>slot " + e.slot + " <gray>· <white>" + e.material.name()
                            + " <gray>· <aqua>" + e.name + " <gray>· <green>/" + e.command);
                }
            }
            case "add" -> {
                if (args.length < 5) { Msg.send(sender, "<red>/obadmin menu add <slot> <material> <command...>"); return; }
                int slot;
                try { slot = Integer.parseInt(args[2]); }
                catch (NumberFormatException ex) { Msg.send(sender, "<red>Slot must be an integer (0-44)."); return; }
                if (slot < 0 || slot > 44) { Msg.send(sender, "<red>Slot must be between 0 and 44."); return; }
                org.bukkit.Material mat = org.bukkit.Material.matchMaterial(args[3]);
                if (mat == null) { Msg.send(sender, "<red>Unknown material: " + args[3]); return; }
                StringBuilder cmd = new StringBuilder();
                for (int i = 4; i < args.length; i++) { if (i > 4) cmd.append(' '); cmd.append(args[i]); }
                String displayName = "<aqua>" + prettyMat(mat);
                plugin.menuConfig().put(slot, mat, displayName, cmd.toString());
                Msg.send(sender, "<green>Added menu item in slot " + slot + " running /" + cmd
                        + ". <gray>Use </gray>/obadmin menu rename " + slot + " <name></gray> to rename.");
            }
            case "rename" -> {
                if (args.length < 4) { Msg.send(sender, "<red>/obadmin menu rename <slot> <name...>"); return; }
                int slot;
                try { slot = Integer.parseInt(args[2]); }
                catch (NumberFormatException ex) { Msg.send(sender, "<red>Slot must be an integer."); return; }
                var entry = plugin.menuConfig().get(slot);
                if (entry == null) { Msg.send(sender, "<red>No menu item in that slot."); return; }
                StringBuilder name = new StringBuilder();
                for (int i = 3; i < args.length; i++) { if (i > 3) name.append(' '); name.append(args[i]); }
                entry.name = name.toString();
                plugin.menuConfig().save();
                Msg.send(sender, "<green>Renamed slot " + slot + " to " + name + ".");
            }
            case "remove" -> {
                if (args.length < 3) { Msg.send(sender, "<red>/obadmin menu remove <slot>"); return; }
                int slot;
                try { slot = Integer.parseInt(args[2]); }
                catch (NumberFormatException ex) { Msg.send(sender, "<red>Slot must be an integer."); return; }
                Msg.send(sender, plugin.menuConfig().remove(slot)
                        ? "<green>Removed menu item in slot " + slot + "."
                        : "<red>No menu item in that slot.");
            }
            default -> Msg.send(sender, "<yellow>/obadmin menu <add|remove|rename|list>");
        }
    }

    private static String prettyMat(org.bukkit.Material m) {
        StringBuilder out = new StringBuilder();
        for (String word : m.name().toLowerCase().split("_")) {
            if (out.length() > 0) out.append(' ');
            out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return out.toString();
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
