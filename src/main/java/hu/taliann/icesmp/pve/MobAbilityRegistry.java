package hu.taliann.icesmp.pve;

import hu.taliann.icesmp.managers.ConfigManager;
import org.bukkit.configuration.ConfigurationSection;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

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
            final MobAbilityDefinition definition = new MobAbilityDefinition(id,
                    MobAbilityDefinition.parseKind(section.getString("kind", "")),
                    section.getLong("cooldown-ticks"), section.getLong("telegraph-ticks"),
                    section.getLong("recovery-ticks", 0L),
                    section.getDouble("radius"), section.getDouble("power"),
                    section.getInt("max-summons", 0),
                    MobAbilityDefinition.TargetRule.parse(section.getString("target-rule", "CURRENT_TARGET")),
                    section.getBoolean("interruptible", false),
                    enums(section.getStringList("eligible-ranks"), MobRank.class, "rank", id),
                    enums(section.getStringList("eligible-archetypes"), MobArchetype.class, "archetype", id),
                    tuning);
            parsed.put(id, definition);
        }
        if (parsed.size() < 4 || parsed.size() > 64) {
            throw new IllegalStateException("mob ability registry must contain 4-64 entries");
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
}
