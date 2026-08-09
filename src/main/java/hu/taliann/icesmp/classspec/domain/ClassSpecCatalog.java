package hu.taliann.icesmp.classspec.domain;

import java.text.Normalizer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Canonical stable 13-class/35-specialization identity catalogue. */
public final class ClassSpecCatalog {

    private static final Map<String, List<String>> SPECIALIZATIONS;
    private static final Map<String, String> PARENTS;
    private static final Map<String, String> COMPANION_NAMESPACES = Map.of(
            "beast_master", "beast_master.stable",
            "necromancer", "necromancer.court",
            "unholy", "unholy.ghoul",
            "demonologist", "demonologist.roster");

    static {
        final Map<String, List<String>> classes = new LinkedHashMap<>();
        classes.put("wizard", List.of("elementalist", "necromancer"));
        classes.put("warrior", List.of("berserker", "guardian"));
        classes.put("archer", List.of("sharpshooter", "beast_master"));
        classes.put("assassin", List.of("poisoner", "phantom", "plaguebringer"));
        classes.put("druid", List.of("feral", "lunar", "ironbark", "restoration"));
        classes.put("paladin", List.of("holy", "retribution", "protection"));
        classes.put("death_knight", List.of("blood", "frost", "unholy"));
        classes.put("shaman", List.of("elemental", "enhancement", "tidal"));
        classes.put("monk", List.of("windwalker", "brewmaster", "mistweaver"));
        classes.put("priest", List.of("discipline", "bone_priest", "shadow"));
        classes.put("warlock", List.of("affliction", "destruction", "demonologist"));
        classes.put("demon_hunter", List.of("havoc", "vengeance"));
        classes.put("evoker", List.of("devastation", "preservation"));
        SPECIALIZATIONS = Map.copyOf(classes);

        final Map<String, String> parents = new LinkedHashMap<>();
        classes.forEach((classId, specs) -> specs.forEach(specId -> parents.put(specId, classId)));
        PARENTS = Map.copyOf(parents);
    }

    private ClassSpecCatalog() {
    }

    public static Set<String> classIds() {
        return SPECIALIZATIONS.keySet();
    }

    public static Set<String> specializationIds() {
        return PARENTS.keySet();
    }

    public static boolean isKnownClass(final String classId) {
        return SPECIALIZATIONS.containsKey(normalize(classId));
    }

    public static boolean isKnownSpecialization(final String specializationId) {
        return PARENTS.containsKey(normalize(specializationId));
    }

    public static boolean belongsTo(final String specializationId, final String classId) {
        return normalize(classId).equals(PARENTS.get(normalize(specializationId)));
    }

    public static String parentOf(final String specializationId) {
        return PARENTS.get(normalize(specializationId));
    }

    public static String companionNamespace(final String specializationId) {
        return COMPANION_NAMESPACES.get(normalize(specializationId));
    }

    /**
     * The one companion projection every gameplay runtime reads.
     *
     * <p>A companion roster is durable Profile v2 state and has no runtime twin; a runtime may only
     * ever look at it through this rule. A roster becomes visible solely through the ACTIVE loadout
     * that owns its namespace, so a sealed, inactive or foreign loadout projects nothing while its
     * durable entries stay untouched. The projection is therefore reconstructible from the profile
     * alone — relog, spec switch and seal need no transient state to be kept in step.</p>
     */
    public static List<CompanionProfile> companionProjection(final ClassLoadout loadout,
                                                             final String namespace) {
        if (loadout == null || loadout.status() != LoadoutStatus.ACTIVE) return List.of();
        final String owner = companionNamespace(loadout.specializationId());
        if (owner == null || !owner.equals(namespace)) return List.of();
        return List.copyOf(loadout.companionRoster().values());
    }

    /** NFKC + Locale.ROOT normalization used by domain maps and duplicate detection. */
    public static String normalize(final String value) {
        if (value == null) {
            return "";
        }
        return Normalizer.normalize(value.trim(), Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
    }
}
