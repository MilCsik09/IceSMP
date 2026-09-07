package hu.taliann.icesmp.playerprofile.application;

import hu.taliann.icesmp.playerprofile.domain.ProfileSectionId;
import hu.taliann.icesmp.playerprofile.domain.section.FactionSection;

import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.Map;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/** Durable whisperer flag and public exposure stage inside the faction section. */
public final class PlayerProfileWhisperStore {
    private final java.util.function.LongSupplier clock;
    public PlayerProfileWhisperStore() { this(System::currentTimeMillis); }
    public PlayerProfileWhisperStore(final java.util.function.LongSupplier clock) { this.clock = Objects.requireNonNull(clock); }


    private static final String WHISPERER = "whisper.whisperer";
    private static final String EVIDENCE = "whisper.evidence";
    private static final String RITE = "whisper.rite";
    private static final String ALIAS = "whisper.alias";
    private static final String RETURN_AFTER = "whisper.return-after";
    public static final long RETURN_COOLDOWN_MILLIS = 86_400_000L;
    private static final int MAX_WITNESSES = 64;
    private static final String EXPOSURE_STAGE = "whisper.exposure-stage";

    public enum Stage {
        CLEAN("tiszta"),
        OBSERVED("megfigyelt"),
        SUSPECTED("gyanúsított"),
        EXPOSED("leleplezett");

        private final String displayName;

        Stage(final String displayName) {
            this.displayName = displayName;
        }

        public String displayName() {
            return displayName;
        }

        public Stage advance() {
            return values()[Math.min(values().length - 1, ordinal() + 1)];
        }

        public Stage cover() {
            return values()[Math.max(0, ordinal() - 1)];
        }
    }

    public record State(boolean whisperer, Stage stage) {
        public State {
            Objects.requireNonNull(stage, "stage");
        }
    }

    public record Adjustment(State state, boolean exposed) {
        public Adjustment {
            Objects.requireNonNull(state, "state");
        }
    }

    public record CoverResult(State state, boolean applied) {
        public CoverResult {
            Objects.requireNonNull(state, "state");
        }
    }

    public State read(final UUID playerId) {
        return decode(PlayerProfileAuthority.current().requireSection(
                Objects.requireNonNull(playerId, "playerId"),
                ProfileSectionId.FACTION, FactionSection.class));
    }

    public CompletionStage<State> makeWhisperer(final UUID playerId) {
        return PlayerProfileAuthority.current().mutateSectionConditional(
                playerId, ProfileSectionId.FACTION, FactionSection.class, current -> {
                    if (!eligible(current, clock.getAsLong()) || current.extensions().containsKey(RITE)) {
                        throw new IllegalStateException("whisper entry unavailable");
                    }
                    final State state = new State(true, Stage.CLEAN);
                    return PlayerProfileService.ConditionalMutation.changed(withState(current, state), state);
                });
    }

    public CompletionStage<State> clear(final UUID playerId) {
        return mutate(playerId, current -> new State(false, Stage.CLEAN));
    }

    public CompletionStage<State> forceExpose(final UUID playerId) {
        return mutate(playerId, current -> new State(false, Stage.EXPOSED));
    }

    /** Advances exactly one fixed stage and atomically clears the role at EXPOSED. */
    public CompletionStage<Adjustment> advance(final UUID playerId) {
        return PlayerProfileAuthority.current().mutateSectionConditional(
                playerId, ProfileSectionId.FACTION, FactionSection.class, current -> {
                    final State before = decode(current);
                    if (!before.whisperer()) {
                        return PlayerProfileService.ConditionalMutation.unchanged(
                                new Adjustment(before, false));
                    }
                    final Stage nextStage = before.stage().advance();
                    final boolean exposed = nextStage == Stage.EXPOSED;
                    final State after = new State(!exposed, nextStage);
                    return PlayerProfileService.ConditionalMutation.changed(
                            withState(current, after), new Adjustment(after, exposed));
                });
    }

