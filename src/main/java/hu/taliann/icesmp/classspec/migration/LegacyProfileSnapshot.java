package hu.taliann.icesmp.classspec.migration;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;

/**
 * Immutable, Bukkit-free snapshot of the legacy class/spec persistence owned by
 * one player. It is input to migration, not a second runtime truth source.
 *
 * <p>The model deliberately has no {@code Player}, {@code Entity}, live entity
 * UUID, item or external-plugin type. Ambiguous mechanical values are retained
 * in a bounded list rather than a map so normalization collisions cannot
 * silently overwrite either legacy value.</p>
 */
public record LegacyProfileSnapshot(
        UUID playerId,
        String primaryClassId,
        int classLevel,
        String specializationId,
        String selectedSpellId,
        Set<String> favoriteSpellIds,
        Optional<LegacyCompanion> companion,
        Map<String, Integer> soulforgeRanks,
        List<MechanicalEntry> unknownMechanicalState,
        List<Diagnostic> diagnostics) {

    public static final int MAX_ID_LENGTH = 128;
    public static final int MAX_FAVORITE_SPELLS = 256;
    public static final int MAX_SOULFORGE_BRANCHES = 16;
    public static final int MAX_UNKNOWN_ENTRIES = 64;
    public static final int MAX_UNKNOWN_KEY_LENGTH = 128;
    public static final int MAX_UNKNOWN_VALUE_LENGTH = 512;
    public static final int MAX_UNKNOWN_TOTAL_CHARACTERS = 16_384;
    public static final int MAX_DIAGNOSTICS = 128;

    public LegacyProfileSnapshot {
        playerId = Objects.requireNonNull(playerId, "playerId");
        primaryClassId = bounded(primaryClassId, MAX_ID_LENGTH, "primaryClassId");
        specializationId = bounded(specializationId, MAX_ID_LENGTH, "specializationId");
        selectedSpellId = bounded(selectedSpellId, MAX_ID_LENGTH, "selectedSpellId");
        if (classLevel < 0 || classLevel > 1_000_000) {
            throw new IllegalArgumentException("classLevel is outside the legacy snapshot limit");
        }
        favoriteSpellIds = immutableSortedStrings(
                Objects.requireNonNull(favoriteSpellIds, "favoriteSpellIds"),
                MAX_FAVORITE_SPELLS, MAX_ID_LENGTH, "favoriteSpellIds");
        companion = Objects.requireNonNull(companion, "companion");
        soulforgeRanks = immutableSoulforgeRanks(soulforgeRanks);

        final List<MechanicalEntry> mechanical = new ArrayList<>(
                Objects.requireNonNull(unknownMechanicalState, "unknownMechanicalState"));
        if (mechanical.size() > MAX_UNKNOWN_ENTRIES) {
            throw new IllegalArgumentException("Too many unknown legacy mechanical entries");
        }
        mechanical.sort(Comparator.comparing(MechanicalEntry::rawKey)
                .thenComparing(entry -> entry.valueKind().name())
                .thenComparing(MechanicalEntry::value));
        unknownMechanicalState = List.copyOf(mechanical);

        final List<Diagnostic> diagnosticCopy = new ArrayList<>(
                Objects.requireNonNull(diagnostics, "diagnostics"));
        if (diagnosticCopy.size() > MAX_DIAGNOSTICS) {
            throw new IllegalArgumentException("Too many legacy snapshot diagnostics");
        }
        diagnostics = List.copyOf(diagnosticCopy);
    }

    public static LegacyProfileSnapshot empty(final UUID playerId) {
        return new LegacyProfileSnapshot(playerId, "", 0, "", "", Set.of(), Optional.empty(),
                Map.of(), List.of(), List.of());
    }

    /** Legacy pet data before the migrator assigns it to a spec-scoped roster. */
    public record LegacyCompanion(
            String typeId,
            String name,
            int level,
            long experience,
            String traitOrMutationId,
            String stance,
            Set<String> equipmentOrModuleIds,
            long resummonAtEpochMillis,
            boolean ritualSummoned) {

        public LegacyCompanion {
            typeId = bounded(typeId, MAX_ID_LENGTH, "companion.typeId");
            name = bounded(name, MAX_UNKNOWN_VALUE_LENGTH, "companion.name");
            traitOrMutationId = bounded(traitOrMutationId, MAX_ID_LENGTH, "companion.traitOrMutationId");
            stance = bounded(stance, MAX_ID_LENGTH, "companion.stance");
            if (level < 0 || level > 1_000_000) {
                throw new IllegalArgumentException("companion.level is outside the legacy snapshot limit");
            }
            if (experience < 0L || resummonAtEpochMillis < 0L) {
                throw new IllegalArgumentException("Companion XP and resummon time must be non-negative");
            }
            equipmentOrModuleIds = immutableSortedStrings(
                    Objects.requireNonNull(equipmentOrModuleIds, "equipmentOrModuleIds"),
                    32, MAX_ID_LENGTH, "companion.equipmentOrModuleIds");
        }
    }

    /** Primitive value kinds accepted from a legacy player PDC. */
    public enum ValueKind {
        STRING,
        BYTE,
        SHORT,
        INTEGER,
        LONG,
        FLOAT,
        DOUBLE,
        BYTE_ARRAY,
        INTEGER_ARRAY,
        LONG_ARRAY
    }

    /** One preserved unknown value; duplicate normalized keys remain distinct. */
    public record MechanicalEntry(String rawKey, String normalizedKey, ValueKind valueKind, String value) {
        public MechanicalEntry {
            rawKey = boundedRequired(rawKey, MAX_UNKNOWN_KEY_LENGTH, "rawKey");
            normalizedKey = boundedRequired(normalizedKey, MAX_UNKNOWN_KEY_LENGTH, "normalizedKey");
            valueKind = Objects.requireNonNull(valueKind, "valueKind");
            value = boundedRaw(Objects.requireNonNull(value, "value"), MAX_UNKNOWN_VALUE_LENGTH, "value");
            if (!normalizedKey.equals(normalizeLegacyKey(rawKey))) {
                throw new IllegalArgumentException("normalizedKey does not match rawKey");
            }
        }
    }

    public enum DiagnosticCode {
        LIVE_ENTITY_REFERENCE_DROPPED,
        NORMALIZED_KEY_COLLISION,
        UNKNOWN_VALUE_DROPPED,
        UNKNOWN_STATE_LIMIT_REACHED,
        UNRESOLVED_SELECTED_SPELL,
        INVALID_LEGACY_VALUE
    }

    /** Bounded, non-sensitive migration diagnostic. */
    public record Diagnostic(DiagnosticCode code, String field, String detail) {
        public Diagnostic {
            code = Objects.requireNonNull(code, "code");
            field = bounded(field, MAX_UNKNOWN_KEY_LENGTH, "field");
            detail = bounded(detail, MAX_UNKNOWN_VALUE_LENGTH, "detail");
        }
    }

    /**
     * Pure collector used by the Bukkit reader. It detects semantic key
     * collisions before any map conversion and enforces the preservation-area
     * size budget.
     */
    public static final class MechanicalStateAccumulator {

        private final List<MechanicalEntry> entries = new ArrayList<>();
        private final List<Diagnostic> diagnostics = new ArrayList<>();
        private final Map<String, String> firstRawKeyByNormalizedKey = new LinkedHashMap<>();
        private int totalCharacters;
        private boolean limitReported;

        public boolean add(final String rawKey, final ValueKind kind, final String value) {
            Objects.requireNonNull(kind, "kind");
            if (value == null) {
                diagnose(DiagnosticCode.UNKNOWN_VALUE_DROPPED, safeField(rawKey), "value is null");
                return false;
            }
            final String cleanRawKey;
            final String cleanValue;
            try {
                cleanRawKey = boundedRequired(rawKey, MAX_UNKNOWN_KEY_LENGTH, "rawKey");
                cleanValue = boundedRaw(value, MAX_UNKNOWN_VALUE_LENGTH, "value");
            } catch (final IllegalArgumentException exception) {
                diagnose(DiagnosticCode.UNKNOWN_VALUE_DROPPED, safeField(rawKey), exception.getMessage());
                return false;
            }

            final String normalizedKey = normalizeLegacyKey(cleanRawKey);
            if (normalizedKey.isBlank()) {
                diagnose(DiagnosticCode.UNKNOWN_VALUE_DROPPED, cleanRawKey, "key normalizes to empty");
                return false;
            }
            final int nextSize = cleanRawKey.length() + normalizedKey.length() + cleanValue.length();
            if (entries.size() >= MAX_UNKNOWN_ENTRIES
                    || totalCharacters + nextSize > MAX_UNKNOWN_TOTAL_CHARACTERS) {
                reportLimit(cleanRawKey);
                return false;
            }

            final String firstRawKey = firstRawKeyByNormalizedKey.putIfAbsent(normalizedKey, cleanRawKey);
            if (firstRawKey != null && !firstRawKey.equals(cleanRawKey)) {
                diagnose(DiagnosticCode.NORMALIZED_KEY_COLLISION, normalizedKey,
                        firstRawKey + " conflicts with " + cleanRawKey + "; both values preserved");
            }
            entries.add(new MechanicalEntry(cleanRawKey, normalizedKey, kind, cleanValue));
            totalCharacters += nextSize;
            return true;
        }

        public void dropLiveEntityReference(final String rawKey) {
            diagnose(DiagnosticCode.LIVE_ENTITY_REFERENCE_DROPPED, safeField(rawKey),
                    "live runtime entity identity intentionally omitted");
        }

        public void diagnose(final DiagnosticCode code, final String field, final String detail) {
            if (diagnostics.size() < MAX_DIAGNOSTICS) {
                diagnostics.add(new Diagnostic(code, safeField(field), safeDetail(detail)));
            }
        }

        public List<MechanicalEntry> entries() {
            return List.copyOf(entries);
        }

        public List<Diagnostic> diagnostics() {
            return List.copyOf(diagnostics);
        }

        private void reportLimit(final String field) {
            if (!limitReported) {
                limitReported = true;
                diagnose(DiagnosticCode.UNKNOWN_STATE_LIMIT_REACHED, field,
                        "bounded legacy mechanical preservation area is full");
            }
        }
    }

    /**
     * NFKC/case/separator normalization for legacy collision detection. Hyphen,
     * dot and underscore runs are considered the same semantic separator.
     */
    public static String normalizeLegacyKey(final String value) {
        if (value == null) {
            return "";
        }
        return Normalizer.normalize(value.trim(), Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT)
                .replaceAll("[-._]+", "_");
    }

    private static Map<String, Integer> immutableSoulforgeRanks(final Map<String, Integer> values) {
        Objects.requireNonNull(values, "soulforgeRanks");
        if (values.size() > MAX_SOULFORGE_BRANCHES) {
            throw new IllegalArgumentException("Too many Soulforge branches");
        }
        final Map<String, Integer> sorted = new TreeMap<>();
        for (final Map.Entry<String, Integer> entry : values.entrySet()) {
            final String key = boundedRequired(entry.getKey(), MAX_ID_LENGTH, "soulforge branch");
            final String normalized = Normalizer.normalize(key, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
            if (sorted.putIfAbsent(normalized, Objects.requireNonNull(entry.getValue(), "Soulforge rank")) != null) {
                throw new IllegalArgumentException("Duplicate normalized Soulforge branch: " + normalized);
            }
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(sorted));
    }

    private static Set<String> immutableSortedStrings(final Set<String> values, final int maxElements,
                                                       final int maxLength, final String field) {
        if (values.size() > maxElements) {
            throw new IllegalArgumentException(field + " has too many entries");
        }
        final Set<String> sorted = new TreeSet<>();
        for (final String value : values) {
            sorted.add(boundedRequired(value, maxLength, field));
        }
        return Collections.unmodifiableSet(new LinkedHashSet<>(sorted));
    }

    private static String boundedRequired(final String value, final int maxLength, final String field) {
        final String cleaned = bounded(value, maxLength, field);
        if (cleaned.isBlank()) {
            throw new IllegalArgumentException(field + " must be non-blank");
        }
        return cleaned;
    }

    private static String bounded(final String value, final int maxLength, final String field) {
        final String cleaned = value == null ? "" : value.trim();
        if (cleaned.length() > maxLength) {
            throw new IllegalArgumentException(field + " exceeds " + maxLength + " characters");
        }
        return cleaned;
    }

    private static String boundedRaw(final String value, final int maxLength, final String field) {
        if (value.length() > maxLength) {
            throw new IllegalArgumentException(field + " exceeds " + maxLength + " characters");
        }
        return value;
    }

    private static String safeField(final String value) {
        final String cleaned = value == null ? "" : value.trim();
        return cleaned.length() <= MAX_UNKNOWN_KEY_LENGTH
                ? cleaned : cleaned.substring(0, MAX_UNKNOWN_KEY_LENGTH);
    }

    private static String safeDetail(final String value) {
        final String cleaned = value == null ? "" : value.trim();
        return cleaned.length() <= MAX_UNKNOWN_VALUE_LENGTH
                ? cleaned : cleaned.substring(0, MAX_UNKNOWN_VALUE_LENGTH);
    }
}
