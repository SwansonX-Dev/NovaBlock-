package com.nova.novablock.boss;

import com.nova.novablock.island.Island;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

public interface Boss {

    /** Stable identifier referenced by phases.yml / config. */
    String id();
    String displayName();
    String themeColor();

    /** Spawn the boss for an island. Returns the spawned BossFight handle. */
    BossFight spawn(Island island, Player triggeringPlayer);

    /** Per-tick mechanic update for an active fight. */
    void onTick(BossFight fight);

    /** Called when the boss takes damage from a player. */
    default void onDamaged(BossFight fight, Player by, double amount) {}

    /** Called when the boss is killed. Return reward coins. */
    default long onDefeat(BossFight fight) { return 1000L; }

    /** Convenience: build a default bossbar component. */
    default BossBar buildBar(LivingEntity entity) {
        return BossBar.bossBar(
                com.nova.novablock.util.Msg.mm("<" + themeColor() + "><bold>" + displayName()),
                1.0f,
                BossBar.Color.PURPLE,
                BossBar.Overlay.PROGRESS);
    }
}
