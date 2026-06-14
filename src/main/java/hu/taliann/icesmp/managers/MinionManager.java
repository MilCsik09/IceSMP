package hu.taliann.icesmp.managers;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manager for summoned minions (necromancer undead, beast master animals).
 * Minions are marked with a 'minion_owner' PDC tag (loyalty) and a
 * 'minion_stance' tag (player-controlled behaviour). An in-memory registry of
 * minion UUIDs per owner backs the per-player summon cap; it lazily prunes
 * dead/despawned minions, so no death listener is required.
 */
public final class MinionManager {

    /** Pet stances cycled by the owner (ideas.md "Pet parancsok"). */
    public enum Stance {
        ACTIVE,   // attacks enemies (default)
        PASSIVE,  // keeps its AI but never picks a target
        STAY      // frozen in place (AI off)
    }

    private final NamespacedKey minionOwnerKey;
    private final NamespacedKey minionStanceKey;
    private final Map<UUID, Set<UUID>> minionsByOwner = new ConcurrentHashMap<>();

    public MinionManager(final JavaPlugin plugin) {
        this.minionOwnerKey = new NamespacedKey(plugin, "minion_owner");
        this.minionStanceKey = new NamespacedKey(plugin, "minion_stance");
    }

    /**
     * Tags a freshly summoned minion with its owner, registers it for the
     * summon cap, and sets the default ACTIVE stance.
     *
     * @param minion the summoned mob
     * @param owner the summoner
     */
    public void tag(final Mob minion, final UUID owner) {
        if (minion == null || owner == null) {
            return;
        }

        minion.getPersistentDataContainer().set(minionOwnerKey, PersistentDataType.STRING, owner.toString());
        minion.getPersistentDataContainer().set(minionStanceKey, PersistentDataType.STRING, Stance.ACTIVE.name());
        minionsByOwner.computeIfAbsent(owner, key -> ConcurrentHashMap.newKeySet()).add(minion.getUniqueId());
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

    public Stance getStance(final Entity entity) {
        if (entity == null) {
            return Stance.ACTIVE;
        }

        final String raw = entity.getPersistentDataContainer().get(minionStanceKey, PersistentDataType.STRING);
        if (raw == null) {
            return Stance.ACTIVE;
        }

        try {
            return Stance.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (final IllegalArgumentException exception) {
            return Stance.ACTIVE;
        }
    }

    /**
     * Cycles a minion's stance (ACTIVE → PASSIVE → STAY → ACTIVE) and applies
     * the immediate effect: STAY freezes the AI, the others enable it; PASSIVE
     * and STAY also drop the current target.
     *
     * @param minion the minion to retune
     * @return the new stance
     */
    public Stance cycleStance(final Mob minion) {
        final Stance next = switch (getStance(minion)) {
            case ACTIVE -> Stance.PASSIVE;
            case PASSIVE -> Stance.STAY;
            case STAY -> Stance.ACTIVE;
        };

        minion.getPersistentDataContainer().set(minionStanceKey, PersistentDataType.STRING, next.name());
        minion.setAI(next != Stance.STAY);
        if (next != Stance.ACTIVE) {
            minion.setTarget(null);
        }
        return next;
    }

    /**
     * Counts an owner's currently alive minions, pruning stale registry entries.
     *
     * @param owner the summoner
     * @return the number of valid, living minions
     */
    public int countActive(final UUID owner) {
        final Set<UUID> ids = minionsByOwner.get(owner);
        if (ids == null || ids.isEmpty()) {
            return 0;
        }

        int alive = 0;
        for (final UUID id : new HashSet<>(ids)) {
            final Entity entity = Bukkit.getEntity(id);
            if (entity != null && entity.isValid() && isMinion(entity)) {
                alive++;
            } else {
                ids.remove(id);
            }
        }
        return alive;
    }
}
