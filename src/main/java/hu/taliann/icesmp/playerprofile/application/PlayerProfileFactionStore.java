package hu.taliann.icesmp.playerprofile.application;

import hu.taliann.icesmp.data.CurrencyType;
import hu.taliann.icesmp.data.FactionType;
import hu.taliann.icesmp.playerprofile.domain.ProfileSectionId;
import hu.taliann.icesmp.playerprofile.domain.PlayerProfileOperation;
import hu.taliann.icesmp.playerprofile.domain.PlayerProfileSnapshot;
import hu.taliann.icesmp.playerprofile.domain.section.EconomySection;
import hu.taliann.icesmp.playerprofile.domain.section.FactionSection;
import hu.taliann.icesmp.playerprofile.domain.section.OperationSection;
import hu.taliann.icesmp.playerprofile.transaction.PlayerProfileTransactionManager;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;

/** Typed faction membership, history and switch-counter authority. */
public final class PlayerProfileFactionStore {

    private static final String LAST_PAID_SWITCH = "switch.last-paid-at";
    private static final String SWITCH_SEASON = "switch.season-id";
    private static final String SWITCH_COUNT = "switch.season-count";

    public record State(Optional<FactionType> membership,
                        Optional<FactionType> lastChosen,
                        boolean everChosen,
                        long joinedAt,
                        long leftAt,
                        List<FactionType> history,
                        long lastPaidSwitchAt,
                        long switchSeason,
                        int switchesThisSeason) {
        public State {
            membership = membership == null ? Optional.empty() : membership;
            lastChosen = lastChosen == null ? Optional.empty() : lastChosen;
            history = history == null ? List.of() : List.copyOf(history);
            if (joinedAt < 0L || leftAt < 0L || lastPaidSwitchAt < 0L
                    || switchSeason < 0L || switchesThisSeason < 0) {
                throw new IllegalArgumentException("negative faction state value");
            }
        }
    }

    public State readCached(final UUID playerId) {
        return decode(PlayerProfileAuthority.current().requireSection(
                Objects.requireNonNull(playerId, "playerId"),
                ProfileSectionId.FACTION, FactionSection.class));
    }

    public CompletionStage<State> load(final UUID playerId) {
        return PlayerProfileAuthority.current().repository().loadSnapshot(playerId)
                .thenApply(profile -> decode(profile.faction().value()));
    }

    public record MembershipView(long sectionRevision, State state) {
        public MembershipView {
            if (sectionRevision < 0) throw new IllegalArgumentException("negative faction revision");
            Objects.requireNonNull(state, "state");
        }
    }

