package hu.taliann.icesmp.managers;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * HP-rendszer: kaszt-HP-profilok (a max életerő a kaszt jellegével és szintjével
 * nő), normalizált szív-kijelzés (a sor mindig 10 szív marad), és harcon kívüli
 * regen (az utolsó sebzés-kontaktus után késleltetve, étel-küszöbbel — az étel
 * harci tét marad). A talent- és felszerelés-HP a saját modifier-kulcsán él, a
 * kettő összeadódik.
 */
public final class ClassHealthService implements hu.taliann.icesmp.session.PlayerStateCleanup {

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final JobManager jobManager;
    private final NamespacedKey classHealthKey;
    /** játékos → az utolsó sebzés-kontaktus (adott VAGY kapott) epoch ms. */
    private final Map<UUID, Long> lastCombatAt = new ConcurrentHashMap<>();

    public ClassHealthService(final JavaPlugin plugin, final ConfigManager configManager,
                              final JobManager jobManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.jobManager = jobManager;
        this.classHealthKey = new NamespacedKey(plugin, "class_health_mod");
    }

    public boolean isEnabled() {
        return configManager.getBoolean("health.enabled", true);
    }

    public void recordCombat(final UUID playerId) {
        if (playerId != null) {
            lastCombatAt.put(playerId, System.currentTimeMillis());
        }
    }

    /**
     * A kaszt-HP modifier + a kijelzés-normalizálás alkalmazása. A JÁTÉKOS SAJÁT
     * régió-szálán hívandó; idempotens — csak tényleges változásnál ír attribútumot,
     * így a periodikus újra-hívás (szint-lépés/kaszt-váltás/reload lefedése) olcsó.
     */
    public void apply(final Player player) {
        final AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth == null) {
            return;
        }
        final double target = isEnabled() ? bonusFor(player) : 0.0D;
        double current = 0.0D;
        boolean present = false;
        for (final AttributeModifier modifier : maxHealth.getModifiers()) {
            if (classHealthKey.equals(modifier.getKey())) {
                current = modifier.getAmount();
                present = true;
                break;
            }
        }
        if ((!present && target != 0.0D) || (present && Math.abs(current - target) > 1.0E-3D)) {
            for (final AttributeModifier modifier : List.copyOf(maxHealth.getModifiers())) {
                if (classHealthKey.equals(modifier.getKey())) {
                    maxHealth.removeModifier(modifier);
                }
            }
            if (target != 0.0D) {
                maxHealth.addModifier(new AttributeModifier(classHealthKey, target,
                        AttributeModifier.Operation.ADD_NUMBER));
            }
            if (player.getHealth() > maxHealth.getValue()) {
                player.setHealth(maxHealth.getValue());
            }
        }
        if (isEnabled() && configManager.getBoolean("health.display.normalize", true)) {
            final double scale = Math.max(2, configManager.getInt("health.display.hearts", 10)) * 2.0D;
            if (!player.isHealthScaled() || Math.abs(player.getHealthScale() - scale) > 1.0E-3D) {
                player.setHealthScale(scale);
                player.setHealthScaled(true);
            }
        } else if (player.isHealthScaled()) {
            player.setHealthScaled(false);
        }
    }

    /** A kaszt-profil HP-többlete: (base − 20) + szint × per-level; kaszt nélkül 0. */
    private double bonusFor(final Player player) {
        final hu.taliann.icesmp.data.JobType job = jobManager.getPrimaryJob(player);
        if (job == null) {
            return 0.0D;
        }
        final String path = "health.class-profiles." + job.getId();
        final double base = configManager.getDouble(path + ".base", 20.0D);
        final double perLevel = Math.max(0.0D, configManager.getDouble(path + ".per-level", 0.35D));
        return Math.max(0.0D, base - 20.0D + jobManager.getPrimaryLevel(player) * perLevel);
    }

    /**
     * Periodikus kör (global schedulerről): profil-frissítés + harcon kívüli regen,
     * játékosonként a saját régió-szálon.
     */
    public void tick() {
        if (!isEnabled()) {
            return;
        }
        final boolean regenEnabled = configManager.getBoolean("health.ooc-regen.enabled", true);
        final long delayMillis = Math.max(0L, configManager.getLong("health.ooc-regen.delay-seconds", 8L)) * 1000L;
        final double percent = Math.max(0.0D, configManager.getDouble("health.ooc-regen.percent-per-tick", 5.0D));
        final int minFood = configManager.getInt("health.ooc-regen.min-food", 6);
        for (final Player player : Bukkit.getOnlinePlayers()) {
            player.getScheduler().run(plugin, task -> {
                apply(player);
                if (!regenEnabled || player.isDead() || percent <= 0.0D) {
                    return;
                }
                final Long last = lastCombatAt.get(player.getUniqueId());
                if (last != null && System.currentTimeMillis() - last < delayMillis) {
                    return;
                }
                if (player.getFoodLevel() < minFood) {
                    return;
                }
                final AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
                if (maxHealth == null || player.getHealth() >= maxHealth.getValue()) {
                    return;
                }
                player.setHealth(Math.min(maxHealth.getValue(),
                        player.getHealth() + maxHealth.getValue() * percent / 100.0D));
            }, null);
        }
    }

    @Override
    public void clearPlayerState(final UUID playerId) {
        if (playerId != null) {
            lastCombatAt.remove(playerId);
        }
    }
}
