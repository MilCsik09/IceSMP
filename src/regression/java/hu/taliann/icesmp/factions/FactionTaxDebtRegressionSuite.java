package hu.taliann.icesmp.factions;

import hu.taliann.icesmp.data.FactionType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Dependency-free tax-origin, legacy migration and collection-contract regressions. */
public final class FactionTaxDebtRegressionSuite {

    private FactionTaxDebtRegressionSuite() {
    }

    public static void main(final String[] args) throws IOException {
        debtAndStrikesStayWithTheirOriginAfterSwitch();
        debtorsRemainCollectibleAfterAssignmentReset();
        scalarLegacyOriginIsNeverInferred();
        unresolvedLegacyDebtRemainsQuarantined();
        membershipSnapshotRestoresAssignmentFreeHistory();
        durableTransactionFailureMatrix();
        durableRecoveryMatrixIsFailClosed();
        strikeLifecycleIsIsolatedPerOrigin();
        snapshotsAreImmutableAndValidated();
        managerPersistsAndCollectsByOriginContract();
        System.out.println("Faction tax debt regression suite passed.");
    }

    private static void scalarLegacyOriginIsNeverInferred() throws IOException {
        final String manager = Files.readString(Path.of(
                "src/main/java/hu/taliann/icesmp/managers/FactionTreasuryManager.java"));
        check(!manager.contains("resolveLegacyOrigin(")
                        && !manager.contains("getLastChosenFaction(playerId)")
                        && manager.contains("taxDebts.putUnresolvedLegacy(playerId"),
                "scalar legacy debt still guesses an origin from current or historical membership");
    }

    private static void debtAndStrikesStayWithTheirOriginAfterSwitch() {
        final FactionTaxDebtLedger ledger = new FactionTaxDebtLedger();
        final UUID playerId = UUID.randomUUID();

        ledger.put(playerId, FactionType.RED, 12.50D, 2);
        // A later BLUE assessment is a second account, not a conversion of the RED debt.
        ledger.put(playerId, FactionType.BLUE, 3.00D, 0);

        checkDouble(12.50D, ledger.getArrears(playerId, FactionType.RED),
                "RED arrears changed currency after a BLUE switch");
        check(ledger.getEvasionStrikes(playerId, FactionType.RED) == 2,
                "RED evasion strikes changed origin after a BLUE switch");
        checkDouble(3.00D, ledger.getArrears(playerId, FactionType.BLUE),
                "the new BLUE assessment was not isolated");
        checkDouble(15.50D, ledger.getTotalArrears(playerId),
                "total arrears did not include both origin accounts");
    }

    private static void debtorsRemainCollectibleAfterAssignmentReset() {
        final FactionTaxDebtLedger ledger = new FactionTaxDebtLedger();
        final UUID knownDebtor = UUID.randomUUID();
        final UUID unresolvedLegacy = UUID.randomUUID();
        ledger.put(knownDebtor, FactionType.RED, 7.5D, 0);
        ledger.putUnresolvedLegacy(unresolvedLegacy, 4.0D, 1);

        check(ledger.playerIdsWithDebt().contains(knownDebtor),
                "known-origin debt disappeared when current assignment was reset");
        check(!ledger.playerIdsWithDebt().contains(unresolvedLegacy)
                        && ledger.quarantinedLegacyPlayerIds().contains(unresolvedLegacy),
                "unknown-origin legacy debt entered runtime collection participants");
        expectFailure(() -> ledger.playerIdsWithDebt().add(UUID.randomUUID()),
                UnsupportedOperationException.class,
                "debtor snapshot must be immutable");
    }