    /** An explicit adjustment is not a paid switch and never rewinds history or other faction axes. */
    public record MembershipAdjustment(UUID operationId, long expectedRevision,
                                       Optional<FactionType> expectedMembership,
                                       Optional<FactionType> target, long occurredAt) {
        public MembershipAdjustment {
            Objects.requireNonNull(operationId, "operationId");
            Objects.requireNonNull(expectedMembership, "expectedMembership");
            Objects.requireNonNull(target, "target");
            if (expectedRevision < 0 || expectedRevision == Long.MAX_VALUE || occurredAt < 1
                    || expectedMembership.equals(target)) {
                throw new IllegalArgumentException("invalid faction adjustment");
            }
        }

        public String receiptId() { return "faction-adjustment:" + operationId; }

        public String fingerprint() {
            final String identity = "faction-adjustment-v1\n" + operationId + "\n"
                    + expectedRevision + "\n" + expectedMembership.map(Enum::name).orElse("GUEST")
                    + "\n" + target.map(Enum::name).orElse("GUEST") + "\n" + occurredAt;
            try {
                return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                        .digest(identity.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            } catch (final java.security.NoSuchAlgorithmException impossible) {
                throw new IllegalStateException(impossible);
            }
        }
    }

    public enum AdjustmentObservation { BEFORE, APPLIED, CONFLICT }

    public record AdjustmentResult(MembershipView after, boolean replayed) {
        public AdjustmentResult { Objects.requireNonNull(after, "after"); }
    }

    public static final class AdjustmentRejected extends IllegalStateException {
        private final String code;
        private AdjustmentRejected(final String code) { super(code); this.code = code; }
        public String code() { return code; }
    }

    public MembershipView membershipView(final UUID playerId) {
        return membershipView(PlayerProfileAuthority.current().requireCached(playerId));
    }

    public AdjustmentObservation observeAdjustment(final UUID playerId,
                                                    final MembershipAdjustment adjustment) {
        return observeAdjustment(PlayerProfileAuthority.current().requireCached(playerId), adjustment);
    }

    public List<MembershipAdjustment> pendingAdjustmentEffects(final UUID playerId) {
        final PlayerProfileSnapshot snapshot = PlayerProfileAuthority.current().requireCached(playerId);
        membershipView(snapshot);
        return snapshot.operations().value().operations().values().stream()
                .filter(receipt -> receipt.type().equals("faction-membership-adjustment") && receipt.requiresReconciliation())
                .sorted(java.util.Comparator.comparing(PlayerProfileOperation::createdAt))
                .map(PlayerProfileFactionStore::adjustmentFromReceipt).toList();
    }

    public boolean adjustmentEffectsCompleted(final UUID playerId, final MembershipAdjustment adjustment) {
        final PlayerProfileSnapshot snapshot = PlayerProfileAuthority.current().requireCached(playerId);
        return observeAdjustment(snapshot, adjustment) == AdjustmentObservation.APPLIED
                && "completed".equals(snapshot.operations().value().operations().get(adjustment.receiptId()).metadata().get("effects-state"));
    }

    /** Called only after the canonical domain consumers have durably acknowledged their cleanup. */
    public CompletionStage<Boolean> completeAdjustmentEffects(final UUID playerId,
                                                              final MembershipAdjustment adjustment) {
        final PlayerProfileAuthority authority = PlayerProfileAuthority.current();
        return authority.mutateSectionConditional(playerId, ProfileSectionId.OPERATIONS,
                OperationSection.class, operations -> {
                    final PlayerProfileSnapshot current = authority.requireCached(playerId);
                    if (!current.operations().value().equals(operations)
                            || observeAdjustment(current, adjustment) != AdjustmentObservation.APPLIED) {
                        throw new AdjustmentRejected("CONFLICT");
                    }
                    final PlayerProfileOperation receipt = operations.operations().get(adjustment.receiptId());
                    adjustmentFromReceipt(receipt);
                    if ("completed".equals(receipt.metadata().get("effects-state"))) {
                        return PlayerProfileService.ConditionalMutation.unchanged(false);
                    }
                    final Map<String, String> metadata = new LinkedHashMap<>(receipt.metadata());
                    metadata.put("effects-state", "completed");
                    final Map<String, PlayerProfileOperation> next = new LinkedHashMap<>(operations.operations());
                    next.put(receipt.operationId(), new PlayerProfileOperation(receipt.operationId(), receipt.type(), receipt.status(),
                            receipt.fingerprint(), receipt.createdAt(), java.time.Instant.now(), metadata));
                    return PlayerProfileService.ConditionalMutation.changed(new OperationSection(next, operations.extensions()), true);
                });
    }

    /** Membership, append-only logical history and operation identity share the canonical profile WAL. */
    public CompletionStage<AdjustmentResult> adjustMembership(final UUID playerId,
                                                             final MembershipAdjustment adjustment) {
        return adjustMembership(playerId, adjustment, () -> { });
    }

    public CompletionStage<AdjustmentResult> adjustMembership(final UUID playerId,
                                                             final MembershipAdjustment adjustment,
                                                             final Runnable commitAdmission) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(adjustment, "adjustment");
        Objects.requireNonNull(commitAdmission, "commitAdmission");
        return PlayerProfileAuthority.current().transact(playerId, snapshot -> {
            final AdjustmentObservation observed = observeAdjustment(snapshot, adjustment);
            if (observed == AdjustmentObservation.CONFLICT) throw new AdjustmentRejected("CONFLICT");
            final FactionSection current = snapshot.faction().value();
            if (observed == AdjustmentObservation.APPLIED) {
                // The transaction manager verifies the existing receipt before considering any update.
                return adjustmentPlan(adjustment, snapshot.faction().revision(), current,
                        new AdjustmentResult(membershipView(snapshot), true), commitAdmission);
            }
            if (snapshot.operations().value().operations().values().stream().anyMatch(receipt ->
                    receipt.type().equals("faction-membership-adjustment") && receipt.requiresReconciliation())) {
                throw new AdjustmentRejected("FACTION_EFFECTS_PENDING");
            }
            if (adjustment.target().orElse(null) == FactionType.DARK
                    && (!Boolean.TRUE.equals(current.extensions().get("sin.exiled"))
                    || !Boolean.TRUE.equals(current.extensions().get("sin.dark-pact")))) {
                throw new AdjustmentRejected("DARK_OATH_REQUIRED");
            }
            if (adjustment.occurredAt() < Math.max(current.joinedAt(), current.leftAt())) {
                throw new AdjustmentRejected("CONFLICT");
            }
            final FactionSection next = adjustment.target().isPresent()
                    ? assign(current, adjustment.target().orElseThrow(), adjustment.occurredAt(), current.cooldowns())
                    : new FactionSection("", current.lastChosenFaction(), current.everChosen(),
                            current.joinedAt(), adjustment.occurredAt(), current.history(),
                            current.reputation(), current.cooldowns(), current.extensions());
            final MembershipView after = new MembershipView(Math.addExact(adjustment.expectedRevision(), 1), decode(next));
            return adjustmentPlan(adjustment, adjustment.expectedRevision(), next,
                    new AdjustmentResult(after, false), commitAdmission);
        });
    }

