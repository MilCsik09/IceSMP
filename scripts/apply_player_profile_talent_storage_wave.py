#!/usr/bin/env python3
"""Move talent ranks, bonus pools and granted-spell provenance to PlayerProfile."""
from __future__ import annotations

from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MANAGER = ROOT / "src/main/java/hu/taliann/icesmp/managers/TalentManager.java"
STORE = ROOT / "src/main/java/hu/taliann/icesmp/playerprofile/application/PlayerProfileTalentStore.java"


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if new in text:
        return text
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected one match, found {count}")
    return text.replace(old, new, 1)


def write_store() -> None:
    STORE.write_text(r'''package hu.taliann.icesmp.playerprofile.application;

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
''', encoding="utf-8")


def patch_manager() -> None:
    text = MANAGER.read_text(encoding="utf-8")
    text = text.replace('import org.bukkit.persistence.PersistentDataType;\n', '')
    for line in (
        '    private final NamespacedKey classTalentsKey;\n',
        '    private final NamespacedKey professionTalentsKey;\n',
        '    /** K8 Emlékszilánk-beváltásból származó extra talentpontok (PDC, additív). */\n',
        '    private final NamespacedKey bonusClassPointsKey;\n',
        '    private final NamespacedKey bonusProfessionPointsKey;\n',
        '        this.classTalentsKey = new NamespacedKey(plugin, "talents_class");\n',
        '        this.professionTalentsKey = new NamespacedKey(plugin, "talents_profession");\n',
        '        this.bonusClassPointsKey = new NamespacedKey(plugin, "talent_bonus_class");\n',
        '        this.bonusProfessionPointsKey = new NamespacedKey(plugin, "talent_bonus_profession");\n',
    ):
        text = text.replace(line, '')
    field_anchor = '    private final SpecializationManager specializationManager;\n'
    fields = ('    private final JavaPlugin plugin;\n'
              '    private final hu.taliann.icesmp.playerprofile.application.PlayerProfileTalentStore talentStore =\n'
              '            new hu.taliann.icesmp.playerprofile.application.PlayerProfileTalentStore();\n')
    if fields not in text:
        if text.count(field_anchor) != 1:
            raise RuntimeError("TalentManager field anchor mismatch")
        text = text.replace(field_anchor, field_anchor + fields, 1)
    constructor_anchor = '        this.configManager = configManager;\n'
    if '        this.plugin = plugin;\n' not in text:
        text = replace_once(text, constructor_anchor,
                            '        this.plugin = plugin;\n' + constructor_anchor,
                            'TalentManager plugin assignment')

    start = text.index('    public Map<String, Integer> getRanks(final Player player, final boolean classPool) {')
    end = text.index('\n    public int getRank(', start)
    text = text[:start] + '''    public Map<String, Integer> getRanks(final Player player, final boolean classPool) {
        return talentStore.state(player.getUniqueId(), pool(classPool)).ranks();
    }
''' + text[end:]

    bonus_start = text.index('    public int getBonusPoints(final Player player, final boolean classPool) {')
    bonus_end = text.index('\n    /**\n     * K8: extra talentpont', bonus_start)
    text = text[:bonus_start] + '''    public int getBonusPoints(final Player player, final boolean classPool) {
        return talentStore.state(player.getUniqueId(), pool(classPool)).bonus();
    }
''' + text[bonus_end:]

    grant_start = text.index('    public void grantBonusPoints(final Player player, final boolean classPool, final int amount) {')
    grant_end = text.index('\n    /**\n     * Checks whether a talent is available', grant_start)
    text = text[:grant_start] + '''    public java.util.concurrent.CompletionStage<Integer> grantBonusPoints(
            final Player player, final boolean classPool, final int amount) {
        if (amount <= 0) return java.util.concurrent.CompletableFuture.completedFuture(
                getBonusPoints(player, classPool));
        return talentStore.grantBonus(player.getUniqueId(), pool(classPool), amount);
    }
''' + text[grant_end:]

    old_tree = '''    private boolean treeGatesMet(final Player player, final boolean classPool, final ConfigurationSection talentSection) {
        final String parentId = talentSection.getString("requires-talent");
        if (parentId != null && !parentId.isBlank() && getRank(player, classPool, parentId) <= 0) {
            return false;
        }

        for (final String excluded : talentSection.getStringList("excludes")) {
            if (getRank(player, classPool, excluded) > 0) {
                return false;
            }
        }

        final int requiresSpent = Math.max(0, talentSection.getInt("requires-spent", 0));
        return requiresSpent <= 0 || getSpentPoints(player, classPool) >= requiresSpent;
    }
'''
    new_tree = '''    private boolean treeGatesMet(final Player player, final boolean classPool,
                                 final ConfigurationSection talentSection) {
        return treeGatesMet(player, classPool, talentSection, getRanks(player, classPool));
    }

    private boolean treeGatesMet(final Player player, final boolean classPool,
                                 final ConfigurationSection talentSection,
                                 final Map<String, Integer> ranks) {
        final String parentId = talentSection.getString("requires-talent");
        if (parentId != null && !parentId.isBlank()
                && ranks.getOrDefault(parentId.trim().toLowerCase(Locale.ROOT), 0) <= 0) {
            return false;
        }
        for (final String excluded : talentSection.getStringList("excludes")) {
            if (ranks.getOrDefault(excluded.trim().toLowerCase(Locale.ROOT), 0) > 0) return false;
        }
        final int requiresSpent = Math.max(0, talentSection.getInt("requires-spent", 0));
        return requiresSpent <= 0 || spentPoints(player, classPool, ranks) >= requiresSpent;
    }
'''
    text = replace_once(text, old_tree, new_tree, 'talent tree gates')

    refund_start = text.index('    public int refundUnavailableTalents(final Player player, final boolean classPool) {')
    refund_end = text.index('\n    /**\n     * Points spent on talents', refund_start)
    text = text[:refund_start] + '''    public java.util.concurrent.CompletionStage<Integer> refundUnavailableTalents(
            final Player player, final boolean classPool) {
        final ConfigurationSection definitions = getDefinitions(classPool);
        return talentStore.transact(player.getUniqueId(), pool(classPool), "refund", state -> {
            final Map<String, Integer> ranks = new LinkedHashMap<>(state.ranks());
            hu.taliann.icesmp.classspec.domain.SpellGrantLedger ledger = state.spellLedger();
            int refunded = 0;
            final var iterator = ranks.entrySet().iterator();
            while (iterator.hasNext()) {
                final Map.Entry<String, Integer> entry = iterator.next();
                if (isAvailable(player, classPool, entry.getKey())) continue;
                refunded = Math.addExact(refunded, entry.getValue());
                final ConfigurationSection section = definitions == null ? null
                        : definitions.getConfigurationSection(entry.getKey());
                final String grantsSpell = section == null ? null : section.getString("grants-spell");
                if (grantsSpell != null && !grantsSpell.isBlank()) {
                    ledger = ledger.remove(grantsSpell,
                            JobManager.SOURCE_TALENT_PREFIX + entry.getKey()).ledger();
                }
                iterator.remove();
            }
            if (refunded == 0) {
                return hu.taliann.icesmp.playerprofile.application.PlayerProfileTalentStore.Decision
                        .unchanged(state, 0);
            }
            return hu.taliann.icesmp.playerprofile.application.PlayerProfileTalentStore.Decision
                    .changed(ranks, state.bonus(), ledger, refunded);
        }).thenCompose(refunded -> schedulePlayer(player, () -> applyAttributeTalents(player))
                .thenApply(ignored -> refunded));
    }
''' + text[refund_end:]

    spent_old = '''    public int getSpentPoints(final Player player, final boolean classPool) {
        int spent = 0;
        for (final Map.Entry<String, Integer> entry : getRanks(player, classPool).entrySet()) {
            if (isAvailable(player, classPool, entry.getKey())) {
                spent += entry.getValue();
            }
        }
        return spent;
    }
'''
    spent_new = '''    public int getSpentPoints(final Player player, final boolean classPool) {
        return spentPoints(player, classPool, getRanks(player, classPool));
    }

    private int spentPoints(final Player player, final boolean classPool,
                            final Map<String, Integer> ranks) {
        int spent = 0;
        for (final Map.Entry<String, Integer> entry : ranks.entrySet()) {
            if (isAvailable(player, classPool, entry.getKey())) {
                spent = Math.addExact(spent, entry.getValue());
            }
        }
        return spent;
    }
'''
    text = replace_once(text, spent_old, spent_new, 'spent points helper')

    spend_start = text.index('    public boolean spendPoint(final Player player, final boolean classPool, final String talentId) {')
    spend_end = text.index('\n    /**\n     * Sums the effect contributions', spend_start)
    text = text[:spend_start] + '''    public java.util.concurrent.CompletionStage<Boolean> spendPoint(
            final Player player, final boolean classPool, final String talentId) {
        final ConfigurationSection definitions = getDefinitions(classPool);
        if (definitions == null || talentId == null) {
            return java.util.concurrent.CompletableFuture.completedFuture(false);
        }
        final String normalizedId = talentId.toLowerCase(Locale.ROOT);
        final ConfigurationSection talentSection = definitions.getConfigurationSection(normalizedId);
        if (talentSection == null) return java.util.concurrent.CompletableFuture.completedFuture(false);
        final int maxRank = Math.max(1, talentSection.getInt("max-rank", 1));
        final String grantsSpell = talentSection.getString("grants-spell");
        final boolean capstone = talentSection.getInt("requires-spent", 0) > 0;
        return talentStore.transact(player.getUniqueId(), pool(classPool), "spend", state -> {
            if (!meetsRequirements(player, talentSection)
                    || !treeGatesMet(player, classPool, talentSection, state.ranks())) {
                return hu.taliann.icesmp.playerprofile.application.PlayerProfileTalentStore.Decision
                        .unchanged(state, new SpendCommit(false, capstone));
            }
            final int available = Math.max(0,
                    Math.addExact(earnedBasePoints(player, classPool), state.bonus())
                            - spentPoints(player, classPool, state.ranks()));
            final int currentRank = state.ranks().getOrDefault(normalizedId, 0);
            if (available <= 0 || currentRank >= maxRank) {
                return hu.taliann.icesmp.playerprofile.application.PlayerProfileTalentStore.Decision
                        .unchanged(state, new SpendCommit(false, capstone));
            }
            final Map<String, Integer> ranks = new LinkedHashMap<>(state.ranks());
            ranks.put(normalizedId, currentRank + 1);
            hu.taliann.icesmp.classspec.domain.SpellGrantLedger ledger = state.spellLedger();
            if (currentRank == 0 && grantsSpell != null && !grantsSpell.isBlank()) {
                ledger = ledger.add(grantsSpell,
                        JobManager.SOURCE_TALENT_PREFIX + normalizedId).ledger();
            }
            return hu.taliann.icesmp.playerprofile.application.PlayerProfileTalentStore.Decision
                    .changed(ranks, state.bonus(), ledger, new SpendCommit(true, capstone));
        }).thenCompose(commit -> {
            if (!commit.changed()) return java.util.concurrent.CompletableFuture.completedFuture(false);
            return schedulePlayer(player, () -> {
                applyAttributeTalents(player);
                if (commit.capstone()) AdvancementService.award(player, "capstone");
            }).thenApply(ignored -> true);
        });
    }

    private record SpendCommit(boolean changed, boolean capstone) { }
''' + text[spend_end:]

    save_start = text.index('    private void saveRanks(final Player player, final boolean classPool,')
    class_end = text.rfind('\n}')
    if class_end <= save_start:
        raise RuntimeError('TalentManager class end not found after saveRanks')
    helpers = '''    public void runOnOwnerThread(final Player player, final Runnable action) {
        player.getScheduler().run(plugin, ignored -> action.run(), null);
    }

    private java.util.concurrent.CompletionStage<Void> schedulePlayer(
            final Player player, final Runnable action) {
        final java.util.concurrent.CompletableFuture<Void> result =
                new java.util.concurrent.CompletableFuture<>();
        player.getScheduler().run(plugin, ignored -> {
            try { action.run(); result.complete(null); }
            catch (final Throwable failure) { result.completeExceptionally(failure); }
        }, () -> result.completeExceptionally(
                new IllegalStateException("Player scheduler rejected talent effect")));
        return result;
    }

    private int earnedBasePoints(final Player player, final boolean classPool) {
        if (classPool) {
            final int perLevels = Math.max(1,
                    configManager.getInt("talents.class.points-per-levels", 5));
            return (jobManager.hasPrimaryJob(player) ? jobManager.getPrimaryLevel(player) : 0)
                    / perLevels;
        }
        final int perLevels = Math.max(1,
                configManager.getInt("talents.profession.points-per-levels", 10));
        int totalLevels = 0;
        for (final ProfessionType professionType : ProfessionType.values()) {
            totalLevels = Math.addExact(totalLevels,
                    Math.max(0, professionManager.getLevel(player, professionType) - 1));
        }
        return totalLevels / perLevels;
    }

    private static hu.taliann.icesmp.playerprofile.application.PlayerProfileTalentStore.Pool pool(
            final boolean classPool) {
        return classPool
                ? hu.taliann.icesmp.playerprofile.application.PlayerProfileTalentStore.Pool.CLASS
                : hu.taliann.icesmp.playerprofile.application.PlayerProfileTalentStore.Pool.PROFESSION;
    }
'''
    text = text[:save_start] + helpers + text[class_end:]
    MANAGER.write_text(text, encoding="utf-8")


def main() -> int:
    write_store()
    patch_manager()
    print("PlayerProfile transactional talent authority wave applied.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
