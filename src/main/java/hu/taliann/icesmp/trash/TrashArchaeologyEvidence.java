package hu.taliann.icesmp.trash;

import java.util.*;
import org.bukkit.configuration.ConfigurationSection;

/** Physical evidence is authored independently of the technical item carrier and loot ecology. */
public record TrashArchaeologyEvidence(String family, String domain, String material,
                                      List<String> facts, String discrepancy) {
    public TrashArchaeologyEvidence {
        Objects.requireNonNull(family); Objects.requireNonNull(domain); Objects.requireNonNull(material);
        facts = List.copyOf(facts); discrepancy = discrepancy == null ? "" : discrepancy;
    }
    static Map<String, TrashArchaeologyEvidence> parse(ConfigurationSection root, Map<String, TrashDefinition> definitions) {
        if (root == null) throw new IllegalStateException("hiányzó authored Archaeology evidence");
        final var result = new LinkedHashMap<String, TrashArchaeologyEvidence>();
        final var groups = Objects.requireNonNull(root.getConfigurationSection("physical-families"));
        for (String family : groups.getKeys(false)) {
            for (String id : groups.getStringList(family + ".items")) {
                if (definitions.containsKey(id) && definitions.get(id).internalKind() == TrashKind.STORY)
                    throw new IllegalStateException("a story tárgyhoz saját tények szükségesek: " + id);
                put(result, definitions, id, new TrashArchaeologyEvidence(family, "everyday",
                        required(groups.getString(family + ".observation")), List.of(), ""));
            }
        }
        final var stories = Objects.requireNonNull(root.getConfigurationSection("stories"));
        for (String id : stories.getKeys(false)) {
            final var section = Objects.requireNonNull(stories.getConfigurationSection(id));
            final String family = required(section.getString("family"));
            final List<String> facts = section.getStringList("facts");
            if (!definitions.containsKey(id) || definitions.get(id).internalKind() != TrashKind.STORY
                    || facts.size() < 2 || facts.size() > 5 || new HashSet<>(facts).size() != facts.size())
                throw new IllegalStateException("érvénytelen story evidence: " + id);
            put(result, definitions, id, new TrashArchaeologyEvidence(family, required(section.getString("domain")),
                    required(groups.getString(family + ".observation")), facts.stream().map(TrashArchaeologyEvidence::required).toList(), ""));
        }
        final var clues = Objects.requireNonNull(root.getConfigurationSection("discrepancies"));
        for (String id : clues.getKeys(false)) {
            final var current = result.get(id);
            if (current == null || definitions.get(id).internalKind().isInert())
                throw new IllegalStateException("érvénytelen anyagi eltérés: " + id);
            result.put(id, new TrashArchaeologyEvidence(current.family(), current.domain(), current.material(),
                    current.facts(), required(clues.getString(id))));
        }
        if (!result.keySet().equals(definitions.keySet())) throw new IllegalStateException("hiányos Archaeology evidence coverage");
        if (result.values().stream().map(TrashArchaeologyEvidence::family).distinct().count() > 64
                || result.values().stream().map(TrashArchaeologyEvidence::domain).distinct().count() > 32)
            throw new IllegalStateException("az Archaeology evidence túllépi a profil breadth korlátját");
        return Map.copyOf(result);
    }
    private static void put(Map<String, TrashArchaeologyEvidence> result, Map<String, TrashDefinition> definitions,
                            String id, TrashArchaeologyEvidence evidence) {
        if (!definitions.containsKey(id) || result.putIfAbsent(id, evidence) != null)
            throw new IllegalStateException("ismeretlen vagy ismétlődő physical identity: " + id);
    }
    private static String required(String text) {
        if (text == null || text.isBlank() || text.length() > 240) throw new IllegalStateException("érvénytelen Archaeology szöveg");
        return text;
    }
}
