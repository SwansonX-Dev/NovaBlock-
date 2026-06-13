package com.nova.novablock.minion;

import com.nova.novablock.NovaBlock;
import com.nova.novablock.phase.Phase;
import com.nova.novablock.util.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;

public enum MinionType {
    PLAINS_FARMER("Plains Farmer", 0, Material.HAY_BLOCK, 30, Material.WHEAT, Material.CARROT, Material.POTATO, Material.OAK_LOG, Material.PUMPKIN),
    UNDERGROUND_MINER("Underground Miner", 1, Material.IRON_PICKAXE, 34, Material.COBBLESTONE, Material.COAL, Material.RAW_IRON, Material.IRON_ORE, Material.COPPER_ORE),
    FROZEN_FORAGER("Frozen Forager", 2, Material.SPRUCE_SAPLING, 38, Material.SNOW_BLOCK, Material.ICE, Material.SPRUCE_LOG, Material.SWEET_BERRIES, Material.PACKED_ICE),
    DESERT_DIGGER("Desert Digger", 3, Material.SAND, 42, Material.SAND, Material.RED_SAND, Material.CACTUS, Material.TERRACOTTA, Material.GOLD_NUGGET),
    REEF_COLLECTOR("Reef Collector", 4, Material.PRISMARINE_SHARD, 46, Material.KELP, Material.PRISMARINE_SHARD, Material.PRISMARINE_CRYSTALS, Material.COD, Material.CLAY_BALL),
    NETHER_HARVESTER("Nether Harvester", 5, Material.NETHER_WART, 52, Material.NETHERRACK, Material.QUARTZ, Material.GLOWSTONE_DUST, Material.NETHER_WART, Material.GOLD_NUGGET),
    ANCIENT_EXCAVATOR("Ancient Excavator", 6, Material.DIAMOND_PICKAXE, 58, Material.DEEPSLATE, Material.REDSTONE, Material.LAPIS_LAZULI, Material.DIAMOND, Material.AMETHYST_SHARD),
    LUSH_GARDENER("Lush Gardener", 7, Material.MOSS_BLOCK, 54, Material.MOSS_BLOCK, Material.AZALEA, Material.CLAY_BALL, Material.BAMBOO, Material.BIG_DRIPLEAF),
    STRONGHOLD_SCHOLAR("Stronghold Scholar", 8, Material.BOOKSHELF, 62, Material.STONE_BRICKS, Material.BOOK, Material.ENDER_PEARL, Material.IRON_INGOT, Material.OBSIDIAN),
    END_SALVAGER("End Salvager", 9, Material.ENDER_PEARL, 68, Material.END_STONE, Material.CHORUS_FRUIT, Material.PURPUR_BLOCK, Material.ENDER_PEARL, Material.OBSIDIAN),
    CELESTIAL_COLLECTOR("Celestial Collector", 10, Material.AMETHYST_CLUSTER, 74, Material.AMETHYST_SHARD, Material.GLOWSTONE_DUST, Material.EXPERIENCE_BOTTLE, Material.DIAMOND, Material.CRYING_OBSIDIAN),
    VOID_SCAVENGER("Void Scavenger", 11, Material.ECHO_SHARD, 84, Material.SCULK, Material.ECHO_SHARD, Material.ENDER_PEARL, Material.OBSIDIAN, Material.NETHERITE_SCRAP),

    // --- Community single-resource minions (placed on a Community OneBlock claim) ---
    COMMUNITY_COBBLESTONE("Cobblestone", 0, Material.COBBLESTONE, 28, true, Material.COBBLESTONE),
    COMMUNITY_COAL("Coal", 0, Material.COAL_ORE, 32, true, Material.COAL),
    COMMUNITY_IRON("Iron", 0, Material.IRON_ORE, 38, true, Material.RAW_IRON),
    COMMUNITY_GOLD("Gold", 0, Material.GOLD_ORE, 42, true, Material.RAW_GOLD),
    COMMUNITY_REDSTONE("Redstone", 0, Material.REDSTONE_ORE, 36, true, Material.REDSTONE),
    COMMUNITY_LAPIS("Lapis", 0, Material.LAPIS_ORE, 40, true, Material.LAPIS_LAZULI),
    COMMUNITY_DIAMOND("Diamond", 0, Material.DIAMOND_ORE, 60, true, Material.DIAMOND),
    COMMUNITY_EMERALD("Emerald", 0, Material.EMERALD_ORE, 64, true, Material.EMERALD),
    COMMUNITY_WOOD("Lumber", 0, Material.OAK_LOG, 30, true, Material.OAK_LOG),
    COMMUNITY_WHEAT("Wheat", 0, Material.WHEAT, 30, true, Material.WHEAT),
    COMMUNITY_SAND("Sand", 0, Material.SAND, 28, true, Material.SAND),
    COMMUNITY_QUARTZ("Quartz", 0, Material.QUARTZ, 46, true, Material.QUARTZ);

    private final String displayName;
    private final int requiredPhaseIndex;
    private final Material displayMaterial;
    private final int baseIntervalSeconds;
    /** True for community-claim minions (placed in the community world, owner-based). */
    private final boolean community;
    private final List<MinionDrop> defaultDrops = new ArrayList<>();

    MinionType(String displayName, int requiredPhaseIndex, Material displayMaterial, int baseIntervalSeconds, Material... outputs) {
        this(displayName, requiredPhaseIndex, displayMaterial, baseIntervalSeconds, false, outputs);
    }

