package hu.taliann.icesmp.playerprofile.application;

import hu.taliann.icesmp.playerprofile.domain.PlayerProfileSectionExtensions;
import hu.taliann.icesmp.playerprofile.domain.ProfileSectionId;
import hu.taliann.icesmp.playerprofile.domain.section.FactionSection;

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/** CAS-backed faction-food duty timestamp and faction binding. */
public final class PlayerProfileFactionFoodStore {
    private static final String LAST_KEY = "food-duty.last-home-food";
    private static final String FACTION_KEY = "food-duty.faction";

    public FoodState read(final UUID playerId) {
        final FactionSection section = PlayerProfileAuthority.current().requireSection(
                Objects.requireNonNull(playerId, "playerId"),
                ProfileSectionId.FACTION, FactionSection.class);
        final Object rawLast = section.extensions().get(LAST_KEY);
        final long last;
        if (rawLast == null) last = -1L;
        else if (rawLast instanceof Number number) last = number.longValue();
        else throw new IllegalStateException("Invalid food-duty timestamp type");
        if (last < -1L) throw new IllegalStateException("Negative food-duty timestamp");
        final Object rawFaction = section.extensions().get(FACTION_KEY);
        final String faction;
        if (rawFaction == null) faction = "";
        else if (rawFaction instanceof String value) faction = normalizeFaction(value);
        else throw new IllegalStateException("Invalid food-duty faction type");
        return new FoodState(last, faction);
    }

    public CompletionStage<FoodState> record(final UUID playerId,
                                             final String rawFaction,
                                             final long timestamp) {
        final String faction = normalizeFaction(rawFaction);
        if (timestamp < 0L) throw new IllegalArgumentException("negative food-duty timestamp");
        return PlayerProfileAuthority.current().mutateSectionConditional(
                playerId, ProfileSectionId.FACTION, FactionSection.class, current -> {
                    final FoodState existing = readCurrent(current);
                    final FoodState result = new FoodState(timestamp, faction);
                    if (existing.equals(result)) {
                        return PlayerProfileService.ConditionalMutation.unchanged(result);
                    }
                    FactionSection next = PlayerProfileSectionExtensions.put(current, LAST_KEY, timestamp);
                    next = PlayerProfileSectionExtensions.put(next, FACTION_KEY, faction);
                    return PlayerProfileService.ConditionalMutation.changed(next, result);
                });
    }

    private static FoodState readCurrent(final FactionSection section) {
        final Object rawLast = section.extensions().get(LAST_KEY);
        final long last = rawLast == null ? -1L : ((Number) rawLast).longValue();
        final Object rawFaction = section.extensions().get(FACTION_KEY);
        final String faction = rawFaction == null ? "" : normalizeFaction((String) rawFaction);
        return new FoodState(last, faction);
    }

    private static String normalizeFaction(final String raw) {
        final String value = raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
        if (!value.isEmpty() && !java.util.Set.of("RED", "BLUE", "NEUTRAL", "DARK").contains(value)) {
            throw new IllegalArgumentException("invalid faction food binding: " + raw);
        }
        return value;
    }

    public record FoodState(long lastHomeFoodAt, String faction) {
        public FoodState {
            if (lastHomeFoodAt < -1L) throw new IllegalArgumentException("invalid timestamp");
            faction = normalizeFaction(faction);
        }
    }
}
