package hu.taliann.icesmp.trash;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Field;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/** Opt-in Paper/Folia startup and shutdown smoke probe; inert outside the dedicated CI profile. */
public final class TrashProductionRuntimeProbe {

    public static final String PROPERTY = "icesmp.trash-production-runtime";
    public static final String PASS_MARKER = "ICESMP_TRASH_PRODUCTION_RUNTIME_PROBE_PASS";
    public static final String SHUTDOWN_PASS_MARKER =
            "ICESMP_TRASH_PRODUCTION_RUNTIME_SHUTDOWN_PASS";
    public static final String FAIL_MARKER = "ICESMP_TRASH_PRODUCTION_RUNTIME_PROBE_FAIL";
    private static final AtomicReference<ProbeSession> ACTIVE = new AtomicReference<>();

    private TrashProductionRuntimeProbe() { }

    public static void maybeRun(final JavaPlugin plugin, final Object assembledCore) {
        if (!Boolean.getBoolean(PROPERTY)) return;
        final ProbeSession session = new ProbeSession(plugin, assembledCore);
        if (!ACTIVE.compareAndSet(null, session)) {
            plugin.getLogger().severe(FAIL_MARKER + " type=DuplicateProbeSession");
            Bukkit.shutdown();
            return;
        }
        Bukkit.getGlobalRegionScheduler().runDelayed(plugin, task -> {
            try {
                check(Bukkit.getPluginManager().isPluginEnabled(plugin),
                        "plugin is not enabled at probe time");
                final TrashCatalog catalog = readField(assembledCore,
                        "trashCatalog", TrashCatalog.class);
                final TrashItemFactory items = readField(assembledCore,
                        "trashItemFactory", TrashItemFactory.class);
                final TrashRuntimeTelemetry telemetry = readField(assembledCore,
                        "trashRuntimeTelemetry", TrashRuntimeTelemetry.class);

                verifyCatalogAndFactory(catalog, items);
                verifyStartedAndCleanRuntime(assembledCore, telemetry);
                session.startupPassed = true;
                plugin.getLogger().info(PASS_MARKER + " platform="
                        + Bukkit.getServer().getName() + " minecraft="
                        + Bukkit.getMinecraftVersion());
            } catch (final Throwable failure) {
                // Never print the exception message: a malformed hidden identity must not enter logs.
                ACTIVE.compareAndSet(session, null);
                plugin.getLogger().severe(FAIL_MARKER + " type="
                        + failure.getClass().getSimpleName());
            } finally {
                Bukkit.shutdown();
            }
        }, 1L);
    }

    /** Called immediately after the core's Trash shutdown hooks have returned. */
    public static void verifyCleanShutdown(final JavaPlugin plugin, final Object assembledCore) {
        if (!Boolean.getBoolean(PROPERTY)) return;
        final ProbeSession session = ACTIVE.getAndSet(null);
        if (session == null || session.plugin != plugin || session.core != assembledCore
                || !session.startupPassed) {
            plugin.getLogger().severe(FAIL_MARKER + " type=MissingStartupProof");
            return;
        }
        try {
            final Object anomaly = readField(assembledCore,
                    "trashAnomalyRuntime", Object.class);
            final Object relic = readField(assembledCore, "trashRelicRuntime", Object.class);
            final Object archaeology = readField(
                    assembledCore, "trashArchaeologyListener", Object.class);
            final Object tooltip = readField(
                    assembledCore, "trashArchaeologyTooltipBridge", Object.class);
            final Object ambient = readField(assembledCore, "trashAmbientManager", Object.class);
            check(readField(anomaly, "heldTick", Object.class) == null,
                    "Anomaly runtime tick survived shutdown");
            for (final String state : Set.of("activePhysics", "runtimeStateEntities",
                    "pendingEchoes", "pairReservations", "compassProjections")) {
                check(sizeOf(readField(anomaly, state, Object.class)) == 0,
                        "Anomaly state survived shutdown");
            }
            for (final String state : Set.of("fields", "effectVetoArmed", "pendingConsumes",
                    "claimedFields", "trackedProjectiles")) {
                check(sizeOf(readField(relic, state, Object.class)) == 0,
                        "Relic state survived shutdown");
            }
            check(sizeOf(readField(archaeology, "sessions", Object.class)) == 0,
                    "Archaeology session survived shutdown");
            check(sizeOf(readField(tooltip, "overlays", Object.class)) == 0,
                    "Archaeology overlay survived shutdown");
            check(sizeOf(readField(ambient, "active", Object.class)) == 0
                            && sizeOf(readField(ambient, "nextAttemptAt", Object.class)) == 0
                            && sizeOf(readField(ambient, "chunkCounts", Object.class)) == 0,
                    "ambient runtime state survived shutdown");
            plugin.getLogger().info(SHUTDOWN_PASS_MARKER);
        } catch (final Throwable failure) {
            plugin.getLogger().severe(FAIL_MARKER + " type="
                    + failure.getClass().getSimpleName());
        }
    }

