package com.nova.novablock.progression;

import com.nova.novablock.NovaBlock;
import com.nova.novablock.util.Msg;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ProgressionManager {

    private final NovaBlock plugin;
    private final Map<UUID, PlayerProgression> cache = new HashMap<>();

    public ProgressionManager(NovaBlock plugin) { this.plugin = plugin; }

    public PlayerProgression get(UUID id) {
        return cache.computeIfAbsent(id, plugin.storage()::loadProgression);
    }

    public PlayerProgression get(Player p) { return get(p.getUniqueId()); }

    public void save(UUID id) {
        PlayerProgression p = cache.get(id);
        if (p != null) plugin.storage().saveProgression(p);
    }

    public void saveAll() {
        for (PlayerProgression p : cache.values()) plugin.storage().saveProgression(p);
    }

    public void unload(UUID id) {
        save(id);
        cache.remove(id);
    }

    public void delete(UUID id) {
        cache.remove(id);
        plugin.storage().deleteProgression(id);
    }

    public void addXp(Player player, SkillType skill, long amount) {
        PlayerProgression p = get(player);
        p.setXp(skill, p.getXp(skill) + amount);
        while (p.getXp(skill) >= PlayerProgression.xpForLevel(p.getLevel(skill))) {
            p.setXp(skill, p.getXp(skill) - PlayerProgression.xpForLevel(p.getLevel(skill)));
            p.setLevel(skill, p.getLevel(skill) + 1);
            int newLevel = p.getLevel(skill);
            Msg.title(player, "<" + skill.color() + ">+ " + skill.displayName() + " Lv " + newLevel,
                    "<gray>Check /ob skills for perks");
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.4f);
            // Notify if perk unlocked at this level
            for (Perk perk : Perk.values()) {
                if (perk.skill == skill && perk.requiredLevel == newLevel) {
                    Msg.send(player, "<gold>Unlocked: <yellow>" + perk.name + " <gray>– " + perk.description);
                }
            }
        }
    }
}
