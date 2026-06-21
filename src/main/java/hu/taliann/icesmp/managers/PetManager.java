package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.data.SpecializationType;
import hu.taliann.icesmp.utils.MessageManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.AbstractSkeleton;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.entity.Zombie;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Companion pets for the Beast Master and Necromancer (ROADMAP phase 12 + capture
 * extension). The pet can be ANY mob, obtained with a spec-specific capture item:
 * the Beast Master tames any non-hostile animal, the Necromancer binds any hostile
 * mob / undead. The pet's type, level, XP and name persist in the player's PDC and
 * re-apply on summon. Tameable pets follow via vanilla; the rest are kept near the
 * owner by a teleport-follow tick. Levels come from the owner's nearby kills.
 */
public final class PetManager {

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final MinionManager minionManager;
    private final SpecializationManager specializationManager;
    private final MessageManager messageManager;
    private final NamespacedKey levelKey;
    private final NamespacedKey xpKey;
    private final NamespacedKey nameKey;
    private final NamespacedKey typeKey;
    private final NamespacedKey entityKey;
    private final NamespacedKey healthModKey;
    private final NamespacedKey damageModKey;
    /** owner UUID → current combat target UUID (assist / defend). */
    private final Map<UUID, UUID> combatTargets = new ConcurrentHashMap<>();
    /** pet UUID → epoch ms when the pet may attack again. */
    private final Map<UUID, Long> attackReady = new ConcurrentHashMap<>();

    public PetManager(final JavaPlugin plugin, final ConfigManager configManager, final MinionManager minionManager,
                      final SpecializationManager specializationManager, final MessageManager messageManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.minionManager = minionManager;
        this.specializationManager = specializationManager;
        this.messageManager = messageManager;
        this.levelKey = new NamespacedKey(plugin, "pet_level");
        this.xpKey = new NamespacedKey(plugin, "pet_xp");
        this.nameKey = new NamespacedKey(plugin, "pet_name");
        this.typeKey = new NamespacedKey(plugin, "pet_type");
        this.entityKey = new NamespacedKey(plugin, "pet_entity");
        this.healthModKey = new NamespacedKey(plugin, "pet_health_mod");
        this.damageModKey = new NamespacedKey(plugin, "pet_damage_mod");
    }

    public boolean isBeastMaster(final Player player) {
        return specializationManager.getClassSpecialization(player) == SpecializationType.BEAST_MASTER;
    }

    public boolean isNecromancer(final Player player) {
        return specializationManager.getClassSpecialization(player) == SpecializationType.NECROMANCER;
    }

    public boolean canOwnPet(final Player player) {
        return isBeastMaster(player) || isNecromancer(player);
    }

    public int getLevel(final Player player) {
        return Math.max(1, player.getPersistentDataContainer().getOrDefault(levelKey, PersistentDataType.INTEGER, 1));
    }

    public int getXp(final Player player) {
        return player.getPersistentDataContainer().getOrDefault(xpKey, PersistentDataType.INTEGER, 0);
    }

    public String getName(final Player player) {
        return player.getPersistentDataContainer().getOrDefault(nameKey, PersistentDataType.STRING, "Társ");
    }

    /** Whether the player may capture the target with their spec's capture item. */
    public boolean isValidTarget(final Player player, final Entity target) {
        if (!(target instanceof Mob) || target instanceof Player || minionManager.isMinion(target)) {
            return false;
        }
        if (isBeastMaster(player)) {
            return !(target instanceof Monster); // any non-hostile animal/mob
        }
        if (isNecromancer(player)) {
            return target instanceof Monster; // any hostile mob / undead
        }
        return false;
    }

    /**
     * Captures the clicked mob as the player's companion (replacing any existing one).
     *
     * @return null on success, otherwise a message key
     */
    public String capture(final Player player, final Entity target) {
        if (!canOwnPet(player)) {
            return "pet-not-allowed";
        }
        if (!isValidTarget(player, target) || !(target instanceof Mob mob)) {
            return "pet-invalid-target";
        }

        removeActive(player);
        player.getPersistentDataContainer().set(typeKey, PersistentDataType.STRING, mob.getType().name());
        adopt(mob, player);
        return null;
    }

