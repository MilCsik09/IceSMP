package hu.taliann.icesmp.playerprofile.application;

import hu.taliann.icesmp.crates.CrateLedger;
import hu.taliann.icesmp.playerprofile.domain.ProfileSectionId;
import hu.taliann.icesmp.playerprofile.domain.section.StatisticsSection;
import hu.taliann.icesmp.playerprofile.persistence.YamlPlayerProfileRepository;
import hu.taliann.icesmp.playerprofile.transaction.YamlPlayerProfileTransactionManager;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.Map;
import java.util.UUID;

/** Crate settlement receipt idempotency, stale-token rejection and reset regressions. */
public final class PlayerProfileCrateStoreRegressionSuite {
    private static int assertions;
    private PlayerProfileCrateStoreRegressionSuite() { }

    public static void main(final String[] args) throws Exception {
        final Path root = Files.createTempDirectory("player-profile-crate-");
        final YamlPlayerProfileRepository repository = new YamlPlayerProfileRepository(root);
        final YamlPlayerProfileTransactionManager transactions =
                new YamlPlayerProfileTransactionManager(repository);
        final PlayerProfileService service = new PlayerProfileService(repository, transactions);
        final PlayerProfileAuthority authority = PlayerProfileAuthority.install(
                service, repository, transactions);
        try {
            final UUID player = UUID.fromString("00000000-0000-0000-0000-000000004055");
            repository.loadSnapshot(player).toCompletableFuture().join();
            final PlayerProfileCrateStore store = new PlayerProfileCrateStore();
            final CrateLedger ledger = new CrateLedger();

            final CrateLedger.Mutation first = ledger.prepare(player, "Tesztjatekos", "napi",
                    1, 1_000L, 60_000L);
            final UUID openingA = UUID.fromString("11111111-1111-1111-1111-111111111111");
            check(store.applyMutation(player, first, openingA).toCompletableFuture().join()
                    == PlayerProfileCrateStore.ApplyStatus.APPLIED, "first settlement applied");
            check(store.applyMutation(player, first, openingA).toCompletableFuture().join()
                    == PlayerProfileCrateStore.ApplyStatus.ALREADY_APPLIED,
                    "replayed settlement resolves by receipt");

            final UUID openingB = UUID.fromString("22222222-2222-2222-2222-222222222222");
            check(store.applyMutation(player, first, openingB).toCompletableFuture().join()
                    == PlayerProfileCrateStore.ApplyStatus.STALE,
                    "stale token with a new opening id is rejected");

            ledger.apply(first);
            final CrateLedger.Mutation second = ledger.prepare(player, "Tesztjatekos", "napi",
                    2, 120_000L, 0L);
            final UUID openingC = UUID.fromString("33333333-3333-3333-3333-333333333333");
            check(store.applyMutation(player, second, openingC).toCompletableFuture().join()
                    == PlayerProfileCrateStore.ApplyStatus.APPLIED, "chained settlement applied");

            repository.invalidate(player);
            repository.loadSnapshot(player).toCompletableFuture().join();
            final StatisticsSection durable = (StatisticsSection) repository.cached(player)
                    .orElseThrow().section(ProfileSectionId.STATISTICS).orElseThrow().value();
            final PlayerProfileCrateStore.PlayerCrateState state =
                    PlayerProfileCrateStore.read(durable);
            check(state.counts().equals(Map.of("napi", 3L)), "counts restart durable");
            check(state.cooldowns().equals(Map.of("napi", 61_000L)),
                    "zero cooldown keeps the previous durable value");
            check("Tesztjatekos".equals(state.lastKnownName()), "name restart durable");
            check(state.recentOps().equals(java.util.List.of(openingA.toString(),
                    openingC.toString())), "receipts ordered and durable");

            check(store.reset(player, "masik").toCompletableFuture().join() == Boolean.FALSE,
                    "reset of an unknown crate is a no-op");
            check(store.reset(player, "napi").toCompletableFuture().join(),
                    "scoped reset removes count and cooldown");
            final StatisticsSection afterScoped = (StatisticsSection) repository.cached(player)
                    .orElseThrow().section(ProfileSectionId.STATISTICS).orElseThrow().value();
            final PlayerProfileCrateStore.PlayerCrateState scoped =
                    PlayerProfileCrateStore.read(afterScoped);
            check(scoped.counts().isEmpty() && scoped.cooldowns().isEmpty()
                    && "Tesztjatekos".equals(scoped.lastKnownName()),
                    "scoped reset keeps the stored name");
            check(store.reset(player, null).toCompletableFuture().join(),
                    "full reset removes the stored name");
            check(PlayerProfileCrateStore.read((StatisticsSection) repository.cached(player)
                    .orElseThrow().section(ProfileSectionId.STATISTICS).orElseThrow().value())
                    .isEmpty(), "full reset leaves an empty crate state");

            expect(IllegalArgumentException.class, () -> store.applyMutation(
                    UUID.fromString("00000000-0000-0000-0000-000000004056"), second, openingC));
            check(service.shutdown(Duration.ofSeconds(5)).toCompletableFuture().join().drained(),
                    "repository drained");
        } finally {
            authority.uninstall();
            try (var paths = Files.walk(root)) {
                paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                    try { Files.deleteIfExists(path); } catch (final Exception ignored) { }
                });
            }
        }
        System.out.println("PlayerProfile crate settlement regression suite passed. assertions=" + assertions);
    }

    private static void check(final boolean condition, final String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }

    private static void expect(final Class<? extends Throwable> expected, final Throwing action) {
        assertions++;
        try {
            action.run();
            throw new AssertionError("Expected " + expected.getSimpleName());
        } catch (final Throwable failure) {
            if (!expected.isInstance(failure)) {
                throw new AssertionError("Expected " + expected.getSimpleName()
                        + " but got " + failure, failure);
            }
        }
    }

    @FunctionalInterface private interface Throwing { void run() throws Exception; }
}
