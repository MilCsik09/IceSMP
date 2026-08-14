package hu.taliann.icesmp.playerprofile.application;

import hu.taliann.icesmp.classspec.domain.SpellGrantLedger;
import hu.taliann.icesmp.playerprofile.domain.ProfileSectionId;
import hu.taliann.icesmp.playerprofile.domain.section.SpellbookSection;
import hu.taliann.icesmp.playerprofile.domain.section.TalentSection;
import hu.taliann.icesmp.playerprofile.transaction.PlayerProfileTransactionManager;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

/** CAS/WAL-backed talent authority with atomic spell-provenance updates. */
public final class PlayerProfileTalentStore {

    public enum Pool {
        CLASS("class:"), PROFESSION("profession:");
        private final String prefix;
        Pool(final String prefix) { this.prefix = prefix; }
        String prefix() { return prefix; }
    }

    public record PoolState(Map<String, Integer> ranks, int bonus,
                            SpellGrantLedger spellLedger) {
        public PoolState {
            ranks = normalizeRanks(ranks);
            if (bonus < 0) throw new IllegalArgumentException("talent bonus cannot be negative");
            Objects.requireNonNull(spellLedger, "spellLedger");
        }
    }

    public record Decision<R>(boolean changed, Map<String, Integer> ranks,
                              int bonus, SpellGrantLedger spellLedger, R result) {
        public Decision {
            ranks = normalizeRanks(ranks);
            if (bonus < 0) throw new IllegalArgumentException("talent bonus cannot be negative");
            Objects.requireNonNull(spellLedger, "spellLedger");
        }
        public static <R> Decision<R> unchanged(final PoolState state, final R result) {
            return new Decision<>(false, state.ranks(), state.bonus(), state.spellLedger(), result);
        }
        public static <R> Decision<R> changed(final Map<String, Integer> ranks,
                                              final int bonus,
                                              final SpellGrantLedger ledger,
                                              final R result) {
            return new Decision<>(true, ranks, bonus, ledger, result);
        }
    }