    private static PlayerProfileTransactionManager.TransactionPlan<AdjustmentResult> adjustmentPlan(
            final MembershipAdjustment adjustment, final long revision, final FactionSection next,
            final AdjustmentResult result, final Runnable commitAdmission) {
        return new PlayerProfileTransactionManager.TransactionPlan<>(adjustment.receiptId(),
                "faction-membership-adjustment", adjustment.fingerprint(),
                List.of(new PlayerProfileTransactionManager.SectionUpdate(ProfileSectionId.FACTION, revision, next)), result, commitAdmission,
                Map.of("effects-schema", "1", "effects-state", "pending", "adjustment-id", adjustment.operationId().toString(),
                        "expected-revision", Long.toString(adjustment.expectedRevision()),
                        "expected-membership", adjustment.expectedMembership().map(Enum::name).orElse("GUEST"),
                        "target-membership", adjustment.target().map(Enum::name).orElse("GUEST"),
                        "occurred-at", Long.toString(adjustment.occurredAt())));
    }

    private static MembershipAdjustment adjustmentFromReceipt(final PlayerProfileOperation receipt) {
        try {
            final Map<String, String> metadata = receipt.metadata();
            if (receipt.status() != PlayerProfileOperation.Status.COMMITTED
                    || !receipt.type().equals("faction-membership-adjustment")
                    || !"1".equals(metadata.get("effects-schema"))
                    || !java.util.Set.of("pending", "completed").contains(metadata.get("effects-state"))) {
                throw new AdjustmentRejected("FACTION_EFFECTS_UNAVAILABLE");
            }
            final var adjustment = new MembershipAdjustment(UUID.fromString(metadata.get("adjustment-id")),
                    Long.parseLong(metadata.get("expected-revision")), membershipId(metadata.get("expected-membership")),
                    membershipId(metadata.get("target-membership")), Long.parseLong(metadata.get("occurred-at")));
            if (!adjustment.receiptId().equals(receipt.operationId()) || !adjustment.fingerprint().equals(receipt.fingerprint())) {
                throw new AdjustmentRejected("FACTION_EFFECTS_UNAVAILABLE");
            }
            return adjustment;
        } catch (final IllegalArgumentException | NullPointerException malformed) {
            throw new AdjustmentRejected("FACTION_EFFECTS_UNAVAILABLE");
        }
    }

    private static Optional<FactionType> membershipId(final String id) {
        return "GUEST".equals(id) ? Optional.empty() : Optional.of(FactionType.valueOf(id));
    }

