package hu.taliann.icesmp.playerprofile.application;

import hu.taliann.icesmp.data.CurrencyType;
import hu.taliann.icesmp.playerprofile.persistence.YamlPlayerProfileRepository;
import hu.taliann.icesmp.playerprofile.transaction.YamlPlayerProfileTransactionManager;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.Map;
import java.util.UUID;

/** PlayerProfile wallet CAS, exact-once operation and restart regressions. */
public final class PlayerProfileEconomyStoreRegressionSuite {
    private static int assertions;

    private PlayerProfileEconomyStoreRegressionSuite() { }

    public static void main(final String[] args) throws Exception {
        final Path root = Files.createTempDirectory("player-profile-economy-");
        final YamlPlayerProfileRepository repository = new YamlPlayerProfileRepository(root);
        final YamlPlayerProfileTransactionManager transactions =
                new YamlPlayerProfileTransactionManager(repository);
        final PlayerProfileService service = new PlayerProfileService(repository, transactions);
        final PlayerProfileAuthority authority = PlayerProfileAuthority.install(
                service, repository, transactions);
        try {
            final UUID player = UUID.fromString("00000000-0000-0000-0000-000000001090");
            repository.loadSnapshot(player).toCompletableFuture().join();
            final PlayerProfileEconomyStore store = new PlayerProfileEconomyStore();

            check(PlayerProfileEconomyStore.toMilli(12.345D) == 12_345L,
                    "milli-unit conversion exact");
            check(Math.abs(PlayerProfileEconomyStore.fromMilli(12_345L) - 12.345D) < 0.000001D,
                    "milli-unit decode exact");

            final var funded = store.mutate(player, before -> {
                final var after = before.add(CurrencyType.RED, 50_000L)
                        .add(CurrencyType.BLUE, 7_500L);
                return PlayerProfileEconomyStore.Decision.changed(after, after);
            }).toCompletableFuture().join();
            check(funded.milli(CurrencyType.RED) == 50_000L, "red wallet funded");
            check(funded.milli(CurrencyType.BLUE) == 7_500L, "blue wallet funded");

            final var debit = store.debitOperation(player, CurrencyType.RED, 10.0D,
                    "market:test:0001").toCompletableFuture().join();
            check(debit != null && debit.status()
                    == PlayerProfileEconomyStore.OperationStatus.DEBITED, "debit committed");
            check(debit.expected().milli(CurrencyType.RED) == 40_000L,
                    "debit wallet snapshot");
            final long revision = repository.cached(player).orElseThrow().economy().revision();
            final var replay = store.debitOperation(player, CurrencyType.RED, 10.0D,
                    "market:test:0001").toCompletableFuture().join();
            check(replay.equals(debit), "debit replay stable");
            check(repository.cached(player).orElseThrow().economy().revision() == revision,
                    "debit replay is no-op");
            expect(IllegalStateException.class, () -> store.debitOperation(player,
                    CurrencyType.BLUE, 10.0D, "market:test:0001").toCompletableFuture().join());

            final var committed = store.transitionOperation(player, "market:test:0001",
                    PlayerProfileEconomyStore.OperationStatus.COMMITTED)
                    .toCompletableFuture().join();
            check(committed.status() == PlayerProfileEconomyStore.OperationStatus.COMMITTED,
                    "operation committed");
            check(store.transitionOperation(player, "market:test:0001",
                    PlayerProfileEconomyStore.OperationStatus.COMMITTED)
                    .toCompletableFuture().join().equals(committed), "terminal replay stable");

            final var rollbackDebit = store.debitOperation(player, CurrencyType.BLUE, 2.5D,
                    "crate:test:0002").toCompletableFuture().join();
            check(rollbackDebit.expected().milli(CurrencyType.BLUE) == 5_000L,
                    "rollback debit reserved");
            final var rolledBack = store.transitionOperation(player, "crate:test:0002",
                    PlayerProfileEconomyStore.OperationStatus.ROLLED_BACK)
                    .toCompletableFuture().join();
            check(rolledBack.status() == PlayerProfileEconomyStore.OperationStatus.ROLLED_BACK,
                    "operation rolled back");
            check(store.readCached(player).milli(CurrencyType.BLUE) == 7_500L,
                    "rollback restored wallet");

            final var expected = store.readCached(player);
            final var next = expected.add(CurrencyType.DARK, 1_000L);
            store.replace(player, expected, next).toCompletableFuture().join();
            expect(IllegalStateException.class, () -> store.replace(player, expected,
                    expected.add(CurrencyType.NEUTRAL, 1_000L)).toCompletableFuture().join());

            repository.invalidate(player);
            final var durable = repository.loadSnapshot(player).toCompletableFuture().join();
            check(durable.economy().value().wallets().get("red") == 40_000L,
                    "wallet restart durable");
            check(store.operationCached(player, "market:test:0001").orElseThrow().status()
                    == PlayerProfileEconomyStore.OperationStatus.COMMITTED,
                    "operation restart durable");
            check(store.operationsByPrefixCached(player, "crate:").size() == 1,
                    "prefix enumeration deterministic");

            check(service.shutdown(Duration.ofSeconds(5)).toCompletableFuture().join().drained(),
                    "repository drained");
        } finally {
            authority.uninstall();
            try (var paths = Files.walk(root)) {
                paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                    try { Files.deleteIfExists(path); }
                    catch (final Exception ignored) { }
                });
            }
        }
        System.out.println("PlayerProfile economy regression suite passed. assertions=" + assertions);
    }

    private static void check(final boolean condition, final String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }

    private static void expect(final Class<? extends Throwable> expected,
                               final Throwing action) {
        assertions++;
        try {
            action.run();
            throw new AssertionError("Expected " + expected.getSimpleName());
        } catch (final Throwable failure) {
            final Throwable root = unwrap(failure);
            if (!expected.isInstance(root)) {
                throw new AssertionError("Expected " + expected.getSimpleName()
                        + " but got " + root, root);
            }
        }
    }

    private static Throwable unwrap(final Throwable failure) {
        Throwable current = failure;
        while ((current instanceof java.util.concurrent.CompletionException
                || current instanceof java.util.concurrent.ExecutionException)
                && current.getCause() != null) current = current.getCause();
        return current;
    }

    @FunctionalInterface private interface Throwing { void run() throws Exception; }
}
