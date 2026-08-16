package hu.taliann.icesmp.playerprofile.application;

import hu.taliann.icesmp.data.CurrencyType;
import hu.taliann.icesmp.playerprofile.domain.PlayerProfileOperation;
import hu.taliann.icesmp.playerprofile.domain.ProfileSectionId;
import hu.taliann.icesmp.playerprofile.domain.section.EconomySection;
import hu.taliann.icesmp.playerprofile.persistence.YamlPlayerProfileRepository;
import hu.taliann.icesmp.playerprofile.transaction.YamlPlayerProfileTransactionManager;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.Map;
import java.util.UUID;

/** Final dependency-free regressions for PlayerProfile full-authority primitives. */
public final class PlayerProfileFullAuthorityRegressionSuite {
    private static int assertions;

    private PlayerProfileFullAuthorityRegressionSuite() { }

    public static void main(final String[] args) throws Exception {
        final Path root = Files.createTempDirectory("player-profile-full-authority-");
        final YamlPlayerProfileRepository repository = new YamlPlayerProfileRepository(root);
        final YamlPlayerProfileTransactionManager transactions =
                new YamlPlayerProfileTransactionManager(repository);
        final PlayerProfileService service = new PlayerProfileService(repository, transactions);
        final PlayerProfileAuthority authority = PlayerProfileAuthority.install(
                service, repository, transactions);
        try {
            final UUID victim = UUID.fromString("00000000-0000-0000-0000-000000001301");
            final UUID hunter = UUID.fromString("00000000-0000-0000-0000-000000001302");
            repository.loadSnapshot(victim).toCompletableFuture().join();
            repository.loadSnapshot(hunter).toCompletableFuture().join();

            verifyOperationReceipts(repository, victim);
            verifyPeriodBudgets(repository, victim);
            verifySpellbookState(repository, hunter);
            verifyBountyAndWalletRecovery(repository, victim, hunter);

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
        System.out.println("PlayerProfile full authority regression suite passed. assertions="
                + assertions);
    }

    private static void verifyOperationReceipts(final YamlPlayerProfileRepository repository,
                                                final UUID player) {
        final PlayerProfileOperationStore store = new PlayerProfileOperationStore();
        final PlayerProfileOperation prepared = store.prepare(player, "season:grant:1",
                "season-member-reward", "fingerprint-1", Map.of("season", "7"))
                .toCompletableFuture().join();
        check(prepared.status() == PlayerProfileOperation.Status.PREPARED,
                "operation prepared");
        final long revision = repository.cached(player).orElseThrow().operations().revision();
        check(store.prepare(player, "season:grant:1", "season-member-reward",
                        "fingerprint-1", Map.of("season", "7"))
                        .toCompletableFuture().join().equals(prepared),
                "prepare replay stable");
        check(repository.cached(player).orElseThrow().operations().revision() == revision,
                "prepare replay is no-op");
        expect(IllegalStateException.class, () -> store.prepare(player, "season:grant:1",
                "market-delivery", "fingerprint-1", Map.of()).toCompletableFuture().join());

        final PlayerProfileOperation committed = store.commit(player, "season:grant:1",
                "season-member-reward", "fingerprint-1").toCompletableFuture().join();
        check(committed.status() == PlayerProfileOperation.Status.COMMITTED,
                "operation committed");
        check(store.commit(player, "season:grant:1", "season-member-reward",
                        "fingerprint-1").toCompletableFuture().join().equals(committed),
                "commit replay stable");
        expect(IllegalStateException.class, () -> store.rollback(player, "season:grant:1",
                "season-member-reward", "fingerprint-1").toCompletableFuture().join());
    }

    private static void verifyPeriodBudgets(final YamlPlayerProfileRepository repository,
                                            final UUID player) {
        final PlayerProfileDailyBudgetStore store = new PlayerProfileDailyBudgetStore();
        final var first = store.reserve(player, "honor-duel.weekly", 42L, 1L, 2L)
                .toCompletableFuture().join();
        final var second = store.reserve(player, "honor-duel.weekly", 42L, 1L, 2L)
                .toCompletableFuture().join();
        final var denied = store.reserve(player, "honor-duel.weekly", 42L, 1L, 2L)
                .toCompletableFuture().join();
        check(first.allowed() && first.state().spent() == 1L, "first budget reservation");
        check(second.allowed() && second.state().spent() == 2L, "second budget reservation");
        check(!denied.allowed() && denied.state().spent() == 2L, "budget cap enforced");
        check(store.rollback(player, "honor-duel.weekly", second, 1L)
                .toCompletableFuture().join(), "latest reservation compensated");
        check(store.read(player, "honor-duel.weekly").spent() == 1L,
                "budget compensation durable");
        check(!store.rollback(player, "honor-duel.weekly", first, 1L)
                .toCompletableFuture().join(), "stale compensation rejected");

        repository.invalidate(player);
        repository.loadSnapshot(player).toCompletableFuture().join();
        check(store.read(player, "honor-duel.weekly").equals(
                        new PlayerProfileDailyBudgetStore.BudgetState(42L, 1L)),
                "daily budget and ordering survive repository restart");
        final var rollover = store.reserve(player, "honor-duel.weekly", 43L, 2L, 3L)
                .toCompletableFuture().join();
        check(rollover.allowed() && rollover.state().equals(
                        new PlayerProfileDailyBudgetStore.BudgetState(43L, 2L)),
                "new period resets spent value without resetting the durable serial");
        repository.invalidate(player);
        repository.loadSnapshot(player).toCompletableFuture().join();
        check(store.read(player, "honor-duel.weekly").equals(rollover.state()),
                "rollover state remains restart durable");

        PlayerProfileAuthority.current().putExtension(player, ProfileSectionId.ECONOMY,
                EconomySection.class, "budget.honor-duel.weekly.sum", "corrupt")
                .toCompletableFuture().join();
        repository.invalidate(player);
        repository.loadSnapshot(player).toCompletableFuture().join();
        expect(IllegalStateException.class, () -> store.read(player, "honor-duel.weekly"));
    }

    private static void verifySpellbookState(final YamlPlayerProfileRepository repository,
                                             final UUID player) {
        final PlayerProfileSpellbookStateStore store =
                new PlayerProfileSpellbookStateStore();
        check(store.selectedSpell(player).isEmpty(), "greenfield selected spell empty");
        check(store.select(player, "ice_bolt").toCompletableFuture().join()
                        .equals("ice_bolt"),
                "selected spell committed");
        final long selectedRevision = repository.cached(player).orElseThrow()
                .spellbook().revision();
        check(store.select(player, "ice_bolt").toCompletableFuture().join()
                        .equals("ice_bolt"),
                "selected spell replay stable");
        check(repository.cached(player).orElseThrow().spellbook().revision()
                        == selectedRevision,
                "selected spell replay is no-op");

        check(store.recordLastCast(player, "ice_bolt", 10_000L)
                        .toCompletableFuture().join() == 10_000L,
                "persistent cooldown committed");
        check(store.lastCast(player, "ice_bolt") == 10_000L,
                "persistent cooldown readable");
        expect(IllegalStateException.class, () -> store.recordLastCast(
                player, "ice_bolt", 9_999L).toCompletableFuture().join());

        repository.invalidate(player);
        repository.loadSnapshot(player).toCompletableFuture().join();
        check(store.selectedSpell(player).equals("ice_bolt"),
                "selected spell restart durable");
        check(store.lastCast(player, "ice_bolt") == 10_000L,
                "persistent cooldown restart durable");

        store.clearCooldowns(player).toCompletableFuture().join();
        check(store.lastCast(player, "ice_bolt") == 0L,
                "persistent cooldown reset");
        store.reset(player).toCompletableFuture().join();
        check(store.selectedSpell(player).isEmpty(), "selected spell reset");
    }

    private static void verifyBountyAndWalletRecovery(
            final YamlPlayerProfileRepository repository,
            final UUID victim,
            final UUID hunter) {
        final PlayerProfileSinStore sins = new PlayerProfileSinStore();
        final PlayerProfileBountyStore bounties = new PlayerProfileBountyStore();
        final PlayerProfileEconomyStore economy = new PlayerProfileEconomyStore();

        final var firstSin = sins.add(victim, 3, 99).toCompletableFuture().join().state();
        check(firstSin.count() == 3 && firstSin.generation() == 1L,
                "first sin generation created");
        final var reservation = bounties.reserve(victim, hunter, CurrencyType.NEUTRAL,
                        25_000L, 3, true, 0L)
                .toCompletableFuture().join().orElseThrow();
        check(reservation.created() && reservation.pending().payoutSequence() == 1L,
                "bounty outbox reserved");
        check(sins.read(victim).count() == 0, "bounty clear committed with outbox");
        check(!bounties.reserve(victim, UUID.randomUUID(), CurrencyType.NEUTRAL,
                        25_000L, 3, true, 0L)
                .toCompletableFuture().join().orElseThrow().created(),
                "pending bounty cannot be stolen by replay");

        final var credit = economy.creditOnce(hunter, CurrencyType.NEUTRAL,
                        reservation.pending().amountMilli(), reservation.pending().operationId())
                .toCompletableFuture().join();
        check(credit.applied() && credit.wallet().milli(CurrencyType.NEUTRAL) == 25_000L,
                "hunter wallet credited once");
        check(!economy.creditOnce(hunter, CurrencyType.NEUTRAL,
                        reservation.pending().amountMilli(), reservation.pending().operationId())
                .toCompletableFuture().join().applied(), "wallet credit replay is no-op");
        check(bounties.complete(victim, reservation.pending()).toCompletableFuture().join(),
                "bounty outbox completed");
        check(!bounties.complete(victim, reservation.pending()).toCompletableFuture().join(),
                "bounty completion replay is no-op");

        final var secondSin = sins.add(victim, 3, 99).toCompletableFuture().join().state();
        check(secondSin.generation() == 2L, "second sin generation advanced");
        final var second = bounties.reserve(victim, hunter, CurrencyType.RED,
                        10_000L, 3, false, 0L)
                .toCompletableFuture().join().orElseThrow().pending();
        check(second.payoutSequence() == 2L, "second bounty sequence advanced");

        repository.invalidate(victim);
        repository.invalidate(hunter);
        repository.loadSnapshot(victim).toCompletableFuture().join();
        repository.loadSnapshot(hunter).toCompletableFuture().join();
        check(bounties.pending(victim).orElseThrow().equals(second),
                "pending bounty survives restart");
        check(economy.readCached(hunter).milli(CurrencyType.NEUTRAL) == 25_000L,
                "wallet credit survives restart");
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

    @FunctionalInterface
    private interface Throwing { void run() throws Exception; }
}
