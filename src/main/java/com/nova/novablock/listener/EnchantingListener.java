package com.nova.novablock.listener;

import com.nova.novablock.NovaBlock;
import com.nova.novablock.progression.Passives;
import com.nova.novablock.progression.Perk;
import com.nova.novablock.progression.PlayerProgression;
import com.nova.novablock.progression.SkillEffects;
import com.nova.novablock.progression.SkillType;
import com.nova.novablock.util.Msg;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Makes the enchanting table the Magic skill's training ground: every enchant
 * grants Magic XP and can trigger the Magic passive and its perks.
 *
 * <p>Magic was previously fed only by loot rooms and prophecies — three sparse,
 * mostly passive sources — so the skill had no activity a player could choose to
 * do. This listener adds one: XP scales with the LEVEL COST of the enchant
 * ({@code xp-per-action * expLevelCost}), so a table-30 enchant is worth far more
 * than a cheap level-1 roll and the grind stays tied to the real lapis + XP cost
 * rather than to click count.
 *
 * <p>Creative enchanting is free, so it's skipped — otherwise creative players
 * could farm the skill infinitely.
 */
public class EnchantingListener implements Listener {

    private final NovaBlock plugin;

    /**
     * Curses CURSEBREAKER strips from the enchant result. {@link Enchantment} is a
     * registry interface on modern Paper (not an enum), so this is a plain Set of the
     * singleton registry instances rather than an EnumSet.
     */
    private static final Set<Enchantment> CURSES = Set.of(
            Enchantment.BINDING_CURSE, Enchantment.VANISHING_CURSE);

    public EnchantingListener(NovaBlock plugin) { this.plugin = plugin; }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEnchant(EnchantItemEvent event) {
        Player p = event.getEnchanter();
        if (p.getGameMode() == GameMode.CREATIVE) return;
        if (!p.hasPermission("novablock.skills")) return;

        PlayerProgression prog = plugin.progression().get(p);
        Map<Enchantment, Integer> toAdd = event.getEnchantsToAdd();

        // XP scales with what the enchant actually cost in levels.
        long cost = Math.max(1, event.getExpLevelCost());
        plugin.progression().addXp(p, SkillType.MAGIC,
                SkillEffects.xpPerAction(SkillType.MAGIC) * cost);

        // CURSEBREAKER (Lv50): drop any curse the table rolled. Collect first —
        // removing while iterating the live map would blow up.
        if (Perk.hasPerk(prog, Perk.CURSEBREAKER)) {
            List<Enchantment> curses = new ArrayList<>();
            for (Enchantment e : toAdd.keySet()) {
                if (CURSES.contains(e)) curses.add(e);
            }
            if (!curses.isEmpty()) {
                curses.forEach(toAdd::remove);
                Msg.actionBar(p, "<#9C66FF>Cursebreaker! <gray>The curse burned away.");
            }
        }

        // Magic passive: chance to push one rolled enchantment up a level. Normally
        // capped at the vanilla maximum; RUNESMITH (Lv25) lets the proc go one past it.
        if (!toAdd.isEmpty() && Passives.roll(prog, SkillType.MAGIC)) {
            boolean overcap = Perk.hasPerk(prog, Perk.RUNESMITH);
            if (upgradeRandomEnchant(toAdd, overcap)) {
                Msg.actionBar(p, overcap
                        ? "<#9C66FF>Runesmith! <gray>The rune bit deeper than it should."
                        : "<#9C66FF>Arcane surge! <gray>+1 enchantment level.");
            }
        }

        // LAPIS_AFFINITY (Lv15): chance to hand back the lapis the table consumed.
        // Vanilla eats (button index + 1) lapis, i.e. 1-3.
        if (Perk.hasPerk(prog, Perk.LAPIS_AFFINITY)
                && ThreadLocalRandom.current().nextDouble()
                   < SkillEffects.enchanting().lapisRefundChance()) {
            int lapis = event.whichButton() + 1;
            giveOrDrop(p, new ItemStack(Material.LAPIS_LAZULI, lapis));
            Msg.actionBar(p, "<#38BDF8>Lapis Affinity! <gray>Refunded " + lapis + " lapis.");
        }

        // SOUL_SIPHON (Lv40): refund a slice of the levels spent.
        if (Perk.hasPerk(prog, Perk.SOUL_SIPHON)) {
            int refund = (int) Math.floor(cost * SkillEffects.enchanting().xpRefundFraction());
            if (refund > 0) {
                p.giveExpLevels(refund);
                Msg.actionBar(p, "<#9C66FF>Soul Siphon! <gray>Reclaimed " + refund + " levels.");
            }
        }
    }

    /**
     * Bumps one randomly chosen rolled enchantment by a level, skipping any that
     * are already at their ceiling. Returns true if something was actually raised.
     */
    private boolean upgradeRandomEnchant(Map<Enchantment, Integer> toAdd, boolean overcap) {
        List<Enchantment> candidates = new ArrayList<>();
        for (Map.Entry<Enchantment, Integer> e : toAdd.entrySet()) {
            if (CURSES.contains(e.getKey())) continue;   // never "upgrade" a curse
            int ceiling = e.getKey().getMaxLevel() + (overcap ? 1 : 0);
            if (e.getValue() < ceiling) candidates.add(e.getKey());
        }
        if (candidates.isEmpty()) return false;
        Enchantment pick = candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
        toAdd.put(pick, toAdd.get(pick) + 1);
        return true;
    }

    /** Adds an item to the player's inventory, dropping any overflow at their feet. */
    private void giveOrDrop(Player p, ItemStack stack) {
        for (ItemStack left : p.getInventory().addItem(stack).values()) {
            p.getWorld().dropItemNaturally(p.getLocation(), left);
        }
    }
}
