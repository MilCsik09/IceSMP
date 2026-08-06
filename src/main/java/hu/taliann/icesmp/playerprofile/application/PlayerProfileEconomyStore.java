package hu.taliann.icesmp.playerprofile.application;

import hu.taliann.icesmp.data.CurrencyType;
import hu.taliann.icesmp.playerprofile.domain.ProfileSectionId;
import hu.taliann.icesmp.playerprofile.domain.section.EconomySection;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

/**
 * Typed PlayerProfile economy authority.
 *
 * <p>Balances are persisted as milli-units in {@link EconomySection#wallets()} so the
 * public double API can remain source compatible without storing floating point YAML
 * scalars. A durable debit witness and its wallet before/after snapshots live in the
 * same economy section; debit, commit and rollback are therefore single-section CAS
 * operations and cannot be torn across a restart.</p>
 */
public final class PlayerProfileEconomyStore {

    public static final long SCALE = 1_000L;
    private static final String OPERATION_PREFIX = "wallet.op.";
    private static final String CODEC_VERSION = "v1";
    private static final int MAX_OPERATIONS = 128;

    public enum OperationStatus { DEBITED, COMMITTED, ROLLED_BACK }

    public record WalletState(Map<CurrencyType, Long> milli) {
        public WalletState {
            final EnumMap<CurrencyType, Long> normalized = emptyWallet();
            if (milli != null) {
                for (final Map.Entry<CurrencyType, Long> entry : milli.entrySet()) {
                    final CurrencyType type = Objects.requireNonNull(entry.getKey(), "currency");
                    final long value = entry.getValue() == null ? 0L : entry.getValue();
                    if (value < 0L) throw new IllegalArgumentException("negative wallet value");
                    normalized.put(type, value);
                }
            }
            milli = Map.copyOf(normalized);
        }

        public long milli(final CurrencyType type) {
            return milli.getOrDefault(Objects.requireNonNull(type, "type"), 0L);
        }

        public double amount(final CurrencyType type) {
            return fromMilli(milli(type));
        }

        public boolean present() {
            return milli.values().stream().anyMatch(value -> value != 0L);
        }

        public WalletState with(final CurrencyType type, final long value) {
            if (value < 0L) throw new IllegalArgumentException("negative wallet value");
            final EnumMap<CurrencyType, Long> next = new EnumMap<>(CurrencyType.class);
            next.putAll(milli);
            next.put(Objects.requireNonNull(type, "type"), value);
            return new WalletState(next);
        }

        public WalletState add(final CurrencyType type, final long delta) {
            if (delta <= 0L) throw new IllegalArgumentException("wallet addition must be positive");
            return with(type, Math.addExact(milli(type), delta));
        }
    }

    public record DurableOperation(String operationId,
                                   UUID playerId,
                                   CurrencyType currency,
                                   long amountMilli,
                                   long createdAtEpochMillis,
                                   OperationStatus status,
                                   boolean previousPresent,
                                   WalletState previous,
                                   WalletState expected) {
        public DurableOperation {
            operationId = requireOperationId(operationId);
            Objects.requireNonNull(playerId, "playerId");
            Objects.requireNonNull(currency, "currency");
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(previous, "previous");
            Objects.requireNonNull(expected, "expected");
            if (amountMilli <= 0L || createdAtEpochMillis <= 0L) {
                throw new IllegalArgumentException("invalid durable wallet operation");
            }
        }

        public double amount() {
            return fromMilli(amountMilli);
        }

        public DurableOperation withStatus(final OperationStatus next) {
            return new DurableOperation(operationId, playerId, currency, amountMilli,
                    createdAtEpochMillis, next, previousPresent, previous, expected);
        }
    }

    public WalletState readCached(final UUID playerId) {
        final EconomySection section = PlayerProfileAuthority.current().requireSection(
                Objects.requireNonNull(playerId, "playerId"),
                ProfileSectionId.ECONOMY, EconomySection.class);
        return decodeWallet(section);
    }

    public CompletionStage<WalletState> load(final UUID playerId) {
        return PlayerProfileAuthority.current().repository().loadSnapshot(playerId)
                .thenApply(profile -> decodeWallet(profile.economy().value()));
    }

