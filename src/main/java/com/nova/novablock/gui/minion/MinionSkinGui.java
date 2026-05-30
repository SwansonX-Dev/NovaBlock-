package com.nova.novablock.gui.minion;

import com.nova.novablock.NovaBlock;
import com.nova.novablock.gui.ChestGui;
import com.nova.novablock.minion.MinionData;
import com.nova.novablock.minion.MinionSkin;
import com.nova.novablock.util.ItemBuilder;
import com.nova.novablock.util.Msg;
import org.bukkit.Material;
import org.bukkit.entity.Player;

public class MinionSkinGui extends ChestGui {
    private final NovaBlock plugin;
    private final java.util.UUID minionId;
    public MinionSkinGui(NovaBlock plugin, java.util.UUID minionId) { super("<dark_gray>Minion Skins", 4); this.plugin = plugin; this.minionId = minionId; }
    @Override protected void build(Player player) {
        fill(Material.GRAY_STAINED_GLASS_PANE, " ");
        MinionData data = plugin.minions().get(minionId);
        if (data == null) return;
        int[] slots = {10,11,12,13,14,15,16};
        MinionSkin[] skins = MinionSkin.values();
        for (int i = 0; i < skins.length; i++) {
            MinionSkin skin = skins[i];
            boolean unlocked = plugin.minions().canUseSkin(player, skin);
            boolean selected = skin.id().equalsIgnoreCase(data.skin());
            set(slots[i], ItemBuilder.of(skin.displayMaterial(data.type())).name(skin.nameColor() + skin.displayName()).lore(selected ? "<green>Selected." : unlocked ? "<gray>Click to apply." : "<red>Locked.").glow().hideFlags().build(), e -> {
                if (!unlocked) { Msg.send(player, "<red>That skin is locked."); return; }
                plugin.minions().setSkin(player, data, skin); open(player);
            });
        }
        set(31, ItemBuilder.of(Material.ARROW).name("<yellow>Back").build(), e -> new MinionControlGui(plugin, minionId).open(player));
    }
}
