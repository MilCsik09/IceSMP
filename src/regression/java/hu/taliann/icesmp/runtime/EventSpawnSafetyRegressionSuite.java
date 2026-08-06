package hu.taliann.icesmp.runtime;

import hu.taliann.icesmp.gui.ConfigMenuGUI;
import hu.taliann.icesmp.gui.EventSpawnConfigMenuExtension;
import hu.taliann.icesmp.managers.EventSpawnSafetyPolicy;
import org.bukkit.configuration.file.YamlConfiguration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Focused source + pure-policy regressions for immersive world-event placement. */
public final class EventSpawnSafetyRegressionSuite {
    private EventSpawnSafetyRegressionSuite() { }

    public static void main(final String[] args) throws Exception {
        verifiesDynamicPlayerDistancePolicy();
        verifiesVisibilityConePolicy();
        verifiesBoundedCandidateSearch();
        verifiesCircularWaterBuffer();
        verifiesFixedAnchorChunkGeometry();
        verifiesPackagedSafetyProfiles();
        verifiesRuntimeWiring();
        verifiesMeteorRecoveryWiring();
        verifiesConfigMenuExtension();
        verifiesKnownEntityEventPaths();
        System.out.println("Event spawn safety regression suite passed.");
    }

    private static void verifiesDynamicPlayerDistancePolicy() {
        check(EventSpawnSafetyPolicy.effectiveHorizontalMinimum(192, 16, 32, true) == 288.0D,
                "16-chunk send distance plus margin must raise the minimum to 288");
        check(EventSpawnSafetyPolicy.effectiveHorizontalMinimum(320, 10, 32, true) == 320.0D,
                "an explicit larger minimum must win over dynamic view distance");
        check(EventSpawnSafetyPolicy.effectiveHorizontalMinimum(192, 32, 64, false) == 192.0D,
                "event profiles must be able to disable dynamic view distance");

        final UUID world = UUID.randomUUID();
        final EventSpawnSafetyPolicy.PlayerPoint ordinary = new EventSpawnSafetyPolicy.PlayerPoint(
                UUID.randomUUID(), new EventSpawnSafetyPolicy.Point(world, 0, 64, 0),
                false, false, false, 1.0D, 0.0D, 16);
        final EventSpawnSafetyPolicy.Point inside =
                new EventSpawnSafetyPolicy.Point(world, 287.999D, 64, 0);
        final EventSpawnSafetyPolicy.Point boundary =
                new EventSpawnSafetyPolicy.Point(world, 288.0D, 64, 0);
        check(EventSpawnSafetyPolicy.tooCloseToRelevantPlayer(
                inside, List.of(ordinary), 288, 0, true, true, false),
                "inside dynamic minimum must be rejected");
        check(!EventSpawnSafetyPolicy.tooCloseToRelevantPlayer(
                boundary, List.of(ordinary), 288, 0, true, true, false),
                "exact dynamic minimum remains valid");

        final EventSpawnSafetyPolicy.PlayerPoint admin = new EventSpawnSafetyPolicy.PlayerPoint(
                UUID.randomUUID(), ordinary.point(), false, false, true, 1.0D, 0.0D, 16);
        check(EventSpawnSafetyPolicy.tooCloseToRelevantPlayer(
                new EventSpawnSafetyPolicy.Point(world, 20, 64, 0), List.of(admin),
                192, 0, true, true, false),
                "a visible admin must block nearby placement by default");
    }

    private static void verifiesVisibilityConePolicy() {
        final UUID world = UUID.randomUUID();
        final EventSpawnSafetyPolicy.PlayerPoint eastFacing = new EventSpawnSafetyPolicy.PlayerPoint(
                UUID.randomUUID(), new EventSpawnSafetyPolicy.Point(world, 0, 64, 0),
                false, false, false, 1.0D, 0.0D, 20);
        check(EventSpawnSafetyPolicy.visibleInsidePlayerCone(
                new EventSpawnSafetyPolicy.Point(world, 250, 64, 0), List.of(eastFacing),
                384, 110, true, true, false),
                "candidate directly in front of a player must be treated as visible");
        check(!EventSpawnSafetyPolicy.visibleInsidePlayerCone(
                new EventSpawnSafetyPolicy.Point(world, -250, 64, 0), List.of(eastFacing),
                384, 110, true, true, false),
                "candidate behind the player must survive the conservative view-cone check");
        check(!EventSpawnSafetyPolicy.visibleInsidePlayerCone(
                new EventSpawnSafetyPolicy.Point(world, 400, 64, 0), List.of(eastFacing),
                384, 110, true, true, false),
                "view-cone checks must remain bounded");
    }