    public CompletionStage<WalletState> replace(final UUID playerId,
                                                final WalletState expected,
                                                final WalletState next) {
        Objects.requireNonNull(expected, "expected");
        Objects.requireNonNull(next, "next");
        return PlayerProfileAuthority.current().mutateSectionConditional(
                playerId, ProfileSectionId.ECONOMY, EconomySection.class, current -> {
                    final WalletState live = decodeWallet(current);
                    if (!live.equals(expected)) {
                        throw new IllegalStateException("stale PlayerProfile wallet snapshot");
                    }
                    if (live.equals(next)) {
                        return PlayerProfileService.ConditionalMutation.unchanged(live);
                    }
                    return PlayerProfileService.ConditionalMutation.changed(
                            withWallet(current, next), next);
                });
    }

    public <R> CompletionStage<R> mutate(final UUID playerId,
                                         final Function<WalletState, Decision<R>> mutation) {
        Objects.requireNonNull(mutation, "mutation");
        return PlayerProfileAuthority.current().mutateSectionConditional(
                playerId, ProfileSectionId.ECONOMY, EconomySection.class, current -> {
                    final WalletState before = decodeWallet(current);
                    final Decision<R> decision = Objects.requireNonNull(
                            mutation.apply(before), "wallet decision");
                    if (!decision.changed()) {
                        return PlayerProfileService.ConditionalMutation.unchanged(decision.result());
                    }
                    final WalletState after = Objects.requireNonNull(
                            decision.wallet(), "changed wallet");
                    return PlayerProfileService.ConditionalMutation.changed(
                            withWallet(current, after), decision.result());
                });
    }

    public record Decision<R>(boolean changed, WalletState wallet, R result) {
        public static <R> Decision<R> unchanged(final R result) {
            return new Decision<>(false, null, result);
        }

        public static <R> Decision<R> changed(final WalletState wallet, final R result) {
            return new Decision<>(true, Objects.requireNonNull(wallet, "wallet"), result);
        }
    }

    /** Creates or replays one restart-durable wallet debit. */
    public CompletionStage<DurableOperation> debitOperation(final UUID playerId,
                                                            final CurrencyType currency,
                                                            final double amount,
                                                            final String operationId) {
        final long amountMilli = toPositiveMilli(amount);
        final String id = requireOperationId(operationId);
        return PlayerProfileAuthority.current().mutateSectionConditional(
                playerId, ProfileSectionId.ECONOMY, EconomySection.class, current -> {
                    final Map<String, String> pending = new LinkedHashMap<>(current.pendingRewards());
                    final String key = operationKey(id);
                    final String encoded = pending.get(key);
                    if (encoded != null) {
                        final DurableOperation existing = decodeOperation(playerId, encoded);
                        requireSameOperation(existing, playerId, currency, amountMilli, id);
                        return PlayerProfileService.ConditionalMutation.unchanged(existing);
                    }
                    pruneTerminalOperations(pending, playerId);
                    if (pending.size() >= MAX_OPERATIONS) {
                        throw new IllegalStateException("PlayerProfile wallet operation ledger is full");
                    }
                    final WalletState before = decodeWallet(current);
                    final long balance = before.milli(currency);
                    if (balance < amountMilli) {
                        return PlayerProfileService.ConditionalMutation.unchanged(null);
                    }
                    final WalletState after = before.with(currency, balance - amountMilli);
                    final DurableOperation created = new DurableOperation(id, playerId, currency,
                            amountMilli, System.currentTimeMillis(), OperationStatus.DEBITED,
                            before.present(), before, after);
                    pending.put(key, encodeOperation(created));
                    final EconomySection next = withWalletAndPending(current, after, pending);
                    return PlayerProfileService.ConditionalMutation.changed(next, created);
                });
    }

    public Optional<DurableOperation> operationCached(final UUID playerId,
                                                       final String operationId) {
        if (operationId == null || operationId.isBlank()) return Optional.empty();
        final EconomySection section = PlayerProfileAuthority.current().requireSection(
                playerId, ProfileSectionId.ECONOMY, EconomySection.class);
        return Optional.ofNullable(section.pendingRewards().get(operationKey(operationId)))
                .map(encoded -> decodeOperation(playerId, encoded));
    }

    public List<DurableOperation> operationsByPrefixCached(final UUID playerId,
                                                           final String prefix) {
        if (prefix == null || prefix.isBlank()) {
            throw new IllegalArgumentException("operation prefix cannot be blank");
        }
        final EconomySection section = PlayerProfileAuthority.current().requireSection(
                playerId, ProfileSectionId.ECONOMY, EconomySection.class);
        final List<DurableOperation> result = new ArrayList<>();
        for (final Map.Entry<String, String> entry : section.pendingRewards().entrySet()) {
            if (!entry.getKey().startsWith(OPERATION_PREFIX)) continue;
            final DurableOperation operation = decodeOperation(playerId, entry.getValue());
            if (operation.operationId().startsWith(prefix)) result.add(operation);
        }
        result.sort(Comparator.comparingLong(DurableOperation::createdAtEpochMillis)
                .thenComparing(DurableOperation::operationId));
        return List.copyOf(result);
    }

