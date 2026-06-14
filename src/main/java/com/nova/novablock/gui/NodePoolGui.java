package com.nova.novablock.gui;

import com.nova.novablock.NovaBlock;
import com.nova.novablock.community.CommunityNodeType;
import com.nova.novablock.community.CommunityNodeType.Weighted;
import com.nova.novablock.community.NodePoolManager;
import com.nova.novablock.util.ItemBuilder;
import com.nova.novablock.util.Msg;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * Admin editor for the Personal OneBlock node pools. With no type it shows a
 * selector (one icon per type); picking a type opens its editor — every block in
 * the pool is shown as a clickable item (click to remove), plus an "add held
 * block" button, a reset-to-default button, and back/nav. Changes persist via
 * {@link NodePoolManager}. Opened from {@code /obadmin nodepool [type]}.
 */
public class NodePoolGui extends ChestGui {

    private static final int PER_PAGE = 45; // top 5 rows; bottom row is controls

    private final NovaBlock plugin;
    private final CommunityNodeType type; // null = selector
    private final int page;

    public NodePoolGui(NovaBlock plugin) { this(plugin, null, 0); }

    public NodePoolGui(NovaBlock plugin, CommunityNodeType type, int page) {
        super(type == null
                ? "<gradient:#7B61FF:#4FC3F7><bold>Node Pools"
                : type.colorGradient() + "<bold>" + type.displayName() + " Pool", 6);
        this.plugin = plugin;
        this.type = type;
        this.page = Math.max(0, page);
    }

    @Override
    protected void build(Player p) {
        if (type == null) buildSelector(p);
        else buildEditor(p);
        fill(Material.GRAY_STAINED_GLASS_PANE, " ");
    }

    private void buildSelector(Player p) {
        NodePoolManager mgr = plugin.nodePools();
        int slot = 0;
        for (CommunityNodeType t : CommunityNodeType.values()) {
            int count = mgr.pool(t).size();
            set(slot++, ItemBuilder.of(t.icon())
                    .name(t.colorGradient() + "<bold>" + t.displayName())
                    .lore("<gray>" + count + " block" + (count == 1 ? "" : "s") + " in pool",
                            t.dropMode() == CommunityNodeType.DropMode.VANILLA
                                    ? "<dark_gray>Drops: natural (ores → raw)"
                                    : "<dark_gray>Drops: the block itself",
                            mgr.isCustomised(t) ? "<aqua>Customised" : "<dark_gray>Default pool",
                            " ",
                            "<yellow>Click to edit blocks")
                    .build(),
                    e -> new NodePoolGui(plugin, t, 0).open(p));
        }
        set(49, ItemBuilder.of(Material.NETHER_STAR)
                .name("<gradient:#7B61FF:#4FC3F7><bold>Admin Menu")
                .lore("<dark_gray>/obadmin").glow().build(),
                e -> p.closeInventory());
    }

    private void buildEditor(Player p) {
        NodePoolManager mgr = plugin.nodePools();
        List<Weighted> pool = mgr.pool(type);

        int maxPage = pool.isEmpty() ? 0 : (pool.size() - 1) / PER_PAGE;
        int safePage = Math.min(page, maxPage);
        int start = safePage * PER_PAGE;
        int end = Math.min(start + PER_PAGE, pool.size());

        int slot = 0;
        for (int i = start; i < end; i++) {
            Weighted w = pool.get(i);
            Material m = w.material();
            ItemStack icon = (m.isItem() ? ItemBuilder.of(m) : ItemBuilder.of(Material.PAPER))
                    .name("<white>" + pretty(m))
                    .lore("<gray>Weight: <yellow>" + w.weight(),
                            " ",
                            "<red>Click to remove")
                    .build();
            set(slot++, icon, click -> {
                mgr.removeMaterial(type, m);
                Msg.send(p, "<gray>Removed <white>" + pretty(m) + "<gray> from the " + type.displayName() + " pool.");
                new NodePoolGui(plugin, type, safePage).open(p);
            });
        }

        // Bottom-row controls.
        if (safePage > 0) {
            set(45, ItemBuilder.of(Material.ARROW).name("<yellow>Previous page")
                    .lore("<gray>Page " + safePage + " / " + (maxPage + 1)).build(),
                    e -> new NodePoolGui(plugin, type, safePage - 1).open(p));
        }
        if (safePage < maxPage) {
            set(53, ItemBuilder.of(Material.ARROW).name("<yellow>Next page")
                    .lore("<gray>Page " + (safePage + 2) + " / " + (maxPage + 1)).build(),
                    e -> new NodePoolGui(plugin, type, safePage + 1).open(p));
        }

        set(47, ItemBuilder.of(Material.LIME_DYE)
                .name("<green><bold>Add held block")
                .lore("<gray>Hold a block in your main hand,",
                        "<gray>then click to add it to this pool",
                        "<gray>(weight " + NodePoolManager.DEFAULT_WEIGHT + ").").glow().build(),
                e -> {
                    ItemStack hand = p.getInventory().getItemInMainHand();
                    if (hand == null || hand.getType().isAir() || !hand.getType().isBlock()) {
                        Msg.send(p, "<red>Hold a placeable block in your main hand to add it.");
                        return;
                    }
                    Material m = hand.getType();
                    boolean added = mgr.addMaterial(type, m, NodePoolManager.DEFAULT_WEIGHT);
                    Msg.send(p, added
                            ? "<green>Added <white>" + pretty(m) + "<green> to the " + type.displayName() + " pool."
                            : "<yellow>" + pretty(m) + " was already in the pool — weight reset to " + NodePoolManager.DEFAULT_WEIGHT + ".");
                    new NodePoolGui(plugin, type, safePage).open(p);
                });

        set(49, ItemBuilder.of(Material.OAK_DOOR)
                .name("<yellow>Back")
                .lore("<dark_gray>Node Pools").build(),
                e -> new NodePoolGui(plugin).open(p));

        set(51, ItemBuilder.of(Material.TNT)
                .name("<red><bold>Reset to default")
                .lore("<gray>Restore this type's original blocks.",
                        mgr.isCustomised(type) ? "<dark_gray>Currently customised." : "<dark_gray>Already at default.")
                .build(),
                e -> {
                    mgr.resetToDefault(type);
                    Msg.send(p, "<gray>Reset the " + type.displayName() + " pool to default.");
                    new NodePoolGui(plugin, type, 0).open(p);
                });
    }

    private static String pretty(Material m) {
        StringBuilder out = new StringBuilder();
        for (String word : m.name().toLowerCase().split("_")) {
            if (out.length() > 0) out.append(' ');
            out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return out.toString();
    }
}
