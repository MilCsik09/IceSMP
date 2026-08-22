package hu.taliann.icesmp.pve;

import hu.taliann.icesmp.managers.ConfigManager;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EntityType;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Startup-built canonical matrix for every Paper 1.21.11 living spawn type. */
public final class CreatureSpeciesRegistry {
    private final ConfigManager config;
    private final MobAbilityRegistry abilities;
    private volatile Map<EntityType, CreatureSpeciesPolicy> policies = Map.of();

    public CreatureSpeciesRegistry(final ConfigManager config, final MobAbilityRegistry abilities) {
        this.config = Objects.requireNonNull(config, "config");
        this.abilities = Objects.requireNonNull(abilities, "abilities");
    }

    public synchronized void load() {
        final ConfigurationSection root = config.getConfiguration()
                .getConfigurationSection("creature-species");
        if (root == null) throw new IllegalStateException("creature-species config section missing");
        final EnumMap<EntityType, CreatureSpeciesPolicy> parsed = new EnumMap<>(EntityType.class);
        for (final String rawType : root.getKeys(false)) {
            final EntityType type = parse(EntityType.class, rawType, "entity type");
            if (!supportedLivingTypes().contains(type)) {
                throw new IllegalStateException("creature species is not a supported living spawn type: " + type);
            }
            if (parsed.containsKey(type)) throw new IllegalStateException("duplicate creature species: " + type);
            final ConfigurationSection section = root.getConfigurationSection(rawType);
            if (section == null) throw new IllegalStateException("invalid creature species section: " + type);

            final Set<CreatureSpeciesPolicy.Temperament> allowed = enums(
                    section.getStringList("temperament.allowed"), CreatureSpeciesPolicy.Temperament.class);
            final EnumMap<CreatureSpeciesPolicy.Temperament, Integer> weights =
                    new EnumMap<>(CreatureSpeciesPolicy.Temperament.class);
            final EnumMap<CreatureSpeciesPolicy.Temperament, Double> fight =
                    new EnumMap<>(CreatureSpeciesPolicy.Temperament.class);
            for (final CreatureSpeciesPolicy.Temperament temperament : allowed) {
                weights.put(temperament, section.getInt("temperament.weights."
                        + temperament.name(), 0));
                fight.put(temperament, section.getDouble("temperament.fight-percent."
                        + temperament.name(), 0.0D));
            }
            final String fleeTechnique = normalize(section.getString(
                    "reaction.flee-technique", ""));
            if (!fleeTechnique.isBlank()) abilities.require(fleeTechnique);
            final CreatureSpeciesPolicy.TemperamentPolicy temperamentPolicy =
                    new CreatureSpeciesPolicy.TemperamentPolicy(weights, fight,
                            section.getBoolean("reaction.warning-before-fight", false),
                            section.getLong("reaction.combat-ticks", 240L), fleeTechnique);

            final ArrayList<String> techniques = new ArrayList<>(
                    normalizedIds(section.getStringList("techniques")));
            if (!fleeTechnique.isBlank() && !techniques.contains(fleeTechnique)) {
                techniques.add(fleeTechnique);
            }
            techniques.forEach(abilities::require);
            final EnumMap<MobRank, List<String>> rankTechniques = new EnumMap<>(MobRank.class);
            final ConfigurationSection rankRoot = section.getConfigurationSection("rank-techniques");
            if (rankRoot != null) {
                for (final String rawRank : rankRoot.getKeys(false)) {
                    final MobRank rank = parse(MobRank.class, rawRank, "rank");
                    final List<String> ids = normalizedIds(rankRoot.getStringList(rawRank));
                    ids.forEach(abilities::require);
                    rankTechniques.put(rank, ids);
                }
            }

            final Set<CreatureSpeciesPolicy.Temperament> socialTemperaments = enums(
                    section.getStringList("social.required-temperaments"),
                    CreatureSpeciesPolicy.Temperament.class);
            if (!allowed.containsAll(socialTemperaments)) {
                throw new IllegalStateException("social temperament escapes species policy: " + type);
            }
            final CreatureSpeciesPolicy.SocialPolicy social = new CreatureSpeciesPolicy.SocialPolicy(
                    parse(CreatureSpeciesPolicy.SocialRelation.class,
                            section.getString("social.relation", "NONE"), "social relation"),
                    section.getDouble("social.radius", 0.0D),
                    section.getInt("social.maximum-assistants", 0), socialTemperaments,
                    section.getLong("social.cooldown-ticks", 200L),
                    section.getInt("social.maximum-candidates", 0));

            final CreatureSpeciesPolicy policy = new CreatureSpeciesPolicy(type.name(),
                    parse(CreatureSpeciesPolicy.Category.class,
                            section.getString("category", "CIVILIAN"), "category"),
                    parse(CreatureSpeciesPolicy.Disposition.class,
                            section.getString("disposition", "NON_COMBAT"), "disposition"),
                    section.getBoolean("level-enabled", false),
                    section.getBoolean("rank-enabled", false),
                    parse(MobArchetype.class, section.getString("archetype", "BRUISER"), "archetype"),
                    allowed, temperamentPolicy,
                    parse(CreatureSpeciesPolicy.ProvocationPolicy.class,
                            section.getString("provocation", "NONE"), "provocation"),
                    techniques, rankTechniques, social,
                    parse(CreatureSpeciesPolicy.RewardProfile.class,
                            section.getString("reward-profile", "VANILLA_ONLY"), "reward profile"),
                    parse(CreatureSpeciesPolicy.BabyPolicy.class,
                            section.getString("baby-policy", "IDENTITY_ONLY"), "baby policy"),
                    parse(CreatureSpeciesPolicy.TamePolicy.class,
                            section.getString("tame-policy", "NOT_TAMEABLE"), "tame policy"));
            parsed.put(type, policy);
        }

        final Set<EntityType> expected = supportedLivingTypes();
        final EnumSet<EntityType> missing = EnumSet.copyOf(expected);
        missing.removeAll(parsed.keySet());
        if (!missing.isEmpty()) {
            throw new IllegalStateException("creature species matrix missing Paper living types: " + missing);
        }
        if (parsed.size() != expected.size()) {
            throw new IllegalStateException("creature species matrix contains unexpected types");
        }
        policies = Map.copyOf(parsed);
    }

