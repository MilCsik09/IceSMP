package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.utils.TransientEntities;
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

/** Manager for summoned minions with UUID-only lifecycle tracking. */
public final class MinionManager {

    public enum Stance {
        ACTIVE,
        PASSIVE,
        STAY
    }

    private final JavaPlugin plugin;
    private final NamespacedKey minionOwnerKey;
    private final NamespacedKey minionStanceKey;
    private final NamespacedKey petKey;
    private final NamespacedKey petCompanionKey;
    private final Map<UUID, Set<UUID>> minionsByOwner = new ConcurrentHashMap<>();

    public MinionManager(final JavaPlugin plugin) {
        this.plugin = plugin;
        this.minionOwnerKey = new NamespacedKey(plugin, "minion_owner");
        this.minionStanceKey = new NamespacedKey(plugin, "minion_stance");
        this.petKey = new NamespacedKey(plugin, "pet");
        this.petCompanionKey = new NamespacedKey(plugin, "pet_companion_id");
    }

    public void tag(final Mob minion, final UUID owner) {
        if (minion == null || owner == null) {
            return;
        }
        minion.getPersistentDataContainer().set(
                minionOwnerKey, PersistentDataType.STRING, owner.toString());
        minion.getPersistentDataContainer().set(
                minionStanceKey, PersistentDataType.STRING, Stance.ACTIVE.name());
        // Explicit registration is idempotent and also covers platforms whose CUSTOM spawn event was
        // observed before this plugin's lifecycle listener became active during a hot reload.
        TransientEntities.register(plugin, minion);
        minionsByOwner.computeIfAbsent(owner,
                key -> ConcurrentHashMap.newKeySet()).add(minion.getUniqueId());
    }

    /** Marks a durable Profile v2 companion in addition to its generic owner tag. */
    public void tagPet(final Mob pet, final UUID owner, final UUID companionId) {
        if (pet == null || owner == null) {
            return;
        }
        tag(pet, owner);
        pet.getPersistentDataContainer().set(petKey, PersistentDataType.BYTE, (byte) 1);
        if (companionId != null) {
            pet.getPersistentDataContainer().set(
                    petCompanionKey, PersistentDataType.STRING, companionId.toString());
        }
    }

    public boolean isMinion(final Entity entity) {
        return getOwner(entity) != null;
    }

    public static boolean isMinionTagged(final Entity entity) {
        return entity != null && entity.getPersistentDataContainer().has(
                NamespacedKey.fromString("icesmp:minion_owner"),
                PersistentDataType.STRING);
    }

    /** Fast, thread-local identity check for durable pets; does not inspect live ownership. */
    public static boolean isPetTagged(final Entity entity) {
        return entity != null && entity.getPersistentDataContainer().has(
                NamespacedKey.fromString("icesmp:pet"), PersistentDataType.BYTE);
    }

    public static UUID petCompanionId(final Entity entity) {
        if (entity == null) {
            return null;
        }
        final String raw = entity.getPersistentDataContainer().get(
                NamespacedKey.fromString("icesmp:pet_companion_id"), PersistentDataType.STRING);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (final IllegalArgumentException ignored) {
            return null;
        }
    }

    public UUID getOwner(final Entity entity) {
        if (entity == null) {
            return null;
        }
        final String rawOwner = entity.getPersistentDataContainer().get(
                minionOwnerKey, PersistentDataType.STRING);
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
        final String raw = entity.getPersistentDataContainer().get(
                minionStanceKey, PersistentDataType.STRING);
        if (raw == null) {
            return Stance.ACTIVE;
        }
        try {
            return Stance.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (final IllegalArgumentException exception) {
            return Stance.ACTIVE;
        }
    }

    public Stance cycleStance(final Mob minion) {
        final Stance next = switch (getStance(minion)) {
            case ACTIVE -> Stance.PASSIVE;
            case PASSIVE -> Stance.STAY;
            case STAY -> Stance.ACTIVE;
        };
        minion.getPersistentDataContainer().set(
                minionStanceKey, PersistentDataType.STRING, next.name());
        minion.setAI(next != Stance.STAY);
        if (next != Stance.ACTIVE) {
            minion.setTarget(null);
        }
        return next;
    }

    public void removeAllOwned(final UUID owner) {
        final Set<UUID> ids = minionsByOwner.remove(owner);
        if (ids == null) {
            return;
        }
        for (final UUID id : ids) {
            TransientEntities.removeById(plugin, id);
        }
    }

    /** Pure UUID/atomic liveness query; no cross-region entity access. */
    public int countActive(final UUID owner) {
        final Set<UUID> ids = minionsByOwner.get(owner);
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        int alive = 0;
        for (final UUID id : new HashSet<>(ids)) {
            if (TransientEntities.isAlive(id)) {
                alive++;
            } else {
                ids.remove(id);
            }
        }
        if (ids.isEmpty()) {
            minionsByOwner.remove(owner, ids);
        }
        return alive;
    }
}
