package hu.taliann.icesmp.professions;

import hu.taliann.icesmp.data.ProfessionType;
import hu.taliann.icesmp.managers.ConfigManager;
import hu.taliann.icesmp.utils.ConfigMaterialResolver;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;

/** Canonical stable-id registry for stackable profession economy materials. */
public final class ProfessionMaterialRegistry {

    public enum Tier { COMMON, REFINED, RARE, SPECIAL, BOSS }
    public enum ProcessingState { RAW, REFINED, COMPONENT, SERVICE, SALVAGE }

    public record Definition(String id, Material icon, String displayName, Tier tier,
                             ProcessingState processingState, ProfessionType primaryProfession,
                             List<String> sourceTypes, List<String> sinkTypes,
                             boolean economyManaged) {
        public Definition {
            id = normalize(id);
            if (icon == null || icon.isAir()) throw new IllegalArgumentException("invalid material icon: " + id);
            displayName = displayName == null || displayName.isBlank() ? id : displayName.trim();
            tier = tier == null ? Tier.COMMON : tier;
            processingState = processingState == null ? ProcessingState.RAW : processingState;
            sourceTypes = normalized(sourceTypes);
            sinkTypes = normalized(sinkTypes);
            if (economyManaged && sourceTypes.isEmpty()) {
                throw new IllegalArgumentException("economy-managed material has no source: " + id);
            }
            if (economyManaged && sinkTypes.isEmpty()) {
                throw new IllegalArgumentException("economy-managed material has no sink: " + id);
            }
        }
    }

    private final ConfigManager configManager;
    private volatile Map<String, Definition> definitions = Map.of();

    public ProfessionMaterialRegistry(final ConfigManager configManager) {
        this.configManager = java.util.Objects.requireNonNull(configManager, "configManager");
    }

    /** Parse/validate privately, then atomically publish one immutable generation. */
    public synchronized void load() {
        final ConfigurationSection root = configManager.getConfiguration() == null ? null
                : configManager.getConfiguration().getConfigurationSection("profession-materials");
        if (root == null) {
            definitions = Map.of();
            return;
        }
        final LinkedHashMap<String, Definition> next = new LinkedHashMap<>();
        for (final String rawId : new TreeSet<>(root.getKeys(false))) {
            final String id = normalize(rawId);
            final ConfigurationSection section = root.getConfigurationSection(rawId);
            if (section == null) continue;
            final Material icon = ConfigMaterialResolver.match(section.getString("material", ""));
            if (icon == null || icon.isAir()) {
                throw new IllegalStateException("profession-materials." + id + ": invalid icon material");
            }
            final Tier tier = enumValue(Tier.class, section.getString("tier", "common"), id, "tier");
            final ProcessingState state = enumValue(ProcessingState.class,
                    section.getString("processing-state", "raw"), id, "processing-state");
            final ProfessionType owner = ProfessionType.fromId(section.getString("primary-profession", ""));
            final Definition definition = new Definition(id, icon,
                    stripLegacy(section.getString("display-name", id)), tier, state, owner,
                    section.getStringList("source-types"), section.getStringList("sink-types"),
                    section.getBoolean("economy-managed", false));
            if (next.putIfAbsent(id, definition) != null) {
                throw new IllegalStateException("duplicate profession material id: " + id);
            }
        }
        definitions = Collections.unmodifiableMap(next);
    }

    public Optional<Definition> find(final String id) {
        return Optional.ofNullable(definitions.get(normalizeNullable(id)));
    }

    public Definition require(final String id) {
        return find(id).orElseThrow(() -> new IllegalArgumentException("unknown profession material: " + id));
    }

    public boolean isDefined(final String id) {
        return find(id).isPresent();
    }

    public Map<String, Definition> all() {
        return definitions;
    }

    private static <E extends Enum<E>> E enumValue(final Class<E> type, final String raw,
                                                    final String id, final String field) {
        try {
            return Enum.valueOf(type, raw.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (final RuntimeException invalid) {
            throw new IllegalStateException("profession-materials." + id + ": invalid " + field + ": " + raw,
                    invalid);
        }
    }

    private static List<String> normalized(final List<String> raw) {
        if (raw == null || raw.isEmpty()) return List.of();
        return raw.stream().filter(java.util.Objects::nonNull).map(ProfessionMaterialRegistry::normalize)
                .distinct().sorted().toList();
    }

    private static String stripLegacy(final String raw) {
        return raw == null ? "" : raw.replaceAll("&[0-9a-fk-orA-FK-OR]", "").trim();
    }

    private static String normalize(final String raw) {
        if (raw == null || raw.isBlank()) throw new IllegalArgumentException("blank profession material id");
        return raw.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
    }

    private static String normalizeNullable(final String raw) {
        return raw == null || raw.isBlank() ? "" : raw.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
    }
}
