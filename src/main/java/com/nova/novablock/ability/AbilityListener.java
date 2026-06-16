package com.nova.novablock.ability;

import com.nova.novablock.NovaBlock;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Arms abilities when a player right-clicks while holding the matching tool.
 * Activation (on break / attack) is driven by the gathering and combat hooks,
 * which call {@link AbilityManager#tryActivate}.
 */
public class AbilityListener implements Listener {

    private final NovaBlock plugin;

    public AbilityListener(NovaBlock plugin) { this.plugin = plugin; }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;
        Player p = event.getPlayer();
        if (!p.hasPermission("novablock.skills")) return;
        ActiveAbility ability = ActiveAbility.forTool(p.getInventory().getItemInMainHand().getType());
        if (ability == null) return;
        plugin.abilities().ready(p, ability);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.abilities().clear(event.getPlayer().getUniqueId());
    }
}