    private static void unresolvedLegacyDebtRemainsQuarantined() {
        final FactionTaxDebtLedger ledger = new FactionTaxDebtLedger();
        final UUID playerId = UUID.randomUUID();
        ledger.putUnresolvedLegacy(playerId, 9.25D, 1);

        checkDouble(0.0D, ledger.getArrears(playerId, FactionType.NEUTRAL),
                "origin-less legacy debt silently became NEUTRAL debt");
        checkDouble(9.25D, ledger.getTotalArrears(playerId),
                "quarantined legacy debt was silently deleted");
        check(ledger.hasQuarantinedLegacy(playerId)
                        && !ledger.playerIdsWithDebt().contains(playerId),
                "quarantined legacy debt became runtime-collectible");

        ledger.put(playerId, FactionType.BLUE, 1.0D, 1);
        checkDouble(1.0D, ledger.getArrears(playerId, FactionType.BLUE),
                "new BLUE debt did not remain independent");
        checkDouble(10.25D, ledger.getTotalArrears(playerId),
                "explicit membership silently consumed quarantined legacy debt");
        check(ledger.hasQuarantinedLegacy(playerId),
                "explicit membership automatically bound unknown-origin legacy debt");
    }

    private static void membershipSnapshotRestoresAssignmentFreeHistory() {
        final UUID playerId = UUID.randomUUID();
        final Map<UUID, FactionType> assignments = new HashMap<>();
        final Map<UUID, FactionType> history = new HashMap<>();
        history.put(playerId, FactionType.RED);
        final FactionMembershipMutation.Snapshot before =
                FactionMembershipMutation.capture(assignments, history, playerId);
        FactionMembershipMutation.assign(assignments, history, playerId, FactionType.BLUE);
        FactionMembershipMutation.restore(assignments, history, before);
        check(!assignments.containsKey(playerId)
                        && history.get(playerId) == FactionType.RED,
                "paid reset lifecycle could not restore an assignment-free durable history");
    }

    private static void durableTransactionFailureMatrix() {
        final TransactionProbe success = new TransactionProbe();
        final DurableTransactionProtocol.ExecutionResult successResult =
                DurableTransactionProtocol.execute(success);
        check(!successResult.recoveryPending()
                        && success.wallet == 1 && success.domain == 1 && success.completed == 1,
                "successful transaction did not commit both stores and clear the WAL");

        final TransactionProbe walletFailure = new TransactionProbe();
        walletFailure.failWallet = true;
        expectFailure(() -> DurableTransactionProtocol.execute(walletFailure),
                IllegalStateException.class, "wallet failure did not escape");
        check(walletFailure.wallet == 0 && walletFailure.domain == 0
                        && walletFailure.rollback == 0 && walletFailure.completed == 1,
                "wallet failure changed domain state or left a needless WAL");

        final TransactionProbe domainFailure = new TransactionProbe();
        domainFailure.failDomain = true;
        expectFailure(() -> DurableTransactionProtocol.execute(domainFailure),
                IllegalStateException.class, "domain failure did not escape");
        check(domainFailure.wallet == 0 && domainFailure.domain == 0
                        && domainFailure.rollback == 1 && domainFailure.completed == 1,
                "domain failure did not durably compensate the wallet");

        final TransactionProbe criticalDomainFailure = new TransactionProbe();
        criticalDomainFailure.failDomainWithError = true;
        expectFailure(() -> DurableTransactionProtocol.execute(criticalDomainFailure),
                AssertionError.class, "critical persistence error did not escape");
        check(criticalDomainFailure.wallet == 0 && criticalDomainFailure.rollback == 1
                        && criticalDomainFailure.completed == 1,
                "critical persistence error skipped durable compensation");

        final TransactionProbe rollbackFailure = new TransactionProbe();
        rollbackFailure.failDomain = true;
        rollbackFailure.failRollback = true;
        final Throwable rollbackThrown = captureFailure(
                () -> DurableTransactionProtocol.execute(rollbackFailure));
        check(rollbackThrown instanceof IllegalStateException
                        && rollbackThrown.getSuppressed().length == 1
                        && rollbackFailure.wallet == 1
                        && rollbackFailure.completed == 0,
                "rollback failure did not fail closed with the WAL retained for recovery");

        final TransactionProbe completionFailure = new TransactionProbe();
        completionFailure.failComplete = true;
        final DurableTransactionProtocol.ExecutionResult completionResult =
                DurableTransactionProtocol.execute(completionFailure);
        check(completionResult.recoveryPending()
                        && completionResult.cleanupFailure() instanceof IllegalStateException
                        && completionFailure.wallet == 1 && completionFailure.domain == 1
                        && completionFailure.rollback == 0,
                "post-commit WAL cleanup failure was reported as a failed/compensated transaction");

        final TransactionProbe noWallet = new TransactionProbe();
        noWallet.hasWallet = false;
        final DurableTransactionProtocol.ExecutionResult noWalletResult =
                DurableTransactionProtocol.execute(noWallet);
        check(!noWalletResult.recoveryPending()
                        && noWallet.wallet == 0 && noWallet.domain == 1 && noWallet.completed == 1,
                "zero-payment tax transaction did not commit domain state idempotently");
    }