    public CompletionStage<DurableOperation> transitionOperation(
            final UUID playerId,
            final String operationId,
            final OperationStatus target) {
        final String id = requireOperationId(operationId);
        Objects.requireNonNull(target, "target");
        if (target == OperationStatus.DEBITED) {
            return CompletableFuture.failedFuture(
                    new IllegalArgumentException("cannot transition back to DEBITED"));
        }
        return PlayerProfileAuthority.current().mutateSectionConditional(
                playerId, ProfileSectionId.ECONOMY, EconomySection.class, current -> {
                    final Map<String, String> pending = new LinkedHashMap<>(current.pendingRewards());
                    final String key = operationKey(id);
                    final String encoded = pending.get(key);
                    if (encoded == null) throw new IllegalStateException("unknown wallet operation");
                    final DurableOperation existing = decodeOperation(playerId, encoded);
                    if (existing.status() == target) {
                        return PlayerProfileService.ConditionalMutation.unchanged(existing);
                    }
                    if (existing.status() != OperationStatus.DEBITED) {
                        throw new IllegalStateException("wallet operation is already terminal");
                    }
                    final WalletState live = decodeWallet(current);
                    if (!live.equals(existing.expected())) {
                        throw new IllegalStateException("wallet operation witness no longer matches wallet");
                    }
                    final WalletState after = target == OperationStatus.ROLLED_BACK
                            ? existing.previous() : live;
                    final DurableOperation updated = existing.withStatus(target);
                    pending.put(key, encodeOperation(updated));
                    final EconomySection next = withWalletAndPending(current, after, pending);
                    return PlayerProfileService.ConditionalMutation.changed(next, updated);
                });
    }

