package com.nova.novablock.pet;

import com.nova.novablock.NovaBlock;
import com.nova.novablock.gui.PetGui;
import com.nova.novablock.island.Island;
import com.nova.novablock.progression.PlayerProgression;
import com.nova.novablock.util.Msg;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class PetManager implements Listener {

    public static final NamespacedKey PET_KEY = new NamespacedKey("novablock", "pet_owner");
    public static final NamespacedKey PET_TYPE_KEY = new NamespacedKey("novablock", "pet_type");

    /** Active ability cooldown per pet, ms. */
    private static final long ACTIVE_COOLDOWN_MS = 20_000L;

    private final NovaBlock plugin;
    /** Active pets indexed by owner UUID. */
    private final Map<UUID, Pet> activePets = new HashMap<>();
    private BukkitTask tickTask;

    public PetManager(NovaBlock plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        startTicker();
    }

    private void startTicker() {
        if (tickTask != null) tickTask.cancel();
        tickTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickAll, 20L, 10L);
    }

    private void tickAll() {
        for (var it = activePets.entrySet().iterator(); it.hasNext(); ) {
            var entry = it.next();
            Pet pet = entry.getValue();
            Player owner = pet.owner();
            LivingEntity entity = pet.entity();
            if (owner == null || !owner.isOnline()) { despawnSilently(pet); it.remove(); continue; }
            if (entity == null || entity.isDead()) { it.remove(); continue; }
            tickPet(pet, owner, entity);
        }
    }

    private void tickPet(Pet pet, Player owner, LivingEntity entity) {
        // Follow / teleport-if-far behavior — works for any mob, regardless of vanilla AI
        double dist = entity.getLocation().distance(owner.getLocation());
        if (entity.getWorld() != owner.getWorld() || dist > 24) {
            entity.teleportAsync(owner.getLocation());
        } else if (dist > 4) {
            Vector toOwner = owner.getLocation().toVector().subtract(entity.getLocation().toVector()).normalize().multiply(0.35);
            // Lift slightly for ground entities so they hop over half-blocks
            if (!entity.isInWater() && entity.isOnGround()) toOwner.setY(Math.max(toOwner.getY(), 0.25));
            entity.setVelocity(toOwner);
        }

        // Per-pet passive that fires every tick (every 10 ticks = 0.5s)
        switch (pet.type()) {
            case DELVER -> delverPassive(pet, owner, entity);
            case BLAZER -> blazerPassive(pet, owner, entity);
            case AXOLITE -> axolitePassive(pet, owner);
            case SCOUT_FOX -> scoutPassive(pet, owner, entity);
            case GUARDIAN_WOLF -> guardianPassive(pet, owner, entity);
            case CHEST_PIG -> { /* purely passive carrier */ }
            case GHASTLING -> ghastlingPassive(pet, owner);
        }
    }

    // ---------------- passives ----------------

    private void delverPassive(Pet pet, Player owner, LivingEntity entity) {
        // Vacuum drops to the owner within 10 blocks
        for (Entity e : entity.getNearbyEntities(10, 6, 10)) {
            if (e instanceof Item item) {
                item.teleport(owner.getLocation());
                entity.getWorld().spawnParticle(Particle.GLOW, item.getLocation(), 4, 0.2, 0.2, 0.2, 0.05);
            }
        }
        // Ping nearby rare ore in the upcoming queue
        Island island = plugin.islands().ofPlayer(owner);
        if (island != null && pet.tickCounter() % 6 == 0) {
            Material upcoming = plugin.prophecies().upcoming(island, 0);
            if (plugin.prophecies().isRare(upcoming)) {
                entity.getWorld().spawnParticle(Particle.END_ROD,
                        island.centerBlock().clone().add(0.5, 1.4, 0.5), 1);
            }
        }
        pet.tickCounter(pet.tickCounter() + 1);
    }

    private void blazerPassive(Pet pet, Player owner, LivingEntity entity) {
        if (pet.tickCounter() % 4 != 0) { pet.tickCounter(pet.tickCounter() + 1); return; }
        pet.tickCounter(pet.tickCounter() + 1);
        if (!(entity instanceof Mob blazer)) return;
        for (Entity e : entity.getNearbyEntities(10, 6, 10)) {
            if (e instanceof Monster m && (m.getTarget() == owner || isHostileToOwner(m, owner))) {
                blazer.setTarget(m);
                m.damage(2.0 + pet.level() * 0.3, entity);
                m.setFireTicks(80);
                entity.getWorld().spawnParticle(Particle.FLAME, e.getLocation().add(0, 1, 0), 8, 0.2, 0.2, 0.2, 0.05);
                return;
            }
        }
    }

    private void axolitePassive(Pet pet, Player owner) {
        if (pet.tickCounter() % 8 != 0) { pet.tickCounter(pet.tickCounter() + 1); return; }
        pet.tickCounter(pet.tickCounter() + 1);
        owner.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 80, 0, true, false));
        for (PotionEffect bad : owner.getActivePotionEffects()) {
            if (isHarmful(bad.getType())) owner.removePotionEffect(bad.getType());
        }
    }

    private void scoutPassive(Pet pet, Player owner, LivingEntity entity) {
        Island island = plugin.islands().ofPlayer(owner);
        if (island == null) return;
        // Surface ping for upcoming rare blocks
        Material upcoming = plugin.prophecies().upcoming(island, 0);
        if (plugin.prophecies().isRare(upcoming)) {
            entity.getWorld().spawnParticle(Particle.END_ROD,
                    island.centerBlock().clone().add(0.5, 1.4, 0.5), 1);
        }
        // The +25% loot-room rate is read by BlockListener via hasScoutBoost()
    }

    private void guardianPassive(Pet pet, Player owner, LivingEntity entity) {
        // Damage redirection happens in onOwnerHurt(); just play idle FX
        if (pet.tickCounter() % 20 == 0) {
            entity.getWorld().spawnParticle(Particle.HEART, entity.getLocation().add(0, 1, 0), 1, 0.2, 0.2, 0.2, 0);
        }
        pet.tickCounter(pet.tickCounter() + 1);
    }

    private void ghastlingPassive(Pet pet, Player owner) {
        owner.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 60, 0, true, false));
    }

    // ---------------- actives (sneak-right-click) ----------------

    private boolean fireActive(Pet pet, Player owner) {
        long now = System.currentTimeMillis();
        if (now - pet.lastActiveMs() < ACTIVE_COOLDOWN_MS) {
            long remaining = (ACTIVE_COOLDOWN_MS - (now - pet.lastActiveMs())) / 1000;
            Msg.actionBar(owner, "<gray>Active ready in " + remaining + "s");
            return false;
        }
        pet.lastActiveMs(now);
        LivingEntity entity = pet.entity();
        switch (pet.type()) {
            case DELVER -> {
                Island island = plugin.islands().ofPlayer(owner);
                if (island != null) {
                    Location at = island.centerBlock().clone().add(0.5, 0, 0.5);
                    for (int i = 0; i < 60; i += 2) {
                        final int dy = i;
                        Bukkit.getScheduler().runTaskLater(plugin, () -> {
                            at.getWorld().spawnParticle(Particle.END_ROD, at.clone().add(0, dy * 0.5, 0), 2, 0.05, 0.05, 0.05, 0);
                        }, i);
                    }
                    Msg.actionBar(owner, "<aqua>Delver marks the next ore on the rift.");
                }
            }
            case BLAZER -> {
                owner.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 160, 0, true, false));
                for (Entity e : owner.getNearbyEntities(6, 4, 6)) {
                    if (e instanceof Monster m) {
                        m.setFireTicks(120);
                        m.damage(4.0 + pet.level() * 0.5, entity);
                    }
                }
                owner.getWorld().spawnParticle(Particle.FLAME, owner.getLocation().add(0, 1, 0), 40, 1, 1, 1, 0.05);
                owner.playSound(owner.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1f, 1.2f);
                Msg.actionBar(owner, "<gold>Blazer ignites nearby foes!");
            }
            case AXOLITE -> {
                owner.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 120, 1, true, false));
                owner.setHealth(Math.min(owner.getAttribute(Attribute.MAX_HEALTH).getValue(), owner.getHealth() + 20));
                owner.getWorld().spawnParticle(Particle.HEART, owner.getLocation().add(0, 1, 0), 20, 0.5, 0.5, 0.5, 0);
                owner.playSound(owner.getLocation(), Sound.ENTITY_AXOLOTL_HURT, 1f, 1.5f);
                Msg.actionBar(owner, "<aqua>Axolite restores you!");
            }
            case SCOUT_FOX -> {
                Island island = plugin.islands().ofPlayer(owner);
                if (island != null) {
                    plugin.prophecies().ensureQueue(island);
                    StringBuilder sb = new StringBuilder("<gray>Next: ");
                    int i = 0;
                    for (Material m : island.upcomingBlocks()) {
                        if (i++ >= 10) break;
                        boolean rare = plugin.prophecies().isRare(m);
                        sb.append(rare ? "<gold>" : "<white>").append(prettyMat(m)).append("<gray>, ");
                    }
                    Msg.send(owner, sb.toString());
                }
                Msg.actionBar(owner, "<green>Scout Fox reveals the prophecy.");
            }
            case GUARDIAN_WOLF -> {
                owner.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 120, 0, true, false));
                for (Entity e : owner.getNearbyEntities(10, 6, 10)) {
                    if (e instanceof LivingEntity le && !(le instanceof Player)) {
                        le.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 120, 0, true, false));
                    }
                }
                owner.getWorld().playSound(owner.getLocation(), Sound.ENTITY_WOLF_HOWL, 1f, 1f);
                Msg.actionBar(owner, "<yellow>Guardian Wolf howls!");
            }
            case CHEST_PIG -> {
                owner.openInventory(pet.storage());
                owner.playSound(owner.getLocation(), Sound.BLOCK_CHEST_OPEN, 1f, 1.2f);
            }
            case GHASTLING -> {
                owner.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, 100, 1, true, false));
                owner.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 200, 0, true, false));
                owner.playSound(owner.getLocation(), Sound.ENTITY_GHAST_SHOOT, 1f, 1.4f);
                Msg.actionBar(owner, "<aqua>Ghastling lifts you skyward!");
            }
        }
        return true;
    }

    /** Other systems check this to give scout-pet owners +25% loot-room chance. */
    public boolean hasScoutBoost(Player owner) {
        Pet p = activePets.get(owner.getUniqueId());
        return p != null && p.type() == PetType.SCOUT_FOX;
    }

    private boolean isHostileToOwner(Mob m, Player owner) {
        return m instanceof Monster && m.getLocation().distance(owner.getLocation()) < 12;
    }

    private boolean isHarmful(PotionEffectType t) {
        return t == PotionEffectType.POISON || t == PotionEffectType.WITHER
                || t == PotionEffectType.WEAKNESS || t == PotionEffectType.SLOWNESS
                || t == PotionEffectType.MINING_FATIGUE || t == PotionEffectType.HUNGER;
    }

    private String prettyMat(Material m) {
        String n = m.name().toLowerCase().replace('_', ' ');
        return Character.toUpperCase(n.charAt(0)) + n.substring(1);
    }

    /** Summon a pet for the player. Despawns any existing one. */
    public Pet summon(Player owner, PetType type) {
        PlayerProgression prog = plugin.progression().get(owner);
        prog.unlockPet(type.id);
        prog.setSelectedPet(type.id);
        despawn(owner);
        Location loc = owner.getLocation().add(1, 0, 0);
        Entity e = owner.getWorld().spawnEntity(loc, type.entityType);
        if (!(e instanceof LivingEntity le)) {
            e.remove();
            return null;
        }
        le.setRemoveWhenFarAway(false);
        le.setPersistent(true);
        le.customName(Component.text(prettyName(owner, type)));
        le.setCustomNameVisible(true);
        le.getPersistentDataContainer().set(PET_KEY, PersistentDataType.STRING, owner.getUniqueId().toString());
        le.getPersistentDataContainer().set(PET_TYPE_KEY, PersistentDataType.STRING, type.id);

        if (le.getAttribute(Attribute.MAX_HEALTH) != null) {
            le.getAttribute(Attribute.MAX_HEALTH).setBaseValue(20 + prog.getPetLevel(type.id) * 2.0);
            le.setHealth(le.getAttribute(Attribute.MAX_HEALTH).getValue());
        }

        Pet pet = new Pet(owner.getUniqueId(), type, le.getUniqueId(), prog.getPetLevel(type.id));
        activePets.put(owner.getUniqueId(), pet);
        owner.playSound(owner.getLocation(), Sound.ENTITY_CAT_AMBIENT, 1f, 1.4f);
        Msg.actionBar(owner, "<green>" + type.displayName + " <gray>is now following you.");
        return pet;
    }

    private String prettyName(Player owner, PetType type) {
        return type.displayName + " §7(§b" + owner.getName() + "§7)";
    }

    public void despawn(Player owner) {
        Pet pet = activePets.remove(owner.getUniqueId());
        if (pet != null) despawnSilently(pet);
    }

    private void despawnSilently(Pet pet) {
        LivingEntity entity = pet.entity();
        if (entity != null) entity.remove();
    }

    public Pet getActive(UUID ownerId) { return activePets.get(ownerId); }
    public Pet getActive(Player p) { return activePets.get(p.getUniqueId()); }

    public void shutdown() {
        if (tickTask != null) { tickTask.cancel(); tickTask = null; }
        for (Pet pet : activePets.values()) despawnSilently(pet);
        activePets.clear();
    }

    public void setTask(Pet pet, PetTask task) {
        pet.setTask(task);
        Player owner = pet.owner();
        if (owner != null) {
            Msg.actionBar(owner, "<aqua>" + pet.type().displayName + " task → <yellow>" + task.displayName);
            owner.playSound(owner.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1.2f);
        }
    }

    public void feed(Player owner, Pet pet) {
        long gained = 25L;
        pet.addXp(gained);
        while (pet.xp() >= Pet.xpForLevel(pet.level())) {
            pet.setXp(pet.xp() - Pet.xpForLevel(pet.level()));
            pet.setLevel(pet.level() + 1);
            plugin.progression().get(owner).levelUpPet(pet.type().id);
            Msg.title(owner, "<gold>" + pet.type().displayName + " Lv " + pet.level(), "<gray>+max HP, +damage");
            owner.playSound(owner.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.6f);
            LivingEntity entity = pet.entity();
            if (entity != null && entity.getAttribute(Attribute.MAX_HEALTH) != null) {
                entity.getAttribute(Attribute.MAX_HEALTH).setBaseValue(20 + pet.level() * 2.0);
            }
        }
    }

    // --- listeners ---

    @EventHandler
    public void onInteractAt(PlayerInteractAtEntityEvent event) {
        handleInteract(event.getPlayer(), event.getRightClicked(), event);
    }

    @EventHandler
    public void onInteract(PlayerInteractEntityEvent event) {
        handleInteract(event.getPlayer(), event.getRightClicked(), event);
    }

    private void handleInteract(Player player, Entity entity, org.bukkit.event.Cancellable event) {
        if (!entity.getPersistentDataContainer().has(PET_KEY, PersistentDataType.STRING)) return;
        String owner = entity.getPersistentDataContainer().get(PET_KEY, PersistentDataType.STRING);
        if (owner == null || !owner.equals(player.getUniqueId().toString())) {
            event.setCancelled(true);
            Msg.actionBar(player, "<red>That's not your pet.");
            return;
        }
        Pet pet = activePets.get(player.getUniqueId());
        if (pet == null) return;
        event.setCancelled(true);

        var hand = player.getInventory().getItemInMainHand();

        // Name tag → rename pet using the tag's text
        if (hand.getType() == Material.NAME_TAG && hand.hasItemMeta()
                && hand.getItemMeta().hasDisplayName()) {
            String newName = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                    .serialize(hand.getItemMeta().displayName());
            pet.setCustomName(newName);
            var ent = pet.entity();
            if (ent != null) {
                ent.customName(Component.text("§6" + newName + " §7(§b" + player.getName() + "§7)"));
                ent.setCustomNameVisible(true);
            }
            hand.setAmount(hand.getAmount() - 1);
            Msg.actionBar(player, "<green>Renamed your pet to <yellow>" + newName);
            return;
        }

        // Holding the pet's preferred food → feed it
        if (hand.getType() == pet.type().icon) {
            hand.setAmount(hand.getAmount() - 1);
            feed(player, pet);
            return;
        }

        // Sneak-right-click → fire the pet's active ability
        if (player.isSneaking()) {
            fireActive(pet, player);
            return;
        }

        // Otherwise open interaction menu (Bedrock-safe chest GUI)
        new PetGui(plugin, pet).open(player);
    }

    @EventHandler
    public void onPetDamage(EntityDamageEvent event) {
        if (!event.getEntity().getPersistentDataContainer().has(PET_KEY, PersistentDataType.STRING)) return;
        if (event.getCause() == EntityDamageEvent.DamageCause.VOID) {
            event.setCancelled(true);
            Pet pet = activePets.get(UUID.fromString(
                    event.getEntity().getPersistentDataContainer().get(PET_KEY, PersistentDataType.STRING)));
            if (pet != null) {
                Player owner = pet.owner();
                if (owner != null) event.getEntity().teleport(owner.getLocation());
            }
            return;
        }
        if (event.getCause() == EntityDamageEvent.DamageCause.DROWNING
                || event.getCause() == EntityDamageEvent.DamageCause.SUFFOCATION
                || event.getCause() == EntityDamageEvent.DamageCause.FALL
                || event.getCause() == EntityDamageEvent.DamageCause.STARVATION) {
            event.setCancelled(true);
        }
    }

    /** Stop hostile pets from targeting the owner. */
    @EventHandler
    public void onTarget(EntityTargetEvent event) {
        if (!event.getEntity().getPersistentDataContainer().has(PET_KEY, PersistentDataType.STRING)) return;
        if (!(event.getTarget() instanceof Player target)) return;
        String owner = event.getEntity().getPersistentDataContainer().get(PET_KEY, PersistentDataType.STRING);
        if (target.getUniqueId().toString().equals(owner)) event.setCancelled(true);
    }

    /**
     * Guardian-wolf passive: redirect 30% of owner damage to the wolf.
     * Runs on MONITOR priority and modifies the damage rather than cancelling so
     * other plugins still see the original event.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onOwnerHurt(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player owner)) return;
        Pet pet = activePets.get(owner.getUniqueId());
        if (pet == null || pet.type() != PetType.GUARDIAN_WOLF) return;
        LivingEntity wolf = pet.entity();
        if (wolf == null || wolf.isDead()) return;
        double redirect = event.getFinalDamage() * 0.30;
        event.setDamage(event.getFinalDamage() - redirect);
        wolf.damage(redirect);
        wolf.getWorld().spawnParticle(Particle.CRIT, wolf.getLocation().add(0, 1, 0), 6, 0.3, 0.3, 0.3, 0.05);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        // Auto-grant starter pets to anyone who doesn't already have them.
        PlayerProgression prog = plugin.progression().get(p);
        boolean grantedAny = false;
        for (PetType type : PetType.values()) {
            if (type.starter && prog.getPetLevel(type.id) == 0) {
                prog.unlockPet(type.id);
                grantedAny = true;
                if (prog.getSelectedPet() == null) prog.setSelectedPet(type.id);
            }
        }
        if (grantedAny) plugin.progression().save(p.getUniqueId());

        // Auto-resummon last selected pet a moment after join
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline()) return;
            String selected = plugin.progression().get(p).getSelectedPet();
            if (selected == null) return;
            PetType type = PetType.byId(selected);
            if (type != null) summon(p, type);
        }, 40L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        despawn(event.getPlayer());
    }
}