    private static MembershipView membershipView(final PlayerProfileSnapshot snapshot) {
        if (!snapshot.faction().health().usable() || !snapshot.operations().health().usable()) {
            throw new AdjustmentRejected("PROFILE_UNAVAILABLE");
        }
        return new MembershipView(snapshot.faction().revision(), decode(snapshot.faction().value()));
    }

    private static AdjustmentObservation observeAdjustment(final PlayerProfileSnapshot snapshot,
                                                           final MembershipAdjustment adjustment) {
        Objects.requireNonNull(adjustment, "adjustment");
        final MembershipView view = membershipView(snapshot);
        final PlayerProfileOperation receipt = snapshot.operations().value().operations().get(adjustment.receiptId());
        if (receipt != null) {
            return receipt.status() == PlayerProfileOperation.Status.COMMITTED
                    && receipt.type().equals("faction-membership-adjustment")
                    && receipt.fingerprint().equals(adjustment.fingerprint())
                    && view.sectionRevision() == adjustment.expectedRevision() + 1
                    && view.state().membership().equals(adjustment.target())
                    ? AdjustmentObservation.APPLIED : AdjustmentObservation.CONFLICT;
        }
        return view.sectionRevision() == adjustment.expectedRevision()
                && view.state().membership().equals(adjustment.expectedMembership())
                ? AdjustmentObservation.BEFORE : AdjustmentObservation.CONFLICT;
    }

    public CompletionStage<State> assign(final UUID playerId, final FactionType target) {
        Objects.requireNonNull(target, "target");
        return PlayerProfileAuthority.current().mutateSectionConditional(
                playerId, ProfileSectionId.FACTION, FactionSection.class, current -> {
                    final State before = decode(current);
                    if (before.membership().orElse(null) == target) {
                        return PlayerProfileService.ConditionalMutation.unchanged(before);
                    }
                    final long now = System.currentTimeMillis();
                    final FactionSection next = assign(current, target, now, current.cooldowns());
                    return PlayerProfileService.ConditionalMutation.changed(next, decode(next));
                });
    }

    /** Exile, oath, expected membership and season counter are checked in the same final commit. */
    public CompletionStage<Boolean> joinDark(final UUID playerId, final FactionType expected,
                                            final long season, final int maxSwitches) {
        return PlayerProfileAuthority.current().mutateSectionConditional(playerId, ProfileSectionId.FACTION,
                FactionSection.class, current -> {
                    final State before = decode(current);
                    if (before.membership().orElse(null) != expected || expected == FactionType.DARK
                            || !Boolean.TRUE.equals(current.extensions().get("sin.exiled"))
                            || !Boolean.TRUE.equals(current.extensions().get("sin.dark-pact"))) {
                        return PlayerProfileService.ConditionalMutation.unchanged(false);
                    }
                    final LinkedHashMap<String, Long> cooldowns = new LinkedHashMap<>(current.cooldowns());
                    if (before.everChosen()) {
                        final long count = cooldowns.getOrDefault(SWITCH_SEASON, 0L) == season
                                ? cooldowns.getOrDefault(SWITCH_COUNT, 0L) : 0L;
                        if (maxSwitches > 0 && count >= maxSwitches) return PlayerProfileService.ConditionalMutation.unchanged(false);
                        cooldowns.put(SWITCH_SEASON, season);
                        cooldowns.put(SWITCH_COUNT, Math.addExact(count, 1L));
                    }
                    return PlayerProfileService.ConditionalMutation.changed(
                            assign(current, FactionType.DARK, System.currentTimeMillis(), cooldowns), true);
                });
    }

    public CompletionStage<State> remove(final UUID playerId) {
        return PlayerProfileAuthority.current().mutateSectionConditional(
                playerId, ProfileSectionId.FACTION, FactionSection.class, current -> {
                    final State before = decode(current);
                    if (before.membership().isEmpty()) {
                        return PlayerProfileService.ConditionalMutation.unchanged(before);
                    }
                    final FactionSection next = new FactionSection("",
                            current.lastChosenFaction(), current.everChosen(),
                            current.joinedAt(), System.currentTimeMillis(), current.history(),
                            current.reputation(), current.cooldowns(), current.extensions());
                    return PlayerProfileService.ConditionalMutation.changed(next, decode(next));
                });
    }

