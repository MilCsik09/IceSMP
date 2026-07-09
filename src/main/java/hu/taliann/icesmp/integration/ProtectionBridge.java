package hu.taliann.icesmp.integration;

import org.bukkit.Bukkit;
import org.bukkit.Location;

import java.lang.reflect.Method;

/**
 * Reflective soft-hook to WorldGuard: tells block-placing world events (meteor
 * crater, treasure chest) whether a location lies inside ANY WorldGuard region,
 * so they land elsewhere instead of inside protected towns/spawn. Like the
 * FancyNpcs/LibsDisguises bridges, this has no compile-time dependency — without
 * WorldGuard every check simply reports "not protected".
 *
 * <p>The reflection chain ({@code WorldGuard.getInstance().getPlatform()
 * .getRegionContainer().createQuery().getApplicableRegions(adapt(loc)).size()})
 * is resolved once and cached; any failure permanently disables the bridge for
 * the session (fail-open, with a single log line). Region queries are
 * thread-safe in WorldGuard, so calling from a region thread is fine.
 */
public final class ProtectionBridge {

    private static volatile boolean initialised;
    private static volatile boolean available;

    private static Object regionQuery;
    private static Method adaptMethod;
    private static Method getApplicableRegionsMethod;
    private static Method sizeMethod;

    private ProtectionBridge() {
    }

    /**
     * Whether the location lies inside any WorldGuard region. Fail-open: without
     * WorldGuard (or if the reflective hook broke) this is always false.
     *
     * @param location the location to test
     * @return true if a WorldGuard region covers the location
     */
    public static boolean isProtected(final Location location) {
        if (location == null || location.getWorld() == null) {
            return false;
        }
        if (!initialised) {
            initialise();
        }
        if (!available) {
            return false;
        }
        try {
            final Object adapted = adaptMethod.invoke(null, location);
            final Object regionSet = getApplicableRegionsMethod.invoke(regionQuery, adapted);
            return ((Number) sizeMethod.invoke(regionSet)).intValue() > 0;
        } catch (final Throwable throwable) {
            available = false; // Broke at runtime (e.g. WG reload) — fail open from here on.
            return false;
        }
    }

    private static synchronized void initialise() {
        if (initialised) {
            return;
        }
        initialised = true;
        if (!Bukkit.getPluginManager().isPluginEnabled("WorldGuard")) {
            return;
        }
        try {
            // Resolve every Method from the PUBLIC WorldGuard API types — resolving from
            // implementation classes risks IllegalAccessException on non-public impls.
            final Class<?> worldGuardClass = Class.forName("com.sk89q.worldguard.WorldGuard");
            final Object worldGuard = worldGuardClass.getMethod("getInstance").invoke(null);
            final Object platform = worldGuardClass.getMethod("getPlatform").invoke(worldGuard);
            // WG7's getPlatform() returns internal.platform.WorldGuardPlatform; if a future
            // version moves/renames it, fall back to the runtime class (made accessible).
            java.lang.reflect.Method getRegionContainer;
            try {
                getRegionContainer = Class.forName("com.sk89q.worldguard.internal.platform.WorldGuardPlatform")
                        .getMethod("getRegionContainer");
            } catch (final ClassNotFoundException moved) {
                getRegionContainer = platform.getClass().getMethod("getRegionContainer");
                getRegionContainer.setAccessible(true);
            }
            final Object container = getRegionContainer.invoke(platform);
            final Class<?> containerClass = Class.forName("com.sk89q.worldguard.protection.regions.RegionContainer");
            regionQuery = containerClass.getMethod("createQuery").invoke(container);

            final Class<?> bukkitAdapter = Class.forName("com.sk89q.worldguard.bukkit.BukkitAdapter");
            adaptMethod = bukkitAdapter.getMethod("adapt", Location.class);
            final Class<?> weLocation = Class.forName("com.sk89q.worldedit.util.Location");
            getApplicableRegionsMethod = Class.forName("com.sk89q.worldguard.protection.regions.RegionQuery")
                    .getMethod("getApplicableRegions", weLocation);
            sizeMethod = Class.forName("com.sk89q.worldguard.protection.ApplicableRegionSet")
                    .getMethod("size");

            available = true;
            Bukkit.getLogger().info("[IceSMP] WorldGuard-híd bekapcsolva: a meteor/kincs események kerülik a WG-régiókat.");
        } catch (final Throwable throwable) {
            available = false;
            Bukkit.getLogger().warning("[IceSMP] WorldGuard jelen van, de a régió-ellenőrző híd nem indult ("
                    + throwable.getClass().getSimpleName() + ") — az események NEM kerülik a WG-régiókat.");
        }
    }
}
