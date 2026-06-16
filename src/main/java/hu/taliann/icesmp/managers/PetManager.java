package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.data.SpecializationType;
import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Wolf;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;

/**
 * Beast Master companion (ROADMAP phase 12): a named, leveling, persistent pet.
 * It is a tamed wolf owned by the player — so vanilla handles following and
 * cross-region teleporting — while its level, XP and name persist in the player's
 * PDC and re-apply on summon. Levels come from the owner's nearby kills.
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
    private final NamespacedKey entityKey;

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
        this.entityKey = new NamespacedKey(plugin, "pet_entity");
    }

    public boolean isBeastMaster(final Player player) {
        return specializationManager.getClassSpecialization(player) == SpecializationType.BEAST_MASTER;
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

    /**
     * Summons (or re-summons) the companion at the player. Beast Master only.
     *
     * @param player the owner
     * @return null on success, otherwise a message key
     */
    public String summon(final Player player) {
        if (!isBeastMaster(player)) {
            return "pet-not-beastmaster";
        }

        removeActive(player);

        final Wolf wolf = player.getWorld().spawn(player.getLocation(), Wolf.class);
        wolf.setTamed(true);
        wolf.setOwner(player);
        wolf.setPersistent(true);
        wolf.setRemoveWhenFarAway(false);
        wolf.setSitting(false);
        applyBuffs(wolf, getLevel(player));
        updateName(wolf, player);
        minionManager.tag(wolf, player.getUniqueId());

        player.getPersistentDataContainer().set(entityKey, PersistentDataType.STRING, wolf.getUniqueId().toString());
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
        final Wolf pet = activePet(player);
        if (pet != null) {
            updateName(pet, player);
        }
        return true;
    }

    /** Awards companion XP for the owner; levels up (rebuffing the active pet) on threshold. */
    public void addXp(final Player player, final int amount) {
        if (amount <= 0 || !isBeastMaster(player)) {
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
            final Wolf pet = activePet(player);
            if (pet != null) {
                applyBuffs(pet, level);
                updateName(pet, player);
            }
            final int shownLevel = level;
            player.sendMessage(messageManager.getMessage(
                    "pet-level-up",
                    "<dark_green>🐺 A társad szintet lépett: <white>{level}</white></dark_green>",
                    Map.of("level", String.valueOf(shownLevel))));
        }
    }

    private int levelCost(final int level) {
        final int base = Math.max(1, configManager.getInt("pets.companion.base-xp", 10));
        final int increment = Math.max(0, configManager.getInt("pets.companion.increment-per-level", 5));
        return base + ((level - 1) * increment);
    }

    private void applyBuffs(final Wolf wolf, final int level) {
        final double healthPerLevel = Math.max(0.0D, configManager.getDouble("pets.companion.health-per-level", 2.0D));
        final double damagePerLevel = Math.max(0.0D, configManager.getDouble("pets.companion.damage-per-level", 0.5D));

        final AttributeInstance maxHealth = wolf.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth != null) {
            maxHealth.setBaseValue(20.0D + (level * healthPerLevel));
            wolf.setHealth(maxHealth.getValue());
        }
        final AttributeInstance attack = wolf.getAttribute(Attribute.ATTACK_DAMAGE);
        if (attack != null) {
            attack.setBaseValue(4.0D + (level * damagePerLevel));
        }
    }

    private void updateName(final Wolf wolf, final Player player) {
        wolf.customName(net.kyori.adventure.text.Component.text(getName(player) + " [Lv " + getLevel(player) + "]",
                net.kyori.adventure.text.format.NamedTextColor.GREEN));
        wolf.setCustomNameVisible(true);
    }

    private Wolf activePet(final Player player) {
        final String raw = player.getPersistentDataContainer().get(entityKey, PersistentDataType.STRING);
        if (raw == null) {
            return null;
        }
        try {
            final Entity entity = Bukkit.getEntity(UUID.fromString(raw));
            return entity instanceof Wolf wolf && wolf.isValid() ? wolf : null;
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
                // Folia: remove on the pet's own region thread.
                entity.getScheduler().run(plugin, task -> entity.remove(), null);
                return true;
            }
        } catch (final IllegalArgumentException ignored) {
            // Malformed stored id.
        }
        return false;
    }
}
