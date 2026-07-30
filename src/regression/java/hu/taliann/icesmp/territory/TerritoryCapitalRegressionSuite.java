package hu.taliann.icesmp.territory;

import hu.taliann.icesmp.data.BlockCuboid;
import hu.taliann.icesmp.data.FactionType;
import hu.taliann.icesmp.data.Territory;
import hu.taliann.icesmp.data.TerritoryType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Focused 3D-capital geometry, command-wiring and consumer regressions. */
public final class TerritoryCapitalRegressionSuite {

    private TerritoryCapitalRegressionSuite() {
    }

    public static void main(final String[] args) throws Exception {
        normalizesInclusiveSelection();
        preservesOuterBlockEdgesAndYBounds();
        keepsOperationalCenterInsideNegativeSingleton();
        rejectsFootprintOverflow();
        verifiesProductionWiring();
        System.out.println("3D territory-capital regression suite passed.");
    }

    private static void normalizesInclusiveSelection() {
        final BlockCuboid cuboid = BlockCuboid.between("world",
                12, 7, 23, 10, 5, 20);
        check(cuboid.minX() == 10 && cuboid.maxX() == 12, "X bounds were not normalized");
        check(cuboid.minY() == 5 && cuboid.maxY() == 7, "Y bounds were not normalized");
        check(cuboid.minZ() == 20 && cuboid.maxZ() == 23, "Z bounds were not normalized");
        check(cuboid.width() == 3L && cuboid.height() == 3L && cuboid.depth() == 4L,
                "inclusive dimensions are wrong");
        check(cuboid.columns() == 12L && cuboid.volume() == 36L,
                "selection area/volume is wrong");
    }

    private static void preservesOuterBlockEdgesAndYBounds() {
        final BlockCuboid cuboid = BlockCuboid.between("world",
                10, 5, 20, 12, 7, 23);
        final Territory territory = territory(cuboid);

        check(territory.contains("world", 10.5D, 5.0D, 20.5D),
                "minimum selected block is outside");
        check(territory.contains("world", 12.999D, 7.999D, 23.999D),
                "maximum selected block volume is outside");
        check(!territory.contains("world", 9.5D, 6.0D, 21.5D),
                "block before minX is inside");
        check(!territory.contains("world", 13.5D, 6.0D, 21.5D),
                "block after maxX is inside");
        check(!territory.contains("world", 11.5D, 4.999D, 21.5D),
                "block below minY is inside");
        check(!territory.contains("world", 11.5D, 8.0D, 21.5D),
                "block above maxY is inside");
        check(!territory.contains("other", 11.5D, 6.0D, 21.5D),
                "cross-world point is inside");
    }

    private static void keepsOperationalCenterInsideNegativeSingleton() {
        final BlockCuboid cuboid = BlockCuboid.between("world",
                -2, 5, -3, -2, 5, -3);
        final Territory territory = territory(cuboid);
        check(territory.x() == -2 && territory.z() == -3,
                "negative singleton centre escaped the selected block");
        check(territory.contains("world",
                        territory.x() + 0.5D, 5.5D, territory.z() + 0.5D),
                "operational centre is outside the negative singleton cuboid");
    }

    private static void rejectsFootprintOverflow() {
        final BlockCuboid edge = new BlockCuboid("world",
                Integer.MAX_VALUE, 0, 0,
                Integer.MAX_VALUE, 0, 0);
        expectThrows(ArithmeticException.class, edge::footprintPolygon);
    }

    private static Territory territory(final BlockCuboid cuboid) {
        final List<int[]> points = cuboid.footprintPolygon();
        final int centerX = cuboid.centerX();
        final int centerZ = cuboid.centerZ();
        int radius = 1;
        for (final int[] point : points) {
            final long dx = (long) point[0] - centerX;
            final long dz = (long) point[1] - centerZ;
            radius = Math.max(radius,
                    Math.toIntExact((long) Math.ceil(Math.sqrt((double) dx * dx + (double) dz * dz))));
        }
        return new Territory("red-capital", FactionType.RED, "Red capital",
                TerritoryType.CAPITAL, cuboid.world(), centerX, centerZ, radius,
                points, cuboid.minY(), cuboid.maxY());
    }

