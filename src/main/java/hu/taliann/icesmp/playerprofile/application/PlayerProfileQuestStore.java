package hu.taliann.icesmp.playerprofile.application;

import hu.taliann.icesmp.playerprofile.domain.ProfileSectionId;
import hu.taliann.icesmp.playerprofile.domain.section.QuestSection;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/** Typed active/completed quest, objective progress and reward settlement authority. */
public final class PlayerProfileQuestStore {

    private static final String DONE_AT_PREFIX = "done-at:";
    private static final String SEASON_PREFIX = "season:";
    private static final String RECEIPT_SEPARATOR = "|";
    private static final int MAX_REWARD_RECEIPTS = 2048;

    public record State(Set<String> active, Set<String> completed,
                        Map<String, Map<String, Long>> progress,
                        Set<String> claimableRewards,
                        Set<String> settledRewards) {
        public State {
            active = Set.copyOf(active);
            completed = Set.copyOf(completed);
            progress = Map.copyOf(progress);
            claimableRewards = Set.copyOf(claimableRewards);
            settledRewards = Set.copyOf(settledRewards);
        }
    }

    public record CompletionReceipt(boolean committed, String receiptId,
                                    String questId, long completedAt,
                                    long seasonId) { }

    public State read(final UUID playerId) {
        final QuestSection section = PlayerProfileAuthority.current().requireSection(
                Objects.requireNonNull(playerId, "playerId"),
                ProfileSectionId.QUESTS, QuestSection.class);
        return state(section);
    }

    public List<String> active(final UUID playerId) {
        return List.copyOf(read(playerId).active());
    }

    public List<String> completed(final UUID playerId) {
        return List.copyOf(read(playerId).completed());
    }

    public int progress(final UUID playerId, final String questId, final int objective) {
        final String id = questId(questId);
        if (objective < 0) throw new IllegalArgumentException("negative objective index");
        return Math.toIntExact(read(playerId).progress().getOrDefault(id, Map.of())
                .getOrDefault(objectiveKey(objective), 0L));
    }

    public long lastCompletedAt(final UUID playerId, final String questId) {
        return PlayerProfileAuthority.current().requireSection(playerId,
                ProfileSectionId.QUESTS, QuestSection.class).cooldowns()
                .getOrDefault(DONE_AT_PREFIX + questId(questId), 0L);
    }

    public long completedSeason(final UUID playerId, final String questId) {
        return PlayerProfileAuthority.current().requireSection(playerId,
                ProfileSectionId.QUESTS, QuestSection.class).cooldowns()
                .getOrDefault(SEASON_PREFIX + questId(questId), -1L);
    }

    public CompletionStage<Boolean> accept(final UUID playerId, final String questId) {
        final String id = questId(questId);
        return PlayerProfileAuthority.current().mutateSectionConditional(
                playerId, ProfileSectionId.QUESTS, QuestSection.class, current -> {
                    if (current.active().containsKey(id)) {
                        return PlayerProfileService.ConditionalMutation.unchanged(false);
                    }
                    final LinkedHashMap<String, Map<String, Long>> active =
                            new LinkedHashMap<>(current.active());
                    active.put(id, Map.of());
                    return PlayerProfileService.ConditionalMutation.changed(
                            copy(current, active, current.completed(), current.rewardReceipts(),
                                    current.cooldowns(), current.claimableRewards()), true);
                });
    }

    public CompletionStage<Boolean> abandon(final UUID playerId, final String questId) {
        final String id = questId(questId);
        return PlayerProfileAuthority.current().mutateSectionConditional(
                playerId, ProfileSectionId.QUESTS, QuestSection.class, current -> {
                    if (!current.active().containsKey(id)) {
                        return PlayerProfileService.ConditionalMutation.unchanged(false);
                    }
                    final LinkedHashMap<String, Map<String, Long>> active =
                            new LinkedHashMap<>(current.active());
                    active.remove(id);
                    return PlayerProfileService.ConditionalMutation.changed(
                            copy(current, active, current.completed(), current.rewardReceipts(),
                                    current.cooldowns(), current.claimableRewards()), true);
                });
    }

