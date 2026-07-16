package hu.taliann.icesmp.integration;

import org.bukkit.Bukkit;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Reflective soft-hook to LuckPerms for the native chat formatter (replaces the
 * external LuckPermsChatFormatterFolia plugin): resolves a player's cached
 * prefix/suffix. No compile-time dependency — without LuckPerms both lookups
 * return the empty string and the chat format simply has no rank decoration.
 *
 * <p>Methods are resolved once against the LuckPerms API <em>interfaces</em>
 * (not the implementation classes), so the invocations stay accessible. The
 * cached-metadata reads are thread-safe by LuckPerms contract, which matters:
 * the chat event is async.
 */
public final class LuckPermsBridge {

    private static volatile boolean initialised;
    private static volatile boolean available;

    private static Object userManager;
    private static Method getUserMethod;
    private static Method getCachedDataMethod;
    private static Method getMetaDataMethod;
    private static Method getPrefixMethod;
    private static Method getSuffixMethod;
    private static Method getPrimaryGroupMethod;

    private LuckPermsBridge() {
    }

    /** The player's LuckPerms prefix (legacy '&' codes), or "" without LP/prefix. */
    public static String prefix(final UUID playerId) {
        return meta(playerId, true);
    }

    /** The player's LuckPerms suffix (legacy '&' codes), or "" without LP/suffix. */
    public static String suffix(final UUID playerId) {
        return meta(playerId, false);
    }

    /**
     * A játékos elsődleges LuckPerms-csoportja kisbetűsítve (tablist-rendezéshez), vagy ""
     * LP nélkül. A cached User-olvasás az LP-kontraktus szerint szálbiztos, így a
     * régió-szálakon futó tablist-frissítés is hívhatja.
     */
    public static String primaryGroup(final UUID playerId) {
        if (!initialised) {
            initialise();
        }
        if (!available || playerId == null || getPrimaryGroupMethod == null) {
            return "";
        }
        try {
            final Object user = getUserMethod.invoke(userManager, playerId);
            if (user == null) {
                return "";
            }
            final Object group = getPrimaryGroupMethod.invoke(user);
            return group == null ? "" : String.valueOf(group).toLowerCase(java.util.Locale.ROOT);
        } catch (final Throwable throwable) {
            return "";
        }
    }

    private static String meta(final UUID playerId, final boolean prefix) {
        if (!initialised) {
            initialise();
        }
        if (!available || playerId == null) {
            return "";
        }
        try {
            final Object user = getUserMethod.invoke(userManager, playerId);
            if (user == null) {
                return ""; // Not loaded (offline) — chat only fires for online players anyway.
            }
            final Object cachedData = getCachedDataMethod.invoke(user);
            final Object metaData = getMetaDataMethod.invoke(cachedData);
            final Object value = (prefix ? getPrefixMethod : getSuffixMethod).invoke(metaData);
            return value == null ? "" : String.valueOf(value);
        } catch (final Throwable throwable) {
            available = false; // Broke at runtime — chat keeps working, just undecorated.
            return "";
        }
    }

    private static synchronized void initialise() {
        if (initialised) {
            return;
        }
        initialised = true;
        if (!Bukkit.getPluginManager().isPluginEnabled("LuckPerms")) {
            return;
        }
        try {
            final Class<?> providerClass = Class.forName("net.luckperms.api.LuckPermsProvider");
            final Object luckPerms = providerClass.getMethod("get").invoke(null);
            userManager = Class.forName("net.luckperms.api.LuckPerms")
                    .getMethod("getUserManager").invoke(luckPerms);
            getUserMethod = Class.forName("net.luckperms.api.model.user.UserManager")
                    .getMethod("getUser", UUID.class);
            getCachedDataMethod = Class.forName("net.luckperms.api.model.user.User")
                    .getMethod("getCachedData");
            getMetaDataMethod = Class.forName("net.luckperms.api.cacheddata.CachedDataManager")
                    .getMethod("getMetaData");
            final Class<?> metaDataClass = Class.forName("net.luckperms.api.cacheddata.CachedMetaData");
            getPrefixMethod = metaDataClass.getMethod("getPrefix");
            getSuffixMethod = metaDataClass.getMethod("getSuffix");
            getPrimaryGroupMethod = Class.forName("net.luckperms.api.model.user.User")
                    .getMethod("getPrimaryGroup");
            available = true;
            Bukkit.getLogger().info("[IceSMP] LuckPerms-híd bekapcsolva: a chat-formázó a LP prefix/suffix-et használja.");
        } catch (final Throwable throwable) {
            available = false;
            Bukkit.getLogger().warning("[IceSMP] LuckPerms jelen van, de a prefix-híd nem indult ("
                    + throwable.getClass().getSimpleName() + ") — a chat prefix nélkül formázódik.");
        }
    }
}
