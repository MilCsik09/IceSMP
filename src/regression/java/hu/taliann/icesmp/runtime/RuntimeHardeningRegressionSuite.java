package hu.taliann.icesmp.runtime;

import hu.taliann.icesmp.data.ClaimFootprint;
import hu.taliann.icesmp.data.ClaimShape;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class RuntimeHardeningRegressionSuite {
    private RuntimeHardeningRegressionSuite() { }

    public static void main(final String[] args) throws Exception {
        claimGeometry();
        polygonClaimGeometry();
        lifecycleSourceContracts();
        System.out.println("Runtime hardening regression suite passed.");
    }

    private static void claimGeometry() {
        final int[][] corners = {{1, 3, 8, 11}, {8, 11, 1, 3}, {1, 11, 8, 3}, {8, 3, 1, 11}};
        for (final int[] value : corners) {
            final ClaimFootprint footprint = ClaimFootprint.between(value[0], value[1], value[2], value[3]);
            check(footprint.equals(new ClaimFootprint(1, 3, 8, 11)), "all corner orders normalize");
            check(footprint.contains(1, 3) && footprint.contains(8, 11), "inclusive corners");
        }
        final List<ClaimFootprint> cases = List.of(
                ClaimFootprint.between(4, 9, 4, 9),
                ClaimFootprint.between(-20, -1, -20, 15),
                ClaimFootprint.between(-8, -8, 8, 8),
                ClaimFootprint.between(0, 0, 15, 15),
                ClaimFootprint.between(15, 15, 32, 47));
        for (final ClaimFootprint footprint : cases) {
            final List<ClaimFootprint.BoundaryPoint> points = footprint.perimeter(2);
            check(new HashSet<>(points).size() == points.size(), "perimeter has no duplicate corners");
            final Set<ClaimFootprint.BoundaryPoint> expected = Set.of(
                    new ClaimFootprint.BoundaryPoint(footprint.minX(), footprint.minZ()),
                    new ClaimFootprint.BoundaryPoint(footprint.outerMaxX(), footprint.minZ()),
                    new ClaimFootprint.BoundaryPoint(footprint.minX(), footprint.outerMaxZ()),
                    new ClaimFootprint.BoundaryPoint(footprint.outerMaxX(), footprint.outerMaxZ()));
            check(points.containsAll(expected), "all outside-edge corners rendered");
        }
        check(ClaimFootprint.between(-1, -1, 1, 1).contains(0, 0), "zero-crossing membership");
        check(!ClaimFootprint.between(0, 0, 15, 15).contains(16, 15), "outside edge excluded");
        check(ClaimFootprint.between(0, 0, 15, 15).overlaps(ClaimFootprint.between(15, 0, 31, 15)),
                "shared block column overlaps");
        check(!ClaimFootprint.between(0, 0, 15, 15).overlaps(ClaimFootprint.between(16, 0, 31, 15)),
                "adjacent claims do not overlap");
    }

    private static void polygonClaimGeometry() {
        final ClaimShape concave = ClaimShape.polygon(List.of(
                new ClaimShape.Point(0, 0),
                new ClaimShape.Point(4, 0),
                new ClaimShape.Point(4, 1),
                new ClaimShape.Point(1, 1),
                new ClaimShape.Point(1, 4),
                new ClaimShape.Point(0, 4)));
        check(concave.contains(3, 0) && concave.contains(0, 3),
                "concave polygon claims both arms");
        check(!concave.contains(3, 3),
                "concave polygon does not claim its bounding-box notch");
        check(concave.columns() == 16, "concave polygon exact column count");
        check(!concave.boundaryColumns().isEmpty()
                        && concave.boundaryColumns().stream().allMatch(
                        point -> concave.contains(point.x(), point.z())),
                "polygon boundary columns belong to the exact shape");

        final ClaimShape negative = ClaimShape.polygon(List.of(
                new ClaimShape.Point(-4, -4),
                new ClaimShape.Point(-1, -4),
                new ClaimShape.Point(-1, -1),
                new ClaimShape.Point(-4, -1)));
        check(negative.contains(-3, -3), "negative-coordinate polygon membership");
        check(!concave.overlaps(negative), "disjoint polygons do not overlap");
        check(concave.overlaps(ClaimShape.rectangle(
                        ClaimFootprint.between(0, 3, 0, 3))),
                "polygon/rectangle overlap uses exact columns");
        check(!concave.overlaps(ClaimShape.rectangle(
                        ClaimFootprint.between(3, 3, 3, 3))),
                "rectangle in concave notch does not falsely overlap");

        boolean invalid = false;
        try {
            ClaimShape.polygon(List.of(
                    new ClaimShape.Point(0, 0),
                    new ClaimShape.Point(4, 4),
                    new ClaimShape.Point(0, 4),
                    new ClaimShape.Point(4, 0)));
        } catch (final IllegalArgumentException expected) {
            invalid = true;
        }
        check(invalid, "self-intersecting/bow-tie polygon rejected");
    }

    private static void lifecycleSourceContracts() throws Exception {
        final String claim = source("src/main/java/hu/taliann/icesmp/managers/ClaimManager.java");
        check(claim.contains("claim.contains(worldName, location.getBlockX(), location.getBlockZ())"),
                "claim lookup is Y-independent");
        check(!claim.contains("drawBoxOutline"), "claim renderer has no 3D box path");
        check(claim.contains("borderTasks.remove(playerId)"), "border preview owns cleanup task");
        check(claim.contains("ClaimShape.polygon(points)")
                        && claim.contains("readClaimPolygon")
                        && claim.contains("shape.rowSpans()"),
                "polygon claims share exact geometry across create, persistence and protection");
        final String claimCommand = source("src/main/java/hu/taliann/icesmp/commands/ClaimCommand.java");
        check(claimCommand.contains("case \"polygon\", \"poligon\"")
                        && claimCommand.contains("case \"polywand\""),
                "normal claim command exposes territory-style polygon selection");
        final String selectionWand = source(
                "src/main/java/hu/taliann/icesmp/listeners/SelectionWandListener.java");
        check(selectionWand.contains("case \"claim-polygon\"")
                        && selectionWand.contains("claimManager.addPolygonPoint"),
                "polygon claim wand records multiple clicked boundary points");

        final String vanish = source("src/main/java/hu/taliann/icesmp/managers/VanishManager.java");
        check(vanish.contains("viewer.hidePlayer(plugin, subject);"), "vanish removes the in-world entity");
        check(vanish.contains("viewer.unlistPlayer(subject);")
                        && vanish.contains("viewer.listPlayer(subject);"),
                "vanish owns per-viewer tab-list removal and restoration");
        check(vanish.contains("TRACKING_REASSERT_TICKS"), "vanish is reasserted after tracking rebuilds");
        check(!vanish.contains("setInvulnerable"), "vanish never leaks Bukkit invulnerability state");
        final String permissions = source("src/main/java/hu/taliann/icesmp/core/Permissions.java");
        check(permissions.contains("MODERATION_VANISH_SEE,\n                \"Vanish állapotú adminok megtekintése\", PermissionDefault.FALSE"),
                "vanish-see is explicit and not default OP");
        check(!permissions.contains("moderationNodes.put(MODERATION_VANISH_SEE"),
                "moderation bundle cannot silently bypass vanish");
        final String vanishListener = source("src/main/java/hu/taliann/icesmp/listeners/VanishListener.java");
        check(vanishListener.contains("PlayerTeleportEvent") && vanishListener.contains("PlayerRespawnEvent"),
                "vanish retracking lifecycle covered");

        final String mobs = source("src/main/java/hu/taliann/icesmp/managers/MobScalingManager.java");
        check(mobs.indexOf("reconcileTerritoryProtection(entity);") < mobs.indexOf("if (!enabled"),
                "DARK protection runs before scaling gates");
        check(mobs.contains("territoryBurnBaselineKey") && mobs.contains("pdc.remove(territoryBurnManagedKey)"),
                "DARK protection restores baseline on exit");
        final String mobListener = source("src/main/java/hu/taliann/icesmp/listeners/MobScalingListener.java");
        check(mobListener.contains("EntitiesLoadEvent") && mobListener.contains("EntityMoveEvent")
                        && mobListener.contains("EntityTeleportEvent"),
                "DARK protection covers load/move/teleport");
        check(mobListener.contains("event.getClass() == EntityCombustEvent.class"),
                "only daylight combustion is cancelled");

        final String display = source("src/main/java/hu/taliann/icesmp/utils/DisplayFxUtil.java");
        check(display.contains("HeightMap.MOTION_BLOCKING_NO_LEAVES")
                        && display.contains("terrainWallColumn"),
                "BlockDisplay wall follows each owned terrain column");
        check(!claim.contains("baseY = location.getY()"),
                "claim display wall is never anchored to viewer Y");
        final String guard = source("src/main/java/hu/taliann/icesmp/managers/EventSpawnGuard.java");
        check(guard.contains("resolveSafeStandingLocation")
                        && guard.contains("material.isOccluding()")
                        && guard.contains("!material.hasGravity()"),
                "event and DARK spawns require stable solid footing");
        final String dark = source("src/main/java/hu/taliann/icesmp/managers/DarkUndeadAmbienceManager.java");
        check(dark.contains("dark-undead.spawn-attempts-per-mob")
                        && dark.contains("spawnGuard.resolveSafeStandingLocation")
                        && dark.contains("territory.contains(territory.world()"),
                "DARK undead use finite exact-territory safe-surface retries");
    }

    private static String source(final String relative) throws Exception {
        return Files.readString(Path.of(relative));
    }

    private static void check(final boolean condition, final String message) {
        if (!condition) throw new AssertionError(message);
    }
}