    /** Removes one stage of pressure. Exposure is final and never restores the role. */
    public CompletionStage<CoverResult> applyCover(final UUID playerId) {
        return PlayerProfileAuthority.current().mutateSectionConditional(
                playerId, ProfileSectionId.FACTION, FactionSection.class, current -> {
                    final State before = decode(current);
                    if (!before.whisperer() || before.stage() == Stage.CLEAN) {
                        return PlayerProfileService.ConditionalMutation.unchanged(
                                new CoverResult(before, false));
                    }
                    final State after = new State(true, before.stage().cover());
                    return PlayerProfileService.ConditionalMutation.changed(
                            withState(current, after), new CoverResult(after, true));
                });
    }

    public enum EvidenceType { RITE, BETRAYAL, OFFERING, UNDEAD }

    public record Incident(UUID id, EvidenceType type, long observedAt) {
        public Incident {
            Objects.requireNonNull(id); Objects.requireNonNull(type);
            if (observedAt < 0) throw new IllegalArgumentException("invalid observation time");
        }
    }

    public enum Withdrawal { LEFT, NOT_ACTIVE, UNRESOLVED, RITE_PENDING }

    /** Withdrawal cannot erase a fresh accusation, legal history, or the entry cooldown. */
    public CompletionStage<Withdrawal> withdraw(final UUID playerId) {
        return PlayerProfileAuthority.current().mutateSectionConditional(
                playerId, ProfileSectionId.FACTION, FactionSection.class, current -> {
                    if (pending(current).isPresent()) return PlayerProfileService.ConditionalMutation.unchanged(Withdrawal.RITE_PENDING);
                    final State before = decode(current);
                    if (!before.whisperer()) return PlayerProfileService.ConditionalMutation.unchanged(Withdrawal.NOT_ACTIVE);
                    final long now = clock.getAsLong();
                    if (before.stage() != Stage.CLEAN || evidence(current).values().stream().anyMatch(v -> expiry(v) > now)) {
                        return PlayerProfileService.ConditionalMutation.unchanged(Withdrawal.UNRESOLVED);
                    }
                    final FactionSection cleared = withState(withExtension(current, EVIDENCE, null), new State(false, Stage.CLEAN));
                    return PlayerProfileService.ConditionalMutation.changed(withReturnAfter(cleared,
                            Math.addExact(now, RETURN_COOLDOWN_MILLIS)), Withdrawal.LEFT);
                });
    }

    /** A witnessed candidate pays no resources and can retry after one clearly announced minute. */
    public CompletionStage<Boolean> interruptCandidate(final UUID playerId) {
        return PlayerProfileAuthority.current().mutateSectionConditional(
                playerId, ProfileSectionId.FACTION, FactionSection.class, current -> {
                    if (!eligible(current, clock.getAsLong()) || pending(current).isPresent()) {
                        return PlayerProfileService.ConditionalMutation.unchanged(false);
                    }
                    return PlayerProfileService.ConditionalMutation.changed(
                            withReturnAfter(current, Math.addExact(clock.getAsLong(), 60_000L)), true);
                });
    }

    private static FactionSection withReturnAfter(final FactionSection current, final long deadline) {
        final Map<String, Long> cooldowns = new LinkedHashMap<>(current.cooldowns());
        cooldowns.put(RETURN_AFTER, Math.max(deadline, cooldowns.getOrDefault(RETURN_AFTER, 0L)));
        return new FactionSection(current.membershipId(), current.lastChosenFaction(), current.everChosen(),
                current.joinedAt(), current.leftAt(), current.history(), current.reputation(), cooldowns, current.extensions());
    }

    public record Accusation(boolean accepted, State state, boolean exposed) { }

