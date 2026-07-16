package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.managers.ConfigManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.Locale;
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

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final ConcurrentHashMap<UUID, Long> lastShownAt = new ConcurrentHashMap<>();

    public DamageIndicatorListener(final JavaPlugin plugin, final ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(final EntityDamageByEntityEvent event) {
        if (!configManager.getBoolean("spells.damage-indicators.enabled", true)) {
            return;
        }

        final double damage = event.getFinalDamage();
        if (damage <= 0.0D) {
            return;
        }

        final Player attacker = resolvePlayer(event.getDamager());
        if (attacker == null) {
            return;
        }

        final Entity victim = event.getEntity();
        if (!isRateLimited(victim.getUniqueId())) {
            spawnIndicator(victim, damage);
        }
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

    /** Spawns the floating number over the victim and schedules its own despawn. */
    private void spawnIndicator(final Entity victim, final double damage) {
        if (victim.getWorld() == null) {
            return;
        }

        final double bob = 1.8D + ThreadLocalRandom.current().nextDouble(0.4D);
        final Location at = victim.getLocation().clone().add(
                ThreadLocalRandom.current().nextDouble(-0.3D, 0.3D), bob,
                ThreadLocalRandom.current().nextDouble(-0.3D, 0.3D));

        final String text = String.format(Locale.ROOT, "%.1f", damage);
        final NamedTextColor color = victim instanceof Player ? NamedTextColor.RED : NamedTextColor.YELLOW;

        final TextDisplay display = at.getWorld().spawn(at, TextDisplay.class, spawned -> {
            spawned.setBillboard(Display.Billboard.CENTER);
            spawned.setPersistent(false);
            spawned.setDefaultBackground(false);
            spawned.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            spawned.setShadowed(true);
            spawned.setTransformation(new Transformation(
                    new Vector3f(), new AxisAngle4f(), new Vector3f(1.0F, 1.0F, 1.0F), new AxisAngle4f()));
            spawned.text(Component.text(text, color));
        });

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
