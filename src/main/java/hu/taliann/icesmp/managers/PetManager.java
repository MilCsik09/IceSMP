package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.data.SpecializationType;
import hu.taliann.icesmp.utils.MessageManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
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
                applyBuffs(pet, level);
                updateName(pet, player);
            }
            player.sendMessage(messageManager.getMessage(
                    "pet-level-up",
                    "<dark_green>🐾 A társad szintet lépett: <white>{level}</white></dark_green>",
                    Map.of("level", String.valueOf(level))));
        }
    }

    /**
     * Keeps non-tameable companions near their owner: any pet that has drifted too
     * far (or to another world) is teleported back to its owner. Run periodically.
     */
    public void followTick() {
        final double followSq = Math.pow(Math.max(4.0D, configManager.getDouble("pets.companion.follow-distance", 16.0D)), 2);
        for (final Player owner : Bukkit.getOnlinePlayers()) {
            final String raw = owner.getPersistentDataContainer().get(entityKey, PersistentDataType.STRING);
            if (raw == null) {
                continue;
            }
            final Entity pet;
            try {
                pet = Bukkit.getEntity(UUID.fromString(raw));
            } catch (final IllegalArgumentException exception) {
                continue;
            }
            if (pet == null || !pet.isValid()) {
                continue;
            }
            final UUID ownerId = owner.getUniqueId();
            pet.getScheduler().run(plugin, task -> {
                final Player live = Bukkit.getPlayer(ownerId);
                if (live == null || !live.isOnline()) {
                    return;
                }
                if (!live.getWorld().equals(pet.getWorld()) || pet.getLocation().distanceSquared(live.getLocation()) > followSq) {
                    pet.teleportAsync(live.getLocation());
                }
            }, null);
        }
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
        applyBuffs(mob, getLevel(player));
        updateName(mob, player);
        minionManager.tag(mob, player.getUniqueId());
        player.getPersistentDataContainer().set(entityKey, PersistentDataType.STRING, mob.getUniqueId().toString());
    }

    private int levelCost(final int level) {
        final int base = Math.max(1, configManager.getInt("pets.companion.base-xp", 10));
        final int increment = Math.max(0, configManager.getInt("pets.companion.increment-per-level", 5));
        return base + ((level - 1) * increment);
    }

    private void applyBuffs(final LivingEntity pet, final int level) {
        final double healthPerLevel = Math.max(0.0D, configManager.getDouble("pets.companion.health-per-level", 2.0D));
        final double damagePerLevel = Math.max(0.0D, configManager.getDouble("pets.companion.damage-per-level", 0.5D));

        // Idempotent attribute modifiers (re-applied on level-up without compounding).
        applyModifier(pet, Attribute.MAX_HEALTH, healthModKey, level * healthPerLevel);
        applyModifier(pet, Attribute.ATTACK_DAMAGE, damageModKey, level * damagePerLevel);

        final AttributeInstance maxHealth = pet.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth != null) {
            pet.setHealth(maxHealth.getValue());
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
        final String raw = player.getPersistentDataContainer().get(entityKey, PersistentDataType.STRING);
        if (raw == null) {
            return false;
        }
        try {
            final Entity entity = Bukkit.getEntity(UUID.fromString(raw));
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
