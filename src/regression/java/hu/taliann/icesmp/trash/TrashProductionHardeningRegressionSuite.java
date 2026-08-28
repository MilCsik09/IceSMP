package hu.taliann.icesmp.trash;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Source and pure-domain gates for the automatable part of Phase G. */
public final class TrashProductionHardeningRegressionSuite {

    private static final Path ROOT = Path.of("src/main/java/hu/taliann/icesmp");
    private static final Path TRASH = ROOT.resolve("trash");
    private static final Path CORE = ROOT.resolve("core/IceSMPCore.java");
    private static final Path STAGING =
            Path.of("docs/development/trash-production-staging.json");

    private TrashProductionHardeningRegressionSuite() { }

    public static void main(final String[] args) throws Exception {
        preservesAggregateOnlyRuntimeTelemetry();
        preservesPerformanceHardCaps();
        preservesCrashAndReloadCleanup();
        preservesHardcodedSecurityBoundary();
        preservesDistributionAndStackedRegressionGates();
        refusesToForgeRuntimeOrHumanEvidence();
        System.out.println("Trash production hardening regression suite passed.");
    }

    private static void preservesAggregateOnlyRuntimeTelemetry() throws Exception {
        final TrashRuntimeTelemetry telemetry = new TrashRuntimeTelemetry();
        telemetry.recordBehaviorRuntimeError();
        telemetry.recordInspectionStarted();
        telemetry.recordInspectionCompleted();
        telemetry.recordInspectionCancelled();
        telemetry.recordArchaeologyUnlock();
        telemetry.recordTooltipTextFallback();
        final TrashRuntimeTelemetry.Snapshot snapshot = telemetry.snapshot();
        check(snapshot.behaviorRuntimeErrors() == 1L
                        && snapshot.inspectionsStarted() == 1L
                        && snapshot.inspectionsCompleted() == 1L
                        && snapshot.inspectionsCancelled() == 1L
                        && snapshot.archaeologyUnlocks() == 1L
                        && snapshot.tooltipTextFallbacks() == 1L,
                "aggregate telemetry counter semantics drifted");

        final String source = source("TrashRuntimeTelemetry.java");
        require(source, "LongAdder", "contention-safe aggregate counters");
        check(occurrences(source, "new LongAdder()") == 6,
                "telemetry field set must remain intentionally bounded");
        for (final String forbidden : List.of("java.util.UUID", "org.bukkit", "Map<",
                "ItemStack", "Player", "TrashDefinition", "TrashAnomalyBehavior",
                "TrashRelicBehavior", "getLogger()", "System.out", "System.err")) {
            check(!source.contains(forbidden),
                    "telemetry retained or emitted sensitive runtime detail: " + forbidden);
        }

        final String anomaly = source("TrashAnomalyRuntime.java");
        final String relic = source("TrashRelicRuntime.java");
        final String archaeology = source("TrashArchaeologyListener.java");
        check(occurrences(anomaly, "telemetry.recordBehaviorRuntimeError()") >= 7,
                "Anomaly fail-closed runtime errors are not observable in aggregate");
        check(occurrences(relic, "telemetry.recordBehaviorRuntimeError()") >= 3,
                "Relic fail-closed runtime errors are not observable in aggregate");
        require(archaeology, "recordInspectionStarted()", "inspection start telemetry");
        require(archaeology, "recordInspectionCompleted()", "inspection completion telemetry");
        require(archaeology, "recordInspectionCancelled()", "inspection cancellation telemetry");
        require(archaeology, "recordTooltipTextFallback()", "tooltip fallback telemetry");

        final String factory = source("TrashItemFactory.java");
        require(factory, "LEGACY.deserialize(legacyColor + text)",
                "single-pass Adventure legacy-color decoding");
        check(!factory.contains("TextUtil.color("),
                "factory pre-expanded ampersand colors into literal section codes");
    }

