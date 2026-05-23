package com.nova.novablock.pet;

import com.nova.novablock.NovaBlock;
import com.nova.novablock.gui.PetGui;
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
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
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
import java.util.Map;
import java.util.UUID;

public class PetManager implements Listener {

    public static final NamespacedKey PET_KEY = new NamespacedKey("novablock", "pet_owner");
    public static final NamespacedKey PET_TYPE_KEY = new NamespacedKey("novablock", "pet_type");

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

        // Task-specific behavior
        switch (pet.task()) {
            case COMBAT -> combatTick(pet, owner, entity);
            case MINE_ASSIST -> mineAssistTick(pet, owner, entity);
            case SCOUT -> scoutTick(pet, owner, entity);
            case SUPPORT -> supportTick(pet, owner);
            case STORAGE, MOUNT, FOLLOW, REST -> { /* passive */ }
        }
    }

    private void combatTick(Pet pet, Player owner, LivingEntity entity) {
        if (!(entity instanceof org.bukkit.entity.Mob mob)) return;
        for (Entity e : entity.getNearbyEntities(8, 4, 8)) {
            if (e instanceof Monster m && m.getTarget() == owner) {
                mob.setTarget(m);
                m.damage(1.0 + pet.level() * 0.5, entity);
                entity.getWorld().spawnParticle(Particle.CRIT, e.getLocation().add(0, 1, 0), 6, 0.2, 0.2, 0.2, 0.1);
                return;
            }
        }
    }

    private void mineAssistTick(Pet pet, Player owner, LivingEntity entity) {
        for (Entity e : entity.getNearbyEntities(8, 4, 8)) {
            if (e instanceof Item item) {
                item.teleport(owner.getLocation());
                entity.getWorld().spawnParticle(Particle.GLOW, e.getLocation(), 6, 0.2, 0.2, 0.2, 0.05);
            }
        }
    }

    private void scoutTick(Pet pet, Player owner, LivingEntity entity) {
        // Highlight rare blocks within 6 blocks of the OneBlock center via particle pings
        var island = plugin.islands().ofPlayer(owner);
        if (island == null) return;
        Material upcoming = plugin.prophecies().upcoming(island, 0);
        if (plugin.prophecies().isRare(upcoming)) {
            entity.getWorld().spawnParticle(Particle.END_ROD,
                    island.centerBlock().clone().add(0.5, 1.5, 0.5), 1);
        }
    }

    private void supportTick(Pet pet, Player owner) {
        long now = System.currentTimeMillis();
        if (now - pet.lastSupportTickMs() < 4000) return;
        pet.setLastSupportTickMs(now);
        owner.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 60, 0, true, false));
        for (PotionEffect bad : owner.getActivePotionEffects()) {
            if (isHarmful(bad.getType())) owner.removePotionEffect(bad.getType());
        }
    }

    private boolean isHarmful(PotionEffectType t) {
        return t == PotionEffectType.POISON || t == PotionEffectType.WITHER
                || t == PotionEffectType.WEAKNESS || t == PotionEffectType.SLOWNESS
                || t == PotionEffectType.MINING_FATIGUE || t == PotionEffectType.HUNGER;
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

        // Name tag → rename pet using the tag's text (works on Bedrock since the
        // tag is edited in an anvil, no chat prompt required).
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

        // Storage pet: shift-right-click opens chest; plain right-click opens interaction menu
        if (pet.type() == PetType.CHEST_PIG && player.isSneaking()) {
            player.openInventory(pet.storage());
            return;
        }
        // Ghastling: sneak-right-click grants 5s of Levitation so the owner can fly to
        // their next platform. addPassenger on a Ghast is unreliable; this is the polished
        // replacement and works identically on Bedrock.
        if (pet.type() == PetType.GHASTLING && player.isSneaking()) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, 100, 1, true, false));
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 200, 0, true, false));
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_GHAST_SHOOT, 1f, 1.4f);
            Msg.actionBar(player, "<aqua>Ghastling lifts you skyward!");
            return;
        }

        // Otherwise open interaction menu (Bedrock-safe chest GUI)
        new PetGui(plugin, pet).open(player);
    }

    @EventHandler
    public void onPetDamage(EntityDamageEvent event) {
        if (!event.getEntity().getPersistentDataContainer().has(PET_KEY, PersistentDataType.STRING)) return;
        // Pets should be effectively invulnerable to environmental damage to avoid frustration
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

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        // Auto-resummon last selected pet
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Player p = event.getPlayer();
            String selected = plugin.progression().get(p).getSelectedPet();
            if (selected == null) return;
            PetType type = PetType.byId(selected);
            if (type != null && p.isOnline()) summon(p, type);
        }, 40L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        despawn(event.getPlayer());
    }
}