    private static void verifiesProductionWiring() throws IOException {
        final String command = source("src/main/java/hu/taliann/icesmp/commands/TerritoryCommand.java");
        final String claims = source("src/main/java/hu/taliann/icesmp/managers/ClaimManager.java");
        final String territories = source("src/main/java/hu/taliann/icesmp/managers/TerritoryManager.java");
        final String core = source("src/main/java/hu/taliann/icesmp/core/IceSMPCore.java");
        final String territoryListener = source(
                "src/main/java/hu/taliann/icesmp/listeners/TerritoryListener.java");
        final String capitalLaw = source(
                "src/main/java/hu/taliann/icesmp/listeners/CapitalLawListener.java");
        final String raid = source("src/main/java/hu/taliann/icesmp/managers/RaidManager.java");
        final String ritual = source("src/main/java/hu/taliann/icesmp/managers/RitualManager.java");
        final String messages = source("src/main/resources/messages/territory.yml");
        final String builderGuide = source("docs/BUILDER_GUIDE.md");

        check(command.contains("\"selection\".equalsIgnoreCase(args[2])")
                        && command.contains("claimManager.snapshotSelection")
                        && command.contains("territoryManager.defineCuboid"),
                "selection command path is not wired");
        check(command.indexOf("territoryManager.defineCuboid")
                        < command.indexOf("claimManager.clearSelection"),
                "selection clears before the capital is persisted");
        check(core.contains("new TerritoryCommand(plugin, territoryManager, claimManager, messageManager)"),
                "ClaimManager is not injected into /territory");
        check(territories.contains("final int centroidX = bounds.centerX()")
                        && territories.contains("final int centroidZ = bounds.centerZ()"),
                "cuboid operational centre is not derived from a selected block");
        check(claims.contains("selection.y1") && claims.contains("selection.y2")
                        && claims.contains("return BlockCuboid.between"),
                "claim selection snapshot lost its Y coordinates");
        check(territoryListener.contains("first.getBlockY() == second.getBlockY()")
                        && territoryListener.contains("first.getWorld() == second.getWorld()"),
                "territory border gate is not 3D/world-aware");
        check(capitalLaw.contains("first.getBlockY() == second.getBlockY()")
                        && capitalLaw.contains("first.getWorld() == second.getWorld()"),
                "capital-law border gate is not 3D/world-aware");
        check(raid.contains("location.getX(), location.getY(), location.getZ()")
                        && raid.contains("deathLocation.getX(), deathLocation.getY(), deathLocation.getZ()"),
                "raid scoring still ignores territory Y bounds");
        check(ritual.contains("capital.minY()") && ritual.contains("capital.maxY()"),
                "capital home destination is not clamped to its Y bounds");
        check(messages.contains("territory-setcapital-selection-success")
                        && messages.contains("territory-setcapital-selection-usage")
                        && messages.contains("territory-help-setcapital-selection")
                        && messages.contains("<sugár|selection>"),
                "selection messages are missing");
        check(builderGuide.contains("/territory setcapital <frakció> selection [név...]"),
                "builder guide is missing the exact command");
    }

    private static String source(final String path) throws IOException {
        return Files.readString(Path.of(path));
    }

    private static void expectThrows(final Class<? extends Throwable> expected,
                                     final ThrowingRunnable runnable) {
        try {
            runnable.run();
        } catch (final Throwable throwable) {
            if (expected.isInstance(throwable)) {
                return;
            }
            throw new AssertionError("Expected " + expected.getSimpleName()
                    + " but got " + throwable.getClass().getSimpleName(), throwable);
        }
        throw new AssertionError("Expected " + expected.getSimpleName());
    }

    private static void check(final boolean condition, final String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Throwable;
    }
}
