package hu.taliann.icesmp.itemization;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Immutable secondary indexes over the canonical {@link ItemTemplateRegistry} snapshot.
 *
 * <p>The registry remains the only authority. This class only memoizes source/family/slot/
 * profession lookups so a 160+ item production catalog is never scanned on every mob death or UI
 * interaction. A registry reload publishes a new immutable map identity, which causes one bounded
 * rebuild on the next lookup.</p>
 */
public final class ItemTemplateCatalogIndex {

    private record Snapshot(Map<String, List<ItemTemplate>> bySource,
                            Map<ArmorFamily, List<ItemTemplate>> byFamily,
                            Map<ItemTemplate.Slot, List<ItemTemplate>> bySlot,
                            Map<String, List<ItemTemplate>> byProfession) {
        private static Snapshot empty() {
            return new Snapshot(Map.of(), Map.of(), Map.of(), Map.of());
        }
    }

    private final ItemTemplateRegistry registry;
    private volatile Map<String, ItemTemplate> indexedRegistrySnapshot = Map.of();
    private volatile Snapshot snapshot = Snapshot.empty();

    public ItemTemplateCatalogIndex(final ItemTemplateRegistry registry) {
        this.registry = java.util.Objects.requireNonNull(registry, "registry");
    }

    public List<ItemTemplate> byAnySource(final Set<String> sourceTags) {
        if (sourceTags == null || sourceTags.isEmpty()) return List.of();
        final Snapshot current = current();
        final LinkedHashSet<ItemTemplate> result = new LinkedHashSet<>();
        for (final String raw : sourceTags) {
            if (raw == null || raw.isBlank()) continue;
            result.addAll(current.bySource().getOrDefault(normalize(raw), List.of()));
        }
        return List.copyOf(result);
    }

    public List<ItemTemplate> byFamily(final ArmorFamily family) {
        return family == null ? List.of() : current().byFamily().getOrDefault(family, List.of());
    }

    public List<ItemTemplate> bySlot(final ItemTemplate.Slot slot) {
        return slot == null ? List.of() : current().bySlot().getOrDefault(slot, List.of());
    }

    public List<ItemTemplate> byProfession(final String professionId) {
        if (professionId == null || professionId.isBlank()) return List.of();
        return current().byProfession().getOrDefault(normalize(professionId), List.of());
    }

    private Snapshot current() {
        final Map<String, ItemTemplate> authoritative = registry.snapshot();
        if (authoritative == indexedRegistrySnapshot) return snapshot;
        return rebuild(authoritative);
    }

    private synchronized Snapshot rebuild(final Map<String, ItemTemplate> authoritative) {
        if (authoritative == indexedRegistrySnapshot) return snapshot;
        final Map<String, List<ItemTemplate>> bySource = new LinkedHashMap<>();
        final Map<ArmorFamily, List<ItemTemplate>> byFamily = new EnumMap<>(ArmorFamily.class);
        final Map<ItemTemplate.Slot, List<ItemTemplate>> bySlot = new EnumMap<>(ItemTemplate.Slot.class);
        final Map<String, List<ItemTemplate>> byProfession = new LinkedHashMap<>();
        for (final ItemTemplate template : authoritative.values()) {
            for (final String source : template.sourceTags()) add(bySource, normalize(source), template);
            if (template.armorFamily() != null) add(byFamily, template.armorFamily(), template);
            add(bySlot, template.slot(), template);
            final String profession = template.encounterMetadata().get("catalog-profession");
            if (profession != null && !profession.isBlank()) {
                add(byProfession, normalize(profession), template);
            }
        }
        final Snapshot built = new Snapshot(freeze(bySource), freeze(byFamily), freeze(bySlot),
                freeze(byProfession));
        indexedRegistrySnapshot = authoritative;
        snapshot = built;
        return built;
    }

    private static String normalize(final String raw) {
        return raw.trim().toLowerCase(Locale.ROOT);
    }

    private static <K> void add(final Map<K, List<ItemTemplate>> target, final K key,
                                final ItemTemplate template) {
        target.computeIfAbsent(key, ignored -> new ArrayList<>()).add(template);
    }

    private static <K> Map<K, List<ItemTemplate>> freeze(final Map<K, List<ItemTemplate>> source) {
        final LinkedHashMap<K, List<ItemTemplate>> result = new LinkedHashMap<>();
        source.forEach((key, value) -> result.put(key, List.copyOf(value)));
        return Map.copyOf(result);
    }
}
