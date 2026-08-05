package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.data.CurrencyType;
import hu.taliann.icesmp.playerprofile.domain.PlayerProfileOperation;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/** Pure exact-once identity and recovery rules for spell mastery upgrades. */
public final class SpellMasteryTransactionProtocol {

    public static final String OPERATION_PREFIX = "spell-mastery:";
    public static final String OPERATION_TYPE = "spell-mastery-upgrade";

    private SpellMasteryTransactionProtocol() {
    }

    public static Identity create(final UUID playerId, final String rawSpell,
                                  final int previousRank, final int targetRank,
                                  final CurrencyType currency, final long cost,
                                  final UUID nonce) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(nonce, "nonce");
        final String spell = normalizeSpell(rawSpell);
        if (previousRank < 0 || targetRank != Math.addExact(previousRank, 1) || cost <= 0L) {
            throw new IllegalArgumentException("invalid spell mastery transition");
        }
        final String fingerprint = OPERATION_PREFIX + playerId + ':' + spell + ':'
                + previousRank + ':' + targetRank + ':' + currency.name() + ':' + cost;
        final String digest = digest(fingerprint);
        final String operationId = OPERATION_PREFIX + playerId + ':' + digest + ':' + nonce;
        if (operationId.length() > 192) {
            throw new IllegalArgumentException("spell mastery operation id exceeds wallet limit");
        }
        return new Identity(playerId, spell, previousRank, targetRank, currency,
                cost, operationId, fingerprint, digest);
    }

    public static Identity verifyCommittedReceipt(
            final CurrencyManager.DurableWalletOperation wallet,
            final PlayerProfileOperation receipt) {
        Objects.requireNonNull(wallet, "wallet");
        Objects.requireNonNull(receipt, "receipt");
        if (!wallet.operationId().equals(receipt.operationId())
                || receipt.status() != PlayerProfileOperation.Status.COMMITTED
                || !OPERATION_TYPE.equals(receipt.type())) {
            throw new IllegalStateException("spell mastery receipt is not committed");
        }
        final ParsedOperationId operation = parseOperationId(wallet.operationId());
        final ParsedFingerprint fingerprint = parseFingerprint(receipt.fingerprint());
        if (!operation.playerId().equals(wallet.playerId())
                || !fingerprint.playerId().equals(wallet.playerId())
                || fingerprint.currency() != wallet.currency()
                || Double.compare(wallet.amount(), (double) fingerprint.cost()) != 0
                || !operation.digest().equals(digest(receipt.fingerprint()))) {
            throw new IllegalStateException("spell mastery wallet/receipt identity mismatch");
        }
        return new Identity(fingerprint.playerId(), fingerprint.spell(),
                fingerprint.previousRank(), fingerprint.targetRank(), fingerprint.currency(),
                fingerprint.cost(), wallet.operationId(), receipt.fingerprint(), operation.digest());
    }

    public static String normalizeSpell(final String spellId) {
        if (spellId == null || spellId.isBlank()) {
            return "";
        }
        final String normalized = spellId.trim().toLowerCase(Locale.ROOT);
        if (!normalized.matches("[a-z0-9][a-z0-9_.-]{0,95}")) {
            throw new IllegalArgumentException("invalid spell id: " + spellId);
        }
        return normalized;
    }

    private static ParsedOperationId parseOperationId(final String operationId) {
        final String[] parts = operationId.split(":", -1);
        if (parts.length != 4 || !"spell-mastery".equals(parts[0])) {
            throw new IllegalStateException("malformed spell mastery operation id");
        }
        try {
            final UUID playerId = UUID.fromString(parts[1]);
            if (!parts[2].matches("[0-9a-f]{24}")) {
                throw new IllegalArgumentException("invalid digest");
            }
            UUID.fromString(parts[3]);
            return new ParsedOperationId(playerId, parts[2]);
        } catch (final IllegalArgumentException failure) {
            throw new IllegalStateException("malformed spell mastery operation id", failure);
        }
    }

    private static ParsedFingerprint parseFingerprint(final String fingerprint) {
        final String[] parts = fingerprint.split(":", -1);
        if (parts.length != 7 || !"spell-mastery".equals(parts[0])) {
            throw new IllegalStateException("malformed spell mastery fingerprint");
        }
        try {
            final UUID playerId = UUID.fromString(parts[1]);
            final String spell = normalizeSpell(parts[2]);
            final int previousRank = Integer.parseInt(parts[3]);
            final int targetRank = Integer.parseInt(parts[4]);
            final CurrencyType currency = CurrencyType.valueOf(parts[5]);
            final long cost = Long.parseLong(parts[6]);
            if (previousRank < 0 || targetRank != Math.addExact(previousRank, 1) || cost <= 0L) {
                throw new IllegalArgumentException("invalid fingerprint transition");
            }
            return new ParsedFingerprint(playerId, spell, previousRank, targetRank, currency, cost);
        } catch (final IllegalArgumentException failure) {
            throw new IllegalStateException("malformed spell mastery fingerprint", failure);
        }
    }

    private static String digest(final String fingerprint) {
        try {
            final byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(fingerprint.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash, 0, 12);
        } catch (final NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    public record Identity(UUID playerId, String spell, int previousRank,
                           int targetRank, CurrencyType currency, long cost,
                           String operationId, String fingerprint, String digest) {
        public Identity {
            Objects.requireNonNull(playerId, "playerId");
            spell = normalizeSpell(spell);
            Objects.requireNonNull(currency, "currency");
            Objects.requireNonNull(operationId, "operationId");
            Objects.requireNonNull(fingerprint, "fingerprint");
            Objects.requireNonNull(digest, "digest");
        }
    }

    private record ParsedOperationId(UUID playerId, String digest) {
    }

    private record ParsedFingerprint(UUID playerId, String spell, int previousRank,
                                     int targetRank, CurrencyType currency, long cost) {
    }
}
