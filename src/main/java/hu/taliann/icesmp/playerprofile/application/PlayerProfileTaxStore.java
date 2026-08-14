package hu.taliann.icesmp.playerprofile.application;

import hu.taliann.icesmp.data.CurrencyType;
import hu.taliann.icesmp.data.FactionType;
import hu.taliann.icesmp.playerprofile.domain.ProfileSectionId;
import hu.taliann.icesmp.playerprofile.domain.section.EconomySection;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/** PlayerProfile tax debt, evasion strike and restart-safe outbox authority. */
public final class PlayerProfileTaxStore {

    private static final String OUTBOX_PREFIX = "tax.outbox.";
    private static final String CODEC = "v1";
    private static final int MAX_OUTBOX = 64;
    private final Clock clock;

    public PlayerProfileTaxStore() {
        this(Clock.systemUTC());
    }

    PlayerProfileTaxStore(final Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public record Debt(FactionType origin, long amountMilli, int evasionStrikes) {
        public Debt {
            Objects.requireNonNull(origin, "origin");
            if (amountMilli < 0L || evasionStrikes < 0) {
                throw new IllegalArgumentException("negative tax debt state");
            }
        }
        public double amount() { return PlayerProfileEconomyStore.fromMilli(amountMilli); }
    }

    public record Outbox(String operationId, FactionType origin,
                         long paidMilli, boolean reportSin) {
        public Outbox {
            if (operationId == null || operationId.isBlank()) {
                throw new IllegalArgumentException("tax operation id required");
            }
            Objects.requireNonNull(origin, "origin");
            if (paidMilli < 0L) throw new IllegalArgumentException("negative tax payment");
        }
        public double paid() { return PlayerProfileEconomyStore.fromMilli(paidMilli); }
    }

    public record Collection(boolean changed, long paidMilli,
                             long owedBeforeMilli, long owedAfterMilli,
                             Outbox outbox) {
        public double paid() { return PlayerProfileEconomyStore.fromMilli(paidMilli); }
        public double owedBefore() { return PlayerProfileEconomyStore.fromMilli(owedBeforeMilli); }
        public double owedAfter() { return PlayerProfileEconomyStore.fromMilli(owedAfterMilli); }
    }

    /** Pure policy result kept next to the canonical tax authority. */
    public record EvasionDecision(int strikesAfter, boolean reportSin) {
        public EvasionDecision {
            if (strikesAfter < 0) {
                throw new IllegalArgumentException("Tax-evasion strikes cannot be negative");
            }
        }
    }

    public List<Debt> debts(final UUID playerId) {
        final EconomySection section = PlayerProfileAuthority.current().requireSection(
                Objects.requireNonNull(playerId, "playerId"),
                ProfileSectionId.ECONOMY, EconomySection.class);
        final List<Debt> result = new ArrayList<>();
        for (final FactionType origin : FactionType.values()) {
            final long amount = section.debts().getOrDefault(amountKey(origin), 0L);
            final int strikes = Math.toIntExact(
                    section.debts().getOrDefault(strikeKey(origin), 0L));
            if (amount > 0L || strikes > 0) result.add(new Debt(origin, amount, strikes));
        }
        return List.copyOf(result);
    }

    public long totalArrearsMilli(final UUID playerId) {
        long total = 0L;
        for (final Debt debt : debts(playerId)) total = Math.addExact(total, debt.amountMilli());
        return total;
    }

    public long arrearsMilli(final UUID playerId, final FactionType origin) {
        return debts(playerId).stream().filter(debt -> debt.origin() == origin)
                .mapToLong(Debt::amountMilli).findFirst().orElse(0L);
    }

    public Set<FactionType> origins(final UUID playerId) {
        final EnumSet<FactionType> result = EnumSet.noneOf(FactionType.class);
        debts(playerId).forEach(debt -> result.add(debt.origin()));
        return Set.copyOf(result);
    }

    /**
     * Atomically assesses, deducts the origin currency, updates arrears/strikes and creates the
     * shared-treasury/sin outbox. Reusing an operation id replays the original outbox.
     */
    public CompletionStage<Collection> collect(final UUID playerId,
                                               final FactionType origin,
                                               final double assessment,
                                               final double maxArrears,
                                               final int evasionThreshold,
                                               final String operationId) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(origin, "origin");
        if (!Double.isFinite(assessment) || assessment < 0.0D
                || !Double.isFinite(maxArrears) || maxArrears < 0.0D
                || evasionThreshold < 0) {
            throw new IllegalArgumentException("invalid tax collection parameters");
        }
        final long assessedMilli = PlayerProfileEconomyStore.toMilli(assessment);
        final long maxMilli = PlayerProfileEconomyStore.toMilli(maxArrears);
        final String operation = operation(operationId);
        final String outboxKey = outboxKey(operation);
        return PlayerProfileAuthority.current().mutateSectionConditional(
                playerId, ProfileSectionId.ECONOMY, EconomySection.class, current -> {
                    final String existingPayload = current.pendingRewards().get(outboxKey);
                    if (existingPayload != null) {
                        final Outbox existing = decode(existingPayload);
                        if (!existing.operationId().equals(operation) || existing.origin() != origin) {
                            throw new IllegalStateException("tax operation id reused");
                        }
                        final long owed = current.debts().getOrDefault(amountKey(origin), 0L);
                        return PlayerProfileService.ConditionalMutation.unchanged(
                                new Collection(false, existing.paidMilli(), owed, owed, existing));
                    }
                    if (EconomyReceiptLedger.taxSettled(
                            current.operationReceipts(), operation)) {
                        final long owed = current.debts().getOrDefault(amountKey(origin), 0L);
                        return PlayerProfileService.ConditionalMutation.unchanged(
                                new Collection(false, 0L, owed, owed, null));
                    }
                    final LinkedHashMap<String, Long> debts = new LinkedHashMap<>(current.debts());
                    final long owedBefore = debts.getOrDefault(amountKey(origin), 0L);
                    long assessedTotal = Math.addExact(owedBefore, assessedMilli);
                    if (maxMilli > 0L) assessedTotal = Math.min(maxMilli, assessedTotal);
                    final String walletKey = CurrencyType.fromFactionType(origin).name()
                            .toLowerCase(Locale.ROOT);
                    final LinkedHashMap<String, Long> wallets = new LinkedHashMap<>(current.wallets());
                    final long balance = wallets.getOrDefault(walletKey, 0L);
                    final long paid = Math.min(balance, assessedTotal);
                    final long owedAfter = assessedTotal - paid;
                    if (paid > 0L) wallets.put(walletKey, balance - paid);
                    final int strikesBefore = Math.toIntExact(
                            debts.getOrDefault(strikeKey(origin), 0L));
                    final EvasionDecision decision = afterCollection(strikesBefore,
                            PlayerProfileEconomyStore.fromMilli(paid),
                            PlayerProfileEconomyStore.fromMilli(owedAfter),
                            maxArrears, evasionThreshold, true);
                    if (owedAfter == 0L) debts.remove(amountKey(origin));
                    else debts.put(amountKey(origin), owedAfter);
                    // A durable sin outbox entry IS the report, so the strike cycle restarts with it.
                    final int storedStrikes = decision.reportSin() ? 0 : decision.strikesAfter();
                    if (storedStrikes == 0) debts.remove(strikeKey(origin));
                    else debts.put(strikeKey(origin), (long) storedStrikes);

                    final boolean changed = assessedMilli > 0L || paid > 0L
                            || owedBefore != owedAfter || strikesBefore != storedStrikes;
                    if (!changed) {
                        return PlayerProfileService.ConditionalMutation.unchanged(
                                new Collection(false, 0L, owedBefore, owedAfter, null));
                    }
                    final LinkedHashMap<String, String> pending =
                            new LinkedHashMap<>(current.pendingRewards());
                    final long taxOutboxes = pending.keySet().stream()
                            .filter(key -> key.startsWith(OUTBOX_PREFIX)).count();
                    if (taxOutboxes >= MAX_OUTBOX) {
                        throw new IllegalStateException("tax outbox is full");
                    }
                    final Outbox outbox = new Outbox(operation, origin, paid,
                            decision.reportSin());
                    pending.put(outboxKey, encode(outbox));
                    final EconomySection next = new EconomySection(wallets,
                            current.bankBalance(), debts, pending,
                            current.operationReceipts(), current.extensions());
                    return PlayerProfileService.ConditionalMutation.changed(next,
                            new Collection(true, paid, owedBefore, owedAfter, outbox));
                });
    }