    private static void verifyCatalogAndFactory(final TrashCatalog catalog,
                                                final TrashItemFactory items) {
        final Map<String, TrashDefinition> definitions = catalog.snapshot();
        check(definitions.size() == TrashCatalog.BASE_IDENTITY_COUNT,
                "runtime catalog denominator drifted");
        check(catalog.phaseSnapshot().size() == 27,
                "runtime lifecycle phase denominator drifted");
        final EnumMap<TrashKind, Integer> counts = new EnumMap<>(TrashKind.class);
        int position = 0;
        int transformed = 0;
        for (final TrashDefinition definition : definitions.values()) {
            position++;
            counts.merge(definition.internalKind(), 1, Integer::sum);
            final ItemStack first = items.create(definition.id(), 1);
            final ItemStack second = items.create(definition.id(), 1);
            check(first.isSimilar(second) && first.getMaxStackSize() == 64,
                    "fresh stack contract drifted at catalog position " + position);
            check(items.isBaseIdentity(first) && items.isKnownItem(first)
                            && items.idOf(first).orElse("").equals(definition.id()),
                    "factory round trip drifted at catalog position " + position);
            final Set<String> pdcKeys = first.getItemMeta().getPersistentDataContainer()
                    .getKeys().stream().map(NamespacedKey::getKey).collect(Collectors.toSet());
            check(pdcKeys.equals(Set.of("trash_id", "trash_phase")),
                    "fresh physical state is not opaque at catalog position " + position);
            if (!definition.successPhase().isBlank()) {
                final ItemStack phase = items.createPhase(
                        definition.id(), definition.successPhase(), 1);
                check(items.isKnownItem(phase)
                                && items.phaseOf(phase).orElse("")
                                .equals(definition.successPhase()),
                        "lifecycle factory round trip drifted at catalog position " + position);
                transformed++;
            }
        }
        check(counts.equals(Map.of(TrashKind.MUNDANE, 190, TrashKind.STORY, 75,
                        TrashKind.ANOMALY, 42, TrashKind.TRASH_RELIC, 23)),
                "runtime category denominator drifted");
        check(transformed == 29, "runtime lifecycle reference denominator drifted");
    }

    private static void verifyStartedAndCleanRuntime(
            final Object assembledCore, final TrashRuntimeTelemetry telemetry) {
        final Object anomaly = readField(assembledCore, "trashAnomalyRuntime", Object.class);
        final Object relic = readField(assembledCore, "trashRelicRuntime", Object.class);
        final Object archaeology = readField(
                assembledCore, "trashArchaeologyListener", Object.class);
        final Object ambient = readField(assembledCore, "trashAmbientManager", Object.class);
        check(readField(anomaly, "heldTick", Object.class) != null,
                "Anomaly runtime did not start");
        check(sizeOf(readField(relic, "fields", Object.class)) == 0,
                "Relic runtime started with temporary fields");
        check(sizeOf(readField(archaeology, "sessions", Object.class)) == 0,
                "Archaeology runtime started with pending sessions");
        check(sizeOf(readField(ambient, "active", Object.class)) == 0,
                "ambient runtime started with synthetic items");
        final TrashRuntimeTelemetry.Snapshot snapshot = telemetry.snapshot();
        check(snapshot.behaviorRuntimeErrors() == 0L
                        && snapshot.inspectionsStarted() == 0L
                        && snapshot.inspectionsCompleted() == 0L
                        && snapshot.inspectionsCancelled() == 0L,
                "runtime started with non-zero operational counters");
    }

    private static int sizeOf(final Object value) {
        if (value instanceof Map<?, ?> map) return map.size();
        if (value instanceof java.util.Collection<?> collection) return collection.size();
        throw new IllegalStateException("runtime bounded state is not a map/collection");
    }

    private static <T> T readField(final Object owner, final String name,
                                   final Class<T> type) {
        try {
            final Field field = owner.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return type.cast(field.get(owner));
        } catch (final ReflectiveOperationException failure) {
            throw new IllegalStateException("runtime component unavailable", failure);
        }
    }

    private static void check(final boolean condition, final String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private static final class ProbeSession {
        private final JavaPlugin plugin;
        private final Object core;
        private volatile boolean startupPassed;

        private ProbeSession(final JavaPlugin plugin, final Object core) {
            this.plugin = java.util.Objects.requireNonNull(plugin, "plugin");
            this.core = java.util.Objects.requireNonNull(core, "core");
        }
    }
}
