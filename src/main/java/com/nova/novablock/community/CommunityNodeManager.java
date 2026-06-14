package com.nova.novablock.community;

import com.nova.novablock.NovaBlock;
import com.nova.novablock.listener.OneBlockGrantListener;
import com.nova.novablock.util.Msg;
import org.bukkit.Location;
import org.bukkit.Material;
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
 * block: whoever mines it (the owner, or anyone they trust on the claim) gets
 * the block's drops plus a small coin reward, and it rerolls into a new
 * resource. No phases — a passive personal resource spot, separate from the
 * shared community block and from personal islands.
 */
public final class CommunityNodeManager implements Listener {

    private final NovaBlock plugin;
    private final File file;
    /** locationKey -> owner UUID */
    private final Map<String, UUID> owners = new HashMap<>();

    /** Weighted resource pool: {material, weight}. */
    private static final Object[][] POOL = {
            {Material.STONE, 60},
            {Material.COAL_ORE, 22},
            {Material.COPPER_ORE, 16},
            {Material.IRON_ORE, 16},
            {Material.GOLD_ORE, 8},
            {Material.REDSTONE_ORE, 8},
            {Material.LAPIS_ORE, 6},
            {Material.DIAMOND_ORE, 3},
            {Material.EMERALD_ORE, 1},
    };
    private static final int TOTAL_WEIGHT;
    static {
        int sum = 0;
        for (Object[] entry : POOL) sum += (int) entry[1];
        TOTAL_WEIGHT = sum;
    }

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
        for (UUID o : owners.values()) if (o.equals(owner)) n++;
        return n;
    }

    public int limit(Player player) {
        if (player.hasPermission("novablock.admin")) return Integer.MAX_VALUE;
        return Math.max(1, plugin.getConfig().getInt("community.personal-oneblock.limit", 3));
    }

    /** Turn {@code block} into a regenerating resource node owned by {@code owner}. */
    public void place(Player owner, Block block) {
        block.setType(rollMaterial(), false);
        owners.put(key(block.getLocation()), owner.getUniqueId());
        save();
    }

    private Material rollMaterial() {
        int roll = ThreadLocalRandom.current().nextInt(TOTAL_WEIGHT);
        int acc = 0;
        for (Object[] entry : POOL) {
            acc += (int) entry[1];
            if (roll < acc) return (Material) entry[0];
        }
        return Material.STONE;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        String locKey = key(block.getLocation());
        UUID owner = owners.get(locKey);
        if (owner == null) return;
        Player p = event.getPlayer();

        // Owner sneak-breaks to reclaim the node: it's removed (no regen) and the
        // grant item is returned so they can place it elsewhere.
        if (p.isSneaking() && owner.equals(p.getUniqueId())) {
            owners.remove(locKey);
            save();
            event.setDropItems(false);
            for (ItemStack leftover : p.getInventory().addItem(OneBlockGrantListener.create(plugin, 1)).values()) {
                p.getWorld().dropItemNaturally(p.getLocation(), leftover);
            }
            Msg.send(p, "<gray>Personal OneBlock reclaimed — your grant item was returned.");
            return;
        }

        // Hand the breaker the block's drops, suppress the vanilla drop, award a
        // small coin reward, then regenerate the node next tick.
        ItemStack tool = p.getInventory().getItemInMainHand();
        Collection<ItemStack> drops = block.getDrops(tool, p);
        event.setDropItems(false);
        for (ItemStack drop : drops) {
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
        Location loc = block.getLocation();
        plugin.getServer().getScheduler().runTask(plugin, () -> loc.getBlock().setType(rollMaterial(), false));
    }

    public void load() {
        owners.clear();
        if (!file.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        for (String entry : y.getStringList("nodes")) {
            int sep = entry.indexOf(';');
            if (sep < 0) continue;
            try { owners.put(entry.substring(sep + 1), UUID.fromString(entry.substring(0, sep))); }
            catch (IllegalArgumentException ignored) {}
        }
    }

    public void save() {
        YamlConfiguration y = new YamlConfiguration();
        List<String> list = new ArrayList<>();
        for (Map.Entry<String, UUID> e : owners.entrySet()) list.add(e.getValue() + ";" + e.getKey());
        y.set("nodes", list);
        try { y.save(file); }
        catch (IOException ex) { plugin.getLogger().warning("Failed to save community-nodes.yml: " + ex.getMessage()); }
    }
}