    /**
     * (Re)summons the companion of the player's stored type — or a sensible default
     * (wolf for Beast Master, zombie for Necromancer) if nothing was captured yet.
     *
     * @return null on success, otherwise a message key
     */
    public String summon(final Player player) {
        if (!canOwnPet(player)) {
            return "pet-not-allowed";
        }

        final EntityType type = resolveType(player);
        if (type == null || type.getEntityClass() == null || !Mob.class.isAssignableFrom(type.getEntityClass())) {
            return "pet-none-captured";
        }

        removeActive(player);
        final Mob mob = (Mob) player.getWorld().spawn(player.getLocation(), type.getEntityClass().asSubclass(Mob.class));
        adopt(mob, player);
        return null;
    }

    public boolean dismiss(final Player player) {
        final boolean removed = removeActive(player);
        player.getPersistentDataContainer().remove(entityKey);
        return removed;
    }

    /**
     * Handles a pet's death: clears the combat state for that pet, and if it was the
     * owner's active companion, clears the stored reference and notifies them. The
     * owner-side PDC/message runs on the owner's region thread (Folia-safe); call this
     * from an EntityDeathEvent for minion-tagged mobs.
     */
    public void handlePetDeath(final LivingEntity dead) {
        final UUID ownerId = minionManager.getOwner(dead);
        if (ownerId == null) {
            return;
        }
        final UUID deadId = dead.getUniqueId();
        combatTargets.remove(ownerId);
        attackReady.remove(deadId);

        final Player owner = Bukkit.getPlayer(ownerId);
        if (owner == null) {
            return; // offline: the stale entityKey resolves to a dead UUID harmlessly on next summon
        }
        owner.getScheduler().run(plugin, task -> {
            final String raw = owner.getPersistentDataContainer().get(entityKey, PersistentDataType.STRING);
            if (deadId.toString().equals(raw)) {
                owner.getPersistentDataContainer().remove(entityKey);
                owner.sendMessage(messageManager.getMessage(
                        "pet-died",
                        "<gray>A társad elesett a harcban. <dark_gray>(/pet summon az új idézéshez)</dark_gray></gray>"));
            }
        }, null);
    }

    public boolean setName(final Player player, final String name) {
        if (name == null || name.isBlank() || name.length() > 24) {
            return false;
        }
        player.getPersistentDataContainer().set(nameKey, PersistentDataType.STRING, name);
        final Mob pet = activePet(player);
        if (pet != null) {
            updateName(pet, player);
        }
        return true;
    }

    /** Awards companion XP for the owner; levels up (rebuffing the active pet) on threshold. */
    public void addXp(final Player player, final int amount) {
        if (amount <= 0 || !canOwnPet(player)) {
            return;
        }
        final int maxLevel = Math.max(1, configManager.getInt("pets.companion.max-level", 30));
        int level = getLevel(player);
        if (level >= maxLevel) {
            return;
        }

        int xp = getXp(player) + amount;
        boolean leveled = false;
        int cost = levelCost(level);
        while (level < maxLevel && xp >= cost) {
            xp -= cost;
            level++;
            leveled = true;
            cost = levelCost(level);
        }

        player.getPersistentDataContainer().set(xpKey, PersistentDataType.INTEGER, xp);
        if (leveled) {
            player.getPersistentDataContainer().set(levelKey, PersistentDataType.INTEGER, level);
            final Mob pet = activePet(player);
            if (pet != null) {
                applyBuffs(pet, level, false);
                updateName(pet, player);
            }
            player.sendMessage(messageManager.getMessage(
                    "pet-level-up",
                    "<dark_green>🐾 A társad szintet lépett: <white>{level}</white></dark_green>",
                    Map.of("level", String.valueOf(level))));
        }
    }

