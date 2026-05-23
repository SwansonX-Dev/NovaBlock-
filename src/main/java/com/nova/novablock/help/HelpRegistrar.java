package com.nova.novablock.help;

import com.nova.novablock.NovaBlock;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.help.HelpTopic;

/**
 * Registers a NovaBlock entry in Bukkit's /help index so players typing /help
 * or /help novablock get a complete command reference and feature overview.
 */
public final class HelpRegistrar {

    private HelpRegistrar() {}

    public static void register(NovaBlock plugin) {
        try {
            HelpTopic novablock = new NovaHelpTopic();
            Bukkit.getHelpMap().addTopic(novablock);

            // Also alias /help nb and /help ob so people can find it quickly.
            Bukkit.getHelpMap().addTopic(new AliasHelpTopic("nb", "novablock", Bukkit.getHelpMap()));
            Bukkit.getHelpMap().addTopic(new AliasHelpTopic("ob", "novablock", Bukkit.getHelpMap()));
        } catch (Throwable t) {
            plugin.getLogger().warning("Failed to register /help entries: " + t.getMessage());
        }
    }

    private static final class NovaHelpTopic extends HelpTopic {
        NovaHelpTopic() {
            this.name = "novablock";
            this.shortText = "NovaBlock — phases, bosses, paxel, skills";
            StringBuilder sb = new StringBuilder();
            sb.append("§6§lNovaBlock\n");
            sb.append("§7Mine your OneBlock through §e12 themed phases§7 — fight bosses, ");
            sb.append("dive into loot-room rifts, and level skills.\n\n");
            sb.append("§e/ob §7or §e/nb §7— open the main menu\n");
            sb.append("§e/ob create §7— claim your island\n");
            sb.append("§e/ob home §7— teleport to your OneBlock\n");
            sb.append("§e/ob prophecy §7— see and lock upcoming blocks\n");
            sb.append("§e/ob skills §7— skill trees and perks\n");
            sb.append("§e/ob shop §7— spend coins\n");
            sb.append("§e/ob quest §7— today's daily challenge\n");
            sb.append("§e/ob leaderboard §7— top islands\n");
            sb.append("§e/ob phase §7— current phase status\n");
            sb.append("§e/ob invite §6<player> §7— team up\n");
            sb.append("§e/ob accept §7— accept a pending invite\n");
            sb.append("§e/ob toggle §7— show/hide the menu hotbar item\n");
            sb.append("§e/sb §7— toggle the sidebar scoreboard\n\n");
            sb.append("§7Admin: §c/obadmin reload | setphase | spawnboss | givecoins | event | wipe\n");
            this.fullText = sb.toString();
        }
        @Override public boolean canSee(CommandSender sender) { return true; }
    }

    /** Lightweight alias that points one help name at another. */
    private static final class AliasHelpTopic extends HelpTopic {
        private final String aliasFor;
        private final org.bukkit.help.HelpMap helpMap;

        AliasHelpTopic(String name, String aliasFor, org.bukkit.help.HelpMap helpMap) {
            this.name = name;
            this.aliasFor = aliasFor;
            this.helpMap = helpMap;
            this.shortText = "Alias for /help " + aliasFor;
            this.fullText = this.shortText;
        }

        @Override public boolean canSee(CommandSender s) { return true; }

        @Override
        public String getFullText(CommandSender forWhom) {
            HelpTopic target = helpMap.getHelpTopic(aliasFor);
            return target == null ? this.fullText : target.getFullText(forWhom);
        }
    }
}
