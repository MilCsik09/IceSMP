package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.session.PlayerStateCleanup;

import org.bukkit.entity.Entity;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The Mételytépő relic's combat mechanic: identifying the weapon, its Justice
 * and Honor Eye ability cooldowns, undead freezing and the ability-damage
 * bypass flag. The sin / dark-pact / bounty domain that this class used to also
 * own now lives in {@link SinManager}; the relic reads it through
 * {@link #isRelicTarget(Entity)} to decide whom it may smite (a marked sinner
 * player, or a non-protected monster).
 */
public final class MetelytepoManager implements PlayerStateCleanup {

    private static final String RELIC_ID = "metelytepo";
    public static final String ABILITY_JUSTICE = "justice";
    public static final String ABILITY_HONOR_EYE = "honor_eye";
    private static final long JUSTICE_COOLDOWN_MILLIS = 30_000L;
    private static final long HONOR_EYE_COOLDOWN_MILLIS = 240_000L;

    private final JavaPlugin plugin;
    private final SinManager sinManager;
    private final NamespacedKey relicIdKey;
    private final Map<UUID, Map<String, Long>> cooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, Double> frozenSpeed = new ConcurrentHashMap<>();
    private final Map<UUID, Long> abilityDamageBypass = new ConcurrentHashMap<>();
    private final Set<EntityType> protectedEntityTypes = EnumSet.of(
            EntityType.PLAYER,
            EntityType.VILLAGER,
            EntityType.IRON_GOLEM,
            EntityType.PIGLIN
    );
    private final Set<EntityType> undeadTypes = EnumSet.of(
            EntityType.ZOMBIE,
            EntityType.ZOMBIE_VILLAGER,
            EntityType.HUSK,
            EntityType.DROWNED,
            EntityType.SKELETON,
            EntityType.STRAY,
            EntityType.WITHER_SKELETON,
            EntityType.BOGGED,
            EntityType.PHANTOM,
            EntityType.WITHER,
            EntityType.ZOGLIN,
            EntityType.ZOMBIFIED_PIGLIN
    );

    public MetelytepoManager(final JavaPlugin plugin, final SinManager sinManager) {
        this.plugin = plugin;
        this.sinManager = sinManager;
        this.relicIdKey = new NamespacedKey(plugin, "relic_id");
    }

    public String relicId() {
        return RELIC_ID;
    }

    public boolean isMetelytepo(final ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir() || !itemStack.hasItemMeta()) {
            return false;
        }

