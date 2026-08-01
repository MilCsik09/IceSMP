package hu.taliann.icesmp.classspec.domain;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Immutable specialization-local state. */
public record ClassLoadout(String specializationId, LoadoutStatus status, SealReason sealReason,
                           Map<String, String> doctrineChoices, MasteryProgress mastery,
                           SoulbondState soulbond, Set<String> favoriteSpells, String selectedSpell,
                           CapstoneStatus capstoneStatus,
                           Map<UUID, CompanionProfile> companionRoster,
                           Map<String, String> mechanicState, String migrationNote) {

    public ClassLoadout {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(mastery, "mastery");
        Objects.requireNonNull(capstoneStatus, "capstoneStatus");
        specializationId = ClassSpecCatalog.normalize(specializationId);
        selectedSpell = ClassSpecCatalog.normalize(selectedSpell);
        migrationNote = migrationNote == null ? "" : migrationNote.trim();
        doctrineChoices = normalizedMap(doctrineChoices, "doctrine");
        favoriteSpells = normalizedSet(favoriteSpells, "favorite spell");
        companionRoster = copyRoster(companionRoster);
        mechanicState = normalizedMap(mechanicState, "mechanic");

        if (status == LoadoutStatus.EMPTY) {
            if (!specializationId.isEmpty() || sealReason != null || !doctrineChoices.isEmpty()
                    || !mastery.equals(MasteryProgress.empty()) || soulbond != null
                    || !favoriteSpells.isEmpty() || !selectedSpell.isEmpty()
                    || capstoneStatus != CapstoneStatus.LOCKED || !companionRoster.isEmpty()
                    || !mechanicState.isEmpty() || !migrationNote.isEmpty()) {
                throw new IllegalArgumentException("An EMPTY loadout may not hide state");
            }
        } else if (specializationId.isEmpty()) {
            throw new IllegalArgumentException("A non-empty loadout requires a specialization id");
        }

        if (status == LoadoutStatus.SEALED && sealReason == null) {
            throw new IllegalArgumentException("A SEALED loadout requires a seal reason");
        }
        if (status != LoadoutStatus.SEALED && sealReason != null) {
            throw new IllegalArgumentException("Only a SEALED loadout may retain a seal reason");
        }

        if (!companionRoster.isEmpty()) {
            final String expectedNamespace = ClassSpecCatalog.companionNamespace(specializationId);
            if (expectedNamespace == null || companionRoster.values().stream()
                    .anyMatch(companion -> !expectedNamespace.equals(companion.namespace()))) {
                throw new IllegalArgumentException("Companion roster leaks outside its specialization namespace");
            }
        }
        final boolean hasSoulforge = mechanicState.keySet().stream().anyMatch(ClassLoadout::isSoulforgeKey);
        if (hasSoulforge && !"necromancer".equals(specializationId)) {
            throw new IllegalArgumentException("Soulforge state is only valid in a necromancer loadout");
        }
    }

    public static ClassLoadout empty() {
        return new ClassLoadout("", LoadoutStatus.EMPTY, null, Map.of(), MasteryProgress.empty(),
                null, Set.of(), "", CapstoneStatus.LOCKED, Map.of(), Map.of(), "");
    }

    public ClassLoadout withStatus(final LoadoutStatus nextStatus, final SealReason nextSealReason) {
        return new ClassLoadout(specializationId, nextStatus, nextSealReason, doctrineChoices,
                mastery, soulbond, favoriteSpells, selectedSpell, capstoneStatus, companionRoster,
                mechanicState, migrationNote);
    }

    public boolean isActivatable() {
        return status == LoadoutStatus.ACTIVE || status == LoadoutStatus.INACTIVE;
    }

    private static boolean isSoulforgeKey(final String key) {
        return key.equals("soulforge") || key.startsWith("soulforge.")
                || key.startsWith("necromancer.soulforge.");
    }

    private static Map<String, String> normalizedMap(final Map<String, String> source,
                                                     final String label) {
        Objects.requireNonNull(source, label + " map");
        final Map<String, String> result = new LinkedHashMap<>();
        for (final Map.Entry<String, String> entry : source.entrySet()) {
            final String key = ClassSpecCatalog.normalize(entry.getKey());
            if (key.isEmpty() || entry.getValue() == null) {
                throw new IllegalArgumentException(label + " keys and values must be present");
            }
            if (result.putIfAbsent(key, entry.getValue()) != null) {
                throw new IllegalArgumentException("Normalized " + label + " key collision: " + key);
            }
        }
        return Map.copyOf(result);
    }

    private static Set<String> normalizedSet(final Collection<String> source, final String label) {
        Objects.requireNonNull(source, label + " set");
        final Set<String> result = new LinkedHashSet<>();
        for (final String raw : source) {
            final String value = ClassSpecCatalog.normalize(raw);
            if (value.isEmpty()) {
                throw new IllegalArgumentException(label + " ids must be non-blank");
            }
            if (!result.add(value)) {
                throw new IllegalArgumentException("Normalized " + label + " collision: " + value);
            }
        }
        return Set.copyOf(result);
    }

    private static Map<UUID, CompanionProfile> copyRoster(
            final Map<UUID, CompanionProfile> source) {
        Objects.requireNonNull(source, "companionRoster");
        final Map<UUID, CompanionProfile> result = new LinkedHashMap<>();
        for (final Map.Entry<UUID, CompanionProfile> entry : source.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null
                    || !entry.getKey().equals(entry.getValue().companionId())) {
                throw new IllegalArgumentException("Companion roster key must equal logical companion id");
            }
            result.put(entry.getKey(), entry.getValue());
        }
        return Map.copyOf(result);
    }
}