    MinionType(String displayName, int requiredPhaseIndex, Material displayMaterial, int baseIntervalSeconds, boolean community, Material... outputs) {
        this.displayName = displayName;
        this.requiredPhaseIndex = requiredPhaseIndex;
        this.displayMaterial = displayMaterial;
        this.baseIntervalSeconds = baseIntervalSeconds;
        this.community = community;
        int weight = 70;
        for (Material output : outputs) {
            double rare = output == Material.NETHERITE_SCRAP ? 0.01 : 0.0;
            defaultDrops.add(new MinionDrop(output, Math.max(10, weight), 1, 1, rare));
            weight -= 12;
        }
    }

    public boolean community() { return community; }

    public String id() { return name().toLowerCase(Locale.ROOT); }
    public String displayName() { return displayName; }
    public int requiredPhaseIndex() { return requiredPhaseIndex; }
    public Material displayMaterial() { return displayMaterial; }
    public int baseIntervalSeconds() { return baseIntervalSeconds; }
    public List<MinionDrop> defaultDrops() { return Collections.unmodifiableList(defaultDrops); }

    public long shopPrice(NovaBlock plugin) {
        return plugin.getConfig().getLong("minions.shop-prices." + id(), 1000L + requiredPhaseIndex * 1500L);
    }

    public boolean shopEnabled(NovaBlock plugin) {
        return plugin.getConfig().getBoolean("minions.shop-enabled." + id(), true);
    }

    public boolean unlocked(int phaseIndex) { return phaseIndex >= requiredPhaseIndex; }

    public ItemStack createItem(NovaBlock plugin, int amount) {
        ItemBuilder builder = ItemBuilder.of(displayMaterial, amount)
                .name("<gold>" + displayName + " Minion");
        if (community) {
            String resource = defaultDrops.isEmpty() ? "resources"
                    : defaultDrops.get(0).material().name().toLowerCase(Locale.ROOT).replace('_', ' ');
            builder.lore("<gray>Place on your <aqua>Community OneBlock<gray> claim.",
                    "<gray>Produces <yellow>" + resource + "<gray>.",
                    "<dark_gray>NovaBlock Community Minion");
        } else {
            builder.lore("<gray>Place on your island.",
                    "<gray>Requires phase <yellow>" + (requiredPhaseIndex + 1) + "</yellow>.",
                    "<dark_gray>NovaBlock Minion");
        }
        return builder.tag(new NamespacedKey(plugin, "minion_type"), id()).hideFlags().build();
    }

    public ItemStack rollOutput(Random random, int yieldLevel, int compactorLevel, List<MinionDrop> drops) {
        MinionDrop drop = rollDrop(random, drops == null || drops.isEmpty() ? defaultDrops : drops);
        int amount = drop.minAmount() + random.nextInt(drop.maxAmount() - drop.minAmount() + 1);
        amount += Math.max(0, yieldLevel / 2);
        if (yieldLevel > 0 && random.nextDouble() < yieldLevel * 0.10) amount++;
        Material material = compact(drop.material(), compactorLevel);
        if (material != drop.material()) amount = Math.max(1, amount / 9);
        return new ItemStack(material, Math.max(1, amount));
    }

    private MinionDrop rollDrop(Random random, List<MinionDrop> drops) {
        List<MinionDrop> eligible = new ArrayList<>();
        for (MinionDrop drop : drops) {
            if (drop.rareChance() <= 0.0 || random.nextDouble() <= drop.rareChance()) eligible.add(drop);
        }
        if (eligible.isEmpty()) {
            for (MinionDrop drop : drops) if (drop.rareChance() <= 0.0) eligible.add(drop);
        }
        if (eligible.isEmpty()) eligible = drops;
        int total = 0;
        for (MinionDrop drop : eligible) total += drop.weight();
        int roll = random.nextInt(Math.max(1, total));
        int acc = 0;
        for (MinionDrop drop : eligible) {
            acc += drop.weight();
            if (roll < acc) return drop;
        }
        return eligible.get(0);
    }

    private Material compact(Material material, int compactorLevel) {
        if (compactorLevel <= 0) return material;
        return switch (material) {
            case COAL -> Material.COAL_BLOCK;
            case RAW_IRON -> Material.RAW_IRON_BLOCK;
            case IRON_INGOT -> Material.IRON_BLOCK;
            case GOLD_NUGGET, GOLD_INGOT -> compactorLevel >= 2 ? Material.GOLD_BLOCK : material;
            case REDSTONE -> Material.REDSTONE_BLOCK;
            case LAPIS_LAZULI -> Material.LAPIS_BLOCK;
            case DIAMOND -> Material.DIAMOND_BLOCK;
            case QUARTZ -> Material.QUARTZ_BLOCK;
            case AMETHYST_SHARD -> Material.AMETHYST_BLOCK;
            case WHEAT -> Material.HAY_BLOCK;
            case SNOWBALL -> Material.SNOW_BLOCK;
            case CLAY_BALL -> Material.CLAY;
            case NETHERITE_SCRAP -> compactorLevel >= 3 ? Material.NETHERITE_INGOT : material;
            default -> material;
        };
    }

    public String phaseName(NovaBlock plugin) {
        Phase phase = plugin.phases().get(requiredPhaseIndex);
        return phase == null ? "Phase " + (requiredPhaseIndex + 1) : phase.getDisplayName();
    }

    public static MinionType byId(String id) {
        if (id == null) return null;
        String normalized = id.toUpperCase(Locale.ROOT).replace('-', '_');
        for (MinionType type : values()) {
            if (type.name().equals(normalized) || type.id().equalsIgnoreCase(id)) return type;
        }
        return null;
    }
}
