package hu.taliann.icesmp.classspec.migration;

import hu.taliann.icesmp.data.JobType;
import hu.taliann.icesmp.data.SpecializationType;
import hu.taliann.icesmp.listeners.AbilityCatalystListener;
import hu.taliann.icesmp.managers.JobManager;
import hu.taliann.icesmp.managers.PetManager;
import hu.taliann.icesmp.managers.SoulforgeManager;
import hu.taliann.icesmp.managers.SpecializationManager;
import hu.taliann.icesmp.managers.SpellFavoritesManager;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Player-thread adapter that reads the existing PDC/manager state into a pure
 * {@link LegacyProfileSnapshot}. The returned object never retains the player,
 * an entity, an item or a live entity UUID.
 */
public final class BukkitLegacyProfileSnapshotReader {

    private static final Set<String> STRING_FIELDS = Set.of(
            "job_primary", "class_spec", "favorite_spells", "unlocked_spells", "spell_grants",
            "pet_name", "pet_type", "pet_stance");
    private static final Set<String> INTEGER_FIELDS = Set.of(
            "job_primary_xp", "selected_spell_index", "pet_level", "pet_xp",
            "soulforge_elet", "soulforge_sebzes", "soulforge_letszam");
    private static final Set<String> LONG_FIELDS = Set.of("pet_respawn_at");
    private static final Set<String> BYTE_FIELDS = Set.of("pet_armor", "pet_summoned");
    private static final Set<String> IGNORED_DERIVED_FIELDS = Set.of(
            "class_health_mod", "class_damage_mod", "pet_health_mod", "pet_damage_mod",
            "pet_armor_defense_mod", "pet_armor_health_mod");
    private static final Set<String> EXPLICIT_UNKNOWN_MECHANICAL_FIELDS = Set.of(
            "memory_spec_unlock", "spell_mastery", "warlock_pakt", "shaman_totem");
    private static final List<String> UNKNOWN_MECHANICAL_PREFIXES = List.of(
            "class_", "class-", "spec_", "spec-", "mastery_", "mastery-",
            "doctrine_", "doctrine-", "soulbond_", "soulbond-", "capstone_", "capstone-",
            "companion_", "companion-", "roster_", "roster-", "minion_", "minion-",
            "pet_", "pet-", "soulforge_", "soulforge-");

    private final String pluginNamespace;
    private final JobManager jobManager;
    private final SpecializationManager specializationManager;
    private final SpellFavoritesManager spellFavoritesManager;
    private final AbilityCatalystListener abilityCatalystListener;
    private final PetManager petManager;
    private final SoulforgeManager soulforgeManager;
    private final NamespacedKey primaryClassKey;
    private final NamespacedKey specializationKey;
    private final NamespacedKey favoritesKey;
    private final NamespacedKey selectedSpellIndexKey;
    private final NamespacedKey petEntityKey;

    public BukkitLegacyProfileSnapshotReader(
            final JavaPlugin plugin,
            final JobManager jobManager,
            final SpecializationManager specializationManager,
            final SpellFavoritesManager spellFavoritesManager,
            final AbilityCatalystListener abilityCatalystListener,
            final PetManager petManager,
            final SoulforgeManager soulforgeManager) {
        Objects.requireNonNull(plugin, "plugin");
        this.jobManager = Objects.requireNonNull(jobManager, "jobManager");
        this.specializationManager = Objects.requireNonNull(specializationManager, "specializationManager");
        this.spellFavoritesManager = Objects.requireNonNull(spellFavoritesManager, "spellFavoritesManager");
        this.abilityCatalystListener = Objects.requireNonNull(abilityCatalystListener, "abilityCatalystListener");
        this.petManager = Objects.requireNonNull(petManager, "petManager");
        this.soulforgeManager = Objects.requireNonNull(soulforgeManager, "soulforgeManager");
        this.primaryClassKey = new NamespacedKey(plugin, "job_primary");
        this.specializationKey = new NamespacedKey(plugin, "class_spec");
        this.favoritesKey = new NamespacedKey(plugin, "favorite_spells");
        this.selectedSpellIndexKey = new NamespacedKey(plugin, "selected_spell_index");
        this.petEntityKey = new NamespacedKey(plugin, "pet_entity");
        this.pluginNamespace = primaryClassKey.getNamespace();
    }

