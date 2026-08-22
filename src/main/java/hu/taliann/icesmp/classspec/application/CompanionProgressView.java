package hu.taliann.icesmp.classspec.application;

import hu.taliann.icesmp.classspec.domain.CompanionProfile;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;

/**
 * Read-only, UI-ready projection of one durable Profile v2 companion.
 *
 * <p>Entity UUIDs and other rebuildable runtime state are intentionally absent. Evolution is
 * derived from the logical companion profile and the configured level curve, so the menu and the
 * live-entity reconciler can explain the same progression without creating a second authority.</p>
 */
public record CompanionProgressView(
        CompanionProfile companion,
        String displayName,
        String roleName,
        String formName,
        int level,
        int powerLevel,
        long experience,
        long nextLevelCost,
        int maxLevel,
        int mutationStage,
        int mutationMaximum,
        String nextFormName,
        OptionalInt nextFormLevel,
        boolean selected,
        boolean live) {

    public static final String UNHOLY_GHOUL = "unholy.ghoul";
    public static final String DEMON_ROSTER = "demonologist.roster";
    public static final String GHOUL_MUTATION_STAGE = "ghoul_mutation_stage";

    public CompanionProgressView {
        Objects.requireNonNull(companion, "companion");
        displayName = required(displayName, "displayName");
        roleName = required(roleName, "roleName");
        formName = required(formName, "formName");
        nextFormName = nextFormName == null ? "" : nextFormName;
        nextFormLevel = Objects.requireNonNull(nextFormLevel, "nextFormLevel");
        if (level < 1 || powerLevel < level || experience < 0L || nextLevelCost < 1L
                || maxLevel < 1 || mutationStage < 0 || mutationMaximum < 0) {
            throw new IllegalArgumentException("Companion projection values are outside bounds");
        }
    }

    public static CompanionProgressView project(final CompanionProfile companion,
                                                final boolean selected,
                                                final boolean live,
                                                final int maxLevel,
                                                final int baseXp,
                                                final int incrementPerLevel,
                                                final int ritualBonusLevels,
                                                final int mutationBonusPerStage,
                                                final int mutationMaximum,
                                                final int tier2Level,
                                                final int tier3Level) {
        Objects.requireNonNull(companion, "companion");
        final int boundedMax = Math.max(1, Math.min(CompanionProfile.MAX_LEVEL, maxLevel));
        final int tier2 = Math.max(2, tier2Level);
        final int tier3 = Math.max(tier2 + 1, tier3Level);
        final boolean ghoul = UNHOLY_GHOUL.equals(companion.namespace());
        final boolean demon = DEMON_ROSTER.equals(companion.namespace());
        final int mutationCap = ghoul ? Math.max(1, mutationMaximum) : 0;
        final int mutation = ghoul ? Math.min(mutationCap, parseNonNegativeInt(
                companion.persistentState().get(GHOUL_MUTATION_STAGE))) : 0;
        final int mutationPower = multiplyBounded(mutation, Math.max(0, mutationBonusPerStage));
        final int ritualPower = Boolean.parseBoolean(companion.persistentState()
                .getOrDefault("ritual_summoned", "false")) ? Math.max(0, ritualBonusLevels) : 0;
        final int powerLevel = addBounded(companion.level(), addBounded(ritualPower, mutationPower));
        final int evolutionLevel = ghoul ? addBounded(companion.level(), mutationPower)
                : companion.level();
        final Evolution evolution = evolution(ghoul, demon, evolutionLevel, mutationPower,
                tier2, tier3, companion.typeId());

        return new CompanionProgressView(companion,
                companion.name().isBlank() ? defaultName(companion.namespace()) : companion.name(),
                roleName(companion.namespace()), evolution.currentName(), companion.level(),
                powerLevel, companion.experience(), levelCost(companion.level(), baseXp,
                incrementPerLevel), boundedMax, mutation, mutationCap, evolution.nextName(),
                evolution.nextBaseLevel(), selected, live);
    }

    /** Runtime entity type derived from the same evolution rules used by the menu. */
    public static String expectedEntityType(final CompanionProfile companion,
                                            final int mutationBonusPerStage,
                                            final int mutationMaximum,
                                            final int tier2Level,
                                            final int tier3Level) {
        Objects.requireNonNull(companion, "companion");
        final int mutation = UNHOLY_GHOUL.equals(companion.namespace())
                ? Math.min(Math.max(1, mutationMaximum), parseNonNegativeInt(
                companion.persistentState().get(GHOUL_MUTATION_STAGE))) : 0;
        final int effectiveLevel = UNHOLY_GHOUL.equals(companion.namespace())
                ? addBounded(companion.level(), multiplyBounded(mutation,
                Math.max(0, mutationBonusPerStage))) : companion.level();
        return evolution(UNHOLY_GHOUL.equals(companion.namespace()),
                DEMON_ROSTER.equals(companion.namespace()), effectiveLevel,
                Math.max(0, effectiveLevel - companion.level()), Math.max(2, tier2Level),
                Math.max(Math.max(2, tier2Level) + 1, tier3Level), companion.typeId()).entityType();
    }

    public boolean maxLevelReached() {
        return level >= maxLevel;
    }

    public long experienceRemaining() {
        return maxLevelReached() ? 0L : Math.max(0L, nextLevelCost - experience);
    }

    public boolean mutationMaximumReached() {
        return mutationMaximum > 0 && mutationStage >= mutationMaximum;
    }

    private static Evolution evolution(final boolean ghoul, final boolean demon,
                                       final int effectiveLevel, final int mutationPower,
                                       final int tier2, final int tier3, final String originalType) {
        if (!ghoul && !demon) {
            return new Evolution(originalType, entityName(originalType), "", OptionalInt.empty());
        }
        if (effectiveLevel >= tier3) {
            return ghoul
                    ? new Evolution("ZOGLIN", "Förtelem", "", OptionalInt.empty())
                    : new Evolution("MAGMA_CUBE", "Magma-behemót", "", OptionalInt.empty());
        }
        if (effectiveLevel >= tier2) {
            return ghoul
                    ? new Evolution("WITHER_SKELETON", "Csontszolga", "Förtelem",
                    OptionalInt.of(Math.max(1, tier3 - mutationPower)))
                    : new Evolution("BLAZE", "Tűz-démon", "Magma-behemót",
                    OptionalInt.of(tier3));
        }
        return ghoul
                ? new Evolution("HUSK", "Ghúl", "Csontszolga",
                OptionalInt.of(Math.max(1, tier2 - mutationPower)))
                : new Evolution("VEX", "Imp", "Tűz-démon", OptionalInt.of(tier2));
    }

    private static long levelCost(final int level, final int baseXp, final int increment) {
        try {
            return Math.addExact(Math.max(1, baseXp),
                    Math.multiplyExact(Math.max(0L, (long) level - 1L), Math.max(0, increment)));
        } catch (final ArithmeticException overflow) {
            return Long.MAX_VALUE;
        }
    }

    private static String roleName(final String namespace) {
        return switch (namespace) {
            case "beast_master.stable" -> "Vadmester istálló";
            case "necromancer.court" -> "Holtak Udvara";
            case UNHOLY_GHOUL -> "Szentségtelen ghúl";
            case DEMON_ROSTER -> "Démoni névsor";
            default -> "Társ";
        };
    }

    private static String defaultName(final String namespace) {
        return switch (namespace) {
            case UNHOLY_GHOUL -> "Ghúl";
            case DEMON_ROSTER -> "Démon";
            case "necromancer.court" -> "Udvaronc";
            default -> "Társ";
        };
    }

    private static String entityName(final String raw) {
        final String key = raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
        final String translated = Map.ofEntries(
                Map.entry("WOLF", "Farkas"), Map.entry("CAT", "Macska"),
                Map.entry("FOX", "Róka"), Map.entry("HORSE", "Ló"),
                Map.entry("PARROT", "Papagáj"), Map.entry("RABBIT", "Nyúl"),
                Map.entry("ZOMBIE", "Zombi"), Map.entry("HUSK", "Ghúl"),
                Map.entry("SKELETON", "Csontváz"), Map.entry("WITHER_SKELETON", "Wither-csontváz"),
                Map.entry("ZOGLIN", "Zoglin"), Map.entry("VEX", "Vex"),
                Map.entry("BLAZE", "Lánglény"), Map.entry("MAGMA_CUBE", "Magmakocka")
        ).get(key);
        if (translated != null) return translated;
        if (key.isBlank()) return "Ismeretlen társ";
        final String[] words = key.toLowerCase(Locale.ROOT).split("_");
        final StringBuilder result = new StringBuilder();
        for (final String word : words) {
            if (word.isBlank()) continue;
            if (!result.isEmpty()) result.append(' ');
            result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return result.toString();
    }

    private static int parseNonNegativeInt(final String raw) {
        if (raw == null || raw.isBlank()) return 0;
        try {
            return Math.max(0, Integer.parseInt(raw));
        } catch (final NumberFormatException invalid) {
            return 0;
        }
    }

    private static int addBounded(final int left, final int right) {
        try {
            return Math.addExact(left, right);
        } catch (final ArithmeticException overflow) {
            return Integer.MAX_VALUE;
        }
    }

    private static int multiplyBounded(final int left, final int right) {
        try {
            return Math.multiplyExact(left, right);
        } catch (final ArithmeticException overflow) {
            return Integer.MAX_VALUE;
        }
    }

    private static String required(final String value, final String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is blank");
        return value;
    }

    private record Evolution(String entityType, String currentName, String nextName,
                             OptionalInt nextBaseLevel) { }
}