    private static void preservesPerformanceHardCaps() throws Exception {
        final String anomaly = source("TrashAnomalyRuntime.java");
        require(anomaly, "MAX_ACTIVE_PHYSICS_PER_WORLD = 256", "physics world cap");
        require(anomaly, "MAX_PENDING_ECHOES = 256", "pending echo cap");
        require(anomaly, "MAX_SEEK_RADIUS = 12.0D", "attraction radius cap");
        require(anomaly, "MAX_NEARBY_ENTITIES = 24", "candidate scan cap");
        check(!anomaly.contains("Bukkit.getWorlds()")
                        && !anomaly.contains("getWorld().getEntities()"),
                "Anomaly hot runtime gained a global world/entity scan");

        final String relic = source("TrashRelicRuntime.java");
        require(relic, "MAX_FIELDS_PER_WORLD = 32", "temporary field world cap");
        require(relic, "MAX_FIELDS_GLOBAL = 128", "temporary field global cap");
        require(relic, "MAX_NEARBY_ENTITIES = 24", "Relic candidate scan cap");
        check(!relic.contains("Bukkit.getWorlds()")
                        && !relic.contains("getWorld().getEntities()"),
                "Relic hot runtime gained a global world/entity scan");

        final String fracture = source("TrashSpatialFractureStore.java");
        require(fracture, "MAX_OPEN = 32", "temporary fracture server cap");
        require(fracture, "MAX_OPEN_PER_PLAYER = 2", "temporary fracture player cap");

        final String ambient = Files.readString(
                Path.of("src/main/resources/content/trash/catalog.yml"));
        require(ambient, "max-per-chunk: 1", "ambient chunk cap");
        require(ambient, "max-per-neighborhood: 4", "ambient 3x3 cap");
        final String ambientRuntime = source("TrashAmbientManager.java");
        require(ambientRuntime, "for (int dx = -1; dx <= 1; dx++)",
                "bounded 3x3 density index");
        require(ambientRuntime, "for (int dz = -1; dz <= 1; dz++)",
                "bounded 3x3 density index");

        final String listener = source("TrashArchaeologyListener.java");
        require(listener, "ConcurrentMap<UUID, Session>", "one keyed session authority");
        require(listener, "INSPECTION_TICKS = 30", "bounded inspection duration");
        check(!listener.contains("getStorageContents()") && !listener.contains("getContents()"),
                "Archaeology gained a per-tick full inventory scan");
        for (final String runtime : List.of(anomaly, relic, listener, ambientRuntime)) {
            check(!runtime.contains("YamlConfiguration") && !runtime.contains("Files.")
                            && !runtime.contains("new File("),
                    "runtime hot path gained filesystem/YAML parsing");
        }
    }

    private static void preservesCrashAndReloadCleanup() throws Exception {
        final String core = Files.readString(CORE);
        for (final String token : List.of("trashAmbientManager.start()",
                "trashAnomalyRuntime.start()", "trashRelicRuntime.start()",
                "trashRelicRuntime::shutdown", "trashAnomalyRuntime::shutdown",
                "trashArchaeologyListener::shutdown", "trashAmbientManager::shutdown",
                "storeCoordinator.saveForShutdown")) {
            require(core, token, "Trash lifecycle boundary");
        }
        check(core.indexOf("trashArchaeologyListener::shutdown")
                        < core.indexOf("storeCoordinator.saveForShutdown")
                        && core.indexOf("storeCoordinator.saveForShutdown")
                        < core.indexOf("playerProfilePlatform.shutdown"),
                "Trash cleanup/save/Profile authority shutdown order drifted");

        final String anomaly = source("TrashAnomalyRuntime.java");
        require(anomaly, "releasePhysics(event.getEntity().getUniqueId())",
                "despawn physics release uses the entity UUID");
        require(anomaly, "item.setGravity(true)", "synthetic gravity restoration");
        require(anomaly, "pendingEchoes.clear()", "pending echo cleanup");
        require(anomaly, "tosses.shutdown()", "synthetic toss task cleanup");

        final String fracture = source("TrashSpatialFractureStore.java");
        require(fracture, "public synchronized void recover()", "fracture restart recovery");
        require(fracture, "YamlStore.saveAtomic(file, yaml)", "atomic fracture journal");
        require(source("TrashRelicRuntime.java"), "fractures.recover()",
                "fracture recovery startup wiring");
        require(source("TrashHistoryStore.java"), "YamlStore.saveAtomic(file, yaml)",
                "atomic history persistence");
        require(source("TrashRecyclePool.java"), "YamlStore.saveAtomic(file, yaml)",
                "atomic recycle persistence");
        require(source("TrashArchaeologyListener.java"), "tooltip.shutdown()",
                "tooltip/session reload cleanup");
        final String probe = source("TrashProductionRuntimeProbe.java");
        require(probe, "Bukkit.shutdown()", "runtime smoke clean shutdown");
        require(Files.readString(ROOT.resolve("IceSMP.java")),
                "TrashProductionRuntimeProbe.maybeRun(this, core)",
                "runtime probe bootstrap wiring");
        check(!probe.contains("failure.printStackTrace()")
                        && !probe.contains("failure.getMessage()"),
                "runtime probe could leak a hidden malformed value into normal logs");
    }

