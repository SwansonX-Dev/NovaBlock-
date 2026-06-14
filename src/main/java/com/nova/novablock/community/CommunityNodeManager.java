package com.nova.novablock.community;

import com.nova.novablock.NovaBlock;
import com.nova.novablock.listener.OneBlockGrantListener;
import com.nova.novablock.util.Msg;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Personal "OneBlock" resource nodes placed on a player's Community OneBlock
 * claim via the Personal OneBlock grant item. A node is a single regenerating
 * block of a chosen {@link CommunityNodeType}: whoever mines it gets either the
 * block's drops or the block itself (per the type's drop mode) plus a small coin
 * reward, and it rerolls into a new material from that type's pool. No phases —
 * a passive personal resource spot, separate from the shared community block and
 * from personal islands.
 */
public final class CommunityNodeManager implements Listener {

    private final NovaBlock plugin;
    private final File file;
    /** locationKey -> node */
    private final Map<String, Node> nodes = new HashMap<>();

    private record Node(UUID owner, CommunityNodeType type) {}

    public CommunityNodeManager(NovaBlock plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "community-nodes.yml");
        load();
    }

    private static String key(Location loc) {
        return loc.getWorld().getName() + "," + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ();
    }

    public int count(UUID owner) {
        int n = 0;
        for (Node node : nodes.values()) if (node.owner().equals(owner)) n++;
        return n;
    }

    public int limit(Player player) {
        if (player.hasPermission("novablock.admin")) return Integer.MAX_VALUE;
        return Math.max(1, plugin.getConfig().getInt("community.personal-oneblock.limit", 3));
    }

    /** Turn {@code block} into a regenerating node of {@code type} owned by {@code owner}. */
    public void place(Player owner, Block block, CommunityNodeType type) {
        block.setType(plugin.nodePools().roll(type, ThreadLocalRandom.current()), false);
        nodes.put(key(block.getLocation()), new Node(owner.getUniqueId(), type));
        save();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        String locKey = key(block.getLocation());
        Node node = nodes.get(locKey);
        if (node == null) return;
        Player p = event.getPlayer();

        // Owner sneak-breaks to reclaim: node removed (no regen), grant item returned.
        if (p.isSneaking() && node.owner().equals(p.getUniqueId())) {
            nodes.remove(locKey);
            save();
            event.setDropItems(false);
            for (ItemStack leftover : p.getInventory().addItem(OneBlockGrantListener.create(plugin, node.type(), 1)).values()) {
                p.getWorld().dropItemNaturally(p.getLocation(), leftover);
            }
            Msg.send(p, "<gray>Personal OneBlock reclaimed — your grant item was returned.");
            return;
        }

        // Reward: either the block's natural drops (mining) or the block itself
        // (building/decoration types). Suppress the vanilla drop in both cases.
        // Each break yields a configurable surplus quantity so stock builds fast.
        event.setDropItems(false);
        int qty = surplusQuantity();
        Collection<ItemStack> reward;
        if (node.type().dropMode() == CommunityNodeType.DropMode.BLOCK && block.getType().isItem()) {
            reward = List.of(new ItemStack(block.getType(), qty));
        } else {
            reward = block.getDrops(p.getInventory().getItemInMainHand(), p);
            if (qty > 1) for (ItemStack drop : reward) drop.setAmount(drop.getAmount() * qty);
        }
        for (ItemStack drop : reward) {
            for (ItemStack leftover : p.getInventory().addItem(drop).values()) {
                p.getWorld().dropItemNaturally(p.getLocation(), leftover);
            }
        }

        long coins = Math.max(0L, plugin.getConfig().getLong("community.personal-oneblock.coins-per-break", 2L));
        if (coins > 0) {
            var island = plugin.islands().ofPlayer(p);
            if (island != null) plugin.economy().award(island, coins);
            else plugin.economy().deposit(p, coins);
        }

        CommunityNodeType type = node.type();
        Location loc = block.getLocation();
        plugin.getServer().getScheduler().runTask(plugin,
                () -> loc.getBlock().setType(plugin.nodePools().roll(type, ThreadLocalRandom.current()), false));
    }

    /** Random surplus stack size per break, from {@code community.personal-oneblock.drops-min/max}. */
    private int surplusQuantity() {
        int min = Math.max(1, plugin.getConfig().getInt("community.personal-oneblock.drops-min", 2));
        int max = Math.max(min, plugin.getConfig().getInt("community.personal-oneblock.drops-max", 4));
        return min == max ? min : ThreadLocalRandom.current().nextInt(min, max + 1);
    }

    public void load() {
        nodes.clear();
        if (!file.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        for (String entry : y.getStringList("nodes")) {
            // Format: "owner;typeId;world,x,y,z"  (old format "owner;world,x,y,z" -> MINING)
            String[] parts = entry.split(";", 3);
            try {
                if (parts.length == 3) {
                    CommunityNodeType type = CommunityNodeType.byId(parts[1]);
                    nodes.put(parts[2], new Node(UUID.fromString(parts[0]),
                            type == null ? CommunityNodeType.MINING : type));
                } else if (parts.length == 2) {
                    nodes.put(parts[1], new Node(UUID.fromString(parts[0]), CommunityNodeType.MINING));
                }
            } catch (IllegalArgumentException ignored) {}
        }
    }

    public void save() {
        YamlConfiguration y = new YamlConfiguration();
        List<String> list = new ArrayList<>();
        for (Map.Entry<String, Node> e : nodes.entrySet()) {
            list.add(e.getValue().owner() + ";" + e.getValue().type().id() + ";" + e.getKey());
        }
        y.set("nodes", list);
        try { y.save(file); }
        catch (IOException ex) { plugin.getLogger().warning("Failed to save community-nodes.yml: " + ex.getMessage()); }
    }
}
