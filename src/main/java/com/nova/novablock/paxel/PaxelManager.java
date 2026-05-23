package com.nova.novablock.paxel;

import com.nova.novablock.NovaBlock;
import com.nova.novablock.island.Island;
import com.nova.novablock.phase.Phase;
import com.nova.novablock.util.ItemBuilder;
import com.nova.novablock.util.Msg;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.Tool;
import io.papermc.paper.registry.RegistryKey;
import io.papermc.paper.registry.TypedKey;
import io.papermc.paper.registry.set.RegistryKeySet;
import io.papermc.paper.registry.set.RegistrySet;
import net.kyori.adventure.util.TriState;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.block.BlockType;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The Paxel: a shovel/axe/pickaxe in one. Player-bound, soulbound, unbreakable.
 * Cannot be dropped, traded, moved out of the player's inventory, or lost on death.
 *
 * <p>Tier is bound to the player's island phase — completing phase 0 unlocks Stone,
 * phase 1 unlocks Iron, etc. (capped at Netherite when reaching phase 5).
 *
 * <p>Abilities:
 *  - <b>Universal tool</b>: the {@code minecraft:tool} data component declares
 *    the paxel as correct-for-drops on the pickaxe, axe, shovel and hoe block
 *    tags, with mining speed scaling per tier. This is what actually makes one
 *    item break dirt, wood, sand, leaves and stone at full speed — Haste alone
 *    can't overcome vanilla's wrong-tool 5× slowdown.
 *  - <b>Auto-smelt</b>: cobble→stone, raw ore→ingot, ancient debris→netherite scrap.
 *  - Persistent action-bar HUD showing tier + phase progress while held.
 */
public class PaxelManager implements Listener {

    public static final NamespacedKey PAXEL_OWNER = new NamespacedKey("novablock", "paxel_owner");
    public static final NamespacedKey PAXEL_TIER = new NamespacedKey("novablock", "paxel_tier");
    public static final NamespacedKey PAXEL_VERSION_KEY = new NamespacedKey("novablock", "paxel_version");
    /** Bump when build() changes in a way that needs to invalidate existing paxels in inventories. */
    public static final int PAXEL_VERSION = 1;

    private static final Material[] TIER_MATERIALS = {
            Material.WOODEN_PICKAXE,
            Material.STONE_PICKAXE,
            Material.IRON_PICKAXE,
            Material.GOLDEN_PICKAXE,
            Material.DIAMOND_PICKAXE,
            Material.NETHERITE_PICKAXE
    };
    private static final String[] TIER_NAMES = {
            "Wooden Paxel", "Stone Paxel", "Iron Paxel", "Gold Paxel", "Diamond Paxel", "Netherite Paxel"
    };
    private static final String[] TIER_COLORS = {
            "#A0793A", "#8C8C8C", "#E0E0E0", "#FFD24D", "#7FFFE0", "#5A2A6A"
    };

    /** Cobble/raw-ore → smelted-result mapping for the auto-smelt ability. */
    private static final Map<Material, Material> SMELT_MAP = Map.ofEntries(
            Map.entry(Material.COBBLESTONE, Material.STONE),
            Map.entry(Material.COBBLED_DEEPSLATE, Material.DEEPSLATE),
            Map.entry(Material.RAW_IRON, Material.IRON_INGOT),
            Map.entry(Material.RAW_GOLD, Material.GOLD_INGOT),
            Map.entry(Material.RAW_COPPER, Material.COPPER_INGOT),
            Map.entry(Material.ANCIENT_DEBRIS, Material.NETHERITE_SCRAP),
            Map.entry(Material.SAND, Material.GLASS),
            Map.entry(Material.RED_SAND, Material.RED_STAINED_GLASS),
            Map.entry(Material.CLAY_BALL, Material.BRICK),
            Map.entry(Material.WET_SPONGE, Material.SPONGE)
    );

    private final NovaBlock plugin;

    public PaxelManager(NovaBlock plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        startHudTicker();
    }

    // ---------------- tiering ----------------

    /** Tier this player has earned based on their island's current phase index. */
    public int tierFor(Player p) {
        Island island = plugin.islands().ofPlayer(p);
        if (island == null) return 0;
        int phase = island.data().getPhaseIndex();
        return Math.max(0, Math.min(TIER_MATERIALS.length - 1, phase));
    }

