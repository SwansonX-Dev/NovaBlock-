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
import java.util.concurrent.ThreadLocalRandom;

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
    public static final int PAXEL_VERSION = 4;

    /** Ores eligible for vein-mining. The OneBlock center is always excluded regardless of type. */
    private static final java.util.Set<Material> ORES = java.util.Set.of(
            Material.COAL_ORE, Material.DEEPSLATE_COAL_ORE,
            Material.IRON_ORE, Material.DEEPSLATE_IRON_ORE,
            Material.GOLD_ORE, Material.DEEPSLATE_GOLD_ORE, Material.NETHER_GOLD_ORE,
            Material.DIAMOND_ORE, Material.DEEPSLATE_DIAMOND_ORE,
            Material.EMERALD_ORE, Material.DEEPSLATE_EMERALD_ORE,
            Material.LAPIS_ORE, Material.DEEPSLATE_LAPIS_ORE,
            Material.REDSTONE_ORE, Material.DEEPSLATE_REDSTONE_ORE,
            Material.COPPER_ORE, Material.DEEPSLATE_COPPER_ORE,
            Material.NETHER_QUARTZ_ORE,
            Material.ANCIENT_DEBRIS);

    /** Hard cap on vein-mine cascade so a misuse doesn't lag the server. */
    private static final int VEIN_MINE_LIMIT = 16;

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
    private org.bukkit.scheduler.BukkitTask hudTask;

    public PaxelManager(NovaBlock plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        startHudTicker();
    }

    public void shutdown() {
        if (hudTask != null) { hudTask.cancel(); hudTask = null; }
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
        lore.add(Msg.mm("<gray>· Telekinesis — drops + XP go to your inventory").decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
        lore.add(Msg.mm("<gray>· Vein-mine ores (up to 16 connected)").decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
        lore.add(Msg.mm("<gray>· Tiers up with your island phase").decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
        lore.add(Msg.mm("").decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
        lore.add(Msg.mm("<gold>★ Soulbound to " + owner.getName()).decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
        lore.add(Msg.mm("<dark_gray>Cannot be dropped or traded.").decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
        meta.lore(lore);
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES);
        meta.addEnchant(Enchantment.EFFICIENCY, Math.min(5, 1 + tier), true);
        if (tier >= 2) meta.addEnchant(Enchantment.FORTUNE, Math.min(3, tier - 1), true);
        meta.addEnchant(Enchantment.UNBREAKING, 3, true);
        if (tier >= 3) meta.addEnchant(Enchantment.MENDING, 1, true);
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

    /** True when the player is currently inside the OG OneBlock world — NovaBlock should
     *  leave their inventory alone there (OG OneBlock issues its own paxel). */
    private boolean isInOgWorld(Player p) {
        var ogPlugin = Bukkit.getPluginManager().getPlugin("OGOneBlock");
        if (ogPlugin == null || !ogPlugin.isEnabled()) return false;
        String ogWorld = ogPlugin.getConfig().getString("world.name", "OGOBworld");
        return p.getWorld().getName().equals(ogWorld);
    }

    /** Give the player a paxel if they don't already have one in inventory, ender chest, or island storage. */
    public void give(Player p) {
        if (isInOgWorld(p)) return;
        PlayerInventory inv = p.getInventory();
        for (ItemStack it : inv.getContents()) {
            if (isOwner(it, p)) return;
        }
        for (ItemStack it : p.getEnderChest().getContents()) {
            if (isOwner(it, p)) return;
        }
        // Island storage check — avoid handing out a second paxel if they stashed theirs.
        var island = plugin.islands().ofPlayer(p);
        if (island != null) {
            var holder = plugin.islandStorage().peekInventory(island);
            if (holder != null) {
                for (ItemStack it : holder.getContents()) {
                    if (isOwner(it, p)) return;
                }
            }
        }
        ItemStack paxel = build(p, tierFor(p));
        inv.addItem(paxel);
        Msg.actionBar(p, "<gold>Your Paxel is in your inventory.");
    }

    /**
     * Upgrade the player's existing paxel in-place if:
     *  - the tier is behind the phase (phase-up upgrade), OR
     *  - the version is older than {@link #PAXEL_VERSION}.
     *
     * <p>Does NOT auto-issue a new paxel — that would dup if the player stashed
     * their paxel in island storage. Use {@link #give(Player)} to issue.
     */
    public void refreshTier(Player p) {
        if (isInOgWorld(p)) return;
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
        hudTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
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

    // ---------------- break handler: telekinesis + vein-mine ----------------

    /**
     * Runs at HIGHEST priority with ignoreCancelled so every other handler
     * (loot-room protection, visitor-build flag, OneBlock-center take-over at
     * HIGH, anti-grief plugins) has already either claimed or cancelled the
     * break by the time we get here. Without that ordering, telekinesis could
     * fire on a break a later handler then cancels — giving the player drops
     * while the block stays in place (a real duplication vector inside loot
     * rooms and on visited islands).
     *
     * <p>BlockListener at HIGH still owns OneBlock-center regen; we explicitly
     * skip the center here so it doesn't double-handle.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPaxelBreak(org.bukkit.event.block.BlockBreakEvent event) {
        Player p = event.getPlayer();
        ItemStack tool = p.getInventory().getItemInMainHand();
        if (!isOwner(tool, p)) return;
        org.bukkit.block.Block block = event.getBlock();

        // Skip the OneBlock center — BlockListener owns its regen and its own drop path.
        if (isObCenter(block)) return;

        // Capture the XP value the event would have dropped, then suppress it.
        int xp = event.getExpToDrop();

        // Containers (furnace, chest, barrel, hopper, brewing stand, dispenser, …)
        // normally spill their CONTENTS as vanilla drops when broken. Because we
        // disable vanilla drops below to do telekinesis, those contents would be
        // destroyed unless we relocate them ourselves.
        //  - Shulker boxes are excluded: their contents ride inside the dropped box
        //    item, so collecting them here too would duplicate the stack.
        //  - For a (double) chest we take only THIS half via getBlockInventory(),
        //    matching vanilla — breaking one half leaves the other intact.
        java.util.List<ItemStack> containerContents = new java.util.ArrayList<>();
        org.bukkit.block.BlockState state = block.getState();
        org.bukkit.inventory.Inventory container = null;
        if (state instanceof org.bukkit.block.Chest chest) {
            container = chest.getBlockInventory();
        } else if (state instanceof org.bukkit.inventory.InventoryHolder holder) {
            container = holder.getInventory();
        }
        if (container != null && !Tag.SHULKER_BOXES.isTagged(block.getType())) {
            for (ItemStack content : container.getContents()) {
                if (content != null && !content.getType().isAir()) {
                    containerContents.add(content.clone());
                }
            }
            container.clear();
        }

        event.setDropItems(false);
        event.setExpToDrop(0);

        // Telekinesis the primary drop + XP, then the relocated container contents.
        collectDrops(p, tool, block, dropsForPaxel(block, tool));
        for (ItemStack content : containerContents) {
            giveOrDrop(p, block, content);   // not smelted — stored items keep their type
        }
        if (xp > 0) p.giveExp(xp);

        // Vein-mine: if the broken block was an ore, chain to connected same-type ores.
        if (ORES.contains(block.getType())) {
            veinMine(p, tool, block);
        }
    }

    private void veinMine(Player p, ItemStack tool, org.bukkit.block.Block start) {
        Material target = start.getType();
        java.util.Set<org.bukkit.block.Block> visited = new java.util.HashSet<>();
        java.util.Deque<org.bukkit.block.Block> queue = new java.util.ArrayDeque<>();
        visited.add(start); // the starting block is already being broken by vanilla
        queue.push(start);

        java.util.List<org.bukkit.block.Block> extras = new java.util.ArrayList<>();
        while (!queue.isEmpty() && extras.size() < VEIN_MINE_LIMIT) {
            org.bukkit.block.Block cur = queue.pop();
            for (int dx = -1; dx <= 1; dx++)
            for (int dy = -1; dy <= 1; dy++)
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dy == 0 && dz == 0) continue;
                org.bukkit.block.Block adj = cur.getRelative(dx, dy, dz);
                if (adj.getType() != target) continue;
                if (!visited.add(adj)) continue;
                if (isObCenter(adj)) continue; // never vein into the OneBlock
                extras.add(adj);
                queue.push(adj);
                if (extras.size() >= VEIN_MINE_LIMIT) break;
            }
        }

        // XP from vein-mined extras is approximated by ore type (Block#getExpDrop
        // isn't part of the API surface we compile against).
        int veinXp = 0;
        for (org.bukkit.block.Block b : extras) {
            collectDrops(p, tool, b, dropsForPaxel(b, tool));
            veinXp += approxOreXp(b.getType());
            b.setType(Material.AIR, false);
        }
        if (veinXp > 0) p.giveExp(veinXp);
    }

    private static int approxOreXp(Material ore) {
        return switch (ore) {
            case COAL_ORE, DEEPSLATE_COAL_ORE -> ThreadLocalRandom.current().nextInt(0, 3);
            case IRON_ORE, DEEPSLATE_IRON_ORE, COPPER_ORE, DEEPSLATE_COPPER_ORE -> 0;
            case GOLD_ORE, DEEPSLATE_GOLD_ORE, NETHER_GOLD_ORE -> 0;
            case DIAMOND_ORE, DEEPSLATE_DIAMOND_ORE -> ThreadLocalRandom.current().nextInt(3, 8);
            case EMERALD_ORE, DEEPSLATE_EMERALD_ORE -> ThreadLocalRandom.current().nextInt(3, 8);
            case LAPIS_ORE, DEEPSLATE_LAPIS_ORE -> ThreadLocalRandom.current().nextInt(2, 6);
            case REDSTONE_ORE, DEEPSLATE_REDSTONE_ORE -> ThreadLocalRandom.current().nextInt(1, 6);
            case NETHER_QUARTZ_ORE -> ThreadLocalRandom.current().nextInt(2, 6);
            case ANCIENT_DEBRIS -> 0;
            default -> 0;
        };
    }

    private void collectDrops(Player p, ItemStack tool, org.bukkit.block.Block block, java.util.Collection<ItemStack> drops) {
        for (ItemStack drop : drops) {
            giveOrDrop(p, block, maybeSmelt(drop));
        }
    }

    /** Add an item to the player's inventory, dropping any overflow at the block. */
    private void giveOrDrop(Player p, org.bukkit.block.Block block, ItemStack item) {
        if (item == null || item.getType().isAir()) return;
        var overflow = p.getInventory().addItem(item);
        if (!overflow.isEmpty()) {
            org.bukkit.Location dropLoc = block.getLocation().add(0.5, 0.5, 0.5);
            for (ItemStack leftover : overflow.values()) {
                block.getWorld().dropItemNaturally(dropLoc, leftover);
            }
        }
    }

    private java.util.Collection<ItemStack> dropsForPaxel(org.bukkit.block.Block block, ItemStack paxel) {
        java.util.Collection<ItemStack> drops = block.getDrops(paxel);
        if (!drops.isEmpty()) return drops;
        ItemStack fallbackTool = fallbackToolFor(block.getType(), tierOf(paxel));
        return fallbackTool == null ? drops : block.getDrops(fallbackTool);
    }

    private ItemStack fallbackToolFor(Material blockType, int tier) {
        Material tool = fallbackToolMaterial(blockType, tier);
        return tool == null ? null : new ItemStack(tool);
    }

    private Material fallbackToolMaterial(Material blockType, int tier) {
        ToolSet set = switch (Math.max(0, Math.min(TIER_MATERIALS.length - 1, tier))) {
            case 0 -> ToolSet.WOOD;
            case 1 -> ToolSet.STONE;
            case 2 -> ToolSet.IRON;
            case 3 -> ToolSet.GOLD;
            case 4 -> ToolSet.DIAMOND;
            default -> ToolSet.NETHERITE;
        };
        if (Tag.MINEABLE_AXE.isTagged(blockType)) return set.axe;
        if (Tag.MINEABLE_SHOVEL.isTagged(blockType)) return set.shovel;
        if (Tag.MINEABLE_HOE.isTagged(blockType)) return set.hoe;
        if (Tag.MINEABLE_PICKAXE.isTagged(blockType)) return set.pickaxe;
        return null;
    }

    private enum ToolSet {
        WOOD(Material.WOODEN_PICKAXE, Material.WOODEN_AXE, Material.WOODEN_SHOVEL, Material.WOODEN_HOE),
        STONE(Material.STONE_PICKAXE, Material.STONE_AXE, Material.STONE_SHOVEL, Material.STONE_HOE),
        IRON(Material.IRON_PICKAXE, Material.IRON_AXE, Material.IRON_SHOVEL, Material.IRON_HOE),
        GOLD(Material.GOLDEN_PICKAXE, Material.GOLDEN_AXE, Material.GOLDEN_SHOVEL, Material.GOLDEN_HOE),
        DIAMOND(Material.DIAMOND_PICKAXE, Material.DIAMOND_AXE, Material.DIAMOND_SHOVEL, Material.DIAMOND_HOE),
        NETHERITE(Material.NETHERITE_PICKAXE, Material.NETHERITE_AXE, Material.NETHERITE_SHOVEL, Material.NETHERITE_HOE);

        final Material pickaxe;
        final Material axe;
        final Material shovel;
        final Material hoe;

        ToolSet(Material pickaxe, Material axe, Material shovel, Material hoe) {
            this.pickaxe = pickaxe;
            this.axe = axe;
            this.shovel = shovel;
            this.hoe = hoe;
        }
    }

    private boolean isObCenter(org.bukkit.block.Block block) {
        Island island = plugin.islands().atLocation(block.getLocation());
        if (island == null) return false;
        org.bukkit.Location center = island.centerBlock();
        return block.getX() == center.getBlockX()
                && block.getY() == center.getBlockY()
                && block.getZ() == center.getBlockZ();
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

    /**
     * Block <em>non-owners</em> from moving someone else's paxel into or out of an
     * external inventory. The owner is allowed to stash their paxel in chests etc.
     */
    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player p)) return;
        ItemStack moved = event.getCurrentItem();
        ItemStack cursor = event.getCursor();
        boolean foreignPaxel = (isPaxel(cursor) && !isOwner(cursor, p))
                || (isPaxel(moved) && !isOwner(moved, p));
        if (!foreignPaxel) return;
        // Only intervene when the click would actually move the paxel into/out of
        // another player's inventory (i.e. clicking inside a chest/etc.) — clicks
        // inside the player's own inventory are fine.
        var top = event.getView().getTopInventory().getType();
        var clicked = event.getClickedInventory() == null ? null : event.getClickedInventory().getType();
        boolean externalTop = top != InventoryType.CRAFTING && top != InventoryType.PLAYER;
        boolean clickInExternal = clicked != null && clicked != InventoryType.PLAYER && clicked != InventoryType.CRAFTING;
        if (clickInExternal || (event.isShiftClick() && externalTop)) {
            event.setCancelled(true);
            Msg.actionBar(p, "<red>That Paxel isn't yours.");
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        ItemStack item = event.getOldCursor();
        if (!isPaxel(item)) return;
        if (!(event.getWhoClicked() instanceof Player p)) return;
        if (isOwner(item, p)) return;
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
