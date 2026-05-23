package com.nova.novablock.command;

import com.nova.novablock.NovaBlock;
import com.nova.novablock.gui.LeaderboardGui;
import com.nova.novablock.gui.MainMenuGui;
import com.nova.novablock.gui.PetSelectGui;
import com.nova.novablock.gui.PetStoreGui;
import com.nova.novablock.gui.ProphecyGui;
import com.nova.novablock.gui.QuestGui;
import com.nova.novablock.gui.ShopGui;
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
            "create", "home", "menu", "prophecy", "skills", "pets", "store",
            "quest", "shop", "leaderboard", "phase", "invite", "accept", "leave",
            "toggle", "help");

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
                island.teleportHome(p);
            }
            case "menu" -> { if (perm(p, "novablock.menu")) new MainMenuGui(plugin).open(p); }
            case "prophecy" -> { if (perm(p, "novablock.prophecy")) new ProphecyGui(plugin).open(p); }
            case "skills" -> { if (perm(p, "novablock.skills")) new SkillsGui(plugin).open(p); }
            case "pets" -> { if (perm(p, "novablock.pets")) new PetSelectGui(plugin).open(p); }
            case "store", "petstore" -> { if (perm(p, "novablock.pets")) new PetStoreGui(plugin).open(p); }
            case "quests", "quest" -> new QuestGui(plugin).open(p);
            case "shop" -> { if (perm(p, "novablock.shop")) new ShopGui(plugin).open(p); }
            case "leaderboard", "lb", "top" -> { if (perm(p, "novablock.leaderboard")) new LeaderboardGui(plugin).open(p); }
            case "phase" -> {
                Island island = plugin.islands().ofPlayer(p);
                if (island == null) { Msg.send(p, "<red>No island."); return true; }
                var phase = plugin.phases().getOrLast(island.data().getPhaseIndex());
                Msg.send(p, "<gray>You are in <" + phase.getThemeColor() + ">" + phase.getDisplayName()
                        + " <gray>(<white>" + island.data().getPhaseProgress() + "/" + phase.getRequiredBlocks() + "<gray>).");
            }
            case "invite" -> invite(p, args);
            case "accept" -> accept(p);
            case "leave" -> leave(p);
            case "toggle" -> {
                if (!p.hasPermission("novablock.toggle")) { denied(p); return true; }
                plugin.hotbar().toggle(p);
            }
            case "help", "?" -> sendHelp(p);
            default -> sendHelp(p);
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
        Msg.send(p, "<red>You don't have permission.");
    }

    private void sendHelp(Player p) {
        Msg.raw(p, "<gradient:#7B61FF:#4FC3F7><bold>NovaBlock</bold></gradient> <dark_gray>—</dark_gray> <gray>commands");
        Msg.raw(p, "<yellow>/ob create <gray>– make your island");
        Msg.raw(p, "<yellow>/ob home <gray>– teleport to your OneBlock");
        Msg.raw(p, "<yellow>/ob menu <gray>– main hub menu");
        Msg.raw(p, "<yellow>/ob prophecy <gray>– see and lock upcoming blocks");
        Msg.raw(p, "<yellow>/ob skills <gray>– skill trees and perks");
        Msg.raw(p, "<yellow>/ob pets <gray>– summon and command pets");
        Msg.raw(p, "<yellow>/ob store <gray>– buy more pets");
        Msg.raw(p, "<yellow>/ob quest <gray>– today's daily quest");
        Msg.raw(p, "<yellow>/ob shop <gray>– spend coins");
        Msg.raw(p, "<yellow>/ob leaderboard <gray>– top islands");
        Msg.raw(p, "<yellow>/ob phase <gray>– current phase status");
        Msg.raw(p, "<yellow>/ob invite <gray>– invite a player to your island");
        Msg.raw(p, "<yellow>/ob accept <gray>– accept a pending invite");
        Msg.raw(p, "<yellow>/ob toggle <gray>– show/hide the menu hotbar item");
        Msg.raw(p, "<yellow>/sb <gray>– show/hide the sidebar scoreboard");
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase();
            return SUBCOMMANDS.stream().filter(s -> s.startsWith(prefix)).collect(Collectors.toList());
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("invite")) {
            String prefix = args[1].toLowerCase();
            return org.bukkit.Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(prefix))
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}
