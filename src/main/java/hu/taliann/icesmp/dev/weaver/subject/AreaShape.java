package hu.taliann.icesmp.dev.weaver.subject;

public sealed interface AreaShape permits RadiusArea, CuboidArea, TerritoryArea, CylinderArea, PolygonPrismArea {
    AreaBounds bounds();
    boolean contains(int x, int y, int z);
}
