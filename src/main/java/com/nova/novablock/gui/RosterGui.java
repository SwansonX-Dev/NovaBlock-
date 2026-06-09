package com.nova.novablock.gui;

import com.nova.novablock.NovaBlock;
import com.nova.novablock.island.Island;
import com.nova.novablock.island.IslandRole;
import com.nova.novablock.util.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Island roster GUI: every team member shown as a player head, ordered
 * owner → co-owners → members.
 *
 * <p>The owner left-clicks to promote/demote and right-clicks to kick;
 * co-owners right-click to kick plain members. Every mutation is delegated to
 * the matching {@code /ob} subcommand so the permission checks, target/team
 * notifications, teleport-on-kick, and persistence all live in exactly one
 * place — this class only renders the roster and routes clicks.
 */
public class RosterGui extends ChestGui {

    private final NovaBlock plugin;

    /** Content slots: rows 1-4, columns 1-7 — keeps the heads off the border for a tidy grid. */
    private static final int[] CONTENT_SLOTS;
    static {
        List<Integer> slots = new ArrayList<>();
        for (int row = 1; row <= 4; row++) {
            for (int col = 1; col <= 7; col++) slots.add(row * 9 + col);
        }
        CONTENT_SLOTS = slots.stream().mapToInt(Integer::intValue).toArray();
    }

    public RosterGui(NovaBlock plugin) {
        super("<#7FFFE0><bold>Island Roster", 6);
        this.plugin = plugin;
    }

    @Override
    protected void build(Player p) {
        Island island = plugin.islands().ofPlayer(p);
        if (island == null) {
            set(22, ItemBuilder.of(Material.BARRIER).name("<red>You aren't on an island.").build(), null);
            backButton(p);
            fill(Material.BLACK_STAINED_GLASS_PANE, " ");
            return;
        }

        IslandRole viewerRole = island.roleOf(p);

        String hint = viewerRole.canManageRoles()
                ? "<dark_gray>Left-click a head to promote/demote · right-click to kick"
                : viewerRole.canManageRoster()
                    ? "<dark_gray>Right-click a member to kick"
                    : "<dark_gray>Only the owner and co-owners can manage the roster";
        set(4, ItemBuilder.of(Material.GOLD_INGOT)
                .name("<gold>Island Bank: <yellow>" + plugin.economy().format(island.data().getBankBalance()) + " coins")
                .lore("<gray>You are <" + viewerRole.color + ">" + viewerRole.displayName + "<gray>.", hint)
                .build(), null);

        // Owner first, then co-owners, then members.
        List<UUID> ordered = new ArrayList<>(island.data().getMembers());
        ordered.sort(Comparator.comparingInt(u -> switch (island.data().getRole(u)) {
            case OWNER -> 0;
            case CO_OWNER -> 1;
            default -> 2;
        }));

        for (int i = 0; i < ordered.size() && i < CONTENT_SLOTS.length; i++) {
            UUID id = ordered.get(i);
            IslandRole role = island.data().getRole(id);
            OfflinePlayer op = Bukkit.getOfflinePlayer(id);
            String name = op.getName() != null ? op.getName() : id.toString().substring(0, 8);

            ItemBuilder ib = ItemBuilder.of(Material.PLAYER_HEAD)
                    .skull(op)
                    .name("<" + role.color + ">" + name)
                    .lore("<gray>Role: <" + role.color + ">" + role.displayName,
                            op.isOnline() ? "<green>● Online" : "<dark_gray>● Offline");

            List<String> actions = actionHints(viewerRole, role, id, p);
            if (!actions.isEmpty()) {
                ib.lore(" ");
                ib.lore(actions);
            }
            set(CONTENT_SLOTS[i], ib.build(), e -> onMemberClick(p, id, e.isRightClick()));
        }

        backButton(p);
        fill(Material.BLACK_STAINED_GLASS_PANE, " ");
    }

    private void backButton(Player p) {
        set(49, ItemBuilder.of(Material.ARROW).name("<gray>← Back to menu").build(),
                e -> new MainMenuGui(plugin).open(p));
    }

    /** Lines describing what {@code viewerRole} can do to this member; empty when nothing. */
    private List<String> actionHints(IslandRole viewerRole, IslandRole targetRole, UUID targetId, Player viewer) {
        List<String> out = new ArrayList<>();
        if (targetId.equals(viewer.getUniqueId())) return out;   // no actions on yourself
        if (targetRole == IslandRole.OWNER) return out;          // owner is untouchable
        if (viewerRole.canManageRoles()) {                       // owner
            if (targetRole == IslandRole.MEMBER) out.add("<green>Left-click: Promote to Co-Owner");
            else if (targetRole == IslandRole.CO_OWNER) out.add("<yellow>Left-click: Demote to Member");
            out.add("<red>Right-click: Kick");
        } else if (viewerRole.canManageRoster() && targetRole == IslandRole.MEMBER) {  // co-owner
            out.add("<red>Right-click: Kick");                    // co-owners can't kick other co-owners
        }
        return out;
    }

    private void onMemberClick(Player p, UUID targetId, boolean rightClick) {
        Island island = plugin.islands().ofPlayer(p);
        if (island == null) return;
        IslandRole viewerRole = island.roleOf(p);
        IslandRole targetRole = island.data().getRole(targetId);

        // Re-validate against live state; the underlying /ob command enforces this too.
        if (targetId.equals(p.getUniqueId()) || targetRole == IslandRole.OWNER) { deny(p); return; }
        OfflinePlayer op = Bukkit.getOfflinePlayer(targetId);
        String name = op.getName();
        if (name == null) { deny(p); return; }

        if (rightClick) {
            if (!viewerRole.canManageRoster()) { deny(p); return; }
            p.performCommand("ob kick " + name);
        } else {
            if (!viewerRole.canManageRoles()) { deny(p); return; }
            if (targetRole == IslandRole.MEMBER) p.performCommand("ob promote " + name);
            else if (targetRole == IslandRole.CO_OWNER) p.performCommand("ob demote " + name);
            else { deny(p); return; }
        }
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.2f);
        open(p); // rebuild from fresh roster state
    }

    private void deny(Player p) {
        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.8f);
    }
}
