package hu.taliann.icesmp.managers;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;

/**
 * Manager for summoned minions (necromancer undead, beast master animals).
 * Minions are marked with a 'minion_owner' PDC tag; the MinionProtectionListener
 * uses it to stop minions from turning on their summoner or on each other.
 */
public final class MinionManager {

    private final NamespacedKey minionOwnerKey;

    public MinionManager(final JavaPlugin plugin) {
        this.minionOwnerKey = new NamespacedKey(plugin, "minion_owner");
    }

    public void tag(final Mob minion, final UUID owner) {
        if (minion == null || owner == null) {
            return;
        }

        minion.getPersistentDataContainer().set(minionOwnerKey, PersistentDataType.STRING, owner.toString());
    }

    public boolean isMinion(final Entity entity) {
        return getOwner(entity) != null;
    }

    /**
     * Gets the summoner of a minion.
     *
     * @param entity the entity to inspect
     * @return the owner UUID, or null if the entity is not a minion
     */
    public UUID getOwner(final Entity entity) {
        if (entity == null) {
            return null;
        }

        final String rawOwner = entity.getPersistentDataContainer().get(minionOwnerKey, PersistentDataType.STRING);
        if (rawOwner == null || rawOwner.isBlank()) {
            return null;
        }

        try {
            return UUID.fromString(rawOwner);
        } catch (final IllegalArgumentException exception) {
            return null;
        }
    }

    public boolean isOwnedBy(final Entity entity, final UUID playerId) {
        final UUID owner = getOwner(entity);
        return owner != null && owner.equals(playerId);
    }
}
