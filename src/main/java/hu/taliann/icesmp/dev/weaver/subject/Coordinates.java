package hu.taliann.icesmp.dev.weaver.subject;

public final class Coordinates {
    private Coordinates() {}
    public static void block(final int x, final int y, final int z) {
        if (Math.abs((long) x) > 30_000_000 || Math.abs((long) z) > 30_000_000 || Math.abs((long) y) > 1_000_000) {
            throw new IllegalArgumentException("Subject coordinates outside bounds");
        }
    }
    public static void position(final double x, final double y, final double z) {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) throw new IllegalArgumentException("Non-finite subject location");
        block((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z));
        if (Math.abs(x) > 30_000_000 || Math.abs(z) > 30_000_000 || Math.abs(y) > 1_000_000) {
            throw new IllegalArgumentException("Subject location outside bounds");
        }
    }
}
