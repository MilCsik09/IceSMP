package hu.taliann.icesmp.runtime;

import hu.taliann.icesmp.managers.EventSpawnSafetyPolicy;
import org.bukkit.configuration.file.YamlConfiguration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

public final class EventSpawnSafetyRegressionSuite {
    private EventSpawnSafetyRegressionSuite() { }

    public static void main(final String[] args) throws Exception {
        verifiesPlayerDistancePolicy();
        verifiesBoundedCandidateSearch();
        verifiesCircularWaterBuffer();
        verifiesSafetyDefaultsAndRuntimeWiring();
        verifiesEveryKnownEntityEventUsesGuardedPlacement();
        System.out.println("Event spawn safety regression suite passed.");
    }

    private static void verifiesPlayerDistancePolicy() {
        final UUID world = UUID.randomUUID();
        final EventSpawnSafetyPolicy.PlayerPoint player = new EventSpawnSafetyPolicy.PlayerPoint(
                UUID.randomUUID(), new EventSpawnSafetyPolicy.Point(world, 0, 64, 0),
                false, false, false);
        check(EventSpawnSafetyPolicy.tooCloseToRelevantPlayer(
                new EventSpawnSafetyPolicy.Point(world, 191.999, 64, 0), List.of(player),
                192, 0, true, true, false),
                "inside 12-chunk horizontal minimum rejected");
        check(!EventSpawnSafetyPolicy.tooCloseToRelevantPlayer(
                new EventSpawnSafetyPolicy.Point(world, 192, 64, 0), List.of(player),
                192, 0, true, true, false),
                "exact minimum accepted");

        final EventSpawnSafetyPolicy.PlayerPoint second = new EventSpawnSafetyPolicy.PlayerPoint(
                UUID.randomUUID(), new EventSpawnSafetyPolicy.Point(world, 400, 64, 0),
                false, false, false);
        check(EventSpawnSafetyPolicy.tooCloseToRelevantPlayer(
                new EventSpawnSafetyPolicy.Point(world, 250, 64, 0), List.of(player, second),
                192, 0, true, true, false),
                "nearest of multiple players enforced");

        final EventSpawnSafetyPolicy.PlayerPoint admin = new EventSpawnSafetyPolicy.PlayerPoint(
                UUID.randomUUID(), new EventSpawnSafetyPolicy.Point(world, 0, 64, 0),
                false, false, true);
        check(EventSpawnSafetyPolicy.tooCloseToRelevantPlayer(
                new EventSpawnSafetyPolicy.Point(world, 20, 64, 0), List.of(admin),
                192, 0, true, true, false),
                "visible admin must block nearby event placement");
        check(!EventSpawnSafetyPolicy.tooCloseToRelevantPlayer(
                new EventSpawnSafetyPolicy.Point(world, 20, 64, 0), List.of(admin),
                192, 0, true, true, true),
                "explicit admin-ignore switch remains deterministic");

        final EventSpawnSafetyPolicy.PlayerPoint spectator = new EventSpawnSafetyPolicy.PlayerPoint(
                UUID.randomUUID(), new EventSpawnSafetyPolicy.Point(world, 0, 64, 0),
                true, false, false);
        check(!EventSpawnSafetyPolicy.tooCloseToRelevantPlayer(
                new EventSpawnSafetyPolicy.Point(world, 1, 64, 0), List.of(spectator),
                192, 0, true, true, false),
                "spectator ignored by policy");
        final EventSpawnSafetyPolicy.PlayerPoint vanished = new EventSpawnSafetyPolicy.PlayerPoint(
                UUID.randomUUID(), new EventSpawnSafetyPolicy.Point(world, 0, 64, 0),
                false, true, false);
        check(!EventSpawnSafetyPolicy.tooCloseToRelevantPlayer(
                new EventSpawnSafetyPolicy.Point(world, 1, 64, 0), List.of(vanished),
                192, 0, true, true, false),
                "vanished player ignored by policy");
    }

    private static void verifiesBoundedCandidateSearch() {
        final List<EventSpawnSafetyPolicy.Offset> candidates =
                EventSpawnSafetyPolicy.candidates(32, 256, 512, 42);
        check(candidates.size() == 32, "expanded bounded attempt count");
        for (final EventSpawnSafetyPolicy.Offset offset : candidates) {
            final double distance = Math.hypot(offset.x(), offset.z());
            check(distance >= 256 - 1.0E-9 && distance <= 512 + 1.0E-9,
                    "candidate remains outside visibility annulus");
        }
        check(candidates.equals(EventSpawnSafetyPolicy.candidates(32, 256, 512, 42)),
                "candidate order deterministic");
    }