    public static long toMilli(final double amount) {
        if (!Double.isFinite(amount) || amount < 0.0D) {
            throw new IllegalArgumentException("wallet amount must be finite and non-negative");
        }
        return BigDecimal.valueOf(amount).movePointRight(3)
                .setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    public static long toPositiveMilli(final double amount) {
        final long result = toMilli(amount);
        if (result <= 0L) throw new IllegalArgumentException("wallet amount must be positive");
        return result;
    }

    public static double fromMilli(final long amount) {
        if (amount < 0L) throw new IllegalArgumentException("negative wallet amount");
        return BigDecimal.valueOf(amount, 3).doubleValue();
    }

    private static WalletState decodeWallet(final EconomySection section) {
        final EnumMap<CurrencyType, Long> values = emptyWallet();
        for (final Map.Entry<String, Long> entry : section.wallets().entrySet()) {
            final CurrencyType type = CurrencyType.fromInput(entry.getKey());
            if (type == null) throw new IllegalStateException(
                    "unknown PlayerProfile wallet currency: " + entry.getKey());
            final long value = entry.getValue() == null ? 0L : entry.getValue();
            if (value < 0L) throw new IllegalStateException("negative PlayerProfile wallet value");
            values.put(type, value);
        }
        return new WalletState(values);
    }

    private static EconomySection withWallet(final EconomySection current,
                                             final WalletState wallet) {
        return new EconomySection(encodeWallet(wallet), current.bankBalance(), current.debts(),
                current.pendingRewards(), current.operationReceipts(), current.extensions());
    }

    private static EconomySection withWalletAndPending(final EconomySection current,
                                                       final WalletState wallet,
                                                       final Map<String, String> pending) {
        return new EconomySection(encodeWallet(wallet), current.bankBalance(), current.debts(),
                pending, current.operationReceipts(), current.extensions());
    }

    private static Map<String, Long> encodeWallet(final WalletState wallet) {
        final LinkedHashMap<String, Long> encoded = new LinkedHashMap<>();
        for (final CurrencyType type : CurrencyType.values()) {
            encoded.put(type.name().toLowerCase(Locale.ROOT), wallet.milli(type));
        }
        return Map.copyOf(encoded);
    }

    private static EnumMap<CurrencyType, Long> emptyWallet() {
        final EnumMap<CurrencyType, Long> map = new EnumMap<>(CurrencyType.class);
        for (final CurrencyType type : CurrencyType.values()) map.put(type, 0L);
        return map;
    }

    private static String operationKey(final String operationId) {
        try {
            final byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(requireOperationId(operationId).getBytes(StandardCharsets.UTF_8));
            return OPERATION_PREFIX + java.util.HexFormat.of().formatHex(digest);
        } catch (final NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    private static String encodeOperation(final DurableOperation operation) {
        final String encodedId = Base64.getUrlEncoder().withoutPadding().encodeToString(
                operation.operationId().getBytes(StandardCharsets.UTF_8));
        final StringBuilder value = new StringBuilder(384)
                .append(CODEC_VERSION).append('|').append(encodedId).append('|')
                .append(operation.currency().name()).append('|')
                .append(operation.amountMilli()).append('|')
                .append(operation.createdAtEpochMillis()).append('|')
                .append(operation.status().name()).append('|')
                .append(operation.previousPresent());
        appendWallet(value, operation.previous());
        appendWallet(value, operation.expected());
        if (value.length() > 512) throw new IllegalArgumentException(
                "wallet operation payload exceeds PlayerProfile bound");
        return value.toString();
    }

    private static void appendWallet(final StringBuilder value, final WalletState wallet) {
        for (final CurrencyType type : CurrencyType.values()) {
            value.append('|').append(wallet.milli(type));
        }
    }

    private static DurableOperation decodeOperation(final UUID playerId, final String encoded) {
        final String[] fields = Objects.requireNonNull(encoded, "encoded operation").split("\\|", -1);
        final int expectedFields = 7 + CurrencyType.values().length * 2;
        if (fields.length != expectedFields || !CODEC_VERSION.equals(fields[0])) {
            throw new IllegalStateException("invalid PlayerProfile wallet operation payload");
        }
        try {
            final String operationId = new String(Base64.getUrlDecoder().decode(fields[1]),
                    StandardCharsets.UTF_8);
            final CurrencyType currency = CurrencyType.valueOf(fields[2]);
            final long amount = Long.parseLong(fields[3]);
            final long created = Long.parseLong(fields[4]);
            final OperationStatus status = OperationStatus.valueOf(fields[5]);
            final boolean previousPresent;
            if ("true".equals(fields[6])) previousPresent = true;
            else if ("false".equals(fields[6])) previousPresent = false;
            else throw new IllegalArgumentException("invalid previous-present value");
            int index = 7;
            final EnumMap<CurrencyType, Long> previous = emptyWallet();
            for (final CurrencyType type : CurrencyType.values()) {
                previous.put(type, Long.parseLong(fields[index++]));
            }
            final EnumMap<CurrencyType, Long> expected = emptyWallet();
            for (final CurrencyType type : CurrencyType.values()) {
                expected.put(type, Long.parseLong(fields[index++]));
            }
            return new DurableOperation(operationId, playerId, currency, amount, created,
                    status, previousPresent, new WalletState(previous), new WalletState(expected));
        } catch (final RuntimeException malformed) {
            throw new IllegalStateException("invalid PlayerProfile wallet operation payload", malformed);
        }
    }

    private static void requireSameOperation(final DurableOperation existing,
                                             final UUID playerId,
                                             final CurrencyType currency,
                                             final long amountMilli,
                                             final String operationId) {
        if (!existing.playerId().equals(playerId)
                || existing.currency() != currency
                || existing.amountMilli() != amountMilli
                || !existing.operationId().equals(operationId)) {
            throw new IllegalStateException("wallet operation id reused with different parameters");
        }
    }

    private static void pruneTerminalOperations(final Map<String, String> pending,
                                                final UUID playerId) {
        if (pending.size() < MAX_OPERATIONS) return;
        final Optional<Map.Entry<String, DurableOperation>> oldest = pending.entrySet().stream()
                .filter(entry -> entry.getKey().startsWith(OPERATION_PREFIX))
                .map(entry -> Map.entry(entry.getKey(), decodeOperation(playerId, entry.getValue())))
                .filter(entry -> entry.getValue().status() != OperationStatus.DEBITED)
                .min(Comparator.comparingLong(entry -> entry.getValue().createdAtEpochMillis()));
        oldest.ifPresent(entry -> pending.remove(entry.getKey()));
    }

    private static String requireOperationId(final String operationId) {
        if (operationId == null || operationId.isBlank() || operationId.trim().length() > 192) {
            throw new IllegalArgumentException("operation id must be non-blank and bounded");
        }
        return operationId.trim();
    }
}