    public CompletionStage<Integer> setProgress(final UUID playerId, final String questId,
                                                final int objective, final int value) {
        final String id = questId(questId);
        if (objective < 0 || value < 0) throw new IllegalArgumentException("invalid progress");
        return PlayerProfileAuthority.current().mutateSectionConditional(
                playerId, ProfileSectionId.QUESTS, QuestSection.class, current -> {
                    final Map<String, Long> existing = current.active().get(id);
                    if (existing == null) {
                        return PlayerProfileService.ConditionalMutation.unchanged(0);
                    }
                    final String key = objectiveKey(objective);
                    final long previous = existing.getOrDefault(key, 0L);
                    if (previous == value) {
                        return PlayerProfileService.ConditionalMutation.unchanged(value);
                    }
                    final LinkedHashMap<String, Long> progress = new LinkedHashMap<>(existing);
                    if (value == 0) progress.remove(key); else progress.put(key, (long) value);
                    final LinkedHashMap<String, Map<String, Long>> active =
                            new LinkedHashMap<>(current.active());
                    active.put(id, Map.copyOf(progress));
                    return PlayerProfileService.ConditionalMutation.changed(
                            copy(current, active, current.completed(), current.rewardReceipts(),
                                    current.cooldowns(), current.claimableRewards()), value);
                });
    }

    public CompletionStage<Integer> incrementProgress(final UUID playerId, final String questId,
                                                      final int objective, final int amount,
                                                      final int maximum) {
        final String id = questId(questId);
        if (objective < 0 || amount <= 0 || maximum < 0) {
            throw new IllegalArgumentException("invalid progress increment");
        }
        return PlayerProfileAuthority.current().mutateSectionConditional(
                playerId, ProfileSectionId.QUESTS, QuestSection.class, current -> {
                    final Map<String, Long> existing = current.active().get(id);
                    if (existing == null) {
                        return PlayerProfileService.ConditionalMutation.unchanged(0);
                    }
                    final String key = objectiveKey(objective);
                    final long before = existing.getOrDefault(key, 0L);
                    final long after = Math.min((long) maximum, Math.addExact(before, amount));
                    if (before == after) {
                        return PlayerProfileService.ConditionalMutation.unchanged(
                                Math.toIntExact(after));
                    }
                    final LinkedHashMap<String, Long> progress = new LinkedHashMap<>(existing);
                    progress.put(key, after);
                    final LinkedHashMap<String, Map<String, Long>> active =
                            new LinkedHashMap<>(current.active());
                    active.put(id, Map.copyOf(progress));
                    return PlayerProfileService.ConditionalMutation.changed(
                            copy(current, active, current.completed(), current.rewardReceipts(),
                                    current.cooldowns(), current.claimableRewards()),
                            Math.toIntExact(after));
                });
    }

    /**
     * Completes only an active quest, removes its objective state, records cooldown/season and
     * creates a durable claimable reward receipt in the same section CAS.
     */
    public CompletionStage<CompletionReceipt> complete(final UUID playerId,
                                                       final String questId,
                                                       final long completedAt,
                                                       final long seasonId) {
        final String id = questId(questId);
        if (completedAt <= 0L || seasonId < 0L) {
            throw new IllegalArgumentException("invalid completion metadata");
        }
        return PlayerProfileAuthority.current().mutateSectionConditional(
                playerId, ProfileSectionId.QUESTS, QuestSection.class, current -> {
                    if (!current.active().containsKey(id)) {
                        return PlayerProfileService.ConditionalMutation.unchanged(
                                new CompletionReceipt(false, "", id, completedAt, seasonId));
                    }
                    final String receipt = receiptId(id, completedAt,
                            current.rewardReceipts(), current.claimableRewards());
                    final LinkedHashMap<String, Map<String, Long>> active =
                            new LinkedHashMap<>(current.active());
                    active.remove(id);
                    final LinkedHashSet<String> completed =
                            new LinkedHashSet<>(current.completed());
                    completed.add(id);
                    final LinkedHashMap<String, Long> cooldowns =
                            new LinkedHashMap<>(current.cooldowns());
                    cooldowns.put(DONE_AT_PREFIX + id, completedAt);
                    cooldowns.put(SEASON_PREFIX + id, seasonId);
                    final LinkedHashSet<String> claimable =
                            new LinkedHashSet<>(current.claimableRewards());
                    claimable.add(receipt);
                    final QuestSection next = copy(current, active, completed,
                            current.rewardReceipts(), cooldowns, claimable);
                    return PlayerProfileService.ConditionalMutation.changed(next,
                            new CompletionReceipt(true, receipt, id, completedAt, seasonId));
                });
    }