    /**
     * Atomically commits paid membership switch, wallet deduction, cooldown timestamp and
     * per-season switch counter in one PlayerProfile WAL transaction.
     */
    public CompletionStage<Boolean> switchDurably(final UUID playerId,
                                                   final FactionType expectedCurrent,
                                                   final FactionType target,
                                                   final CurrencyType currency,
                                                   final double cost,
                                                   final long seasonId) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(currency, "currency");
        if (!Double.isFinite(cost) || cost < 0.0D || seasonId < 0L) {
            throw new IllegalArgumentException("invalid faction switch parameters");
        }
        final long costMilli = PlayerProfileEconomyStore.toMilli(cost);
        return PlayerProfileAuthority.current().transact(playerId, snapshot -> {
            final FactionSection currentFaction = snapshot.faction().value();
            final State currentState = decode(currentFaction);
            if (currentState.membership().orElse(null) != expectedCurrent) {
                throw new SwitchRejected();
            }
            final EconomySection currentEconomy = snapshot.economy().value();
            final Map<String, Long> wallets = new LinkedHashMap<>(currentEconomy.wallets());
            final String walletKey = currency.name().toLowerCase(Locale.ROOT);
            final long currentBalance = wallets.getOrDefault(walletKey, 0L);
            if (currentBalance < costMilli) throw new InsufficientBalance();
            if (costMilli > 0L) wallets.put(walletKey, currentBalance - costMilli);

            final long now = System.currentTimeMillis();
            final LinkedHashMap<String, Long> cooldowns = new LinkedHashMap<>(
                    currentFaction.cooldowns());
            cooldowns.put(LAST_PAID_SWITCH, now);
            final long storedSeason = cooldowns.getOrDefault(SWITCH_SEASON, 0L);
            final long count = storedSeason == seasonId
                    ? cooldowns.getOrDefault(SWITCH_COUNT, 0L) : 0L;
            cooldowns.put(SWITCH_SEASON, seasonId);
            cooldowns.put(SWITCH_COUNT, Math.addExact(count, 1L));

            final FactionSection nextFaction = assign(currentFaction, target, now, cooldowns);
            final List<PlayerProfileTransactionManager.SectionUpdate> updates = new ArrayList<>();
            updates.add(new PlayerProfileTransactionManager.SectionUpdate(
                    ProfileSectionId.FACTION, snapshot.faction().revision(), nextFaction));
            if (costMilli > 0L) {
                final EconomySection nextEconomy = new EconomySection(wallets,
                        currentEconomy.bankBalance(), currentEconomy.debts(),
                        currentEconomy.pendingRewards(), currentEconomy.operationReceipts(),
                        currentEconomy.extensions());
                updates.add(new PlayerProfileTransactionManager.SectionUpdate(
                        ProfileSectionId.ECONOMY, snapshot.economy().revision(), nextEconomy));
            }
            final String expected = expectedCurrent == null ? "guest" : expectedCurrent.name();
            final String operationId = "faction-switch:" + playerId + ':'
                    + snapshot.faction().revision() + ':' + target.name().toLowerCase(Locale.ROOT);
            final String fingerprint = playerId + "|" + expected + '|' + target.name()
                    + '|' + currency.name() + '|' + costMilli + '|' + seasonId;
            return new PlayerProfileTransactionManager.TransactionPlan<>(operationId,
                    "faction-switch", fingerprint, updates, Boolean.TRUE);
        }).handle((result, failure) -> {
            if (failure == null) return result;
            final Throwable root = unwrap(failure);
            if (root instanceof SwitchRejected || root instanceof InsufficientBalance) return false;
            throw new CompletionException(root);
        });
    }

    public CompletionStage<Integer> recordSeasonSwitch(final UUID playerId,
                                                       final long seasonId,
                                                       final boolean paid) {
        if (seasonId < 0L) throw new IllegalArgumentException("negative season id");
        return PlayerProfileAuthority.current().mutateSectionConditional(
                playerId, ProfileSectionId.FACTION, FactionSection.class, current -> {
                    final LinkedHashMap<String, Long> cooldowns = new LinkedHashMap<>(current.cooldowns());
                    final long storedSeason = cooldowns.getOrDefault(SWITCH_SEASON, 0L);
                    final long currentCount = storedSeason == seasonId
                            ? cooldowns.getOrDefault(SWITCH_COUNT, 0L) : 0L;
                    final long nextCount = Math.addExact(currentCount, 1L);
                    cooldowns.put(SWITCH_SEASON, seasonId);
                    cooldowns.put(SWITCH_COUNT, nextCount);
                    if (paid) cooldowns.put(LAST_PAID_SWITCH, System.currentTimeMillis());
                    final FactionSection next = copyWithCooldowns(current, cooldowns);
                    return PlayerProfileService.ConditionalMutation.changed(next,
                            Math.toIntExact(nextCount));
                });
    }

    public long lastPaidSwitchAt(final UUID playerId) {
        return readCached(playerId).lastPaidSwitchAt();
    }

    public int switchesThisSeason(final UUID playerId, final long seasonId) {
        final State state = readCached(playerId);
        return state.switchSeason() == seasonId ? state.switchesThisSeason() : 0;
    }

    private static FactionSection assign(final FactionSection current,
                                         final FactionType target,
                                         final long now,
                                         final Map<String, Long> cooldowns) {
        final List<String> history = new ArrayList<>(current.history());
        if (history.isEmpty() || !history.get(history.size() - 1).equalsIgnoreCase(target.name())) {
            history.add(target.name());
            if (history.size() > 128) history.remove(0);
        }
        return new FactionSection(target.name(), target.name(), true, now,
                current.membershipId().isBlank() ? current.leftAt() : now,
                history, current.reputation(), cooldowns, current.extensions());
    }

    private static FactionSection copyWithCooldowns(final FactionSection current,
                                                    final Map<String, Long> cooldowns) {
        return new FactionSection(current.membershipId(), current.lastChosenFaction(),
                current.everChosen(), current.joinedAt(), current.leftAt(), current.history(),
                current.reputation(), cooldowns, current.extensions());
    }

    private static State decode(final FactionSection section) {
        final Optional<FactionType> membership = parseOptional(section.membershipId());
        final Optional<FactionType> last = parseOptional(section.lastChosenFaction());
        final List<FactionType> history = section.history().stream()
                .map(FactionType::fromInput)
                .map(value -> Objects.requireNonNull(value,
                        "unknown faction in PlayerProfile history"))
                .toList();
        final long count = section.cooldowns().getOrDefault(SWITCH_COUNT, 0L);
        return new State(membership, last, section.everChosen(), section.joinedAt(),
                section.leftAt(), history,
                section.cooldowns().getOrDefault(LAST_PAID_SWITCH, 0L),
                section.cooldowns().getOrDefault(SWITCH_SEASON, 0L),
                Math.toIntExact(count));
    }

    private static Optional<FactionType> parseOptional(final String raw) {
        if (raw == null || raw.isBlank()) return Optional.empty();
        final FactionType parsed = FactionType.fromInput(raw);
        if (parsed == null) throw new IllegalStateException(
                "unknown faction in PlayerProfile: " + raw);
        return Optional.of(parsed);
    }

    private static Throwable unwrap(final Throwable failure) {
        Throwable current = failure;
        while ((current instanceof CompletionException
                || current instanceof java.util.concurrent.ExecutionException)
                && current.getCause() != null) current = current.getCause();
        return current;
    }

    private static final class SwitchRejected extends RuntimeException {
        private static final long serialVersionUID = 1L;
        private SwitchRejected() { super("faction membership changed", null, false, false); }
    }

    private static final class InsufficientBalance extends RuntimeException {
        private static final long serialVersionUID = 1L;
        private InsufficientBalance() { super("insufficient wallet balance", null, false, false); }
    }
}
