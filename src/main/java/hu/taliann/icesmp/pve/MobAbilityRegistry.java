package hu.taliann.icesmp.pve;

import hu.taliann.icesmp.managers.ConfigManager;
import org.bukkit.configuration.ConfigurationSection;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.List;
import java.util.ArrayList;

/** Atomically published, config-backed canonical mob ability registry. */
public final class MobAbilityRegistry {
    private final ConfigManager config;
    private volatile Map<String, MobAbilityDefinition> abilities = Map.of();

    public MobAbilityRegistry(final ConfigManager config) {
        this.config = Objects.requireNonNull(config, "config");
    }

    public synchronized void load() {
        final ConfigurationSection root = config.getConfiguration()
                .getConfigurationSection("mob-abilities");
        if (root == null) throw new IllegalStateException("mob-abilities config section missing");
        final LinkedHashMap<String, MobAbilityDefinition> parsed = new LinkedHashMap<>();
        for (final String rawId : root.getKeys(false)) {
            final String id = normalize(rawId);
            if (parsed.containsKey(id)) throw new IllegalStateException("duplicate mob ability: " + id);
            final ConfigurationSection section = root.getConfigurationSection(rawId);
            if (section == null) throw new IllegalStateException("invalid mob ability section: " + id);
            final LinkedHashMap<String, Double> tuning = new LinkedHashMap<>();
            final ConfigurationSection tuningSection = section.getConfigurationSection("tuning");
            if (tuningSection != null) {
                for (final String key : tuningSection.getKeys(false)) {
                    final Object raw = tuningSection.get(key);
                    if (!(raw instanceof Number value)) {
                        throw new IllegalStateException("non-numeric mob ability tuning: " + id + '/' + key);
                    }
                    tuning.put(key, value.doubleValue());
                }
            }
            final MobAbilityDefinition.Kind kind = MobAbilityDefinition.parseKind(
                    section.getString("kind", ""));
            final MobAbilityDefinition.Presentation presentation = presentation(
                    section.getConfigurationSection("presentation"), kind);
            validatePresentation(presentation, id);
            final MobAbilityDefinition definition = new MobAbilityDefinition(id,
                    kind,
                    section.getLong("cooldown-ticks"), section.getLong("telegraph-ticks"),
                    section.getLong("recovery-ticks", 0L),
                    section.getDouble("radius"), section.getDouble("power"),
                    section.getInt("max-summons", 0),
                    MobAbilityDefinition.TargetRule.parse(section.getString("target-rule", "CURRENT_TARGET")),
                    section.getBoolean("interruptible", false),
                    enums(section.getStringList("eligible-ranks"), MobRank.class, "rank", id),
                    enums(section.getStringList("eligible-archetypes"), MobArchetype.class, "archetype", id),
                    tuning,
                    triggers(section.getStringList("triggers"), id),
                    conditions(section.getMapList("conditions"), id),
                    actions(section.getMapList("actions"), id), presentation);
            parsed.put(id, definition);
        }
        if (parsed.size() < 4 || parsed.size() > 128) {
            throw new IllegalStateException("mob ability registry must contain 4-128 entries");
        }
        abilities = Map.copyOf(parsed);
    }

    public Optional<MobAbilityDefinition> find(final String abilityId) {
        if (abilityId == null || abilityId.isBlank()) return Optional.empty();
        return Optional.ofNullable(abilities.get(normalize(abilityId)));
    }

    public MobAbilityDefinition require(final String abilityId) {
        return find(abilityId).orElseThrow(() ->
                new IllegalArgumentException("unknown mob ability: " + abilityId));
    }

    public Map<String, MobAbilityDefinition> all() { return abilities; }

    private static MobAbilityDefinition.Presentation presentation(
            final ConfigurationSection section, final MobAbilityDefinition.Kind kind) {
        final MobAbilityDefinition.Presentation fallback =
                MobAbilityDefinition.Presentation.defaults(kind);
        if (section == null) return fallback;
        return new MobAbilityDefinition.Presentation(
                section.getString("telegraph-particle", fallback.telegraphParticle()),
                section.getString("telegraph-sound", fallback.telegraphSound()),
                section.getString("impact-particle", fallback.impactParticle()),
                section.getString("impact-sound", fallback.impactSound()),
                section.getInt("particle-count", fallback.particleCount()),
                (float) section.getDouble("volume", fallback.volume()),
                (float) section.getDouble("pitch", fallback.pitch()));
    }

