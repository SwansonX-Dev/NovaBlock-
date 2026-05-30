package com.nova.novablock.gui.minion;

import com.nova.novablock.NovaBlock;
import com.nova.novablock.gui.ChestGui;
import com.nova.novablock.util.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.entity.Player;

public class MinionHelpGui extends ChestGui {
    private final NovaBlock plugin;
    public MinionHelpGui(NovaBlock plugin) { super("<dark_gray>Minion Help", 4); this.plugin = plugin; }
    @Override protected void build(Player player) {
        fill(Material.GRAY_STAINED_GLASS_PANE, " ");
        set(4, ItemBuilder.of(Material.NETHER_STAR).name("<gold>NovaBlock Minions").lore("<gray>Buy, place, link, fuel, upgrade, and manage", "<gray>island minions from chest-friendly menus.").build(), null);
        set(10, ItemBuilder.of(Material.EMERALD).name("<green>Buy Minions").lore("<gray>Open <yellow>/minions shop</yellow>.", "<gray>Minions are phase locked and use coins.").build(), e -> new MinionShopGui(plugin).open(player));
        set(11, ItemBuilder.of(Material.ARMOR_STAND).name("<yellow>Place And Open").lore("<gray>Right-click your island with a minion item.", "<gray>Right-click the display to manage it.").build(), null);
        set(12, ItemBuilder.of(Material.CHEST).name("<aqua>Link Output").lore("<gray>Click <yellow>Link Chest</yellow>, then punch", "<gray>a chest, trapped chest, or barrel.").build(), null);
        set(13, ItemBuilder.of(Material.COAL).name("<gold>Fuel").lore("<gray>Fuel is optional.", "<gray>Hold fuel and click <yellow>Add Fuel</yellow>.").build(), null);
        set(14, ItemBuilder.of(Material.ANVIL).name("<light_purple>Upgrades").lore("<gray>Speed, Yield, Fuel Efficiency,", "<gray>and Compactor cost coins.").build(), null);
        set(15, ItemBuilder.of(Material.MAP).name("<green>Overview").lore("<gray>Open <yellow>/minions</yellow> to view all", "<gray>placed island minions.").build(), e -> new MinionOverviewGui(plugin).open(player));
        set(16, ItemBuilder.of(Material.REDSTONE_TORCH).name("<red>Paused States").lore("<gray>Paused if offline, unlinked, full,", "<gray>missing, outside island, or unloaded.").build(), null);
        set(22, ItemBuilder.of(Material.ENDER_PEARL).name("<yellow>Move Safely").lore("<gray>Move keeps fuel, upgrades, skin,", "<gray>linked chest, and logs intact.").build(), null);
        set(23, ItemBuilder.of(Material.BOOK).name("<aqua>Types").lore("<gray>Open <yellow>/minions types</yellow> to view", "<gray>all minions, prices, unlocks, and drops.").build(), e -> new MinionTypesGui(plugin).open(player));
        if (player.hasPermission("novablock.minions.admin") || player.hasPermission("novablock.admin.minions")) {
            set(31, ItemBuilder.of(Material.WRITABLE_BOOK).name("<aqua>Admin Output Editor").lore("<gray>Open <yellow>/minions outputs</yellow>.", "<gray>Edit drops, weights, amounts, rare caps.").build(), e -> new MinionOutputAdminGui(plugin).open(player));
            set(32, ItemBuilder.of(Material.SPYGLASS).name("<gold>Admin Debug").lore("<gray>Open <yellow>/minions debug <player></yellow>", "<gray>to inspect placed minions and links.").build(), null);
        }
    }
}
