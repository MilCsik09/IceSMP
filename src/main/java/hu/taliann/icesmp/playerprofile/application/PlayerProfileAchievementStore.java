package hu.taliann.icesmp.playerprofile.application;

import hu.taliann.icesmp.playerprofile.domain.ProfileSectionId;
import hu.taliann.icesmp.playerprofile.domain.section.AchievementSection;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
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
    private static final String PENDING_REWARDS_KEY = "achievement_reward_pending";
    private static final int MAX_PENDING_REWARDS = 512;

    public enum RewardKind { NONE, CLASS_XP, CURRENCY }
    public enum RewardState { PENDING, SETTLED }

    /** Payload is part of the durable reservation identity and therefore survives config/faction changes. */
    public record PendingReward(String receiptId, RewardKind kind, long amount, String currencyId) {
        public PendingReward {
            receiptId = id(receiptId);
            Objects.requireNonNull(kind, "kind");
            if (amount < 0L) throw new IllegalArgumentException("negative achievement reward");
            currencyId = currencyId == null ? "" : currencyId.trim().toLowerCase(Locale.ROOT);
            if (kind == RewardKind.CURRENCY && currencyId.isBlank()) {
                throw new IllegalArgumentException("currency reward requires currency id");
            }
            if (kind != RewardKind.CURRENCY && !currencyId.isBlank()) {
                throw new IllegalArgumentException("non-currency reward cannot carry currency id");
            }
        }
    }

    public record RewardReservation(RewardState state, PendingReward reward, boolean created) {
        public RewardReservation {
            Objects.requireNonNull(state, "state");
            Objects.requireNonNull(reward, "reward");
        }
        public boolean pending() { return state == RewardState.PENDING; }
    }

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
     * Creates/replays one durable pending reward. Reservation is not delivery: the receipt moves
     * to claimedRewards only after the idempotent external mutation is proven complete.
     */
    public CompletionStage<RewardReservation> reserveReward(final UUID playerId,
                                                             final PendingReward requested) {
        Objects.requireNonNull(requested, "requested");
        return PlayerProfileAuthority.current().mutateSectionConditional(
                playerId, ProfileSectionId.ACHIEVEMENTS, AchievementSection.class, current -> {
                    if (current.claimedRewards().contains(requested.receiptId())) {
                        return PlayerProfileService.ConditionalMutation.unchanged(
                                new RewardReservation(RewardState.SETTLED, requested, false));
                    }
                    final LinkedHashMap<String, PendingReward> pending = pending(current);
                    final PendingReward existing = pending.get(requested.receiptId());
                    if (existing != null) {
                        requireSameReward(existing, requested);
                        return PlayerProfileService.ConditionalMutation.unchanged(
                                new RewardReservation(RewardState.PENDING, existing, false));
                    }
                    if (pending.size() >= MAX_PENDING_REWARDS) {
                        throw new IllegalStateException("achievement pending reward ledger is full");
                    }
                    pending.put(requested.receiptId(), requested);
                    final AchievementSection next = withPending(current, pending,
                            current.claimedRewards());
                    return PlayerProfileService.ConditionalMutation.changed(next,
                            new RewardReservation(RewardState.PENDING, requested, true));
                });
    }

    /**
     * Atomically settles one pending reward after delivery. Replays are idempotent; payload reuse
     * with different parameters is an explicit identity conflict.
     */
    public CompletionStage<Boolean> settleReward(final UUID playerId,
                                                  final PendingReward expected) {
        Objects.requireNonNull(expected, "expected");
        return PlayerProfileAuthority.current().mutateSectionConditional(
                playerId, ProfileSectionId.ACHIEVEMENTS, AchievementSection.class, current -> {
                    if (current.claimedRewards().contains(expected.receiptId())) {
                        return PlayerProfileService.ConditionalMutation.unchanged(false);
                    }
                    final LinkedHashMap<String, PendingReward> pending = pending(current);
                    final PendingReward existing = pending.get(expected.receiptId());
                    if (existing == null) {
                        throw new IllegalStateException("achievement reward has no pending reservation");
                    }
                    requireSameReward(existing, expected);
                    pending.remove(expected.receiptId());
                    final LinkedHashSet<String> claimed = new LinkedHashSet<>(current.claimedRewards());
                    claimed.add(expected.receiptId());
                    final AchievementSection next = withPending(current, pending, claimed);
                    return PlayerProfileService.ConditionalMutation.changed(next, true);
                });
    }

    public Optional<PendingReward> pendingReward(final UUID playerId, final String rawReceipt) {
        return Optional.ofNullable(pending(section(playerId)).get(id(rawReceipt)));
    }

    public List<PendingReward> pendingRewards(final UUID playerId) {
        final ArrayList<PendingReward> rewards = new ArrayList<>(pending(section(playerId)).values());
        rewards.sort(Comparator.comparing(PendingReward::receiptId));
        return List.copyOf(rewards);
    }

    /** Compatibility diagnostic: true for either pending or settled durable receipt. */
    public boolean rewardReserved(final UUID playerId, final String rawReceipt) {
        final String receipt = id(rawReceipt);
        final AchievementSection current = section(playerId);
        return current.claimedRewards().contains(receipt) || pending(current).containsKey(receipt);
    }

    public boolean rewardSettled(final UUID playerId, final String rawReceipt) {
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

    private static LinkedHashMap<String, PendingReward> pending(final AchievementSection section) {
        final LinkedHashMap<String, PendingReward> result = new LinkedHashMap<>();
        final Object raw = section.extensions().get(PENDING_REWARDS_KEY);
        if (raw == null) return result;
        if (!(raw instanceof Map<?, ?> map)) {
            throw new IllegalStateException("invalid achievement pending reward ledger");
        }
        for (final Map.Entry<?, ?> entry : map.entrySet()) {
            final String receipt = id(String.valueOf(entry.getKey()));
            if (!(entry.getValue() instanceof String encoded)) {
                throw new IllegalStateException("invalid achievement pending reward payload");
            }
            final PendingReward decoded = decodeReward(receipt, encoded);
            if (result.putIfAbsent(receipt, decoded) != null) {
                throw new IllegalStateException("duplicate achievement pending reward");
            }
        }
        if (result.size() > MAX_PENDING_REWARDS) {
            throw new IllegalStateException("achievement pending reward ledger exceeds limit");
        }
        return result;
    }

    private static AchievementSection withPending(final AchievementSection current,
                                                   final Map<String, PendingReward> pending,
                                                   final Set<String> claimed) {
        final LinkedHashMap<String, Object> extensions = new LinkedHashMap<>(current.extensions());
        if (pending.isEmpty()) {
            extensions.remove(PENDING_REWARDS_KEY);
        } else {
            final LinkedHashMap<String, String> encoded = new LinkedHashMap<>();
            pending.forEach((receipt, reward) -> encoded.put(receipt, encodeReward(reward)));
            extensions.put(PENDING_REWARDS_KEY, encoded);
        }
        return new AchievementSection(current.unlocked(), current.publicAchievements(), claimed,
                current.bestiary(), extensions);
    }

    private static String encodeReward(final PendingReward reward) {
        return reward.kind().name() + '|' + reward.amount() + '|' + reward.currencyId();
    }

    private static PendingReward decodeReward(final String receipt, final String encoded) {
        final String[] parts = encoded.split("\\|", -1);
        if (parts.length != 3) throw new IllegalStateException("invalid pending achievement reward");
        try {
            return new PendingReward(receipt, RewardKind.valueOf(parts[0]),
                    Long.parseLong(parts[1]), parts[2]);
        } catch (final RuntimeException malformed) {
            throw new IllegalStateException("invalid pending achievement reward", malformed);
        }
    }

    private static void requireSameReward(final PendingReward existing,
                                          final PendingReward requested) {
        if (!existing.equals(requested)) {
            throw new IllegalStateException(
                    "achievement reward receipt reused with different parameters");
        }
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
