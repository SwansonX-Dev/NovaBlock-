package com.nova.novablock.chat;

import com.nova.novablock.gui.ChestGui;
import com.nova.novablock.util.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Read-only viewer for a {@link ChatTagManager.Snapshot}.
 *
 * <p>{@link ChestGui#handleClick} cancels every click and {@code GuiManager} cancels
 * drags, so nothing here can be taken — items are shown with no click handler attached.
 */
public class SnapshotGui extends ChestGui {

    /** Player inventory layout: 0-35 storage (0-8 hotbar), 36-39 armour, 40 offhand. */
    private static final int STORAGE_END = 36;
    private static final int ARMOUR_START = 36;
    private static final int OFFHAND = 40;

    private final ChatTagManager.Snapshot snapshot;

    public SnapshotGui(ChatTagManager.Snapshot snapshot) {
        super(title(snapshot), snapshot.kind() == ChatTagManager.Kind.ENDER ? 3 : 6);
        this.snapshot = snapshot;
    }

    private static String title(ChatTagManager.Snapshot s) {
        String what = s.kind() == ChatTagManager.Kind.ENDER ? "Ender Chest" : "Inventory";
        return "<dark_gray>" + s.ownerName() + "'s <white>" + what;
    }

    @Override
    protected void build(Player viewer) {
        if (snapshot.kind() == ChatTagManager.Kind.ENDER) {
            buildEnder();
        } else {
            buildInventory();
        }
    }

    private void buildEnder() {
        ItemStack[] c = snapshot.contents();
        for (int i = 0; i < Math.min(c.length, size()); i++) set(i, c[i], null);
    }

    /**
     * Mirror the real inventory layout rather than dumping slots in raw order: main
     * storage on top, hotbar on its own row beneath it, then armour and offhand — so it
     * reads the way the sender's screen actually looked.
     */
    private void buildInventory() {
        ItemStack[] c = snapshot.contents();

        // Rows 1-3: main storage (player slots 9-35) -> GUI 0-26
        for (int i = 9; i < STORAGE_END && i < c.length; i++) set(i - 9, c[i], null);
        // Row 4: hotbar (player slots 0-8) -> GUI 27-35
        for (int i = 0; i < 9 && i < c.length; i++) set(27 + i, c[i], null);

        // Row 5: separator
        ItemStack divider = ItemBuilder.of(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 36; i < 45; i++) set(i, divider, null);

        // Row 6: armour then offhand. getContents() stores armour boots-first, so walk it
        // backwards to show helmet -> boots the way the player's own armour column reads.
        for (int i = ARMOUR_START; i < OFFHAND && i < c.length; i++) {
            set(45 + (OFFHAND - 1 - i), c[i], null);
        }
        if (c.length > OFFHAND) set(49, c[OFFHAND], null);

        // Pad only the tail of the armour row. A blanket fill() would also plug every
        // empty storage slot, making a nearly-empty inventory look packed with panes.
        ItemStack pad = ItemBuilder.of(Material.BLACK_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 50; i < size(); i++) set(i, pad, null);
    }
}
