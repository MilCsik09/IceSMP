package hu.taliann.icesmp.integration;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

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
    /** Mindkét lekérdezési út ugyanazt az adapter-FQCN-t használja. */
    private static final String BUKKIT_ADAPTER_CLASS =
            "com.sk89q.worldedit.bukkit.BukkitAdapter";

    /**
     * A feloldott reflexiós lánc IMMUTABLE pillanatképe. A lekérdezések ezt kapják meg egy
     * darabban, ezért sosem láthatnak félig kiürített mezőket egy újra-feloldás közben — a
     * korábbi, mezőnkénti nullázás alatt egy másik régió-szál „nincs régió"-t kapott volna,
     * azaz a claim-ellenőrzés rövid időre fail-OPEN lett.
     */
    private record Chain(Object regionContainer, Object regionQuery, Method adaptLocation,
                         Method adaptWorld, Method applicableRegions, Method size,
                         Method containerGet, Method managerOverlap, Method blockVectorAt,
                         Constructor<?> cuboid) {
    }

    /** null = még nem (vagy már nem) feloldott lánc. */
    private static volatile Chain chain;
    /** A WorldGuard plugin nincs jelen — ez NEM hiba, hanem biztos „nincs régió". */
    private static volatile boolean absent;
    /** Amíg ez a jövőben van, nem kérdezünk (megszakító nyitva). */
    private static volatile long brokenUntil;
    private static final AtomicInteger failures = new AtomicInteger();

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
        final Chain resolved = acquire();
        if (resolved == null) {
            return unavailableAnswer();
        }
        try {
            final Object adapted = resolved.adaptLocation().invoke(null, location);
            final Object regionSet = resolved.applicableRegions().invoke(resolved.regionQuery(), adapted);
            failures.set(0);
            return ((Number) resolved.size().invoke(regionSet)).intValue() > 0;
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
        final Chain resolved = acquire();
        if (resolved == null) {
            return unavailableAnswer();
        }
        if (resolved.cuboid() == null || resolved.managerOverlap() == null) {
            // Régebbi/átalakított WG-API: a box-lekérdezés nem áll rendelkezésre.
            return null;
        }
        try {
            final Object weWorld = resolved.adaptWorld().invoke(null, world);
            final Object manager = resolved.containerGet().invoke(resolved.regionContainer(), weWorld);
            if (manager == null) {
                // RegionContainer#get akkor is null lehet, ha a régiótámogatás ki van
                // kapcsolva vagy a régióadat betöltése hibázott. Claimnél ez UNKNOWN.
                return null;
            }
            final Object min = resolved.blockVectorAt().invoke(null,
                    Math.min(minX, maxX), world.getMinHeight(), Math.min(minZ, maxZ));
            final Object max = resolved.blockVectorAt().invoke(null,
                    Math.max(minX, maxX), world.getMaxHeight() - 1, Math.max(minZ, maxZ));
            final Object probe = resolved.cuboid().newInstance("icesmp_claim_probe", min, max);
            final Object regionSet = resolved.managerOverlap().invoke(manager, probe);
            failures.set(0);
            return ((Number) resolved.size().invoke(regionSet)).intValue() > 0;
        } catch (final Throwable throwable) {
            tripBreaker(throwable);
            return null;
        }
    }

    /** Whether WorldGuard is present and the bridge is currently answering. */
    public static boolean isHealthy() {
        return acquire() != null;
    }

    /**
     * A válasz, amikor a híd nem tud kérdezni: WorldGuard NÉLKÜL ez biztos „nincs régió",
     * megszakító alatt vagy sikertelen feloldás után viszont „nem tudom" — a hívó dönti el a
     * saját kockázat-irányát (a claim elutasít, az esemény-spawn átmegy).
     */
    private static Boolean unavailableAnswer() {
        return absent ? Boolean.FALSE : null;
    }

    /**
     * A használható lánc, vagy null ha most nem tudunk kérdezni. EGY zár alatt dönt és old fel,
     * ezért nincs félig-kész állapot; a sikertelen feloldás ÚJ backoffot nyit, tehát a híd nem
     * ragad be véglegesen fail-open állapotba (a régi kód egyetlen bukott újra-inicializálás
     * után szerver-újraindításig hallgatott).
     */
    private static synchronized Chain acquire() {
        if (System.currentTimeMillis() < brokenUntil) {
            return null;
        }
        final Chain existing = chain;
        if (existing != null) {
            return existing;
        }
        final org.bukkit.plugin.Plugin worldGuard =
                Bukkit.getPluginManager().getPlugin("WorldGuard");
        if (worldGuard == null) {
            absent = true;
            return null;
        }
        absent = false;
        if (!worldGuard.isEnabled()) {
            // Telepítve, de letiltva/enable-hibásan nem azonos a valódi hiánnyal:
            // a claim-ellenőrzés ilyenkor UNKNOWN és fail-closed marad.
            brokenUntil = System.currentTimeMillis() + BREAKER_MILLIS;
            return null;
        }
        final Chain resolved = resolveChain();
        if (resolved == null) {
            // Sikertelen feloldás: új backoff, hogy a következő ablakban ÚJRA próbáljuk.
            brokenUntil = System.currentTimeMillis() + BREAKER_MILLIS;
            return null;
        }
        chain = resolved;
        return resolved;
    }

    private static void tripBreaker(final Throwable throwable) {
        brokenUntil = System.currentTimeMillis() + BREAKER_MILLIS;
        // A lánc eldobása: a következő ablakban újra feloldjuk. Egy WorldGuard-reload
        // érvényteleníti a cache-elt regionQuery/regionContainer objektumokat.
        chain = null;
        final int count = failures.incrementAndGet();
        if (count == 1 || count % LOG_EVERY_FAILURES == 0) {
            final String message = "[IceSMP] WorldGuard-lekérdezés hibázott ("
                    + failureDescription(throwable) + ", " + count + ". alkalom) — a híd "
                    + (BREAKER_MILLIS / 1000L) + " másodpercre kikapcsol, majd ÚJRA FELOLDJA a "
                    + "WG-hivatkozásokat. Amíg nem tud válaszolni, a claim-átfedés ellenőrzés "
                    + "ELUTASÍT (fail-closed).";
            if (count == 1) {
                Bukkit.getLogger().log(Level.WARNING, message, throwable);
            } else {
                Bukkit.getLogger().warning(message);
            }
        }
    }

    /** Rövid, egysoros ok a logfejlécbe; az első hiba teljes stack trace-t is kap. */
    private static String failureDescription(final Throwable throwable) {
        Throwable root = throwable;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        final String detail = root.getMessage();
        if (detail == null || detail.isBlank()) {
            return root.getClass().getSimpleName();
        }
        return root.getClass().getSimpleName() + ": "
                + detail.replace('\n', ' ').replace('\r', ' ');
    }

    /** @return a feloldott lánc, vagy null ha a reflexiós feloldás nem sikerült */
    private static Chain resolveChain() {
        final Object container;
        final Object query;
        final Method adaptLocation;
        final Method applicableRegions;
        final Method size;
        final Class<?> bukkitAdapter;
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
            container = getRegionContainer.invoke(platform);
            final Class<?> containerClass = Class.forName("com.sk89q.worldguard.protection.regions.RegionContainer");
            query = containerClass.getMethod("createQuery").invoke(container);

            bukkitAdapter = Class.forName(BUKKIT_ADAPTER_CLASS);
            adaptLocation = bukkitAdapter.getMethod("adapt", Location.class);
            final Class<?> weLocation = Class.forName("com.sk89q.worldedit.util.Location");
            applicableRegions = Class.forName("com.sk89q.worldguard.protection.regions.RegionQuery")
                    .getMethod("getApplicableRegions", weLocation);
            size = Class.forName("com.sk89q.worldguard.protection.ApplicableRegionSet")
                    .getMethod("size");
        } catch (final Throwable throwable) {
            Bukkit.getLogger().log(Level.WARNING,
                    "[IceSMP] WorldGuard jelen van, de a régió-ellenőrző híd nem oldható fel ("
                            + failureDescription(throwable) + ") — újrapróbálás "
                            + (BREAKER_MILLIS / 1000L)
                            + " másodperc múlva; addig a claim-ellenőrzés elutasít.",
                    throwable);
            return null;
        }

        // A box-lekérdezés a támogatott WG7 szerződés része. Ha nem oldható fel, az egész
        // láncot eldobjuk: az események fail-open, a claimek fail-closed irányban döntenek,
        // a breaker lejárta után pedig a teljes híd újra feloldódik.
        Method adaptWorld = null;
        Method containerGet = null;
        Method blockVectorAt = null;
        Constructor<?> cuboid = null;
        Method managerOverlap = null;
        try {
            final Class<?> weWorldClass = Class.forName("com.sk89q.worldedit.world.World");
            adaptWorld = bukkitAdapter.getMethod("adapt", World.class);
            containerGet = Class.forName("com.sk89q.worldguard.protection.regions.RegionContainer")
                    .getMethod("get", weWorldClass);
            final Class<?> blockVectorClass = Class.forName("com.sk89q.worldedit.math.BlockVector3");
            blockVectorAt = blockVectorClass.getMethod("at", int.class, int.class, int.class);
            cuboid = Class.forName("com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion")
                    .getConstructor(String.class, blockVectorClass, blockVectorClass);
            managerOverlap = Class.forName("com.sk89q.worldguard.protection.managers.RegionManager")
                    .getMethod("getApplicableRegions",
                            Class.forName("com.sk89q.worldguard.protection.regions.ProtectedRegion"));
        } catch (final Throwable throwable) {
            Bukkit.getLogger().log(Level.WARNING,
                    "[IceSMP] A WorldGuard box-átfedés lekérdezés nem oldható fel ("
                            + failureDescription(throwable)
                            + ") — a claim-átfedés ellenőrzés elutasít (fail-closed).",
                    throwable);
            return null;
        }
        failures.set(0);
        Bukkit.getLogger().info("[IceSMP] WorldGuard-híd feloldva: a claim- és esemény-ellenőrzés él.");
        return new Chain(container, query, adaptLocation, adaptWorld, applicableRegions, size,
                containerGet, managerOverlap, blockVectorAt, cuboid);
    }
}