    public Optional<CreatureSpeciesPolicy> find(final EntityType type) {
        return type == null ? Optional.empty() : Optional.ofNullable(policies.get(type));
    }

    /** Runtime lookup is deliberately non-aggressive when startup validation was bypassed. */
    public CreatureSpeciesPolicy profile(final EntityType type) {
        return find(type).orElseGet(() -> CreatureSpeciesPolicy.nonCombatFallback(
                type == null ? "unknown" : type.name()));
    }

    public Map<EntityType, CreatureSpeciesPolicy> all() { return policies; }

    public static Set<EntityType> supportedLivingTypes() {
        final EnumSet<EntityType> result = EnumSet.noneOf(EntityType.class);
        for (final EntityType type : EntityType.values()) {
            if (type != EntityType.PLAYER && type.isAlive() && type.isSpawnable()) result.add(type);
        }
        return Set.copyOf(result);
    }

    private static List<String> normalizedIds(final List<String> raw) {
        if (raw == null || raw.isEmpty()) return List.of();
        final LinkedHashSet<String> ids = new LinkedHashSet<>();
        for (final String value : raw) ids.add(normalize(value));
        if (ids.contains("") || ids.size() != raw.size() || ids.size() > 12) {
            throw new IllegalStateException("invalid creature technique list");
        }
        return List.copyOf(ids);
    }

    private static <E extends Enum<E>> Set<E> enums(final List<String> raw, final Class<E> type) {
        if (raw == null || raw.isEmpty()) return Set.of();
        final LinkedHashSet<E> values = new LinkedHashSet<>();
        for (final String value : raw) values.add(parse(type, value, type.getSimpleName()));
        if (values.size() != raw.size()) throw new IllegalStateException("duplicate enum value");
        return Set.copyOf(values);
    }

    private static <E extends Enum<E>> E parse(final Class<E> type, final String raw,
                                                final String field) {
        try {
            return Enum.valueOf(type, Objects.requireNonNullElse(raw, "").trim()
                    .toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (final IllegalArgumentException invalid) {
            throw new IllegalStateException("invalid creature " + field + ": " + raw, invalid);
        }
    }

    private static String normalize(final String raw) {
        return raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT).replace('-', '_');
    }
}
