package hu.taliann.icesmp.playerprofile.application;

import hu.taliann.icesmp.data.CurrencyType;
import hu.taliann.icesmp.data.FactionType;
import hu.taliann.icesmp.playerprofile.domain.ProfileSectionId;
import hu.taliann.icesmp.playerprofile.domain.section.EconomySection;
import hu.taliann.icesmp.playerprofile.domain.section.FactionSection;
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
