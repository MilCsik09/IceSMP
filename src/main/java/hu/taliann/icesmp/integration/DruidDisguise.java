package hu.taliann.icesmp.integration;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/**
 * Optional LibsDisguises bridge — <b>pure reflection</b>, so IceSMP compiles and runs with no
 * compile-time or hard runtime dependency on LibsDisguises. When the library is installed (and on
 * our classpath via the {@code paper-plugin.yml} soft-dependency), the Druid forms additionally
 * disguise the player as the matching mob; otherwise the form system stays stat-only and this class
 * silently no-ops. Any reflection/API mismatch is swallowed, so a wrong guess can never break a cast.
 *
 * <p>All calls must run on the player's own region thread (the Druid form spell already does), so the
 * disguise packets are sent Folia-correctly.
 */
public final class DruidDisguise {

    private static final boolean AVAILABLE;
    private static Method disguiseToAll;
    private static Method undisguiseToAll;
    private static Method setViewSelfDisguise;
    private static Constructor<?> mobDisguiseCtor;
    private static Method disguiseTypeValueOf;

    static {
        boolean ok = false;
        try {
            final Class<?> api = Class.forName("me.libraryaddict.disguise.DisguiseAPI");
            final Class<?> disguise = Class.forName("me.libraryaddict.disguise.disguisetypes.Disguise");
            final Class<?> mobDisguise = Class.forName("me.libraryaddict.disguise.disguisetypes.MobDisguise");
            final Class<?> disguiseType = Class.forName("me.libraryaddict.disguise.disguisetypes.DisguiseType");

            disguiseToAll = api.getMethod("disguiseToAll", Entity.class, disguise);
            undisguiseToAll = api.getMethod("undisguiseToAll", Entity.class);
            setViewSelfDisguise = disguise.getMethod("setViewSelfDisguise", boolean.class);
            mobDisguiseCtor = mobDisguise.getConstructor(disguiseType);
            disguiseTypeValueOf = disguiseType.getMethod("valueOf", String.class);
            ok = true;
        } catch (final Throwable ignored) {
            // LibsDisguises absent or its API changed — disguise stays off, the stance still works.
        }
        AVAILABLE = ok;
    }

    private DruidDisguise() {
    }

    /** @return true if LibsDisguises is present and its expected API was bound. */
    public static boolean isAvailable() {
        return AVAILABLE;
    }

    /**
     * Disguises the player as the given LibsDisguises mob type (e.g. {@code "POLAR_BEAR"}), so the form
     * also changes appearance. Best-effort: a missing library or API mismatch is a no-op.
     *
     * @param player the player to disguise
     * @param disguiseTypeName the {@code DisguiseType} enum name of the target mob
     */
    public static void apply(final Player player, final String disguiseTypeName) {
        if (!AVAILABLE || player == null || disguiseTypeName == null) {
            return;
        }
        try {
            final Object type = disguiseTypeValueOf.invoke(null, disguiseTypeName);
            final Object disguise = mobDisguiseCtor.newInstance(type);
            setViewSelfDisguise.invoke(disguise, true);
            disguiseToAll.invoke(null, player, disguise);
        } catch (final Throwable ignored) {
            // Best-effort; the stat-stance was already applied by the caller.
        }
    }

    /**
     * Removes any active disguise from the player (back to the normal player model).
     *
     * @param player the player to restore
     */
    public static void clear(final Player player) {
        if (!AVAILABLE || player == null) {
            return;
        }
        try {
            undisguiseToAll.invoke(null, player);
        } catch (final Throwable ignored) {
            // Best-effort.
        }
    }
}