    public List<Outbox> pending(final UUID playerId) {
        final EconomySection section = PlayerProfileAuthority.current().requireSection(
                playerId, ProfileSectionId.ECONOMY, EconomySection.class);
        final List<Outbox> result = new ArrayList<>();
        section.pendingRewards().forEach((key, value) -> {
            if (key.startsWith(OUTBOX_PREFIX)) result.add(decode(value));
        });
        result.sort(Comparator.comparing(Outbox::operationId));
        return List.copyOf(result);
    }

    public CompletionStage<Boolean> settle(final UUID playerId, final String operationId) {
        final String operation = operation(operationId);
        final String key = outboxKey(operation);
        return PlayerProfileAuthority.current().mutateSectionConditional(
                playerId, ProfileSectionId.ECONOMY, EconomySection.class, current -> {
                    if (EconomyReceiptLedger.taxSettled(
                            current.operationReceipts(), operation)) {
                        return PlayerProfileService.ConditionalMutation.unchanged(false);
                    }
                    if (!current.pendingRewards().containsKey(key)) {
                        throw new IllegalStateException("unknown tax outbox");
                    }
                    final LinkedHashMap<String, String> pending =
                            new LinkedHashMap<>(current.pendingRewards());
                    pending.remove(key);
                    final EconomyReceiptLedger.Update ledger = EconomyReceiptLedger.addTax(
                            current, operation, clock.millis());
                    return PlayerProfileService.ConditionalMutation.changed(
                            new EconomySection(current.wallets(), current.bankBalance(),
                                    current.debts(), pending, ledger.receipts(),
                                    ledger.extensions()), true);
                });
    }

