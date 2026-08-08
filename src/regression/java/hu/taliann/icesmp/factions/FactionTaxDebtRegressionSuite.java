package hu.taliann.icesmp.factions;

import hu.taliann.icesmp.playerprofile.application.PlayerProfileTaxStore;

import java.lang.reflect.Method;
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

    private static void evasionPolicyStaysPendingUntilDurableSettlement() throws Exception {
        final Decision rejected = invokeAfterCollection(
                2, 0.0D, 50.0D, 50.0D, 3, false);
        check(rejected.strikesAfter() == 3 && !rejected.reportSin(),
                "scheduler rejection cleared the durable tax-evasion threshold");

        final Decision offlineRetry = invokeAfterCollection(
                3, 50.0D, 0.0D, 50.0D, 3, false);
        check(offlineRetry.strikesAfter() == 3 && !offlineRetry.reportSin(),
                "offline repayment erased a pending tax-evasion delivery");

        final Decision ownerRetry = invokeAfterCollection(
                3, 50.0D, 0.0D, 50.0D, 3, true);
        check(ownerRetry.strikesAfter() == 3 && ownerRetry.reportSin(),
                "pending tax-evasion sin was not retried when a consumer became available");

        final Decision ordinaryPayment = invokeAfterCollection(
                2, 5.0D, 20.0D, 50.0D, 3, true);
        check(ordinaryPayment.strikesAfter() == 0 && !ordinaryPayment.reportSin(),
                "sub-threshold strikes survived a normal arrears recovery");

        final Decision disabled = invokeAfterCollection(
                3, 0.0D, 50.0D, 50.0D, 0, true);
        check(disabled.strikesAfter() == 0,
                "disabled evasion policy retained a stale pending threshold");
    }

    private static Decision invokeAfterCollection(
            final int strikesBefore,
            final double paid,
            final double owedAfter,
            final double maxArrears,
            final int threshold,
            final boolean consumerAvailable) throws Exception {
        final Method policy = PlayerProfileTaxStore.class.getDeclaredMethod(
                "afterCollection",
                int.class,
                double.class,
                double.class,
                double.class,
                int.class,
                boolean.class);
        policy.setAccessible(true);
        final Object result = policy.invoke(
                null, strikesBefore, paid, owedAfter, maxArrears, threshold, consumerAvailable);

        final Method strikesAccessor = result.getClass().getDeclaredMethod("strikesAfter");
        final Method reportAccessor = result.getClass().getDeclaredMethod("reportSin");
        strikesAccessor.setAccessible(true);
        reportAccessor.setAccessible(true);
        return new Decision(
                (int) strikesAccessor.invoke(result),
                (boolean) reportAccessor.invoke(result));
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
                        && !source.contains("FactionTaxJournal")
                        && !source.contains("tax-debts.")
                        && !source.contains("legacy-tax-debts")
                        && !source.contains("putUnresolvedLegacy")
                        && !source.contains("resolveLegacyOrigin"),
                "legacy tax debt authority, journal or migration remains in production manager");
        check(source.contains("CurrencyType.fromFactionType(origin)"),
                "tax debt is not collected in its origin currency");
    }

    private static void legacyTaxAuthorityIsRemoved() {
        check(Files.notExists(Path.of(
                        "src/main/java/hu/taliann/icesmp/factions/FactionTaxDebtLedger.java")),
                "legacy tax compatibility alias remains in the production tree");
        check(Files.notExists(Path.of(
                        "src/main/java/hu/taliann/icesmp/factions/FactionTaxJournal.java")),
                "legacy tax WAL remains in the production tree");
    }

    private static void check(final boolean condition, final String message) {
        if (!condition) throw new AssertionError(message);
    }

    private record Decision(int strikesAfter, boolean reportSin) { }
}
