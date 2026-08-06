package hu.taliann.icesmp.playerprofile.application;

import hu.taliann.icesmp.crates.CrateLedger;
import hu.taliann.icesmp.playerprofile.domain.ProfileSectionId;
import hu.taliann.icesmp.playerprofile.domain.section.StatisticsSection;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/**
 * CAS-backed per-player crate opening counts, cooldowns and settlement receipts.
 *
 * <p>The settlement commits here before the shared crates file drops its recovery fence, so the
 * opening-id receipt is what keeps a crash between the two writes exact-once: a replayed
 * settlement or an orphaned fence resolves against the receipt instead of double-counting.</p>
 */
public final class PlayerProfileCrateStore {
    private static final String NAME_KEY = "crate.name";
    private static final String COUNT_PREFIX = "crate.counts.";
    private static final String COOLDOWN_PREFIX = "crate.cooldown.";
    private static final String OPS_KEY = "crate.ops";
    private static final int MAX_OPS = 64;

    public enum ApplyStatus { APPLIED, ALREADY_APPLIED, STALE }

    /** Applies a prepared ledger mutation exactly once, keyed by the opening id. */
    public CompletionStage<ApplyStatus> applyMutation(final UUID playerId,
                                                      final CrateLedger.Mutation mutation,
                                                      final UUID openingId) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(mutation, "mutation");
        Objects.requireNonNull(openingId, "openingId");
        if (!playerId.equals(mutation.playerId())) {
            throw new IllegalArgumentException("crate mutation belongs to another player");
        }
        return PlayerProfileAuthority.current().mutateSectionConditional(
                playerId, ProfileSectionId.STATISTICS, StatisticsSection.class, current -> {
                    final PlayerCrateState state = read(current);
                    if (state.recentOps().contains(openingId.toString())) {
                        return PlayerProfileService.ConditionalMutation.unchanged(
                                ApplyStatus.ALREADY_APPLIED);
                    }
                    if (state.counts().getOrDefault(mutation.crateId(), 0L) != mutation.previousCount()
                            || state.cooldowns().getOrDefault(mutation.crateId(), 0L)
                                    != mutation.previousCooldown()
                            || !Objects.equals(state.lastKnownName(), mutation.previousName())) {
                        return PlayerProfileService.ConditionalMutation.unchanged(ApplyStatus.STALE);
                    }
                    final Map<String, Object> extensions = new LinkedHashMap<>(current.extensions());
                    extensions.put(COUNT_PREFIX + mutation.crateId(), mutation.newCount());
                    if (mutation.newCooldown() == 0L) {
                        extensions.remove(COOLDOWN_PREFIX + mutation.crateId());
                    } else {
                        extensions.put(COOLDOWN_PREFIX + mutation.crateId(), mutation.newCooldown());
                    }
                    if (mutation.newName() == null) {
                        extensions.remove(NAME_KEY);
                    } else {
                        extensions.put(NAME_KEY, mutation.newName());
                    }
                    final List<String> ops = new ArrayList<>(state.recentOps());
                    ops.add(openingId.toString());
                    while (ops.size() > MAX_OPS) {
                        ops.remove(0);
                    }
                    extensions.put(OPS_KEY, List.copyOf(ops));
                    return PlayerProfileService.ConditionalMutation.changed(
                            withExtensions(current, extensions), ApplyStatus.APPLIED);
                });
    }

    /** Removes one crate's count+cooldown, or every crate entry and the name when crateId is null. */
    public CompletionStage<Boolean> reset(final UUID playerId, final String crateId) {
        Objects.requireNonNull(playerId, "playerId");
        return PlayerProfileAuthority.current().mutateSectionConditional(
                playerId, ProfileSectionId.STATISTICS, StatisticsSection.class, current -> {
                    final Map<String, Object> extensions = new LinkedHashMap<>(current.extensions());
                    boolean changed;
                    if (crateId == null) {
                        changed = extensions.keySet().removeIf(key -> key.equals(NAME_KEY)
                                || key.startsWith(COUNT_PREFIX) || key.startsWith(COOLDOWN_PREFIX));
                    } else {
                        changed = extensions.remove(COUNT_PREFIX + crateId) != null;
                        changed |= extensions.remove(COOLDOWN_PREFIX + crateId) != null;
                    }
                    if (!changed) {
                        return PlayerProfileService.ConditionalMutation.unchanged(false);
                    }
                    return PlayerProfileService.ConditionalMutation.changed(
                            withExtensions(current, extensions), true);
                });
    }

    /** Decodes the durable crate state of one player's statistics section. */
    public static PlayerCrateState read(final StatisticsSection section) {
        String name = null;
        final Map<String, Long> counts = new LinkedHashMap<>();
        final Map<String, Long> cooldowns = new LinkedHashMap<>();
        List<String> ops = List.of();
        for (final Map.Entry<String, Object> entry : section.extensions().entrySet()) {
            final String key = entry.getKey();
            if (key.equals(NAME_KEY)) {
                name = stringValue(entry.getValue());
            } else if (key.startsWith(COUNT_PREFIX)) {
                counts.put(key.substring(COUNT_PREFIX.length()), longValue(entry.getValue()));
            } else if (key.startsWith(COOLDOWN_PREFIX)) {
                cooldowns.put(key.substring(COOLDOWN_PREFIX.length()), longValue(entry.getValue()));
            } else if (key.equals(OPS_KEY)) {
                ops = stringList(entry.getValue());
            }
        }
        return new PlayerCrateState(name, counts, cooldowns, ops);
    }

    private static StatisticsSection withExtensions(final StatisticsSection base,
                                                    final Map<String, Object> extensions) {
        return new StatisticsSection(base.lifetime(), base.season(),
                base.claimedMilestones(), extensions);
    }

    private static long longValue(final Object raw) {
        if (raw instanceof Number number) return number.longValue();
        throw new IllegalStateException("Invalid crate numeric extension: " + raw);
    }

    private static String stringValue(final Object raw) {
        if (raw instanceof String value) return value;
        throw new IllegalStateException("Invalid crate name extension: " + raw);
    }

    private static List<String> stringList(final Object raw) {
        if (!(raw instanceof List<?> list)) {
            throw new IllegalStateException("Invalid crate receipt list: " + raw);
        }
        final List<String> ops = new ArrayList<>(list.size());
        for (final Object value : list) {
            ops.add(stringValue(value));
        }
        return List.copyOf(ops);
    }

    public record PlayerCrateState(String lastKnownName, Map<String, Long> counts,
                                   Map<String, Long> cooldowns, List<String> recentOps) {
        public PlayerCrateState {
            counts = Map.copyOf(counts);
            cooldowns = Map.copyOf(cooldowns);
            recentOps = List.copyOf(recentOps);
        }

        public boolean isEmpty() {
            return lastKnownName == null && counts.isEmpty() && cooldowns.isEmpty();
        }

        public CrateLedger.PlayerSnapshot toLedgerSnapshot() {
            return new CrateLedger.PlayerSnapshot(lastKnownName, counts, cooldowns);
        }
    }
}