    public Set<String> pendingRewards(final UUID playerId) {
        return read(playerId).claimableRewards();
    }

    public String questFromReceipt(final String receipt) {
        if (receipt == null || receipt.isBlank()) throw new IllegalArgumentException("blank receipt");
        final int split = receipt.indexOf(RECEIPT_SEPARATOR);
        if (split <= 0) throw new IllegalArgumentException("invalid quest receipt");
        return questId(receipt.substring(0, split));
    }

    public CompletionStage<Boolean> settleReward(final UUID playerId, final String receipt) {
        Objects.requireNonNull(receipt, "receipt");
        return PlayerProfileAuthority.current().mutateSectionConditional(
                playerId, ProfileSectionId.QUESTS, QuestSection.class, current -> {
                    if (current.rewardReceipts().contains(receipt)) {
                        return PlayerProfileService.ConditionalMutation.unchanged(false);
                    }
                    if (!current.claimableRewards().contains(receipt)) {
                        throw new IllegalStateException("unknown claimable quest reward");
                    }
                    final LinkedHashSet<String> claimable =
                            new LinkedHashSet<>(current.claimableRewards());
                    claimable.remove(receipt);
                    final LinkedHashSet<String> settled =
                            new LinkedHashSet<>(current.rewardReceipts());
                    while (settled.size() >= MAX_REWARD_RECEIPTS) {
                        settled.remove(settled.iterator().next());
                    }
                    settled.add(receipt);
                    return PlayerProfileService.ConditionalMutation.changed(
                            copy(current, current.active(), current.completed(), settled,
                                    current.cooldowns(), claimable), true);
                });
    }

    private static State state(final QuestSection section) {
        return new State(section.active().keySet(), section.completed(), section.active(),
                section.claimableRewards(), section.rewardReceipts());
    }

    private static QuestSection copy(final QuestSection current,
                                     final Map<String, Map<String, Long>> active,
                                     final Set<String> completed,
                                     final Set<String> rewardReceipts,
                                     final Map<String, Long> cooldowns,
                                     final Set<String> claimable) {
        return new QuestSection(active, completed, rewardReceipts, cooldowns,
                current.communityContributions(), claimable, current.extensions());
    }

    private static String objectiveKey(final int index) {
        return "objective." + index;
    }

    private static String receiptId(final String questId, final long completedAt,
                                    final Set<String> settled, final Set<String> claimable) {
        int suffix = 0;
        String candidate;
        do {
            candidate = questId + RECEIPT_SEPARATOR + completedAt
                    + (suffix == 0 ? "" : "-" + suffix);
            suffix++;
        } while (settled.contains(candidate) || claimable.contains(candidate));
        return candidate;
    }

    private static String questId(final String raw) {
        if (raw == null || raw.isBlank()) throw new IllegalArgumentException("quest id required");
        final String id = raw.trim().toLowerCase(Locale.ROOT);
        if (!id.matches("[a-z0-9][a-z0-9._-]{0,95}")) {
            throw new IllegalArgumentException("invalid quest id: " + raw);
        }
        return id;
    }
}