    /**
     * Reads one immutable snapshot. The caller must invoke this on the owning
     * player's Paper/Folia scheduler; no file I/O or entity resolution occurs.
     */
    public LegacyProfileSnapshot read(final Player player) {
        Objects.requireNonNull(player, "player");
        final PersistentDataContainer pdc = player.getPersistentDataContainer();
        final LegacyProfileSnapshot.MechanicalStateAccumulator uncertain =
                new LegacyProfileSnapshot.MechanicalStateAccumulator();

        final JobType knownClass = jobManager.getLegacyPrimaryJob(player);
        final String rawClass = pdc.get(primaryClassKey, PersistentDataType.STRING);
        final String primaryClassId = knownClass == null
                ? safeIdentity("job_primary", rawClass, uncertain)
                : knownClass.getId();

        final SpecializationType knownSpecialization = specializationManager.getLegacyClassSpecialization(player);
        final String rawSpecialization = pdc.get(specializationKey, PersistentDataType.STRING);
        final String specializationId = knownSpecialization == null
                ? safeIdentity("class_spec", rawSpecialization, uncertain)
                : knownSpecialization.getId();

        final List<String> unlockedSpells = abilityCatalystListener.getUnlockedSpellIds(player);
        final Integer storedSelection = pdc.get(selectedSpellIndexKey, PersistentDataType.INTEGER);
        final String selectedSpellId = safeIdentity(
                "selected_spell", abilityCatalystListener.getSelectedSpellId(player), uncertain);
        if (storedSelection != null && (storedSelection < 0 || storedSelection >= unlockedSpells.size())) {
            uncertain.add("selected_spell_index", LegacyProfileSnapshot.ValueKind.INTEGER,
                    Integer.toString(storedSelection));
            uncertain.diagnose(LegacyProfileSnapshot.DiagnosticCode.UNRESOLVED_SELECTED_SPELL,
                    "selected_spell_index", "stored index does not identify an unlocked spell");
        }

        final Set<String> favoriteSpells = boundedFavorites(player, pdc, uncertain);
        final Optional<LegacyProfileSnapshot.LegacyCompanion> companion = readCompanion(player, uncertain);
        final Map<String, Integer> soulforgeRanks = readSoulforgeRanks(player, uncertain);
        readUnknownMechanicalState(pdc, uncertain);

        return new LegacyProfileSnapshot(
                player.getUniqueId(),
                primaryClassId,
                Math.max(0, jobManager.getPrimaryLevel(player)),
                specializationId,
                selectedSpellId,
                favoriteSpells,
                companion,
                soulforgeRanks,
                uncertain.entries(),
                uncertain.diagnostics());
    }

    private Set<String> boundedFavorites(final Player player, final PersistentDataContainer pdc,
                                         final LegacyProfileSnapshot.MechanicalStateAccumulator uncertain) {
        final Set<String> source = spellFavoritesManager.favorites(player);
        final List<String> sorted = source.stream().filter(Objects::nonNull).sorted().toList();
        final Set<String> accepted = new LinkedHashSet<>();
        boolean invalid = sorted.size() > LegacyProfileSnapshot.MAX_FAVORITE_SPELLS;
        for (final String spellId : sorted) {
            if (accepted.size() >= LegacyProfileSnapshot.MAX_FAVORITE_SPELLS) {
                break;
            }
            final String clean = spellId.trim();
            if (clean.isEmpty() || clean.length() > LegacyProfileSnapshot.MAX_ID_LENGTH) {
                invalid = true;
                continue;
            }
            accepted.add(clean);
        }
        if (invalid) {
            final String raw = pdc.get(favoritesKey, PersistentDataType.STRING);
            if (raw != null) {
                uncertain.add("favorite_spells", LegacyProfileSnapshot.ValueKind.STRING, raw);
            }
            uncertain.diagnose(LegacyProfileSnapshot.DiagnosticCode.INVALID_LEGACY_VALUE,
                    "favorite_spells", "one or more favorite spell ids exceeded snapshot limits");
        }
        return Set.copyOf(accepted);
    }

    private Optional<LegacyProfileSnapshot.LegacyCompanion> readCompanion(
            final Player player, final LegacyProfileSnapshot.MechanicalStateAccumulator uncertain) {
        final PetManager.PersistentPetSnapshot pet = petManager.snapshotPersistentState(player);
        if (!pet.present()) {
            return Optional.empty();
        }
        if (pet.liveEntityReferencePresent()) {
            uncertain.dropLiveEntityReference("pet_entity");
        }
        final Set<String> equipment = pet.armored() ? Set.of("legacy_pet_armor") : Set.of();
        return Optional.of(new LegacyProfileSnapshot.LegacyCompanion(
                safeIdentity("pet_type", pet.typeId(), uncertain),
                safeText("pet_name", pet.name(), LegacyProfileSnapshot.MAX_UNKNOWN_VALUE_LENGTH, uncertain),
                Math.max(0, pet.level()),
                Math.max(0L, pet.experience()),
                "",
                safeIdentity("pet_stance", pet.stance(), uncertain),
                equipment,
                Math.max(0L, pet.resummonAtEpochMillis()),
                pet.ritualSummoned()));
    }

