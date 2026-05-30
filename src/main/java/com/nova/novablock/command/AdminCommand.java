package com.nova.novablock.command;

import com.nova.novablock.NovaBlock;
import com.nova.novablock.island.Island;
import com.nova.novablock.season.SeasonManager;
import com.nova.novablock.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
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
            "flags", "storage", "menu", "path", "freshstart", "fix", "setspawn");
    private static final List<String> EVENTS = List.of(
            "diamond_hour", "double_coins", "blood_moon", "lush_bloom", "rift_storm", "stop");

    private final NovaBlock plugin;

    public AdminCommand(NovaBlock plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        // Per-subcommand permission is checked below; allow entry if the user has
        // any admin permission at all (or the wildcard).
        if (args.length == 0) {
            Msg.send(sender, "<yellow>/obadmin <reload|setphase|spawnboss|givecoins|event|wipe|freshstart>");
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
                plugin.antiAfk().reload();
                plugin.prestige().reload();
                plugin.seasonalPaths().load();
                plugin.seasonalPaths().ensureTags();
                if (plugin.minions() != null) plugin.minions().reloadSettings();
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
                int idx;
                try { idx = Integer.parseInt(args[2]); }
                catch (NumberFormatException ex) { Msg.send(sender, "<red>Phase index must be an integer."); return true; }
                idx = Math.max(0, Math.min(plugin.phases().phaseCount() - 1, idx));
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
                long amt;
                try { amt = Long.parseLong(args[2]); }
                catch (NumberFormatException ex) { Msg.send(sender, "<red>Amount must be an integer."); return true; }
                plugin.economy().award(island, amt);
                Msg.send(sender, "<green>Gave " + amt + " coins.");
            }
            case "event" -> {
                if (args.length < 2) { Msg.send(sender, "<red>/obadmin event <name|stop>"); return true; }
                if (args[1].equalsIgnoreCase("stop")) { plugin.seasons().endEvent(); return true; }
                try {
                    var e = SeasonManager.ServerEvent.valueOf(args[1].toUpperCase());
                    int minutes = 10;
                    if (args.length >= 3) {
                        try { minutes = Integer.parseInt(args[2]); }
                        catch (NumberFormatException nfe) { Msg.send(sender, "<red>Duration must be an integer."); return true; }
                    }
                    plugin.seasons().startEvent(e, minutes);
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
            case "path" -> handlePath(sender, args);
            case "freshstart" -> handleFreshStart(sender, args);
            case "fix" -> handleFix(sender, args);
            case "setspawn" -> {
                if (!(sender instanceof Player p)) { Msg.send(sender, "<red>Players only."); return true; }
                plugin.spawn().setLocation(p.getLocation());
                Msg.send(sender, "<green>Server spawn set to your current location.");
            }
            default -> Msg.send(sender, "<red>Unknown subcommand.");
        }
        return true;
    }

    private void handlePath(CommandSender sender, String[] args) {
        if (args.length < 2) {
            var path = plugin.seasonalPaths().activePath();
            Msg.send(sender, "<yellow>/obadmin path <status|points|resetplayer|reload>");
            Msg.send(sender, "<gray>Active: <white>" + path.name() + " <dark_gray>(" + path.key() + ")");
            return;
        }
        switch (args[1].toLowerCase()) {
            case "status" -> {
                var path = plugin.seasonalPaths().activePath();
                Msg.send(sender, "<gold>Active path: <white>" + path.name());
                Msg.send(sender, "<gray>Pet: <yellow>" + path.petId() + " <gray>Tag: <aqua>" + path.tagId());
            }
            case "points" -> {
                if (args.length < 4) {
                    Msg.send(sender, "<red>/obadmin path points <player> <amount>");
                    return;
                }
                Player target = Bukkit.getPlayerExact(args[2]);
                if (target == null) {
                    Msg.send(sender, "<red>Player not online.");
                    return;
                }
                int amount;
                try { amount = Integer.parseInt(args[3]); }
                catch (NumberFormatException ex) {
                    Msg.send(sender, "<red>Amount must be an integer.");
                    return;
                }
                plugin.seasonalPaths().addAdminPoints(target, amount);
                Msg.send(sender, "<green>Added " + amount + " path points to " + target.getName() + ".");
            }
            case "resetplayer" -> {
                if (args.length < 3) {
                    Msg.send(sender, "<red>/obadmin path resetplayer <player>");
                    return;
                }
                Player target = Bukkit.getPlayerExact(args[2]);
                if (target == null) {
                    Msg.send(sender, "<red>Player not online.");
                    return;
                }
                plugin.seasonalPaths().resetPlayer(target);
                Msg.send(sender, "<green>Reset active path progress for " + target.getName() + ".");
            }
            case "reload" -> {
                plugin.seasonalPaths().load();
                plugin.seasonalPaths().ensureTags();
                Msg.send(sender, "<green>Reloaded seasonal path state and ensured xTags definitions.");
            }
            default -> Msg.send(sender, "<yellow>/obadmin path <status|points|resetplayer|reload>");
        }
    }

    private void handleFix(CommandSender sender, String[] args) {
        if (args.length < 2) {
            Msg.send(sender, "<red>/obadmin fix <player|all>");
            return;
        }
        if (args[1].equalsIgnoreCase("all")) {
            int repaired = plugin.repairs().repairLoadedIslands();
            Msg.send(sender, "<green>Checked all loaded islands. Repaired " + repaired + " OneBlock(s).");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        Island island = target == null ? null : plugin.islands().ofPlayer(target);
        if (island == null) {
            Msg.send(sender, "<red>Target has no island or is not online.");
            return;
        }
        boolean repaired = plugin.repairs().repair(island, true);
        Msg.send(sender, repaired
                ? "<green>Restored " + target.getName() + "'s OneBlock."
                : "<gray>" + target.getName() + "'s OneBlock already looks healthy.");
    }

    private void handleFreshStart(CommandSender sender, String[] args) {
        if (args.length < 3 || !args[2].equalsIgnoreCase("confirm")) {
            Msg.send(sender, "<red>/obadmin freshstart <player> confirm");
            Msg.send(sender, "<yellow>This wipes NovaBlock and xEconomy player data.");
            return;
        }
        OfflinePlayer target = Bukkit.getPlayerExact(args[1]);
        if (target == null) target = Bukkit.getOfflinePlayer(args[1]);
        if (!target.hasPlayedBefore() && !target.isOnline()) {
            Msg.send(sender, "<red>That player has never joined this server.");
            return;
        }

        java.util.UUID id = target.getUniqueId();
        String name = target.getName() != null ? target.getName() : args[1];

        dev.xsuite.economy.api.PlayerData playerData = dev.xsuite.economy.api.XEconomy.playerData();
        if (playerData == null) {
            Msg.send(sender, "<red>xEconomy player-data reset service is unavailable.");
            return;
        }

        Player online = target.getPlayer();
        if (online != null) resetOnlineSession(online);

        plugin.islands().resetPlayer(id);
        plugin.progression().delete(id);
        playerData.reset(id, name);
        Msg.send(sender, "<green>Fresh-start reset completed for " + name + ".");
    }

    private void resetOnlineSession(Player player) {
        player.closeInventory();
        player.getInventory().clear();
        player.getEnderChest().clear();
        player.setExp(0f);
        player.setLevel(0);
        player.setTotalExperience(0);
        player.setFoodLevel(20);
        player.setSaturation(5f);
        player.setFireTicks(0);
        for (var effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }
        if (!Bukkit.getWorlds().isEmpty()) {
            player.teleport(Bukkit.getWorlds().getFirst().getSpawnLocation());
        }
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
                case "setphase", "givecoins", "wipe", "freshstart" -> Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(n -> n.toLowerCase().startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
                case "path" -> List.of("status", "points", "resetplayer", "reload").stream()
                        .filter(n -> n.startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
                case "fix" -> {
                    java.util.List<String> tabs = Bukkit.getOnlinePlayers().stream()
                            .map(Player::getName)
                            .collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));
                    tabs.add("all");
                    yield tabs.stream()
                            .filter(n -> n.toLowerCase().startsWith(args[1].toLowerCase()))
                            .collect(Collectors.toList());
                }
                case "spawnboss" -> plugin.bosses().bossIds().stream()
                        .filter(b -> b.startsWith(args[1].toLowerCase()))
                        .sorted()
                        .collect(Collectors.toList());
                case "event" -> EVENTS.stream().filter(e -> e.startsWith(args[1].toLowerCase())).collect(Collectors.toList());
                default -> Collections.emptyList();
            };
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("freshstart")) {
            return "confirm".startsWith(args[2].toLowerCase()) ? List.of("confirm") : Collections.emptyList();
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("path")
                && (args[1].equalsIgnoreCase("points") || args[1].equalsIgnoreCase("resetplayer"))) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(args[2].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}
