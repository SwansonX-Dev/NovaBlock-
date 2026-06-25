package com.nova.novablock.ability;

import com.nova.novablock.NovaBlock;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.EnumSet;
import java.util.Set;

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
        Material tool = p.getInventory().getItemInMainHand().getType();
        ActiveAbility ability = ActiveAbility.forTool(tool);
        if (ability == null) return;
        // Don't hijack a vanilla tool-on-block interaction (axe strips a log / scrapes
        // copper / removes wax, shovel makes a dirt path, hoe tills soil). Arming there
        // would pester the player — and risk eating — the interaction they actually want.
        // They can still ready the ability by right-clicking air or any inert block.
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK
                && event.getClickedBlock() != null
                && toolInteractsWith(tool, event.getClickedBlock())) {
            return;
        }
        plugin.abilities().ready(p, ability);
    }

    /** Soil a hoe tills into farmland (or dirt, for grass paths). */
    private static final Set<Material> TILLABLE = EnumSet.of(
            Material.GRASS_BLOCK, Material.DIRT, Material.COARSE_DIRT,
            Material.PODZOL, Material.MYCELIUM, Material.ROOTED_DIRT, Material.DIRT_PATH);

    /** Blocks a shovel turns into a dirt path. */
    private static final Set<Material> PATHABLE = EnumSet.of(
            Material.GRASS_BLOCK, Material.DIRT, Material.COARSE_DIRT,
            Material.PODZOL, Material.MYCELIUM, Material.ROOTED_DIRT);

    /** True when right-clicking {@code block} with {@code tool} triggers a vanilla interaction. */
    private boolean toolInteractsWith(Material tool, Block block) {
        String t = tool.name();
        Material b = block.getType();
        if (t.endsWith("_AXE")) {
            // Strippable logs/wood/stems/hyphae, plus copper scraping and wax removal.
            return Tag.LOGS.isTagged(b) || b.name().contains("COPPER") || b.name().startsWith("WAXED_");
        }
        if (t.endsWith("_HOE")) return TILLABLE.contains(b);
        if (t.endsWith("_SHOVEL")) return PATHABLE.contains(b) || b == Material.CAMPFIRE || b == Material.SOUL_CAMPFIRE;
        return false;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.abilities().clear(event.getPlayer().getUniqueId());
    }
}
