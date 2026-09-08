package hu.taliann.icesmp.trash;

import java.util.*;
import java.util.function.LongSupplier;

/** Canonical bounded transient field authority, shared by native activation and developer adapters. */
public final class TrashRuleFieldService {
    public static final int MAX_FIELDS_PER_WORLD = 32;
    public static final int MAX_FIELDS_GLOBAL = 128;
    public static final double MAX_RADIUS = 32.0D;
    public static final long MAX_LIFETIME_MILLIS = 60_000L;
    public enum FieldKind { PROJECTILE_WALL, ACOUSTIC_NULL, CEASEFIRE, SPATIAL_ANCHOR }
    public record Point(UUID world, double x, double y, double z) {
        public Point {
            Objects.requireNonNull(world);
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                    || Math.abs(x) > 32_000_000 || Math.abs(y) > 32_000_000 || Math.abs(z) > 32_000_000) throw new IllegalArgumentException("Invalid field point");
        }
    }
    public record RuleField(UUID id, FieldKind kind, Point center, double radius, long expiresAt, UUID owner, String reservationToken) {
        public RuleField {
            Objects.requireNonNull(id); Objects.requireNonNull(kind); Objects.requireNonNull(center); Objects.requireNonNull(owner);
            if (!Double.isFinite(radius) || radius <= 0 || radius > MAX_RADIUS || expiresAt < 0
                    || reservationToken != null && (kind != FieldKind.PROJECTILE_WALL || !UUID.fromString(reservationToken).toString().equals(reservationToken)))
                throw new IllegalArgumentException("Invalid rule field");
        }
        public boolean active(long now) { return expiresAt > now; }
        public boolean contains(Point point, long now) {
            if (!active(now) || !center.world().equals(point.world())) return false;
            final double x = center.x() - point.x(), y = center.y() - point.y(), z = center.z() - point.z();
            return x * x + y * y + z * z <= radius * radius;
        }
    }
    public record Snapshot(long revision, List<RuleField> fields, Set<UUID> claimed, boolean open) {
        public Snapshot { fields = List.copyOf(fields); claimed = Set.copyOf(claimed); }
    }
    /** Identity equality prevents a retired callback releasing a newer claim of the same field. */
    public static final class FieldClaim {
        private final RuleField field;
        private FieldClaim(RuleField field) { this.field = field; }
        public RuleField field() { return field; }
    }
    private final LongSupplier clock;
    private final Map<UUID, RuleField> fields = new LinkedHashMap<>();
    private final Map<UUID, FieldClaim> claimed = new HashMap<>();
    private long revision;
    private boolean open = true;
    public TrashRuleFieldService() { this(System::currentTimeMillis); }
    public TrashRuleFieldService(LongSupplier clock) { this.clock = Objects.requireNonNull(clock); }
    public synchronized Snapshot snapshot() { return new Snapshot(revision, List.copyOf(fields.values()), claimed.keySet(), open); }
    public synchronized boolean hasCapacity(UUID world) {
        Objects.requireNonNull(world);
        return open && fields.size() < MAX_FIELDS_GLOBAL && fields.values().stream().filter(f -> f.center().world().equals(world)).count() < MAX_FIELDS_PER_WORLD;
    }
    public synchronized boolean add(RuleField field) {
        Objects.requireNonNull(field); final long now = clock.getAsLong();
        if (now < 0 || !field.active(now) || field.expiresAt() - now > MAX_LIFETIME_MILLIS || fields.containsKey(field.id()) || !hasCapacity(field.center().world())) return false;
        fields.put(field.id(), field); revision++; return true;
    }
    /** Exact immutable field match; a reused/stale value cannot remove a different definition. */
    public synchronized boolean remove(RuleField field) {
        Objects.requireNonNull(field);
        if (!fields.remove(field.id(), field)) return false;
        claimed.remove(field.id()); revision++; return true;
    }
    /** Developer removal cannot interrupt the native inventory-consumption claim. */
    public synchronized boolean removeIdle(RuleField field, long expectedRevision) {
        return revision == expectedRevision && !claimed.containsKey(field.id()) && remove(field);
    }
    public synchronized boolean contains(RuleField field) { return open && field.equals(fields.get(field.id())); }
    public synchronized boolean isClaimed(FieldClaim claim) {
        Objects.requireNonNull(claim);
        return open && claimed.get(claim.field().id()) == claim && contains(claim.field())
                && claim.field().active(clock.getAsLong());
    }
    public synchronized Optional<FieldClaim> claim(Point point, FieldKind kind) {
        Objects.requireNonNull(point); Objects.requireNonNull(kind);
        if (!open) return Optional.empty(); final long now = clock.getAsLong();
        for (final var field : fields.values()) if (field.kind() == kind && field.contains(point, now) && !claimed.containsKey(field.id())) {
            final var claim = new FieldClaim(field); claimed.put(field.id(), claim);
            revision++; return Optional.of(claim);
        }
        return Optional.empty();
    }
    public synchronized boolean releaseClaim(FieldClaim claim) {
        Objects.requireNonNull(claim);
        if (!claimed.remove(claim.field().id(), claim)) return false;
        revision++; return true;
    }
    public synchronized boolean removeClaimed(FieldClaim claim) {
        Objects.requireNonNull(claim);
        return claimed.get(claim.field().id()) == claim && remove(claim.field());
    }
    public synchronized boolean activeAt(Point point, FieldKind kind) {
        Objects.requireNonNull(point); Objects.requireNonNull(kind); final long now = clock.getAsLong();
        return open && fields.values().stream().anyMatch(field -> field.kind() == kind && field.contains(point, now));
    }
    public synchronized boolean hasKind(FieldKind kind) {
        final long now = clock.getAsLong(); return open && fields.values().stream().anyMatch(field -> field.kind() == kind && field.active(now));
    }
    /** The caller owns reservation release; never discard expired reservations silently. */
    public synchronized List<RuleField> expire() {
        final long now = clock.getAsLong(); final List<RuleField> expired = fields.values().stream().filter(field -> !field.active(now) && !claimed.containsKey(field.id())).toList();
        expired.forEach(this::remove); return expired;
    }
    public synchronized List<RuleField> close() {
        final List<RuleField> removed = List.copyOf(fields.values()); fields.clear(); claimed.clear(); open = false; revision++; return removed;
    }
}
