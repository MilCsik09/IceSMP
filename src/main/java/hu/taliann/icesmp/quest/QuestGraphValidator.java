package hu.taliann.icesmp.quest;

import hu.taliann.icesmp.classspec.domain.ClassSpecCatalog;
import hu.taliann.icesmp.managers.QuestManager;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Teljes quest-gráf validátor: a registry-csere (enable/reload) és az admin-szerkesztő
 * fail-fast kapuja. Hibalistát ad vissza — a hívó hibás candidate esetén a korábbi
 * érvényes registry-pillanatképet tartja meg; a lánc-ciklus konkrét útvonallal
 * diagnosztizált.
 */
public final class QuestGraphValidator {

    private QuestGraphValidator() {
    }

    public static List<String> validate(final ConfigurationSection questsRoot) {
        final List<String> errors = new ArrayList<>();
        if (questsRoot == null) {
            return errors;
        }
        final Set<String> ids = new LinkedHashSet<>();
        for (final String rawId : questsRoot.getKeys(false)) {
            final String id = rawId.toLowerCase(Locale.ROOT);
            if (!ids.add(id)) {
                errors.add(id + ": duplicate quest id (case-insensitive collision)");
            }
        }
        final Map<String, List<String>> nextEdges = new HashMap<>();
        for (final String id : ids) {
            final ConfigurationSection quest = questsRoot.getConfigurationSection(id);
            if (quest == null) {
                errors.add(id + ": quest entry is not a section");
                continue;
            }
            validateQuest(id, quest, ids, errors);
            nextEdges.put(id, nextIds(quest));
        }
        detectChainCycles(nextEdges, errors);
        return errors;
    }

    /** Egyetlen quest szerkezeti validálása (az admin-szerkesztő is ezt hívja mentés előtt). */
    public static void validateQuest(final String id, final ConfigurationSection quest,
                                     final Set<String> knownIds, final List<String> errors) {
        try {
            QuestSourcePolicy.parse(quest);
        } catch (final IllegalArgumentException invalid) {
            errors.add(id + ": " + invalid.getMessage());
        }
        if (QuestCategory.fromConfig(quest.getString("category"), QuestCategory.SIDE) == null) {
            errors.add(id + ": invalid category: " + quest.getString("category"));
        }
        if (QuestVisibility.fromConfig(quest.getString("visibility.mode",
                quest.getString("visibility")), QuestVisibility.ALWAYS) == null) {
            errors.add(id + ": invalid visibility: " + quest.getString("visibility.mode"));
        }
        final List<ConfigurationSection> objectives = objectiveSections(quest);
        if (objectives.isEmpty()) {
            errors.add(id + ": quest has no objectives (impossible empty quest)");
        }
        for (int index = 0; index < objectives.size(); index++) {
            final String type = objectives.get(index).getString("type", "");
            if (!QuestManager.OBJECTIVE_TYPES.contains(type.toUpperCase(Locale.ROOT))) {
                errors.add(id + ": objective #" + (index + 1) + " has invalid type: " + type);
            }
        }
        for (final String next : nextIds(quest)) {
            if (next.equals(id)) {
                errors.add(id + ": next references itself (direct self-cycle)");
            } else if (!knownIds.contains(next)) {
                errors.add(id + ": unknown next quest: " + next);
            }
        }
        final String requiredQuest = quest.getString("requires-quest", "");
        if (!requiredQuest.isBlank()
                && !knownIds.contains(requiredQuest.toLowerCase(Locale.ROOT))) {
            errors.add(id + ": unknown requires-quest: " + requiredQuest);
        }
        final String requiredSpecialization = quest.getString("requires-specialization", "");
        if (!requiredSpecialization.isBlank()
                && !ClassSpecCatalog.isKnownSpecialization(requiredSpecialization)) {
            errors.add(id + ": unknown requires-specialization: " + requiredSpecialization);
        }
    }

    /** A `next` string ÉS lista alakban is olvasható; az unlock-célok kanonikus listája. */
    public static List<String> nextIds(final ConfigurationSection quest) {
        final List<String> result = new ArrayList<>();
        if (quest.isList("next")) {
            for (final String id : quest.getStringList("next")) {
                if (id != null && !id.isBlank()) {
                    result.add(id.trim().toLowerCase(Locale.ROOT));
                }
            }
        } else {
            final String single = quest.getString("next", "");
            if (!single.isBlank()) {
                result.add(single.trim().toLowerCase(Locale.ROOT));
            }
        }
        return result;
    }

    private static void detectChainCycles(final Map<String, List<String>> edges,
                                          final List<String> errors) {
        final Set<String> done = new HashSet<>();
        for (final String start : edges.keySet()) {
            if (done.contains(start)) {
                continue;
            }
            final Deque<String> path = new ArrayDeque<>();
            if (walk(start, edges, done, new HashSet<>(), path, errors)) {
                return;
            }
        }
    }

    private static boolean walk(final String node, final Map<String, List<String>> edges,
                                final Set<String> done, final Set<String> onPath,
                                final Deque<String> path, final List<String> errors) {
        if (!onPath.add(node)) {
            final List<String> cycle = new ArrayList<>(path);
            cycle.add(node);
            final int from = cycle.indexOf(node);
            errors.add("chain cycle detected: "
                    + String.join(" -> ", cycle.subList(from, cycle.size())));
            return true;
        }
        path.addLast(node);
        for (final String next : edges.getOrDefault(node, List.of())) {
            if (!done.contains(next) && edges.containsKey(next)
                    && walk(next, edges, done, onPath, path, errors)) {
                return true;
            }
        }
        path.removeLast();
        onPath.remove(node);
        done.add(node);
        return false;
    }

    private static List<ConfigurationSection> objectiveSections(final ConfigurationSection quest) {
        final List<ConfigurationSection> result = new ArrayList<>();
        final ConfigurationSection plural = quest.getConfigurationSection("objectives");
        if (plural != null) {
            for (final String key : plural.getKeys(false)) {
                final ConfigurationSection section = plural.getConfigurationSection(key);
                if (section != null) {
                    result.add(section);
                }
            }
        }
        final ConfigurationSection single = quest.getConfigurationSection("objective");
        if (single != null) {
            result.add(single);
        }
        return result;
    }
}