        final String relicId = itemStack.getItemMeta().getPersistentDataContainer().get(relicIdKey, PersistentDataType.STRING);
        return RELIC_ID.equalsIgnoreCase(relicId);
    }

    /**
     * Checks if a player is on the Justice ability cooldown.
     *
     * @param player the player to check
     * @return true if on cooldown, false otherwise
     */
    public boolean isOnJusticeCooldown(final Player player) {
        return isOnCooldown(player, ABILITY_JUSTICE);
    }

    /**
     * Gets the remaining cooldown time for Justice ability in milliseconds.
     *
     * @param player the player
     * @return remaining milliseconds, or 0 if not on cooldown
     */
    public long getJusticeRemainingMillis(final Player player) {
        return getRemainingMillis(player, ABILITY_JUSTICE);
    }

    /**
     * Triggers the Justice ability cooldown for a player.
     *
     * @param player the player
     */
    public void triggerJusticeCooldown(final Player player) {
        startCooldown(player, ABILITY_JUSTICE, JUSTICE_COOLDOWN_MILLIS);
    }

    /**
     * Atomically starts the Justice cooldown only if it is currently free, returning
     * whether it was acquired. Used as the single fire-gate so two near-simultaneous
     * events (e.g. interact + interact-entity) can't both fire within one cooldown.
     */
    public boolean tryConsumeJusticeCooldown(final Player player) {
        return tryStartCooldown(player, ABILITY_JUSTICE, JUSTICE_COOLDOWN_MILLIS);
    }

    /**
     * Checks if a player is on the Honor Eye ability cooldown.
     *
     * @param player the player to check
     * @return true if on cooldown, false otherwise
     */
    public boolean isOnHonorEyeCooldown(final Player player) {
        return isOnCooldown(player, ABILITY_HONOR_EYE);
    }

    /**
     * Gets the remaining cooldown time for Honor Eye ability in milliseconds.
     *
     * @param player the player
     * @return remaining milliseconds, or 0 if not on cooldown
     */
    public long getHonorEyeRemainingMillis(final Player player) {
        return getRemainingMillis(player, ABILITY_HONOR_EYE);
    }

    /**
     * Triggers the Honor Eye ability cooldown for a player.
     *
     * @param player the player
     */
    public void triggerHonorEyeCooldown(final Player player) {
        startCooldown(player, ABILITY_HONOR_EYE, HONOR_EYE_COOLDOWN_MILLIS);
    }

    /** Atomic fire-gate for Honor Eye — see {@link #tryConsumeJusticeCooldown(Player)}. */
    public boolean tryConsumeHonorEyeCooldown(final Player player) {
        return tryStartCooldown(player, ABILITY_HONOR_EYE, HONOR_EYE_COOLDOWN_MILLIS);
    }

    public boolean isProtectedEntityType(final EntityType type) {
        return protectedEntityTypes.contains(type);
    }

    /**
     * Whether the Mételytépő may smite this entity: a marked sinner player (via
     * the {@link SinManager}), or any non-protected monster.
     *
     * @param entity the potential target
     * @return true if the relic treats it as a valid target
     */
    public boolean isRelicTarget(final Entity entity) {
        if (entity == null) {
            return false;
        }

        if (entity instanceof Player player) {
            return sinManager.isSinner(player);
        }

        if (isProtectedEntityType(entity.getType())) {
            return false;
        }

        return entity instanceof Monster;
    }

    public boolean isUndead(final LivingEntity target) {
        return undeadTypes.contains(target.getType());
    }

    public void freezeUndead(final LivingEntity target, final long ticks) {
        final AttributeInstance speedAttribute = target.getAttribute(Attribute.MOVEMENT_SPEED);
        if (speedAttribute != null) {
            frozenSpeed.put(target.getUniqueId(), speedAttribute.getBaseValue());
            speedAttribute.setBaseValue(0.0D);
        }
        target.setAI(false);

        // Folia: unfreeze on the target's own entity scheduler instead of a BukkitRunnable.
        // The map entry is removed in EVERY path (run, invalid, and the retired-callback that
        // fires when the mob dies/unloads before the delay) so frozen-mob UUIDs never leak.
        final UUID targetId = target.getUniqueId();
        target.getScheduler().runDelayed(plugin, task -> {
            final Double original = frozenSpeed.remove(targetId);
            if (!target.isValid()) {
                return;
            }

            target.setAI(true);
            if (original != null) {
                final AttributeInstance speed = target.getAttribute(Attribute.MOVEMENT_SPEED);
                if (speed != null) {
                    speed.setBaseValue(original);
                }
            }
        }, () -> frozenSpeed.remove(targetId), Math.max(1L, ticks));
    }

    public void markAbilityDamageBypass(final LivingEntity target, final long millis) {
        abilityDamageBypass.put(target.getUniqueId(), System.currentTimeMillis() + millis);
    }

    public boolean consumeAbilityDamageBypass(final LivingEntity target) {
        final Long expiresAt = abilityDamageBypass.get(target.getUniqueId());
        if (expiresAt == null) {
            return false;
        }

        if (expiresAt < System.currentTimeMillis()) {
            abilityDamageBypass.remove(target.getUniqueId());
            return false;
        }

        abilityDamageBypass.remove(target.getUniqueId());
        return true;
    }

    private boolean isOnCooldown(final Player player, final String ability) {
        return getRemainingMillis(player, ability) > 0L;
    }

    private long getRemainingMillis(final Player player, final String ability) {
        final Map<String, Long> playerCooldowns = cooldowns.get(player.getUniqueId());
        if (playerCooldowns == null) {
            return 0L;
        }

        final Long expiresAt = playerCooldowns.get(ability);
        return remainingMillis(expiresAt);
    }

    private void startCooldown(final Player player, final String ability, final long cooldownMillis) {
        cooldowns.computeIfAbsent(player.getUniqueId(), key -> new ConcurrentHashMap<>())
                .put(ability, System.currentTimeMillis() + cooldownMillis);
    }

    /**
     * Atomically acquires a cooldown if it is free. The whole check-and-set runs inside
     * {@link ConcurrentHashMap#compute}, so concurrent callers are serialized and only one
     * can acquire it within a window.
     *
     * @return true if the cooldown was free and is now started
     */
    private boolean tryStartCooldown(final Player player, final String ability, final long cooldownMillis) {
        final long now = System.currentTimeMillis();
        final Map<String, Long> playerCooldowns =
                cooldowns.computeIfAbsent(player.getUniqueId(), key -> new ConcurrentHashMap<>());
        final boolean[] acquired = {false};
        playerCooldowns.compute(ability, (key, existing) -> {
            if (existing != null && existing > now) {
                return existing; // still on cooldown — leave it
            }
            acquired[0] = true;
            return now + cooldownMillis;
        });
        return acquired[0];
    }

    private long remainingMillis(final Long expiresAt) {
        if (expiresAt == null) {
            return 0L;
        }

        return Math.max(0L, expiresAt - System.currentTimeMillis());
    }

    public void cleanup(final UUID playerId) {
        if (playerId == null) {
            return;
        }

        cooldowns.remove(playerId);
        frozenSpeed.remove(playerId);
        abilityDamageBypass.remove(playerId);
    }

    public void clearPlayerState(final UUID playerId) {
        cleanup(playerId);
    }
}