    private Map<String, Integer> readSoulforgeRanks(
            final Player player, final LegacyProfileSnapshot.MechanicalStateAccumulator uncertain) {
        final Map<String, Integer> result = new LinkedHashMap<>();
        soulforgeManager.snapshotPersistedRanks(player).entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    final String branch = entry.getKey().name().toLowerCase(Locale.ROOT);
                    final int rank = entry.getValue();
                    result.put(branch, rank);
                    if (rank < 0 || rank > SoulforgeManager.MAX_RANK) {
                        uncertain.diagnose(LegacyProfileSnapshot.DiagnosticCode.INVALID_LEGACY_VALUE,
                                "soulforge_" + branch, "rank is outside the supported legacy range");
                    }
                });
        return Map.copyOf(result);
    }

    private void readUnknownMechanicalState(final PersistentDataContainer pdc,
                                            final LegacyProfileSnapshot.MechanicalStateAccumulator uncertain) {
        final List<NamespacedKey> keys = new ArrayList<>(pdc.getKeys());
        keys.sort(Comparator.comparing(NamespacedKey::toString));
        for (final NamespacedKey key : keys) {
            if (!pluginNamespace.equals(key.getNamespace())) {
                continue;
            }
            final String rawKey = key.getKey();
            if (rawKey.equals(petEntityKey.getKey())) {
                // Reported once by the pet snapshot path; never copy the UUID/value.
                continue;
            }
            if (IGNORED_DERIVED_FIELDS.contains(rawKey)) {
                continue;
            }
            if (isStructuredFieldWithExpectedType(pdc, key, rawKey)) {
                continue;
            }
            if (isStructuredField(rawKey)) {
                uncertain.diagnose(LegacyProfileSnapshot.DiagnosticCode.INVALID_LEGACY_VALUE,
                        rawKey, "legacy PDC field has an unexpected primitive type");
                preservePrimitive(pdc, key, uncertain);
                continue;
            }
            if (EXPLICIT_UNKNOWN_MECHANICAL_FIELDS.contains(rawKey) || hasUnknownMechanicalPrefix(rawKey)) {
                preservePrimitive(pdc, key, uncertain);
            }
        }
    }

    private static boolean isStructuredFieldWithExpectedType(final PersistentDataContainer pdc,
                                                               final NamespacedKey key, final String rawKey) {
        if (STRING_FIELDS.contains(rawKey)) {
            return pdc.has(key, PersistentDataType.STRING);
        }
        if (INTEGER_FIELDS.contains(rawKey)) {
            return pdc.has(key, PersistentDataType.INTEGER);
        }
        if (LONG_FIELDS.contains(rawKey)) {
            return pdc.has(key, PersistentDataType.LONG);
        }
        if (BYTE_FIELDS.contains(rawKey)) {
            return pdc.has(key, PersistentDataType.BYTE);
        }
        return false;
    }

    private static boolean isStructuredField(final String rawKey) {
        return STRING_FIELDS.contains(rawKey) || INTEGER_FIELDS.contains(rawKey)
                || LONG_FIELDS.contains(rawKey) || BYTE_FIELDS.contains(rawKey);
    }

    private static boolean hasUnknownMechanicalPrefix(final String key) {
        return UNKNOWN_MECHANICAL_PREFIXES.stream().anyMatch(key::startsWith);
    }

    private static void preservePrimitive(final PersistentDataContainer pdc, final NamespacedKey key,
                                          final LegacyProfileSnapshot.MechanicalStateAccumulator uncertain) {
        final String rawKey = key.getKey();
        if (pdc.has(key, PersistentDataType.STRING)) {
            final String value = pdc.get(key, PersistentDataType.STRING);
            if (value != null && isLiveRuntimeIdentity(rawKey, value)) {
                uncertain.dropLiveEntityReference(rawKey);
            } else if (value != null) {
                uncertain.add(rawKey, LegacyProfileSnapshot.ValueKind.STRING, value);
            }
            return;
        }
        if (pdc.has(key, PersistentDataType.BYTE)) {
            uncertain.add(rawKey, LegacyProfileSnapshot.ValueKind.BYTE,
                    Byte.toString(pdc.getOrDefault(key, PersistentDataType.BYTE, (byte) 0)));
            return;
        }
        if (pdc.has(key, PersistentDataType.SHORT)) {
            uncertain.add(rawKey, LegacyProfileSnapshot.ValueKind.SHORT,
                    Short.toString(pdc.getOrDefault(key, PersistentDataType.SHORT, (short) 0)));
            return;
        }
        if (pdc.has(key, PersistentDataType.INTEGER)) {
            uncertain.add(rawKey, LegacyProfileSnapshot.ValueKind.INTEGER,
                    Integer.toString(pdc.getOrDefault(key, PersistentDataType.INTEGER, 0)));
            return;
        }
        if (pdc.has(key, PersistentDataType.LONG)) {
            uncertain.add(rawKey, LegacyProfileSnapshot.ValueKind.LONG,
                    Long.toString(pdc.getOrDefault(key, PersistentDataType.LONG, 0L)));
            return;
        }
        if (pdc.has(key, PersistentDataType.FLOAT)) {
            uncertain.add(rawKey, LegacyProfileSnapshot.ValueKind.FLOAT,
                    Float.toString(pdc.getOrDefault(key, PersistentDataType.FLOAT, 0.0F)));
            return;
        }
        if (pdc.has(key, PersistentDataType.DOUBLE)) {
            uncertain.add(rawKey, LegacyProfileSnapshot.ValueKind.DOUBLE,
                    Double.toString(pdc.getOrDefault(key, PersistentDataType.DOUBLE, 0.0D)));
            return;
        }
        if (pdc.has(key, PersistentDataType.BYTE_ARRAY)) {
            final byte[] value = pdc.get(key, PersistentDataType.BYTE_ARRAY);
            if (value != null && value.length > LegacyProfileSnapshot.MAX_UNKNOWN_VALUE_LENGTH / 2) {
                uncertain.diagnose(LegacyProfileSnapshot.DiagnosticCode.UNKNOWN_VALUE_DROPPED,
                        rawKey, "legacy byte array exceeds the snapshot limit");
                return;
            }
            uncertain.add(rawKey, LegacyProfileSnapshot.ValueKind.BYTE_ARRAY,
                    value == null ? "" : HexFormat.of().formatHex(value));
            return;
        }
        if (pdc.has(key, PersistentDataType.INTEGER_ARRAY)) {
            final int[] value = pdc.get(key, PersistentDataType.INTEGER_ARRAY);
            if (value != null && value.length > 32) {
                uncertain.diagnose(LegacyProfileSnapshot.DiagnosticCode.UNKNOWN_VALUE_DROPPED,
                        rawKey, "legacy integer array exceeds the snapshot limit");
                return;
            }
            uncertain.add(rawKey, LegacyProfileSnapshot.ValueKind.INTEGER_ARRAY,
                    value == null ? "" : Arrays.toString(value));
            return;
        }
        if (pdc.has(key, PersistentDataType.LONG_ARRAY)) {
            final long[] value = pdc.get(key, PersistentDataType.LONG_ARRAY);
            if (value != null && value.length > 24) {
                uncertain.diagnose(LegacyProfileSnapshot.DiagnosticCode.UNKNOWN_VALUE_DROPPED,
                        rawKey, "legacy long array exceeds the snapshot limit");
                return;
            }
            uncertain.add(rawKey, LegacyProfileSnapshot.ValueKind.LONG_ARRAY,
                    value == null ? "" : Arrays.toString(value));
            return;
        }
        uncertain.diagnose(LegacyProfileSnapshot.DiagnosticCode.UNKNOWN_VALUE_DROPPED,
                rawKey, "unsupported legacy PDC primitive type");
    }

    private static String safeIdentity(final String field, final String value,
                                       final LegacyProfileSnapshot.MechanicalStateAccumulator uncertain) {
        return safeText(field, value, LegacyProfileSnapshot.MAX_ID_LENGTH, uncertain);
    }

    private static String safeText(final String field, final String value, final int maxLength,
                                   final LegacyProfileSnapshot.MechanicalStateAccumulator uncertain) {
        if (value == null) {
            return "";
        }
        final String clean = value.trim();
        if (clean.length() <= maxLength) {
            return clean;
        }
        uncertain.add(field, LegacyProfileSnapshot.ValueKind.STRING, value);
        uncertain.diagnose(LegacyProfileSnapshot.DiagnosticCode.INVALID_LEGACY_VALUE,
                field, "value exceeds the structured snapshot limit");
        return "";
    }

    private static boolean isLiveRuntimeIdentity(final String rawKey, final String value) {
        final String normalizedKey = LegacyProfileSnapshot.normalizeLegacyKey(rawKey);
        final boolean explicitlyDurableIdentity = normalizedKey.contains("logical")
                || normalizedKey.contains("signature") || normalizedKey.contains("soulbond");
        final boolean runtimeEntityField = !explicitlyDurableIdentity
                && (normalizedKey.contains("entity") || normalizedKey.contains("pet")
                || normalizedKey.contains("minion") || normalizedKey.contains("companion"));
        if (!runtimeEntityField) {
            return false;
        }
        try {
            UUID.fromString(value.trim());
            return true;
        } catch (final IllegalArgumentException exception) {
            return false;
        }
    }
}
