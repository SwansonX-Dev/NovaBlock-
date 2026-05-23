package com.nova.novablock.compat;

import com.nova.novablock.NovaBlock;
import com.nova.novablock.island.Island;
import com.nova.novablock.pet.Pet;
import com.nova.novablock.phase.Phase;
import com.nova.novablock.progression.PlayerProgression;
import com.nova.novablock.progression.SkillType;
import com.nova.novablock.quest.Quest;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

/**
 * PlaceholderAPI expansion. Registered only if PlaceholderAPI is installed.
 *
 * Available placeholders (all prefixed with %novablock_…%):
 *   phase, phase_name, phase_progress, phase_required
 *   blocks, coins
 *   skill_mining_level, skill_combat_level, skill_magic_level, skill_luck_level
 *   pet_name, pet_task, pet_level
 *   quest_name, quest_progress, quest_required
 *   event_name, event_seconds_left
 */
public class PlaceholderHook extends PlaceholderExpansion {

    private final NovaBlock plugin;

    public PlaceholderHook(NovaBlock plugin) { this.plugin = plugin; }

    @Override public String getIdentifier() { return "novablock"; }
    @Override public String getAuthor() { return "Nova"; }
    @Override public String getVersion() { return plugin.getDescription().getVersion(); }
    @Override public boolean persist() { return true; }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        if (player == null) return "";
        String key = params.toLowerCase();

        // Server-wide event placeholders
        if (key.equals("event_name")) {
            var ev = plugin.seasons().active();
            return ev == null ? "" : ev.displayName;
        }
        if (key.equals("event_seconds_left")) {
            var ev = plugin.seasons().active();
            if (ev == null) return "0";
            return String.valueOf(Math.max(0, (plugin.seasons().activeUntil() - System.currentTimeMillis()) / 1000));
        }

        Island island = plugin.islands().ofPlayer(player.getUniqueId());

        switch (key) {
            case "phase" -> { return island == null ? "0" : String.valueOf(island.data().getPhaseIndex() + 1); }
            case "phase_name" -> {
                if (island == null) return "";
                Phase phase = plugin.phases().getOrLast(island.data().getPhaseIndex());
                return phase == null ? "" : phase.getDisplayName();
            }
            case "phase_progress" -> { return island == null ? "0" : String.valueOf(island.data().getPhaseProgress()); }
            case "phase_required" -> {
                if (island == null) return "0";
                Phase phase = plugin.phases().getOrLast(island.data().getPhaseIndex());
                return phase == null ? "0" : String.valueOf(phase.getRequiredBlocks());
            }
            case "blocks" -> { return island == null ? "0" : String.valueOf(island.data().getBlocksBroken()); }
            case "coins" -> { return island == null ? "0" : String.valueOf(island.data().getCoins()); }
        }

        if (key.startsWith("skill_") && key.endsWith("_level")) {
            String name = key.substring("skill_".length(), key.length() - "_level".length());
            try {
                SkillType type = SkillType.valueOf(name.toUpperCase());
                PlayerProgression prog = plugin.progression().get(player.getUniqueId());
                return String.valueOf(prog.getLevel(type));
            } catch (IllegalArgumentException ignored) { return ""; }
        }

        if (key.startsWith("pet_")) {
            if (!player.isOnline()) return "";
            Pet pet = plugin.pets().getActive(player.getUniqueId());
            if (pet == null) return "";
            return switch (key) {
                case "pet_name" -> pet.customName() != null ? pet.customName() : pet.type().displayName;
                case "pet_task" -> pet.task().displayName;
                case "pet_level" -> String.valueOf(pet.level());
                default -> "";
            };
        }

        if (key.startsWith("quest_")) {
            Quest q = plugin.quests().today();
            if (q == null) return "";
            var online = player.isOnline() ? Bukkit.getPlayer(player.getUniqueId()) : null;
            return switch (key) {
                case "quest_name" -> q.displayName();
                case "quest_progress" -> online == null ? "0" : String.valueOf(plugin.quests().progressOf(online));
                case "quest_required" -> String.valueOf(q.requiredAmount());
                default -> "";
            };
        }

        return null;
    }
}
