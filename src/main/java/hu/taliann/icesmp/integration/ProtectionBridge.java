package hu.taliann.icesmp.integration;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Reflective soft-hook to WorldGuard. Two questions are answered here:
 * <ul>
 *   <li><b>point</b> — does a location lie inside any WG region? (block-placing world events:
 *       meteor crater, treasure chest, event spawns);</li>
 *   <li><b>box</b> — does a whole claim box INTERSECT any WG region? A point-sampled check
 *       (chunk centres + corners at sea level) let a small, narrow or differently-elevated
 *       region slip between the samples, so a private claim could land over spawn, a dungeon
 *       or an admin building.</li>
 * </ul>
 *
 * <p>Like the FancyNpcs/LibsDisguises bridges this has no compile-time dependency — without
 * WorldGuard every check reports "no region". The reflection chain is resolved once and cached.
 *
 * <p><b>Failure model.</b> WG absent is NOT an error: the answer is a definite "no region".
 * A runtime failure (e.g. a WG reload mid-query) used to disable the bridge permanently, so a
 * single glitch silently removed every overlap check until the next restart. Now a failure only
 * opens a {@value #BREAKER_MILLIS}ms circuit breaker and the bridge retries afterwards. During
 * the breaker the answer is UNKNOWN ({@code null} from the {@code query*} methods): callers
 * decide their own risk direction — the claim check refuses (fail-closed), while an event spawn
 * proceeds (fail-open, a missed spawn rule is cheaper than no events at all).
 *
 * <p>Region queries are thread-safe in WorldGuard, so calling from a region thread is fine.
 */
public final class ProtectionBridge {

    /** A megszakító nyitva-tartása: ennyi ideig nem kérdezzük újra a hibázó WG-t. */
    private static final long BREAKER_MILLIS = 60_000L;
    /** Ennyi egymást követő hiba után szólunk a logban is (a spam ellen). */
    private static final int LOG_EVERY_FAILURES = 5;

    private static volatile boolean initialised;
    /** A WG jelen van ÉS a reflexiós lánc feloldódott. */
    private static volatile boolean installed;
    /** Amíg ez a jövőben van, a hídat hibásnak tekintjük (megszakító nyitva). */
    private static volatile long brokenUntil;
    private static final AtomicInteger failures = new AtomicInteger();
    /** Megszakító-trip után a lejáratkor a reflexiós láncot ÚJRA fel kell oldani. */
    private static final java.util.concurrent.atomic.AtomicBoolean needsReinit =
            new java.util.concurrent.atomic.AtomicBoolean();

    private static Object regionContainer;
    private static Object regionQuery;
    private static Method adaptLocationMethod;
    private static Method adaptWorldMethod;
    private static Method getApplicableRegionsMethod;
    private static Method sizeMethod;
    private static Method containerGetMethod;
    private static Method managerOverlapMethod;
    private static Method blockVectorAtMethod;
    private static Constructor<?> cuboidConstructor;

    private ProtectionBridge() {
    }

    /**
     * Whether the location lies inside any WorldGuard region. FAIL-OPEN: without WorldGuard,
     * or while the circuit breaker is open, this reports false. Use
     * {@link #queryProtected(Location)} when the caller must distinguish "no region" from
     * "cannot tell".
     */
    public static boolean isProtected(final Location location) {
        return Boolean.TRUE.equals(queryProtected(location));
    }

    /**
     * The point query with an explicit unknown state.
     *
     * @return TRUE inside a region, FALSE outside (or WG absent), null while the bridge is broken
     */
    public static Boolean queryProtected(final Location location) {
        if (location == null || location.getWorld() == null) {
            return Boolean.FALSE;
        }
        if (!ready()) {
            return unavailableAnswer();
        }
        try {
            final Object adapted = adaptLocationMethod.invoke(null, location);
            final Object regionSet = getApplicableRegionsMethod.invoke(regionQuery, adapted);
            failures.set(0);
            return ((Number) sizeMethod.invoke(regionSet)).intValue() > 0;
        } catch (final Throwable throwable) {
            tripBreaker(throwable);
            return null;
        }
    }

    /**
     * Whether the block box intersects any WorldGuard region — a REAL cuboid intersection over
     * the world's full height, not a handful of sample points.
     *
     * @return TRUE on overlap, FALSE when clear (or WG absent), null while the bridge is broken
     */
    public static Boolean queryRegionOverlap(final World world, final int minX, final int minZ,
                                             final int maxX, final int maxZ) {
        if (world == null) {
            return Boolean.FALSE;
        }
        if (!ready()) {
            return unavailableAnswer();
        }
        if (cuboidConstructor == null || managerOverlapMethod == null) {
            // Régebbi/átalakított WG-API: a box-lekérdezés nem áll rendelkezésre.
            return null;
        }
        try {
            final Object weWorld = adaptWorldMethod.invoke(null, world);
            final Object manager = containerGetMethod.invoke(regionContainer, weWorld);
            if (manager == null) {
                failures.set(0);
                return Boolean.FALSE; // a világban nincs region-manager = nincs régió
            }
            final Object min = blockVectorAtMethod.invoke(null,
                    Math.min(minX, maxX), world.getMinHeight(), Math.min(minZ, maxZ));
            final Object max = blockVectorAtMethod.invoke(null,
                    Math.max(minX, maxX), world.getMaxHeight(), Math.max(minZ, maxZ));
            final Object probe = cuboidConstructor.newInstance("icesmp_claim_probe", min, max);
            final Object regionSet = managerOverlapMethod.invoke(manager, probe);
            failures.set(0);
            return ((Number) sizeMethod.invoke(regionSet)).intValue() > 0;
        } catch (final Throwable throwable) {
            tripBreaker(throwable);
            return null;
        }
    }

    /** Whether WorldGuard is present and the bridge is currently answering. */
    public static boolean isHealthy() {
        return ready();
    }

    /**
     * A válasz, amikor a híd nem tud kérdezni: WorldGuard NÉLKÜL ez biztos „nincs régió",
     * megszakító alatt viszont „nem tudom" — a hívó dönti el a saját kockázat-irányát.
     */
    private static Boolean unavailableAnswer() {
        return installed ? null : Boolean.FALSE;
    }

    private static boolean breakerOpen() {
        return System.currentTimeMillis() < brokenUntil;
    }

    /**
     * Whether the bridge may answer now. A megszakító lejártakor ÚJRA FELOLDJA a reflexiós
     * láncot: egy WorldGuard-reload érvényteleníti a cache-elt {@code regionQuery}/
     * {@code regionContainer} objektumokat, és azokkal a lejárat után is ugyanaz a hiba jönne —
     * a híd a szerver-újraindításig hibás maradt volna (claim fail-closed, esemény fail-open).
     */
    private static boolean ready() {
        if (!initialised) {
            initialise();
        }
        if (breakerOpen()) {
            return false;
        }
        if (needsReinit.compareAndSet(true, false)) {
            reinitialise();
        }
        return installed;
    }

    /** Eldobja a cache-elt reflexiós láncot, és a következő igényre újra feloldja. */
    private static synchronized void reinitialise() {
        initialised = false;
        installed = false;
        regionContainer = null;
        regionQuery = null;
        adaptLocationMethod = null;
        adaptWorldMethod = null;
        getApplicableRegionsMethod = null;
        sizeMethod = null;
        containerGetMethod = null;
        managerOverlapMethod = null;
        blockVectorAtMethod = null;
        cuboidConstructor = null;
        initialise();
    }

    private static void tripBreaker(final Throwable throwable) {
        brokenUntil = System.currentTimeMillis() + BREAKER_MILLIS;
        needsReinit.set(true);
        final int count = failures.incrementAndGet();
        if (count == 1 || count % LOG_EVERY_FAILURES == 0) {
            Bukkit.getLogger().warning("[IceSMP] WorldGuard-lekérdezés hibázott ("
                    + throwable.getClass().getSimpleName() + ", " + count + ". alkalom) — a híd "
                    + (BREAKER_MILLIS / 1000L) + " másodpercre kikapcsol, majd ÚJRA FELOLDJA a "
                    + "WG-hivatkozásokat (reload után a régiek elavulnak). "
                    + "Amíg hibás, a claim-átfedés ellenőrzés ELUTASÍT (fail-closed).");
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
            Method getRegionContainer;
            try {
                getRegionContainer = Class.forName("com.sk89q.worldguard.internal.platform.WorldGuardPlatform")
                        .getMethod("getRegionContainer");
            } catch (final ClassNotFoundException moved) {
                getRegionContainer = platform.getClass().getMethod("getRegionContainer");
                getRegionContainer.setAccessible(true);
            }
            regionContainer = getRegionContainer.invoke(platform);
            final Class<?> containerClass = Class.forName("com.sk89q.worldguard.protection.regions.RegionContainer");
            regionQuery = containerClass.getMethod("createQuery").invoke(regionContainer);

            final Class<?> bukkitAdapter = Class.forName("com.sk89q.worldguard.bukkit.BukkitAdapter");
            adaptLocationMethod = bukkitAdapter.getMethod("adapt", Location.class);
            final Class<?> weLocation = Class.forName("com.sk89q.worldedit.util.Location");
            getApplicableRegionsMethod = Class.forName("com.sk89q.worldguard.protection.regions.RegionQuery")
                    .getMethod("getApplicableRegions", weLocation);
            sizeMethod = Class.forName("com.sk89q.worldguard.protection.ApplicableRegionSet")
                    .getMethod("size");

            installed = true;
            Bukkit.getLogger().info("[IceSMP] WorldGuard-híd bekapcsolva: a meteor/kincs események kerülik a WG-régiókat.");
        } catch (final Throwable throwable) {
            installed = false;
            Bukkit.getLogger().warning("[IceSMP] WorldGuard jelen van, de a régió-ellenőrző híd nem indult ("
                    + throwable.getClass().getSimpleName() + ") — az események NEM kerülik a WG-régiókat.");
            return;
        }

        // A box-lekérdezés OPCIONÁLIS képesség: ha ez a lánc nem oldódik fel, a pont-lekérdezés
        // akkor is él (a claim-ellenőrzés ilyenkor „nem tudom"-ot kap és elutasít).
        try {
            final Class<?> weWorldClass = Class.forName("com.sk89q.worldedit.world.World");
            adaptWorldMethod = Class.forName("com.sk89q.worldguard.bukkit.BukkitAdapter")
                    .getMethod("adapt", World.class);
            containerGetMethod = Class.forName("com.sk89q.worldguard.protection.regions.RegionContainer")
                    .getMethod("get", weWorldClass);
            final Class<?> blockVectorClass = Class.forName("com.sk89q.worldedit.math.BlockVector3");
            blockVectorAtMethod = blockVectorClass.getMethod("at", int.class, int.class, int.class);
            cuboidConstructor = Class.forName("com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion")
                    .getConstructor(String.class, blockVectorClass, blockVectorClass);
            managerOverlapMethod = Class.forName("com.sk89q.worldguard.protection.managers.RegionManager")
                    .getMethod("getApplicableRegions",
                            Class.forName("com.sk89q.worldguard.protection.regions.ProtectedRegion"));
        } catch (final Throwable throwable) {
            cuboidConstructor = null;
            managerOverlapMethod = null;
            Bukkit.getLogger().warning("[IceSMP] A WorldGuard box-átfedés lekérdezés nem áll rendelkezésre ("
                    + throwable.getClass().getSimpleName() + ") — a claim-átfedés ellenőrzés elutasít, "
                    + "amíg ez nem oldódik meg (fail-closed).");
        }
    }
}