    public PoolState state(final UUID playerId, final Pool pool) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(pool, "pool");
        final var profile = PlayerProfileAuthority.current().requireCached(playerId);
        return state(profile.talents().value(), profile.spellbook().value(), pool);
    }

    public CompletionStage<Integer> grantBonus(final UUID playerId, final Pool pool,
                                                final int amount) {
        if (amount <= 0) throw new IllegalArgumentException("bonus amount must be positive");
        return PlayerProfileAuthority.current().mutateSectionConditional(
                playerId, ProfileSectionId.TALENTS, TalentSection.class, current -> {
                    final int before = bonus(current, pool);
                    final int after = Math.addExact(before, amount);
                    return PlayerProfileService.ConditionalMutation.changed(
                            withPool(current, pool, ranks(current, pool), after), after);
                });
    }

    public <R> CompletionStage<R> transact(final UUID playerId, final Pool pool,
                                           final String operationType,
                                           final Function<PoolState, Decision<R>> mutation) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(pool, "pool");
        Objects.requireNonNull(mutation, "mutation");
        final String type = requireId(operationType, "operation type");
        return PlayerProfileAuthority.current().transact(playerId, snapshot -> {
            final TalentSection currentTalents = snapshot.talents().value();
            final SpellbookSection currentSpellbook = snapshot.spellbook().value();
            final PoolState state = state(currentTalents, currentSpellbook, pool);
            final Decision<R> decision = Objects.requireNonNull(mutation.apply(state), "talent decision");
            if (!decision.changed()) {
                throw new NoChangeException(decision.result());
            }
            final TalentSection nextTalents = withPool(currentTalents, pool,
                    decision.ranks(), decision.bonus());
            final SpellbookSection nextSpellbook = withLedger(currentSpellbook,
                    decision.spellLedger());
            final List<PlayerProfileTransactionManager.SectionUpdate> updates = new ArrayList<>();
            if (!nextTalents.equals(currentTalents)) {
                updates.add(new PlayerProfileTransactionManager.SectionUpdate(
                        ProfileSectionId.TALENTS, snapshot.talents().revision(), nextTalents));
            }
            if (!nextSpellbook.equals(currentSpellbook)) {
                updates.add(new PlayerProfileTransactionManager.SectionUpdate(
                        ProfileSectionId.SPELLBOOK, snapshot.spellbook().revision(), nextSpellbook));
            }
            if (updates.isEmpty()) {
                throw new NoChangeException(decision.result());
            }
            final String fingerprint = fingerprint(type, pool, decision);
            final String operationId = "talent:" + type + ':' + playerId + ':'
                    + fingerprint.substring(0, 24) + ':' + UUID.randomUUID();
            return new PlayerProfileTransactionManager.TransactionPlan<>(
                    operationId, "talent-" + type, fingerprint, updates, decision.result());
        }).handle((result, failure) -> {
            if (failure == null) return result;
            final Throwable cause = unwrap(failure);
            if (cause instanceof NoChangeException noChange) {
                @SuppressWarnings("unchecked") final R value = (R) noChange.result;
                return value;
            }
            throw new CompletionException(cause);
        });
    }

    private static PoolState state(final TalentSection talents,
                                   final SpellbookSection spellbook,
                                   final Pool pool) {
        return new PoolState(ranks(talents, pool), bonus(talents, pool),
                SpellGrantLedger.fromProvenance(spellbook.provenance()));
    }

    private static Map<String, Integer> ranks(final TalentSection section, final Pool pool) {
        final TreeMap<String, Integer> result = new TreeMap<>();
        section.loadout().forEach((key, value) -> {
            if (!key.startsWith(pool.prefix())) return;
            final String id = requireId(key.substring(pool.prefix().length()), "talent id");
            final int rank;
            try { rank = Integer.parseInt(value); }
            catch (final NumberFormatException malformed) {
                throw new IllegalStateException("invalid PlayerProfile talent rank for " + id, malformed);
            }
            if (rank <= 0) throw new IllegalStateException("non-positive talent rank for " + id);
            result.put(id, rank);
        });
        return Map.copyOf(result);
    }

    private static int bonus(final TalentSection section, final Pool pool) {
        return pool == Pool.CLASS ? section.points() : section.bonusPoints();
    }

    private static TalentSection withPool(final TalentSection current, final Pool pool,
                                          final Map<String, Integer> ranks,
                                          final int bonus) {
        final Map<String, Integer> normalized = normalizeRanks(ranks);
        final LinkedHashMap<String, String> loadout = new LinkedHashMap<>(current.loadout());
        loadout.keySet().removeIf(key -> key.startsWith(pool.prefix()));
        normalized.forEach((id, rank) -> loadout.put(pool.prefix() + id,
                Integer.toString(rank)));
        final LinkedHashSet<String> purchased = new LinkedHashSet<>(current.purchased());
        purchased.removeIf(key -> key.startsWith(pool.prefix()));
        normalized.keySet().forEach(id -> purchased.add(pool.prefix() + id));
        return new TalentSection(
                pool == Pool.CLASS ? bonus : current.points(),
                pool == Pool.PROFESSION ? bonus : current.bonusPoints(),
                purchased, current.provenance(), loadout,
                current.refundReceipts(), current.extensions());
    }

    private static SpellbookSection withLedger(final SpellbookSection current,
                                                final SpellGrantLedger ledger) {
        if (current.provenance().equals(ledger.provenance())) return current;
        return new SpellbookSection(ledger.provenance(), current.selectedSpell(),
                current.favorites(), current.mastery(), current.persistentCooldowns(),
                current.uiState(), current.extensions());
    }

    private static Map<String, Integer> normalizeRanks(final Map<String, Integer> source) {
        if (source == null || source.isEmpty()) return Map.of();
        final TreeMap<String, Integer> result = new TreeMap<>();
        source.forEach((rawId, rawRank) -> {
            final String id = requireId(rawId, "talent id");
            final int rank = rawRank == null ? 0 : rawRank;
            if (rank <= 0) throw new IllegalArgumentException("talent rank must be positive");
            result.put(id, rank);
        });
        return Map.copyOf(result);
    }

    private static String fingerprint(final String type, final Pool pool,
                                      final Decision<?> decision) {
        final String canonical = type + '|' + pool.name() + '|'
                + new TreeMap<>(decision.ranks()) + '|' + decision.bonus() + '|'
                + decision.spellLedger().serialize();
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (final NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    private static String requireId(final String value, final String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " cannot be blank");
        final String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (!normalized.matches("[a-z0-9][a-z0-9._-]{0,95}")) {
            throw new IllegalArgumentException("invalid " + field + ": " + value);
        }
        return normalized;
    }

    private static Throwable unwrap(final Throwable failure) {
        Throwable current = failure;
        while ((current instanceof CompletionException
                || current instanceof java.util.concurrent.ExecutionException)
                && current.getCause() != null) current = current.getCause();
        return current;
    }

    private static final class NoChangeException extends RuntimeException {
        private static final long serialVersionUID = 1L;
        private final Object result;
        private NoChangeException(final Object result) {
            super("talent transaction has no durable changes", null, false, false);
            this.result = result;
        }
    }
}