    /** Build a fresh paxel item for the given player at the given tier. */
    public ItemStack build(Player owner, int tier) {
        tier = Math.max(0, Math.min(TIER_MATERIALS.length - 1, tier));
        Material mat = TIER_MATERIALS[tier];
        ItemStack stack = ItemBuilder.of(mat).build();
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Msg.mm("<" + TIER_COLORS[tier] + "><bold>" + TIER_NAMES[tier]
                + " <gray>(" + owner.getName() + ")")
                .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
        List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
        lore.add(Msg.mm("<gray>Shovel · Axe · Pickaxe in one.").decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
        lore.add(Msg.mm("<gray>Tier <yellow>" + (tier + 1) + "/" + TIER_MATERIALS.length).decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
        lore.add(Msg.mm("").decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
        lore.add(Msg.mm("<aqua>Abilities").decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
        lore.add(Msg.mm("<gray>· Mines pickaxe + axe + shovel + hoe blocks").decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
        lore.add(Msg.mm("<gray>· Auto-smelt cobble & ores").decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
        lore.add(Msg.mm("<gray>· Tiers up with your island phase").decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
        lore.add(Msg.mm("").decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
        lore.add(Msg.mm("<gold>★ Soulbound to " + owner.getName()).decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
        lore.add(Msg.mm("<dark_gray>Cannot be dropped or traded.").decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
        meta.lore(lore);
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES);
        meta.addEnchant(Enchantment.EFFICIENCY, Math.min(5, 2 + tier), true);
        if (tier >= 2) meta.addEnchant(Enchantment.FORTUNE, Math.min(3, tier - 1), true);
        meta.addEnchant(Enchantment.UNBREAKING, 3, true);
        meta.addEnchant(Enchantment.MENDING, 1, true);
        meta.getPersistentDataContainer().set(PAXEL_OWNER, PersistentDataType.STRING, owner.getUniqueId().toString());
        meta.getPersistentDataContainer().set(PAXEL_TIER, PersistentDataType.INTEGER, tier);
        meta.getPersistentDataContainer().set(PAXEL_VERSION_KEY, PersistentDataType.INTEGER, PAXEL_VERSION);
        if (meta instanceof Damageable d) d.setDamage(0);
        stack.setItemMeta(meta);

        // Universal-tool data component — what actually makes one item break dirt,
        // wood, sand and stone at full speed. Without this, the pickaxe Material
        // would still get vanilla's 5× wrong-tool slowdown on shovel/axe blocks
        // and Haste alone can't dig it out of that hole.
        float speed = 4f + tier * 2f;   // tier 0 → 4, tier 5 → 14 (faster than gold pickaxe at top tier)
        List<Tool.Rule> rules = new ArrayList<>();
        rules.add(Tool.rule(blocksFromTag(Tag.MINEABLE_PICKAXE), speed, TriState.TRUE));
        rules.add(Tool.rule(blocksFromTag(Tag.MINEABLE_AXE), speed, TriState.TRUE));
        rules.add(Tool.rule(blocksFromTag(Tag.MINEABLE_SHOVEL), speed, TriState.TRUE));
        rules.add(Tool.rule(blocksFromTag(Tag.MINEABLE_HOE), speed, TriState.TRUE));
        stack.setData(DataComponentTypes.TOOL, Tool.tool()
                .addRules(rules)
                .defaultMiningSpeed(1.5f)
                .damagePerBlock(0)
                .build());

        return stack;
    }

    private static RegistryKeySet<BlockType> blocksFromTag(Tag<Material> tag) {
        List<TypedKey<BlockType>> keys = new ArrayList<>(tag.getValues().size());
        for (Material m : tag.getValues()) {
            keys.add(TypedKey.create(RegistryKey.BLOCK, m.getKey()));
        }
        return RegistrySet.keySet(RegistryKey.BLOCK, keys);
    }

    public boolean isPaxel(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(PAXEL_OWNER, PersistentDataType.STRING);
    }

    public boolean isOwner(ItemStack item, Player p) {
        if (!isPaxel(item)) return false;
        String owner = item.getItemMeta().getPersistentDataContainer().get(PAXEL_OWNER, PersistentDataType.STRING);
        return p.getUniqueId().toString().equals(owner);
    }

    public int tierOf(ItemStack item) {
        if (!isPaxel(item)) return -1;
        Integer t = item.getItemMeta().getPersistentDataContainer().get(PAXEL_TIER, PersistentDataType.INTEGER);
        return t == null ? 0 : t;
    }

    /** Returns the recorded version of an existing paxel, or 0 if the key is missing (legacy paxel). */
    public int versionOf(ItemStack item) {
        if (!isPaxel(item)) return -1;
        Integer v = item.getItemMeta().getPersistentDataContainer().get(PAXEL_VERSION_KEY, PersistentDataType.INTEGER);
        return v == null ? 0 : v;
    }

    /** Auto-smelt drop transform. Returns a new ItemStack of the smelted material, or the input unchanged. */
    public ItemStack maybeSmelt(ItemStack drop) {
        if (drop == null) return drop;
        Material to = SMELT_MAP.get(drop.getType());
        if (to == null) return drop;
        return new ItemStack(to, drop.getAmount());
    }

    /** Give the player a paxel if they don't already have one. */
    public void give(Player p) {
        PlayerInventory inv = p.getInventory();
        for (ItemStack it : inv.getContents()) {
            if (isOwner(it, p)) return;
        }
        ItemStack paxel = build(p, tierFor(p));
        inv.addItem(paxel);
        Msg.actionBar(p, "<gold>Your Paxel is in your inventory.");
    }

    /**
     * Check the player's inventory for a paxel and rebuild it if:
     *  - the tier is behind the phase (phase-up upgrade), OR
     *  - the version is older than {@link #PAXEL_VERSION} (legacy paxel from
     *    before a behaviour change; rebuilding picks up the new tool component,
     *    auto-smelt mapping, etc.).
     */
    public void refreshTier(Player p) {
        PlayerInventory inv = p.getInventory();
        int target = tierFor(p);
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack it = inv.getItem(i);
            if (!isOwner(it, p)) continue;
            int current = tierOf(it);
            int version = versionOf(it);
            boolean tierBehind = current < target;
            boolean outdated = version < PAXEL_VERSION;
            if (tierBehind || outdated) {
                inv.setItem(i, build(p, target));
                if (tierBehind) {
                    Msg.title(p, "<gold>Paxel Upgraded", "<" + TIER_COLORS[target] + ">" + TIER_NAMES[target]);
                    p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_USE, 0.8f, 1.4f);
                }
            }
            return;
        }
        // No paxel in inventory: re-issue one.
        give(p);
    }

    /** Force-replace any existing paxel with a fresh one at the player's current tier. Used by /obadmin givepaxel. */
    public void replace(Player p) {
        PlayerInventory inv = p.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack it = inv.getItem(i);
            if (isOwner(it, p)) inv.setItem(i, null);
        }
        give(p);
    }

    /** Called by BlockListener after each break — currently a no-op (tier is phase-driven now). */
    public void onMine(Player p, Material broken) {
        // tier upgrades happen on phase-up; nothing to do per-break.
    }

    // ---------------- HUD ticker ----------------

    /**
     * Every second, send an action-bar HUD with paxel tier + phase progress for
     * each online player holding their paxel. Transient messages (combo, +XP)
     * still pop briefly over the persistent line.
     */
    private void startHudTicker() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                ItemStack main = p.getInventory().getItemInMainHand();
                if (!isOwner(main, p)) continue;
                int tier = tierOf(main);

                Island island = plugin.islands().ofPlayer(p);
                String hud;
                if (island == null) {
                    hud = "<" + TIER_COLORS[tier] + ">" + TIER_NAMES[tier]
                            + " <gray>· <yellow>No island";
                } else {
                    Phase phase = plugin.phases().getOrLast(island.data().getPhaseIndex());
                    int prog = island.data().getPhaseProgress();
                    int req = phase == null ? 1 : phase.getRequiredBlocks();
                    String phaseName = phase == null ? "?" : phase.getDisplayName();
                    String phaseColor = phase == null ? "white" : phase.getThemeColor();
                    hud = "<" + TIER_COLORS[tier] + ">" + TIER_NAMES[tier]
                            + " <gray>· <" + phaseColor + ">" + phaseName
                            + " <white>" + prog + "<gray>/<white>" + req;
                }
                Msg.actionBar(p, hud);
            }
        }, 20L, 20L);
    }

    // ---------------- listeners: lock it down ----------------

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        // Slight delay so progression cache is warm.
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (event.getPlayer().isOnline()) {
                give(event.getPlayer());
                refreshTier(event.getPlayer());
            }
        }, 25L);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (event.getPlayer().isOnline()) give(event.getPlayer());
        }, 5L);
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        if (isPaxel(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
            Msg.actionBar(event.getPlayer(), "<red>You can't drop the Paxel.");
        }
    }

    /** Prevent moving the paxel into another inventory (chests, ender chests, etc.). */
    @EventHandler
    public void onClick(InventoryClickEvent event) {
        ItemStack moved = event.getCurrentItem();
        ItemStack cursor = event.getCursor();
        boolean blocked = false;
        if (event.getClickedInventory() != null
                && event.getClickedInventory().getType() != InventoryType.PLAYER
                && event.getClickedInventory().getType() != InventoryType.CRAFTING) {
            if (isPaxel(cursor) || isPaxel(moved)) blocked = true;
        }
        if (event.isShiftClick() && isPaxel(moved)
                && event.getView().getTopInventory().getType() != InventoryType.CRAFTING
                && event.getView().getTopInventory().getType() != InventoryType.PLAYER) {
            blocked = true;
        }
        if (blocked) {
            event.setCancelled(true);
            if (event.getWhoClicked() instanceof Player p) Msg.actionBar(p, "<red>The Paxel stays with you.");
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        ItemStack item = event.getOldCursor();
        if (!isPaxel(item)) return;
        for (int slot : event.getRawSlots()) {
            if (slot >= event.getView().getTopInventory().getSize()
                    && event.getView().getTopInventory().getType() == InventoryType.CRAFTING) continue;
            if (slot < event.getView().getTopInventory().getSize()
                    && event.getView().getTopInventory().getType() != InventoryType.PLAYER
                    && event.getView().getTopInventory().getType() != InventoryType.CRAFTING) {
                event.setCancelled(true);
                return;
            }
        }
    }

    /** Prevent the paxel item from appearing as a death drop. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(PlayerDeathEvent event) {
        event.getDrops().removeIf(this::isPaxel);
    }

    /** Stop other players picking up a stray paxel (shouldn't happen, but belt and braces). */
    @EventHandler
    public void onPickup(EntityPickupItemEvent event) {
        ItemStack stack = event.getItem().getItemStack();
        if (!isPaxel(stack)) return;
        if (event.getEntity() instanceof Player p && isOwner(stack, p)) return;
        event.setCancelled(true);
    }
}
