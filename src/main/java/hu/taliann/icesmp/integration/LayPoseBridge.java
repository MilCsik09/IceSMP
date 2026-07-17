package hu.taliann.icesmp.integration;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/**
 * Optional LibsDisguises bridge for IDEAS A49 (native {@code /sit fekves}) — <b>pure reflection</b>,
 * same pattern as {@link DruidDisguise}: IceSMP compiles and runs with no compile-time or hard
 * runtime dependency on LibsDisguises, and any API mismatch is swallowed rather than breaking the
 * caller. When the library is missing (or its API changed), {@link #isAvailable()} returns false and
 * the command should fall back to a "requires LibsDisguises" message.
 *
 * <p><b>How the lay pose works:</b> a {@code PlayerDisguise(Player)} disguises the player as
 * themselves (their own skin/name, invisible swap) purely so its {@code FlagWatcher} can be driven;
 * {@code FlagWatcher#setSleeping(true)} then forces the client-rendered {@code EntityPose} to
 * {@code SLEEPING}. This is a pure client-side metadata pose — unlike vanilla bed-sleeping it needs
 * no bed block/position (LibsDisguises' {@code LivingWatcher#setBedPosition} is a separate, unused
 * knob here): the model simply renders lying flat at the player's feet. The player's actual hitbox,
 * movement and collision stay standing-sized — this is a visual-only stance, exactly like the Druid
 * form's mob disguise is a visual-only stat stance.
 *
 * <p>All calls must run on the player's own region thread (the {@code /sit fekves} command already
 * does), so the disguise packets are sent Folia-correctly.
 */
public final class LayPoseBridge {

    private static final boolean AVAILABLE;
    private static Constructor<?> playerDisguiseCtor;
    private static Method setViewSelfDisguise;
    private static Method getWatcher;
    private static Method setSleeping;
    private static Method disguiseToAll;
    private static Method undisguiseToAll;

    static {
        boolean ok = false;
        try {
            final Class<?> api = Class.forName("me.libraryaddict.disguise.DisguiseAPI");
            final Class<?> disguise = Class.forName("me.libraryaddict.disguise.disguisetypes.Disguise");
            final Class<?> playerDisguise = Class.forName("me.libraryaddict.disguise.disguisetypes.PlayerDisguise");
            final Class<?> flagWatcher = Class.forName("me.libraryaddict.disguise.disguisetypes.FlagWatcher");

            playerDisguiseCtor = playerDisguise.getConstructor(Player.class);
            setViewSelfDisguise = disguise.getMethod("setViewSelfDisguise", boolean.class);
            getWatcher = playerDisguise.getMethod("getWatcher");
            setSleeping = flagWatcher.getMethod("setSleeping", boolean.class);
            disguiseToAll = api.getMethod("disguiseToAll", Entity.class, disguise);
            undisguiseToAll = api.getMethod("undisguiseToAll", Entity.class);
            ok = true;
        } catch (final Throwable ignored) {
            // LibsDisguises absent or its API changed — the lay pose stays unavailable; callers must
            // treat isAvailable() == false as "cannot lay down" (no fallback stat-only effect exists,
            // unlike the Druid forms, since lying down is purely cosmetic to begin with).
        }
        AVAILABLE = ok;
    }

    private LayPoseBridge() {
    }

    /** @return true if LibsDisguises is present and its expected API was bound. */
    public static boolean isAvailable() {
        return AVAILABLE;
    }

    /**
     * Disguises the player as themselves but forces the sleeping pose, so they visually lie down in
     * place. Best-effort: any reflection failure is a no-op and returns false, so the caller can show
     * the "unavailable" message instead of a silently-ignored command.
     *
     * @param player the player to lay down
     * @return true if the lay pose was applied
     */
    public static boolean apply(final Player player) {
        if (!AVAILABLE || player == null) {
            return false;
        }
        try {
            final Object disguise = playerDisguiseCtor.newInstance(player);
            setViewSelfDisguise.invoke(disguise, true);
            final Object watcher = getWatcher.invoke(disguise);
            setSleeping.invoke(watcher, true);
            disguiseToAll.invoke(null, player, disguise);
            return true;
        } catch (final Throwable ignored) {
            return false;
        }
    }

    /**
     * Removes the lay disguise from the player (back to the normal player model).
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