    private static void verifiesBoundedCandidateSearch() {
        final List<EventSpawnSafetyPolicy.Offset> candidates =
                EventSpawnSafetyPolicy.candidates(32, 288, 512, 42);
        check(candidates.size() == 32, "candidate attempts must be bounded");
        for (final EventSpawnSafetyPolicy.Offset offset : candidates) {
            final double distance = Math.hypot(offset.x(), offset.z());
            check(distance >= 288 - 1.0E-9 && distance <= 512 + 1.0E-9,
                    "candidate escaped its configured annulus");
        }
        check(candidates.equals(EventSpawnSafetyPolicy.candidates(32, 288, 512, 42)),
                "candidate order must remain deterministic");
    }

    private static void verifiesCircularWaterBuffer() {
        final List<EventSpawnSafetyPolicy.GridOffset> radius =
                EventSpawnSafetyPolicy.waterProbeOffsets(8);
        check(radius.getFirst().equals(new EventSpawnSafetyPolicy.GridOffset(0, 0)),
                "water scan must fast-fail from the exact spawn column");
        check(radius.contains(new EventSpawnSafetyPolicy.GridOffset(8, 0))
                        && !radius.contains(new EventSpawnSafetyPolicy.GridOffset(8, 8)),
                "water mask must be circular rather than square");
        check(radius == EventSpawnSafetyPolicy.waterProbeOffsets(8),
                "water masks must be cached and immutable");
        check(EventSpawnSafetyPolicy.waterProbeOffsets(100).stream()
                        .allMatch(offset -> offset.x() * offset.x()
                                + offset.z() * offset.z() <= 32 * 32),
                "water mask radius must stay hard-bounded");
    }

    private static void verifiesFixedAnchorChunkGeometry() {
        for (final int block : List.of(-33, -17, -16, -1, 0, 15, 16, 31, 32, 1_000_003)) {
            final double center = EventSpawnSafetyPolicy.chunkCenterCoordinate(block);
            final int expectedChunk = block >> 4;
            check(((int) Math.floor(center - 8.0D)) >> 4 == expectedChunk,
                    "legacy -8 offset escaped the scheduler chunk at " + block);
            check(((int) Math.floor(Math.nextDown(center + 8.0D))) >> 4 == expectedChunk,
                    "legacy +8 exclusive offset escaped the scheduler chunk at " + block);
        }
    }

    private static void verifiesPackagedSafetyProfiles() {
        final YamlConfiguration config = YamlConfiguration.loadConfiguration(Path.of(
                "src/main/resources/config/event-spawn-safety.yml").toFile());
        check(config.getDouble("world-events.safety.min-horizontal-distance-blocks") >= 192.0D,
                "global player distance default regressed");
        check(config.getBoolean("world-events.placement.dynamic-view-distance-enabled"),
                "dynamic send/view-distance protection must default on");
        check(config.getBoolean("world-events.placement.visibility-cone.enabled"),
                "view-cone protection must default on");
        check(config.getInt("world-events.placement.max-concurrent-searches") == 2,
                "search concurrency default changed unexpectedly");
        check(config.getInt("world-events.placement.max-chunks-per-search") >= 96,
                "distant footprint search lacks a viable chunk budget");
        check(config.getLong("world-events.placement.search-timeout-millis") >= 5000L,
                "async search timeout is too short");
        check(config.getInt("world-events.placement.arrival.delay-seconds") > 0,
                "arrival state must have a visible pre-spawn window");
        check(config.getBoolean("world-events.placement.arrival.player-hint"),
                "arrival state must produce a player-facing directional hint");

        check(config.getBoolean("world-events.water-safety.enabled")
                        && config.getBoolean("world-events.water-safety.enforce-all-events")
                        && config.getInt("world-events.water-safety.buffer-blocks") >= 1,
                "global water and shoreline protection regressed");

        check(config.getDouble("world-events.profiles.stranger.search-min-radius-blocks") >= 64.0D
                        && config.getDouble("world-events.profiles.stranger.search-max-radius-blocks") >= 96.0D,
                "Stranger hidden-local search ring is missing");
        check(!config.getBoolean("world-events.profiles.stranger.use-dynamic-view-distance", true),
                "Stranger must use its intentional local hidden profile");
        check(!config.getBoolean("world-events.profiles.player-caravan.arrival.enabled", true),
                "paid player caravan must not be held in a cosmetic arrival delay");
        for (final String internal : List.of("escort-route", "escort-wave")) {
            check(config.getDouble("world-events.profiles." + internal
                            + ".min-horizontal-distance-blocks", -1.0D) == 0.0D,
                    internal + " must remain usable after players reach the event");
            check(!config.getBoolean("world-events.profiles." + internal
                            + ".use-dynamic-view-distance", true),
                    internal + " must not inherit the global distance gate");
        }
        check(!config.isSet("world-events.placement.generate-unloaded-chunks"),
                "event placement must never expose world generation as an option");
    }

