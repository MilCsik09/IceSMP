package hu.taliann.icesmp.utils;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Hard-CC diminishing returns (PvP): ugyanarra a JÁTÉKOS-célpontra ismételt
 * hard CC (fagyasztás, erős lassítás) csökkenő erővel hat — 100% → 50% → 25% →
 * immun, a window-seconds ablakon belül. Mob-célpontra nem vonatkozik (a PvE
 * kontroll-játék maradjon jutalmazó). A számláló konkurrens map (a CC bármely
 * régió-szálon eshet), a lejárt ablakok hozzáférésekor és túlcsordulásnál
 * söprődnek.
 */
public final class CcDiminish {

    private static volatile hu.taliann.icesmp.managers.ConfigManager configManager;
    /** célpont → {ablak-kezdet ms, eddigi hard CC-k száma az ablakban}. */
    private static final Map<UUID, long[]> WINDOWS = new ConcurrentHashMap<>();

    private CcDiminish() {
    }

    public static void init(final hu.taliann.icesmp.managers.ConfigManager config) {
        configManager = config;
    }

    /**
     * A következő hard CC szorzója a célponton (1.0 / 0.5 / 0.25 / 0.0), és a
     * számláló léptetése. Nem-játékos célpontra mindig 1.0.
     */
    public static double nextFactor(final LivingEntity target) {
        final hu.taliann.icesmp.managers.ConfigManager config = configManager;
        if (config == null || !(target instanceof Player)
                || !config.getBoolean("spells.cc-dr.enabled", true)) {
            return 1.0D;
        }
        final long windowMillis = Math.max(1L, config.getLong("spells.cc-dr.window-seconds", 15L)) * 1000L;
        final long now = System.currentTimeMillis();
        if (WINDOWS.size() > 512) {
            WINDOWS.values().removeIf(entry -> now - entry[0] > windowMillis);
        }
        final long[] entry = WINDOWS.compute(target.getUniqueId(), (key, old) ->
                old == null || now - old[0] > windowMillis ? new long[]{now, 1L} : new long[]{old[0], old[1] + 1L});
        return switch ((int) Math.min(entry[1], 4L)) {
            case 1 -> 1.0D;
            case 2 -> 0.5D;
            case 3 -> 0.25D;
            default -> 0.0D;
        };
    }

    public static void clear(final UUID targetId) {
        if (targetId != null) {
            WINDOWS.remove(targetId);
        }
    }
}
