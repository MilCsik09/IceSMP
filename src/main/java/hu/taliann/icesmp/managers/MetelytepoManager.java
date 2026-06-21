package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.data.FactionType;
import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.Bukkit;

import org.bukkit.entity.Entity;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.Particle;
import org.bukkit.Sound;
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

public final class MetelytepoManager {

    private static final String RELIC_ID = "metelytepo";
    public static final String ABILITY_JUSTICE = "justice";
    public static final String ABILITY_HONOR_EYE = "honor_eye";
    private static final long JUSTICE_COOLDOWN_MILLIS = 30_000L;
    private static final long HONOR_EYE_COOLDOWN_MILLIS = 240_000L;

    private final JavaPlugin plugin;
    private final MessageManager messageManager;
    private final ConfigManager configManager;
    private final FactionManager factionManager;
    private final NamespacedKey relicIdKey;
    private final NamespacedKey sinnerKey;
    private final NamespacedKey darkPactKey;
    private final NamespacedKey sinCountKey;
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

    public MetelytepoManager(final JavaPlugin plugin, final ConfigManager configManager,
                             final MessageManager messageManager, final FactionManager factionManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.messageManager = messageManager;
        this.factionManager = factionManager;
        this.relicIdKey = new NamespacedKey(plugin, "relic_id");
        this.sinnerKey = new NamespacedKey(plugin, "is_sinner");
        this.darkPactKey = new NamespacedKey(plugin, "dark_pact");
        this.sinCountKey = new NamespacedKey(plugin, "sin_count");
    }

    /**
     * Gets the player's accumulated sin count.
     *
     * @param player the player
     * @return the number of recorded sins
     */
    public int getSinCount(final Player player) {
        return player == null ? 0
                : player.getPersistentDataContainer().getOrDefault(sinCountKey, PersistentDataType.INTEGER, 0);
    }

    /**
     * Records a sin: marks the player as sinner, increments the sin counter,
     * and once the configured threshold is reached the sinner is automatically
     * exiled to the Dark faction (sealing the permanent dark pact).
     *
     * @param player the sinning player
     * @param amount how many sins to add
     * @return the new sin count
     */
    public int addSin(final Player player, final int amount) {
        if (player == null || amount <= 0) {
            return getSinCount(player);
        }

        final int newCount = getSinCount(player) + amount;
        player.getPersistentDataContainer().set(sinCountKey, PersistentDataType.INTEGER, newCount);
        markAsSinner(player);

        final int exileThreshold = Math.max(0, configManager.getInt("factions.sins.exile-threshold", 4));
        if (exileThreshold > 0 && newCount >= exileThreshold
                && factionManager.getFaction(player.getUniqueId()) != FactionType.DARK) {
            exileToDark(player);
        }

        return newCount;
    }

    private void exileToDark(final Player player) {
        factionManager.setFaction(player.getUniqueId(), FactionType.DARK);
        sealDarkPact(player);
        player.sendMessage(messageManager.getMessage(
                "sinner.exiled",
                "<dark_purple>Bűneid súlya alatt összeroskadt a becsületed: száműztek a Sötét frakcióba. A paktum örök.</dark_purple>"
        ));
        Bukkit.getServer().broadcast(messageManager.getMessage(
                "sinner.exile-broadcast",
                "<dark_purple>{player} bűnei elérték a tűréshatárt — a Sötét frakcióba száműzték!</dark_purple>",
                Map.of("player", player.getName())
        ));
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.6F, 0.7F);
        player.getWorld().spawnParticle(Particle.SQUID_INK, player.getLocation().add(0.0D, 1.0D, 0.0D), 40, 0.4D, 0.6D, 0.4D, 0.03D);
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

    public boolean isSinner(final Entity entity) {
        if (entity == null) {
            return false;
        }

        if (entity instanceof Player player) {
            return player.getPersistentDataContainer().getOrDefault(sinnerKey, PersistentDataType.BYTE, (byte) 0) == (byte) 1;
        }

        if (isProtectedEntityType(entity.getType())) {
            return false;
        }

        return entity instanceof Monster;
    }

    public void markAsSinner(final Player player) {
        player.getPersistentDataContainer().set(sinnerKey, PersistentDataType.BYTE, (byte) 1);
    }

    /**
     * Seals the dark pact on a player who joined the Dark faction:
     * the sinner mark becomes permanent and can never be cleansed again.
     *
     * @param player the player joining the Dark faction
     */
    public void sealDarkPact(final Player player) {
        if (player == null) {
            return;
        }

        player.getPersistentDataContainer().set(darkPactKey, PersistentDataType.BYTE, (byte) 1);
        markAsSinner(player);
    }

    public boolean hasDarkPact(final Player player) {
        return player != null
                && player.getPersistentDataContainer().getOrDefault(darkPactKey, PersistentDataType.BYTE, (byte) 0) == (byte) 1;
    }

    /**
     * Breaks the dark pact (the only path: the completed penance quest chain).
     * Removes the pact, the sinner mark and the sin counter with a redemption effect.
     *
     * @param player the redeemed player
     */
    public void breakDarkPact(final Player player) {
        if (player == null) {
            return;
        }

        player.getPersistentDataContainer().remove(darkPactKey);
        player.getPersistentDataContainer().remove(sinCountKey);
        player.getPersistentDataContainer().remove(sinnerKey);
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 1.0F, 1.4F);
        player.getWorld().spawnParticle(Particle.END_ROD, player.getLocation().add(0.0D, 1.0D, 0.0D), 60, 0.5D, 0.8D, 0.5D, 0.05D);
        player.sendMessage(messageManager.getMessage(
                "sinner.pact-broken",
                "<gold>A vezeklésed teljes: a sötét paktum megtört, bűneid feloldozást nyertek.</gold>"
        ));
    }

    /**
     * Clears the sinner mark unless the player is bound by the dark pact.
     *
     * @param player the player to cleanse
     * @return true if the mark was removed (or was absent), false if the dark pact blocks it
     */
    public boolean clearSinner(final Player player) {
        if (player == null) {
            return true;
        }

        if (hasDarkPact(player)) {
            return false;
        }

        // Cleansing also wipes the sin counter.
        player.getPersistentDataContainer().remove(sinCountKey);

        if (!player.getPersistentDataContainer().has(sinnerKey, PersistentDataType.BYTE)) {
            return true;
        }

        player.getPersistentDataContainer().remove(sinnerKey);
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0F, 1.6F);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ILLUSIONER_CAST_SPELL, 0.7F, 1.8F);
        player.getWorld().spawnParticle(Particle.END_ROD, player.getLocation().add(0.0D, 1.0D, 0.0D), 24, 0.35D, 0.5D, 0.35D, 0.02D);
        player.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, player.getLocation().add(0.0D, 1.0D, 0.0D), 16, 0.25D, 0.4D, 0.25D, 0.01D);
        player.sendMessage(messageManager.getMessage("sinner.cleansed", "<green><i>Megtisztultal a buneidtol...</i></green>"));
        return true;
    }

    public boolean isSinnerTarget(final LivingEntity target) {
        return isSinner(target);
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


