package com.nova.novablock.command;

import com.nova.novablock.NovaBlock;
import com.nova.novablock.gui.HelpGui;
import com.nova.novablock.gui.LeaderboardGui;
import com.nova.novablock.gui.MainMenuGui;
import com.nova.novablock.gui.minion.MinionOverviewGui;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class OneBlockCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of(
            "create", "delete", "home", "menu", "prophecy", "skills", "flags", "storage",
            "quest", "leaderboard", "phase", "prestige", "invite", "accept", "leave",
            "visit", "upgrades", "upgrade", "path", "atlas", "pet", "pets", "toggle", "fix",
            "setspawn", "friend", "friends", "sprint", "minion", "minions", "hub", "community",
            "team", "members", "roster", "promote", "demote", "kick", "trust", "untrust",
            "bank", "autosell", "backpack", "help");
    private static final List<String> FRIEND_SUBS = List.of("add", "accept", "deny", "remove", "list");
    private static final List<String> BANK_SUBS = List.of("deposit", "withdraw", "balance");
    private static final long DELETE_CONFIRM_WINDOW_MS = 30_000L;

    private final NovaBlock plugin;
    private final Map<UUID, Long> pendingDeletes = new HashMap<>();

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
            case "delete", "wipe" -> deleteIsland(p, args);
            case "home" -> {
                if (!p.hasPermission("novablock.home")) { denied(p); return true; }
                Island island = plugin.islands().ofPlayer(p);
                if (island == null) { Msg.send(p, "<red>You don't have an island yet. Try <yellow>/ob create</yellow>."); return true; }
                boolean wantsNether = args.length > 1 && args[1].equalsIgnoreCase("nether");
                boolean wantsEnd = args.length > 1 && args[1].equalsIgnoreCase("end");
                if (wantsNether) {
                    if (!plugin.worlds().isNetherEnabled()) {
                        Msg.send(p, "<red>The Nether dimension is disabled on this server.");
                        return true;
                    }
                    if (!island.isNetherUnlocked()) {
                        Msg.send(p, "<red>The Nether is sealed. Reach Phase 7 to break through.");
                        return true;
                    }
                    plugin.repairs().repairNether(island, false);
                    island.teleportNetherHome(p);
                } else if (wantsEnd) {
                    if (!plugin.worlds().isEndEnabled()) {
                        Msg.send(p, "<red>The End dimension is disabled on this server.");
                        return true;
                    }
                    if (!island.isEndUnlocked()) {
                        Msg.send(p, "<red>The End is sealed. <gray>Prestige at least once to tear it open.");
                        return true;
                    }
                    plugin.repairs().repairEnd(island, false);
                    island.teleportEndHome(p);
                } else {
                    plugin.repairs().repair(island, false);
                    island.teleportHome(p);
                }
            }
            case "menu" -> { if (perm(p, "novablock.menu")) new MainMenuGui(plugin).open(p); }
            case "prophecy" -> { if (perm(p, "novablock.prophecy")) new ProphecyGui(plugin).open(p); }
            case "skills" -> { if (perm(p, "novablock.skills")) new SkillsGui(plugin).open(p); }
            case "flags" -> { if (perm(p, "novablock.flags")) new com.nova.novablock.gui.IslandFlagsGui(plugin).open(p); }
            case "storage", "vault" -> com.nova.novablock.island.IslandStorageManager.tryOpen(plugin, p);
            case "quests", "quest" -> new QuestGui(plugin).open(p);
            case "questline", "chronicle" -> new com.nova.novablock.gui.IslandQuestlineGui(plugin).open(p);
            case "leaderboard", "lb", "top" -> { if (perm(p, "novablock.leaderboard")) new LeaderboardGui(plugin).open(p); }
            case "phase" -> {
                Island island = plugin.islands().ofPlayer(p);
                if (island == null) { Msg.send(p, "<red>No island."); return true; }
                var phase = plugin.phases().getOrLast(island.data().getPhaseIndex());
                Msg.send(p, "<gray>You are in <" + phase.getThemeColor() + ">" + phase.getDisplayName()
                        + " <gray>(<white>" + island.data().getPhaseProgress() + "/" + phase.getRequiredBlocks() + "<gray>).");
            }
            case "prestige" -> openPrestige(p, args);
            case "invite" -> invite(p, args);
            case "accept" -> accept(p);
            case "leave" -> leave(p);
            case "team", "members", "roster" -> {
                if (args.length >= 2 && args[1].equalsIgnoreCase("list")) showTeam(p);
                else new com.nova.novablock.gui.RosterGui(plugin).open(p);
            }
            case "promote" -> promote(p, args);
            case "demote" -> demote(p, args);
            case "kick" -> kick(p, args);
            case "trust" -> handleTrust(p, args, false);
            case "untrust" -> handleTrust(p, args, true);
            case "bank" -> bank(p, args);
            case "visit" -> visit(p, args);
            case "upgrades", "upgrade" -> new com.nova.novablock.gui.UpgradesGui(plugin).open(p);
            case "path", "pass", "season", "atlas" -> new SeasonalPathGui(plugin).open(p);
            case "pet", "pets" -> openPets(p);
            case "minion", "minions" -> {
                if (!p.hasPermission("novablock.minions.use")) { denied(p); return true; }
                if (args.length >= 2) {
                    new MinionCommand(plugin).onCommand(sender, command, label, java.util.Arrays.copyOfRange(args, 1, args.length));
                } else {
                    new MinionOverviewGui(plugin).open(p);
                }
            }
            case "fix", "repair" -> fixOneBlock(p);
            case "setspawn" -> {
                if (!p.hasPermission("novablock.setspawn")) { denied(p); return true; }
                plugin.playerSpawns().set(p.getUniqueId(), p.getLocation());
                Msg.send(p, "<green>Spawn set. You'll respawn here and rejoin here. <gray>(<yellow>/spawn</yellow> to teleport back)");
            }
            case "friend", "friends", "f" -> handleFriend(p, args);
            case "sprint" -> new com.nova.novablock.gui.SprintGui(plugin).open(p);
            case "hub", "community" -> openHub(p, args);
            case "autosell", "sell" -> {
                var prog = plugin.progression().get(p);
                boolean enabled = !prog.isAutoSellEnabled();
                prog.setAutoSellEnabled(enabled);
                plugin.progression().save(p.getUniqueId());
                Msg.send(p, enabled
                        ? "<green>Auto-sell <bold>ON</bold><green>. Common blocks mined on the Community OneBlock now sell for coins."
                        : "<gray>Auto-sell <bold>OFF</bold><gray>. Community drops go to your inventory again.");
            }
            case "backpack", "bp" -> {
                if (args.length >= 2 && args[1].equalsIgnoreCase("toggle")) plugin.backpacks().toggleItem(p);
                else com.nova.novablock.backpack.BackpackManager.tryOpen(plugin, p);
            }
            case "depositchest", "dchest" -> plugin.depositChests().giveLinkTool(p);
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
        if (!island.roleOf(p).canManageRoster()) {
            Msg.send(p, "<red>Only the owner or a co-owner can invite people.");
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

    private void deleteIsland(Player p, String[] args) {
        if (!p.hasPermission("novablock.delete")) { denied(p); return; }
        Island island = plugin.islands().ofPlayer(p);
        if (island == null) { Msg.send(p, "<gray>You don't have an island."); return; }
        if (!island.data().getOwner().equals(p.getUniqueId())) {
            Msg.send(p, "<red>Only the owner can delete the island. Use <yellow>/ob leave<red> to leave it.");
            return;
        }
        boolean confirming = args.length > 1 && args[1].equalsIgnoreCase("confirm");
        long now = System.currentTimeMillis();
        Long expiry = pendingDeletes.get(p.getUniqueId());
        boolean hasPending = expiry != null && expiry > now;
        if (!confirming) {
            pendingDeletes.put(p.getUniqueId(), now + DELETE_CONFIRM_WINDOW_MS);
            Msg.send(p, "<red>⚠ This will permanently delete your island — phase progress, minions,");
            Msg.send(p, "<red>storage, upgrades, and member access are all wiped. <bold>This cannot be undone.</bold>");
            Msg.send(p, "<yellow>Type <white>/ob delete confirm<yellow> within 30 seconds to proceed.");
            return;
        }
        if (!hasPending) {
            Msg.send(p, "<gray>Confirmation expired. Run <yellow>/ob delete<gray> again to start over.");
            return;
        }
        pendingDeletes.remove(p.getUniqueId());
        java.util.List<UUID> memberSnapshot = new java.util.ArrayList<>(island.data().getMembers());
        plugin.islands().delete(island);
        org.bukkit.Location spawn = plugin.spawn().location();
        if (spawn != null) p.teleport(spawn);
        Msg.send(p, "<green>Your island has been deleted.");
        for (UUID m : memberSnapshot) {
            if (m.equals(p.getUniqueId())) continue;
            Player member = org.bukkit.Bukkit.getPlayer(m);
            if (member != null) {
                Msg.send(member, "<red>" + p.getName() + " deleted the island. You no longer have an island.");
            }
        }
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

    // ---------------- team roles ----------------

    private void showTeam(Player p) {
        Island island = plugin.islands().ofPlayer(p);
        if (island == null) { Msg.send(p, "<red>You aren't on an island."); return; }
        Msg.send(p, "<gold><bold>Island Team");
        Msg.send(p, "<gray>Bank: <yellow>" + plugin.economy().format(island.data().getBankBalance()) + " coins");
        // Owner first, then co-owners, then members.
        java.util.List<UUID> ordered = new java.util.ArrayList<>(island.data().getMembers());
        ordered.sort(java.util.Comparator.comparingInt(u -> {
            switch (island.data().getRole(u)) {
                case OWNER: return 0;
                case CO_OWNER: return 1;
                default: return 2;
            }
        }));
        for (UUID u : ordered) {
            com.nova.novablock.island.IslandRole role = island.data().getRole(u);
            org.bukkit.OfflinePlayer op = org.bukkit.Bukkit.getOfflinePlayer(u);
            String name = op.getName() != null ? op.getName() : u.toString().substring(0, 8);
            boolean online = op.isOnline();
            Msg.send(p, "<" + role.color + ">" + role.displayName + " <gray>· "
                    + (online ? "<green>" : "<dark_gray>") + name);
        }
        if (island.roleOf(p).canManageRoles()) {
            Msg.send(p, "<dark_gray>/ob promote · /ob demote · /ob kick · /ob bank");
        } else if (island.roleOf(p).canManageRoster()) {
            Msg.send(p, "<dark_gray>/ob invite · /ob kick · /ob bank");
        } else {
            Msg.send(p, "<dark_gray>/ob bank deposit <amount>");
        }
    }

    private void promote(Player p, String[] args) {
        Island island = plugin.islands().ofPlayer(p);
        if (island == null) { Msg.send(p, "<red>You aren't on an island."); return; }
        if (!island.roleOf(p).canManageRoles()) { Msg.send(p, "<red>Only the owner can promote members."); return; }
        if (args.length < 2) { Msg.send(p, "<gray>Usage: <yellow>/ob promote <player>"); return; }
        UUID target = resolveMember(island, args[1]);
        if (target == null) { Msg.send(p, "<red>No island member named " + args[1] + "."); return; }
        if (target.equals(p.getUniqueId())) { Msg.send(p, "<red>You're already the owner."); return; }
        com.nova.novablock.island.IslandRole current = island.data().getRole(target);
        if (current == com.nova.novablock.island.IslandRole.CO_OWNER) {
            Msg.send(p, "<gray>They're already a co-owner.");
            return;
        }
        plugin.islands().setMemberRole(island, target, com.nova.novablock.island.IslandRole.CO_OWNER);
        String name = nameOf(target);
        Msg.send(p, "<green>Promoted <yellow>" + name + "<green> to Co-Owner.");
        Player online = org.bukkit.Bukkit.getPlayer(target);
        if (online != null) Msg.send(online, "<gold>★ You were promoted to <#7FFFE0>Co-Owner<gold> of the island.");
    }

    private void demote(Player p, String[] args) {
        Island island = plugin.islands().ofPlayer(p);
        if (island == null) { Msg.send(p, "<red>You aren't on an island."); return; }
        if (!island.roleOf(p).canManageRoles()) { Msg.send(p, "<red>Only the owner can demote members."); return; }
        if (args.length < 2) { Msg.send(p, "<gray>Usage: <yellow>/ob demote <player>"); return; }
        UUID target = resolveMember(island, args[1]);
        if (target == null) { Msg.send(p, "<red>No island member named " + args[1] + "."); return; }
        if (island.data().getRole(target) != com.nova.novablock.island.IslandRole.CO_OWNER) {
            Msg.send(p, "<gray>That player isn't a co-owner.");
            return;
        }
        plugin.islands().setMemberRole(island, target, com.nova.novablock.island.IslandRole.MEMBER);
        String name = nameOf(target);
        Msg.send(p, "<gray>Demoted <yellow>" + name + "<gray> to Member.");
        Player online = org.bukkit.Bukkit.getPlayer(target);
        if (online != null) Msg.send(online, "<gray>You were demoted to Member on the island.");
    }

    private void kick(Player p, String[] args) {
        Island island = plugin.islands().ofPlayer(p);
        if (island == null) { Msg.send(p, "<red>You aren't on an island."); return; }
        if (!island.roleOf(p).canManageRoster()) { Msg.send(p, "<red>Only the owner or a co-owner can kick members."); return; }
        if (args.length < 2) { Msg.send(p, "<gray>Usage: <yellow>/ob kick <player>"); return; }
        UUID target = resolveMember(island, args[1]);
        if (target == null) { Msg.send(p, "<red>No island member named " + args[1] + "."); return; }
        if (target.equals(island.data().getOwner())) { Msg.send(p, "<red>You can't kick the owner."); return; }
        if (target.equals(p.getUniqueId())) { Msg.send(p, "<gray>Use <yellow>/ob leave<gray> to leave your own island."); return; }
        // Co-owners can't kick other co-owners — only the owner can.
        com.nova.novablock.island.IslandRole targetRole = island.data().getRole(target);
        if (targetRole == com.nova.novablock.island.IslandRole.CO_OWNER && !island.roleOf(p).canManageRoles()) {
            Msg.send(p, "<red>Only the owner can remove another co-owner.");
            return;
        }
        String name = nameOf(target);
        if (!plugin.islands().removeMember(island, target)) {
            Msg.send(p, "<red>Couldn't remove " + name + ".");
            return;
        }
        Msg.send(p, "<gray>Removed <yellow>" + name + "<gray> from the island.");
        Player online = org.bukkit.Bukkit.getPlayer(target);
        if (online != null) {
            Msg.send(online, "<red>You were removed from " + p.getName() + "'s island.");
            org.bukkit.Location spawn = plugin.spawn().location();
            if (spawn != null) online.teleport(spawn);
        }
        for (UUID m : island.data().getMembers()) {
            Player member = org.bukkit.Bukkit.getPlayer(m);
            if (member != null && !member.equals(p)) Msg.send(member, "<gray>" + name + " was removed from the island.");
        }
    }

    // ---------------- trust ----------------

    /**
     * {@code /ob trust <player|list>} and {@code /ob untrust <player>} — grant or
     * revoke build/break and container access on the sender's island. Only the
     * owner or a co-owner (anyone who can manage the roster) may change trust.
     */
    private void handleTrust(Player p, String[] args, boolean untrust) {
        if (!p.hasPermission("novablock.trust")) { denied(p); return; }
        Island island = plugin.islands().ofPlayer(p);
        if (island == null) {
            Msg.send(p, "<red>You need an island first. Try <yellow>/ob create</yellow>.");
            return;
        }
        if (!island.roleOf(p).canManageRoster()) {
            Msg.send(p, "<red>Only the owner or a co-owner can " + (untrust ? "untrust" : "trust") + " players.");
            return;
        }
        if (!untrust && args.length >= 2 && args[1].equalsIgnoreCase("list")) {
            listTrusted(p, island);
            return;
        }
        if (args.length < 2) {
            Msg.send(p, "<gray>Usage: <yellow>/ob " + (untrust ? "untrust <player>" : "trust <player|list>"));
            return;
        }

        org.bukkit.OfflinePlayer target = resolveOffline(args[1]);
        if (target == null) { Msg.send(p, "<red>Never seen a player named " + args[1] + "."); return; }
        UUID targetId = target.getUniqueId();
        String name = target.getName() != null ? target.getName() : args[1];

        if (targetId.equals(p.getUniqueId())) {
            Msg.send(p, "<red>You can't " + (untrust ? "untrust" : "trust") + " yourself.");
            return;
        }

        if (untrust) {
            if (!plugin.islands().removeTrusted(island, targetId)) {
                Msg.send(p, "<gray>" + name + " isn't trusted on your island.");
                return;
            }
            Msg.send(p, "<gray>Revoked <yellow>" + name + "<gray>'s build access on your island.");
            Player online = target.getPlayer();
            if (online != null) {
                Msg.send(online, "<gray>" + p.getName() + " removed your build access on their island.");
            }
            return;
        }

        if (island.isMember(targetId)) {
            Msg.send(p, "<gray>" + name + " is already a member of your island.");
            return;
        }
        if (!plugin.islands().addTrusted(island, targetId)) {
            Msg.send(p, "<gray>" + name + " is already trusted on your island.");
            return;
        }
        Msg.send(p, "<green>Trusted <yellow>" + name
                + "<green> — they can now build and break on your island.");
        Player online = target.getPlayer();
        if (online != null) {
            Msg.send(online, "<green>" + p.getName()
                    + " trusted you — you can now build on their island.");
            online.playSound(online.getLocation(), org.bukkit.Sound.UI_TOAST_IN, 1f, 1.4f);
        }
    }

    private void listTrusted(Player p, Island island) {
        var trusted = island.data().getTrusted();
        if (trusted.isEmpty()) {
            Msg.send(p, "<gray>No one is trusted on your island. Add someone with <yellow>/ob trust <player>.");
            return;
        }
        Msg.send(p, "<gold><bold>Trusted players");
        for (UUID u : trusted) {
            org.bukkit.OfflinePlayer op = org.bukkit.Bukkit.getOfflinePlayer(u);
            String name = op.getName() != null ? op.getName() : u.toString().substring(0, 8);
            Msg.send(p, "<gray>· " + (op.isOnline() ? "<green>" : "<dark_gray>") + name);
        }
    }

    /** Resolve a name to an OfflinePlayer: online first, then cache, then a played-before lookup. */
    private org.bukkit.OfflinePlayer resolveOffline(String name) {
        Player online = org.bukkit.Bukkit.getPlayerExact(name);
        if (online != null) return online;
        org.bukkit.OfflinePlayer cached = org.bukkit.Bukkit.getOfflinePlayerIfCached(name);
        if (cached != null) return cached;
        org.bukkit.OfflinePlayer op = org.bukkit.Bukkit.getOfflinePlayer(name);
        return op.hasPlayedBefore() ? op : null;
    }

    // ---------------- island bank ----------------

    private void bank(Player p, String[] args) {
        Island island = plugin.islands().ofPlayer(p);
        if (island == null) { Msg.send(p, "<red>You aren't on an island."); return; }
        if (args.length < 2 || args[1].equalsIgnoreCase("balance")) {
            Msg.send(p, "<gold>Island Bank: <yellow>" + plugin.economy().format(island.data().getBankBalance()) + " coins");
            Msg.send(p, "<gray>Deposit: <yellow>/ob bank deposit <amount|all>");
            if (island.roleOf(p).canWithdrawBank()) Msg.send(p, "<gray>Withdraw: <yellow>/ob bank withdraw <amount|all>");
            return;
        }
        String sub = args[1].toLowerCase();
        if (!sub.equals("deposit") && !sub.equals("withdraw")) {
            Msg.send(p, "<gray>Usage: <yellow>/ob bank <deposit|withdraw|balance> [amount]");
            return;
        }
        if (sub.equals("withdraw") && !island.roleOf(p).canWithdrawBank()) {
            Msg.send(p, "<red>Only the owner can withdraw from the island bank.");
            return;
        }
        if (args.length < 3) { Msg.send(p, "<gray>Usage: <yellow>/ob bank " + sub + " <amount|all>"); return; }

        boolean deposit = sub.equals("deposit");
        long available = deposit ? plugin.economy().balance(p) : island.data().getBankBalance();
        long amount;
        if (args[2].equalsIgnoreCase("all")) {
            amount = available;
        } else {
            try { amount = Long.parseLong(args[2].replace(",", "")); }
            catch (NumberFormatException ex) { Msg.send(p, "<red>'" + args[2] + "' isn't a number."); return; }
        }
        if (amount <= 0) { Msg.send(p, "<red>Enter a positive amount."); return; }

        if (deposit) {
            if (!plugin.islands().bankDeposit(island, p, amount)) {
                Msg.send(p, "<red>You don't have <yellow>" + plugin.economy().format(amount) + "<red> coins.");
                return;
            }
            Msg.send(p, "<green>Deposited <yellow>" + plugin.economy().format(amount)
                    + "<green> coins. Bank: <yellow>" + plugin.economy().format(island.data().getBankBalance()));
        } else {
            if (!plugin.islands().bankWithdraw(island, p, amount)) {
                Msg.send(p, "<red>The bank only has <yellow>" + plugin.economy().format(island.data().getBankBalance()) + "<red> coins.");
                return;
            }
            Msg.send(p, "<green>Withdrew <yellow>" + plugin.economy().format(amount)
                    + "<green> coins. Bank: <yellow>" + plugin.economy().format(island.data().getBankBalance()));
        }
    }

    /** Resolve a name to a member UUID of this island (online or offline). Null if not a member. */
    private UUID resolveMember(Island island, String name) {
        for (UUID u : island.data().getMembers()) {
            org.bukkit.OfflinePlayer op = org.bukkit.Bukkit.getOfflinePlayer(u);
            if (op.getName() != null && op.getName().equalsIgnoreCase(name)) return u;
        }
        return null;
    }

    private String nameOf(UUID id) {
        org.bukkit.OfflinePlayer op = org.bukkit.Bukkit.getOfflinePlayer(id);
        return op.getName() != null ? op.getName() : id.toString().substring(0, 8);
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
        boolean allowed = plugin.visits().canVisit(p, target);
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

    private void openHub(Player p, String[] args) {
        if (!p.hasPermission("novablock.hub")) {
            denied(p);
            return;
        }
        if (args.length >= 2 && (args[1].equalsIgnoreCase("tp")
                || args[1].equalsIgnoreCase("warp")
                || args[1].equalsIgnoreCase("spawn"))) {
            p.closeInventory();
            p.performCommand("warp community");
            return;
        }
        new com.nova.novablock.gui.HubGui(plugin).open(p);
    }

    private void openPrestige(Player p, String[] args) {
        Island island = plugin.islands().ofPlayer(p);
        if (island == null) {
            Msg.send(p, "<red>You don't have an island yet. Try <yellow>/ob create</yellow>.");
            return;
        }
        // Direct prestige of a named dimension: /ob prestige <overworld|nether|end>.
        // doPrestige guards eligibility + unlock and messages on failure.
        if (args.length > 1) {
            com.nova.novablock.island.Dimension dim = switch (args[1].toLowerCase()) {
                case "overworld", "over", "ow" -> com.nova.novablock.island.Dimension.OVERWORLD;
                case "nether", "hell" -> com.nova.novablock.island.Dimension.NETHER;
                case "end" -> com.nova.novablock.island.Dimension.END;
                default -> null;
            };
            if (dim == null) {
                Msg.send(p, "<red>Unknown dimension. Use <yellow>overworld<red>, <yellow>nether<red>, or <yellow>end<red>.");
                return;
            }
            plugin.prestige().doPrestige(p, island, dim);
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

    private void handleFriend(Player p, String[] args) {
        if (!p.hasPermission("novablock.friend")) { denied(p); return; }
        if (args.length < 2) {
            new com.nova.novablock.gui.FriendsGui(plugin).open(p);
            return;
        }
        String sub = args[1].toLowerCase();
        if (sub.equals("list")) { new com.nova.novablock.gui.FriendsGui(plugin).open(p); return; }
        if (args.length < 3) { Msg.send(p, "<red>/ob friend " + sub + " <player>"); return; }
        org.bukkit.OfflinePlayer target = org.bukkit.Bukkit.getPlayerExact(args[2]);
        if (target == null) target = org.bukkit.Bukkit.getOfflinePlayerIfCached(args[2]);
        if (target == null) target = org.bukkit.Bukkit.getOfflinePlayer(args[2]);
        if (target == null || (!target.hasPlayedBefore() && target.getPlayer() == null)) {
            Msg.send(p, "<red>No such player.");
            return;
        }
        java.util.UUID self = p.getUniqueId();
        java.util.UUID other = target.getUniqueId();
        if (self.equals(other)) { Msg.send(p, "<red>You can't friend yourself."); return; }
        String targetName = target.getName() == null ? args[2] : target.getName();
        switch (sub) {
            case "add" -> {
                var r = plugin.friends().addRequest(self, other);
                switch (r) {
                    case SENT -> {
                        Msg.send(p, "<green>Friend request sent to <yellow>" + targetName + "<green>.");
                        Player online = target.getPlayer();
                        if (online != null) Msg.send(online,
                                "<aqua>Friend request from <yellow>" + p.getName()
                                        + "<aqua>. <gray>(<yellow>/ob friend accept " + p.getName() + "<gray>)");
                    }
                    case ACCEPTED_INCOMING -> {
                        Msg.send(p, "<green>You and <yellow>" + targetName + "<green> are now friends.");
                        Player online = target.getPlayer();
                        if (online != null) Msg.send(online,
                                "<green>You and <yellow>" + p.getName() + "<green> are now friends.");
                    }
                    case ALREADY_FRIENDS -> Msg.send(p, "<yellow>You're already friends with " + targetName + ".");
                    case ALREADY_SENT -> Msg.send(p, "<yellow>You already sent a request to " + targetName + ".");
                    case SELF -> Msg.send(p, "<red>You can't friend yourself.");
                }
            }
            case "accept" -> {
                var r = plugin.friends().acceptRequest(self, other);
                switch (r) {
                    case OK -> {
                        Msg.send(p, "<green>You and <yellow>" + targetName + "<green> are now friends.");
                        Player online = target.getPlayer();
                        if (online != null) Msg.send(online,
                                "<green>You and <yellow>" + p.getName() + "<green> are now friends.");
                    }
                    case NO_REQUEST -> Msg.send(p, "<red>No pending request from " + targetName + ".");
                    case ALREADY_FRIENDS -> Msg.send(p, "<yellow>You're already friends with " + targetName + ".");
                }
            }
            case "deny" -> {
                if (plugin.friends().denyRequest(self, other)) {
                    Msg.send(p, "<gray>Denied request from " + targetName + ".");
                } else {
                    Msg.send(p, "<red>No pending request from " + targetName + ".");
                }
            }
            case "remove" -> {
                if (plugin.friends().removeFriend(self, other)) {
                    Msg.send(p, "<gray>Removed " + targetName + " from your friends.");
                    Player online = target.getPlayer();
                    if (online != null) Msg.send(online,
                            "<gray>" + p.getName() + " removed you from their friends.");
                } else {
                    Msg.send(p, "<red>You weren't friends with " + targetName + ".");
                }
            }
            default -> Msg.send(p, "<yellow>/ob friend <add|accept|deny|remove|list> [player]");
        }
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
        if (args.length == 2 && (args[0].equalsIgnoreCase("promote")
                || args[0].equalsIgnoreCase("demote") || args[0].equalsIgnoreCase("kick"))
                && sender instanceof Player tp) {
            String prefix = args[1].toLowerCase();
            Island island = plugin.islands().ofPlayer(tp);
            if (island == null) return Collections.emptyList();
            java.util.List<String> names = new java.util.ArrayList<>();
            for (UUID u : island.data().getMembers()) {
                if (u.equals(tp.getUniqueId())) continue;
                org.bukkit.OfflinePlayer op = org.bukkit.Bukkit.getOfflinePlayer(u);
                if (op.getName() != null && op.getName().toLowerCase().startsWith(prefix)) names.add(op.getName());
            }
            return names;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("untrust") && sender instanceof Player tp) {
            // /ob untrust suggests only the players already trusted on the sender's island.
            String prefix = args[1].toLowerCase();
            Island island = plugin.islands().ofPlayer(tp);
            if (island == null) return Collections.emptyList();
            java.util.List<String> names = new java.util.ArrayList<>();
            for (UUID u : island.data().getTrusted()) {
                org.bukkit.OfflinePlayer op = org.bukkit.Bukkit.getOfflinePlayer(u);
                if (op.getName() != null && op.getName().toLowerCase().startsWith(prefix)) names.add(op.getName());
            }
            return names;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("trust")) {
            String prefix = args[1].toLowerCase();
            java.util.List<String> out = org.bukkit.Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(prefix))
                    .collect(Collectors.toList());
            if ("list".startsWith(prefix)) out.add("list");
            return out;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("bank")) {
            String prefix = args[1].toLowerCase();
            return BANK_SUBS.stream().filter(s -> s.startsWith(prefix)).collect(Collectors.toList());
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("bank")
                && (args[1].equalsIgnoreCase("deposit") || args[1].equalsIgnoreCase("withdraw"))) {
            String prefix = args[2].toLowerCase();
            return java.util.stream.Stream.of("all")
                    .filter(s -> s.startsWith(prefix)).collect(Collectors.toList());
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("delete") || args[0].equalsIgnoreCase("wipe"))) {
            String prefix = args[1].toLowerCase();
            return java.util.stream.Stream.of("confirm")
                    .filter(s -> s.startsWith(prefix))
                    .collect(Collectors.toList());
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("home")) {
            String prefix = args[1].toLowerCase();
            return java.util.stream.Stream.of("nether", "end")
                    .filter(s -> s.startsWith(prefix))
                    .collect(Collectors.toList());
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("prestige")) {
            String prefix = args[1].toLowerCase();
            return java.util.stream.Stream.of("overworld", "nether", "end")
                    .filter(s -> s.startsWith(prefix))
                    .collect(Collectors.toList());
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("friend") || args[0].equalsIgnoreCase("friends") || args[0].equalsIgnoreCase("f"))) {
            String prefix = args[1].toLowerCase();
            return FRIEND_SUBS.stream().filter(s -> s.startsWith(prefix)).collect(Collectors.toList());
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("hub") || args[0].equalsIgnoreCase("community"))) {
            String prefix = args[1].toLowerCase();
            return List.of("tp", "warp", "spawn").stream().filter(s -> s.startsWith(prefix)).collect(Collectors.toList());
        }
        if (args.length == 3 && (args[0].equalsIgnoreCase("friend") || args[0].equalsIgnoreCase("friends") || args[0].equalsIgnoreCase("f"))) {
            String prefix = args[2].toLowerCase();
            return org.bukkit.Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(prefix))
                    .collect(Collectors.toList());
        }
        if (args.length >= 2 && (args[0].equalsIgnoreCase("minions") || args[0].equalsIgnoreCase("minion"))) {
            return new MinionCommand(plugin).onTabComplete(sender, command, alias, java.util.Arrays.copyOfRange(args, 1, args.length));
        }
        return Collections.emptyList();
    }
}
