package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.managers.ConfigManager;
import hu.taliann.icesmp.managers.JobManager;
import hu.taliann.icesmp.managers.ResourceManager;
import hu.taliann.icesmp.utils.DisplayFxUtil;
import hu.taliann.icesmp.utils.TransientEntities;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
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
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

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
    /** A combat-vitals displayek külön, jóval alacsonyabb világ-szintű biztonsági plafonja. */
    private static final int MAX_VITAL_DISPLAYS = 512;

    /** Az utolsó célpont bejegyzés max ennyi ideig érvényes (a HUD ennyi ideig mutatja). */
    private static final long LAST_TARGET_TTL_MILLIS = 10_000L;

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final AbilityCatalystListener catalystListener;
    private final ResourceManager resourceManager;
    private final JobManager jobManager;
    private final ConcurrentHashMap<UUID, Long> lastShownAt = new ConcurrentHashMap<>();
    // Attacker UUID -> az utolsó célpontja (a HUD célpont-sorához).
    private final Map<UUID, LastTarget> lastTargets = new ConcurrentHashMap<>();
    // Target UUID -> rövid életű, külön entitásként a célpont feje alatt követett combat-vitals kijelzés.
    private final Map<UUID, VitalDisplay> vitalDisplays = new ConcurrentHashMap<>();
    private final AtomicLong vitalGenerations = new AtomicLong();

    /** Egy játékos legutóbb megütött célpontjának pillanatképe. */
    public record LastTarget(UUID targetId, String targetName, boolean player, long atMillis) {
    }

    private record VitalDisplay(TextDisplay display, ScheduledTask followTask, long generation) {
    }

    private record VitalsAppearance(Component text, float scale, float viewRange) {
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
        recordLastTarget(attacker.getUniqueId(), victim);
        if (configManager.getBoolean("spells.damage-indicators.enabled", true)
                && !isRateLimited(victim.getUniqueId())) {
            spawnIndicator(attacker, victim, damage);
        }
        if (victim instanceof LivingEntity living
                && configManager.getBoolean("hud.profile.enabled", true)) {
            // MONITOR alatt a Bukkit-életerő még a találat ELŐTTI érték lehet. A sérült entitás
            // saját következő tickjén olvassuk ki, így a kijelzés a tényleges, levont HP-t mutatja.
            living.getScheduler().run(plugin,
                    task -> showTargetVitals(attacker, living), null);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(final EntityDeathEvent event) {
        removeVitalDisplay(event.getEntity().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(final PlayerQuitEvent event) {
        final UUID playerId = event.getPlayer().getUniqueId();
        lastTargets.remove(playerId);
        lastTargets.entrySet().removeIf(entry -> entry.getValue().targetId().equals(playerId));
        removeVitalDisplay(playerId);
    }

    /** Eltárolja az attacker utolsó megütött célpontját (HUD célpont-sor). */
    private void recordLastTarget(final UUID attackerId, final Entity victim) {
        final String name = victim instanceof Player victimPlayer ? victimPlayer.getName() : formatEntityType(victim.getType());
        if (lastTargets.size() > MAX_TRACKED_ENTITIES) {
            // Ugyanaz a leak-védelem, mint a lastShownAt-nál: nincs természetes cleanup-hook mobokra.
            lastTargets.clear();
        }
        lastTargets.put(attackerId, new LastTarget(victim.getUniqueId(), name, victim instanceof Player, System.currentTimeMillis()));
    }

    /**
     * Az attacker legutóbb megütött célpontja, vagy null, ha nincs ilyen / a
     * bejegyzés {@link #LAST_TARGET_TTL_MILLIS}-nél régebbi (a lejárt bejegyzést lustán törli).
     */
    public LastTarget lastTarget(final UUID attackerId) {
        if (attackerId == null) {
            return null;
        }
        final LastTarget target = lastTargets.get(attackerId);
        if (target == null) {
            return null;
        }
        if (System.currentTimeMillis() - target.atMillis() > LAST_TARGET_TTL_MILLIS) {
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

    /**
     * Találat után egyetlen, a célpont magasságához igazított HP/resource sort tart életben.
     * A rövid target-owned követőfeladat csak ezt az egy displayt mozgatja; világ- vagy
     * közelség-szkennelés nincs. Alapból private az attacker számára; az {@code everyone} opció
     * tudatos szerverdizájn-döntésként nyilvánossá teheti.
     */
    private void showTargetVitals(final Player attacker, final LivingEntity target) {
        if (!target.isValid() || target.isDead()
                || !configManager.getBoolean("hud.profile.enabled", true)) {
            return;
        }
        final VitalsAppearance appearance = captureVitalsAppearance(target);
        final boolean attackerOnly = !"everyone".equalsIgnoreCase(
                configManager.getString("hud.profile.visibility", "attacker-only"));
        final UUID targetId = target.getUniqueId();
        final VitalDisplay current = vitalDisplays.get(targetId);
        TextDisplay display = current == null ? null : current.display();
        boolean created = false;
        if (display == null) {
            if (current != null) {
                vitalDisplays.remove(targetId, current);
            }
            if (vitalDisplays.size() >= MAX_VITAL_DISPLAYS || target.getWorld() == null) {
                return;
            }
            final Location at = vitalsLocation(target);
            display = at.getWorld().spawn(at, TextDisplay.class, spawned -> {
                spawned.setBillboard(Display.Billboard.CENTER);
                spawned.setTeleportDuration(2);
                spawned.setPersistent(false);
                spawned.addScoreboardTag(DisplayFxUtil.FX_TAG);
                spawned.setDefaultBackground(false);
                spawned.setBackgroundColor(Color.fromARGB(150, 4, 8, 13));
                spawned.setShadowed(true);
                spawned.setSeeThrough(false);
                spawned.setAlignment(TextDisplay.TextAlignment.CENTER);
                spawned.setLineWidth(240);
                spawned.setVisibleByDefault(!attackerOnly);
                applyVitalsAppearance(spawned, appearance);
            });
            TransientEntities.register(plugin, display);
            created = true;
        }

        if (!created) {
            final TextDisplay updatedDisplay = display;
            display.getScheduler().run(plugin, task -> {
                if (updatedDisplay.isValid()) {
                    updatedDisplay.setVisibleByDefault(!attackerOnly);
                    applyVitalsAppearance(updatedDisplay, appearance);
                }
            }, null);
        }
        final long generation = vitalGenerations.incrementAndGet();
        final ScheduledTask followTask;
        if (current == null || current.display() != display || current.followTask() == null) {
            final TextDisplay followedDisplay = display;
            followTask = target.getScheduler().runAtFixedRate(plugin,
                    task -> followTargetVitals(targetId, target, followedDisplay, task),
                    () -> retireTargetVitals(targetId, followedDisplay), 1L, 2L);
        } else {
            followTask = current.followTask();
        }
        if (followTask == null) {
            removeDisplaySafely(display);
            return;
        }
        final VitalDisplay refreshed = new VitalDisplay(display, followTask, generation);
        vitalDisplays.put(targetId, refreshed);

        if (attackerOnly) {
            revealToAttacker(attacker, display);
        }

        final long lifetime = Math.max(20L, Math.min(6000L,
                configManager.getInt("hud.profile.lifetime-ticks", 100)));
        final TextDisplay scheduledDisplay = display;
        display.getScheduler().runDelayed(plugin,
                task -> expireVitalDisplay(targetId, generation, scheduledDisplay),
                () -> vitalDisplays.remove(targetId, refreshed), lifetime);
    }

    private void followTargetVitals(final UUID targetId, final LivingEntity target,
                                    final TextDisplay display, final ScheduledTask task) {
        final VitalDisplay current = vitalDisplays.get(targetId);
        if (current == null || current.display() != display || !target.isValid() || target.isDead()) {
            task.cancel();
            if (current != null && current.display() == display
                    && vitalDisplays.remove(targetId, current)) {
                removeDisplaySafely(display);
            }
            return;
        }
        final Location next = vitalsLocation(target);
        display.getScheduler().run(plugin, scheduled -> {
            if (display.isValid()) {
                display.teleportAsync(next);
            }
        }, null);
    }

    private void retireTargetVitals(final UUID targetId, final TextDisplay display) {
        final VitalDisplay current = vitalDisplays.get(targetId);
        if (current != null && current.display() == display) {
            vitalDisplays.remove(targetId, current);
        }
        // EntityScheduler retired callbacks run in critical code: only enqueue cleanup on the
        // display's own scheduler here; never remove another entity directly from the callback.
        display.getScheduler().run(plugin, task -> removeIfValid(display), null);
    }

    private Location vitalsLocation(final LivingEntity target) {
        final double offset = Math.max(-2.0D, Math.min(3.0D,
                configManager.getDouble("hud.profile.height-offset", 0.20D)));
        return target.getLocation().clone().add(
                0.0D, Math.max(0.1D, target.getHeight()) + offset, 0.0D);
    }

    private VitalsAppearance captureVitalsAppearance(final LivingEntity target) {
        final AttributeInstance maxAttribute = target.getAttribute(Attribute.MAX_HEALTH);
        final double maximum = Math.max(1.0D, maxAttribute == null ? 20.0D : maxAttribute.getValue());
        final double health = Math.max(0.0D, Math.min(maximum, target.getHealth()));
        final double percent = health / maximum;
        final NamedTextColor healthColor = percent <= 0.25D
                ? NamedTextColor.RED : percent <= 0.55D ? NamedTextColor.YELLOW : NamedTextColor.GREEN;
        Component text = Component.text("HP ", NamedTextColor.GRAY)
                .append(Component.text(compact(health) + "/" + compact(maximum), healthColor));

        if (target instanceof Player player && resourceManager != null && jobManager != null
                && configManager.getBoolean("hud.profile.show-player-resource", true)
                && resourceManager.isEnabled() && jobManager.hasPrimaryJob(player)) {
            text = text.append(Component.text("  •  ", NamedTextColor.DARK_GRAY))
                    .append(Component.text(resourceManager.resourceName(player) + " ", NamedTextColor.GRAY))
                    .append(Component.text(resourceManager.resourceValue(player) + "/"
                            + resourceManager.resourceMax(player), NamedTextColor.AQUA));
        }
        final float scale = (float) Math.max(0.35D, Math.min(2.0D,
                configManager.getDouble("hud.profile.scale", 0.75D)));
        final float viewRange = (float) Math.max(0.25D, Math.min(4.0D,
                configManager.getDouble("hud.profile.view-range", 1.0D)));
        return new VitalsAppearance(text, scale, viewRange);
    }

    private static void applyVitalsAppearance(final TextDisplay display,
                                               final VitalsAppearance appearance) {
        display.text(appearance.text());
        display.setTransformation(new Transformation(
                new Vector3f(), new AxisAngle4f(),
                new Vector3f(appearance.scale(), appearance.scale(), appearance.scale()),
                new AxisAngle4f()));
        display.setViewRange(appearance.viewRange());
    }

    private void revealToAttacker(final Player attacker, final TextDisplay display) {
        if (attacker == null) {
            return;
        }
        final Runnable reveal = () -> {
            if (attacker.isOnline()) {
                attacker.showEntity(plugin, display);
            }
        };
        if (org.bukkit.Bukkit.isOwnedByCurrentRegion(attacker)) {
            reveal.run();
        } else {
            attacker.getScheduler().run(plugin, task -> reveal.run(), null);
        }
    }

    private void expireVitalDisplay(final UUID targetId, final long generation,
                                    final TextDisplay display) {
        final VitalDisplay current = vitalDisplays.get(targetId);
        if (current == null || current.generation() != generation || current.display() != display) {
            return;
        }
        vitalDisplays.remove(targetId, current);
        current.followTask().cancel();
        removeIfValid(display);
    }

    private void removeVitalDisplay(final UUID targetId) {
        final VitalDisplay current = vitalDisplays.remove(targetId);
        if (current != null) {
            current.followTask().cancel();
            removeDisplaySafely(current.display());
        }
    }

    private void removeDisplaySafely(final TextDisplay display) {
        display.getScheduler().run(plugin, task -> removeIfValid(display), null);
    }

    private static String compact(final double value) {
        final double rounded = Math.rint(value * 10.0D) / 10.0D;
        if (Math.abs(rounded - Math.rint(rounded)) < 0.001D) {
            return Long.toString(Math.round(rounded));
        }
        return String.format(Locale.ROOT, "%.1f", rounded);
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
