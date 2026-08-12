package hu.taliann.icesmp.playerprofile.application;

import hu.taliann.icesmp.data.CurrencyType;
import hu.taliann.icesmp.playerprofile.domain.section.EconomySection;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Comparator;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

final class EconomyReceiptLedger {

    static final int MAX_CREDIT_RECEIPTS = 256;
    static final int MAX_TAX_RECEIPTS = 128;
    static final int MAX_RECEIPTS = MAX_CREDIT_RECEIPTS + MAX_TAX_RECEIPTS;
    static final Duration REPLAY_WINDOW = Duration.ofDays(30);

    private static final String CREDIT_V1_PREFIX = "credit.";
    private static final String CREDIT_V2_PREFIX = "credit.v2.";
    private static final String TAX_V2_PREFIX = "tax.v2.";
    private static final String LEGACY_MIGRATED_AT = "receipt-ledger.legacy-migrated-at";

    private EconomyReceiptLedger() { }

    static Optional<String> credit(final Set<String> receipts, final String operationId) {
        final String digest = digestId(operationId);
        final String v1 = CREDIT_V1_PREFIX + digest + '.';
        final String v2 = CREDIT_V2_PREFIX + digest + '.';
        return receipts.stream().filter(value -> value.startsWith(v1) || value.startsWith(v2))
                .findFirst();
    }

    static boolean sameCredit(final String receipt, final String operationId,
                              final CurrencyType currency, final long amountMilli) {
        final String suffix = currency.name().toLowerCase(java.util.Locale.ROOT)
                + '.' + amountMilli;
        final String digest = digestId(operationId);
        return receipt.equals(CREDIT_V1_PREFIX + digest + '.' + suffix)
                || receipt.startsWith(CREDIT_V2_PREFIX + digest + '.' + suffix + '.');
    }

    static boolean taxSettled(final Set<String> receipts, final String operationId) {
        if (receipts.contains(operationId)) return true;
        final String prefix = TAX_V2_PREFIX + digestId(operationId) + '.';
        return receipts.stream().anyMatch(value -> value.startsWith(prefix));
    }

    static Update addCredit(final EconomySection current, final String operationId,
                            final CurrencyType currency, final long amountMilli,
                            final long nowEpochMillis) {
        final Migration migration = migrate(current, nowEpochMillis);
        final LinkedHashSet<String> receipts = migration.receipts();
        makeRoom(receipts, ReceiptType.CREDIT, MAX_CREDIT_RECEIPTS,
                migration.legacyMigratedAt(), nowEpochMillis);
        receipts.add(CREDIT_V2_PREFIX + digestId(operationId) + '.'
                + currency.name().toLowerCase(java.util.Locale.ROOT) + '.' + amountMilli + '.'
                + nowEpochMillis);
        requireTotalLimit(receipts);
        return new Update(receipts, migration.extensions());
    }

    static Update addTax(final EconomySection current, final String operationId,
                         final long nowEpochMillis) {
        final Migration migration = migrate(current, nowEpochMillis);
        final LinkedHashSet<String> receipts = migration.receipts();
        makeRoom(receipts, ReceiptType.TAX, MAX_TAX_RECEIPTS,
                migration.legacyMigratedAt(), nowEpochMillis);
        receipts.add(TAX_V2_PREFIX + digestId(operationId) + '.' + nowEpochMillis);
        requireTotalLimit(receipts);
        return new Update(receipts, migration.extensions());
    }

    private static Migration migrate(final EconomySection current, final long nowEpochMillis) {
        final LinkedHashSet<String> receipts = new LinkedHashSet<>(current.operationReceipts());
        final LinkedHashMap<String, Object> extensions = new LinkedHashMap<>(current.extensions());
        long migratedAt = longValue(extensions.get(LEGACY_MIGRATED_AT));
        if (migratedAt <= 0L && receipts.stream().anyMatch(EconomyReceiptLedger::legacy)) {
            migratedAt = nowEpochMillis;
            extensions.put(LEGACY_MIGRATED_AT, migratedAt);
        }
        return new Migration(receipts, extensions, migratedAt);
    }

    private static void makeRoom(final LinkedHashSet<String> receipts,
                                 final ReceiptType type, final int quota,
                                 final long legacyMigratedAt, final long nowEpochMillis) {
        final long count = receipts.stream().filter(value -> type.matches(value)).count();
        if (count < quota) return;
        final long cutoff = Math.subtractExact(nowEpochMillis, REPLAY_WINDOW.toMillis());
        final Optional<String> oldestExpired = receipts.stream()
                .filter(value -> type.matches(value))
                .map(value -> new DatedReceipt(value, createdAt(value, legacyMigratedAt)))
                .filter(value -> value.createdAt() > 0L && value.createdAt() <= cutoff)
                .min(Comparator.comparingLong(DatedReceipt::createdAt)
                        .thenComparing(DatedReceipt::value))
                .map(DatedReceipt::value);
        if (oldestExpired.isEmpty()) {
            throw new IllegalStateException(type.label
                    + " receipt quota is full inside the active replay window");
        }
        receipts.remove(oldestExpired.orElseThrow());
    }

    private static long createdAt(final String receipt, final long legacyMigratedAt) {
        if (legacy(receipt)) return legacyMigratedAt;
        final int separator = receipt.lastIndexOf('.');
        if (separator < 0 || separator == receipt.length() - 1) return -1L;
        try {
            return Long.parseLong(receipt.substring(separator + 1));
        } catch (final NumberFormatException malformed) {
            return -1L;
        }
    }

    private static boolean legacy(final String receipt) {
        return !receipt.startsWith(CREDIT_V2_PREFIX) && !receipt.startsWith(TAX_V2_PREFIX);
    }

    private static void requireTotalLimit(final Set<String> receipts) {
        if (receipts.size() > MAX_RECEIPTS) {
            throw new IllegalStateException("PlayerProfile economy receipt ledger is full");
        }
    }

    private static long longValue(final Object value) {
        if (value instanceof Number number) return number.longValue();
        return 0L;
    }

    private static String digestId(final String operationId) {
        try {
            final byte[] hash = MessageDigest.getInstance("SHA-256").digest(
                    Objects.requireNonNull(operationId, "operationId")
                            .getBytes(StandardCharsets.UTF_8));
            return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (final NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    record Update(Set<String> receipts, Map<String, Object> extensions) {
        Update {
            receipts = Collections.unmodifiableSet(new LinkedHashSet<>(receipts));
            extensions = Collections.unmodifiableMap(new LinkedHashMap<>(extensions));
        }
    }

    private record Migration(LinkedHashSet<String> receipts,
                             LinkedHashMap<String, Object> extensions,
                             long legacyMigratedAt) { }

    private record DatedReceipt(String value, long createdAt) { }

    private enum ReceiptType {
        CREDIT("wallet credit") {
            @Override boolean matches(final String receipt) {
                return receipt.startsWith(CREDIT_V1_PREFIX)
                        || receipt.startsWith(CREDIT_V2_PREFIX);
            }
        },
        TAX("tax settlement") {
            @Override boolean matches(final String receipt) {
                return !CREDIT.matches(receipt);
            }
        };

        private final String label;

        ReceiptType(final String label) { this.label = label; }

        abstract boolean matches(String receipt);
    }
}