    private static void durableRecoveryMatrixIsFailClosed() {
        check(DurableRecoveryPolicy.decide(false, true, false, true, true)
                        == DurableRecoveryPolicy.Decision.COMPLETE_COMMITTED,
                "all-after state was not completed idempotently");
        check(DurableRecoveryPolicy.decide(true, false, true, false, true)
                        == DurableRecoveryPolicy.Decision.DISCARD_UNAPPLIED,
                "all-before state was not discarded idempotently");
        check(DurableRecoveryPolicy.decide(true, false, false, true, true)
                        == DurableRecoveryPolicy.Decision.ROLLBACK_WALLET,
                "wallet-only commit was not classified for compensation");
        check(DurableRecoveryPolicy.decide(false, true, true, false, true)
                        == DurableRecoveryPolicy.Decision.ROLLBACK_DOMAIN,
                "domain-only commit was not classified for rollback");
        check(DurableRecoveryPolicy.decide(false, true, true, true, false)
                        == DurableRecoveryPolicy.Decision.COMPLETE_COMMITTED,
                "domain-only tax transaction without wallet was not completed");
        check(DurableRecoveryPolicy.decide(true, false, true, true, false)
                        == DurableRecoveryPolicy.Decision.DISCARD_UNAPPLIED,
                "unapplied no-wallet tax transaction was not discarded");

        final boolean[] values = {false, true};
        for (final boolean domainBefore : values) {
            for (final boolean domainAfter : values) {
                for (final boolean walletBefore : values) {
                    for (final boolean walletAfter : values) {
                        final DurableRecoveryPolicy.Decision decision =
                                DurableRecoveryPolicy.decide(domainBefore, domainAfter,
                                        walletBefore, walletAfter, true);
                        final boolean known = domainAfter && walletAfter
                                || domainBefore && walletBefore
                                || domainBefore && walletAfter
                                || domainAfter && walletBefore;
                        if (!known) {
                            check(decision == DurableRecoveryPolicy.Decision.AMBIGUOUS,
                                    "unknown recovery state did not fail closed: "
                                            + domainBefore + "/" + domainAfter + "/"
                                            + walletBefore + "/" + walletAfter);
                        }
                    }
                }
            }
        }
    }


    private static void strikeLifecycleIsIsolatedPerOrigin() {
        final FactionTaxDebtLedger ledger = new FactionTaxDebtLedger();
        final UUID playerId = UUID.randomUUID();
        ledger.put(playerId, FactionType.RED, 50.0D, 1);
        ledger.put(playerId, FactionType.BLUE, 50.0D, 2);

        check(ledger.incrementEvasionStrikes(playerId, FactionType.RED, 3) == 2,
                "RED strike did not increment independently");
        check(ledger.getEvasionStrikes(playerId, FactionType.BLUE) == 2,
                "RED strike mutation leaked into BLUE debt");
        check(ledger.clearEvasionStrikes(playerId, FactionType.RED),
                "RED strike reset was not reported");
        check(ledger.getEvasionStrikes(playerId, FactionType.RED) == 0,
                "RED strike reset failed");
        check(ledger.getEvasionStrikes(playerId, FactionType.BLUE) == 2,
                "RED strike reset erased BLUE strikes");
        ledger.put(playerId, FactionType.DARK, 50.0D, Integer.MAX_VALUE);
        check(ledger.incrementEvasionStrikes(playerId, FactionType.DARK,
                        Integer.MAX_VALUE) == Integer.MAX_VALUE,
                "maximum strike counter overflowed into a negative value");

        check(ledger.setArrears(playerId, FactionType.RED, 0.0D),
                "settled RED debt did not report a state change");
        check(ledger.debtsFor(playerId).stream()
                        .noneMatch(debt -> debt.faction() == FactionType.RED),
                "empty RED debt bucket leaked after settlement");
    }