    /** Exact evidence is stored on the suspect, so consumption and the legal transition share one WAL. */
    public CompletionStage<Boolean> grantEvidence(final UUID witness, final UUID suspect, final Incident incident, final long ttlMillis) {
        if (witness.equals(suspect) || ttlMillis <= 0L) throw new IllegalArgumentException("invalid evidence");
        return PlayerProfileAuthority.current().mutateSectionConditional(
                suspect, ProfileSectionId.FACTION, FactionSection.class, current -> {
                    final long now = clock.getAsLong();
                    if (!decode(current).whisperer() || incident.observedAt() > now
                            || now - incident.observedAt() >= Math.min(ttlMillis, 86_400_000L)) {
                        return PlayerProfileService.ConditionalMutation.unchanged(false);
                    }
                    final Map<String, Object> entries = evidence(current);
                    entries.entrySet().removeIf(e -> expiry(e.getValue()) <= now);
                    final String key = witness.toString();
                    // One incident cannot be farmed by repeated sightings or duplicate callbacks.
                    if (entries.containsKey(key) || entries.size() >= MAX_WITNESSES) {
                        return PlayerProfileService.ConditionalMutation.unchanged(false);
                    }
                    entries.put(key, Map.of("expires", Math.addExact(incident.observedAt(), Math.min(ttlMillis, 86_400_000L)),
                            "consumed", false, "event-id", incident.id().toString(),
                            "event-type", incident.type().name(), "observed-at", incident.observedAt()));
                    return PlayerProfileService.ConditionalMutation.changed(
                            withExtension(current, EVIDENCE, entries), true);
                });
    }

    public boolean hasEvidence(final UUID witness, final UUID suspect) {
        final Object value = evidence(section(suspect)).get(witness.toString());
        return value instanceof Map<?, ?> entry && expiry(value) > clock.getAsLong()
                && !Boolean.TRUE.equals(entry.get("consumed"));
    }

    public CompletionStage<Accusation> accuse(final UUID witness, final UUID suspect) {
        return PlayerProfileAuthority.current().mutateSectionConditional(
                suspect, ProfileSectionId.FACTION, FactionSection.class, current -> {
                    final State before = decode(current);
                    final Map<String, Object> entries = evidence(current);
                    final Object value = entries.get(witness.toString());
                    if (!(value instanceof Map<?, ?> entry) || expiry(value) <= clock.getAsLong()
                            || Boolean.TRUE.equals(entry.get("consumed"))) {
                        return PlayerProfileService.ConditionalMutation.unchanged(new Accusation(false, before, false));
                    }
                    final Map<String, Object> consumed = new LinkedHashMap<>();
                    entry.forEach((key, item) -> consumed.put((String) key, item));
                    consumed.put("consumed", true);
                    entries.put(witness.toString(), consumed);
                    final Stage next = before.whisperer() ? before.stage().advance() : before.stage();
                    final boolean exposed = before.whisperer() && next == Stage.EXPOSED;
                    final State after = new State(before.whisperer() && !exposed, next);
                    final FactionSection changed = withState(withExtension(current, EVIDENCE, entries), after);
                    return PlayerProfileService.ConditionalMutation.changed(changed, new Accusation(true, after, exposed));
                });
    }

    public long returnRemainingMillis(final UUID playerId) {
        return Math.max(0L, section(playerId).cooldowns().getOrDefault(RETURN_AFTER, 0L) - clock.getAsLong());
    }

    public boolean canEnter(final UUID playerId) {
        final FactionSection current = section(playerId);
        return eligible(current, clock.getAsLong()) && !current.extensions().containsKey(RITE);
    }

    /** A channel pseudonym never contains the account name or account UUID. */
    public CompletionStage<String> channelAlias(final UUID playerId) {
        return PlayerProfileAuthority.current().mutateSectionConditional(
                playerId, ProfileSectionId.FACTION, FactionSection.class, current -> {
                    final Object existing = current.extensions().get(ALIAS);
                    if (existing instanceof String alias && !alias.isBlank()) {
                        return PlayerProfileService.ConditionalMutation.unchanged(alias);
                    }
                    final String alias = newAlias();
                    return PlayerProfileService.ConditionalMutation.changed(withExtension(current, ALIAS, alias), alias);
                });
    }

