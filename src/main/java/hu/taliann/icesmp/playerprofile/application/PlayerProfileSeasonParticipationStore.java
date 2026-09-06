package hu.taliann.icesmp.playerprofile.application;

import hu.taliann.icesmp.data.FactionType;
import hu.taliann.icesmp.playerprofile.domain.ProfileSectionId;
import hu.taliann.icesmp.playerprofile.domain.section.FactionSection;
import java.util.*;
import java.util.concurrent.CompletionStage;

/** Personal qualifying activity, bounded and persisted on the canonical faction profile. */
public final class PlayerProfileSeasonParticipationStore {
    private static final String KEY = "season.participation";
    public record State(int season, String faction, long joinedAt, long lastActive, Set<String> activities) {
        public State { activities = Set.copyOf(activities); }
    }

    /** At most one qualifying contribution per activity category and UTC day; never mere online time. */
    public CompletionStage<Boolean> record(final UUID player, final FactionType faction,
                                           final int season, final String source) {
        if (season < 1 || source == null || !source.matches("[a-z-]{1,32}")) throw new IllegalArgumentException("invalid season activity");
        return PlayerProfileAuthority.current().mutateSectionConditional(player, ProfileSectionId.FACTION,
                FactionSection.class, current -> {
                    if (!current.membershipId().equalsIgnoreCase(faction.name())
                            || Boolean.TRUE.equals(current.extensions().get("sin.exiled")) && faction != FactionType.DARK) {
                        return PlayerProfileService.ConditionalMutation.unchanged(false);
                    }
                    final State before = decode(current);
                    final long now = System.currentTimeMillis();
                    final Set<String> activities = matches(before, current, faction, season)
                            ? new LinkedHashSet<>(before.activities()) : new LinkedHashSet<>();
                    final String receipt = source + ':' + now / 86_400_000L;
                    if (!activities.add(receipt)) return PlayerProfileService.ConditionalMutation.unchanged(false);
                    if (activities.size() > 512) throw new IllegalStateException("season activity limit reached");
                    final Map<String, Object> extensions = new LinkedHashMap<>(current.extensions());
                    extensions.put(KEY, Map.of("season", season, "faction", faction.name(),
                            "joined-at", current.joinedAt(), "last-active", now, "activities", List.copyOf(activities)));
                    final FactionSection next = new FactionSection(current.membershipId(), current.lastChosenFaction(),
                            current.everChosen(), current.joinedAt(), current.leftAt(), current.history(),
                            current.reputation(), current.cooldowns(), extensions);
                    return PlayerProfileService.ConditionalMutation.changed(next, true);
                });
    }

    public CompletionStage<Optional<State>> load(final UUID id) {
        return PlayerProfileAuthority.current().repository().loadSnapshot(id).thenApply(snapshot -> {
            final FactionSection section = snapshot.faction().value();
            final State state = decode(section);
            return state != null && state.joinedAt() == section.joinedAt()
                    && state.faction().equalsIgnoreCase(section.membershipId()) ? Optional.of(state) : Optional.empty();
        });
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
                new LinkedHashSet<>(activities.stream().map(String.class::cast).toList()));
    }
}