    private static void snapshotsAreImmutableAndValidated() {
        final FactionTaxDebtLedger ledger = new FactionTaxDebtLedger();
        final UUID playerId = UUID.randomUUID();
        ledger.put(playerId, FactionType.DARK, 4.0D, 0);
        final List<FactionTaxDebtLedger.Debt> snapshot = ledger.snapshot();
        expectFailure(() -> snapshot.add(new FactionTaxDebtLedger.Debt(
                playerId, FactionType.RED, 1.0D, 0)),
                UnsupportedOperationException.class,
                "debt snapshot must be immutable");
        expectFailure(() -> ledger.put(playerId, FactionType.RED, -1.0D, 0),
                IllegalArgumentException.class,
                "negative arrears must fail validation");
        expectFailure(() -> ledger.put(playerId, FactionType.RED,
                        Double.POSITIVE_INFINITY, 0),
                IllegalArgumentException.class,
                "non-finite arrears must fail validation");
        expectFailure(() -> ledger.put(playerId, null, 1.0D, 0),
                IllegalArgumentException.class,
                "origin-less modern debt must fail validation");
    }

    private static void managerPersistsAndCollectsByOriginContract() throws IOException {
        final String source = Files.readString(Path.of(
                "src/main/java/hu/taliann/icesmp/managers/FactionTreasuryManager.java"));

        check(source.contains("tax-debts.")
                        && source.contains("legacy-tax-debts-unresolved."),
                "origin-aware and unresolved-legacy persistence roots are missing");
        check(!source.contains("resolveLegacyOrigin(")
                        && !source.contains("getLastChosenFaction(playerId)")
                        && source.contains("putUnresolvedLegacy(playerId"),
                "scalar legacy migration still infers an unproven faction/currency origin");
        check(!source.contains("bindUnresolvedLegacy(")
                        && source.contains("quarantinedLegacyPlayerIds()"),
                "unknown-origin legacy debt is still automatically rebound at runtime");
        check(source.contains("CurrencyType.fromFactionType(origin)"),
                "old debt is not collected in its origin currency/treasury");
        check(source.contains("participants.addAll(taxDebts.playerIdsWithDebt())")
                        && source.contains("final FactionType currentFaction = assignments.get(citizenId)")
                        && source.contains("currentFaction == null || exempt.contains")
                        && !source.contains("factionManager.getFaction(citizenId)"),
                "assignment reset hides known debt or creates a new guest assessment");
        check(source.contains("!evasionReportedThisRun.contains(citizenId)")
                        && source.contains("evasionReportedThisRun.add(citizenId)"),
                "multiple origin accounts can report multiple sins in one collection run");
        check(source.contains("DurableTransactionProtocol.execute(")
                        && source.contains("currencyManager.rollbackDurably")
                        && source.contains("YamlStore.registerCriticalWrite(storageFile)"),
                "tax collection bypasses the durable WAL/rollback/critical-store protocol");
        check(source.contains("final ConfigManager.ConfigSnapshot config = configManager.snapshot()")
                        && source.contains("getTaxRate(currentFaction, config)"),
                "one tax cycle can mix values from different config generations");
        check(source.contains("taxJournal.prepare(")
                        && source.contains("recoverPendingTaxTransaction()")
                        && source.contains("DurableRecoveryPolicy.decide(")
                        && source.contains("applyDomainState(entry.playerId(), entry.origin(), entry.before())"),
                "tax transaction lacks durable WAL recovery or in-memory domain rollback");
        final String membership = Files.readString(Path.of(
                "src/main/java/hu/taliann/icesmp/managers/FactionManager.java"));
        final String switchJournal = Files.readString(Path.of(
                "src/main/java/hu/taliann/icesmp/factions/FactionSwitchJournal.java"));
        final String currency = Files.readString(Path.of(
                "src/main/java/hu/taliann/icesmp/managers/CurrencyManager.java"));
        check(membership.contains("FactionMembershipMutation.capture(")
                        && switchJournal.contains("membership-before.assignment-present")
                        && switchJournal.contains("membership-before.history-present"),
                "paid switch WAL cannot restore an assignment-free player with durable history");
        check(currency.contains("catch (final RuntimeException | Error failure)")
                        && currency.contains("runCompensatingCurrencyMutation"),
                "critical wallet write failures skip memory restore or durable compensation");
        final String join = Files.readString(Path.of(
                "src/main/java/hu/taliann/icesmp/commands/faction/FactionJoinSubcommand.java"));
        check(join.contains("if (hasPriorChoice")
                        && join.contains("FactionSwitchRules.passesSeasonRules("),
                "first explicit choice is incorrectly blocked by season switch rules");
        check(source.contains("raw instanceof Number number")
                        && source.contains("value != Math.rint(value)")
                        && source.contains("YamlStore.failCorrupt(storageFile"),
                "malformed persisted treasury/debt data is silently normalized or rewritten");
        check(!source.contains("Map<UUID, Double> taxArrears")
                        && !source.contains("Map<UUID, Integer> evasionStrikes"),
                "scalar tax state survived alongside the origin-aware ledger");
    }