    public record Rite(UUID operation, List<String> before, List<String> after, double healthBefore, double healthAfter) {
        public Rite {
            before = List.copyOf(before); after = List.copyOf(after);
            if (before.size() != after.size() || before.size() > 64 || !Double.isFinite(healthBefore)
                    || !Double.isFinite(healthAfter) || healthAfter < 1 || healthAfter > healthBefore) {
                throw new IllegalArgumentException("invalid rite snapshot");
            }
        }
    }

    public Optional<Rite> pendingRite(final UUID playerId) {
        return pending(section(playerId));
    }

    public CompletionStage<Boolean> prepareRite(final UUID playerId, final Rite rite) {
        return PlayerProfileAuthority.current().mutateSectionConditional(
                playerId, ProfileSectionId.FACTION, FactionSection.class, current -> {
                    if (!eligible(current, clock.getAsLong()) || pending(current).isPresent()) {
                        return PlayerProfileService.ConditionalMutation.unchanged(false);
                    }
                    final Map<String, Object> value = Map.of("id", rite.operation().toString(),
                            "before", rite.before(), "after", rite.after(),
                            "health-before", rite.healthBefore(), "health-after", rite.healthAfter());
                    return PlayerProfileService.ConditionalMutation.changed(withExtension(current, RITE, value), true);
                });
    }

    /** Invoked only after inventory+health have been saved together, or before either was changed on abort. */
    public CompletionStage<Boolean> finishRite(final UUID playerId, final UUID operation, final boolean grant) {
        return PlayerProfileAuthority.current().mutateSectionConditional(
                playerId, ProfileSectionId.FACTION, FactionSection.class, current -> {
                    final Optional<Rite> pending = pending(current);
                    if (pending.isEmpty() || !pending.get().operation().equals(operation)) {
                        return PlayerProfileService.ConditionalMutation.unchanged(false);
                    }
                    if (grant && !eligible(current, clock.getAsLong())) {
                        throw new IllegalStateException("rite eligibility changed; pending receipt requires recovery");
                    }
                    final FactionSection cleared = withExtension(current, RITE, null);
                    return PlayerProfileService.ConditionalMutation.changed(
                            grant ? withState(cleared, new State(true, Stage.CLEAN)) : cleared, true);
                });
    }

    private static Optional<Rite> pending(final FactionSection current) {
        final Object raw = current.extensions().get(RITE);
        if (raw == null) return Optional.empty();
        if (!(raw instanceof Map<?, ?> map) || !(map.get("before") instanceof List<?> before)
                || !(map.get("after") instanceof List<?> after)
                || !(map.get("health-before") instanceof Number healthBefore)
                || !(map.get("health-after") instanceof Number healthAfter)) {
            throw new IllegalStateException("invalid pending rite");
        }
        return Optional.of(new Rite(UUID.fromString((String) map.get("id")),
                before.stream().map(String.class::cast).toList(), after.stream().map(String.class::cast).toList(),
                healthBefore.doubleValue(), healthAfter.doubleValue()));
    }

    private static boolean eligible(final FactionSection current, final long now) {
        return java.util.Set.of("red", "blue", "neutral").contains(current.membershipId().toLowerCase(java.util.Locale.ROOT))
                && !Boolean.TRUE.equals(current.extensions().get("sin.exiled"))
                && !decode(current).whisperer() && current.cooldowns().getOrDefault(RETURN_AFTER, 0L) <= now;
    }

    private static FactionSection section(final UUID id) {
        return PlayerProfileAuthority.current().requireSection(id, ProfileSectionId.FACTION, FactionSection.class);
    }

    private static Map<String, Object> evidence(final FactionSection section) {
        final Object raw = section.extensions().get(EVIDENCE);
        final Map<String, Object> result = new LinkedHashMap<>();
        if (raw == null) return result;
        if (!(raw instanceof Map<?, ?> values) || values.size() > MAX_WITNESSES) {
            throw new IllegalStateException("invalid whisper evidence");
        }
        values.forEach((k,v) -> { UUID.fromString((String) k); expiry(v); result.put((String) k, v); });
        return result;
    }