    private static void validatePresentation(final MobAbilityDefinition.Presentation value,
                                             final String abilityId) {
        try {
            org.bukkit.Particle.valueOf(value.telegraphParticle().toUpperCase(Locale.ROOT));
            org.bukkit.Particle.valueOf(value.impactParticle().toUpperCase(Locale.ROOT));
        } catch (final IllegalArgumentException invalid) {
            throw new IllegalStateException("unknown ability particle: " + abilityId, invalid);
        }
        if (org.bukkit.Registry.SOUNDS.get(org.bukkit.NamespacedKey.minecraft(
                value.telegraphSound())) == null
                || org.bukkit.Registry.SOUNDS.get(org.bukkit.NamespacedKey.minecraft(
                value.impactSound())) == null) {
            throw new IllegalStateException("unknown ability sound: " + abilityId);
        }
    }

    private static String normalize(final String raw) {
        return raw.trim().toLowerCase(Locale.ROOT);
    }

    private static <E extends Enum<E>> Set<E> enums(final java.util.List<String> values,
                                                     final Class<E> type,
                                                     final String field,
                                                     final String abilityId) {
        if (values == null || values.isEmpty()) return Set.of();
        final java.util.LinkedHashSet<E> result = new java.util.LinkedHashSet<>();
        for (final String raw : values) {
            try {
                result.add(Enum.valueOf(type, raw.trim().toUpperCase(Locale.ROOT).replace('-', '_')));
            } catch (final IllegalArgumentException invalid) {
                throw new IllegalStateException("unknown ability " + field + ": " + abilityId + '/' + raw,
                        invalid);
            }
        }
        return Set.copyOf(result);
    }

    private static Set<MobAbilityDefinition.Trigger> triggers(final List<String> values,
                                                               final String abilityId) {
        if (values == null || values.isEmpty()) return Set.of(MobAbilityDefinition.Trigger.ON_TIMER);
        final java.util.LinkedHashSet<MobAbilityDefinition.Trigger> result = new java.util.LinkedHashSet<>();
        for (final String raw : values) {
            try {
                result.add(MobAbilityDefinition.Trigger.parse(raw));
            } catch (final IllegalArgumentException invalid) {
                throw new IllegalStateException("unknown ability trigger: " + abilityId + '/' + raw, invalid);
            }
        }
        if (result.size() != values.size()) throw new IllegalStateException("duplicate ability trigger: " + abilityId);
        return Set.copyOf(result);
    }

    private static List<MobTechniqueCondition> conditions(final List<Map<?, ?>> values,
                                                           final String abilityId) {
        if (values == null || values.isEmpty()) return List.of();
        final ArrayList<MobTechniqueCondition> result = new ArrayList<>();
        for (final Map<?, ?> value : values) {
            try {
                final MobTechniqueCondition.Type type = MobTechniqueCondition.parseType(
                        String.valueOf(value.get("type")));
                final Object raw = value.get("value");
                final double parameter = raw instanceof Number number ? number.doubleValue() : 0.0D;
                result.add(new MobTechniqueCondition(type, parameter));
            } catch (final RuntimeException invalid) {
                throw new IllegalStateException("invalid ability condition: " + abilityId, invalid);
            }
        }
        return List.copyOf(result);
    }

    private static List<MobTechniqueAction> actions(final List<Map<?, ?>> values,
                                                     final String abilityId) {
        if (values == null || values.isEmpty()) return List.of();
        final ArrayList<MobTechniqueAction> result = new ArrayList<>();
        for (final Map<?, ?> value : values) {
            try {
                final MobTechniqueAction.Type type = MobTechniqueAction.Type.valueOf(
                        String.valueOf(value.get("type")).trim().toUpperCase(Locale.ROOT).replace('-', '_'));
                final Object targetRaw = value.get("target");
                final MobTechniqueAction.Target target = targetRaw == null
                        ? MobTechniqueAction.Target.CURRENT_TARGET
                        : MobTechniqueAction.Target.valueOf(String.valueOf(targetRaw).trim()
                        .toUpperCase(Locale.ROOT).replace('-', '_'));
                final LinkedHashMap<String, Double> parameters = new LinkedHashMap<>();
                final Object rawParameters = value.get("parameters");
                if (rawParameters instanceof Map<?, ?> parameterMap) {
                    for (final var entry : parameterMap.entrySet()) {
                        if (!(entry.getValue() instanceof Number number)) {
                            throw new IllegalArgumentException("non-numeric action parameter");
                        }
                        parameters.put(String.valueOf(entry.getKey()), number.doubleValue());
                    }
                }
                result.add(new MobTechniqueAction(type, target, parameters,
                        value.get("reference") == null ? "" : String.valueOf(value.get("reference"))));
            } catch (final RuntimeException invalid) {
                throw new IllegalStateException("invalid ability action: " + abilityId, invalid);
            }
        }
        return List.copyOf(result);
    }
}