    /**
     * Records a combat target for the owner's active pet (assist when the owner
     * strikes a mob, or defend when the owner is struck). Ignored if the target is
     * the owner or one of the owner's own minions, so a pet never turns on its allies.
     */
    public void setCombatTarget(final Player owner, final LivingEntity target) {
        if (owner == null || target == null || target.isDead() || !target.isValid()) {
            return;
        }
        if (target.getUniqueId().equals(owner.getUniqueId()) || minionManager.isOwnedBy(target, owner.getUniqueId())) {
            return;
        }
        if (activePet(owner) == null) {
            return;
        }
        combatTargets.put(owner.getUniqueId(), target.getUniqueId());
    }

    /**
     * Drives every active companion each scheduler pass. The pet's behaviour is
     * controlled entirely by the plugin (not the mob's own AI): in ACTIVE stance it
     * chases its target via the pathfinder and lands plugin-applied hits, so even a
     * peaceful animal fights like a real pet; with no target it follows the owner.
     * STAY holds position, PASSIVE only follows.
     */
    public void tick() {
        final double followSq = Math.pow(Math.max(4.0D, configManager.getDouble("pets.companion.follow-distance", 16.0D)), 2);
        final double followStartSq = Math.pow(Math.max(2.0D, configManager.getDouble("pets.companion.follow-start-distance", 5.0D)), 2);
        final double reach = Math.max(1.5D, configManager.getDouble("pets.companion.attack-reach", 2.6D));
        final double aggro = Math.max(0.0D, configManager.getDouble("pets.companion.aggro-range", 10.0D));
        final double leash = Math.max(8.0D, configManager.getDouble("pets.companion.leash-range", 24.0D));
        final double chaseSpeed = Math.max(0.1D, configManager.getDouble("pets.companion.chase-speed", 1.3D));
        final long cooldownMs = Math.max(200L, configManager.getInt("pets.companion.attack-cooldown-ticks", 16) * 50L);

        // Folia: read each owner's PDC + location on the OWNER's region thread, snapshot it,
        // then hop to the PET's region thread for all pet mutations (the pet may be elsewhere).
        for (final Player owner : Bukkit.getOnlinePlayers()) {
            owner.getScheduler().run(plugin, ownerTask ->
                    tickOwner(owner, followSq, followStartSq, reach, aggro, leash, chaseSpeed, cooldownMs), null);
        }
    }

    private void tickOwner(final Player owner, final double followSq, final double followStartSq, final double reach,
                           final double aggro, final double leash, final double chaseSpeed, final long cooldownMs) {
        final String raw = owner.getPersistentDataContainer().get(entityKey, PersistentDataType.STRING);
        if (raw == null) {
            return;
        }
        final Entity entity;
        try {
            entity = Bukkit.getEntity(UUID.fromString(raw));
        } catch (final IllegalArgumentException exception) {
            return;
        }
        if (!(entity instanceof Mob pet)) {
            return;
        }
        final UUID ownerId = owner.getUniqueId();
        final int level = getLevel(owner);
        final Location ownerLoc = owner.getLocation();
        final World ownerWorld = owner.getWorld();
        pet.getScheduler().run(plugin, petTask ->
                runPetTick(pet, ownerId, ownerLoc, ownerWorld, level, followSq, followStartSq, reach, aggro, leash, chaseSpeed, cooldownMs), null);
    }

    private void runPetTick(final Mob pet, final UUID ownerId, final Location ownerLoc, final World ownerWorld,
                            final int level, final double followSq, final double followStartSq, final double reach,
                            final double aggro, final double leash, final double chaseSpeed, final long cooldownMs) {
        if (!pet.isValid()) {
            return;
        }

        final MinionManager.Stance stance = minionManager.getStance(pet);
        if (stance == MinionManager.Stance.STAY) {
            combatTargets.remove(ownerId);
            return; // hold position — no follow, no combat
        }

        LivingEntity target = null;
        if (stance == MinionManager.Stance.ACTIVE) {
            target = resolveTarget(ownerId, ownerWorld, ownerLoc, leash);
            if (target == null) {
                target = acquireNearbyThreat(pet, ownerId, aggro);
                if (target != null) {
                    combatTargets.put(ownerId, target.getUniqueId());
                }
            }
        } else {
            combatTargets.remove(ownerId); // PASSIVE never fights
        }

        if (target != null) {
            attack(pet, target, level, reach, chaseSpeed, cooldownMs);
            return;
        }

        // No target → follow the owner. Every pet trails its owner by default: it walks toward
        // them once it lags past the follow-start radius, and teleports to catch up only when it
        // falls too far behind or ends up in another world.
        followOwner(pet, ownerLoc, ownerWorld, followSq, followStartSq, chaseSpeed);
    }

