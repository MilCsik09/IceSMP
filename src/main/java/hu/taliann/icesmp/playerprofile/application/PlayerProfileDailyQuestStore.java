package hu.taliann.icesmp.playerprofile.application;

import hu.taliann.icesmp.playerprofile.domain.ProfileSectionId;
import hu.taliann.icesmp.playerprofile.domain.section.QuestSection;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/** Daily/weekly quest progress, streak and reward receipt authority. */
public final class PlayerProfileDailyQuestStore {
    private static final String DAILY = "daily";
    private static final String WEEKLY = "weekly";
    private static final String PERIOD = "period";
    private static final String PROGRESS = "progress";
    private static final String DONE = "done";
    private static final String STREAK = "daily.streak";
    private static final String LAST_DONE = "daily.last-done";

    public PeriodState state(final UUID playerId, final boolean weekly,
                             final long currentPeriod) {
        if (currentPeriod < 0L) throw new IllegalArgumentException("negative quest period");
        final QuestSection section = PlayerProfileAuthority.current().requireSection(
                Objects.requireNonNull(playerId, "playerId"),
                ProfileSectionId.QUESTS, QuestSection.class);
        return state(section, slot(weekly), currentPeriod);
    }

    public int streak(final UUID playerId) {
        final QuestSection section = PlayerProfileAuthority.current().requireSection(
                Objects.requireNonNull(playerId, "playerId"),
                ProfileSectionId.QUESTS, QuestSection.class);
        return Math.toIntExact(section.cooldowns().getOrDefault(STREAK, 0L));
    }

    public CompletionStage<AdvanceResult> advanceDaily(final UUID playerId,
                                                        final long day,
                                                        final int target) {
        return advance(playerId, false, day, target);
    }

    public CompletionStage<AdvanceResult> advanceWeekly(final UUID playerId,
                                                         final long week,
                                                         final int target) {
        return advance(playerId, true, week, target);
    }

    private CompletionStage<AdvanceResult> advance(final UUID playerId,
                                                    final boolean weekly,
                                                    final long period,
                                                    final int target) {
        if (period < 0L || target <= 0) throw new IllegalArgumentException("invalid quest advance");
        final String slot = slot(weekly);
        return PlayerProfileAuthority.current().mutateSectionConditional(
                playerId, ProfileSectionId.QUESTS, QuestSection.class, current -> {
                    final PeriodState previous = state(current, slot, period);
                    if (previous.done()) {
                        return PlayerProfileService.ConditionalMutation.unchanged(
                                new AdvanceResult(previous.progress(), true, false,
                                        Math.toIntExact(current.cooldowns().getOrDefault(STREAK, 0L))));
                    }
                    final int progress = Math.min(target,
                            Math.addExact(previous.progress(), 1));
                    final boolean completedNow = progress >= target;
                    final LinkedHashMap<String, Map<String, Long>> active =
                            new LinkedHashMap<>(current.active());
                    active.put(slot, Map.of(PERIOD, period, PROGRESS, (long) progress,
                            DONE, completedNow ? 1L : 0L));
                    final LinkedHashMap<String, Long> cooldowns =
                            new LinkedHashMap<>(current.cooldowns());
                    int streak = Math.toIntExact(cooldowns.getOrDefault(STREAK, 0L));
                    final LinkedHashSet<String> completed =
                            new LinkedHashSet<>(current.completed());
                    final LinkedHashSet<String> receipts =
                            new LinkedHashSet<>(current.rewardReceipts());
                    if (completedNow) {
                        final String receipt = slot + ':' + period;
                        completed.add(receipt);
                        receipts.add(receipt);
                        if (!weekly) {
                            final long lastDone = cooldowns.getOrDefault(LAST_DONE, Long.MAX_VALUE);
                            streak = lastDone == period - 1L ? Math.addExact(streak, 1) : 1;
                            cooldowns.put(STREAK, (long) streak);
                            cooldowns.put(LAST_DONE, period);
                        }
                    }
                    final QuestSection next = new QuestSection(active, completed, receipts,
                            cooldowns, current.communityContributions(),
                            current.claimableRewards(), current.extensions());
                    return PlayerProfileService.ConditionalMutation.changed(next,
                            new AdvanceResult(progress, completedNow, completedNow, streak));
                });
    }

    private static PeriodState state(final QuestSection section, final String slot,
                                     final long currentPeriod) {
        final Map<String, Long> raw = section.active().get(slot);
        if (raw == null || raw.getOrDefault(PERIOD, Long.MIN_VALUE) != currentPeriod) {
            return new PeriodState(0, false);
        }
        final long progress = raw.getOrDefault(PROGRESS, 0L);
        if (progress < 0L || progress > Integer.MAX_VALUE) {
            throw new IllegalStateException("invalid PlayerProfile quest progress");
        }
        final long done = raw.getOrDefault(DONE, 0L);
        if (done != 0L && done != 1L) {
            throw new IllegalStateException("invalid PlayerProfile quest completion marker");
        }
        return new PeriodState((int) progress, done == 1L);
    }

    private static String slot(final boolean weekly) { return weekly ? WEEKLY : DAILY; }

    public record PeriodState(int progress, boolean done) {
        public PeriodState {
            if (progress < 0) throw new IllegalArgumentException("negative progress");
        }
    }

    public record AdvanceResult(int progress, boolean done,
                                boolean completedNow, int streak) {
        public AdvanceResult {
            if (progress < 0 || streak < 0) throw new IllegalArgumentException("negative quest state");
        }
    }
}
