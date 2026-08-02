package hu.taliann.icesmp.factions;

import hu.taliann.icesmp.data.FactionType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Dependency-free tax-origin, legacy migration and collection-contract regressions. */
public final class FactionTaxDebtRegressionSuite {

    private FactionTaxDebtRegressionSuite() {
    }

    public static void main(final String[] args) throws IOException {
        debtAndStrikesStayWithTheirOriginAfterSwitch();
        debtorsRemainCollectibleAfterAssignmentReset();
        legacyOriginEvidenceIsFailClosed();
        unresolvedLegacyGuestNeverBecomesImplicitNeutral();
        strikeLifecycleIsIsolatedPerOrigin();
        snapshotsAreImmutableAndValidated();
        managerPersistsAndCollectsByOriginContract();
        System.out.println("Faction tax debt regression suite passed.");
    }

    private static void legacyOriginEvidenceIsFailClosed() {
        check(FactionTaxDebtLedger.resolveLegacyOrigin(
                        Optional.of(FactionType.RED), Optional.of(FactionType.BLUE))
                        .orElseThrow() == FactionType.RED,
                "durable history overrode an active explicit membership");
        check(FactionTaxDebtLedger.resolveLegacyOrigin(
                        Optional.empty(), Optional.of(FactionType.BLUE))
                        .orElseThrow() == FactionType.BLUE,
                "durable history did not recover an assignment-free legacy origin");
        check(FactionTaxDebtLedger.resolveLegacyOrigin(
                        Optional.empty(), Optional.empty()).isEmpty(),
                "missing legacy evidence invented a NEUTRAL origin");
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
        check(ledger.playerIdsWithDebt().contains(unresolvedLegacy),
                "unresolved legacy debt disappeared from durable collection participants");
        expectFailure(() -> ledger.playerIdsWithDebt().add(UUID.randomUUID()),
                UnsupportedOperationException.class,
                "debtor snapshot must be immutable");
    }

    private static void unresolvedLegacyGuestNeverBecomesImplicitNeutral() {
        final FactionTaxDebtLedger ledger = new FactionTaxDebtLedger();
        final UUID playerId = UUID.randomUUID();
        ledger.putUnresolvedLegacy(playerId, 9.25D, 1);

        check(!ledger.bindUnresolvedLegacy(playerId, null),
                "an assignment-free guest bound legacy debt without explicit membership");
        checkDouble(0.0D, ledger.getArrears(playerId, FactionType.NEUTRAL),
                "origin-less legacy debt silently became NEUTRAL debt");
        checkDouble(9.25D, ledger.getTotalArrears(playerId),
                "guest legacy debt was deleted while waiting for an explicit faction");
        check(ledger.unresolvedLegacySnapshot().size() == 1,
                "unresolved legacy state was not durably representable");

        ledger.put(playerId, FactionType.BLUE, 1.0D, 1);
        check(ledger.bindUnresolvedLegacy(playerId, FactionType.BLUE),
                "explicit membership did not bind the legacy debt");
        check(ledger.unresolvedLegacySnapshot().isEmpty(),
                "bound legacy state remained duplicated in the unresolved bucket");
        checkDouble(10.25D, ledger.getArrears(playerId, FactionType.BLUE),
                "legacy amount was not merged with the explicit faction's debt");
        check(ledger.getEvasionStrikes(playerId, FactionType.BLUE) == 2,
                "legacy strikes were not merged with the explicit faction's strikes");
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
        check(source.contains("factionManager.getChosenFaction(playerId)")
                        && source.contains("factionManager.getLastChosenFaction(playerId)")
                        && source.contains("resolveLegacyOrigin(")
                        && source.contains("putUnresolvedLegacy(playerId"),
                "legacy migration lost active -> durable history -> unresolved precedence");
        check(source.contains("taxDebts.bindUnresolvedLegacy(citizenId, currentFaction)"),
                "explicit membership does not activate preserved legacy debt");
        check(source.contains("CurrencyType.fromFactionType(originFaction)")
                        && source.contains("collected.merge(originFaction, paid"),
                "old debt is not collected in its origin currency/treasury");
        check(source.contains("participants.addAll(taxDebts.playerIdsWithDebt())")
                        && source.contains("final FactionType currentFaction = assignments.get(citizenId)")
                        && source.contains("currentFaction == null || exempt.contains")
                        && !source.contains("factionManager.getFaction(citizenId)"),
                "assignment reset hides known debt or creates a new guest assessment");
        check(source.contains("evasionReportedThisRun.add(citizenId)"),
                "multiple origin accounts can report multiple sins in one collection run");
        check(source.contains("raw instanceof Number number")
                        && source.contains("value != Math.rint(value)"),
                "malformed debt amounts/strikes are no longer validated before publication");
        check(!source.contains("Map<UUID, Double> taxArrears")
                        && !source.contains("Map<UUID, Integer> evasionStrikes"),
                "scalar tax state survived alongside the origin-aware ledger");
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
