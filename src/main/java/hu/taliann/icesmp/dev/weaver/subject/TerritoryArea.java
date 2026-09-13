package hu.taliann.icesmp.dev.weaver.subject;

public record TerritoryArea(String territoryId, String revision, AreaShape resolved) implements AreaShape {
    public TerritoryArea {
        if (territoryId == null || territoryId.isBlank() || territoryId.length() > 128 || revision == null || revision.isBlank()
                || revision.length() > 256 || resolved == null || resolved instanceof TerritoryArea) throw new IllegalArgumentException("Invalid territory area snapshot");
    }
    @Override public AreaBounds bounds() { return resolved.bounds(); }
    @Override public boolean contains(final int x, final int y, final int z) { return resolved.contains(x, y, z); }
}
