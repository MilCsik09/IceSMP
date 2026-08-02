package hu.taliann.icesmp.classspec.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Predicate;

/**
 * Strict, deterministic provenance ledger for runtime spell grants.
 * The ledger is derivative runtime persistence: Profile v2 remains the class/spec authority, while
 * every grant records an explicit source so reconciliation can revoke only the affected authority.
 */
public final class SpellGrantLedger {
    public static final int MAX_SPELLS = 2048;
    public static final int MAX_SOURCES_PER_SPELL = 64;
    public static final int MAX_SERIALIZED_LENGTH = 1_048_576;

    private final Map<String, Set<String>> grants;

    private SpellGrantLedger(final Map<String, Set<String>> grants) {
        final Map<String, Set<String>> copy = new TreeMap<>();
        grants.forEach((spell, sources) -> copy.put(normalizeSpell(spell), normalizedSources(sources)));
        if (copy.size() > MAX_SPELLS) throw new IllegalArgumentException("Spell grant ledger is too large");
        this.grants = Collections.unmodifiableMap(copy);
    }

    public static SpellGrantLedger empty() { return new SpellGrantLedger(Map.of()); }

    public static SpellGrantLedger parse(final String serialized) {
        if (serialized == null || serialized.isBlank()) return empty();
        if (serialized.length() > MAX_SERIALIZED_LENGTH) {
            throw new IllegalArgumentException("Spell grant ledger exceeds size limit");
        }
        final Map<String, Set<String>> parsed = new LinkedHashMap<>();
        for (final String entry : serialized.split(";", -1)) {
            if (entry.isBlank()) throw new IllegalArgumentException("Blank spell grant entry");
            final int split = entry.indexOf('=');
            if (split <= 0 || split != entry.lastIndexOf('=')) {
                throw new IllegalArgumentException("Malformed spell grant entry");
            }
            final String spell = normalizeSpell(entry.substring(0, split));
            if (parsed.containsKey(spell)) throw new IllegalArgumentException("Duplicate spell grant: " + spell);
            final Set<String> sources = new LinkedHashSet<>();
            for (final String rawSource : entry.substring(split + 1).split("\\|", -1)) {
                final String source = normalizeSource(rawSource);
                if (!sources.add(source)) throw new IllegalArgumentException("Duplicate spell source");
            }
            if (sources.isEmpty() || sources.size() > MAX_SOURCES_PER_SPELL) {
                throw new IllegalArgumentException("Spell source count out of bounds");
            }
            parsed.put(spell, sources);
        }
        return new SpellGrantLedger(parsed);
    }

    public Set<String> spellIds() { return grants.keySet(); }
    public Set<String> sources(final String spellId) {
        return grants.getOrDefault(normalizeSpell(spellId), Set.of());
    }
    public boolean contains(final String spellId) { return grants.containsKey(normalizeSpell(spellId)); }

    public Mutation add(final String spellId, final String source) {
        final String spell = normalizeSpell(spellId);
        final String normalizedSource = normalizeSource(source);
        final Map<String, Set<String>> mutable = mutableCopy();
        final boolean newlyUnlocked = !mutable.containsKey(spell);
        final Set<String> sources = mutable.computeIfAbsent(spell, ignored -> new LinkedHashSet<>());
        final boolean changed = sources.add(normalizedSource);
        if (sources.size() > MAX_SOURCES_PER_SPELL) throw new IllegalArgumentException("Too many spell sources");
        return new Mutation(changed ? new SpellGrantLedger(mutable) : this, newlyUnlocked && changed, changed);
    }

    public Mutation remove(final String spellId, final String source) {
        final String spell = normalizeSpell(spellId);
        final String normalizedSource = normalizeSource(source);
        final Map<String, Set<String>> mutable = mutableCopy();
        final Set<String> sources = mutable.get(spell);
        if (sources == null || !sources.remove(normalizedSource)) return new Mutation(this, false, false);
        final boolean locked = sources.isEmpty();
        if (locked) mutable.remove(spell);
        return new Mutation(new SpellGrantLedger(mutable), locked, true);
    }

    public RevokeResult revokeSources(final Predicate<String> sourceMatches) {
        Objects.requireNonNull(sourceMatches, "sourceMatches");
        final Map<String, Set<String>> mutable = mutableCopy();
        final List<String> locked = new ArrayList<>();
        boolean changed = false;
        for (final String spell : List.copyOf(mutable.keySet())) {
            final Set<String> sources = mutable.get(spell);
            if (!sources.removeIf(sourceMatches)) continue;
            changed = true;
            if (sources.isEmpty()) {
                mutable.remove(spell);
                locked.add(spell);
            }
        }
        return new RevokeResult(changed ? new SpellGrantLedger(mutable) : this,
                List.copyOf(locked), changed);
    }

    public String serialize() {
        final StringBuilder output = new StringBuilder();
        grants.forEach((spell, sources) -> {
            if (!output.isEmpty()) output.append(';');
            output.append(spell).append('=').append(String.join("|", sources));
        });
        if (output.length() > MAX_SERIALIZED_LENGTH) {
            throw new IllegalStateException("Spell grant ledger exceeds serialized size limit");
        }
        return output.toString();
    }

    private Map<String, Set<String>> mutableCopy() {
        final Map<String, Set<String>> copy = new LinkedHashMap<>();
        grants.forEach((spell, sources) -> copy.put(spell, new LinkedHashSet<>(sources)));
        return copy;
    }

    private static Set<String> normalizedSources(final Set<String> rawSources) {
        Objects.requireNonNull(rawSources, "sources");
        if (rawSources.isEmpty() || rawSources.size() > MAX_SOURCES_PER_SPELL) {
            throw new IllegalArgumentException("Spell source count out of bounds");
        }
        final Set<String> sorted = new TreeSet<>();
        rawSources.forEach(source -> {
            if (!sorted.add(normalizeSource(source))) throw new IllegalArgumentException("Duplicate spell source");
        });
        return Collections.unmodifiableSet(sorted);
    }

    public static String normalizeSpell(final String raw) {
        final String value = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        if (value.isEmpty() || value.length() > 128 || !value.matches("[a-z0-9._-]+")) {
            throw new IllegalArgumentException("Invalid spell id");
        }
        return value;
    }

    public static String normalizeSource(final String raw) {
        final String value = raw == null ? "" : raw.trim();
        if (value.isEmpty() || value.length() > 192) {
            throw new IllegalArgumentException("Spell grant source must be explicit and bounded");
        }
        final int colon = value.indexOf(':');
        final String type = (colon < 0 ? value : value.substring(0, colon)).toUpperCase(Locale.ROOT);
        if (!Set.of("BASE", "SPEC", "TALENT", "QUEST", "ADMIN").contains(type))
            throw new IllegalArgumentException("Unsupported grant source type");
        if (colon < 0) return type;
        final String identity = value.substring(colon + 1).trim().toLowerCase(Locale.ROOT);
        if (identity.isEmpty() || !identity.matches("[a-z0-9._-]+")) {
            throw new IllegalArgumentException("Invalid grant source identity");
        }
        return type + ':' + identity;
    }

    public record Mutation(SpellGrantLedger ledger, boolean spellLockChanged, boolean changed) {
        public Mutation { Objects.requireNonNull(ledger, "ledger"); }
    }
    public record RevokeResult(SpellGrantLedger ledger, List<String> lockedSpellIds, boolean changed) {
        public RevokeResult {
            Objects.requireNonNull(ledger, "ledger");
            lockedSpellIds = List.copyOf(lockedSpellIds);
        }
    }
}
