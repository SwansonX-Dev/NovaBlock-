package com.nova.novablock.quest;

import com.nova.novablock.NovaBlock;
import com.nova.novablock.progression.PlayerProgression;
import com.nova.novablock.progression.SkillType;
import com.nova.novablock.util.Msg;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class QuestManager {

    private final NovaBlock plugin;
    private final List<Quest> dailyPool = new ArrayList<>();
    private Quest todayQuest;
    private long currentDayStamp;

    public QuestManager(NovaBlock plugin) { this.plugin = plugin; }

    public void loadDailyQuests() {
        dailyPool.clear();
        dailyPool.add(new Quest("d_stone", "Stonebreaker",
                "Break 200 stone.", Quest.QuestType.BREAK_BLOCK, Material.STONE, 200, 500, 200));
        dailyPool.add(new Quest("d_iron", "Iron-Bound",
                "Break 40 iron ore.", Quest.QuestType.BREAK_BLOCK, Material.IRON_ORE, 40, 800, 300));
        dailyPool.add(new Quest("d_diamond", "Diamond Hunter",
                "Break 8 diamond ore.", Quest.QuestType.BREAK_BLOCK, Material.DIAMOND_ORE, 8, 1500, 500));
        dailyPool.add(new Quest("d_grind", "Grinder",
                "Break 500 blocks of any kind.", Quest.QuestType.BREAK_ANY, Material.AIR, 500, 750, 250));
        dailyPool.add(new Quest("d_rift", "Riftwalker",
                "Clear 1 loot room.", Quest.QuestType.COMPLETE_LOOT_ROOM, Material.END_PORTAL_FRAME, 1, 1200, 400));
        dailyPool.add(new Quest("d_boss", "Boss Slayer",
                "Defeat 1 boss.", Quest.QuestType.KILL_BOSS, Material.NETHERITE_SWORD, 1, 2000, 800));
        rollDaily();
    }

    public void rollDaily() {
        long day = LocalDate.now().toEpochDay();
        if (day == currentDayStamp && todayQuest != null) return;
        currentDayStamp = day;
        todayQuest = dailyPool.get(ThreadLocalRandom.current().nextInt(dailyPool.size()));
        plugin.getLogger().info("Today's quest: " + todayQuest.displayName());
    }

    public Quest today() { rollDaily(); return todayQuest; }
    public long dayStamp() { rollDaily(); return currentDayStamp; }

    public int progressOf(Player p) {
        PlayerProgression prog = plugin.progression().get(p);
        if (prog.getQuestDayStamp() != dayStamp()) {
            prog.setQuestDayStamp(dayStamp());
            prog.setQuestProgress(0);
        }
        return prog.getQuestProgress();
    }

    public void onBlockBroken(Player p, Material broken) {
        Quest q = today();
        if (q.type() == Quest.QuestType.BREAK_BLOCK && broken == q.targetMaterial()) advance(p, 1);
        if (q.type() == Quest.QuestType.BREAK_ANY) advance(p, 1);
    }

    public void onBossKilled(Player p) {
        Quest q = today();
        if (q.type() == Quest.QuestType.KILL_BOSS) advance(p, 1);
    }

    public void onLootRoomCompleted(Player p) {
        Quest q = today();
        if (q.type() == Quest.QuestType.COMPLETE_LOOT_ROOM) advance(p, 1);
    }

    private void advance(Player p, int amount) {
        Quest q = today();
        PlayerProgression prog = plugin.progression().get(p);
        int before = progressOf(p);
        int after = Math.min(q.requiredAmount(), before + amount);
        prog.setQuestProgress(after);
        if (before < q.requiredAmount() && after >= q.requiredAmount()) {
            // Reward
            var island = plugin.islands().ofPlayer(p);
            if (island != null) plugin.economy().award(island, q.coinReward());
            plugin.progression().addXp(p, SkillType.MINING, q.xpReward());
            Msg.title(p, "<gold>★ Daily Quest Complete", "<yellow>+" + q.coinReward() + " coins, +" + q.xpReward() + " XP");
            p.playSound(p.getLocation(), org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        }
    }
}
