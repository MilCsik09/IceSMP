package hu.taliann.icesmp.classspec.domain;

import java.math.BigDecimal;
import java.util.Objects;

/** Restart-durable idempotency receipt. */
public record ProfileOperation(
        String operationId, ProfileOperationType type, ProfileOperationStatus status,
        String target, String amount, String currencyId, long startedRevision, String detail) {

    public static final int MAX_ID_LENGTH = 160;
    public static final int MAX_DETAIL_LENGTH = 512;

    public ProfileOperation {
        operationId = required(operationId, "operationId", MAX_ID_LENGTH);
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(status, "status");
        target = clean(target, MAX_ID_LENGTH);
        amount = canonicalAmount(amount, type == ProfileOperationType.SOUL_SHARD_MUTATION);
        currencyId = ClassSpecCatalog.normalize(currencyId);
        if (startedRevision < 0L) {
            throw new IllegalArgumentException("Operation startedRevision must be non-negative");
        }
        detail = clean(detail, MAX_DETAIL_LENGTH);
        if (type == ProfileOperationType.SOULFORGE_UPGRADE && target.isEmpty()) {
            throw new IllegalArgumentException("Soulforge operation requires a target branch");
        }
        if (type == ProfileOperationType.RESPEC && currencyId.isEmpty()) {
            throw new IllegalArgumentException("Respec operation requires a currency id");
        }
    }

    public ProfileOperation withStatus(final ProfileOperationStatus next, final String nextDetail) {
        return new ProfileOperation(operationId, type, next, target, amount, currencyId,
                startedRevision, nextDetail);
    }

    public BigDecimal decimalAmount() { return new BigDecimal(amount); }

    private static String canonicalAmount(final String value, final boolean allowNegative) {
        final String raw = value == null || value.isBlank() ? "0" : value.trim();
        if (raw.indexOf('e') >= 0 || raw.indexOf('E') >= 0) {
            throw new IllegalArgumentException("Operation amount must not use scientific notation");
        }
        final BigDecimal decimal;
        try { decimal = new BigDecimal(raw); }
        catch (final NumberFormatException invalid) {
            throw new IllegalArgumentException("Operation amount must be decimal", invalid);
        }
        final BigDecimal normalized = decimal.stripTrailingZeros();
        final String plain = normalized.toPlainString();
        final long digits = plain.chars().filter(Character::isDigit).count();
        if ((!allowNegative && decimal.signum() < 0) || digits > 24 || Math.max(0, decimal.scale()) > 8) {
            throw new IllegalArgumentException("Operation amount is outside the supported range");
        }
        return plain;
    }

    private static String required(final String value, final String label, final int maximum) {
        final String cleaned = clean(value, maximum);
        if (cleaned.isEmpty()) throw new IllegalArgumentException(label + " must be non-blank");
        return cleaned;
    }

    private static String clean(final String value, final int maximum) {
        final String cleaned = value == null ? "" : value.trim();
        if (cleaned.length() > maximum)
            throw new IllegalArgumentException("Operation text exceeds " + maximum + " characters");
        return cleaned;
    }
}
