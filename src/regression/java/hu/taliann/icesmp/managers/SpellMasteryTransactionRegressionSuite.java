package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.data.CurrencyType;
import hu.taliann.icesmp.playerprofile.domain.PlayerProfileOperation;

import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

/** Dependency-free exact-once identity and recovery regressions for spell mastery. */
public final class SpellMasteryTransactionRegressionSuite {

    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000001086");
    private static final UUID NONCE = UUID.fromString("00000000-0000-0000-0000-000000001087");
    private static int assertions;

    private SpellMasteryTransactionRegressionSuite() {
    }

    public static void main(final String[] args) {
        roundTripCommittedReceipt();
        mismatchesFailClosed();
        identityValidation();
        System.out.println("Spell mastery transaction regression suite passed. assertions=" + assertions);
    }

    private static void roundTripCommittedReceipt() {
        final SpellMasteryTransactionProtocol.Identity identity =
                SpellMasteryTransactionProtocol.create(PLAYER, "Fire_Ball", 1, 2,
                        CurrencyType.RED, 100L, NONCE);
        check(identity.spell().equals("fire_ball"), "spell normalized");
        check(identity.operationId().length() <= 192, "operation id bounded");
        check(identity.operationId().startsWith("spell-mastery:" + PLAYER + ':'),
                "operation owner embedded");

        final CurrencyManager.DurableWalletOperation wallet = wallet(identity,
                CurrencyType.RED, 100.0D,
                CurrencyManager.DurableWalletOperationStatus.DEBITED);
        final PlayerProfileOperation receipt = receipt(identity,
                PlayerProfileOperation.Status.COMMITTED, identity.fingerprint());
        final SpellMasteryTransactionProtocol.Identity recovered =
                SpellMasteryTransactionProtocol.verifyCommittedReceipt(wallet, receipt);
        check(recovered.equals(identity), "identity round trip");
    }

    private static void mismatchesFailClosed() {
        final SpellMasteryTransactionProtocol.Identity identity =
                SpellMasteryTransactionProtocol.create(PLAYER, "root", 0, 1,
                        CurrencyType.BLUE, 50L, NONCE);
        final CurrencyManager.DurableWalletOperation wallet = wallet(identity,
                CurrencyType.BLUE, 50.0D,
                CurrencyManager.DurableWalletOperationStatus.DEBITED);
        expect(IllegalStateException.class, () ->
                SpellMasteryTransactionProtocol.verifyCommittedReceipt(wallet,
                        receipt(identity, PlayerProfileOperation.Status.PREPARED,
                                identity.fingerprint())));
        expect(IllegalStateException.class, () ->
                SpellMasteryTransactionProtocol.verifyCommittedReceipt(wallet,
                        receipt(identity, PlayerProfileOperation.Status.COMMITTED,
                                identity.fingerprint() + "-tampered")));

        final CurrencyManager.DurableWalletOperation wrongAmount = wallet(identity,
                CurrencyType.BLUE, 51.0D,
                CurrencyManager.DurableWalletOperationStatus.DEBITED);
        expect(IllegalStateException.class, () ->
                SpellMasteryTransactionProtocol.verifyCommittedReceipt(wrongAmount,
                        receipt(identity, PlayerProfileOperation.Status.COMMITTED,
                                identity.fingerprint())));
    }

    private static void identityValidation() {
        expect(IllegalArgumentException.class, () ->
                SpellMasteryTransactionProtocol.create(PLAYER, "bad:id", 0, 1,
                        CurrencyType.NEUTRAL, 10L, NONCE));
        expect(IllegalArgumentException.class, () ->
                SpellMasteryTransactionProtocol.create(PLAYER, "root", 1, 3,
                        CurrencyType.NEUTRAL, 10L, NONCE));
        expect(IllegalArgumentException.class, () ->
                SpellMasteryTransactionProtocol.create(PLAYER, "root", 0, 1,
                        CurrencyType.NEUTRAL, 0L, NONCE));
    }

    private static CurrencyManager.DurableWalletOperation wallet(
            final SpellMasteryTransactionProtocol.Identity identity,
            final CurrencyType currency,
            final double amount,
            final CurrencyManager.DurableWalletOperationStatus status) {
        final EnumMap<CurrencyType, Double> previous = zeroWallet();
        previous.put(currency, 500.0D);
        final EnumMap<CurrencyType, Double> expected = new EnumMap<>(previous);
        expected.put(currency, previous.get(currency) - amount);
        return new CurrencyManager.DurableWalletOperation(
                identity.operationId(), PLAYER, currency, amount, 1L, status,
                true, previous, expected);
    }

    private static PlayerProfileOperation receipt(
            final SpellMasteryTransactionProtocol.Identity identity,
            final PlayerProfileOperation.Status status,
            final String fingerprint) {
        return new PlayerProfileOperation(identity.operationId(),
                SpellMasteryTransactionProtocol.OPERATION_TYPE, status, fingerprint,
                Instant.EPOCH, Instant.EPOCH, Map.of());
    }

    private static EnumMap<CurrencyType, Double> zeroWallet() {
        final EnumMap<CurrencyType, Double> wallet = new EnumMap<>(CurrencyType.class);
        for (final CurrencyType type : CurrencyType.values()) {
            wallet.put(type, 0.0D);
        }
        return wallet;
    }

    private static void check(final boolean value, final String message) {
        assertions++;
        if (!value) {
            throw new AssertionError(message);
        }
    }

    private static void expect(final Class<? extends Throwable> expected,
                               final Throwing action) {
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

    @FunctionalInterface
    private interface Throwing {
        void run() throws Exception;
    }
}
