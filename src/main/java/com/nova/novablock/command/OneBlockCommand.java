package com.nova.novablock.command;

import com.nova.novablock.NovaBlock;
import com.nova.novablock.gui.HelpGui;
import com.nova.novablock.gui.LeaderboardGui;
import com.nova.novablock.gui.MainMenuGui;
import com.nova.novablock.gui.ProphecyGui;
import com.nova.novablock.gui.QuestGui;
import com.nova.novablock.gui.SeasonalPathGui;
import com.nova.novablock.gui.SkillsGui;
import com.nova.novablock.island.Island;
import com.nova.novablock.util.Msg;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class OneBlockCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of(
            "create", "home", "menu", "prophecy", "skills", "flags", "storage",
            "quest", "leaderboard", "phase", "prestige", "invite", "accept", "leave",
            "visit", "upgrades", "upgrade", "path", "atlas", "pet", "pets", "toggle", "fix",
            "setspawn", "help");

    private final NovaBlock plugin;

    public OneBlockCommand(NovaBlock plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Players only.");
            return true;
        }
        if (args.length == 0) {
            new MainMenuGui(plugin).open(p);
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "create" -> {
                if (!p.hasPermission("novablock.create")) { denied(p); return true; }
                if (plugin.islands().ofPlayer(p) != null) {
                    Msg.send(p, "<red>You already have an island. Use /ob home.");
                    return true;
                }
                Island island = plugin.islands().create(p);
                Msg.send(p, "<green>Island created! Teleporting...");
                island.teleportHome(p);
            }
            case "home" -> {
                if (!p.hasPermission("novablock.home")) { denied(p); return true; }
                Island island = plugin.islands().ofPlayer(p);
                if (island == null) { Msg.send(p, "<red>You don't have an island yet. Try <yellow>/ob create</yellow>."); return true; }
                plugin.repairs().repair(island, false);
                island.teleportHome(p);
            }
            case "menu" -> { if (perm(p, "novablock.menu")) new MainMenuGui(plugin).open(p); }
            case "prophecy" -> { if (perm(p, "novablock.prophecy")) new ProphecyGui(plugin).open(p); }
            case "skills" -> { if (perm(p, "novablock.skills")) new SkillsGui(plugin).open(p); }
            case "flags" -> { if (perm(p, "novablock.flags")) new com.nova.novablock.gui.IslandFlagsGui(plugin).open(p); }
            case "storage", "vault" -> com.nova.novablock.island.IslandStorageManager.tryOpen(plugin, p);
            case "quests", "quest" -> new QuestGui(plugin).open(p);
            case "leaderboard", "lb", "top" -> { if (perm(p, "novablock.leaderboard")) new LeaderboardGui(plugin).open(p); }
            case "phase" -> {
                Island island = plugin.islands().ofPlayer(p);
                if (island == null) { Msg.send(p, "<red>No island."); return true; }
                var phase = plugin.phases().getOrLast(island.data().getPhaseIndex());
                Msg.send(p, "<gray>You are in <" + phase.getThemeColor() + ">" + phase.getDisplayName()
                        + " <gray>(<white>" + island.data().getPhaseProgress() + "/" + phase.getRequiredBlocks() + "<gray>).");
            }
            case "prestige" -> openPrestige(p);
            case "invite" -> invite(p, args);
            case "accept" -> accept(p);
            case "leave" -> leave(p);
            case "visit" -> visit(p, args);
            case "upgrades", "upgrade" -> new com.nova.novablock.gui.UpgradesGui(plugin).open(p);
            case "path", "pass", "season", "atlas" -> new SeasonalPathGui(plugin).open(p);
            case "pet", "pets" -> openPets(p);
            case "fix", "repair" -> fixOneBlock(p);
            case "setspawn" -> {
                if (!p.hasPermission("novablock.setspawn")) { denied(p); return true; }
                plugin.playerSpawns().set(p.getUniqueId(), p.getLocation());
                Msg.send(p, "<green>Spawn set. You'll respawn here and rejoin here. <gray>(<yellow>/spawn</yellow> to teleport back)");
            }
            case "toggle" -> {
                if (!p.hasPermission("novablock.toggle")) { denied(p); return true; }
                plugin.hotbar().toggle(p);
            }
            case "help", "?" -> new HelpGui(plugin).open(p);
            default -> new HelpGui(plugin).open(p);
        }
        return true;
    }

    private void invite(Player p, String[] args) {
        Island island = plugin.islands().ofPlayer(p);
        if (island == null) { Msg.send(p, "<red>You need an island first. Try <yellow>/ob create</yellow>."); return; }
        if (!island.data().getOwner().equals(p.getUniqueId())) {
            Msg.send(p, "<red>Only the island owner can invite people.");
            return;
        }
        if (args.length < 2) { Msg.send(p, "<gray>Usage: <yellow>/ob invite <player>"); return; }
        Player target = org.bukkit.Bukkit.getPlayerExact(args[1]);
        if (target == null) { Msg.send(p, "<red>That player isn't online."); return; }
        if (target.equals(p)) { Msg.send(p, "<red>You can't invite yourself."); return; }
        if (island.isMember(target)) { Msg.send(p, "<gray>They're already on your island."); return; }
        if (plugin.islands().ofPlayer(target) != null) {
            Msg.send(p, "<red>" + target.getName() + " already has an island.");
            return;
        }
        plugin.invites().invite(target.getUniqueId(), island.data().getId());
        Msg.send(p, "<green>Invited <yellow>" + target.getName() + "<green>. They have 60 seconds to <yellow>/ob accept</yellow>.");
        Msg.send(target, "<gold>" + p.getName() + " <gray>invited you to their island. Tap <yellow>/ob accept</yellow> within 60s.");
        target.playSound(target.getLocation(), org.bukkit.Sound.UI_TOAST_IN, 1f, 1.4f);
    }

    private void leave(Player p) {
        Island island = plugin.islands().ofPlayer(p);
        if (island == null) { Msg.send(p, "<gray>You aren't on an island."); return; }
        if (island.data().getOwner().equals(p.getUniqueId())) {
            Msg.send(p, "<red>You're the owner — ask an admin to wipe the island instead.");
            return;
        }
        plugin.islands().removeMember(island, p.getUniqueId());
        Msg.send(p, "<gray>You left the island.");
        for (java.util.UUID m : island.data().getMembers()) {
            Player member = org.bukkit.Bukkit.getPlayer(m);
            if (member != null) Msg.send(member, "<gray>" + p.getName() + " left the island.");
        }
    }

    private void visit(Player p, String[] args) {
        if (args.length < 2) { Msg.send(p, "<gray>Usage: <yellow>/ob visit <player>"); return; }
        Player target = org.bukkit.Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            // Allow visiting offline players too — look up by name in the island cache.
            var offline = org.bukkit.Bukkit.getOfflinePlayer(args[1]);
            if (!offline.hasPlayedBefore()) { Msg.send(p, "<red>Never seen that player."); return; }
            Island offlineIsland = plugin.islands().ofPlayer(offline.getUniqueId());
            if (offlineIsland == null) { Msg.send(p, "<red>They don't have an island."); return; }
            tryVisit(p, offlineIsland, offline.getName());
            return;
        }
        Island targetIsland = plugin.islands().ofPlayer(target);
        if (targetIsland == null) { Msg.send(p, "<red>" + target.getName() + " has no island."); return; }
        tryVisit(p, targetIsland, target.getName());
    }

    private void tryVisit(Player p, Island target, String ownerName) {
        // Members and admins always allowed; everyone else needs VISITOR_BUILD.
        boolean isMember = target.isMember(p);
        boolean isAdmin = p.hasPermission("novablock.admin");
        boolean allowed = isMember || isAdmin
                || target.data().isFlag(com.nova.novablock.island.IslandFlag.VISITOR_BUILD);
        if (!allowed) {
            Msg.send(p, com.nova.novablock.util.Messages.format("visit-closed",
                    "<red>{target}'s island is closed to visitors.", "target", ownerName));
            return;
        }
        p.closeInventory();
        target.teleportHome(p);
        Msg.send(p, com.nova.novablock.util.Messages.format("visit-success",
                "<aqua>Visiting <yellow>{target}<aqua>'s island.", "target", ownerName));
    }

    private void openPets(Player p) {
        p.closeInventory();
        if (org.bukkit.Bukkit.getPluginManager().getPlugin("xPets") == null) {
            Msg.send(p, "<red>The pets system (xPets) isn't installed on this server.");
            return;
        }
        p.performCommand("pets");
    }

    private void openPrestige(Player p) {
        Island island = plugin.islands().ofPlayer(p);
        if (island == null) {
            Msg.send(p, "<red>You don't have an island yet. Try <yellow>/ob create</yellow>.");
            return;
        }
        if (!plugin.prestige().canPrestige(island)) {
            var last = plugin.phases().get(plugin.phases().phaseCount() - 1);
            Msg.send(p, "<red>You must complete <" + last.getThemeColor() + ">"
                    + last.getDisplayName() + " <red>before you can prestige.");
            return;
        }
        new com.nova.novablock.gui.PrestigeGui(plugin).open(p);
    }

    private void fixOneBlock(Player p) {
        if (!p.hasPermission("novablock.fix")) {
            denied(p);
            return;
        }
        Island island = plugin.islands().ofPlayer(p);
        if (island == null) {
            Msg.send(p, "<red>You don't have an island yet. Try <yellow>/ob create</yellow>.");
            return;
        }
        boolean repaired = plugin.repairs().repair(island, true);
        Msg.send(p, repaired
                ? "<green>Your OneBlock was restored."
                : "<gray>Your OneBlock already looks healthy.");
        island.teleportHome(p);
    }

    private void accept(Player p) {
        if (plugin.islands().ofPlayer(p) != null) {
            Msg.send(p, "<red>You already have an island. Leave it first.");
            return;
        }
        java.util.UUID islandId = plugin.invites().resolve(p.getUniqueId());
        if (islandId == null) { Msg.send(p, "<gray>No pending invite (or it expired)."); return; }
        Island island = plugin.islands().get(islandId);
        if (island == null) { Msg.send(p, "<red>That island no longer exists."); return; }
        plugin.islands().addMember(island, p.getUniqueId());
        Msg.send(p, "<green>You joined the island! Teleporting...");
        island.teleportHome(p);
        for (java.util.UUID m : island.data().getMembers()) {
            Player member = org.bukkit.Bukkit.getPlayer(m);
            if (member != null && !member.equals(p)) {
                Msg.send(member, "<green>" + p.getName() + " <gray>joined the island.");
            }
        }
    }

    private boolean perm(Player p, String node) {
        if (p.hasPermission(node)) return true;
        denied(p);
        return false;
    }

    private void denied(Player p) {
        Msg.send(p, com.nova.novablock.util.Messages.of("no-permission", "<red>You don't have permission."));
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase();
            return SUBCOMMANDS.stream().filter(s -> s.startsWith(prefix)).collect(Collectors.toList());
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("invite") || args[0].equalsIgnoreCase("visit"))) {
            String prefix = args[1].toLowerCase();
            return org.bukkit.Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(prefix))
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}
