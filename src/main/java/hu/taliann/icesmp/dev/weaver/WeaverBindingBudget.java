package hu.taliann.icesmp.dev.weaver;

/** One non-recursive VFX binding; no callable action graph or child binding capability. */
public final class WeaverBindingBudget {
    private final long expiresAt;
    private int remaining = 16;
    private long nextAllowed;
    public WeaverBindingBudget(long createdAt) { if (createdAt < 0) throw new IllegalArgumentException("Negative binding time"); nextAllowed = createdAt; expiresAt = Math.addExact(createdAt, 120_000); }
    public synchronized boolean tryFire(long now, int depth) {
        if (depth != 0 || remaining == 0 || now >= expiresAt || now < nextAllowed) return false;
        remaining--; nextAllowed = Math.addExact(now, 2_000); return true;
    }
}
