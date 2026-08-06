package hu.taliann.icesmp.playerprofile.application;

import hu.taliann.icesmp.data.CurrencyType;
import hu.taliann.icesmp.data.FactionType;
import hu.taliann.icesmp.playerprofile.persistence.YamlPlayerProfileRepository;
import hu.taliann.icesmp.playerprofile.transaction.YamlPlayerProfileTransactionManager;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.UUID;

/** Tax assessment, wallet deduction, debt, outbox and restart regressions. */
public final class PlayerProfileTaxStoreRegressionSuite {
    private static int assertions;

    private PlayerProfileTaxStoreRegressionSuite() { }

    public static void main(final String[] args) throws Exception {
        final Path root = Files.createTempDirectory("player-profile-tax-");
        final YamlPlayerProfileRepository repository = new YamlPlayerProfileRepository(root);
        final YamlPlayerProfileTransactionManager transactions =
                new YamlPlayerProfileTransactionManager(repository);
        final PlayerProfileService service = new PlayerProfileService(repository, transactions);
        final PlayerProfileAuthority authority = PlayerProfileAuthority.install(
                service, repository, transactions);
        try {
            final UUID player = UUID.fromString("00000000-0000-0000-0000-000000001093");
            repository.loadSnapshot(player).toCompletableFuture().join();
            final PlayerProfileEconomyStore economy = new PlayerProfileEconomyStore();
            final PlayerProfileTaxStore taxes = new PlayerProfileTaxStore();

            economy.mutate(player, before -> PlayerProfileEconomyStore.Decision.changed(
                    before.add(CurrencyType.RED, 30_000L), true))
                    .toCompletableFuture().join();

            final var first = taxes.collect(player, FactionType.RED,
                            20.0D, 50.0D, 2, "tax:test:1")
                    .toCompletableFuture().join();
            check(first.changed(), "first assessment changed profile");
            check(first.paidMilli() == 20_000L, "wallet paid assessment");
            check(first.owedAfterMilli() == 0L, "no debt with sufficient wallet");
            check(first.outbox() != null && first.outbox().paidMilli() == 20_000L,
                    "treasury outbox created");
            check(economy.readCached(player).milli(CurrencyType.RED) == 10_000L,
                    "wallet deducted in same CAS");
            check(taxes.pending(player).size() == 1, "outbox pending");

            final long revision = repository.cached(player).orElseThrow().economy().revision();
            final var replay = taxes.collect(player, FactionType.RED,
                            20.0D, 50.0D, 2, "tax:test:1")
                    .toCompletableFuture().join();
            check(!replay.changed(), "operation replay is no-op");
            check(repository.cached(player).orElseThrow().economy().revision() == revision,
                    "replay does not advance revision");

            check(taxes.settle(player, "tax:test:1").toCompletableFuture().join(),
                    "outbox settlement committed");
            check(!taxes.settle(player, "tax:test:1").toCompletableFuture().join(),
                    "settlement replay rejected");
            check(taxes.pending(player).isEmpty(), "settled outbox removed");

            final var partial = taxes.collect(player, FactionType.RED,
                            40.0D, 50.0D, 2, "tax:test:2")
                    .toCompletableFuture().join();
            check(partial.paidMilli() == 10_000L, "remaining wallet collected");
            check(partial.owedAfterMilli() == 30_000L, "origin debt retained");
            check(!partial.outbox().reportSin(), "sub-max debt does not strike");

            final var maxDebt = taxes.collect(player, FactionType.RED,
                            20.0D, 50.0D, 2, "tax:test:3")
                    .toCompletableFuture().join();
            check(maxDebt.owedBeforeMilli() == 30_000L
                    && maxDebt.owedAfterMilli() == 50_000L,
                    "debt reaches configured maximum");
            check(!maxDebt.outbox().reportSin(), "first max-debt strike not reported");
            check(taxes.debts(player).getFirst().evasionStrikes() == 1,
                    "first max-debt strike retained");

            final var reported = taxes.collect(player, FactionType.RED,
                            0.0D, 50.0D, 2, "tax:test:4")
                    .toCompletableFuture().join();
            check(reported.owedBeforeMilli() == 50_000L
                    && reported.owedAfterMilli() == 50_000L,
                    "max debt persists without wallet");
            check(reported.outbox().reportSin(), "threshold creates sin outbox");
            check(taxes.debts(player).getFirst().evasionStrikes() == 0,
                    "reported strike cycle resets atomically");
            check(taxes.arrearsMilli(player, FactionType.RED) == 50_000L,
                    "origin arrears readable");

            repository.invalidate(player);
            repository.loadSnapshot(player).toCompletableFuture().join();
            check(taxes.arrearsMilli(player, FactionType.RED) == 50_000L,
                    "arrears restart durable");
            check(taxes.pending(player).stream().anyMatch(outbox ->
                            outbox.operationId().equals("tax:test:2"))
                    && taxes.pending(player).stream().anyMatch(outbox ->
                            outbox.operationId().equals("tax:test:3"))
                    && taxes.pending(player).stream().anyMatch(outbox ->
                            outbox.operationId().equals("tax:test:4")),
                    "unsettled outboxes restart durable");
            check(economy.readCached(player).milli(CurrencyType.RED) == 0L,
                    "wallet deduction restart durable");

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
        System.out.println("PlayerProfile tax regression suite passed. assertions=" + assertions);
    }

    private static void check(final boolean condition, final String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}
