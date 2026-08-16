package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.managers.ConfigManager;
import hu.taliann.icesmp.managers.JobManager;
import hu.taliann.icesmp.managers.ResourceManager;
import hu.taliann.icesmp.hud.TargetHudState;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Floating damage-number indicators (A8): purely cosmetic — a short-lived
 * {@link TextDisplay} pops up over an entity whenever a PLAYER (directly or via a
 * fired projectile) damages it, then despawns itself a second later. Registered at
 * {@link EventPriority#MONITOR} with {@code ignoreCancelled = true} so it never
 * observes damage that was cancelled/zeroed by an earlier handler and can never
 * itself affect combat resolution.
 *
 * <p>Folia note: {@link EntityDamageByEntityEvent} runs on the DAMAGED entity's own
 * region thread, so spawning the display at the damaged entity's location is
 * region-local and safe without any scheduler hop. The display's own despawn timer
 * then runs on ITS OWN entity scheduler.
 */
public final class DamageIndicatorListener implements Listener {

    /** Ticks the number stays visible before it self-removes. */
    private static final long LIFETIME_TICKS = 20L;
    /** Rate-limit window per damaged entity (~5 ticks) so spam hits don't flood the world with displays. */
    private static final long RATE_LIMIT_MILLIS = 250L;
    /** Safety valve: if the rate-limit map grows unbounded (many distinct mobs), wipe it rather than leak forever. */
    private static final int MAX_TRACKED_ENTITIES = 2000;
    private static final long DEFAULT_LAST_TARGET_TTL_MILLIS = 10_000L;

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final AbilityCatalystListener catalystListener;
    private final ResourceManager resourceManager;
    private final JobManager jobManager;
    private final ConcurrentHashMap<UUID, Long> lastShownAt = new ConcurrentHashMap<>();
    // Attacker UUID -> az utolsó célpontja (a HUD célpont-sorához).
    private final Map<UUID, LastTarget> lastTargets = new ConcurrentHashMap<>();
    /** A target frame teljes, owner-threaden rögzített célpont-pillanatképe. */
    public record LastTarget(UUID targetId, String targetName, TargetHudState.Kind kind,
                             TargetHudState.Rank rank, int level,
                             String mobStatus,
                             double health, double maximumHealth,
                             String className, String resourceName,
                             int resource, int resourceMaximum, long atMillis) {
        public boolean player() {
            return kind == TargetHudState.Kind.PLAYER;
        }
    }

    public DamageIndicatorListener(final JavaPlugin plugin, final ConfigManager configManager,
                                    final AbilityCatalystListener catalystListener) {
        this(plugin, configManager, catalystListener, null, null);
    }

    public DamageIndicatorListener(final JavaPlugin plugin, final ConfigManager configManager,
                                    final AbilityCatalystListener catalystListener,
                                    final ResourceManager resourceManager, final JobManager jobManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.catalystListener = catalystListener;
        this.resourceManager = resourceManager;
        this.jobManager = jobManager;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(final EntityDamageByEntityEvent event) {
        final double damage = event.getFinalDamage();
        if (damage <= 0.0D) {
            return;
        }

        final Player attacker = resolvePlayer(event.getDamager());
        if (attacker == null) {
            return;
        }

        final Entity victim = event.getEntity();
        // Az onDamage a MEGÜTÖTT entitás (victim) régió-szálán fut, tehát a neve/típusa
        // itt biztonságosan olvasható; a snapshotot csak later a HUD olvassa (lastTarget()).
        recordLastTarget(attacker.getUniqueId(), victim, damage);
        if (configManager.getBoolean("spells.damage-indicators.enabled", true)
                && !isRateLimited(victim.getUniqueId())) {
            spawnIndicator(attacker, victim, damage);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(final EntityDeathEvent event) {
        final UUID dead = event.getEntity().getUniqueId();
        lastTargets.remove(dead);
        lastTargets.entrySet().removeIf(entry -> entry.getValue().targetId().equals(dead));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(final PlayerQuitEvent event) {
        final UUID playerId = event.getPlayer().getUniqueId();
        lastTargets.remove(playerId);
        lastTargets.entrySet().removeIf(entry -> entry.getValue().targetId().equals(playerId));
    }

    /** Eltárolja az attacker utolsó megütött célpontját (HUD célpont-sor). */
    private void recordLastTarget(final UUID attackerId, final Entity victim,
                                  final double finalDamage) {
        final String name = targetName(victim);
        if (lastTargets.size() > MAX_TRACKED_ENTITIES) {
            // Ugyanaz a leak-védelem, mint a lastShownAt-nál: nincs természetes cleanup-hook mobokra.
            lastTargets.clear();
        }
        final LivingEntity living = victim instanceof LivingEntity value ? value : null;
        final AttributeInstance maximumAttribute = living == null
                ? null : living.getAttribute(Attribute.MAX_HEALTH);
        final double maximumHealth = Math.max(1.0D,
                maximumAttribute == null ? 20.0D : maximumAttribute.getValue());
        final double health = living == null ? maximumHealth
                : Math.max(0.0D, Math.min(maximumHealth, living.getHealth() - finalDamage));
        final Integer storedLevel = mobLevel(victim);
        final int level = storedLevel == null ? 0 : Math.max(0, storedLevel);
        final TargetHudState.Rank rank = mobRank(victim, level, maximumHealth);
        final String mobStatus = mobStatus(victim);
        final TargetHudState.Kind kind = targetKind(victim);
        String className = "";
        String resourceName = "";
        int resource = 0;
        int resourceMaximum = 0;
        if (victim instanceof Player targetPlayer && resourceManager != null && jobManager != null) {
            if (jobManager.hasPrimaryJob(targetPlayer)) {
                final var job = jobManager.getPrimaryJob(targetPlayer);
                className = job == null ? "" : PlainTextComponentSerializer.plainText()
                        .serialize(job.getDisplayName());
            }
            if (resourceManager.isEnabled()) {
                resourceName = resourceManager.resourceName(targetPlayer);
                resource = resourceManager.resourceValue(targetPlayer);
                resourceMaximum = resourceManager.resourceMax(targetPlayer);
            }
        }
        lastTargets.put(attackerId, new LastTarget(victim.getUniqueId(), name, kind, rank,
                level, mobStatus, health, maximumHealth, className, resourceName,
                resource, resourceMaximum, System.currentTimeMillis()));
    }

    /** Mob-only presentation metadata; player state is never read from PDC. */
    private Integer mobLevel(final Entity entity) {
        if (entity instanceof Player) {
            return null;
        }
        return entity.getPersistentDataContainer().get(
                new NamespacedKey(plugin, "mob_level"), PersistentDataType.INTEGER);
    }

    /** Mob-only rank markers; player rank/class data comes from the live HUD snapshot. */
    private boolean isWorldBoss(final Entity entity) {
        if (entity instanceof Player) {
            return false;
        }
        return entity.getPersistentDataContainer().has(
                new NamespacedKey(plugin, "world_boss"), PersistentDataType.BYTE)
                || entity.getPersistentDataContainer().has(
                new NamespacedKey(plugin, "dungeon_boss"), PersistentDataType.STRING);
    }

    private TargetHudState.Rank mobRank(final Entity entity, final int level,
                                        final double maximumHealth) {
        if (entity instanceof Player) return TargetHudState.Rank.NORMAL;
        final String stored = entity.getPersistentDataContainer().get(
                new NamespacedKey(plugin, "mob_rank"), PersistentDataType.STRING);
        if (stored != null) {
            try {
                return TargetHudState.Rank.valueOf(stored.trim().toUpperCase(java.util.Locale.ROOT));
            } catch (final IllegalArgumentException ignored) {
                // Legacy/future unknown rank falls through to conservative presentation.
            }
        }
        if (isWorldBoss(entity)) return TargetHudState.Rank.BOSS;
        return level >= 20 || maximumHealth >= 80.0D
                ? TargetHudState.Rank.ELITE : TargetHudState.Rank.NORMAL;
    }

    private String mobStatus(final Entity entity) {
        if (entity instanceof Player) return "";
        final String affixes = entity.getPersistentDataContainer().get(
                new NamespacedKey(plugin, "mob_affixes"), PersistentDataType.STRING);
        if (affixes != null && !affixes.isBlank()) {
            return java.util.Arrays.stream(affixes.split(",")).limit(2)
                    .map(value -> value.toLowerCase(java.util.Locale.ROOT).replace('_', ' '))
                    .collect(java.util.stream.Collectors.joining(" • "));
        }
        final String archetype = entity.getPersistentDataContainer().get(
                new NamespacedKey(plugin, "mob_archetype"), PersistentDataType.STRING);
        return archetype == null ? "" : archetype.toLowerCase(java.util.Locale.ROOT)
                .replace('_', ' ');
    }

    private static String targetName(final Entity victim) {
        if (victim instanceof Player player) return player.getName();
        final Component custom = victim.customName();
        if (custom != null) {
            final String plain = PlainTextComponentSerializer.plainText().serialize(custom).trim();
            final String withoutLevel = plain.replaceFirst(
                    "(?i)^\\[?(?:lvl|lv\\.?|szint)\\s*\\d+\\]?\\s*", "").trim();
            if (!withoutLevel.isBlank()) return withoutLevel;
        }
        return formatEntityType(victim.getType());
    }

    private static TargetHudState.Kind targetKind(final Entity victim) {
        if (victim instanceof Player) return TargetHudState.Kind.PLAYER;
        if (victim instanceof Monster) return TargetHudState.Kind.HOSTILE;
        if (victim instanceof Animals) return TargetHudState.Kind.PASSIVE;
        return TargetHudState.Kind.NEUTRAL;
    }

    /**
     * Az attacker legutóbb megütött célpontja, vagy null, ha nincs ilyen / a
     * bejegyzés a konfigurált célpont-időkorlátnál régebbi (a lejárt bejegyzést lustán törli).
     */
    public LastTarget lastTarget(final UUID attackerId) {
        if (attackerId == null) {
            return null;
        }
        final LastTarget target = lastTargets.get(attackerId);
        if (target == null) {
            return null;
        }
        final long configuredSeconds = Math.max(1L, Math.min(30L, configManager.getLong(
                "hud.icesmp-hud.target-frame.expire-seconds",
                DEFAULT_LAST_TARGET_TTL_MILLIS / 1000L)));
        if (System.currentTimeMillis() - target.atMillis() > configuredSeconds * 1000L) {
            lastTargets.remove(attackerId);
            return null;
        }
        return target;
    }

    /**
     * Egyszerűsített, magyaros formázás a mob-típus nevéből (pl. {@code ZOMBIE_VILLAGER} ->
     * {@code "Zombie villager"}); a {@link DeathRecapListener#translateEntityType} teljes
     * fordítótáblájától szándékosan független, hogy ne kelljen tőle függeni.
     */
    private static String formatEntityType(final EntityType type) {
        final String raw = type.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        return raw.isEmpty() ? raw : Character.toUpperCase(raw.charAt(0)) + raw.substring(1);
    }

    /** The player behind a hit (direct melee or a fired projectile), else null. */
    private static Player resolvePlayer(final Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof Projectile projectile) {
            final ProjectileSource shooter = projectile.getShooter();
            if (shooter instanceof Player player) {
                return player;
            }
        }
        return null;
    }

    /** True if this entity already showed an indicator within the rate-limit window. */
    private boolean isRateLimited(final UUID victimId) {
        final long now = System.currentTimeMillis();
        final Long last = lastShownAt.get(victimId);
        if (last != null && now - last < RATE_LIMIT_MILLIS) {
            return true;
        }
        if (lastShownAt.size() > MAX_TRACKED_ENTITIES) {
            // Unbounded growth guard: a UUID-keyed map of transient mobs never has a natural
            // cleanup hook (no session-end event like players), so just drop everything and
            // let it refill — worst case is a few indicators bypass the rate-limit once.
            lastShownAt.clear();
        }
        lastShownAt.put(victimId, now);
        return false;
    }

    /**
     * Spawns the floating number over the victim and schedules its own despawn.
     *
     * <p>Láthatóság ({@code spells.damage-indicators.visibility}): {@code attacker-only}
     * (default) esetén a display {@code visibleByDefault=false}-szal spawnol, és csak a sebző
     * kapja meg {@code showEntity}-vel — a Folia-szabály miatt a sebző SAJÁT régió-szálán
     * (lövedékes találatnál a lövő másik régióban lehet, ilyenkor scheduler-hoppal).
     * {@code everyone} esetén mindenki látja, aki a közelben van.
     */
    private void spawnIndicator(final Player attacker, final Entity victim, final double damage) {
        if (victim.getWorld() == null) {
            return;
        }
        final boolean attackerOnly = !"everyone".equalsIgnoreCase(
                configManager.getString("spells.damage-indicators.visibility", "attacker-only"));

        final double bob = 1.8D + ThreadLocalRandom.current().nextDouble(0.4D);
        final Location at = victim.getLocation().clone().add(
                ThreadLocalRandom.current().nextDouble(-0.3D, 0.3D), bob,
                ThreadLocalRandom.current().nextDouble(-0.3D, 0.3D));

        // Kombó/lánc-befejező castok után 3 mp-ig kiemelt (nagyobb, arany) sebzés-szám.
        final boolean comboBoosted = catalystListener != null && catalystListener.hasComboBoost(attacker.getUniqueId());
        final String text = String.format(Locale.ROOT, "%.1f", damage) + (comboBoosted ? "!" : "");
        final NamedTextColor color = comboBoosted
                ? NamedTextColor.GOLD
                : victim instanceof Player ? NamedTextColor.RED : NamedTextColor.YELLOW;
        final float scale = comboBoosted ? 1.5F : 1.0F;

        final TextDisplay display = at.getWorld().spawn(at, TextDisplay.class, spawned -> {
            spawned.setBillboard(Display.Billboard.CENTER);
            spawned.setPersistent(false);
            spawned.setDefaultBackground(false);
            spawned.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            spawned.setShadowed(true);
            spawned.setTransformation(new Transformation(
                    new Vector3f(), new AxisAngle4f(), new Vector3f(scale, scale, scale), new AxisAngle4f()));
            spawned.text(Component.text(text, color));
            if (attackerOnly) {
                // A spawn-consumer még a világba kerülés ELŐTT fut, így a display soha,
                // egyetlen tickre sem látszik azoknak, akiknek nem szánjuk.
                spawned.setVisibleByDefault(false);
            }
        });

        if (attackerOnly) {
            if (org.bukkit.Bukkit.isOwnedByCurrentRegion(attacker)) {
                attacker.showEntity(plugin, display);
            } else {
                // Lövedékes találat: a lövő másik régió-szálon lehet — showEntity csak a
                // saját szálán hívható. Mire a hop lefut, a display el is tűnhetett (1 mp
                // élettartam), ezért isValid-kapu.
                attacker.getScheduler().run(plugin, task -> {
                    if (display.isValid()) {
                        attacker.showEntity(plugin, display);
                    }
                }, null);
            }
        }

        // Despawn on the display's OWN entity scheduler (Folia-correct); the retired callback
        // covers the case where the display's region is unloaded/it is otherwise removed before
        // the delayed task can run, so it never leaks either way.
        display.getScheduler().runDelayed(plugin, task -> removeIfValid(display), () -> removeIfValid(display), LIFETIME_TICKS);
    }

    private static void removeIfValid(final TextDisplay display) {
        if (display.isValid()) {
            display.remove();
        }
    }
}
