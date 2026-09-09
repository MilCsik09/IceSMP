package hu.taliann.icesmp.trash;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import static hu.taliann.icesmp.trash.TrashRuleFieldService.*;

/** Real native field authority: geometry, caps, exact conditional removal, claim contention and lifecycle. */
public final class TrashRuleFieldRegressionSuite {
    private static int assertions;
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); assertions++; }
    private static RuleField field(UUID world, FieldKind kind, long expiry) {
        return new RuleField(UUID.randomUUID(), kind, new Point(world, 0, 0, 0), 6, expiry, UUID.randomUUID(), kind == FieldKind.PROJECTILE_WALL ? UUID.randomUUID().toString() : null);
    }
    public static void main(String[] args) throws Exception {
        geometryAndLifecycle(); capsAndSnapshots(); contention(); claimIdentity(); finalEffectAdmission(); invalidValues(); lifecycleProbe();
        System.out.println("Trash rule-field authority passed. assertions=" + assertions);
    }
    private static void geometryAndLifecycle() {
        final var clock = new AtomicLong(1000); final var service = new TrashRuleFieldService(clock::get); final UUID world = UUID.randomUUID();
        for (final var kind : FieldKind.values()) {
            final var field = field(world, kind, 3000); check(service.add(field), "typed native field admitted");
            check(service.activeAt(new Point(world, 6, 0, 0), kind), "inclusive radius boundary");
            check(!service.activeAt(new Point(world, 6.01, 0, 0), kind), "outside radius excluded");
            check(!service.activeAt(new Point(world, 0, 6.01, 0), kind), "vertical distance matters");
            check(!service.activeAt(new Point(UUID.randomUUID(), 0, 0, 0), kind), "world UUID isolation");
            check(service.removeIdle(field, service.snapshot().revision()), "idle field removed with exact revision");
            check(!service.hasKind(kind), "removal reaches native query");
        }
        final var wall = field(world, FieldKind.PROJECTILE_WALL, 3000); service.add(wall);
        final var observed = service.snapshot(); final var claim = service.claim(new Point(world, 0, 0, 0), FieldKind.PROJECTILE_WALL).orElseThrow();
        check(claim.field().equals(wall), "claim carries native immutable field");
        check(!service.removeIdle(wall, observed.revision()) && !service.removeIdle(wall, service.snapshot().revision()), "developer removal cannot interrupt in-flight native claim");
        clock.set(3000); check(!service.activeAt(wall.center(), wall.kind()) && !service.isClaimed(claim), "expired claim cannot authorize late inventory consumption");
        check(service.expire().isEmpty() && service.snapshot().fields().size() == 1, "pending reservation retained for native release callback");
        service.releaseClaim(claim); check(service.expire().equals(List.of(wall)), "expiry returns reservation for owner cleanup");
        check(service.snapshot().fields().isEmpty() && service.snapshot().claimed().isEmpty(), "released field leaves no registry ghost");
        final var active = field(world, FieldKind.CEASEFIRE, 4000); service.add(active);
        final var removed = service.close(); check(removed.equals(List.of(active)) && !service.snapshot().open(), "shutdown drains fields for native cleanup");
        check(!service.add(field(world, FieldKind.CEASEFIRE, 4000)) && service.claim(active.center(), active.kind()).isEmpty(), "closed service fails admission and claims");
    }
    private static void finalEffectAdmission() throws Exception {
        final var service = new TrashRuleFieldService(() -> 1000);
        final var wall = field(UUID.randomUUID(), FieldKind.PROJECTILE_WALL, 3000); service.add(wall);
        final var claim = service.claim(wall.center(), wall.kind()).orElseThrow();
        check(service.tryObserveClaimedEffect(claim, () -> false,
                () -> { throw new AssertionError("refused effect ran"); }).isEmpty(), "owner refusal became a negative observation");
        check(!service.tryObserveClaimedEffect(claim, () -> true, () -> false).orElseThrow(),
                "unobserved removal became positive evidence");
        final var entered = new CountDownLatch(1); final var release = new CountDownLatch(1);
        final var closing = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var effect = executor.submit(() -> service.tryObserveClaimedEffect(claim, () -> true, () -> {
                entered.countDown();
                try { if (!release.await(5, TimeUnit.SECONDS)) throw new AssertionError("effect wait timed out"); }
                catch (InterruptedException failure) { throw new AssertionError(failure); }
                return true;
            }));
            Future<List<RuleField>> close = null;
            try {
                check(entered.await(5, TimeUnit.SECONDS), "native effect did not enter");
                close = executor.submit(() -> { closing.countDown(); return service.close(); });
                check(closing.await(5, TimeUnit.SECONDS), "native close did not enter");
                try { close.get(50, TimeUnit.MILLISECONDS); throw new AssertionError("claim closed between final admission and observation"); }
                catch (TimeoutException expected) { assertions++; }
            } finally { release.countDown(); }
            check(effect.get(5, TimeUnit.SECONDS).orElseThrow(), "entered native observation was lost");
            check(close.get(5, TimeUnit.SECONDS).equals(List.of(wall)), "close did not resume after the bounded effect");
        }
        check(service.tryObserveClaimedEffect(claim,
                () -> { throw new AssertionError("closed claim entered owner admission"); },
                () -> { throw new AssertionError("closed claim replayed effect"); }).isEmpty(), "closed claim admitted a late effect");
    }
    private static void capsAndSnapshots() {
        final var service = new TrashRuleFieldService(() -> 1000); final UUID world = UUID.randomUUID(); final var original = field(world, FieldKind.ACOUSTIC_NULL, 2000);
        check(service.add(original) && !service.add(original), "duplicate ID cannot add a second field");
        final var other = new RuleField(original.id(), original.kind(), original.center(), 7, original.expiresAt(), original.owner(), null);
        check(!service.add(other) && !service.remove(other), "same ID with another definition cannot overwrite or remove");
        final var retained = service.snapshot();
        for (int i = 1; i < MAX_FIELDS_PER_WORLD; i++) check(service.add(field(world, FieldKind.ACOUSTIC_NULL, 2000)), "world capacity admits expected field");
        check(!service.hasCapacity(world) && !service.add(field(world, FieldKind.ACOUSTIC_NULL, 2000)), "world cap enforced");
        for (int i = 1; i < 4; i++) { final UUID next = UUID.randomUUID(); for (int j = 0; j < MAX_FIELDS_PER_WORLD; j++) check(service.add(field(next, FieldKind.SPATIAL_ANCHOR, 2000)), "global capacity admits expected field"); }
        check(service.snapshot().fields().size() == MAX_FIELDS_GLOBAL && !service.add(field(UUID.randomUUID(), FieldKind.SPATIAL_ANCHOR, 2000)), "global cap enforced");
        check(retained.fields().equals(List.of(original)) && retained.claimed().isEmpty(), "previous snapshot is detached");
        try { retained.fields().clear(); throw new AssertionError("mutable snapshot"); } catch (UnsupportedOperationException expected) { assertions++; }
        check(!service.removeIdle(original, retained.revision()), "external registry drift rejects conditional remove");
    }
    private static void contention() throws Exception {
        final var service = new TrashRuleFieldService(() -> 1000); final var wall = field(UUID.randomUUID(), FieldKind.PROJECTILE_WALL, 2000); service.add(wall);
        final var executor = Executors.newFixedThreadPool(8);
        try {
            final List<Future<Boolean>> attempts = new ArrayList<>();
            final var admitted = new java.util.concurrent.atomic.AtomicReference<FieldClaim>();
            for (int i = 0; i < 32; i++) attempts.add(executor.submit(() -> {
                final var claim = service.claim(wall.center(), wall.kind());
                claim.ifPresent(admitted::set); return claim.isPresent();
            }));
            int accepted = 0; for (final var attempt : attempts) if (attempt.get(5, TimeUnit.SECONDS)) accepted++;
            check(accepted == 1 && service.snapshot().claimed().size() == 1, "simultaneous projectiles receive one consumption claim");
            service.releaseClaim(admitted.get()); check(service.claim(wall.center(), wall.kind()).isPresent(), "failed native claim can retry");
            check(service.remove(wall) && service.snapshot().claimed().isEmpty(), "native consumption removes field and claim together");
            for (int i = 0; i < MAX_FIELDS_PER_WORLD - 1; i++) service.add(field(wall.center().world(), FieldKind.CEASEFIRE, 2000));
            attempts.clear();
            for (int i = 0; i < 32; i++) attempts.add(executor.submit(() -> service.add(field(wall.center().world(), FieldKind.CEASEFIRE, 2000))));
            accepted = 0; for (final var attempt : attempts) if (attempt.get(5, TimeUnit.SECONDS)) accepted++;
            check(accepted == 1 && service.snapshot().fields().size() == MAX_FIELDS_PER_WORLD, "concurrent creators cannot race beyond world cap");
        } finally { executor.shutdownNow(); }
    }
    private static void lifecycleProbe() {
        final var service = new TrashRuleFieldService(() -> 1000);
        TrashProductionRuntimeProbe.verifyRuleFieldState(service.snapshot(), true);
        assertions++;
        rejectsProbe(service.snapshot(), false);
        final var wall = field(UUID.randomUUID(), FieldKind.PROJECTILE_WALL, 2000);
        check(service.add(wall), "probe observes the native field service");
        rejectsProbe(service.snapshot(), true);
        check(service.claim(wall.center(), wall.kind()).isPresent(), "native claim admitted for shutdown proof");
        rejectsProbe(service.snapshot(), true);
        service.close();
        TrashProductionRuntimeProbe.verifyRuleFieldState(service.snapshot(), false);
        assertions++;
        rejectsProbe(service.snapshot(), true);
        for (boolean open : new boolean[]{true, false}) {
            rejectsProbe(new Snapshot(0, List.of(), Set.of(wall.id()), open), open);
            rejectsProbe(new Snapshot(0, List.of(wall), Set.of(), open), open);
        }
    }
    private static void claimIdentity() throws Exception {
        final var clock = new AtomicLong(1000); final var service = new TrashRuleFieldService(clock::get);
        final var wall = field(UUID.randomUUID(), FieldKind.PROJECTILE_WALL, 2000); service.add(wall);
        final var first = service.claim(wall.center(), wall.kind()).orElseThrow();
        check(service.isClaimed(first) && service.releaseClaim(first), "first native claim cannot release itself");
        final var second = service.claim(wall.center(), wall.kind()).orElseThrow();
        check(first != second && !service.isClaimed(first) && service.isClaimed(second), "new claim reuses retired admission identity");
        final long revision = service.snapshot().revision();
        try (var executor = Executors.newFixedThreadPool(8)) {
            final List<Future<Boolean>> stale = new ArrayList<>();
            for (int index = 0; index < 32; index++) stale.add(executor.submit(() ->
                    service.releaseClaim(first) || service.removeClaimed(first)));
            for (final var result : stale) check(!result.get(5, TimeUnit.SECONDS), "late callback removed a newer field/claim");
        }
        check(service.snapshot().revision() == revision && service.isClaimed(second), "stale callbacks changed canonical field generation");
        final var other = new TrashRuleFieldService(clock::get); other.add(wall);
        final var foreign = other.claim(wall.center(), wall.kind()).orElseThrow();
        check(!service.releaseClaim(foreign) && !service.removeClaimed(foreign), "claim from another authority instance was accepted");
        check(service.remove(wall) && service.add(wall), "native remove/re-add fixture failed");
        final var third = service.claim(wall.center(), wall.kind()).orElseThrow();
        check(!service.isClaimed(second) && !service.removeClaimed(second) && !service.releaseClaim(second),
                "removed/re-added identical field accepted an old token");
        clock.set(2000);
        check(!service.isClaimed(third) && service.removeClaimed(third),
                "expired operation cannot finish its own exact cleanup");
        check(service.snapshot().fields().isEmpty() && service.snapshot().claimed().isEmpty(), "exact completion left claim state behind");
        service.close();
        check(!service.releaseClaim(third) && !service.removeClaimed(third), "closed authority accepts a retired token");
    }
    private static void rejectsProbe(Snapshot snapshot, boolean expectedOpen) {
        try {
            TrashProductionRuntimeProbe.verifyRuleFieldState(snapshot, expectedOpen);
            throw new AssertionError("lifecycle proof accepted invalid state");
        } catch (IllegalStateException expected) { assertions++; }
    }
    private static void invalidValues() {
        final UUID world = UUID.randomUUID(); final var service = new TrashRuleFieldService(() -> 1000);
        check(!service.add(field(world, FieldKind.CEASEFIRE, 1000)), "already expired field refused");
        check(!service.add(field(world, FieldKind.CEASEFIRE, 1001 + MAX_LIFETIME_MILLIS)), "field lifetime bounded");
        for (double invalid : new double[]{Double.NaN, Double.POSITIVE_INFINITY, -1, 0, 33}) {
            try { new RuleField(UUID.randomUUID(), FieldKind.CEASEFIRE, new Point(world, 0, 0, 0), invalid, 2000, UUID.randomUUID(), null); throw new AssertionError("invalid radius accepted"); }
            catch (IllegalArgumentException expected) { assertions++; }
        }
        try { new Point(world, Double.NaN, 0, 0); throw new AssertionError("invalid point accepted"); } catch (IllegalArgumentException expected) { assertions++; }
    }
}
