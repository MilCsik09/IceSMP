package hu.taliann.icesmp.factions;

import hu.taliann.icesmp.playerprofile.application.PlayerProfileTaxStore;

import java.nio.file.Files;
import java.nio.file.Path;

/** Greenfield tax authority and shared-treasury boundary regressions. */
public final class FactionTaxDebtRegressionSuite {

    private FactionTaxDebtRegressionSuite() { }

    public static void main(final String[] args) throws Exception {
        evasionPolicyStaysPendingUntilDurableSettlement();
        treasuryAmountMathFailsClosed();
        productionManagerUsesPlayerProfileTaxAuthority();
        legacyTaxAuthorityIsRemoved();
        System.out.println("Faction tax debt regression suite passed.");
    }

    private static void evasionPolicyStaysPendingUntilDurableSettlement() {
        final PlayerProfileTaxStore.EvasionDecision rejected =
                PlayerProfileTaxStore.afterCollection(
                        2, 0.0D, 50.0D, 50.0D, 3, false);
        check(rejected.strikesAfter() == 3 && !rejected.reportSin(),
                "scheduler rejection cleared the durable tax-evasion threshold");

        final PlayerProfileTaxStore.EvasionDecision offlineRetry =
                PlayerProfileTaxStore.afterCollection(
                        3, 50.0D, 0.0D, 50.0D, 3, false);
        check(offlineRetry.strikesAfter() == 3 && !offlineRetry.reportSin(),
                "offline repayment erased a pending tax-evasion delivery");

        final PlayerProfileTaxStore.EvasionDecision ownerRetry =
                PlayerProfileTaxStore.afterCollection(
                        3, 50.0D, 0.0D, 50.0D, 3, true);
        check(ownerRetry.strikesAfter() == 3 && ownerRetry.reportSin(),
                "pending tax-evasion sin was not retried when a consumer became available");

        final PlayerProfileTaxStore.EvasionDecision ordinaryPayment =
                PlayerProfileTaxStore.afterCollection(
                        2, 5.0D, 20.0D, 50.0D, 3, true);
        check(ordinaryPayment.strikesAfter() == 0 && !ordinaryPayment.reportSin(),
                "sub-threshold strikes survived a normal arrears recovery");
        check(PlayerProfileTaxStore.afterCollection(
                        3, 0.0D, 50.0D, 50.0D, 0, true).strikesAfter() == 0,
                "disabled evasion policy retained a stale pending threshold");
    }

    private static void treasuryAmountMathFailsClosed() {
        check(PlayerProfileTaxStore.checkedAmountAdd(10.0D, 2.5D) == 12.5D,
                "normal treasury addition changed");
        check(Double.isNaN(PlayerProfileTaxStore.checkedAmountAdd(
                        Double.MAX_VALUE, Double.MAX_VALUE))
                        && Double.isNaN(PlayerProfileTaxStore.checkedAmountAdd(
                        Double.POSITIVE_INFINITY, 1.0D))
                        && Double.isNaN(PlayerProfileTaxStore.checkedAmountAdd(1.0D, 0.0D))
                        && Double.isNaN(PlayerProfileTaxStore.checkedAmountAdd(-1.0D, 1.0D)),
                "invalid or overflowing treasury balance was accepted");
    }

    private static void productionManagerUsesPlayerProfileTaxAuthority() throws Exception {
        final String source = Files.readString(Path.of(
                "src/main/java/hu/taliann/icesmp/managers/FactionTreasuryManager.java"));
        check(source.contains("PlayerProfileTaxStore")
                        && source.contains("repository().listPlayerIds()")
                        && source.contains("taxStore.collect(")
                        && source.contains("taxStore.settle("),
                "treasury manager bypasses PlayerProfile tax authority/outbox recovery");
        check(!source.contains("FactionTaxDebtLedger")
                        && !source.contains("tax-debts.")
                        && !source.contains("legacy-tax-debts")
                        && !source.contains("putUnresolvedLegacy")
                        && !source.contains("resolveLegacyOrigin"),
                "legacy tax debt authority or migration remains in production manager");
        check(source.contains("CurrencyType.fromFactionType(origin)"),
                "tax debt is not collected in its origin currency");
    }

    private static void legacyTaxAuthorityIsRemoved() {
        check(Files.notExists(Path.of(
                        "src/main/java/hu/taliann/icesmp/factions/FactionTaxDebtLedger.java")),
                "legacy tax compatibility alias remains in the production tree");
    }

    private static void check(final boolean condition, final String message) {
        if (!condition) throw new AssertionError(message);
    }
}
