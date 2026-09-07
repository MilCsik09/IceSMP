package hu.taliann.icesmp.playerprofile.application;

import hu.taliann.icesmp.data.FactionType;
import hu.taliann.icesmp.integrity.*;
import hu.taliann.icesmp.playerprofile.domain.ProfileSectionId;
import hu.taliann.icesmp.playerprofile.domain.section.FactionSection;
import java.util.*;
import java.util.concurrent.CompletionStage;

/** Personal qualifying activity, bounded and persisted on the canonical faction profile. */
public final class PlayerProfileSeasonParticipationStore {
    private static final String KEY = "season.participation";
    private final java.util.function.LongSupplier clock;
    public PlayerProfileSeasonParticipationStore() { this(System::currentTimeMillis); }
    public PlayerProfileSeasonParticipationStore(java.util.function.LongSupplier clock) { this.clock = Objects.requireNonNull(clock); }
    public record State(int season, String faction, long joinedAt, long lastActive, Set<String> activities, long profileRevision) {
        public State { activities = Set.copyOf(activities); if (profileRevision < 0) throw new IllegalArgumentException("Invalid participation revision"); }
        public static State newest(State before, State candidate) {
            if (before.profileRevision() == candidate.profileRevision() && !before.equals(candidate))
                throw new IllegalStateException("Conflicting participation generation");
            return before.profileRevision() > candidate.profileRevision() ? before : candidate;
        }
    }

    /** At most one qualifying contribution per activity category and UTC day; never mere online time. */
    public CompletionStage<Boolean> record(final UUID player, final FactionType faction,
                                           final int season, final String source) {
        return record(player, faction, season, source, RewardContext.recipientOnly(RewardChannel.SEASON_CREDIT, player));
    }

    public CompletionStage<Boolean> record(final UUID player, final FactionType faction,
            final int season, final String source, final RewardContext reward) {
        Objects.requireNonNull(faction); Objects.requireNonNull(reward).require(RewardChannel.SEASON_CREDIT, player);
        if (season < 1 || source == null || !source.matches("[a-z-]{1,32}")) throw new IllegalArgumentException("invalid season activity");
        final long now = clock.getAsLong();
        if (now < 0) throw new IllegalArgumentException("Invalid activity time");
        return PlayerProfileAuthority.current().mutateRewardSectionConditional(player, ProfileSectionId.FACTION,
                FactionSection.class, reward, current -> {
                    if (!current.membershipId().equalsIgnoreCase(faction.name())
                            || Boolean.TRUE.equals(current.extensions().get("sin.exiled")) && faction != FactionType.DARK) {
                        return PlayerProfileService.ConditionalMutation.unchanged(false);
                    }
                    final State before = decode(current);
                    if (before != null && before.season() > season) return PlayerProfileService.ConditionalMutation.unchanged(false);
                    final Set<String> activities = matches(before, current, faction, season)
                            ? new LinkedHashSet<>(before.activities()) : new LinkedHashSet<>();
                    final String receipt = source + ':' + now / 86_400_000L;
                    if (!activities.add(receipt)) return PlayerProfileService.ConditionalMutation.unchanged(false);
                    if (activities.size() > 512) throw new IllegalStateException("season activity limit reached");
                    final Map<String, Object> extensions = new LinkedHashMap<>(current.extensions());
                    extensions.put(KEY, Map.of("season", season, "faction", faction.name(),
                            "joined-at", current.joinedAt(), "last-active", matches(before, current, faction, season)
                                    ? Math.max(now, before.lastActive()) : now, "activities", List.copyOf(activities)));
                    final FactionSection next = new FactionSection(current.membershipId(), current.lastChosenFaction(),
                            current.everChosen(), current.joinedAt(), current.leftAt(), current.history(),
                            current.reputation(), current.cooldowns(), extensions);
                    return PlayerProfileService.ConditionalMutation.changed(next, true);
                });
    }

    public CompletionStage<Optional<State>> load(final UUID id) {
        return PlayerProfileAuthority.current().repository().loadSnapshot(id).thenApply(snapshot -> {
            final FactionSection section = snapshot.faction().value();
            final State decoded = decode(section);
            final State state = decoded == null ? null : new State(decoded.season(), decoded.faction(), decoded.joinedAt(),
                    decoded.lastActive(), decoded.activities(), snapshot.profileRevision());
            return state != null && state.joinedAt() == section.joinedAt()
                    && state.faction().equalsIgnoreCase(section.membershipId()) ? Optional.of(state) : Optional.empty();
        });
    }

    public boolean matchesCurrentMembership(final UUID player, final State expected) {
        Objects.requireNonNull(expected);
        try {
            final var current = section(player);
            return expected.joinedAt() == current.joinedAt() && expected.faction().equalsIgnoreCase(current.membershipId())
                    && (!Boolean.TRUE.equals(current.extensions().get("sin.exiled")) || FactionType.DARK.name().equals(expected.faction()));
        } catch (final RuntimeException | LinkageError unavailable) { return false; }
    }

    public int contributions(final UUID player, final FactionType faction, final int season) {
        final FactionSection section = section(player);
        final State state = decode(section);
        return matches(state, section, faction, season) ? state.activities().size() : 0;
    }

    public boolean active(final UUID player, final FactionType faction, final int season, final long now) {
        final FactionSection section = section(player);
        final State state = decode(section);
        return matches(state, section, faction, season) && state.lastActive() > now - 7L * 86_400_000L;
    }

    private static boolean matches(final State state, final FactionSection current, final FactionType faction, final int season) {
        return state != null && state.season() == season && state.faction().equals(faction.name())
                && state.joinedAt() == current.joinedAt() && current.membershipId().equalsIgnoreCase(faction.name());
    }

    private static FactionSection section(final UUID id) {
        return PlayerProfileAuthority.current().requireSection(id, ProfileSectionId.FACTION, FactionSection.class);
    }

    private static State decode(final FactionSection section) {
        final Object raw = section.extensions().get(KEY);
        if (raw == null) return null;
        if (!(raw instanceof Map<?, ?> value) || !(value.get("season") instanceof Number season)
                || !(value.get("faction") instanceof String faction) || !(value.get("joined-at") instanceof Number joined)
                || !(value.get("last-active") instanceof Number active) || !(value.get("activities") instanceof List<?> activities)
                || activities.size() > 512) throw new IllegalStateException("invalid season participation");
        return new State(season.intValue(), faction, joined.longValue(), active.longValue(),
                new LinkedHashSet<>(activities.stream().map(String.class::cast).toList()), 0);
    }
}
