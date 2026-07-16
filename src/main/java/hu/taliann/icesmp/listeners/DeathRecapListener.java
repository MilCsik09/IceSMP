package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.managers.ConfigManager;
import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.projectiles.ProjectileSource;

import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Death recap (A8-ish QoL): tracks the last ~10 seconds of damage a player took in a bounded
 * per-player ring buffer, and on death sends them a short summary of what killed them.
 *
 * <p>Folia note: {@link EntityDamageEvent}/{@link EntityDamageByEntityEvent}/{@link PlayerDeathEvent}
 * all run on the DAMAGED/dying entity's own region thread when the entity is a player, so writing
 * to that player's own buffer entry and calling {@code sendMessage} on them here is region-local
 * and needs no scheduler hop. Each player's {@link Deque} is only ever mutated by that player's own
 * region thread (the events here only fire for the victim being this listener's target), so a
 * lock-free {@link ConcurrentLinkedDeque} with size-trimming is safe; the outer map is a
 * {@link ConcurrentHashMap} because different players' region threads touch different keys
 * concurrently.
 *
 * <p>Registered at {@link EventPriority#MONITOR} with {@code ignoreCancelled = true} so this is
 * purely observational and never affects combat resolution.
 */
public final class DeathRecapListener implements Listener {

    /** Bounded ring-buffer size per player (oldest entries fall off first). */
    private static final int MAX_ENTRIES = 10;
    /** How many of the most recent (still-relevant) entries are shown in the recap. */
    private static final int RECAP_ENTRIES = 5;
    /** Entries older than this are not shown in the recap. */
    private static final long RECAP_WINDOW_MILLIS = 10_000L;

    private final ConfigManager configManager;
    private final MessageManager messageManager;
    private final Map<UUID, Deque<Entry>> recentDamage = new ConcurrentHashMap<>();

    public DeathRecapListener(final ConfigManager configManager, final MessageManager messageManager) {
        this.configManager = configManager;
        this.messageManager = messageManager;
    }

    /** One recorded hit: when, how much (in raw damage points), and where it came from. */
    private record Entry(long timestamp, double damage, String source, String shooter) {
    }

    /** Non-entity-caused damage (fall, fire, lava, drowning, poison, void, ...). */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamage(final EntityDamageEvent event) {
        if (!isEnabled()) {
            return;
        }
        // Az EntityDamageByEntityEvent UGYANAZT a HandlerList-et használja, mint az ősosztálya,
        // így ez a handler az entitás-ütésekre IS lefutna — azokat a dedikált handler rögzíti,
        // itt kihagyjuk, különben minden ütés kétszer számítódna.
        if (event instanceof EntityDamageByEntityEvent) {
            return;
        }
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        final double damage = event.getFinalDamage();
        if (damage <= 0.0D) {
            return;
        }
        record(victim.getUniqueId(), damage, translateCause(event.getCause()), null);
    }

    /**
     * Entity-caused damage (melee/projectile hits). Note: Bukkit dispatches
     * {@link EntityDamageByEntityEvent} to its OWN handler list, not {@link EntityDamageEvent}'s —
     * so this and {@link #onEntityDamage(EntityDamageEvent)} never both fire for the same hit.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamageByEntity(final EntityDamageByEntityEvent event) {
        if (!isEnabled()) {
            return;
        }
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        final double damage = event.getFinalDamage();
        if (damage <= 0.0D) {
            return;
        }

        final Entity damager = event.getDamager();
        String shooter = null;
        final String source;
        if (damager instanceof Player attacker) {
            source = attacker.getName();
        } else if (damager instanceof Projectile projectile) {
            source = translateEntityType(projectile.getType());
            final ProjectileSource projectileSource = projectile.getShooter();
            if (projectileSource instanceof Player shooterPlayer) {
                shooter = shooterPlayer.getName();
            } else if (projectileSource instanceof LivingEntity shooterEntity) {
                shooter = translateEntityType(shooterEntity.getType());
            }
        } else {
            source = translateEntityType(damager.getType());
        }

        record(victim.getUniqueId(), damage, source, shooter);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(final PlayerDeathEvent event) {
        if (!isEnabled()) {
            return;
        }

        final Player victim = event.getEntity();
        final Deque<Entry> buffer = recentDamage.remove(victim.getUniqueId());
        if (buffer == null || buffer.isEmpty()) {
            return;
        }

        final long now = System.currentTimeMillis();
        final List<Entry> relevant = new ArrayList<>();
        double total = 0.0D;
        for (final Entry entry : buffer) {
            if (now - entry.timestamp() <= RECAP_WINDOW_MILLIS) {
                relevant.add(entry);
                total += entry.damage();
            }
        }
        if (relevant.isEmpty()) {
            return;
        }

        victim.sendMessage(messageManager.get("death-recap-header",
                "&c☠ Halál-összegző (utolsó 10 mp):", Map.of()));

        final int from = Math.max(0, relevant.size() - RECAP_ENTRIES);
        for (final Entry entry : relevant.subList(from, relevant.size())) {
            final double secondsAgo = (now - entry.timestamp()) / 1000.0D;
            final double hearts = entry.damage() / 2.0D;
            final String sourceText = entry.shooter() == null
                    ? entry.source()
                    : entry.source() + " (" + entry.shooter() + ")";
            victim.sendMessage(messageManager.get("death-recap-entry",
                    "&7-{damage}❤ {source} ({seconds} mp-e)",
                    Map.of(
                            "damage", format(hearts),
                            "source", sourceText,
                            "seconds", format(secondsAgo)
                    )));
        }

        victim.sendMessage(messageManager.get("death-recap-total",
                "&7Összesített sebzés: -{total}❤",
                Map.of("total", format(total / 2.0D))));
    }

    @EventHandler
    public void onPlayerQuit(final PlayerQuitEvent event) {
        clearPlayerState(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onPlayerKick(final PlayerKickEvent event) {
        // PlayerKickEvent does not reliably chain to PlayerQuitEvent — clear here too.
        clearPlayerState(event.getPlayer().getUniqueId());
    }

    /** Releases the in-memory buffer held for this player. Safe to call even if none exists. */
    public void clearPlayerState(final UUID playerId) {
        recentDamage.remove(playerId);
    }

    private boolean isEnabled() {
        return configManager.getBoolean("spells.death-recap.enabled", true);
    }

    private void record(final UUID playerId, final double damage, final String source, final String shooter) {
        final Deque<Entry> buffer = recentDamage.computeIfAbsent(playerId, id -> new ConcurrentLinkedDeque<>());
        buffer.addLast(new Entry(System.currentTimeMillis(), damage, source, shooter));
        while (buffer.size() > MAX_ENTRIES) {
            buffer.pollFirst();
        }
    }

    private static String format(final double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    /** Hungarian translation for the common {@link EntityDamageEvent.DamageCause} values. */
    private static String translateCause(final EntityDamageEvent.DamageCause cause) {
        return switch (cause) {
            case FALL -> "ZUHANÁS";
            case FIRE -> "TŰZ";
            case FIRE_TICK -> "ÉGÉS";
            case LAVA -> "LÁVA";
            case DROWNING -> "FULLADÁS";
            case POISON -> "MÉREG";
            case WITHER -> "WITHER";
            case MAGIC -> "MÁGIA";
            case ENTITY_EXPLOSION -> "ROBBANÁS";
            case BLOCK_EXPLOSION -> "ROBBANÁS";
            case VOID -> "ŰR";
            case STARVATION -> "ÉHEZÉS";
            case FREEZE -> "FAGYÁS";
            case CONTACT -> "TÜSKE";
            case PROJECTILE -> "LÖVEDÉK";
            default -> cause.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        };
    }

    /** Best-effort Hungarian name for common mob/projectile types; falls back to a formatted enum name. */
    private static String translateEntityType(final EntityType type) {
        return switch (type) {
            case ZOMBIE -> "Zombi";
            case HUSK -> "Husk";
            case DROWNED -> "Vízizombi";
            case ZOMBIE_VILLAGER -> "Zombi Falusi";
            case SKELETON -> "Csontváz";
            case STRAY -> "Fagyos Csontváz";
            case WITHER_SKELETON -> "Wither Csontváz";
            case BOGGED -> "Mocsári Csontváz";
            case SPIDER -> "Pók";
            case CAVE_SPIDER -> "Barlangi Pók";
            case CREEPER -> "Creeper";
            case ENDERMAN -> "Enderman";
            case ENDERMITE -> "Endermite";
            case WITCH -> "Boszorkány";
            case PHANTOM -> "Fantom";
            case BLAZE -> "Blaze";
            case GHAST -> "Ghast";
            case SLIME -> "Nyálka";
            case MAGMA_CUBE -> "Magmakocka";
            case WITHER -> "Wither";
            case ENDER_DRAGON -> "Enderdrakon";
            case PILLAGER -> "Fosztogató";
            case VINDICATOR -> "Bosszúálló";
            case EVOKER -> "Idéző";
            case VEX -> "Vex";
            case RAVAGER -> "Pusztító";
            case GUARDIAN -> "Őrző";
            case ELDER_GUARDIAN -> "Vén Őrző";
            case SHULKER -> "Shulker";
            case SILVERFISH -> "Ezüstmoly";
            case HOGLIN -> "Hoglin";
            case ZOGLIN -> "Zoglin";
            case PIGLIN -> "Piglin";
            case PIGLIN_BRUTE -> "Piglin Vadóc";
            case WARDEN -> "Őrszem";
            case BREEZE -> "Fuvallat";
            case WOLF -> "Farkas";
            case POLAR_BEAR -> "Jegesmedve";
            case IRON_GOLEM -> "Vasgólem";
            case ARROW -> "Nyíl";
            case SPECTRAL_ARROW -> "Spektrál Nyíl";
            case TRIDENT -> "Szigony";
            case SNOWBALL -> "Hógolyó";
            case FIREBALL -> "Tűzgolyó";
            case SMALL_FIREBALL -> "Kis Tűzgolyó";
            case WITHER_SKULL -> "Wither Koponya";
            case SHULKER_BULLET -> "Shulker Lövedék";
            case LLAMA_SPIT -> "Láma Köpés";
            case DRAGON_FIREBALL -> "Sárkány Tűzgolyó";
            case FIREWORK_ROCKET -> "Tűzijáték";
            case TNT -> "TNT";
            default -> {
                final String raw = type.name().toLowerCase(Locale.ROOT).replace('_', ' ');
                yield raw.substring(0, 1).toUpperCase(Locale.ROOT) + raw.substring(1);
            }
        };
    }
}
