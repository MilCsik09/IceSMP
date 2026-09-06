package hu.taliann.icesmp.dev.weaver;

import hu.taliann.icesmp.dev.weaver.subject.*;
import hu.taliann.icesmp.dev.weaver.api.AreaLimits;
import java.util.List;
import java.util.UUID;

public final class WeaverAreaRegressionSuite {
    public static void main(final String[] args) {
        final UUID world = UUID.randomUUID();
        final AreaRef edge = new AreaRef(world, new RadiusArea(15.5, 64, 15.5, 16));
        WeaverTypeCompatibilityRegressionSuite.check(edge.shape().bounds().chunkCount() == 9, "negative/border chunk calculation");
        WeaverTypeCompatibilityRegressionSuite.check(edge.shape().contains(15, 64, 15), "radius center absent");
        WeaverTypeCompatibilityRegressionSuite.check(!edge.shape().contains(40, 64, 40), "radius leaked bounding-box corner");
        WeaverTypeCompatibilityRegressionSuite.rejects(() -> new RadiusArea(0, 0, 0, 16.01));
        WeaverTypeCompatibilityRegressionSuite.rejects(() -> new CuboidArea(new AreaBounds(0, 0, 0, 32, 0, 0)));
        WeaverTypeCompatibilityRegressionSuite.rejects(() -> new CylinderArea(0, 0, 1, 0, 32));
        WeaverTypeCompatibilityRegressionSuite.rejects(() -> new AreaLimits(4097, 128, 9, 9, 16));
        WeaverTypeCompatibilityRegressionSuite.rejects(() -> new AreaLimits(4096, 129, 9, 9, 16));
        WeaverTypeCompatibilityRegressionSuite.rejects(() -> new AreaLimits(4096, 128, 9, 9, 17));
        WeaverTypeCompatibilityRegressionSuite.rejects(() -> new PolygonPrismArea(List.of(new PolygonPrismArea.Vertex(0, 0),
                new PolygonPrismArea.Vertex(5, 5), new PolygonPrismArea.Vertex(0, 5), new PolygonPrismArea.Vertex(5, 0)), 0, 1));
        WeaverTypeCompatibilityRegressionSuite.rejects(() -> new AreaRef(world, new PolygonPrismArea(List.of(new PolygonPrismArea.Vertex(0, 0),
                new PolygonPrismArea.Vertex(100, 0), new PolygonPrismArea.Vertex(0, 100)), 0, 1)));
        final PolygonPrismArea triangle = new PolygonPrismArea(List.of(new PolygonPrismArea.Vertex(0, 0), new PolygonPrismArea.Vertex(8, 0),
                new PolygonPrismArea.Vertex(0, 8)), 64, 66);
        WeaverTypeCompatibilityRegressionSuite.check(triangle.contains(1, 64, 1) && !triangle.contains(7, 64, 7) && !triangle.contains(1, 63, 1), "polygon prism containment");
        System.out.println("Weaver AREA geometry regression suite passed; live collection is a separate acceptance gate.");
    }
}