    private static void verifiesCircularWaterBuffer() {
        final List<EventSpawnSafetyPolicy.GridOffset> zero =
                EventSpawnSafetyPolicy.waterProbeOffsets(0);
        check(zero.equals(List.of(new EventSpawnSafetyPolicy.GridOffset(0, 0))),
                "zero-radius water scan must inspect the spawn column exactly once");

        final List<EventSpawnSafetyPolicy.GridOffset> radius =
                EventSpawnSafetyPolicy.waterProbeOffsets(4);
        check(radius.get(0).equals(new EventSpawnSafetyPolicy.GridOffset(0, 0)),
                "water scan must start at the spawn column for fast fail");
        check(radius.contains(new EventSpawnSafetyPolicy.GridOffset(4, 0))
                        && radius.contains(new EventSpawnSafetyPolicy.GridOffset(-4, 0))
                        && radius.contains(new EventSpawnSafetyPolicy.GridOffset(0, 4))
                        && radius.contains(new EventSpawnSafetyPolicy.GridOffset(0, -4)),
                "cardinal shoreline boundary must be covered");
        check(!radius.contains(new EventSpawnSafetyPolicy.GridOffset(4, 4)),
                "square corner outside the circular buffer must not be scanned");
        check(radius == EventSpawnSafetyPolicy.waterProbeOffsets(4),
                "water probe mask must be cached and immutable per bounded radius");
        check(EventSpawnSafetyPolicy.waterProbeOffsets(100).stream()
                        .allMatch(offset -> offset.x() * offset.x()
                                + offset.z() * offset.z() <= 32 * 32),
                "water buffer radius must be bounded against accidental quadratic explosions");
    }

    private static void verifiesSafetyDefaultsAndRuntimeWiring() throws Exception {
        final YamlConfiguration config = YamlConfiguration.loadConfiguration(Path.of(
                "src/main/resources/config/event-spawn-safety.yml").toFile());
        check(config.getDouble("world-events.safety.min-horizontal-distance-blocks") >= 192.0D,
                "events must default beyond normal entity visibility");
        check(!config.getBoolean("world-events.safety.ignore-admins", true),
                "visible admins must count as players by default");
        check(config.getInt("world-events.safety.search-attempts") >= 32,
                "distant search needs enough bounded candidates");
        check(config.getDouble("world-events.safety.search-min-radius-blocks") >= 256.0D,
                "player-anchored search must begin beyond 16 chunks");
        check(config.getDouble("world-events.safety.search-max-radius-blocks") >= 512.0D,
                "search must have room to escape protected or wet terrain");

        check(config.getBoolean("world-events.water-safety.enabled"),
                "world-event water safety must default to enabled");
        check(config.getBoolean("world-events.water-safety.enforce-all-events"),
                "legacy per-event water=false must not bypass the global rule");
        check(config.getInt("world-events.water-safety.buffer-blocks") >= 1,
                "shoreline buffer default must reject immediate water edges");
        check(!config.isSet("world-events.spawn-rules.player-caravan.water")
                        && !config.isSet("world-events.spawn-rules.caravan.water"),
                "water-safety subsystem must not duplicate world.yml spawn-rule ownership");

        final String guard = read("src/main/java/hu/taliann/icesmp/managers/EventSpawnGuard.java");
        check(guard.contains("waterOrShoreUnsafe")
                        && guard.contains("HeightMap.WORLD_SURFACE")
                        && guard.contains("Waterlogged")
                        && guard.contains("findSafeAtOrNear")
                        && guard.contains("EventSpawnSafetyPolicy.waterProbeOffsets")
                        && guard.contains("reserveAfterSurfaceValidation")
                        && guard.contains("WATER_OR_SHORE")
                        && guard.contains("min-horizontal-distance-blocks\", 192.0D")
                        && guard.contains("ignore-admins\", false")
                        && guard.contains("search-attempts\", 32")
                        && guard.contains("search-min-radius-blocks\", 256.0D")
                        && guard.contains("search-max-radius-blocks\", 512.0D"),
                "central guard lost safe player-distance or shoreline fallbacks");

        final String caravan = read("src/main/java/hu/taliann/icesmp/managers/CaravanManager.java");
        check(caravan.contains("findSafeAtOrNear(\"caravan\"")
                        && caravan.contains("arrivalPending = true")
                        && caravan.contains("anchorGeneration")
                        && caravan.contains("synchronized boolean forceArrive")
                        && !caravan.contains("getHighestBlockYAt")
                        && !caravan.contains("topOf("),
                "merchant caravan may bypass the central location resolver or double-launch");

        final String playerCaravan = read(
                "src/main/java/hu/taliann/icesmp/managers/PlayerCaravanManager.java");
        check(playerCaravan.contains("findSafeNear(\"player-caravan\"")
                        && playerCaravan.contains("failPendingSpawn")
                        && playerCaravan.contains("treasuryManager.deposit(faction, amount)")
                        && playerCaravan.contains("PENDING_CONVOY_ID")
                        && !playerCaravan.contains("getHighestBlockYAt")
                        && !playerCaravan.contains("ThreadLocalRandom"),
                "player caravan may bypass safe search or lose cargo on search failure");

        final String configManager = read(
                "src/main/java/hu/taliann/icesmp/managers/ConfigManager.java");
        check(configManager.contains("\"event-spawn-safety\""),
                "event-spawn safety subsystem is not loaded on existing deployments");
    }