    private static void preservesHardcodedSecurityBoundary() throws Exception {
        final String authority = Files.readString(ROOT.resolve("security/HiddenDevAuthority.java"));
        final String developerId = "2d47d7b6-294e-4a14-922c-befacd66ee6d";
        check(occurrences(authority, developerId) == 1,
                "production hidden-content allowlist must contain exactly one explicit UUID");
        require(authority, "Set.of(PRIMARY_DEVELOPER)", "immutable single-principal allowlist");
        check(!authority.contains("hasPermission(") && !authority.contains("isOp()"),
                "normal permission or OP entered hidden authority");

        final String command = Files.readString(ROOT.resolve("commands/IceSMPCommand.java"));
        final int executeGate = command.indexOf("\"dev\".equalsIgnoreCase(args[0])"
                + " && HiddenDevAuthority.mayUseHiddenContent(sender)");
        final int execute = command.indexOf("trashDevCommand.execute", executeGate);
        check(executeGate >= 0 && execute > executeGate,
                "hidden command does not fail closed before delegate parsing");
        final int suggestGate = command.indexOf(
                "if (!HiddenDevAuthority.mayUseHiddenContent(sender)) return List.of();");
        final int suggest = command.indexOf("trashDevCommand.suggest", suggestGate);
        check(suggestGate >= 0 && suggest > suggestGate,
                "hidden tab completion is visible before authority rejection");

        final String dev = source("TrashDevCommand.java");
        check(!dev.contains("hasPermission(") && !dev.contains("isOp()"),
                "hidden DEV route gained an ordinary admin bypass");
        for (final Path publicSurface : List.of(Path.of("README.md"), Path.of("ROADMAP.md"),
                Path.of("docs/admin"))) {
            if (Files.isDirectory(publicSurface)) {
                try (var files = Files.walk(publicSurface)) {
                    check(files.filter(Files::isRegularFile).noneMatch(path -> contains(path, developerId)),
                            "hardcoded developer identity leaked into admin documentation");
                }
            } else {
                check(!Files.readString(publicSurface).contains(developerId),
                        "hardcoded developer identity leaked into public documentation");
            }
        }
    }

    private static void preservesDistributionAndStackedRegressionGates() throws Exception {
        final String distribution = Files.readString(Path.of(
                "src/regression/java/hu/taliann/icesmp/trash/TrashLootDistributionRegressionSuite.java"));
        require(distribution, "TRIALS_PER_SOURCE = 10_000_000", "10M per-source simulation");
        require(distribution, "for (final TrashLootSource source : TrashLootSource.values())",
                "all source simulation");
        require(distribution, "verifyContextBias(selector)", "context-bias simulation");
        require(distribution, "selection.displaced()", "displaced-roll simulation");
        require(distribution, "recycleSubstitutionChance()", "recycle substitution simulation");

        final String gradle = Files.readString(Path.of("build.gradle.kts"));
        for (final String task : List.of("trashCatalogRegressionTest",
                "trashLootDistributionRegressionTest", "trashHistoryRegressionTest",
                "trashAnomalyRegressionTest", "trashRelicRegressionTest",
                "trashArchaeologyRegressionTest", "trashProductionHardeningRegressionTest")) {
            require(gradle, task, "stacked Gradle check task");
        }
        require(gradle, "tasks.check", "full check wiring");
    }

    private static void refusesToForgeRuntimeOrHumanEvidence() throws Exception {
        final String staging = Files.readString(STAGING);
        require(staging, "\"production_ready\": false", "honest production status");
        for (final String status : List.of("AUTOMATED_SMOKE_ENFORCED_BY_CI",
                "CLIENT_EVIDENCE_REQUIRED", "GAMEPLAY_STAGING_REQUIRED",
                "LOAD_STAGING_REQUIRED")) {
            require(staging, status, "unverified staging status");
        }
        require(staging, "real-player interaction evidence required",
                "Paper interaction evidence remains explicit");
        require(staging, "real-player cross-region interaction evidence required",
                "Folia interaction evidence remains explicit");
        require(staging, "\"minimum_duration_hours\": 2", "minimum human staging duration");
        require(staging, "\"target_duration_hours\": 4", "target human staging duration");
        require(staging, "\"target_players_min\": 50", "minimum load target");
        require(staging, "\"target_players_max\": 60", "maximum load target");
        check(!staging.contains("RUNTIME_PASS") && !staging.contains("HUMAN_PASS"),
                "unexecuted runtime/human staging was forged as PASS");

        final String workflow = Files.readString(Path.of(
                ".github/workflows/trash-production-hardening.yml"));
        require(workflow, "trashProductionHardeningRegressionTest",
                "Phase G CI hardening task");
        require(workflow, "trashLootDistributionRegressionTest",
                "Phase G CI Monte Carlo task");
        require(workflow, "runServer", "real Paper startup smoke task");
        require(workflow, "runFolia", "real Folia startup smoke task");
        require(workflow, "ICESMP_TRASH_PRODUCTION_RUNTIME_PROBE_PASS",
                "runtime proof marker enforcement");
    }

    private static boolean contains(final Path path, final String token) {
        try {
            return Files.readString(path).contains(token);
        } catch (final Exception failure) {
            throw new IllegalStateException("cannot inspect " + path, failure);
        }
    }

    private static String source(final String file) throws Exception {
        return Files.readString(TRASH.resolve(file));
    }

    private static int occurrences(final String source, final String token) {
        int count = 0;
        int offset = 0;
        while ((offset = source.indexOf(token, offset)) >= 0) {
            count++;
            offset += token.length();
        }
        return count;
    }

    private static void require(final String source, final String token,
                                final String description) {
        check(source.contains(token), "missing " + description + ": " + token);
    }

    private static void check(final boolean condition, final String message) {
        if (!condition) throw new AssertionError(message);
    }
}
