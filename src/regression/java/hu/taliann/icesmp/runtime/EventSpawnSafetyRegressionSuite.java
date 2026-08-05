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
        verifiesWaterSafetyRuntimeWiring();
        verifiesEveryKnownEntityEventUsesDryPlacement();
        System.out.println("Event spawn safety regression suite passed.");
    }

    private static void verifiesPlayerDistancePolicy() {
        final UUID world = UUID.randomUUID();
        final EventSpawnSafetyPolicy.PlayerPoint player = new EventSpawnSafetyPolicy.PlayerPoint(
                UUID.randomUUID(), new EventSpawnSafetyPolicy.Point(world, 0, 64, 0), false, false, false);
        check(EventSpawnSafetyPolicy.tooCloseToRelevantPlayer(
                new EventSpawnSafetyPolicy.Point(world, 95.999, 64, 0), List.of(player),
                96, 0, true, true, true), "inside horizontal minimum rejected");
        check(!EventSpawnSafetyPolicy.tooCloseToRelevantPlayer(
                new EventSpawnSafetyPolicy.Point(world, 96, 64, 0), List.of(player),
                96, 0, true, true, true), "exact minimum accepted");
        final EventSpawnSafetyPolicy.PlayerPoint second = new EventSpawnSafetyPolicy.PlayerPoint(
                UUID.randomUUID(), new EventSpawnSafetyPolicy.Point(world, 250, 64, 0), false, false, false);
        check(EventSpawnSafetyPolicy.tooCloseToRelevantPlayer(
                new EventSpawnSafetyPolicy.Point(world, 170, 64, 0), List.of(player, second),
                96, 0, true, true, true), "nearest of multiple players enforced");
        final EventSpawnSafetyPolicy.PlayerPoint spectator = new EventSpawnSafetyPolicy.PlayerPoint(
                UUID.randomUUID(), new EventSpawnSafetyPolicy.Point(world, 0, 64, 0), true, false, false);
        check(!EventSpawnSafetyPolicy.tooCloseToRelevantPlayer(
                new EventSpawnSafetyPolicy.Point(world, 1, 64, 0), List.of(spectator),
                96, 0, true, true, true), "spectator ignored by policy");
        final EventSpawnSafetyPolicy.PlayerPoint vanished = new EventSpawnSafetyPolicy.PlayerPoint(
                UUID.randomUUID(), new EventSpawnSafetyPolicy.Point(world, 0, 64, 0), false, true, false);
        check(!EventSpawnSafetyPolicy.tooCloseToRelevantPlayer(
                new EventSpawnSafetyPolicy.Point(world, 1, 64, 0), List.of(vanished),
                96, 0, true, true, true), "vanished player ignored by policy");
    }

    private static void verifiesBoundedCandidateSearch() {
        final List<EventSpawnSafetyPolicy.Offset> candidates =
                EventSpawnSafetyPolicy.candidates(24, 96, 256, 42);
        check(candidates.size() == 24, "bounded attempt count");
        for (final EventSpawnSafetyPolicy.Offset offset : candidates) {
            final double distance = Math.hypot(offset.x(), offset.z());
            check(distance >= 96 - 1.0E-9 && distance <= 256 + 1.0E-9,
                    "candidate remains inside configured annulus");
        }
        check(candidates.equals(EventSpawnSafetyPolicy.candidates(24, 96, 256, 42)),
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
        check(radius.equals(EventSpawnSafetyPolicy.waterProbeOffsets(4)),
                "water probe order must be deterministic");
        check(EventSpawnSafetyPolicy.waterProbeOffsets(100).stream()
                        .allMatch(offset -> offset.x() * offset.x() + offset.z() * offset.z() <= 32 * 32),
                "water buffer radius must be bounded against accidental quadratic explosions");
    }

    private static void verifiesWaterSafetyRuntimeWiring() throws Exception {
        final YamlConfiguration config = YamlConfiguration.loadConfiguration(Path.of(
                "src/main/resources/config/event-spawn-safety.yml").toFile());
        check(config.getBoolean("world-events.water-safety.enabled"),
                "world-event water safety must default to enabled");
        check(config.getBoolean("world-events.water-safety.enforce-all-events"),
                "legacy per-event water=false must not bypass the global rule");
        check(config.getInt("world-events.water-safety.buffer-blocks") >= 1,
                "shoreline buffer default must reject immediate water edges");
        check(config.getBoolean("world-events.spawn-rules.caravan.water")
                        && config.getBoolean("world-events.spawn-rules.player-caravan.water"),
                "both caravan systems must explicitly opt into dry spawning");

        final String guard = read("src/main/java/hu/taliann/icesmp/managers/EventSpawnGuard.java");
        check(guard.contains("waterOrShoreUnsafe")
                        && guard.contains("HeightMap.WORLD_SURFACE")
                        && guard.contains("Waterlogged")
                        && guard.contains("findSafeAtOrNear")
                        && guard.contains("EventSpawnSafetyPolicy.waterProbeOffsets")
                        && guard.contains("WATER_OR_SHORE"),
                "central surface resolver lost waterlogged/shoreline enforcement");

        final String caravan = read("src/main/java/hu/taliann/icesmp/managers/CaravanManager.java");
        check(caravan.contains("findSafeAtOrNear(\"caravan\"")
                        && caravan.contains("arrivalPending = true")
                        && caravan.contains("anchorGeneration")
                        && !caravan.contains("getHighestBlockYAt")
                        && !caravan.contains("topOf("),
                "merchant caravan may bypass the central dry-location resolver or double-launch");

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
                "water-safety subsystem is not loaded on existing deployments");
    }

    private static void verifiesEveryKnownEntityEventUsesDryPlacement() throws Exception {
        final String ambient = read(
                "src/main/java/hu/taliann/icesmp/managers/AmbientEventManager.java");
        check(ambient.contains("resolveSafeStandingLocation(\"animal-migration\"")
                        && section(ambient, "private void spawnHerd", "private void rewardParticipants")
                        .indexOf("getHighestBlockYAt") < 0,
                "ambient animal migration may still spawn a herd on water");

        final String cultists = read(
                "src/main/java/hu/taliann/icesmp/managers/CultistEventManager.java");
        final String cultistSpawner = section(cultists,
                "private void spawnCultist", "private void prepareCultist");
        check(cultists.contains("resolveSafeStandingLocation(\"cultists\"")
                        && cultistSpawner.contains("resolveSafeStandingLocation")
                        && !cultistSpawner.contains("getHighestBlockYAt"),
                "cultist offset spawns may bypass dry placement");

        assertContains("WildHuntManager.java", "isUnsafeSurface(\"wild-hunt\"");
        assertContains("InvasionManager.java", "isUnsafeSurface(\"invasion\"");
        assertContains("CorruptionManager.java", "isUnsafeSurface(\"corruption\"");
        assertContains("StrangerNpcManager.java", "isUnsafeSurface(\"stranger\"");
        assertContains("EscortManager.java", "isUnsafeSurface(\"escort\"");
        assertContains("WorldBossManager.java", "findSafeNear(\"world-boss\"");
    }

    private static void assertContains(final String file, final String marker) throws Exception {
        final String source = read("src/main/java/hu/taliann/icesmp/managers/" + file);
        check(source.contains(marker), "known event spawn path lost central dry guard: " + file);
    }

    private static String section(final String source, final String start, final String end) {
        final int from = source.indexOf(start);
        final int to = source.indexOf(end, Math.max(0, from + start.length()));
        check(from >= 0 && to > from, "source section missing: " + start + " -> " + end);
        return source.substring(from, to);
    }

    private static String read(final String path) throws Exception {
        return Files.readString(Path.of(path));
    }

    private static void check(final boolean condition, final String message) {
        if (!condition) throw new AssertionError(message);
    }
}