    private static void verifiesEveryKnownEntityEventUsesGuardedPlacement() throws Exception {
        final String ambient = read(
                "src/main/java/hu/taliann/icesmp/managers/AmbientEventManager.java");
        final String herd = section(ambient,
                "private void animalMigration", "private void rewardParticipants");
        check(herd.contains("findSafeNear(\"animal-migration\"")
                        && herd.contains("resolveSafeStandingLocation(\"animal-migration\"")
                        && herd.contains("isBlocked(\"animal-migration\"")
                        && !herd.contains("getHighestBlockYAt"),
                "ambient animal migration may still appear beside a player or on water");

        final String cultists = read(
                "src/main/java/hu/taliann/icesmp/managers/CultistEventManager.java");
        final String cultistSpawner = section(cultists,
                "private void spawnCultist", "private void prepareCultist");
        check(cultists.contains("findSafeNear(\"cultists\"")
                        && cultists.contains("findSafeAtOrNear(\"cultists\"")
                        && cultistSpawner.contains("resolveSafeStandingLocation")
                        && cultistSpawner.contains("isBlocked(\"cultists\"")
                        && !cultistSpawner.contains("getHighestBlockYAt"),
                "cultist center or offset spawns may bypass distant dry placement");

        final String wildHunt = read(
                "src/main/java/hu/taliann/icesmp/managers/WildHuntManager.java");
        final String wildSpawn = section(wildHunt,
                "private synchronized boolean spawn", "private void escape");
        check(wildSpawn.contains("findSafeNear(\"wild-hunt\"")
                        && wildSpawn.contains("isBlocked(\"wild-hunt\"")
                        && !wildSpawn.contains("getHighestBlockYAt"),
                "Wild Hunt may visibly pop in beside its anchor");

        final String meteor = read(
                "src/main/java/hu/taliann/icesmp/managers/MeteorEventManager.java");
        final String meteorSpawn = section(meteor,
                "private synchronized boolean spawn", "private void carve");
        check(meteorSpawn.contains("findSafeNear(\"meteor\"")
                        && meteorSpawn.contains("isBlocked(\"meteor\"")
                        && !meteorSpawn.contains("meteor.spawn-radius"),
                "meteor may still use the old visible 90-block landing square");

        assertContains("InvasionManager.java", "findSafeNear(\"invasion\"");
        assertContains("WorldBossManager.java", "findSafeNear(\"world-boss\"");
        assertContains("CorruptionManager.java", "isUnsafeSurface(\"corruption\"");
        assertContains("StrangerNpcManager.java", "isUnsafeSurface(\"stranger\"");
        assertContains("EscortManager.java", "isUnsafeSurface(\"escort\"");
    }

    private static void assertContains(final String file, final String marker) throws Exception {
        final String source = read("src/main/java/hu/taliann/icesmp/managers/" + file);
        check(source.contains(marker),
                "known event spawn path lost central guard: " + file);
    }

    private static String section(final String source, final String start, final String end) {
        final int from = source.indexOf(start);
        final int to = source.indexOf(end, Math.max(0, from + start.length()));
        check(from >= 0 && to > from,
                "source section missing: " + start + " -> " + end);
        return source.substring(from, to);
    }

    private static String read(final String path) throws Exception {
        return Files.readString(Path.of(path));
    }

    private static void check(final boolean condition, final String message) {
        if (!condition) throw new AssertionError(message);
    }
}
