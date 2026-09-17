package hu.taliann.icesmp.ux;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Semantic, presentation-only tooltip composition.
 *
 * <p>Sections are rebuilt from canonical state and a player-aware context. This class deliberately
 * has no persistence or PDC mutation API: generated presentation cannot become item authority.</p>
 */
public final class TooltipEngine {

    public enum SectionId {
        HEADER, TYPE, PRIMARY_STATS, REQUIREMENTS, EFFECTS, EQUIPMENT,
        SOCKETS, ARCHAEOLOGY, PROVENANCE, STORY, FLAVOR, DEBUG, CUSTOM
    }

    public record Context(Player player, ItemStack canonicalItem, Map<String, Object> values) {
        public Context {
            values = values == null ? Map.of() : Map.copyOf(values);
        }
    }

    public record Section(SectionId id, int order, List<Component> lines) {
        public Section {
            Objects.requireNonNull(id, "id");
            if (order < 0) throw new IllegalArgumentException("order must be non-negative");
            lines = lines == null ? List.of() : List.copyOf(lines);
        }

        public static Section of(final SectionId id, final int order, final Collection<Component> lines) {
            return new Section(id, order, lines == null ? List.of() : List.copyOf(lines));
        }
    }

    private TooltipEngine() {
    }

    public static Section generated(final SectionId id, final int order,
                                    final Collection<Component> lines) {
        return Section.of(id, order, lines);
    }

    /**
     * Deterministically orders sections, replaces duplicate section identities and removes
     * repeated component lines while preserving authored component values.
     */
    public static List<Component> render(final Collection<Section> sections) {
        if (sections == null || sections.isEmpty()) return List.of();
        final Map<SectionId, Section> byId = new LinkedHashMap<>();
        for (final Section section : sections) {
            if (section == null || section.lines().isEmpty()) continue;
            byId.put(section.id(), section);
        }
        final List<Section> ordered = new ArrayList<>(byId.values());
        ordered.sort(Comparator.comparingInt(Section::order)
                .thenComparing(section -> section.id().name()));
        final List<Component> result = new ArrayList<>();
        for (final Section section : ordered) {
            for (final Component line : section.lines()) {
                if (line != null && !result.contains(line)) result.add(line);
            }
        }
        return List.copyOf(result);
    }

    public static List<Component> replace(final Collection<Section> sections,
                                          final Section replacement) {
        final List<Section> retained = new ArrayList<>();
        if (sections != null) {
            for (final Section section : sections) {
                if (section != null && section.id() != replacement.id()) retained.add(section);
            }
        }
        retained.add(replacement);
        return render(retained);
    }
}
