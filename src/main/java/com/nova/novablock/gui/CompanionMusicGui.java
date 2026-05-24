package com.nova.novablock.gui;

import com.nova.novablock.NovaBlock;
import com.nova.novablock.util.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

public class CompanionMusicGui extends ChestGui {

    private static final List<DiscButton> DISCS = List.of(
            new DiscButton(Material.MUSIC_DISC_13, Sound.MUSIC_DISC_13),
            new DiscButton(Material.MUSIC_DISC_CAT, Sound.MUSIC_DISC_CAT),
            new DiscButton(Material.MUSIC_DISC_BLOCKS, Sound.MUSIC_DISC_BLOCKS),
            new DiscButton(Material.MUSIC_DISC_CHIRP, Sound.MUSIC_DISC_CHIRP),
            new DiscButton(Material.MUSIC_DISC_FAR, Sound.MUSIC_DISC_FAR),
            new DiscButton(Material.MUSIC_DISC_MALL, Sound.MUSIC_DISC_MALL),
            new DiscButton(Material.MUSIC_DISC_MELLOHI, Sound.MUSIC_DISC_MELLOHI),
            new DiscButton(Material.MUSIC_DISC_STAL, Sound.MUSIC_DISC_STAL),
            new DiscButton(Material.MUSIC_DISC_STRAD, Sound.MUSIC_DISC_STRAD),
            new DiscButton(Material.MUSIC_DISC_WARD, Sound.MUSIC_DISC_WARD),
            new DiscButton(Material.MUSIC_DISC_11, Sound.MUSIC_DISC_11),
            new DiscButton(Material.MUSIC_DISC_WAIT, Sound.MUSIC_DISC_WAIT),
            new DiscButton(Material.MUSIC_DISC_OTHERSIDE, Sound.MUSIC_DISC_OTHERSIDE),
            new DiscButton(Material.MUSIC_DISC_5, Sound.MUSIC_DISC_5),
            new DiscButton(Material.MUSIC_DISC_PIGSTEP, Sound.MUSIC_DISC_PIGSTEP),
            new DiscButton(Material.MUSIC_DISC_RELIC, Sound.MUSIC_DISC_RELIC),
            new DiscButton(Material.MUSIC_DISC_CREATOR, Sound.MUSIC_DISC_CREATOR),
            new DiscButton(Material.MUSIC_DISC_CREATOR_MUSIC_BOX, Sound.MUSIC_DISC_CREATOR_MUSIC_BOX),
            new DiscButton(Material.MUSIC_DISC_PRECIPICE, Sound.MUSIC_DISC_PRECIPICE)
    );

    private final NovaBlock plugin;

    public CompanionMusicGui(NovaBlock plugin) {
        super("<gradient:#4FC3F7:#7B61FF><bold>Companion Music", 4);
        this.plugin = plugin;
    }

    @Override
    protected void build(Player p) {
        int[] slots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32};
        Sound current = plugin.companions().music(p);
        for (int i = 0; i < DISCS.size() && i < slots.length; i++) {
            DiscButton disc = DISCS.get(i);
            boolean selected = disc.sound == current;
            ItemBuilder item = ItemBuilder.of(disc.material)
                    .name((selected ? "<green>" : "<light_purple>") + label(disc.sound))
                    .lore(selected ? "<green>Currently looping." : "<gray>Click to loop this disc.");
            if (selected) item.glow();
            set(slots[i], item.build(), e -> {
                plugin.companions().setMusic(p, disc.sound);
                new CompanionGui(plugin).open(p);
            });
        }

        set(34, ItemBuilder.of(Material.BARRIER)
                        .name("<red>Stop Music")
                        .lore("<gray>Keep companion active but stop disc loop.")
                        .build(),
                e -> {
                    plugin.companions().setMusic(p, null);
                    new CompanionGui(plugin).open(p);
                });
        set(35, ItemBuilder.of(Material.ARROW).name("<gray>Back").build(),
                e -> new CompanionGui(plugin).open(p));

        fill(Material.GRAY_STAINED_GLASS_PANE, " ");
    }

    private String label(Sound sound) {
        return sound.name().replace("MUSIC_DISC_", "").toLowerCase(Locale.ROOT);
    }

    private record DiscButton(Material material, Sound sound) {}
}