    private static void verifiesRuntimeWiring() throws Exception {
        final String guard = read("src/main/java/hu/taliann/icesmp/managers/EventSpawnGuard.java");
        check(guard.contains("player.getSendViewDistance()")
                        && guard.contains("visibleInsidePlayerCone")
                        && guard.contains("pendingArrivals")
                        && guard.contains("completeSearchPhase")
                        && guard.contains("arrival-revalidation")
                        && guard.contains("findSafeRoute")
                        && guard.contains("footprintAllowed")
                        && guard.contains("final BlockReason surface = surfaceReason(key, world")
                        && guard.contains("recentLocations")
                        && guard.contains("allowed-biomes")
                        && guard.contains("getChunkAtAsync(chunkX, chunkZ, false)")
                        && guard.contains("max-concurrent-searches")
                        && guard.contains("search-timeout-millis")
                        && guard.contains("timeoutTicks")
                        && guard.contains("GlobalRegionScheduler().runDelayed")
                        && guard.contains("max-chunks-per-search")
                        && guard.contains("EventSpawnDebugListener")
                        && guard.contains("EventSpawnConfigMenuExtension.install"),
                "central guard lost one or more immersive placement guarantees");
        check(!guard.contains("generate-unloaded-chunks"),
                "runtime must not allow event searches to generate terrain");

        final String snapshots = read(
                "src/main/java/hu/taliann/icesmp/listeners/EventSpawnGuardListener.java");
        check(snapshots.contains("getYaw()") && snapshots.contains("getPitch()")
                        && snapshots.contains("changedPositionOrDirection"),
                "look-direction snapshots must refresh when the player turns");

        final String debug = read(
                "src/main/java/hu/taliann/icesmp/listeners/EventSpawnDebugListener.java");
        check(debug.contains("/events debug spawn <event-kulcs>")
                        && debug.contains("guard.debugSearch"),
                "admin spawn diagnostics are not wired");
    }

    private static void verifiesMeteorRecoveryWiring() throws Exception {
        final String meteor = read(
                "src/main/java/hu/taliann/icesmp/managers/MeteorEventManager.java");
        check(meteor.contains("meteor-restore.yml")
                        && meteor.contains("persistRecovery")
                        && meteor.contains("recoverInterruptedCrater")
                        && meteor.contains("instanceof TileState")
                        && meteor.contains("getAsString()")
                        && meteor.contains("Bukkit.createBlockData")
                        && meteor.contains("scheduleRestore")
                        && meteor.contains("Bukkit.isOwnedByCurrentRegion")
                        && meteor.contains("getRegionScheduler().run")
                        && meteor.contains("Files.deleteIfExists")
                        && meteor.contains("YamlStore.loadTracked")
                        && meteor.contains("CorruptStateFileError")
                        && meteor.contains("Long.MAX_VALUE"),
                "meteor crater lost durable, per-region restoration");
        check(!meteor.contains("List<BlockState>")
                        && !meteor.contains("for (final BlockState state : states)"),
                "meteor must not replay cross-region BlockState snapshots from one task");
        final int activeLifecycle = meteor.indexOf("if (isActive())");
        final int startupRecovery = meteor.indexOf("recoverInterruptedCrater()");
        check(activeLifecycle >= 0 && startupRecovery > activeLifecycle,
                "active meteor expiry must run before startup recovery-file gating");
    }

