package hu.taliann.icesmp.playerprofile.application;

import hu.taliann.icesmp.data.ProfessionType;
import hu.taliann.icesmp.playerprofile.domain.ProfileSectionId;
import hu.taliann.icesmp.playerprofile.domain.section.ProfessionProfileState;
import hu.taliann.icesmp.playerprofile.domain.section.ProfessionSection;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/**
 * CAS-backed weekly profession-guild contribution, award and claim state.
 *
 * <p>The award marker and the pending rewards live in the same PROFESSIONS section as the
 * experience map, so the claim clears the pending entry and credits the XP in one section
 * commit — a crash can never duplicate or lose an already-claimed reward.</p>
 */
public final class PlayerProfileWeeklyGoalStore {
    private static final String WEEK_KEY = "weekly-goal.week";
    private static final String REWARDED_WEEK_KEY = "weekly-goal.rewarded-week";
    private static final String PENDING_PREFIX = "weekly-goal.pending.";

    /** Adds contribution units for the given week; a stale stored week resets the progress. */
    public CompletionStage<Long> recordContribution(final UUID playerId,
                                                    final ProfessionType profession,
                                                    final long units,
                                                    final long week) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(profession, "profession");
        if (units <= 0L) throw new IllegalArgumentException("weekly contribution must be positive");
        if (week < 0L) throw new IllegalArgumentException("negative week index");
        return PlayerProfileAuthority.current().mutateSectionConditional(
                playerId, ProfileSectionId.PROFESSIONS, ProfessionSection.class, current -> {
                    final long storedWeek = longValue(current.extensions().get(WEEK_KEY), -1L);
                    final Map<String, Long> progress = storedWeek == week
                            ? new LinkedHashMap<>(current.weeklyProgress())
                            : new LinkedHashMap<>();
                    final long total = Math.addExact(
                            progress.getOrDefault(profession.getId(), 0L), units);
                    progress.put(profession.getId(), total);
                    final Map<String, Object> extensions = new LinkedHashMap<>(current.extensions());
                    extensions.put(WEEK_KEY, week);
                    return PlayerProfileService.ConditionalMutation.changed(
                            withState(current, progress, extensions), total);
                });
    }

    /**
     * Converts the player's evaluated-week contributions into pending rewards. Idempotent per
     * (player, week): a repeated evaluation after a crash cannot double-award.
     */
    public CompletionStage<Map<String, Long>> award(final UUID playerId,
                                                    final long evaluatedWeek,
                                                    final Map<String, Integer> rewardXpByProfession,
                                                    final long minContribution) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(rewardXpByProfession, "rewardXpByProfession");
        if (evaluatedWeek < 0L) throw new IllegalArgumentException("negative week index");
        if (minContribution < 1L) throw new IllegalArgumentException("minContribution must be at least 1");
        final Map<String, Integer> rewards = Map.copyOf(rewardXpByProfession);
        return PlayerProfileAuthority.current().mutateSectionConditional(
                playerId, ProfileSectionId.PROFESSIONS, ProfessionSection.class, current -> {
                    if (longValue(current.extensions().get(REWARDED_WEEK_KEY), -1L) == evaluatedWeek) {
                        return PlayerProfileService.ConditionalMutation.unchanged(Map.of());
                    }
                    if (longValue(current.extensions().get(WEEK_KEY), -1L) != evaluatedWeek) {
                        return PlayerProfileService.ConditionalMutation.unchanged(Map.of());
                    }
                    final Map<String, Object> extensions = new LinkedHashMap<>(current.extensions());
                    final Map<String, Long> awarded = new LinkedHashMap<>();
                    for (final Map.Entry<String, Integer> entry : rewards.entrySet()) {
                        final long xp = entry.getValue() == null ? 0L : entry.getValue();
                        final long contribution = current.weeklyProgress()
                                .getOrDefault(entry.getKey(), 0L);
                        if (xp <= 0L || contribution < minContribution) continue;
                        final String pendingKey = PENDING_PREFIX + entry.getKey();
                        final long pending = longValue(extensions.get(pendingKey), 0L);
                        extensions.put(pendingKey, Math.addExact(pending, xp));
                        awarded.put(entry.getKey(), xp);
                    }
                    extensions.put(REWARDED_WEEK_KEY, evaluatedWeek);
                    extensions.remove(WEEK_KEY);
                    return PlayerProfileService.ConditionalMutation.changed(
                            withState(current, Map.of(), extensions), Map.copyOf(awarded));
                });
    }

    /** Credits every pending reward as profession XP and clears it in the same section commit. */
    public CompletionStage<List<ClaimedReward>> claim(final UUID playerId,
                                                      final int baseXp,
                                                      final int incrementPerLevel,
                                                      final int maxLevel) {
        Objects.requireNonNull(playerId, "playerId");
        return PlayerProfileAuthority.current().mutateSectionConditional(
                playerId, ProfileSectionId.PROFESSIONS, ProfessionSection.class, current -> {
                    final Map<String, Long> pending = pendingOf(current);
                    if (pending.isEmpty()) {
                        return PlayerProfileService.ConditionalMutation.unchanged(List.of());
                    }
                    final Map<String, Object> extensions = new LinkedHashMap<>(current.extensions());
                    final List<ClaimedReward> claimed = new ArrayList<>();
                    ProfessionSection section = current;
                    for (final Map.Entry<String, Long> entry : pending.entrySet()) {
                        extensions.remove(PENDING_PREFIX + entry.getKey());
                        final ProfessionType profession = ProfessionType.fromId(entry.getKey());
                        if (profession == null || entry.getValue() <= 0L) continue;
                        final ProfessionProfileState.ExperienceMutation mutation =
                                ProfessionProfileState.addExperience(section, profession,
                                        entry.getValue(), baseXp, incrementPerLevel, maxLevel);
                        section = mutation.section();
                        claimed.add(new ClaimedReward(entry.getKey(), entry.getValue(),
                                mutation.previousLevel(), mutation.level()));
                    }
                    return PlayerProfileService.ConditionalMutation.changed(
                            withState(section, section.weeklyProgress(), extensions),
                            List.copyOf(claimed));
                });
    }

    /** Undelivered reward XP per profession id, decoded from the section extensions. */
    public static Map<String, Long> pendingOf(final ProfessionSection section) {
        final Map<String, Long> pending = new LinkedHashMap<>();
        for (final Map.Entry<String, Object> entry : section.extensions().entrySet()) {
            if (!entry.getKey().startsWith(PENDING_PREFIX)) continue;
            pending.put(entry.getKey().substring(PENDING_PREFIX.length()),
                    longValue(entry.getValue(), 0L));
        }
        return pending;
    }

    private static ProfessionSection withState(final ProfessionSection base,
                                               final Map<String, Long> weeklyProgress,
                                               final Map<String, Object> extensions) {
        return new ProfessionSection(base.experience(), base.levels(), base.specializations(),
                base.recipes(), weeklyProgress, extensions);
    }

    private static long longValue(final Object raw, final long fallback) {
        if (raw == null) return fallback;
        if (raw instanceof Number number) return number.longValue();
        throw new IllegalStateException("Invalid weekly-goal numeric extension: " + raw);
    }

    public record ClaimedReward(String professionId, long xp, int previousLevel, int level) {
        public ClaimedReward {
            Objects.requireNonNull(professionId, "professionId");
            if (xp <= 0L) throw new IllegalArgumentException("claimed XP must be positive");
        }
    }
}