    private static Throwable captureFailure(final Runnable action) {
        try {
            action.run();
        } catch (final Throwable thrown) {
            return thrown;
        }
        throw new AssertionError("expected failure");
    }

    private static final class TransactionProbe implements DurableTransactionProtocol.Steps {
        private boolean hasWallet = true;
        private boolean failWallet;
        private boolean failDomain;
        private boolean failDomainWithError;
        private boolean failRollback;
        private boolean failComplete;
        private int wallet;
        private int domain;
        private int rollback;
        private int completed;

        @Override
        public void prepare() {
        }

        @Override
        public boolean hasWalletMutation() {
            return hasWallet;
        }

        @Override
        public void applyWallet() {
            if (failWallet) {
                throw new IllegalStateException("wallet");
            }
            wallet = 1;
        }

        @Override
        public void commitDomain() {
            if (failDomainWithError) {
                throw new AssertionError("critical-domain");
            }
            if (failDomain) {
                throw new IllegalStateException("domain");
            }
            domain = 1;
        }

        @Override
        public void rollbackWallet() {
            rollback++;
            if (failRollback) {
                throw new IllegalStateException("rollback");
            }
            wallet = 0;
        }

        @Override
        public void completeJournal() {
            if (failComplete) {
                throw new IllegalStateException("complete");
            }
            completed++;
        }
    }

    private static void expectFailure(final Runnable action,
                                      final Class<? extends Throwable> expectedType,
                                      final String message) {
        try {
            action.run();
        } catch (final Throwable thrown) {
            if (expectedType.isInstance(thrown)) {
                return;
            }
            throw new AssertionError(message + ": wrong exception " + thrown, thrown);
        }
        throw new AssertionError(message + ": expected " + expectedType.getSimpleName());
    }

    private static void checkDouble(final double expected, final double actual,
                                    final String message) {
        if (Math.abs(expected - actual) > 0.000_001D) {
            throw new AssertionError(message + ": expected=" + expected + ", actual=" + actual);
        }
    }

    private static void check(final boolean condition, final String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
