package hu.taliann.icesmp.trash;

import hu.taliann.icesmp.playerprofile.application.PlayerProfileAuthority;
import hu.taliann.icesmp.playerprofile.application.PlayerProfileService;
import hu.taliann.icesmp.playerprofile.domain.ProfileSectionId;
import hu.taliann.icesmp.playerprofile.domain.section.AchievementSection;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/** Canonical Profile v2-backed authority for the hidden Archaeology discipline. */
public final class TrashArchaeologyProfileStore {

    private static final String EXTENSION_KEY = "trash_archaeology";
    private static final int MAX_LEVEL = 50;
    private static final int MAX_FAMILIES = 64;
    private static final int MAX_DOMAINS = 32;
    private static final int MAX_KNOWLEDGE = 4_096;

    public Profile profile(final UUID playerId) {
        final AchievementSection section = PlayerProfileAuthority.current().requireSection(
                Objects.requireNonNull(playerId, "playerId"), ProfileSectionId.ACHIEVEMENTS,
                AchievementSection.class);
        return decode(section.extensions().get(EXTENSION_KEY));
    }

    public CompletionStage<Commit> commitInspection(final UUID playerId, final Evidence evidence) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(evidence, "evidence");
        return PlayerProfileAuthority.current().mutateSectionConditional(
                playerId, ProfileSectionId.ACHIEVEMENTS, AchievementSection.class, current -> {
                    final Profile before = decode(current.extensions().get(EXTENSION_KEY));
                    final Commit commit = advance(before, evidence);
                    if (commit.profile().equals(before)) {
                        return PlayerProfileService.ConditionalMutation.unchanged(commit);
                    }
                    return PlayerProfileService.ConditionalMutation.changed(
                            write(current, commit.profile()), commit);
                });
    }

    /** Pure transition used by the CAS authority and deterministic regression simulations. */
    public static Commit advance(final Profile before, final Evidence evidence) {
        Objects.requireNonNull(before, "before");
        Objects.requireNonNull(evidence, "evidence");
        final LinkedHashSet<String> novel = new LinkedHashSet<>();
        for (final Discovery discovery : evidence.discoveries()) {
            if (!before.knowledge().contains(discovery.signature())) {
                novel.add(discovery.signature());
            }
        }
        if (novel.isEmpty()) return new Commit(before, Set.of(), 0L, false);

        final TreeSet<String> families = new TreeSet<>(before.families());
        final TreeSet<String> domains = new TreeSet<>(before.domains());
        families.add(evidence.family());
        domains.add(evidence.domain());
        if (families.size() > MAX_FAMILIES || domains.size() > MAX_DOMAINS) {
            throw new IllegalStateException("Archaeology breadth limit exceeded");
        }
        final TreeSet<String> knowledge = new TreeSet<>(before.knowledge());
        knowledge.addAll(novel);
        if (knowledge.size() > MAX_KNOWLEDGE) {
            throw new IllegalStateException("Archaeology knowledge ledger is full");
        }

        final boolean breadthReadyBefore = before.familiarity() >= 10
                && before.families().size() >= 4
                && before.domains().size() >= 3
                && before.historicalInspections() >= 3;
        final boolean higherOrder = evidence.discoveries().stream()
                .anyMatch(discovery -> novel.contains(discovery.signature())
                        && discovery.higherOrder());
        final boolean unlockedNow = !before.unlocked() && breadthReadyBefore && higherOrder;
        final boolean unlocked = before.unlocked() || unlockedNow;
        long awarded = 0L;
        if (unlocked) {
            for (final Discovery discovery : evidence.discoveries()) {
                if (novel.contains(discovery.signature())) {
                    awarded = Math.addExact(awarded, discovery.insight());
                }
            }
        }
        final long insight = Math.addExact(before.insight(), awarded);
        final int familiarity = Math.addExact(before.familiarity(), 1);
        final int historical = Math.addExact(before.historicalInspections(),
                evidence.historical() ? 1 : 0);
        final int level = unlocked ? Math.max(unlockedNow ? 1 : before.level(),
                levelFor(insight)) : 0;
        final Profile after = new Profile(unlocked, level, insight, familiarity,
                historical, families, domains, knowledge);
        return new Commit(after, novel, awarded, unlockedNow);
    }

    public CompletionStage<Profile> unlock(final UUID playerId) {
        return mutate(playerId, profile -> new Profile(true,
                Math.max(Math.max(1, profile.level()), levelFor(profile.insight())),
                profile.insight(), profile.familiarity(), profile.historicalInspections(),
                profile.families(), profile.domains(), profile.knowledge()));
    }

    public CompletionStage<Profile> setLevel(final UUID playerId, final int level) {
        if (level < 0 || level > MAX_LEVEL) {
            throw new IllegalArgumentException("Archaeology level must be 0..50");
        }
        return mutate(playerId, profile -> new Profile(level > 0, level, profile.insight(),
                profile.familiarity(), profile.historicalInspections(), profile.families(),
                profile.domains(), profile.knowledge()));
    }

    public CompletionStage<Profile> addInsight(final UUID playerId, final long amount) {
        if (amount < 0L) throw new IllegalArgumentException("negative Archaeology insight");
        return mutate(playerId, profile -> {
            final long insight = Math.addExact(profile.insight(), amount);
            final int level = profile.unlocked()
                    ? Math.max(profile.level(), levelFor(insight)) : 0;
            return new Profile(profile.unlocked(), level, insight, profile.familiarity(),
                    profile.historicalInspections(), profile.families(), profile.domains(),
                    profile.knowledge());
        });
    }

    public CompletionStage<Profile> reset(final UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        return PlayerProfileAuthority.current().mutateSectionConditional(
                playerId, ProfileSectionId.ACHIEVEMENTS, AchievementSection.class, current -> {
                    if (!current.extensions().containsKey(EXTENSION_KEY)) {
                        return PlayerProfileService.ConditionalMutation.unchanged(Profile.empty());
                    }
                    final LinkedHashMap<String, Object> extensions =
                            new LinkedHashMap<>(current.extensions());
                    extensions.remove(EXTENSION_KEY);
                    return PlayerProfileService.ConditionalMutation.changed(
                            new AchievementSection(current.unlocked(), current.publicAchievements(),
                                    current.claimedRewards(), current.bestiary(), extensions),
                            Profile.empty());
                });
    }

    private CompletionStage<Profile> mutate(final UUID playerId,
                                             final java.util.function.UnaryOperator<Profile> mutation) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(mutation, "mutation");
        return PlayerProfileAuthority.current().mutateSectionConditional(
                playerId, ProfileSectionId.ACHIEVEMENTS, AchievementSection.class, current -> {
                    final Profile before = decode(current.extensions().get(EXTENSION_KEY));
                    final Profile after = Objects.requireNonNull(mutation.apply(before),
                            "profile mutation");
                    if (after.equals(before)) {
                        return PlayerProfileService.ConditionalMutation.unchanged(after);
                    }
                    return PlayerProfileService.ConditionalMutation.changed(
                            write(current, after), after);
                });
    }

    private static AchievementSection write(final AchievementSection current,
                                             final Profile profile) {
        final LinkedHashMap<String, Object> extensions = new LinkedHashMap<>(current.extensions());
        extensions.put(EXTENSION_KEY, encode(profile));
        return new AchievementSection(current.unlocked(), current.publicAchievements(),
                current.claimedRewards(), current.bestiary(), extensions);
    }

    private static Map<String, Object> encode(final Profile profile) {
        final LinkedHashMap<String, Object> encoded = new LinkedHashMap<>();
        encoded.put("unlocked", profile.unlocked());
        encoded.put("level", profile.level());
        encoded.put("insight", profile.insight());
        encoded.put("familiarity", profile.familiarity());
        encoded.put("historical", profile.historicalInspections());
        encoded.put("families", truthMap(profile.families()));
        encoded.put("domains", truthMap(profile.domains()));
        encoded.put("knowledge", truthMap(profile.knowledge()));
        return Map.copyOf(encoded);
    }

    private static Map<String, Boolean> truthMap(final Set<String> values) {
        final TreeMap<String, Boolean> encoded = new TreeMap<>();
        values.forEach(value -> encoded.put(value, true));
        return Map.copyOf(encoded);
    }

    private static Profile decode(final Object raw) {
        if (raw == null) return Profile.empty();
        if (!(raw instanceof Map<?, ?> map)) {
            throw new IllegalStateException("invalid Archaeology profile payload");
        }
        final boolean unlocked = bool(map.get("unlocked"));
        final int level = integer(map.get("level"), 0, MAX_LEVEL, "level");
        final long insight = longValue(map.get("insight"), "insight");
        final int familiarity = integer(map.get("familiarity"), 0,
                Integer.MAX_VALUE, "familiarity");
        final int historical = integer(map.get("historical"), 0,
                Integer.MAX_VALUE, "historical");
        if ((unlocked && level < 1) || (!unlocked && level != 0)) {
            throw new IllegalStateException("inconsistent Archaeology unlock/level");
        }
        return new Profile(unlocked, level, insight, familiarity, historical,
                stringKeys(map.get("families"), MAX_FAMILIES, "families"),
                stringKeys(map.get("domains"), MAX_DOMAINS, "domains"),
                stringKeys(map.get("knowledge"), MAX_KNOWLEDGE, "knowledge"));
    }

    private static Set<String> stringKeys(final Object raw, final int max, final String field) {
        if (raw == null) return Set.of();
        if (!(raw instanceof Map<?, ?> map) || map.size() > max) {
            throw new IllegalStateException("invalid Archaeology " + field);
        }
        final TreeSet<String> result = new TreeSet<>();
        for (final Map.Entry<?, ?> entry : map.entrySet()) {
            if (!Boolean.TRUE.equals(entry.getValue())) {
                throw new IllegalStateException("invalid Archaeology " + field + " marker");
            }
            result.add(id(String.valueOf(entry.getKey())));
        }
        return Set.copyOf(result);
    }

    private static boolean bool(final Object value) {
        if (value == null) return false;
        if (value instanceof Boolean bool) return bool;
        throw new IllegalStateException("invalid Archaeology boolean");
    }

    private static int integer(final Object value, final int min, final int max,
                               final String field) {
        if (!(value instanceof Number number)) {
            if (value == null && min == 0) return 0;
            throw new IllegalStateException("invalid Archaeology " + field);
        }
        final long raw = number.longValue();
        if (raw < min || raw > max) throw new IllegalStateException("invalid Archaeology " + field);
        return (int) raw;
    }

    private static long longValue(final Object value, final String field) {
        if (value == null) return 0L;
        if (!(value instanceof Number number) || number.longValue() < 0L) {
            throw new IllegalStateException("invalid Archaeology " + field);
        }
        return number.longValue();
    }

    private static int levelFor(final long insight) {
        int level = 1;
        while (level < MAX_LEVEL && insight >= threshold(level + 1)) level++;
        return level;
    }

    public static long threshold(final int level) {
        if (level < 1 || level > MAX_LEVEL) {
            throw new IllegalArgumentException("Archaeology level must be 1..50");
        }
        return Math.round(0.55D * level * level + 4.5D * level);
    }

    private static String id(final String raw) {
        if (raw == null || raw.isBlank()) throw new IllegalArgumentException("blank Archaeology id");
        final String normalized = raw.trim().toLowerCase(Locale.ROOT);
        if (normalized.length() > 128 || !normalized.matches("[a-z0-9][a-z0-9._:@/-]*")) {
            throw new IllegalArgumentException("invalid Archaeology id: " + raw);
        }
        return normalized;
    }

    public record Profile(boolean unlocked, int level, long insight, int familiarity,
                          int historicalInspections, Set<String> families, Set<String> domains,
                          Set<String> knowledge) {
        public Profile {
            if (level < 0 || level > MAX_LEVEL || insight < 0L || familiarity < 0
                    || historicalInspections < 0) {
                throw new IllegalArgumentException("invalid Archaeology profile counters");
            }
            if ((unlocked && level < 1) || (!unlocked && level != 0)
                    || historicalInspections > familiarity) {
                throw new IllegalArgumentException("inconsistent Archaeology profile");
            }
            families = Set.copyOf(families);
            domains = Set.copyOf(domains);
            knowledge = Set.copyOf(knowledge);
            if (families.size() > MAX_FAMILIES || domains.size() > MAX_DOMAINS
                    || knowledge.size() > MAX_KNOWLEDGE) {
                throw new IllegalArgumentException("Archaeology profile limit exceeded");
            }
        }

        public static Profile empty() {
            return new Profile(false, 0, 0L, 0, 0, Set.of(), Set.of(), Set.of());
        }
    }

    public record Discovery(String signature, int insight, boolean higherOrder) {
        public Discovery {
            signature = id(signature);
            if (insight < 0 || insight > 5) {
                throw new IllegalArgumentException("fact insight must be 0..5");
            }
        }
    }

    public record Evidence(String family, String domain, boolean historical,
                           List<Discovery> discoveries) {
        public Evidence {
            family = id(family);
            domain = id(domain);
            discoveries = List.copyOf(discoveries);
            if (discoveries.isEmpty()) throw new IllegalArgumentException("empty inspection");
            final Set<String> signatures = new java.util.HashSet<>();
            if (discoveries.stream().anyMatch(discovery ->
                    !signatures.add(discovery.signature()))) {
                throw new IllegalArgumentException("duplicate inspection signature");
            }
        }
    }

    public record Commit(Profile profile, Set<String> novelSignatures, long awardedInsight,
                         boolean unlockedNow) {
        public Commit {
            Objects.requireNonNull(profile, "profile");
            novelSignatures = Set.copyOf(novelSignatures);
            if (awardedInsight < 0L) throw new IllegalArgumentException("negative awarded insight");
        }
    }
}
