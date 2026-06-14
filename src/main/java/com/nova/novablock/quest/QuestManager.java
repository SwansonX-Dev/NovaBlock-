package com.nova.novablock.quest;

import com.nova.novablock.NovaBlock;
import com.nova.novablock.progression.PlayerProgression;
import com.nova.novablock.progression.SkillType;
import com.nova.novablock.quest.Quest.QuestCategory;
import com.nova.novablock.quest.Quest.QuestType;
import com.nova.novablock.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Daily quest system. Each day the server deterministically rolls a set of
 * {@value #DAILY_COUNT} quests from a categorised pool, biased toward category
 * variety so players get a spread across personal / community / OG OneBlock and
 * pet objectives. Quests that depend on a sibling plugin (OG OneBlock, xPets)
 * are only eligible when that plugin is actually enabled, so a player can never
 * be handed a quest they have no way to progress.
 *
 * <p>Progress is tracked per quest id on {@link PlayerProgression}; rewards fire
 * once, the moment a quest's threshold is crossed. The daily roll is seeded by
 * the epoch day so it is stable across restarts within the same day — that
 * matters because progress is keyed by quest id.
 */
public class QuestManager {

    public static final int DAILY_COUNT = 3;

    private final NovaBlock plugin;
    private final List<Quest> pool = new ArrayList<>();
    private List<Quest> todayQuests = new ArrayList<>();
    private long currentDayStamp = Long.MIN_VALUE;

    public QuestManager(NovaBlock plugin) { this.plugin = plugin; }

    public void loadDailyQuests() {
        pool.clear();

        // --- Personal OneBlock -------------------------------------------------
        pool.add(new Quest("p_stone", "Stonebreaker", "Break 200 stone.",
                QuestType.BREAK_BLOCK, QuestCategory.PERSONAL, Material.STONE, Material.STONE, 200, 500, 200));
        pool.add(new Quest("p_iron", "Iron-Bound", "Break 40 iron ore.",
                QuestType.BREAK_BLOCK, QuestCategory.PERSONAL, Material.IRON_ORE, Material.IRON_ORE, 40, 800, 300));
        pool.add(new Quest("p_gold", "Gold Rush", "Break 30 gold ore.",
                QuestType.BREAK_BLOCK, QuestCategory.PERSONAL, Material.GOLD_ORE, Material.GOLD_ORE, 30, 900, 320));
        pool.add(new Quest("p_coal", "Carbon Copy", "Break 120 coal ore.",
                QuestType.BREAK_BLOCK, QuestCategory.PERSONAL, Material.COAL_ORE, Material.COAL_ORE, 120, 600, 220));
        pool.add(new Quest("p_redstone", "Power Lines", "Break 60 redstone ore.",
                QuestType.BREAK_BLOCK, QuestCategory.PERSONAL, Material.REDSTONE_ORE, Material.REDSTONE_ORE, 60, 750, 260));
        pool.add(new Quest("p_lapis", "Deep Blue", "Break 40 lapis ore.",
                QuestType.BREAK_BLOCK, QuestCategory.PERSONAL, Material.LAPIS_ORE, Material.LAPIS_ORE, 40, 800, 280));
        pool.add(new Quest("p_emerald", "Emerald Eyes", "Break 12 emerald ore.",
                QuestType.BREAK_BLOCK, QuestCategory.PERSONAL, Material.EMERALD_ORE, Material.EMERALD_ORE, 12, 1400, 460));
        pool.add(new Quest("p_diamond", "Diamond Hunter", "Break 8 diamond ore.",
                QuestType.BREAK_BLOCK, QuestCategory.PERSONAL, Material.DIAMOND_ORE, Material.DIAMOND_ORE, 8, 1500, 500));
        pool.add(new Quest("p_grind", "Grinder", "Break 500 blocks of any kind.",
                QuestType.BREAK_ANY, QuestCategory.PERSONAL, null, Material.IRON_PICKAXE, 500, 750, 250));
        pool.add(new Quest("p_marathon", "Marathon Miner", "Break 1500 blocks of any kind.",
                QuestType.BREAK_ANY, QuestCategory.PERSONAL, null, Material.NETHERITE_PICKAXE, 1500, 2200, 750));
        pool.add(new Quest("p_rift", "Rift Runner", "Clear 1 loot room.",
                QuestType.COMPLETE_LOOT_ROOM, QuestCategory.PERSONAL, null, Material.END_PORTAL_FRAME, 1, 1200, 400));
        pool.add(new Quest("p_boss", "Boss Slayer", "Defeat 1 boss.",
                QuestType.KILL_BOSS, QuestCategory.PERSONAL, null, Material.NETHERITE_SWORD, 1, 2000, 800));
        pool.add(new Quest("p_boss3", "Boss Hunter", "Defeat 3 bosses.",
                QuestType.KILL_BOSS, QuestCategory.PERSONAL, null, Material.WITHER_SKELETON_SKULL, 3, 5000, 1800));
        pool.add(new Quest("p_phase", "Phase Pusher", "Advance 1 phase.",
                QuestType.ADVANCE_PHASE, QuestCategory.PERSONAL, null, Material.NETHER_STAR, 1, 1800, 600));
        pool.add(new Quest("p_prophecy", "Fate Sealed", "Fulfil 2 prophecies.",
                QuestType.FULFILL_PROPHECY, QuestCategory.PERSONAL, null, Material.AMETHYST_SHARD, 2, 1600, 540));
        pool.add(new Quest("p_combo", "On a Roll", "Reach a x25 break combo.",
                QuestType.REACH_COMBO, QuestCategory.PERSONAL, null, Material.BLAZE_POWDER, 25, 1300, 440));

        // --- Community OneBlock -------------------------------------------------
        pool.add(new Quest("c_contrib", "Community Pillar", "Break 150 blocks on the Community OneBlock.",
                QuestType.COMMUNITY_BREAK, QuestCategory.COMMUNITY, null, Material.BEACON, 150, 1200, 420));
        pool.add(new Quest("c_contrib_big", "Hub Hero", "Break 400 blocks on the Community OneBlock.",
                QuestType.COMMUNITY_BREAK, QuestCategory.COMMUNITY, null, Material.CONDUIT, 400, 2600, 880));

        // --- OG OneBlock (only eligible when OGOneBlock is enabled) -------------
        pool.add(new Quest("og_break", "Hard Mode Hustle", "Break 200 blocks on OG OneBlock.",
                QuestType.OG_BREAK, QuestCategory.OG, null, Material.NETHERRACK, 200, 1600, 560));
        pool.add(new Quest("og_break_big", "OG Grind", "Break 600 blocks on OG OneBlock.",
                QuestType.OG_BREAK, QuestCategory.OG, null, Material.NETHERITE_INGOT, 600, 3400, 1200));

        // --- Pets (only eligible when xPets is enabled) ------------------------
        pool.add(new Quest("pet_gather", "Pet Provisions", "Send a pet on a gather mission.",
                QuestType.PET_GATHER, QuestCategory.PET, null, Material.BUNDLE, 1, 1000, 360));

        rollDaily();
    }

    /** True when this quest's category depends on a sibling plugin that is present. */
    private boolean isEligible(Quest q) {
        return switch (q.category()) {
            case OG -> Bukkit.getPluginManager().isPluginEnabled("OGOneBlock");
            case PET -> Bukkit.getPluginManager().isPluginEnabled("xPets");
            default -> true;
        };
    }

    public void rollDaily() {
        long day = LocalDate.now().toEpochDay();
        if (day == currentDayStamp && !todayQuests.isEmpty()) return;
        currentDayStamp = day;

        List<Quest> eligible = new ArrayList<>();
        for (Quest q : pool) if (isEligible(q)) eligible.add(q);

        // Deterministic within the day so progress (keyed by quest id) survives restarts.
        Random rnd = new Random(day);
        Collections.shuffle(eligible, rnd);

        List<Quest> chosen = new ArrayList<>(DAILY_COUNT);
        Set<QuestCategory> usedCats = EnumSet.noneOf(QuestCategory.class);
        // Always seed one Personal quest so every player — including those who never
        // touch OG/community/pets — has at least one quest they can complete.
        for (Quest q : eligible) {
            if (q.category() == QuestCategory.PERSONAL) {
                chosen.add(q);
                usedCats.add(QuestCategory.PERSONAL);
                break;
            }
        }
        // First pass: maximise variety — at most one quest per category.
        for (Quest q : eligible) {
            if (chosen.size() >= DAILY_COUNT) break;
            if (chosen.contains(q)) continue;
            if (usedCats.add(q.category())) chosen.add(q);
        }
        // Second pass: top up if the eligible categories were too few to fill the slots.
        for (Quest q : eligible) {
            if (chosen.size() >= DAILY_COUNT) break;
            if (!chosen.contains(q)) chosen.add(q);
        }

        todayQuests = chosen;
        StringBuilder log = new StringBuilder("Today's quests: ");
        for (int i = 0; i < chosen.size(); i++) {
            if (i > 0) log.append(", ");
            log.append(chosen.get(i).displayName());
        }
        plugin.getLogger().info(log.toString());
    }

    public List<Quest> todayQuests() { rollDaily(); return todayQuests; }
    public long dayStamp() { rollDaily(); return currentDayStamp; }

    /** Legacy single-quest accessor — the first of today's quests (or null). */
    public Quest today() {
        rollDaily();
        return todayQuests.isEmpty() ? null : todayQuests.get(0);
    }

    /** Reset a player's per-quest progress when the quest day rolls over. */
    private void ensureDay(PlayerProgression prog) {
        if (prog.getQuestDayStamp() != dayStamp()) {
            prog.setQuestDayStamp(dayStamp());
            prog.clearQuestProgress();
        }
    }

    public int progressOf(Player p, Quest q) {
        if (q == null) return 0;
        PlayerProgression prog = plugin.progression().get(p);
        ensureDay(prog);
        return Math.min(q.requiredAmount(), prog.getQuestProgress(q.id()));
    }

    /** Legacy single-quest accessor — progress on the first of today's quests. */
    public int progressOf(Player p) { return progressOf(p, today()); }

    public boolean isComplete(Player p, Quest q) {
        return q != null && progressOf(p, q) >= q.requiredAmount();
    }

    // --- Event hooks -----------------------------------------------------------

    public void onBlockBroken(Player p, Material broken) {
        for (Quest q : todayQuests()) {
            if (q.type() == QuestType.BREAK_BLOCK && matchesTarget(q.targetMaterial(), broken)) advance(p, q, 1);
            else if (q.type() == QuestType.BREAK_ANY) advance(p, q, 1);
        }
    }

    /** A BREAK_BLOCK target also matches its deepslate ore variant (and vice versa). */
    private static boolean matchesTarget(Material target, Material broken) {
        if (target == null) return false;
        return target == broken || canonicalOre(target) == canonicalOre(broken);
    }

    /** Collapse a deepslate ore to its stone-ore equivalent so the two count as one. */
    private static Material canonicalOre(Material m) {
        return switch (m) {
            case DEEPSLATE_IRON_ORE -> Material.IRON_ORE;
            case DEEPSLATE_GOLD_ORE -> Material.GOLD_ORE;
            case DEEPSLATE_COAL_ORE -> Material.COAL_ORE;
            case DEEPSLATE_COPPER_ORE -> Material.COPPER_ORE;
            case DEEPSLATE_REDSTONE_ORE -> Material.REDSTONE_ORE;
            case DEEPSLATE_LAPIS_ORE -> Material.LAPIS_ORE;
            case DEEPSLATE_DIAMOND_ORE -> Material.DIAMOND_ORE;
            case DEEPSLATE_EMERALD_ORE -> Material.EMERALD_ORE;
            default -> m;
        };
    }

    public void onBossKilled(Player p) { advanceType(p, QuestType.KILL_BOSS, 1); }

    public void onLootRoomCompleted(Player p) { advanceType(p, QuestType.COMPLETE_LOOT_ROOM, 1); }

    public void onPhaseAdvanced(Player p) { advanceType(p, QuestType.ADVANCE_PHASE, 1); }

    public void onProphecyFulfilled(Player p) { advanceType(p, QuestType.FULFILL_PROPHECY, 1); }

    public void onCommunityBreak(Player p) { advanceType(p, QuestType.COMMUNITY_BREAK, 1); }

    /** A combo is a high-water mark, not an accumulation — record the best reached. */
    public void onComboReached(Player p, int combo) {
        for (Quest q : todayQuests()) {
            if (q.type() == QuestType.REACH_COMBO) recordHighWater(p, q, combo);
        }
    }

    /**
     * Entry point for activity originating in sibling plugins (OG OneBlock, xPets)
     * which signal NovaBlock through {@code NovaBlock#onExternalActivity}.
     */
    public void onExternalActivity(Player p, String key, int amount) {
        if (amount <= 0 || key == null) return;
        QuestType type = switch (key.toLowerCase(java.util.Locale.ROOT)) {
            case "og_break" -> QuestType.OG_BREAK;
            case "pet_gather" -> QuestType.PET_GATHER;
            default -> null;
        };
        if (type != null) advanceType(p, type, amount);
    }

    private void advanceType(Player p, QuestType type, int amount) {
        for (Quest q : todayQuests()) if (q.type() == type) advance(p, q, amount);
    }

    private void advance(Player p, Quest q, int amount) {
        PlayerProgression prog = plugin.progression().get(p);
        ensureDay(prog);
        int before = prog.getQuestProgress(q.id());
        if (before >= q.requiredAmount()) return;
        int after = Math.min(q.requiredAmount(), before + amount);
        prog.setQuestProgress(q.id(), after);
        if (after >= q.requiredAmount()) reward(p, q, prog);
    }

    private void recordHighWater(Player p, Quest q, int value) {
        PlayerProgression prog = plugin.progression().get(p);
        ensureDay(prog);
        int before = prog.getQuestProgress(q.id());
        if (before >= q.requiredAmount()) return;
        int after = Math.min(q.requiredAmount(), Math.max(before, value));
        if (after == before) return;
        prog.setQuestProgress(q.id(), after);
        if (after >= q.requiredAmount()) reward(p, q, prog);
    }

    private void reward(Player p, Quest q, PlayerProgression prog) {
        long coins = q.coinReward();
        long xp = q.xpReward();
        // GAMBLER (Luck 30) bumps daily quest rewards by 50%.
        boolean gambler = com.nova.novablock.progression.Perk.hasPerk(prog,
                com.nova.novablock.progression.Perk.GAMBLER);
        if (gambler) {
            coins = Math.round(coins * 1.50);
            xp = Math.round(xp * 1.50);
        }
        var island = plugin.islands().ofPlayer(p);
        if (island != null) plugin.economy().award(island, coins);
        plugin.progression().addXp(p, SkillType.MINING, xp);
        Msg.title(p, "<gold>★ Quest Complete",
                "<yellow>" + q.displayName() + " <gray>· +" + coins + " coins, +" + xp + " XP"
                        + (gambler ? " <#FFD24D>(Gambler)" : ""));
        p.playSound(p.getLocation(), org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        plugin.seasonalPaths().award(p, com.nova.novablock.season.SeasonalPathManager.PathSource.QUEST, 75);
        plugin.sprint().recordQuestCompleted(p.getUniqueId());
    }
}
