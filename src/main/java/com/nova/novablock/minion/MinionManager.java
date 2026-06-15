package com.nova.novablock.minion;

import com.nova.novablock.NovaBlock;
import com.nova.novablock.gui.minion.MinionControlGui;
import com.nova.novablock.island.Island;
import com.nova.novablock.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public class MinionManager implements Listener {
    private final NovaBlock plugin;
    private final NamespacedKey itemTypeKey;
    private final NamespacedKey entityIdKey;
    private final Map<UUID, MinionData> minions = new HashMap<>();
    private final Map<UUID, List<Entity>> displays = new HashMap<>();
    private final Map<UUID, UUID> pendingLinks = new HashMap<>();
    private final Map<UUID, UUID> pendingMoves = new HashMap<>();
    private final EnumMap<MinionType, List<MinionDrop>> outputTables = new EnumMap<>(MinionType.class);
    private final Random random = new Random();
    private File file;
    private BukkitTask task;
    private long tickRate;
    private boolean dirty;

    public MinionManager(NovaBlock plugin) {
        this.plugin = plugin;
        this.itemTypeKey = new NamespacedKey(plugin, "minion_type");
        this.entityIdKey = new NamespacedKey(plugin, "minion_id");
    }

    public void start() {
        file = new File(plugin.getDataFolder(), "minions.yml");
        tickRate = Math.max(20L, plugin.getConfig().getLong("minions.tick-rate-ticks", 20L));
        load();
        loadOutputTables();
        validateConfig();
        Bukkit.getPluginManager().registerEvents(this, plugin);
        removeTaggedEntities();
        for (MinionData data : minions.values()) spawnDisplay(data);
        task = Bukkit.getScheduler().runTaskTimer(plugin, (Runnable) this::tick, tickRate, tickRate);
    }

    public void shutdown() {
        if (task != null) task.cancel();
        save();
        cleanupDisplays();
    }

    public Collection<MinionData> all() { return minions.values(); }
    public MinionData get(UUID id) { return minions.get(id); }
    public List<MinionDrop> drops(MinionType type) { return Collections.unmodifiableList(outputTables.getOrDefault(type, type.defaultDrops())); }

    public List<MinionData> ofIsland(UUID islandId) {
        List<MinionData> out = new ArrayList<>();
        for (MinionData data : minions.values()) if (islandId.equals(data.islandId())) out.add(data);
        return out;
    }

    public int count(UUID islandId) { return ofIsland(islandId).size(); }

    /** Community (owner-based) minions belonging to a player. */
    public List<MinionData> ofOwner(UUID ownerId) {
        List<MinionData> out = new ArrayList<>();
        for (MinionData data : minions.values()) {
            if (data.isCommunity() && ownerId.equals(data.ownerId())) out.add(data);
        }
        return out;
    }

    public int communityCount(UUID ownerId) { return ofOwner(ownerId).size(); }

    /** Per-player community minion cap (config; admins unlimited; limit perms still apply). */
    public int communityLimit(Player player) {
        if (player.hasPermission("novablock.minions.admin") || player.hasPermission("novablock.admin")) return Integer.MAX_VALUE;
        int limit = Math.max(1, plugin.getConfig().getInt("minions.community-limit", 5));
        for (var info : player.getEffectivePermissions()) {
            if (!info.getValue()) continue;
            String perm = info.getPermission().toLowerCase(Locale.ROOT);
            if (!perm.startsWith("novablock.minions.community-limit.")) continue;
            try { limit = Math.max(limit, Integer.parseInt(perm.substring("novablock.minions.community-limit.".length()))); }
            catch (NumberFormatException ignored) {}
        }
        return limit;
    }

    /** True if {@code world} is the Community OneBlock world. */
    private boolean isCommunityWorld(org.bukkit.World world) {
        if (world == null || plugin.community() == null) return false;
        return world.getName().equals(plugin.community().communityWorldName());
    }

    public int limit(Player player) { return limit(player, plugin.islands().ofPlayer(player)); }

    public int limit(Player player, Island island) {
        if (player.hasPermission("novablock.minions.admin") || player.hasPermission("novablock.admin")) return Integer.MAX_VALUE;
        int limit = Math.max(1, plugin.getConfig().getInt("minions.default-island-limit", 6));
        if (island != null) {
            int perPrestige = Math.max(0, plugin.getConfig().getInt("minions.limit-per-prestige", 1));
            int maxBonus = Math.max(0, plugin.getConfig().getInt("minions.max-prestige-limit-bonus", 6));
            limit += Math.min(maxBonus, island.data().getPrestigeLevel() * perPrestige);
        }
        for (var info : player.getEffectivePermissions()) {
            if (!info.getValue()) continue;
            String perm = info.getPermission().toLowerCase(Locale.ROOT);
            if (!perm.startsWith("novablock.minions.limit.")) continue;
            try { limit = Math.max(limit, Integer.parseInt(perm.substring("novablock.minions.limit.".length()))); }
            catch (NumberFormatException ignored) {}
        }
        return limit;
    }

    public void beginLink(Player player, UUID minionId) {
        MinionData data = minions.get(minionId);
        if (data == null) { Msg.send(player, "<red>That minion no longer exists."); return; }
        if (!canManage(player, data)) return;
        pendingLinks.put(player.getUniqueId(), minionId);
        player.closeInventory();
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.4f);
        Msg.send(player, "<yellow>Punch a chest or barrel on this island to link output.");
    }

    public void beginMove(Player player, UUID minionId) {
        MinionData data = minions.get(minionId);
        if (data == null || !canManage(player, data)) return;
        pendingMoves.put(player.getUniqueId(), minionId);
        player.closeInventory();
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.4f);
        Msg.send(player, "<yellow>Right-click the new block position for this minion.");
    }

    public boolean addFuel(Player player, MinionData data, ItemStack stack) {
        if (stack == null || stack.getType().isAir()) return false;
        MinionFuel fuel = MinionFuel.of(stack.getType());
        if (fuel == null) { Msg.send(player, "<red>Hold coal, blaze fuel, lava, or dried kelp blocks."); return false; }
        long ticks = Math.round(fuel.durationTicks() * (1.0 + data.upgrade(MinionUpgrade.FUEL_EFFICIENCY) * 0.20));
        data.addFuelTicks(ticks);
        stack.setAmount(stack.getAmount() - 1);
        dirty = true;
        player.playSound(player.getLocation(), Sound.BLOCK_BLASTFURNACE_FIRE_CRACKLE, 0.6f, 1.2f);
        Msg.send(player, "<green>Added fuel.");
        return true;
    }

    public boolean upgrade(Player player, MinionData data, MinionUpgrade upgrade) {
        int current = data.upgrade(upgrade);
        if (current >= upgrade.maxLevel()) { Msg.send(player, "<gray>That upgrade is already maxed."); return false; }
        long cost = upgradeCost(upgrade, current + 1);
        boolean paid = data.isCommunity()
                ? plugin.economy().spend(player, cost)
                : plugin.economy().spend(plugin.islands().get(data.islandId()), cost);
        if (!paid) {
            Msg.send(player, "<red>Not enough coins. Need <yellow>" + cost + "<red>.");
            return false;
        }
        data.setUpgrade(upgrade, current + 1);
        dirty = true;
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 0.55f, 1.35f);
        return true;
    }

    public long upgradeCost(MinionUpgrade upgrade, int level) {
        String path = "minions.upgrade-costs." + upgrade.name().toLowerCase(Locale.ROOT).replace('_', '-') + "." + level;
        long fallback = switch (upgrade) {
            case SPEED -> 1000L * level * level;
            case YIELD -> 1250L * level * level;
            case FUEL_EFFICIENCY -> 750L * level * level;
            case COMPACTOR -> 2500L * level;
        };
        return plugin.getConfig().getLong(path, fallback);
    }

    public boolean canUseSkin(Player player, MinionSkin skin) {
        return skin == MinionSkin.DEFAULT
                || player.hasPermission("novablock.minions.admin")
                || player.hasPermission("novablock.admin")
                || player.hasPermission("novablock.minions.skin." + skin.id())
                || plugin.getConfig().getBoolean("minions.skins." + skin.id() + ".default-unlocked", false);
    }

    public void setSkin(Player player, MinionData data, MinionSkin skin) {
        if (!canManage(player, data)) return;
        if (!canUseSkin(player, skin)) { Msg.send(player, "<red>That skin is locked."); return; }
        data.setSkin(skin.id());
        dirty = true;
        save();
        refreshDisplay(data);
        player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_LEATHER, 0.7f, 1.2f);
    }

    public ItemStack testRoll(MinionData data) {
        return data.type().rollOutput(random, data.upgrade(MinionUpgrade.YIELD), data.upgrade(MinionUpgrade.COMPACTOR), drops(data.type()));
    }

    public boolean hasInventoryRoom(Inventory inventory, ItemStack stack) { return canFit(inventory, stack); }

    public boolean isShopEnabled(MinionType type) { return type.shopEnabled(plugin); }
    public void setShopEnabled(MinionType type, boolean enabled) {
        plugin.getConfig().set("minions.shop-enabled." + type.id(), enabled);
        plugin.saveConfig();
    }

    public void reloadSettings() {
        plugin.reloadConfig();
        long newTickRate = Math.max(20L, plugin.getConfig().getLong("minions.tick-rate-ticks", 20L));
        if (newTickRate != tickRate) {
            tickRate = newTickRate;
            if (task != null) task.cancel();
            task = Bukkit.getScheduler().runTaskTimer(plugin, (Runnable) this::tick, tickRate, tickRate);
        }
        loadOutputTables();
        validateConfig();
    }

    public void validateConfig() {
        for (MinionType type : MinionType.values()) {
            if (type.shopPrice(plugin) < 0) plugin.getLogger().warning("Minion " + type.id() + " has a negative shop price.");
            List<?> rows = plugin.getConfig().getList("minions.outputs." + type.id());
            if (rows == null) continue;
            for (Object raw : rows) {
                if (!(raw instanceof Map<?, ?> map)) {
                    plugin.getLogger().warning("Minion " + type.id() + " output row is not a map: " + raw);
                    continue;
                }
                Material material = Material.matchMaterial(String.valueOf(map.get("material")));
                if (material == null || material.isAir()) plugin.getLogger().warning("Minion " + type.id() + " has invalid output material: " + map.get("material"));
                int weight = intVal(map.get("weight"), 1);
                if (weight <= 0) plugin.getLogger().warning("Minion " + type.id() + " output " + map.get("material") + " has non-positive weight.");
                double rare = doubleVal(map.get("rareChance"), 0.0);
                if (rare < 0.0 || rare > 1.0) plugin.getLogger().warning("Minion " + type.id() + " output " + map.get("material") + " rareChance must be 0.0-1.0.");
            }
        }
    }

    public void addDrop(MinionType type, Material material, int weight) {
        if (material == null || material.isAir()) return;
        List<MinionDrop> drops = new ArrayList<>(outputTables.getOrDefault(type, type.defaultDrops()));
        for (int i = 0; i < drops.size(); i++) {
            MinionDrop drop = drops.get(i);
            if (drop.material() == material) {
                drops.set(i, new MinionDrop(material, drop.weight() + Math.max(1, weight), drop.minAmount(), drop.maxAmount(), drop.rareChance()));
                outputTables.put(type, drops);
                saveOutputTables();
                return;
            }
        }
        drops.add(new MinionDrop(material, weight));
        outputTables.put(type, drops);
        saveOutputTables();
    }

    public void adjustDropWeight(MinionType type, Material material, int delta) { mutateDrop(type, material, delta, 0, 0.0); }
    public void adjustDropAmount(MinionType type, Material material, int minDelta, int maxDelta) {
        List<MinionDrop> drops = new ArrayList<>(outputTables.getOrDefault(type, type.defaultDrops()));
        for (int i = 0; i < drops.size(); i++) {
            MinionDrop d = drops.get(i);
            if (d.material() == material) drops.set(i, new MinionDrop(material, d.weight(), d.minAmount() + minDelta, d.maxAmount() + maxDelta, d.rareChance()));
        }
        outputTables.put(type, drops); saveOutputTables();
    }
    public void adjustDropRareChance(MinionType type, Material material, double delta) { mutateDrop(type, material, 0, 0, delta); }
    private void mutateDrop(MinionType type, Material material, int weightDelta, int amountDelta, double rareDelta) {
        List<MinionDrop> drops = new ArrayList<>(outputTables.getOrDefault(type, type.defaultDrops()));
        for (int i = 0; i < drops.size(); i++) {
            MinionDrop d = drops.get(i);
            if (d.material() == material) drops.set(i, new MinionDrop(material, d.weight() + weightDelta, d.minAmount(), d.maxAmount() + amountDelta, d.rareChance() + rareDelta));
        }
        outputTables.put(type, drops); saveOutputTables();
    }
    public void removeDrop(MinionType type, Material material) {
        List<MinionDrop> drops = new ArrayList<>(outputTables.getOrDefault(type, type.defaultDrops()));
        drops.removeIf(drop -> drop.material() == material);
        if (drops.isEmpty()) drops.addAll(type.defaultDrops());
        outputTables.put(type, drops); saveOutputTables();
    }
    public void resetOutputTable(MinionType type) { outputTables.put(type, new ArrayList<>(type.defaultDrops())); saveOutputTables(); }
    public void reloadOutputTables() { plugin.reloadConfig(); loadOutputTables(); }

    public void pickup(Player player, MinionData data) {
        if (!canManage(player, data)) return;
        ItemStack item = data.type().createItem(plugin, 1);
        if (!hasInventoryRoom(player.getInventory(), item)) {
            Msg.send(player, "<red>Make room in your inventory first.");
            return;
        }
        minions.remove(data.id());
        removeDisplay(data.id());
        player.getInventory().addItem(item);
        dirty = true; save(); player.closeInventory();
        player.playSound(player.getLocation(), Sound.ITEM_BUNDLE_INSERT, 0.7f, 1.1f);
    }

    public void delete(Player player, MinionData data) {
        if (!canManage(player, data)) return;
        minions.remove(data.id());
        removeDisplay(data.id());
        dirty = true; save(); player.closeInventory();
        player.playSound(player.getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, 0.6f, 1.0f);
    }

    public void removeIsland(UUID islandId) {
        for (MinionData data : new ArrayList<>(minions.values())) {
            if (islandId.equals(data.islandId())) { minions.remove(data.id()); removeDisplay(data.id()); dirty = true; }
        }
        if (dirty) save();
    }

    public int removeForPlayerOrIsland(String target) {
        UUID islandId = null;
        try { islandId = UUID.fromString(target); }
        catch (IllegalArgumentException ignored) {
            Island island = plugin.islands().ofPlayer(Bukkit.getOfflinePlayer(target).getUniqueId());
            if (island != null) islandId = island.data().getId();
        }
        if (islandId == null) return 0;
        int before = minions.size();
        removeIsland(islandId);
        return before - minions.size();
    }

    public boolean canManage(Player player, MinionData data) {
        if (player.hasPermission("novablock.minions.admin") || player.hasPermission("novablock.admin")) return true;
        if (data.isCommunity()) {
            if (player.getUniqueId().equals(data.ownerId())) return true;
            Msg.send(player, "<red>This minion belongs to another player.");
            return false;
        }
        Island island = plugin.islands().get(data.islandId());
        if (island != null && island.isMember(player)) return true;
        Msg.send(player, "<red>This minion belongs to another island.");
        return false;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onMove(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) return;
        UUID minionId = pendingMoves.get(event.getPlayer().getUniqueId());
        if (minionId == null) return;
        event.setCancelled(true);
        MinionData data = minions.get(minionId);
        if (data == null || !canManage(event.getPlayer(), data)) { pendingMoves.remove(event.getPlayer().getUniqueId()); return; }
        Block target = event.getClickedBlock().getRelative(event.getBlockFace());
        if (data.isCommunity()) {
            if (!isCommunityWorld(target.getWorld())
                    || !com.nova.novablock.compat.ClaimBridge.ownsClaimAt(event.getPlayer(), target.getLocation())) {
                Msg.send(event.getPlayer(), "<red>Move the minion inside your own community claim.");
                return;
            }
        } else {
            Island island = plugin.islands().atLocation(target.getLocation());
            if (island == null || !island.data().getId().equals(data.islandId()) || !island.isMember(event.getPlayer())) {
                Msg.send(event.getPlayer(), "<red>Move the minion inside its own island.");
                return;
            }
        }
        data.setLocation(target.getLocation().add(0.5, 0.05, 0.5));
        pendingMoves.remove(event.getPlayer().getUniqueId());
        dirty = true; save(); spawnDisplay(data);
        event.getPlayer().playSound(event.getPlayer().getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.6f, 1.2f);
        new MinionControlGui(plugin, data.id()).open(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) return;
        ItemStack hand = event.getItem();
        MinionType type = typeFromItem(hand);
        if (type == null) return;
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (!player.hasPermission("novablock.minions.use")) { Msg.send(player, "<red>You don't have permission to use minions."); return; }
        Location place = event.getClickedBlock().getRelative(event.getBlockFace()).getLocation();
        Island island = plugin.islands().atLocation(place);

        MinionData data;
        if (island != null) {
            if (!island.isMember(player)) { Msg.send(player, "<red>Place minions on your own island."); return; }
            if (!type.unlocked(island.data().getPhaseIndex()) && !player.hasPermission("novablock.minions.admin")) { Msg.send(player, "<red>Your island has not unlocked this minion."); return; }
            if (count(island.data().getId()) >= limit(player, island)) { Msg.send(player, "<red>Your island minion limit is reached."); return; }
            data = new MinionData(UUID.randomUUID(), island.data().getId(), type, place.clone().add(0.5, 0.05, 0.5));
        } else if (isCommunityWorld(place.getWorld())) {
            if (!com.nova.novablock.compat.ClaimBridge.ownsClaimAt(player, place)) {
                Msg.send(player, "<red>Place community minions on your own claim. <gray>Claim the land here first.");
                return;
            }
            if (communityCount(player.getUniqueId()) >= communityLimit(player)) {
                Msg.send(player, "<red>You've reached your community minion limit (" + communityLimit(player) + ").");
                return;
            }
            data = new MinionData(UUID.randomUUID(), null, type, place.clone().add(0.5, 0.05, 0.5));
            data.setOwnerId(player.getUniqueId());
        } else {
            Msg.send(player, "<red>Place minions on your island or your Community OneBlock claim.");
            return;
        }
        minions.put(data.id(), data);
        spawnDisplay(data);
        player.playSound(player.getLocation(), Sound.ENTITY_ARMOR_STAND_PLACE, 0.8f, 1.1f);
        if (hand != null) hand.setAmount(hand.getAmount() - 1);
        dirty = true; save();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onLink(PlayerInteractEvent event) {
        if (event.getAction() != Action.LEFT_CLICK_BLOCK && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        UUID minionId = pendingLinks.get(event.getPlayer().getUniqueId());
        if (minionId == null) return;
        event.setCancelled(true);
        Block block = event.getClickedBlock();
        if (block == null || !isOutputContainer(block.getType())) { Msg.send(event.getPlayer(), "<red>That is not a chest or barrel."); return; }
        MinionData data = minions.get(minionId);
        if (data == null) { pendingLinks.remove(event.getPlayer().getUniqueId()); return; }
        if (data.isCommunity()) {
            if (!isCommunityWorld(block.getWorld())
                    || !com.nova.novablock.compat.ClaimBridge.ownsClaimAt(event.getPlayer(), block.getLocation())) {
                Msg.send(event.getPlayer(), "<red>Link a chest on your own community claim."); return;
            }
        } else {
            Island own = plugin.islands().get(data.islandId());
            Island linked = plugin.islands().atLocation(block.getLocation());
            if (own == null || linked == null || !own.data().getId().equals(linked.data().getId())) { Msg.send(event.getPlayer(), "<red>Link a chest on the same island."); return; }
        }
        data.setLinked(block.getLocation());
        data.setStatus(MinionStatus.READY);
        pendingLinks.remove(event.getPlayer().getUniqueId());
        dirty = true; save(); refreshDisplay(data);
        event.getPlayer().playSound(event.getPlayer().getLocation(), Sound.BLOCK_CHEST_LOCKED, 0.7f, 1.6f);
        new MinionControlGui(plugin, data.id()).open(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onEntityClick(PlayerInteractEntityEvent event) {
        UUID id = entityMinionId(event.getRightClicked());
        if (id == null) return;
        event.setCancelled(true);
        MinionData data = minions.get(id);
        if (data != null && canManage(event.getPlayer(), data)) new MinionControlGui(plugin, id).open(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onContainerBreak(BlockBreakEvent event) {
        if (!isOutputContainer(event.getBlock().getType())) return;
        for (MinionData data : minions.values()) {
            Location linked = data.linkedLocation();
            if (linked != null && sameBlock(linked, event.getBlock().getLocation())) { data.setStatus(MinionStatus.MISSING_CHEST); dirty = true; refreshDisplay(data); }
        }
    }

    private void tick() {
        for (MinionData data : minions.values()) tick(data);
        if (dirty) save();
    }

    private void tick(MinionData data) {
        if (data.isCommunity()) {
            if (data.ownerId() == null || Bukkit.getPlayer(data.ownerId()) == null) { setStatus(data, MinionStatus.NO_ONLINE_MEMBER); return; }
        } else {
            Island island = plugin.islands().get(data.islandId());
            if (island == null) return;
            if (!hasOnlineMember(island)) { setStatus(data, MinionStatus.NO_ONLINE_MEMBER); return; }
        }
        if (!data.hasLinkedChest()) { setStatus(data, MinionStatus.UNLINKED); return; }
        Inventory inventory = linkedInventory(data);
        if (inventory == null) { refreshDisplay(data); return; }
        long elapsed = tickRate;
        if (data.fuelTicksRemaining() > 0) data.consumeFuelTicks(elapsed);
        data.addAccumulatedTicks(elapsed);
        long interval = effectiveIntervalTicks(data);
        if (data.accumulatedTicks() < interval) { setStatus(data, MinionStatus.READY); dirty = true; return; }
        ItemStack output = testRoll(data);
        if (!plugin.getConfig().getBoolean("minions.discard-leftovers", false) && !canFit(inventory, output)) { setStatus(data, MinionStatus.CHEST_FULL); dirty = true; return; }
        inventory.addItem(output);
        data.consumeAccumulatedTicks(interval);
        data.addProductionLog("+" + output.getAmount() + " " + pretty(output.getType()));
        setStatus(data, MinionStatus.READY);
        dirty = true;
    }

    private void setStatus(MinionData data, MinionStatus status) { data.setStatus(status); refreshDisplay(data); }

    private Inventory linkedInventory(MinionData data) {
        Location loc = data.linkedLocation();
        if (loc == null || loc.getWorld() == null) { data.setStatus(MinionStatus.MISSING_CHEST); return null; }
        World world = loc.getWorld();
        if (!world.isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4)) { data.setStatus(MinionStatus.CHUNK_UNLOADED); return null; }
        Block block = loc.getBlock();
        if (!isOutputContainer(block.getType()) || !(block.getState() instanceof Container container)) { data.setStatus(MinionStatus.MISSING_CHEST); return null; }
        if (!data.isCommunity()) {
            Island own = plugin.islands().get(data.islandId());
            Island linked = plugin.islands().atLocation(loc);
            if (own == null || linked == null || !own.data().getId().equals(linked.data().getId())) { data.setStatus(MinionStatus.CHEST_OUTSIDE_ISLAND); return null; }
        }
        return container.getInventory();
    }

    private long effectiveIntervalTicks(MinionData data) {
        double speed = 1.0 + data.upgrade(MinionUpgrade.SPEED) * 0.12;
        if (data.fuelTicksRemaining() > 0) speed *= plugin.getConfig().getDouble("minions.fuel.default-speed-multiplier", 1.35);
        return Math.max(20L, Math.round((data.type().baseIntervalSeconds() * 20L) / speed));
    }

    private boolean hasOnlineMember(Island island) {
        for (UUID member : island.data().getMembers()) if (Bukkit.getPlayer(member) != null) return true;
        return false;
    }

    private boolean canFit(Inventory inventory, ItemStack stack) {
        int remaining = stack.getAmount();
        for (ItemStack content : inventory.getStorageContents()) {
            if (content == null || content.getType().isAir()) remaining -= stack.getMaxStackSize();
            else if (content.isSimilar(stack)) remaining -= Math.max(0, stack.getMaxStackSize() - content.getAmount());
            if (remaining <= 0) return true;
        }
        return false;
    }

    private MinionType typeFromItem(ItemStack stack) {
        if (stack == null || stack.getType().isAir() || !stack.hasItemMeta()) return null;
        return MinionType.byId(stack.getItemMeta().getPersistentDataContainer().get(itemTypeKey, PersistentDataType.STRING));
    }

    private void spawnDisplay(MinionData data) {
        Location loc = data.location();
        if (loc == null || loc.getWorld() == null) return;
        removeDisplay(data.id());
        MinionSkin skin = MinionSkin.byId(data.skin());
        List<Entity> entities = new ArrayList<>();
        ItemDisplay item = loc.getWorld().spawn(loc.clone().add(0, 0.7, 0), ItemDisplay.class, display -> {
            display.setItemStack(new ItemStack(skin.displayMaterial(data.type())));
            display.setBillboard(Display.Billboard.VERTICAL);
            display.setGlowing(skin.glowColor() != null);
            if (skin.glowColor() != null) display.setGlowColorOverride(skin.glowColor());
            display.setPersistent(false);
            display.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(0.75f, 0.75f, 0.75f), new AxisAngle4f()));
            tagEntity(display, data.id());
        });
        TextDisplay text = loc.getWorld().spawn(loc.clone().add(0, 1.55, 0), TextDisplay.class, display -> {
            display.text(Msg.mm(skin.nameColor() + data.type().displayName() + "\n" + data.status().color() + data.status().displayName()));
            display.setBillboard(Display.Billboard.CENTER);
            display.setDefaultBackground(false);
            display.setPersistent(false);
            tagEntity(display, data.id());
        });
        Interaction hitbox = loc.getWorld().spawn(loc.clone().add(0, 0.8, 0), Interaction.class, interaction -> {
            interaction.setInteractionWidth(1.2f);
            interaction.setInteractionHeight(1.8f);
            interaction.setResponsive(true);
            interaction.setPersistent(false);
            tagEntity(interaction, data.id());
        });
        entities.add(item); entities.add(text); entities.add(hitbox);
        displays.put(data.id(), entities);
    }

    private void refreshDisplay(MinionData data) {
        List<Entity> list = displays.get(data.id());
        if (list == null || list.stream().anyMatch(Entity::isDead)) { spawnDisplay(data); return; }
        MinionSkin skin = MinionSkin.byId(data.skin());
        for (Entity entity : list) {
            if (entity instanceof ItemDisplay item) {
                item.setItemStack(new ItemStack(skin.displayMaterial(data.type())));
                item.setGlowing(skin.glowColor() != null);
                item.setGlowColorOverride(skin.glowColor());
            } else if (entity instanceof TextDisplay text) {
                text.text(Msg.mm(skin.nameColor() + data.type().displayName() + "\n" + data.status().color() + data.status().displayName()));
            }
        }
    }

    private void tagEntity(Entity entity, UUID id) { entity.getPersistentDataContainer().set(entityIdKey, PersistentDataType.STRING, id.toString()); }
    private UUID entityMinionId(Entity entity) {
        String raw = entity.getPersistentDataContainer().get(entityIdKey, PersistentDataType.STRING);
        try { return raw == null ? null : UUID.fromString(raw); } catch (IllegalArgumentException ex) { return null; }
    }
    private void removeDisplay(UUID id) {
        List<Entity> entities = displays.remove(id);
        if (entities != null) for (Entity entity : entities) if (entity != null && !entity.isDead()) entity.remove();
    }
    private void cleanupDisplays() { for (UUID id : new ArrayList<>(displays.keySet())) removeDisplay(id); removeTaggedEntities(); }
    private void removeTaggedEntities() {
        for (World world : Bukkit.getWorlds()) for (Entity entity : world.getEntities()) if (entity.getPersistentDataContainer().has(entityIdKey, PersistentDataType.STRING)) entity.remove();
    }
    private boolean isOutputContainer(Material material) { return material == Material.CHEST || material == Material.TRAPPED_CHEST || material == Material.BARREL; }
    private boolean sameBlock(Location a, Location b) {
        return a.getWorld() != null && a.getWorld().equals(b.getWorld()) && a.getBlockX() == b.getBlockX() && a.getBlockY() == b.getBlockY() && a.getBlockZ() == b.getBlockZ();
    }
    public String pretty(Material material) {
        String[] parts = material.name().toLowerCase(Locale.ROOT).split("_");
        List<String> out = new ArrayList<>();
        for (String part : parts) out.add(Character.toUpperCase(part.charAt(0)) + part.substring(1));
        return String.join(" ", out);
    }

    private void load() {
        minions.clear();
        if (!file.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);

        ConfigurationSection islands = yaml.getConfigurationSection("islands");
        if (islands != null) {
            for (String islandKey : islands.getKeys(false)) {
                try {
                    UUID islandId = UUID.fromString(islandKey);
                    ConfigurationSection islandSec = islands.getConfigurationSection(islandKey);
                    if (islandSec == null) continue;
                    for (String minionKey : islandSec.getKeys(false)) {
                        loadMinion(islandSec.getConfigurationSection(minionKey), minionKey, islandId, null);
                    }
                } catch (RuntimeException ex) {
                    plugin.getLogger().warning("Skipping invalid minion data: " + ex.getMessage());
                }
            }
        }

        ConfigurationSection community = yaml.getConfigurationSection("community");
        if (community != null) {
            for (String ownerKey : community.getKeys(false)) {
                try {
                    UUID ownerId = UUID.fromString(ownerKey);
                    ConfigurationSection ownerSec = community.getConfigurationSection(ownerKey);
                    if (ownerSec == null) continue;
                    for (String minionKey : ownerSec.getKeys(false)) {
                        loadMinion(ownerSec.getConfigurationSection(minionKey), minionKey, null, ownerId);
                    }
                } catch (RuntimeException ex) {
                    plugin.getLogger().warning("Skipping invalid community minion data: " + ex.getMessage());
                }
            }
        }
    }

    private void loadMinion(ConfigurationSection sec, String minionKey, UUID islandId, UUID ownerId) {
        if (sec == null) return;
        MinionType type = MinionType.byId(sec.getString("type"));
        String worldName = sec.getString("world", "");
        World world = Bukkit.getWorld(worldName);
        if (type == null || world == null) {
            // Dropping here also erases the minion on the next save() — warn loudly
            // so a missing/unloaded world is never a silent data loss.
            plugin.getLogger().warning("Skipping minion " + minionKey + ": "
                    + (type == null ? "unknown type '" + sec.getString("type") + "'"
                                     : "world '" + worldName + "' is not loaded")
                    + ". Its data will be lost on next save.");
            return;
        }
        MinionData data = new MinionData(UUID.fromString(minionKey), islandId, type, new Location(world, sec.getDouble("x"), sec.getDouble("y"), sec.getDouble("z")));
        if (ownerId != null) data.setOwnerId(ownerId);
        String linkedWorld = sec.getString("linked.world");
        if (linkedWorld != null && !linkedWorld.isBlank()) data.setLinked(new Location(Bukkit.getWorld(linkedWorld), sec.getInt("linked.x"), sec.getInt("linked.y"), sec.getInt("linked.z")));
        data.setSkin(sec.getString("skin", "default"));
        data.setFuelTicksRemaining(sec.getLong("fuel-ticks"));
        data.setAccumulatedTicks(sec.getLong("accumulated-ticks"));
        data.loadProductionLog(sec.getStringList("production-log"));
        ConfigurationSection upgrades = sec.getConfigurationSection("upgrades");
        if (upgrades != null) for (MinionUpgrade upgrade : MinionUpgrade.values()) data.setUpgrade(upgrade, upgrades.getInt(upgrade.name().toLowerCase(Locale.ROOT).replace('_', '-')));
        minions.put(data.id(), data);
    }

    private void loadOutputTables() {
        outputTables.clear();
        for (MinionType type : MinionType.values()) {
            List<MinionDrop> drops = new ArrayList<>();
            List<?> configured = plugin.getConfig().getList("minions.outputs." + type.id());
            if (configured != null) {
                for (Object raw : configured) {
                    if (!(raw instanceof Map<?, ?> map)) continue;
                    Material material = Material.matchMaterial(String.valueOf(map.get("material")));
                    if (material == null || material.isAir()) continue;
                    drops.add(new MinionDrop(material, intVal(map.get("weight"), 1), intVal(map.get("min"), 1), intVal(map.get("max"), 1), doubleVal(map.get("rareChance"), 0.0)));
                }
            }
            outputTables.put(type, drops.isEmpty() ? new ArrayList<>(type.defaultDrops()) : drops);
        }
        if (!plugin.getConfig().isConfigurationSection("minions.outputs")) saveOutputTables();
    }

    private int intVal(Object raw, int fallback) {
        if (raw instanceof Number n) return n.intValue();
        try { return Integer.parseInt(String.valueOf(raw)); } catch (RuntimeException ex) { return fallback; }
    }
    private double doubleVal(Object raw, double fallback) {
        if (raw instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(String.valueOf(raw)); } catch (RuntimeException ex) { return fallback; }
    }

    public void saveOutputTables() {
        for (MinionType type : MinionType.values()) {
            List<Map<String, Object>> rows = new ArrayList<>();
            for (MinionDrop drop : outputTables.getOrDefault(type, type.defaultDrops())) {
                Map<String, Object> row = new HashMap<>();
                row.put("material", drop.material().name());
                row.put("weight", drop.weight());
                row.put("min", drop.minAmount());
                row.put("max", drop.maxAmount());
                if (drop.rareChance() > 0.0) row.put("rareChance", drop.rareChance());
                rows.add(row);
            }
            plugin.getConfig().set("minions.outputs." + type.id(), rows);
        }
        plugin.saveConfig();
    }

    public void save() {
        if (file == null) return;
        YamlConfiguration yaml = new YamlConfiguration();
        for (MinionData data : minions.values()) {
            String path = data.isCommunity()
                    ? "community." + data.ownerId() + "." + data.id() + "."
                    : "islands." + data.islandId() + "." + data.id() + ".";
            yaml.set(path + "type", data.type().id());
            yaml.set(path + "world", data.worldName());
            yaml.set(path + "x", data.x()); yaml.set(path + "y", data.y()); yaml.set(path + "z", data.z());
            yaml.set(path + "skin", data.skin());
            yaml.set(path + "fuel-ticks", data.fuelTicksRemaining());
            yaml.set(path + "accumulated-ticks", data.accumulatedTicks());
            yaml.set(path + "production-log", data.productionLog());
            if (data.hasLinkedChest()) {
                yaml.set(path + "linked.world", data.linkedWorldName());
                yaml.set(path + "linked.x", data.linkedX()); yaml.set(path + "linked.y", data.linkedY()); yaml.set(path + "linked.z", data.linkedZ());
            }
            for (var entry : data.upgrades().entrySet()) yaml.set(path + "upgrades." + entry.getKey().name().toLowerCase(Locale.ROOT).replace('_', '-'), entry.getValue());
        }
        try { yaml.save(file); dirty = false; } catch (IOException ex) { plugin.getLogger().warning("Failed to save minions.yml: " + ex.getMessage()); }
    }
}