    /** Keeps an idle pet near its owner (snapshot location): walk to trail, teleport to catch up. */
    private void followOwner(final Mob pet, final Location ownerLoc, final World ownerWorld, final double followSq,
                             final double followStartSq, final double chaseSpeed) {
        if (!ownerWorld.equals(pet.getWorld())) {
            pet.teleportAsync(ownerLoc);
            return;
        }
        final double distSq = pet.getLocation().distanceSquared(ownerLoc);
        if (distSq > followSq) {
            pet.teleportAsync(ownerLoc);
        } else if (distSq > followStartSq) {
            pet.getPathfinder().moveTo(ownerLoc, chaseSpeed);
        }
    }

    /** Validates the stored combat target (alive, same world, within the owner's leash). */
    private LivingEntity resolveTarget(final UUID ownerId, final World ownerWorld, final Location ownerLoc, final double leash) {
        final UUID id = combatTargets.get(ownerId);
        if (id == null) {
            return null;
        }
        final Entity entity = Bukkit.getEntity(id);
        if (!(entity instanceof LivingEntity living) || living.isDead() || !living.isValid()
                || living.getUniqueId().equals(ownerId)
                || !living.getWorld().equals(ownerWorld)
                || living.getLocation().distanceSquared(ownerLoc) > leash * leash) {
            combatTargets.remove(ownerId);
            return null;
        }
        return living;
    }

    /** Picks the nearest hostile mob around the pet to defend against (excludes allies). */
    private LivingEntity acquireNearbyThreat(final Mob pet, final UUID ownerId, final double aggro) {
        if (aggro <= 0.0D) {
            return null;
        }
        LivingEntity best = null;
        double bestSq = aggro * aggro;
        for (final Entity nearby : pet.getNearbyEntities(aggro, aggro, aggro)) {
            if (!(nearby instanceof Monster monster) || monster.isDead() || !monster.isValid()
                    || minionManager.isOwnedBy(monster, ownerId)) {
                continue;
            }
            final double sq = monster.getLocation().distanceSquared(pet.getLocation());
            if (sq < bestSq) {
                bestSq = sq;
                best = monster;
            }
        }
        return best;
    }

    /** Chases the target via the pathfinder and lands a plugin-applied hit on cooldown. */
    private void attack(final Mob pet, final LivingEntity target, final int level, final double reach,
                        final double chaseSpeed, final long cooldownMs) {
        pet.setTarget(target); // reinforce mobs that do have attack AI
        if (pet.getLocation().distanceSquared(target.getLocation()) > reach * reach) {
            pet.getPathfinder().moveTo(target, chaseSpeed); // AI-independent chase
            return;
        }
        final long now = System.currentTimeMillis();
        final Long ready = attackReady.get(pet.getUniqueId());
        if (ready != null && now < ready) {
            return;
        }
        attackReady.put(pet.getUniqueId(), now + cooldownMs);
        pet.swingMainHand();
        target.damage(petDamage(level), pet); // the plugin lands the hit, whatever the mob is
    }

    private double petDamage(final int level) {
        final double base = Math.max(0.5D, configManager.getDouble("pets.companion.attack-damage-base", 3.0D));
        final double perLevel = Math.max(0.0D, configManager.getDouble("pets.companion.damage-per-level", 0.5D));
        return base + (level * perLevel);
    }

    private EntityType resolveType(final Player player) {
        final String stored = player.getPersistentDataContainer().get(typeKey, PersistentDataType.STRING);
        if (stored != null) {
            try {
                return EntityType.valueOf(stored.toUpperCase(Locale.ROOT));
            } catch (final IllegalArgumentException ignored) {
                // Fall through to the default.
            }
        }
        return isNecromancer(player) ? EntityType.ZOMBIE : EntityType.WOLF;
    }

