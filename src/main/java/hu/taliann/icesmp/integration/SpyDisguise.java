package hu.taliann.icesmp.integration;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/**
 * G14 — kém-álca híd (LibsDisguises soft-depend, reflexiós — a DruidDisguise
 * mintája): a játékost egy HAMIS NEVŰ játékos-alaknak mutatja mindenki másnak.
 * Best-effort: hiányzó library vagy API-eltérés esetén no-op. Folia: a hívó
 * felelőssége, hogy a cél saját régió-szálán fusson.
 */
public final class SpyDisguise {

    private static final boolean AVAILABLE;
    private static Method disguiseToAll;
    private static Method undisguiseToAll;
    private static Method setViewSelfDisguise;
    private static Constructor<?> playerDisguiseCtor;

    static {
        boolean available = false;
        try {
            final Class<?> api = Class.forName("me.libraryaddict.disguise.DisguiseAPI");
            final Class<?> disguise = Class.forName("me.libraryaddict.disguise.disguisetypes.Disguise");
            final Class<?> playerDisguise = Class.forName("me.libraryaddict.disguise.disguisetypes.PlayerDisguise");
            disguiseToAll = api.getMethod("disguiseToAll", Entity.class, disguise);
            undisguiseToAll = api.getMethod("undisguiseToAll", Entity.class);
            setViewSelfDisguise = disguise.getMethod("setViewSelfDisguise", boolean.class);
            playerDisguiseCtor = playerDisguise.getConstructor(String.class);
            available = true;
        } catch (final ReflectiveOperationException | LinkageError ignored) {
            // LibsDisguises nincs jelen — a kém-mechanika kikapcsol.
        }
        AVAILABLE = available;
    }

    private SpyDisguise() {
    }

    public static boolean isAvailable() {
        return AVAILABLE;
    }

    /** Álca felvétele hamis névvel; best-effort. */
    public static boolean apply(final Player player, final String fakeName) {
        if (!AVAILABLE || player == null || fakeName == null || fakeName.isBlank()) {
            return false;
        }
        try {
            final Object disguise = playerDisguiseCtor.newInstance(fakeName);
            setViewSelfDisguise.invoke(disguise, true);
            disguiseToAll.invoke(null, player, disguise);
            return true;
        } catch (final ReflectiveOperationException exception) {
            return false;
        }
    }

    public static void remove(final Player player) {
        if (!AVAILABLE || player == null) {
            return;
        }
        try {
            undisguiseToAll.invoke(null, player);
        } catch (final ReflectiveOperationException ignored) {
            // best-effort
        }
    }
}