    private static long expiry(final Object value) {
        if (!(value instanceof Map<?, ?> entry) || !(entry.get("expires") instanceof Number expiry)
                || !(entry.get("consumed") instanceof Boolean) || expiry.longValue() < 0L) {
            throw new IllegalStateException("invalid whisper evidence entry");
        }
        UUID.fromString((String) entry.get("event-id"));
        EvidenceType.valueOf((String) entry.get("event-type"));
        if (!(entry.get("observed-at") instanceof Number observedAt) || observedAt.longValue() < 0
                || observedAt.longValue() >= expiry.longValue()) throw new IllegalStateException("invalid evidence time");
        return expiry.longValue();
    }

    private static String newAlias() { return "Árny-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12); }

    private static FactionSection withExtension(final FactionSection current, final String key, final Object value) {
        final Map<String, Object> extensions = new LinkedHashMap<>(current.extensions());
        if (value == null) extensions.remove(key); else extensions.put(key, value);
        return new FactionSection(current.membershipId(), current.lastChosenFaction(), current.everChosen(),
                current.joinedAt(), current.leftAt(), current.history(), current.reputation(), current.cooldowns(), extensions);
    }

    private CompletionStage<State> mutate(
            final UUID playerId,
            final java.util.function.Function<State, State> mutation) {
        Objects.requireNonNull(mutation, "mutation");
        return PlayerProfileAuthority.current().mutateSectionConditional(
                playerId, ProfileSectionId.FACTION, FactionSection.class, current -> {
                    final State before = decode(current);
                    final State after = Objects.requireNonNull(mutation.apply(before), "whisper state");
                    if (after.equals(before)) {
                        return PlayerProfileService.ConditionalMutation.unchanged(before);
                    }
                    return PlayerProfileService.ConditionalMutation.changed(
                            withState(current, after), after);
                });
    }

    private static State decode(final FactionSection section) {
        final Object rawFlag = section.extensions().get(WHISPERER);
        final boolean whisperer;
        if (rawFlag == null) whisperer = false;
        else if (rawFlag instanceof Boolean value) whisperer = value;
        else throw new IllegalStateException("invalid whisperer flag type");
        final long rawStage = section.reputation().getOrDefault(EXPOSURE_STAGE, 0L);
        if (rawStage < 0L || rawStage >= Stage.values().length) {
            throw new IllegalStateException("invalid whisper exposure stage");
        }
        final Stage stage = Stage.values()[(int) rawStage];
        if (!whisperer && stage != Stage.CLEAN && stage != Stage.EXPOSED) {
            throw new IllegalStateException("inactive whisperer retains intermediate exposure stage");
        }
        return new State(whisperer, stage);
    }

    private FactionSection withState(final FactionSection current, final State state) {
        final LinkedHashMap<String, Long> reputation = new LinkedHashMap<>(current.reputation());
        if (state.stage() == Stage.CLEAN) reputation.remove(EXPOSURE_STAGE);
        else reputation.put(EXPOSURE_STAGE, (long) state.stage().ordinal());
        final LinkedHashMap<String, Object> extensions = new LinkedHashMap<>(current.extensions());
        if (state.whisperer()) extensions.put(WHISPERER, true);
        else extensions.remove(WHISPERER);
        final LinkedHashMap<String, Long> cooldowns = new LinkedHashMap<>(current.cooldowns());
        if (state.whisperer() && !extensions.containsKey(ALIAS)) extensions.put(ALIAS, newAlias());
        if (state.stage() == Stage.EXPOSED) {
            extensions.put("sin.exiled", true);
            if (decode(current).stage() != Stage.EXPOSED) {
                cooldowns.put(RETURN_AFTER, Math.addExact(clock.getAsLong(), RETURN_COOLDOWN_MILLIS));
            }
        }
        return new FactionSection(current.membershipId(), current.lastChosenFaction(),
                current.everChosen(), current.joinedAt(), current.leftAt(), current.history(),
                reputation, cooldowns, extensions);
    }
}
