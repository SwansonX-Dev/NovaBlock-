package com.nova.novablock.command;

import com.nova.novablock.NovaBlock;
import com.nova.novablock.gui.minion.MinionDebugGui;
import com.nova.novablock.gui.minion.MinionHelpGui;
import com.nova.novablock.gui.minion.MinionOutputAdminGui;
import com.nova.novablock.gui.minion.MinionOutputEditorGui;
import com.nova.novablock.gui.minion.MinionOverviewGui;
import com.nova.novablock.gui.minion.MinionShopGui;
import com.nova.novablock.gui.minion.MinionTypesGui;
import com.nova.novablock.island.Island;
import com.nova.novablock.minion.MinionData;
import com.nova.novablock.minion.MinionType;
import com.nova.novablock.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class MinionCommand implements CommandExecutor, TabCompleter {
    private final NovaBlock plugin;
    public MinionCommand(NovaBlock plugin) { this.plugin = plugin; }
    @Override public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            if (sender instanceof Player p && canUse(p)) new MinionOverviewGui(plugin).open(p);
            else sender.sendMessage("Players only.");
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "shop", "buy" -> { if (sender instanceof Player p && canUse(p)) new MinionShopGui(plugin).open(p); }
            case "overview" -> { if (sender instanceof Player p && canUse(p)) new MinionOverviewGui(plugin).open(p); }
            case "help", "?" -> { if (sender instanceof Player p && canUse(p)) new MinionHelpGui(plugin).open(p); else sender.sendMessage("Minions: /" + label + " [overview|shop|outputs]"); }
            case "types" -> { if (sender instanceof Player p && canUse(p)) new MinionTypesGui(plugin).open(p); else listTypes(sender); }
            case "reload" -> reload(sender);
            case "debug" -> debug(sender, label, args);
            case "give" -> give(sender, label, args);
            case "list" -> list(sender);
            case "removeall" -> removeAll(sender, label, args);
            case "admin", "outputs" -> outputs(sender, args);
            default -> { if (sender instanceof Player p) new MinionOverviewGui(plugin).open(p); }
        }
        return true;
    }
    private boolean admin(CommandSender sender) { return sender.hasPermission("novablock.minions.admin") || sender.hasPermission("novablock.admin.minions"); }
    private boolean canUse(Player player) {
        if (player.hasPermission("novablock.minions.use")) return true;
        Msg.send(player, "<red>You don't have permission.");
        return false;
    }
    private void give(CommandSender sender, String label, String[] args) {
        if (!admin(sender)) { sender.sendMessage("No permission."); return; }
        if (args.length < 3) { sender.sendMessage("Usage: /" + label + " give <player> <type> [amount]"); return; }
        Player target = Bukkit.getPlayerExact(args[1]);
        MinionType type = MinionType.byId(args[2]);
        if (target == null || type == null) { sender.sendMessage("Unknown player or type."); return; }
        int amount = 1;
        if (args.length >= 4) {
            try {
                amount = Math.max(1, Math.min(64, Integer.parseInt(args[3])));
            } catch (NumberFormatException ex) {
                sender.sendMessage("Amount must be a number.");
                return;
            }
        }
        target.getInventory().addItem(type.createItem(plugin, amount));
    }
    private void list(CommandSender sender) {
        if (!(sender instanceof Player p) || !admin(sender)) return;
        Island island = plugin.islands().ofPlayer(p);
        if (island == null) return;
        Msg.send(p, "<gold>Minions on this island: <yellow>" + plugin.minions().count(island.data().getId()));
        for (MinionData data : plugin.minions().ofIsland(island.data().getId())) Msg.send(p, "<gray>- <yellow>" + data.type().displayName() + " <gray>" + data.status().displayName());
    }
    private void removeAll(CommandSender sender, String label, String[] args) {
        if (!admin(sender)) { sender.sendMessage("No permission."); return; }
        if (args.length < 2) { sender.sendMessage("Usage: /" + label + " removeall <player|islandId>"); return; }
        sender.sendMessage("Removed " + plugin.minions().removeForPlayerOrIsland(args[1]) + " minion(s).");
    }
    private void outputs(CommandSender sender, String[] args) {
        if (!(sender instanceof Player p) || !admin(sender)) return;
        if (args.length >= 2) {
            MinionType type = MinionType.byId(args[1]);
            if (type != null) { new MinionOutputEditorGui(plugin, type).open(p); return; }
        }
        new MinionOutputAdminGui(plugin).open(p);
    }
    private void reload(CommandSender sender) {
        if (!admin(sender)) { sender.sendMessage("No permission."); return; }
        plugin.minions().reloadSettings();
        sender.sendMessage("Reloaded minion settings.");
    }
    private void debug(CommandSender sender, String label, String[] args) {
        if (!admin(sender)) { sender.sendMessage("No permission."); return; }
        if (!(sender instanceof Player p)) { sender.sendMessage("Players only."); return; }
        if (args.length < 2) { sender.sendMessage("Usage: /" + label + " debug <player>"); return; }
        org.bukkit.OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        new MinionDebugGui(plugin, target.getUniqueId()).open(p);
    }
    private void listTypes(CommandSender sender) {
        for (MinionType type : MinionType.values()) {
            sender.sendMessage(type.id() + " - " + type.displayName() + " - " + type.shopPrice(plugin) + " coins");
        }
    }
    @Override public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) return filter(List.of("overview", "shop", "buy", "help", "types", "reload", "debug", "give", "list", "removeall", "outputs", "admin"), args[0]);
        if (args.length == 2 && (args[0].equalsIgnoreCase("give") || args[0].equalsIgnoreCase("removeall") || args[0].equalsIgnoreCase("debug"))) return Bukkit.getOnlinePlayers().stream().map(Player::getName).filter(n -> n.toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT))).collect(Collectors.toList());
        if (args.length == 3 && args[0].equalsIgnoreCase("give")) return filter(Arrays.stream(MinionType.values()).map(MinionType::id).collect(Collectors.toList()), args[2]);
        if (args.length == 2 && (args[0].equalsIgnoreCase("outputs") || args[0].equalsIgnoreCase("admin"))) return filter(Arrays.stream(MinionType.values()).map(MinionType::id).collect(Collectors.toList()), args[1]);
        return Collections.emptyList();
    }
    private List<String> filter(List<String> values, String prefix) { String p = prefix.toLowerCase(Locale.ROOT); List<String> out = new ArrayList<>(); for (String value : values) if (value.startsWith(p)) out.add(value); return out; }
}
