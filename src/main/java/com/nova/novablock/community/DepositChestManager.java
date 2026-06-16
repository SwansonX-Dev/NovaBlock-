package com.nova.novablock.community;

import com.nova.novablock.NovaBlock;
import com.nova.novablock.compat.ClaimBridge;
import com.nova.novablock.progression.PlayerProgression;
import com.nova.novablock.util.ItemBuilder;
import com.nova.novablock.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Per-player "deposit chest" for the Community OneBlock: players link a chest on
 * their own community claim and mined community drops auto-deposit into it,
 * relieving the inventory pressure from the high-variety community pool.
 *
 * <p>Linking is deliberate: {@code /ob depositchest} hands out a tagged linking
 * tool ({@link #buildLinkTool()}); clicking a chest/barrel you own on a community
 * claim with it sets that container as your deposit chest (mirrors the community
 * branch of {@link com.nova.novablock.minion.MinionManager}'s chest link). The
 * link is stored on the player's {@link PlayerProgression} so it survives relogs.
 *
 * <p>{@link #deposit(Player, ItemStack)} is the routing entry point used by
 * {@link CommunityBlock#onBreak}: it returns whatever the chest couldn't take so
 * the caller can fall back to today's inventory/ground behaviour. A throttled
 * action-bar warning fires when the chest is full or unavailable.
 */
public class DepositChestManager implements Listener {

    public static final NamespacedKey TOOL_KEY = new NamespacedKey("novablock", "community_deposit_tool");
    /** Don't spam the "chest full" warning on every block break. */
    private static final long WARN_COOLDOWN_MS = 10_000L;

    private final NovaBlock plugin;
    private final Map<UUID, Long> lastWarn = new HashMap<>();

    public DepositChestManager(NovaBlock plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    // ---------------- link tool ----------------

    public ItemStack buildLinkTool() {
        ItemStack stack = ItemBuilder.of(Material.RECOVERY_COMPASS)
                .name("<#5DDEF4><bold>Deposit Chest Linker")
                .lore("<gray>Click a chest or barrel on your",
                        "<gray>community claim to link it.",
                        "<gray>Community OneBlock drops will then",
                        "<gray>auto-deposit into that chest.",
                        "<dark_gray>Consumed on use.")
                .build();
        ItemMeta meta = stack.getItemMeta();
        meta.getPersistentDataContainer().set(TOOL_KEY, PersistentDataType.BYTE, (byte) 1);
        stack.setItemMeta(meta);
        return stack;
    }

    public boolean isLinkTool(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(TOOL_KEY, PersistentDataType.BYTE);
    }

    /** Give the player one linking tool with usage instructions. */
    public void giveLinkTool(Player p) {
        for (ItemStack overflow : p.getInventory().addItem(buildLinkTool()).values()) {
            p.getWorld().dropItemNaturally(p.getLocation(), overflow);
        }
        Msg.send(p, "<#5DDEF4>Deposit Chest Linker <gray>given. Click a chest on your "
                + "community claim to link it — mined community drops will auto-deposit there.");
    }

    private boolean isContainer(Material m) {
        return m == Material.CHEST || m == Material.TRAPPED_CHEST || m == Material.BARREL;
    }

    private boolean isCommunityWorld(World world) {
        if (world == null || plugin.community() == null) return false;
        return world.getName().equals(plugin.community().communityWorldName());
    }

    // ---------------- linking ----------------

    @EventHandler
    public void onLink(PlayerInteractEvent event) {
        if (event.getAction() != Action.LEFT_CLICK_BLOCK && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (!isLinkTool(event.getItem())) return;
        event.setCancelled(true);
        Player p = event.getPlayer();
        Block block = event.getClickedBlock();
        if (block == null || !isContainer(block.getType())) {
            Msg.send(p, "<red>Click a chest or barrel to link it.");
            return;
        }
        if (!isCommunityWorld(block.getWorld())
                || !ClaimBridge.ownsClaimAt(p, block.getLocation())) {
            Msg.send(p, "<red>Link a chest on your own community claim.");
            return;
        }
        Location loc = block.getLocation();
        plugin.progression().get(p).setDepositChest(loc.getWorld().getName(),
                loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
        plugin.progression().save(p.getUniqueId());
        consumeOneTool(p);
        Msg.send(p, "<green>Deposit chest linked! <gray>Community OneBlock drops will auto-deposit here.");
        p.playSound(p.getLocation(), Sound.BLOCK_CHEST_LOCKED, 0.7f, 1.6f);
    }

    private void consumeOneTool(Player p) {
        var inv = p.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (!isLinkTool(s)) continue;
            int amt = s.getAmount();
            if (amt <= 1) inv.setItem(i, null);
            else s.setAmount(amt - 1);
            return;
        }
    }

    /** Forget the link if the player breaks their own linked deposit chest. */
    @EventHandler(ignoreCancelled = true)
    public void onBreakChest(BlockBreakEvent event) {
        Block b = event.getBlock();
        if (!isContainer(b.getType())) return;
        PlayerProgression prog = plugin.progression().get(event.getPlayer());
        if (!prog.hasDepositChest()) return;
        Location loc = b.getLocation();
        if (loc.getWorld() == null || !loc.getWorld().getName().equals(prog.getDepositChestWorld())) return;
        if (loc.getBlockX() != prog.getDepositChestX()
                || loc.getBlockY() != prog.getDepositChestY()
                || loc.getBlockZ() != prog.getDepositChestZ()) return;
        prog.clearDepositChest();
        plugin.progression().save(event.getPlayer().getUniqueId());
        Msg.actionBar(event.getPlayer(), "<gray>Deposit chest unlinked.");
    }

    // ---------------- deposit routing ----------------

    /**
     * Try to deposit {@code item} into the player's linked deposit chest.
     *
     * @return {@code null} if it was fully deposited; the leftover stack if the
     *         player has no chest, the chest is unavailable (missing/unloaded/
     *         no longer owned), or the chest is full. The caller routes the
     *         leftover through its normal inventory/ground fallback.
     */
    public ItemStack deposit(Player p, ItemStack item) {
        if (item == null || item.getType().isAir()) return null;
        PlayerProgression prog = plugin.progression().get(p);
        if (!prog.hasDepositChest()) return item;

        World world = Bukkit.getWorld(prog.getDepositChestWorld());
        if (world == null) return item; // world not loaded — silently fall back
        int x = prog.getDepositChestX(), y = prog.getDepositChestY(), z = prog.getDepositChestZ();
        if (!world.isChunkLoaded(x >> 4, z >> 4)) { warn(p); return item; }
        Block block = world.getBlockAt(x, y, z);
        if (!isContainer(block.getType()) || !(block.getState() instanceof Container container)) {
            warn(p);
            return item;
        }
        if (!ClaimBridge.ownsClaimAt(p, block.getLocation())) { warn(p); return item; }

        Map<Integer, ItemStack> overflow = container.getInventory().addItem(item.clone());
        if (overflow.isEmpty()) return null;
        warn(p);
        return overflow.values().iterator().next();
    }

    private void warn(Player p) {
        long now = System.currentTimeMillis();
        Long last = lastWarn.get(p.getUniqueId());
        if (last != null && now - last < WARN_COOLDOWN_MS) return;
        lastWarn.put(p.getUniqueId(), now);
        Msg.actionBar(p, "<red>Deposit chest full! <gray>Empty it to keep auto-depositing.");
    }
}
