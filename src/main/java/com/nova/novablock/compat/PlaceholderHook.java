package com.nova.novablock.compat;

import com.nova.novablock.NovaBlock;
import com.nova.novablock.island.Island;
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
 *   island_level, island_points, island_level_progress, island_level_required
 *   skill_<name>_<field> where name = mining|combat|magic|luck|farming|fishing|woodcutting|excavation
 *                          and field = level|xp|required|max|passive
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
            case "coins" -> { return island == null ? "0" : String.valueOf(plugin.economy().balance(island)); }
            case "island_level" -> { return island == null ? "1" : String.valueOf(plugin.islands().levelOf(island)); }
            case "island_points" -> {
                return island == null ? "0"
                        : String.valueOf(com.nova.novablock.island.IslandLevel.points(island.data()));
            }
            case "island_level_progress" -> {
                return island == null ? "0"
                        : String.valueOf(com.nova.novablock.island.IslandLevel.progressInLevel(island.data()));
            }
            case "island_level_required" -> {
                return island == null ? "0"
                        : String.valueOf(com.nova.novablock.island.IslandLevel.pointsThisLevel(island.data()));
            }
            // prestige_level is the stacked total across all three dimensions (back-compat).
            case "prestige_level" -> { return island == null ? "0" : String.valueOf(island.data().getTotalPrestigeLevel()); }
            case "prestige_overworld" -> { return island == null ? "0" : String.valueOf(island.data().getPrestigeLevel(com.nova.novablock.island.Dimension.OVERWORLD)); }
            case "prestige_nether" -> { return island == null ? "0" : String.valueOf(island.data().getPrestigeLevel(com.nova.novablock.island.Dimension.NETHER)); }
            case "prestige_end" -> { return island == null ? "0" : String.valueOf(island.data().getPrestigeLevel(com.nova.novablock.island.Dimension.END)); }
            case "prestige_title" -> {
                int lvl = island == null ? 0 : island.data().getTotalPrestigeLevel();
                return lvl <= 0 ? "" : plugin.prestige().title(lvl);
            }
            case "event_color" -> {
                var ev = plugin.seasons().active();
                return ev == null ? "<gray>" : ev.color;
            }
            case "login_streak" -> {
                return String.valueOf(plugin.progression().get(player.getUniqueId()).getLoginStreak());
            }
            case "path_name" -> { return plugin.seasonalPaths().activePath().name(); }
            case "path_tier" -> {
                PlayerProgression prog = plugin.progression().get(player.getUniqueId());
                return String.valueOf(plugin.seasonalPaths().tierFor(prog.getSeasonalPathPoints()));
            }
            case "path_points" -> {
                return String.valueOf(plugin.progression().get(player.getUniqueId()).getSeasonalPathPoints());
            }
            case "path_pet" -> { return plugin.seasonalPaths().activePath().petId(); }
            case "path_tag" -> { return plugin.seasonalPaths().activePath().tagId(); }
            case "atlas_score" -> {
                return String.valueOf(plugin.progression().get(player.getUniqueId()).getAtlasScore());
            }
            case "atlas_title" -> {
                return plugin.seasonalPaths().atlasTitle(plugin.progression().get(player.getUniqueId()));
            }
        }

        if (key.startsWith("skill_")) {
            String rest = key.substring("skill_".length());
            int sep = rest.lastIndexOf('_');
            if (sep > 0) {
                String name = rest.substring(0, sep);
                String field = rest.substring(sep + 1);
                try {
                    SkillType type = SkillType.valueOf(name.toUpperCase());
                    PlayerProgression prog = plugin.progression().get(player.getUniqueId());
                    return switch (field) {
                        case "level" -> String.valueOf(prog.getLevel(type));
                        case "xp" -> String.valueOf(prog.getXp(type));
                        case "required" -> String.valueOf(PlayerProgression.xpForLevel(prog.getLevel(type)));
                        case "max" -> String.valueOf(PlayerProgression.maxLevel());
                        case "passive" -> String.format("%.1f",
                                com.nova.novablock.progression.Passives.chance(prog, type) * 100);
                        default -> "";
                    };
                } catch (IllegalArgumentException ignored) { return ""; }
            }
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