    private static void verifiesConfigMenuExtension() throws Exception {
        EventSpawnConfigMenuExtension.install();
        final Set<String> keys = ConfigMenuGUI.allEntries().stream()
                .map(ConfigMenuGUI.Entry::key).collect(Collectors.toSet());
        for (final String required : List.of(
                "world-events.water-safety.enabled",
                "world-events.water-safety.enforce-all-events",
                "world-events.water-safety.buffer-blocks",
                "world-events.placement.dynamic-view-distance-enabled",
                "world-events.placement.visibility-cone.enabled",
                "world-events.placement.max-concurrent-searches",
                "world-events.placement.search-timeout-millis",
                "world-events.placement.route-attempts",
                "world-events.placement.arrival.player-hint")) {
            check(keys.contains(required), "config GUI extension missing: " + required);
        }
        for (final ConfigMenuGUI.Category category : ConfigMenuGUI.CATEGORIES.values()) {
            check(category.entries().size() <= 45,
                    "config GUI category capacity exceeded: " + category.id());
        }
        final String extension = read(
                "src/main/java/hu/taliann/icesmp/gui/EventSpawnConfigMenuExtension.java");
        check(extension.contains("OVERFLOW_CATEGORY")
                        && extension.contains("Event spawn-védelem"),
                "GUI extension must remain compatible with concurrent menu expansion");
    }

    private static void verifiesKnownEntityEventPaths() throws Exception {
        final String escort = read("src/main/java/hu/taliann/icesmp/managers/EscortManager.java");
        check(escort.contains("findSafeAtOrNear(\"escort\"")
                        && escort.contains("findSafeNear(\"escort\"")
                        && escort.contains("findSafeRoute(\"escort\"")
                        && escort.contains("resolveSafeStandingLocation(\n                    \"escort-wave\"")
                        && escort.contains("isBlocked(\"escort-route\"")
                        && escort.contains("force-use-player-anchor\", false")
                        && !escort.contains("spawnConvoy(base)"),
                "escort may bypass distant start, route or internal-wave profiles");

        final String stranger = read(
                "src/main/java/hu/taliann/icesmp/managers/StrangerNpcManager.java");
        check(stranger.contains("findSafeNear(\"stranger\"")
                        && stranger.contains("this::placeStranger")
                        && !stranger.contains("nextDouble(Math.PI * 2.0D)"),
                "Stranger still relies on a single direct random placement");

        final String spawnPoints = read(
                "src/main/java/hu/taliann/icesmp/managers/EventSpawnPointManager.java");
        check(spawnPoints.contains("chunkCenterCoordinate")
                        && spawnPoints.contains("\"world-boss\".equals(eventKey)"),
                "world-boss fixed anchors may escape their Folia scheduler chunk");

        final String ambient = read(
                "src/main/java/hu/taliann/icesmp/managers/AmbientEventManager.java");
        check(ambient.contains("findSafeNear(\"animal-migration\"")
                        && ambient.contains("resolveSafeStandingLocation(\"animal-migration\""),
                "animal migration lost distant dry placement");

        final String cultists = read(
                "src/main/java/hu/taliann/icesmp/managers/CultistEventManager.java");
        check(cultists.contains("findSafeNear(\"cultists\"")
                        && cultists.contains("findSafeAtOrNear(\"cultists\""),
                "cultist center placement lost the central search");

        assertContains("WildHuntManager.java", "findSafeNear(\"wild-hunt\"");
        assertContains("MeteorEventManager.java", "findSafeNear(\"meteor\"");
        assertContains("InvasionManager.java", "findSafeNear(\"invasion\"");
        assertContains("WorldBossManager.java", "findSafeNear(\"world-boss\"");
        assertContains("DarkUndeadAmbienceManager.java", "isBlocked(\"dark-undead\"");
    }

    private static void assertContains(final String file, final String marker) throws Exception {
        final String source = read("src/main/java/hu/taliann/icesmp/managers/" + file);
        check(source.contains(marker), "known event path lost central guard: " + file);
    }

    private static String read(final String path) throws Exception {
        return Files.readString(Path.of(path));
    }

    private static void check(final boolean condition, final String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