    private void adopt(final Mob mob, final Player player) {
        mob.setPersistent(true);
        mob.setRemoveWhenFarAway(false);
        if (mob instanceof Tameable tameable) {
            tameable.setTamed(true);
            tameable.setOwner(player);
        }
        if (mob instanceof Zombie zombie) {
            zombie.setShouldBurnInDay(false);
        }
        if (mob instanceof AbstractSkeleton skeleton) {
            skeleton.setShouldBurnInDay(false);
        }
        applyBuffs(mob, getLevel(player), true);
        updateName(mob, player);
        minionManager.tag(mob, player.getUniqueId());
        player.getPersistentDataContainer().set(entityKey, PersistentDataType.STRING, mob.getUniqueId().toString());
    }

    private int levelCost(final int level) {
        final int base = Math.max(1, configManager.getInt("pets.companion.base-xp", 10));
        final int increment = Math.max(0, configManager.getInt("pets.companion.increment-per-level", 5));
        return base + ((level - 1) * increment);
    }

    /**
     * (Re)applies the level-based attribute buffs. {@code heal} fully restores the
     * pet to its new max health — only wanted on summon/adopt. On level-up the cap
     * grows but current health is NOT topped up, so a kill mid-fight can't instantly
     * heal a near-dead pet to full.
     */
    private void applyBuffs(final LivingEntity pet, final int level, final boolean heal) {
        final double healthPerLevel = Math.max(0.0D, configManager.getDouble("pets.companion.health-per-level", 2.0D));
        final double damagePerLevel = Math.max(0.0D, configManager.getDouble("pets.companion.damage-per-level", 0.5D));

        // Idempotent attribute modifiers (re-applied on level-up without compounding).
        applyModifier(pet, Attribute.MAX_HEALTH, healthModKey, level * healthPerLevel);
        applyModifier(pet, Attribute.ATTACK_DAMAGE, damageModKey, level * damagePerLevel);

        if (heal) {
            final AttributeInstance maxHealth = pet.getAttribute(Attribute.MAX_HEALTH);
            if (maxHealth != null) {
                pet.setHealth(maxHealth.getValue());
            }
        }
    }

    private void applyModifier(final LivingEntity pet, final Attribute attribute, final NamespacedKey key, final double amount) {
        final AttributeInstance instance = pet.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        for (final AttributeModifier modifier : instance.getModifiers()) {
            if (key.equals(modifier.getKey())) {
                instance.removeModifier(modifier);
            }
        }
        if (amount != 0.0D) {
            instance.addModifier(new AttributeModifier(key, amount, AttributeModifier.Operation.ADD_NUMBER));
        }
    }

    private void updateName(final Mob pet, final Player player) {
        pet.customName(Component.text(getName(player) + " [Lv " + getLevel(player) + "]", NamedTextColor.GREEN));
        pet.setCustomNameVisible(true);
    }

    private Mob activePet(final Player player) {
        final String raw = player.getPersistentDataContainer().get(entityKey, PersistentDataType.STRING);
        if (raw == null) {
            return null;
        }
        try {
            final Entity entity = Bukkit.getEntity(UUID.fromString(raw));
            return entity instanceof Mob mob && mob.isValid() ? mob : null;
        } catch (final IllegalArgumentException exception) {
            return null;
        }
    }

    private boolean removeActive(final Player player) {
        combatTargets.remove(player.getUniqueId());
        final String raw = player.getPersistentDataContainer().get(entityKey, PersistentDataType.STRING);
        if (raw == null) {
            return false;
        }
        try {
            final UUID petId = UUID.fromString(raw);
            attackReady.remove(petId);
            final Entity entity = Bukkit.getEntity(petId);
            if (entity != null) {
                entity.getScheduler().run(plugin, task -> entity.remove(), null);
                return true;
            }
        } catch (final IllegalArgumentException ignored) {
            // Malformed stored id.
        }
        return false;
    }
}
