package com.nova.novablock.season;

import com.nova.novablock.NovaBlock;
import com.nova.novablock.island.Island;
import com.nova.novablock.progression.PlayerProgression;
import com.nova.novablock.util.ItemBuilder;
import com.nova.novablock.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class SeasonalPathManager {

    public static final int PATH_COUNT = 100;
    public static final int TIER_COUNT = 30;
    private static final YearMonth EPOCH = YearMonth.of(2026, 6);
    private static final String[] THEMES = {
            "Astral Bloom", "Molten Crown", "Frostbound Forge", "Tideglass Depths", "Verdant Circuit",
            "Sculk Eclipse", "Sunken Relic", "Stormwright Oath", "Amber Harvest", "Void Lantern",
            "Crystal Orchard", "Emberwild Hunt", "Moonlit Bazaar", "Golden Rift", "Ancient Sky",
            "Prism Dunes", "Nether Rose", "Echo Garden", "Iron Masquerade", "Aether Compass"
    };
    private static final Material[] ICONS = {
            Material.NETHER_STAR, Material.MAGMA_CREAM, Material.BLUE_ICE, Material.PRISMARINE_CRYSTALS,
            Material.MOSS_BLOCK, Material.SCULK, Material.HEART_OF_THE_SEA, Material.LIGHTNING_ROD,
            Material.HONEYCOMB, Material.ENDER_EYE, Material.AMETHYST_SHARD, Material.BLAZE_POWDER,
            Material.CLOCK, Material.GOLD_INGOT, Material.FEATHER, Material.SAND, Material.CRIMSON_FUNGUS,
            Material.ECHO_SHARD, Material.IRON_BLOCK, Material.COMPASS
    };
    private static final String[] COLORS = {
            "#B38CFF", "#FF6B35", "#7DD3FC", "#22D3EE", "#78D64B",
            "#00A896", "#38BDF8", "#FACC15", "#F59E0B", "#8B5CF6",
            "#C084FC", "#F97316", "#E879F9", "#FDE047", "#60A5FA",
            "#FDBA74", "#FB7185", "#A7F3D0", "#D1D5DB", "#93C5FD"
    };
    private static final String[] ACCENTS = {
            "#F0E6FF", "#FFD27F", "#E0F7FF", "#A8F5FF", "#D4F5BA",
            "#5EEAD4", "#A5F3FC", "#FFF59B", "#FCD34D", "#C4B5FD",
            "#E9D5FF", "#FCA5A5", "#F5D0FE", "#FEF9C3", "#BFDBFE",
            "#FED7AA", "#FECDD3", "#D1FAE5", "#F3F4F6", "#DBEAFE"
    };

    private final NovaBlock plugin;
    private final NamespacedKey itemKey;
    private final File stateFile;
    private final Set<String> claimedFirsts = new HashSet<>();

    public SeasonalPathManager(NovaBlock plugin) {
        this.plugin = plugin;
        this.itemKey = new NamespacedKey(plugin, "seasonal_reward");
        this.stateFile = new File(plugin.getDataFolder(), "seasonal-state.yml");
    }

    public void load() {
        claimedFirsts.clear();
        if (!stateFile.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(stateFile);
        claimedFirsts.addAll(y.getStringList("claimed-firsts"));
    }

    public void save() {
        YamlConfiguration y = new YamlConfiguration();
        y.set("claimed-firsts", claimedFirsts.stream().sorted().toList());
        try {
            y.save(stateFile);
        } catch (IOException ex) {
            plugin.getLogger().warning("Failed to save seasonal state: " + ex.getMessage());
        }
    }

    public SeasonalPath activePath() {
        YearMonth now = YearMonth.now(ZoneOffset.UTC);
        long months = ChronoUnit.MONTHS.between(EPOCH, now);
        int index = Math.floorMod((int) months, PATH_COUNT) + 1;
        return path(index, now);
    }

    public SeasonalPath path(int index, YearMonth month) {
        int themeIndex = Math.floorMod(index - 1, THEMES.length);
        String seasonName = THEMES[themeIndex];
        return new SeasonalPath(index, month, seasonName + " " + roman(((index - 1) / THEMES.length) + 1),
                COLORS[themeIndex], ACCENTS[themeIndex], ICONS[themeIndex]);
    }

    public void ensureActive(Player player) {
        PlayerProgression prog = plugin.progression().get(player);
        SeasonalPath path = activePath();
        if (path.key().equals(prog.getSeasonalPathKey())) {
            retryPendingRewards(player, prog);
            return;
        }
        prog.setSeasonalPathKey(path.key());
        prog.setSeasonalPathPoints(0);
        prog.getClaimedSeasonalTiers().clear();
        retryPendingRewards(player, prog);
        plugin.progression().save(player.getUniqueId());
    }

    public void award(Player player, PathSource source, int points) {
        if (points <= 0) return;
        ensureActive(player);
        PlayerProgression prog = plugin.progression().get(player);
        SeasonalPath path = activePath();
        int beforeTier = tierFor(prog.getSeasonalPathPoints());
        prog.addSeasonalPathPoints(points);
        prog.addAtlasScore(source.atlasScore);
        int afterTier = tierFor(prog.getSeasonalPathPoints());
        if (afterTier > beforeTier) {
            Msg.actionBar(player, "<" + path.color() + ">Path Tier " + afterTier + " <gray>unlocked");
            player.playSound(player.getLocation(), Sound.UI_TOAST_IN, 0.8f, 1.3f);
        }
        claimAvailable(player, prog, path);
    }

    public void addAdminPoints(Player player, int points) {
        award(player, PathSource.ADMIN, points);
        plugin.progression().save(player.getUniqueId());
    }

    public int tierFor(int points) {
        return Math.min(TIER_COUNT, Math.max(0, points / 250));
    }

    public int pointsForTier(int tier) {
        return Math.max(1, Math.min(TIER_COUNT, tier)) * 250;
    }

    public String atlasTitle(PlayerProgression prog) {
        int score = prog.getAtlasScore();
        if (score >= 25_000) return "<gradient:#FDE047:#FFFFFF>Atlas Eternal";
        if (score >= 10_000) return "<#B38CFF>Atlas Paragon";
        if (score >= 5_000) return "<#38BDF8>Atlas Keeper";
        if (score >= 1_500) return "<#78D64B>Atlas Seeker";
        return "<gray>Atlas Initiate";
    }

    public List<String> tierLore(PlayerProgression prog, SeasonalPath path, int tier) {
        List<String> lore = new ArrayList<>();
        lore.add("<gray>Requires <yellow>" + pointsForTier(tier) + " <gray>path points");
        lore.add(prog.hasClaimedSeasonalTier(tier) ? "<green>Claimed" : "<yellow>Reward: " + rewardLabel(tier));
        if (tier == 30) lore.add("<light_purple>Includes this month's pet and tag.");
        return lore;
    }

    public String rewardLabel(int tier) {
        if (tier == 30) return "monthly pet, path tag, and relic";
        if (tier == 20) return "rare seasonal loot bundle";
        if (tier == 10) return "custom enchanted tool";
        if (tier % 5 == 0) return "rare seasonal loot";
        return "coins, XP, and supplies";
    }

    public void resetPlayer(Player player) {
        PlayerProgression prog = plugin.progression().get(player);
        SeasonalPath path = activePath();
        prog.setSeasonalPathKey(path.key());
        prog.setSeasonalPathPoints(0);
        prog.getClaimedSeasonalTiers().clear();
        plugin.progression().save(player.getUniqueId());
    }

    public void ensureTags() {
        if (Bukkit.getPluginManager().getPlugin("xTags") == null) return;
        for (int i = 1; i <= PATH_COUNT; i++) {
            SeasonalPath path = path(i, activePath().month());
            String shimmer = "<gradient:" + path.color() + ":" + path.accent() + ":" + path.color() + ">";
            String firstShimmer = "<gradient:#FFD700:#FFF8B0:#FFA500:#FFD700>";
            // Permission-gated: only players granted the tag (i.e., who completed the path)
            // can equip it. Permission node mirrors the tag id for easy LP/perm-admin lookup.
            ensureTag(path.tagId(), path.name(),
                    shimmer + "[" + path.name() + "]</gradient>",
                    "novablock.tag." + path.tagId());
            ensureTag(path.firstTagId(), "First " + path.name(),
                    firstShimmer + "<bold>[✦ " + path.name() + " ✦]</bold></gradient>",
                    "novablock.tag." + path.firstTagId());
        }
    }

    private void claimAvailable(Player player, PlayerProgression prog, SeasonalPath path) {
        int unlocked = tierFor(prog.getSeasonalPathPoints());
        boolean claimedAny = false;
        for (int tier = 1; tier <= unlocked; tier++) {
            if (prog.hasClaimedSeasonalTier(tier)) continue;
            grantTier(player, prog, path, tier);
            prog.markSeasonalTierClaimed(tier);
            claimedAny = true;
        }
        if (claimedAny) plugin.progression().save(player.getUniqueId());
    }

    private void grantTier(Player player, PlayerProgression prog, SeasonalPath path, int tier) {
        Island island = plugin.islands().ofPlayer(player);
        if (island != null) {
            plugin.economy().award(island, 350L * tier);
        }
        plugin.progression().addXp(player, com.nova.novablock.progression.SkillType.LUCK, 15L * tier);
        giveItem(player, rewardItem(path, tier, false));
        // Tag drops only on path completion (tier 30). Permission-gated in xTags so it
        // visibly marks the player as having finished this path.
        if (tier == TIER_COUNT) {
            grantTag(player, prog, path.tagId());
            grantPet(player, prog, path.petId());
            String firstKey = "path-" + path.index();
            if (claimedFirsts.add(firstKey)) {
                grantTag(player, prog, path.firstTagId());
                giveItem(player, rewardItem(path, tier, true));
                save();
                Bukkit.broadcast(Msg.mm("<gold>1-of-1 claimed: <yellow>" + player.getName()
                        + " <gray>completed <white>" + path.name() + " <gray>first."));
            }
        }
        Msg.send(player, "<" + path.color() + ">Path tier " + tier + " claimed: <yellow>" + rewardLabel(tier));
    }

    private ItemStack rewardItem(SeasonalPath path, int tier, boolean first) {
        Material material = first ? Material.NETHER_STAR : switch (tier % 5) {
            case 0 -> Material.DIAMOND;
            case 1 -> Material.EXPERIENCE_BOTTLE;
            case 2 -> Material.GOLDEN_CARROT;
            case 3 -> Material.AMETHYST_SHARD;
            default -> Material.EMERALD;
        };
        ItemBuilder builder = ItemBuilder.of(material, first ? 1 : Math.max(1, tier / 2))
                .name((first ? "<gold>1-of-1 " : "<" + path.color() + ">") + path.name() + " Tier " + tier)
                .lore("<gray>NovaBlock seasonal path reward.",
                        "<dark_gray>" + path.key() + " · tier " + tier)
                .tag(itemKey, path.key() + ":" + tier + ":" + (first ? "first" : "normal"))
                .hideFlags();
        if (first || tier % 10 == 0) {
            builder.enchant(Enchantment.UNBREAKING, Math.max(1, tier / 10));
        }
        ItemStack item = builder.build();
        var meta = item.getItemMeta();
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        meta.getPersistentDataContainer().set(itemKey, PersistentDataType.STRING,
                path.key() + ":" + tier + ":" + (first ? "first" : "normal"));
        item.setItemMeta(meta);
        return item;
    }

    private void giveItem(Player player, ItemStack item) {
        var overflow = player.getInventory().addItem(item);
        for (ItemStack leftover : overflow.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), leftover);
        }
    }

    private void grantPet(Player player, PlayerProgression prog, String petId) {
        String command = "petadmin give " + player.getName() + " " + petId;
        if (Bukkit.getPluginManager().getPlugin("xPets") == null) {
            prog.addPendingRewardCommand(command);
            return;
        }
        try {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
        } catch (Throwable t) {
            prog.addPendingRewardCommand(command);
            plugin.getLogger().warning("Queued xPets reward after grant failed: " + t.getMessage());
        }
    }

    private void grantTag(Player player, PlayerProgression prog, String tagId) {
        if (Bukkit.getPluginManager().getPlugin("xTags") == null) {
            prog.addPendingRewardCommand("tagadmin grant " + player.getName() + " " + tagId);
            return;
        }
        try {
            Class<?> serviceType = Class.forName("dev.xsuite.tags.api.TagService");
            Object service = Bukkit.getServicesManager().load(serviceType);
            if (service != null) {
                serviceType.getMethod("grantTag", java.util.UUID.class, String.class)
                        .invoke(service, player.getUniqueId(), tagId);
                return;
            }
        } catch (ReflectiveOperationException ignored) {
            // Older xTags: command fallback.
        }
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "tagadmin grant " + player.getName() + " " + tagId);
    }

    private void ensureTag(String id, String display, String format, String permission) {
        try {
            Class<?> serviceType = Class.forName("dev.xsuite.tags.api.TagService");
            Object service = Bukkit.getServicesManager().load(serviceType);
            if (service != null) {
                serviceType.getMethod("ensureTag", String.class, String.class, String.class, String.class)
                        .invoke(service, id, display, format, permission == null ? "" : permission);
                return;
            }
        } catch (ReflectiveOperationException ignored) {
            // Older xTags: command fallback.
        }
        String perm = permission == null || permission.isBlank() ? "none" : permission;
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "tagadmin create " + id + " " + perm + " " + format);
    }

    private void retryPendingRewards(Player player, PlayerProgression prog) {
        if (prog.getPendingRewardCommands().isEmpty()) return;
        for (String command : new ArrayList<>(prog.getPendingRewardCommands())) {
            String lower = command.toLowerCase(Locale.ROOT);
            if (lower.startsWith("petadmin ") && Bukkit.getPluginManager().getPlugin("xPets") == null) continue;
            if (lower.startsWith("tagadmin ") && Bukkit.getPluginManager().getPlugin("xTags") == null) continue;
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
            prog.removePendingRewardCommand(command);
        }
    }

    private static String roman(int number) {
        return switch (number) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            default -> String.valueOf(number);
        };
    }

    public enum PathSource {
        MINING(1), QUEST(75), BOSS(100), LOOT_ROOM(90), PHASE(125), PRESTIGE(500), LOGIN(50), ADMIN(0);

        private final int atlasScore;

        PathSource(int atlasScore) {
            this.atlasScore = atlasScore;
        }
    }

    public record SeasonalPath(int index, YearMonth month, String name, String color, String accent, Material icon) {
        public String key() { return month + "-" + index; }
        public String petId() { return "novablock_path_" + String.format("%03d", index); }
        public String tagId() { return "nb_path_" + String.format("%03d", index); }
        public String firstTagId() { return "nb_first_path_" + String.format("%03d", index); }
        public String shortName() { return "Path " + String.format("%03d", index); }
    }
}
