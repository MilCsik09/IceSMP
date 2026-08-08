package hu.taliann.icesmp.playerprofile.application;

import hu.taliann.icesmp.playerprofile.domain.ProfileSectionId;
import hu.taliann.icesmp.playerprofile.domain.section.AchievementSection;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/**
 * Canonical CAS-backed achievement, bestiary and per-player discovery authority.
 * Global first-discoverer records remain separate shared world aggregates.
 */
public final class PlayerProfileAchievementStore {

    private static final String HIDDEN_SPOT_PREFIX = "hidden-spot:";
    private static final String BESTIARY_SEPARATOR = ":";

    public Set<String> unlocked(final UUID playerId) {
        return section(playerId).unlocked();
    }

    public boolean isUnlocked(final UUID playerId, final String id) {
        return section(playerId).unlocked().contains(id(id));
    }

    public CompletionStage<Boolean> unlock(final UUID playerId, final String rawId) {
        final String achievementId = id(rawId);
        return PlayerProfileAuthority.current().mutateSectionConditional(
                playerId, ProfileSectionId.ACHIEVEMENTS, AchievementSection.class, current -> {
                    if (current.unlocked().contains(achievementId)) {
                        return PlayerProfileService.ConditionalMutation.unchanged(false);
                    }
                    final LinkedHashSet<String> unlocked = new LinkedHashSet<>(current.unlocked());
                    unlocked.add(achievementId);
                    final AchievementSection next = new AchievementSection(unlocked,
                            current.publicAchievements(), current.claimedRewards(),
                            current.bestiary(), current.extensions());
                    return PlayerProfileService.ConditionalMutation.changed(next, true);
                });
    }

    /**
     * Reserves one reward receipt before the non-transactional Bukkit delivery begins.
     * This is deliberately at-most-once: a crash can require admin reconciliation, but
     * reconnect/retry cannot duplicate currency or items.
     */
    public CompletionStage<Boolean> reserveReward(final UUID playerId, final String rawReceipt) {
        final String receipt = id(rawReceipt);
        return PlayerProfileAuthority.current().mutateSectionConditional(
                playerId, ProfileSectionId.ACHIEVEMENTS, AchievementSection.class, current -> {
                    if (current.claimedRewards().contains(receipt)) {
                        return PlayerProfileService.ConditionalMutation.unchanged(false);
                    }
                    final LinkedHashSet<String> claimed = new LinkedHashSet<>(current.claimedRewards());
                    claimed.add(receipt);
                    final AchievementSection next = new AchievementSection(current.unlocked(),
                            current.publicAchievements(), claimed,
                            current.bestiary(), current.extensions());
                    return PlayerProfileService.ConditionalMutation.changed(next, true);
                });
    }

    public boolean rewardReserved(final UUID playerId, final String rawReceipt) {
        return section(playerId).claimedRewards().contains(id(rawReceipt));
    }

    public Set<String> bestiaryEntries(final UUID playerId, final String rawCategory) {
        final String prefix = "bestiary:" + id(rawCategory) + BESTIARY_SEPARATOR;
        final TreeSet<String> entries = new TreeSet<>();
        section(playerId).bestiary().forEach((key, value) -> {
            if (value > 0L && key.startsWith(prefix)) {
                entries.add(key.substring(prefix.length()));
            }
        });
        return Set.copyOf(entries);
    }

    public CompletionStage<BestiaryRecord> recordBestiary(final UUID playerId,
                                                           final String rawCategory,
                                                           final String rawEntry) {
        final String category = id(rawCategory);
        final String entry = id(rawEntry);
        final String key = "bestiary:" + category + BESTIARY_SEPARATOR + entry;
        return PlayerProfileAuthority.current().mutateSectionConditional(
                playerId, ProfileSectionId.ACHIEVEMENTS, AchievementSection.class, current -> {
                    final long present = current.bestiary().getOrDefault(key, 0L);
                    final int existingCount = count(current, category);
                    if (present > 0L) {
                        return PlayerProfileService.ConditionalMutation.unchanged(
                                new BestiaryRecord(false, existingCount));
                    }
                    final LinkedHashMap<String, Long> bestiary = new LinkedHashMap<>(current.bestiary());
                    bestiary.put(key, 1L);
                    final AchievementSection next = new AchievementSection(current.unlocked(),
                            current.publicAchievements(), current.claimedRewards(),
                            bestiary, current.extensions());
                    return PlayerProfileService.ConditionalMutation.changed(next,
                            new BestiaryRecord(true, Math.addExact(existingCount, 1)));
                });
    }

    public boolean hasVisitedHiddenSpot(final UUID playerId, final String rawSpotId) {
        return section(playerId).unlocked().contains(HIDDEN_SPOT_PREFIX + id(rawSpotId));
    }

    public CompletionStage<Boolean> markHiddenSpotVisited(final UUID playerId,
                                                           final String rawSpotId) {
        return unlock(playerId, HIDDEN_SPOT_PREFIX + id(rawSpotId));
    }

    private AchievementSection section(final UUID playerId) {
        return PlayerProfileAuthority.current().requireSection(
                Objects.requireNonNull(playerId, "playerId"),
                ProfileSectionId.ACHIEVEMENTS, AchievementSection.class);
    }

    private static int count(final AchievementSection section, final String category) {
        final String prefix = "bestiary:" + category + BESTIARY_SEPARATOR;
        int count = 0;
        for (final var entry : section.bestiary().entrySet()) {
            if (entry.getValue() > 0L && entry.getKey().startsWith(prefix)) count++;
        }
        return count;
    }

    private static String id(final String raw) {
        if (raw == null || raw.isBlank()) throw new IllegalArgumentException("id cannot be blank");
        final String normalized = raw.trim().toLowerCase(Locale.ROOT);
        if (normalized.length() > 112 || !normalized.matches("[a-z0-9][a-z0-9._:-]*")) {
            throw new IllegalArgumentException("invalid achievement state id: " + raw);
        }
        return normalized;
    }

    public record BestiaryRecord(boolean created, int categoryCount) {
        public BestiaryRecord {
            if (categoryCount < 0) throw new IllegalArgumentException("negative category count");
        }
    }
}