    /**
     * Threshold policy for a durable tax-evasion sin outbox. A reached threshold remains pending
     * until an owner-thread consumer acknowledges it; ordinary repayment clears only sub-threshold
     * strikes.
     */
    public static EvasionDecision afterCollection(final int strikesBefore,
                                                  final double paid,
                                                  final double owedAfter,
                                                  final double maxArrears,
                                                  final int threshold,
                                                  final boolean mayReportSin) {
        final int normalizedBefore = Math.max(0, strikesBefore);
        if (threshold <= 0) return new EvasionDecision(0, false);
        if (normalizedBefore >= threshold) {
            return new EvasionDecision(normalizedBefore, mayReportSin);
        }
        if (!Double.isFinite(paid) || paid < 0.0D
                || !Double.isFinite(owedAfter) || owedAfter < 0.0D
                || !Double.isFinite(maxArrears) || maxArrears < 0.0D) {
            return new EvasionDecision(normalizedBefore, false);
        }
        if (maxArrears > 0.0D && paid <= 0.0D && owedAfter >= maxArrears) {
            final int next = normalizedBefore >= threshold - 1
                    ? threshold : normalizedBefore + 1;
            return new EvasionDecision(next, next >= threshold && mayReportSin);
        }
        if (owedAfter <= 0.0D || owedAfter < maxArrears) {
            return new EvasionDecision(0, false);
        }
        return new EvasionDecision(normalizedBefore, false);
    }

    /** Returns NaN when either operand or the resulting persisted balance is invalid. */
    public static double checkedAmountAdd(final double current, final double addition) {
        if (!Double.isFinite(current) || current < 0.0D
                || !Double.isFinite(addition) || addition <= 0.0D) {
            return Double.NaN;
        }
        final double next = current + addition;
        return Double.isFinite(next) && next >= 0.0D ? next : Double.NaN;
    }

    private static String amountKey(final FactionType origin) {
        return "tax." + origin.name().toLowerCase(Locale.ROOT) + ".amount";
    }

    private static String strikeKey(final FactionType origin) {
        return "tax." + origin.name().toLowerCase(Locale.ROOT) + ".strikes";
    }

    private static String encode(final Outbox outbox) {
        final String operation = Base64.getUrlEncoder().withoutPadding().encodeToString(
                outbox.operationId().getBytes(StandardCharsets.UTF_8));
        return CODEC + '|' + operation + '|' + outbox.origin().name() + '|'
                + outbox.paidMilli() + '|' + outbox.reportSin();
    }

    private static Outbox decode(final String encoded) {
        final String[] fields = Objects.requireNonNull(encoded).split("\\|", -1);
        if (fields.length != 5 || !CODEC.equals(fields[0])) {
            throw new IllegalStateException("invalid tax outbox payload");
        }
        try {
            final String operation = new String(Base64.getUrlDecoder().decode(fields[1]),
                    StandardCharsets.UTF_8);
            final FactionType origin = FactionType.valueOf(fields[2]);
            final long paid = Long.parseLong(fields[3]);
            final boolean sin;
            if ("true".equals(fields[4])) sin = true;
            else if ("false".equals(fields[4])) sin = false;
            else throw new IllegalArgumentException("invalid sin flag");
            return new Outbox(operation, origin, paid, sin);
        } catch (final RuntimeException malformed) {
            throw new IllegalStateException("invalid tax outbox payload", malformed);
        }
    }

    private static String outboxKey(final String operationId) {
        try {
            final byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(operationId.getBytes(StandardCharsets.UTF_8));
            return OUTBOX_PREFIX + java.util.HexFormat.of().formatHex(hash);
        } catch (final NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    private static String operation(final String raw) {
        if (raw == null || raw.isBlank() || raw.trim().length() > 128) {
            throw new IllegalArgumentException("invalid tax operation id");
        }
        return raw.trim();
    }
}
