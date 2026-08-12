package hu.taliann.icesmp.playerprofile.application;

import hu.taliann.icesmp.playerprofile.domain.ProfileSectionId;
import hu.taliann.icesmp.playerprofile.domain.section.QuestSection;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** Typed active/completed quest, objective progress and reward settlement authority. */
public final class PlayerProfileQuestStore {

    private static final String DONE_AT_PREFIX = "done-at:";
    private static final String SEASON_PREFIX = "season:";
    private static final String RECEIPT_SEPARATOR = "|";
    private static final String DISCOVERED_PREFIX = "discovered.";
    private static final String SOURCE_PREFIX = "source.";
    private static final String ACCEPTED_AT_PREFIX = "accepted-at.";
    private static final String QUEST_METADATA_EXTENSION = "quest-metadata";
    private static final String SOURCE_FIELD = "source";
    private static final String ACCEPTED_AT_FIELD = "accepted-at";
    private static final String DISCOVERED_FIELD = "discovered";
    private static final String TRACKED_KEY = "tracked";
    private static final String REWARD_DELIVERY_EXTENSION = "reward-delivery-ledger";
    private static final int MAX_REWARD_RECEIPTS = 2048;
    private static final int MAX_DELIVERY_COMPONENTS = 512;

    public enum RewardComponentState {
        PREPARED,
        DELIVERED
    }

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
        return accept(playerId, questId, "");
    }

    /** A felvétel a start-forrás auditjával EGY commitban rögzül. */
    public CompletionStage<Boolean> accept(final UUID playerId, final String questId,
                                           final String startSource) {
        final String id = questId(questId);
        final String source = startSource == null ? "" : startSource.trim().toLowerCase(Locale.ROOT);
        return PlayerProfileAuthority.current().mutateSectionConditional(
                playerId, ProfileSectionId.QUESTS, QuestSection.class, current -> {
                    if (current.active().containsKey(id)) {
                        return PlayerProfileService.ConditionalMutation.unchanged(false);
                    }
                    final LinkedHashMap<String, Map<String, Long>> active =
                            new LinkedHashMap<>(current.active());
                    active.put(id, Map.of());
                    final LinkedHashMap<String, Object> extensions =
                            migratedExtensions(current.extensions());
                    final LinkedHashMap<String, Map<String, Object>> metadata =
                            mutableMetadata(extensions);
                    final LinkedHashMap<String, Object> quest =
                            new LinkedHashMap<>(metadata.getOrDefault(id, Map.of()));
                    if (!source.isBlank()) {
                        quest.put(SOURCE_FIELD, source);
                    }
                    quest.put(ACCEPTED_AT_FIELD, System.currentTimeMillis());
                    metadata.put(id, Map.copyOf(quest));
                    putMetadata(extensions, metadata);
                    return PlayerProfileService.ConditionalMutation.changed(
                            copyWithExtensions(current, active, current.completed(),
                                    current.rewardReceipts(), current.cooldowns(),
                                    current.claimableRewards(), extensions), true);
                });
    }

    /** Tartós quest-felfedezés; {@code true}, ha ez volt az első felfedezés. */
    public CompletionStage<Boolean> discover(final UUID playerId, final String questId,
                                             final String source) {
        final String id = questId(questId);
        final String from = source == null ? "" : source.trim().toLowerCase(Locale.ROOT);
        return PlayerProfileAuthority.current().mutateSectionConditional(
                playerId, ProfileSectionId.QUESTS, QuestSection.class, current -> {
                    if (metadata(current.extensions()).getOrDefault(id, Map.of())
                            .containsKey(DISCOVERED_FIELD)) {
                        return PlayerProfileService.ConditionalMutation.unchanged(false);
                    }
                    final LinkedHashMap<String, Object> extensions =
                            migratedExtensions(current.extensions());
                    final LinkedHashMap<String, Map<String, Object>> metadata =
                            mutableMetadata(extensions);
                    final LinkedHashMap<String, Object> quest =
                            new LinkedHashMap<>(metadata.getOrDefault(id, Map.of()));
                    quest.put(DISCOVERED_FIELD, from.isBlank() ? "unknown" : from);
                    metadata.put(id, Map.copyOf(quest));
                    putMetadata(extensions, metadata);
                    return PlayerProfileService.ConditionalMutation.changed(
                            copyWithExtensions(current, current.active(), current.completed(),
                                    current.rewardReceipts(), current.cooldowns(),
                                    current.claimableRewards(), extensions), true);
                });
    }

    public boolean isDiscovered(final UUID playerId, final String questId) {
        final QuestSection section = PlayerProfileAuthority.current().requireSection(playerId,
                ProfileSectionId.QUESTS, QuestSection.class);
        return metadata(section.extensions()).getOrDefault(questId(questId), Map.of())
                .containsKey(DISCOVERED_FIELD);
    }

    /** Trackelt quest kijelölése ({@code null} = törlés); HUD/waypoint rendszerek alapja. */
    public CompletionStage<Boolean> setTracked(final UUID playerId, final String questId) {
        final String id = questId == null || questId.isBlank() ? "" : questId(questId);
        return PlayerProfileAuthority.current().mutateSectionConditional(
                playerId, ProfileSectionId.QUESTS, QuestSection.class, current -> {
                    final Object existing = current.extensions().get(TRACKED_KEY);
                    if (id.isEmpty() ? existing == null : id.equals(existing)) {
                        return PlayerProfileService.ConditionalMutation.unchanged(false);
                    }
                    final LinkedHashMap<String, Object> extensions =
                            migratedExtensions(current.extensions());
                    if (id.isEmpty()) {
                        extensions.remove(TRACKED_KEY);
                    } else {
                        extensions.put(TRACKED_KEY, id);
                    }
                    return PlayerProfileService.ConditionalMutation.changed(
                            copyWithExtensions(current, current.active(), current.completed(),
                                    current.rewardReceipts(), current.cooldowns(),
                                    current.claimableRewards(), extensions), true);
                });
    }

    public String tracked(final UUID playerId) {
        final Object value = PlayerProfileAuthority.current().requireSection(playerId,
                ProfileSectionId.QUESTS, QuestSection.class).extensions().get(TRACKED_KEY);
        return value instanceof String id && !id.isBlank() ? id : null;
    }

    /** A rögzített start-forrás auditja (diagnosztika); {@code null}, ha nincs. */
    public String startSource(final UUID playerId, final String questId) {
        final QuestSection section = PlayerProfileAuthority.current().requireSection(playerId,
                ProfileSectionId.QUESTS, QuestSection.class);
        final Object value = metadata(section.extensions())
                .getOrDefault(questId(questId), Map.of()).get(SOURCE_FIELD);
        return value instanceof String source && !source.isBlank() ? source : null;
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
                    final LinkedHashMap<String, Object> extensions =
                            withoutActiveMetadata(current.extensions(), id);
                    return PlayerProfileService.ConditionalMutation.changed(
                            copyWithExtensions(current, active, current.completed(),
                                    current.rewardReceipts(), current.cooldowns(),
                                    current.claimableRewards(), extensions), true);
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
                    final QuestSection next = copyWithExtensions(current, active, completed,
                            current.rewardReceipts(), cooldowns, claimable,
                            withoutActiveMetadata(current.extensions(), id));
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

    /** Durable phase 1 for physical rewards. Replays never downgrade DELIVERED components. */
    public CompletionStage<Boolean> prepareRewardComponents(final UUID playerId,
                                                            final String receipt,
                                                            final Set<String> componentIds) {
        Objects.requireNonNull(playerId, "playerId");
        final String rewardReceipt = rewardReceipt(receipt);
        final LinkedHashSet<String> components = normalizedComponents(componentIds);
        if (components.isEmpty()) return CompletableFuture.completedFuture(false);
        return PlayerProfileAuthority.current().mutateSectionConditional(
                playerId, ProfileSectionId.QUESTS, QuestSection.class, current -> {
                    if (current.rewardReceipts().contains(rewardReceipt)) {
                        return PlayerProfileService.ConditionalMutation.unchanged(false);
                    }
                    if (!current.claimableRewards().contains(rewardReceipt)) {
                        throw new IllegalStateException("unknown claimable quest reward");
                    }
                    final LinkedHashMap<String, Map<String, RewardComponentState>> ledger =
                            mutableDeliveryLedger(current);
                    final LinkedHashMap<String, RewardComponentState> receiptState =
                            new LinkedHashMap<>(ledger.getOrDefault(rewardReceipt, Map.of()));
                    boolean changed = false;
                    for (final String component : components) {
                        if (!receiptState.containsKey(component)) {
                            receiptState.put(component, RewardComponentState.PREPARED);
                            changed = true;
                        }
                    }
                    if (!changed) {
                        return PlayerProfileService.ConditionalMutation.unchanged(false);
                    }
                    if (receiptState.size() > MAX_DELIVERY_COMPONENTS) {
                        throw new IllegalStateException("quest reward component limit exceeded");
                    }
                    ledger.put(rewardReceipt, Map.copyOf(receiptState));
                    return PlayerProfileService.ConditionalMutation.changed(
                            copyWithDeliveryLedger(current, ledger), true);
                });
    }

    public Map<String, RewardComponentState> rewardComponentStates(final UUID playerId,
                                                                   final String receipt) {
        final String rewardReceipt = rewardReceipt(receipt);
        final QuestSection section = PlayerProfileAuthority.current().requireSection(
                Objects.requireNonNull(playerId, "playerId"),
                ProfileSectionId.QUESTS, QuestSection.class);
        return deliveryLedger(section).getOrDefault(rewardReceipt, Map.of());
    }

    /** Durable phase 2 after the physical item is present or recovered as a stamped witness. */
    public CompletionStage<Boolean> markRewardComponentsDelivered(final UUID playerId,
                                                                  final String receipt,
                                                                  final Set<String> componentIds) {
        Objects.requireNonNull(playerId, "playerId");
        final String rewardReceipt = rewardReceipt(receipt);
        final LinkedHashSet<String> components = normalizedComponents(componentIds);
        if (components.isEmpty()) return CompletableFuture.completedFuture(false);
        return PlayerProfileAuthority.current().mutateSectionConditional(
                playerId, ProfileSectionId.QUESTS, QuestSection.class, current -> {
                    if (current.rewardReceipts().contains(rewardReceipt)) {
                        return PlayerProfileService.ConditionalMutation.unchanged(false);
                    }
                    if (!current.claimableRewards().contains(rewardReceipt)) {
                        throw new IllegalStateException("unknown claimable quest reward");
                    }
                    final LinkedHashMap<String, Map<String, RewardComponentState>> ledger =
                            mutableDeliveryLedger(current);
                    final Map<String, RewardComponentState> existing = ledger.get(rewardReceipt);
                    if (existing == null) {
                        throw new IllegalStateException("quest reward components were not prepared");
                    }
                    final LinkedHashMap<String, RewardComponentState> receiptState =
                            new LinkedHashMap<>(existing);
                    boolean changed = false;
                    for (final String component : components) {
                        final RewardComponentState state = receiptState.get(component);
                        if (state == null) {
                            throw new IllegalStateException(
                                    "quest reward component was not prepared: " + component);
                        }
                        if (state != RewardComponentState.DELIVERED) {
                            receiptState.put(component, RewardComponentState.DELIVERED);
                            changed = true;
                        }
                    }
                    if (!changed) {
                        return PlayerProfileService.ConditionalMutation.unchanged(false);
                    }
                    ledger.put(rewardReceipt, Map.copyOf(receiptState));
                    return PlayerProfileService.ConditionalMutation.changed(
                            copyWithDeliveryLedger(current, ledger), true);
                });
    }

    /** Parent receipt may settle only after every prepared physical component is durable DELIVERED. */
    public CompletionStage<Boolean> settleReward(final UUID playerId, final String receipt) {
        final String rewardReceipt = rewardReceipt(receipt);
        return PlayerProfileAuthority.current().mutateSectionConditional(
                playerId, ProfileSectionId.QUESTS, QuestSection.class, current -> {
                    if (current.rewardReceipts().contains(rewardReceipt)) {
                        return PlayerProfileService.ConditionalMutation.unchanged(false);
                    }
                    if (!current.claimableRewards().contains(rewardReceipt)) {
                        throw new IllegalStateException("unknown claimable quest reward");
                    }
                    final LinkedHashMap<String, Map<String, RewardComponentState>> ledger =
                            mutableDeliveryLedger(current);
                    final Map<String, RewardComponentState> componentStates = ledger.get(rewardReceipt);
                    if (componentStates != null && componentStates.values().stream()
                            .anyMatch(state -> state != RewardComponentState.DELIVERED)) {
                        throw new IllegalStateException(
                                "quest physical reward components are not fully delivered");
                    }
                    final LinkedHashSet<String> claimable =
                            new LinkedHashSet<>(current.claimableRewards());
                    claimable.remove(rewardReceipt);
                    final LinkedHashSet<String> settled =
                            new LinkedHashSet<>(current.rewardReceipts());
                    while (settled.size() >= MAX_REWARD_RECEIPTS) {
                        settled.remove(settled.iterator().next());
                    }
                    settled.add(rewardReceipt);
                    ledger.remove(rewardReceipt);
                    return PlayerProfileService.ConditionalMutation.changed(
                            copyWithDeliveryLedger(current, current.active(), current.completed(),
                                    settled, current.cooldowns(), claimable, ledger), true);
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
        return copyWithExtensions(current, active, completed, rewardReceipts, cooldowns,
                claimable, current.extensions());
    }

    private static QuestSection copyWithExtensions(final QuestSection current,
                                                   final Map<String, Map<String, Long>> active,
                                                   final Set<String> completed,
                                                   final Set<String> rewardReceipts,
                                                   final Map<String, Long> cooldowns,
                                                   final Set<String> claimable,
                                                   final Map<String, Object> extensions) {
        return new QuestSection(active, completed, rewardReceipts, cooldowns,
                current.communityContributions(), claimable, migratedExtensions(extensions));
    }

    private static LinkedHashMap<String, Object> withoutActiveMetadata(
            final Map<String, Object> source, final String questId) {
        final LinkedHashMap<String, Object> extensions = migratedExtensions(source);
        final LinkedHashMap<String, Map<String, Object>> metadata = mutableMetadata(extensions);
        final LinkedHashMap<String, Object> quest =
                new LinkedHashMap<>(metadata.getOrDefault(questId, Map.of()));
        quest.remove(SOURCE_FIELD);
        quest.remove(ACCEPTED_AT_FIELD);
        if (quest.isEmpty()) metadata.remove(questId);
        else metadata.put(questId, Map.copyOf(quest));
        putMetadata(extensions, metadata);
        return extensions;
    }

    private static LinkedHashMap<String, Object> migratedExtensions(
            final Map<String, Object> source) {
        final LinkedHashMap<String, Object> extensions = new LinkedHashMap<>(source);
        final LinkedHashMap<String, Map<String, Object>> metadata = mutableMetadata(extensions);
        for (final Map.Entry<String, Object> entry : source.entrySet()) {
            final LegacyField legacy = legacyField(entry.getKey());
            if (legacy == null) continue;
            final LinkedHashMap<String, Object> quest = new LinkedHashMap<>(
                    metadata.getOrDefault(legacy.questId(), Map.of()));
            quest.putIfAbsent(legacy.field(), entry.getValue());
            metadata.put(legacy.questId(), Map.copyOf(quest));
            extensions.remove(entry.getKey());
        }
        putMetadata(extensions, metadata);
        return extensions;
    }

    private static Map<String, Map<String, Object>> metadata(
            final Map<String, Object> extensions) {
        return Map.copyOf(mutableMetadata(migratedExtensions(extensions)));
    }

    private static LinkedHashMap<String, Map<String, Object>> mutableMetadata(
            final Map<String, Object> extensions) {
        final Object raw = extensions.get(QUEST_METADATA_EXTENSION);
        final LinkedHashMap<String, Map<String, Object>> result = new LinkedHashMap<>();
        if (raw == null) return result;
        if (!(raw instanceof Map<?, ?> quests)) {
            throw new IllegalStateException("invalid quest metadata extension");
        }
        for (final Map.Entry<?, ?> entry : quests.entrySet()) {
            final String id = questId(String.valueOf(entry.getKey()));
            if (!(entry.getValue() instanceof Map<?, ?> fields)) {
                throw new IllegalStateException("invalid quest metadata entry: " + id);
            }
            final LinkedHashMap<String, Object> decoded = new LinkedHashMap<>();
            fields.forEach((key, value) -> decoded.put(String.valueOf(key), value));
            result.put(id, Map.copyOf(decoded));
        }
        return result;
    }

    private static void putMetadata(final LinkedHashMap<String, Object> extensions,
                                    final Map<String, Map<String, Object>> metadata) {
        if (metadata.isEmpty()) extensions.remove(QUEST_METADATA_EXTENSION);
        else extensions.put(QUEST_METADATA_EXTENSION, Map.copyOf(metadata));
    }

    private static LegacyField legacyField(final String key) {
        if (key.startsWith(SOURCE_PREFIX)) {
            return new LegacyField(questId(key.substring(SOURCE_PREFIX.length())), SOURCE_FIELD);
        }
        if (key.startsWith(ACCEPTED_AT_PREFIX)) {
            return new LegacyField(questId(key.substring(ACCEPTED_AT_PREFIX.length())),
                    ACCEPTED_AT_FIELD);
        }
        if (key.startsWith(DISCOVERED_PREFIX)) {
            return new LegacyField(questId(key.substring(DISCOVERED_PREFIX.length())),
                    DISCOVERED_FIELD);
        }
        return null;
    }

    private record LegacyField(String questId, String field) { }

    private static QuestSection copyWithDeliveryLedger(
            final QuestSection current,
            final Map<String, Map<String, RewardComponentState>> ledger) {
        return copyWithDeliveryLedger(current, current.active(), current.completed(),
                current.rewardReceipts(), current.cooldowns(), current.claimableRewards(), ledger);
    }

    private static QuestSection copyWithDeliveryLedger(
            final QuestSection current,
            final Map<String, Map<String, Long>> active,
            final Set<String> completed,
            final Set<String> rewardReceipts,
            final Map<String, Long> cooldowns,
            final Set<String> claimable,
            final Map<String, Map<String, RewardComponentState>> ledger) {
        final LinkedHashMap<String, Object> extensions = new LinkedHashMap<>(current.extensions());
        if (ledger.isEmpty()) {
            extensions.remove(REWARD_DELIVERY_EXTENSION);
        } else {
            final LinkedHashMap<String, Object> encodedLedger = new LinkedHashMap<>();
            ledger.forEach((rewardReceipt, components) -> {
                final LinkedHashMap<String, Object> encodedComponents = new LinkedHashMap<>();
                components.forEach((component, state) -> encodedComponents.put(
                        component, state.name().toLowerCase(Locale.ROOT)));
                encodedLedger.put(rewardReceipt, Map.copyOf(encodedComponents));
            });
            extensions.put(REWARD_DELIVERY_EXTENSION, Map.copyOf(encodedLedger));
        }
        return copyWithExtensions(current, active, completed, rewardReceipts,
                cooldowns, claimable, Map.copyOf(extensions));
    }

    private static LinkedHashMap<String, Map<String, RewardComponentState>> mutableDeliveryLedger(
            final QuestSection section) {
        return new LinkedHashMap<>(deliveryLedger(section));
    }

    private static Map<String, Map<String, RewardComponentState>> deliveryLedger(
            final QuestSection section) {
        final Object raw = section.extensions().get(REWARD_DELIVERY_EXTENSION);
        if (raw == null) return Map.of();
        if (!(raw instanceof Map<?, ?> receipts)) {
            throw new IllegalStateException("invalid quest reward delivery ledger");
        }
        final LinkedHashMap<String, Map<String, RewardComponentState>> decoded = new LinkedHashMap<>();
        for (final Map.Entry<?, ?> receiptEntry : receipts.entrySet()) {
            final String receipt = rewardReceipt(String.valueOf(receiptEntry.getKey()));
            if (!(receiptEntry.getValue() instanceof Map<?, ?> rawComponents)) {
                throw new IllegalStateException("invalid quest reward component ledger");
            }
            if (rawComponents.size() > MAX_DELIVERY_COMPONENTS) {
                throw new IllegalStateException("quest reward component limit exceeded");
            }
            final LinkedHashMap<String, RewardComponentState> components = new LinkedHashMap<>();
            for (final Map.Entry<?, ?> componentEntry : rawComponents.entrySet()) {
                final String component = rewardComponentId(String.valueOf(componentEntry.getKey()));
                final RewardComponentState state;
                try {
                    state = RewardComponentState.valueOf(
                            String.valueOf(componentEntry.getValue()).trim().toUpperCase(Locale.ROOT));
                } catch (final IllegalArgumentException invalid) {
                    throw new IllegalStateException(
                            "invalid quest reward component state: " + componentEntry.getValue(), invalid);
                }
                components.put(component, state);
            }
            decoded.put(receipt, Map.copyOf(components));
        }
        return Map.copyOf(decoded);
    }

    private static LinkedHashSet<String> normalizedComponents(final Set<String> componentIds) {
        Objects.requireNonNull(componentIds, "componentIds");
        if (componentIds.size() > MAX_DELIVERY_COMPONENTS) {
            throw new IllegalArgumentException("quest reward component limit exceeded");
        }
        final LinkedHashSet<String> result = new LinkedHashSet<>();
        for (final String component : componentIds) result.add(rewardComponentId(component));
        return result;
    }

    private static String rewardComponentId(final String raw) {
        if (raw == null || raw.isBlank()) throw new IllegalArgumentException("reward component id required");
        final String id = raw.trim().toLowerCase(Locale.ROOT);
        if (!id.matches("[a-z0-9][a-z0-9._:-]{0,127}")) {
            throw new IllegalArgumentException("invalid reward component id: " + raw);
        }
        return id;
    }

    private static String rewardReceipt(final String raw) {
        if (raw == null || raw.isBlank() || raw.length() > 128) {
            throw new IllegalArgumentException("invalid quest reward receipt");
        }
        return raw.trim();
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
