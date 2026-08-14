package hu.taliann.icesmp.client.projection;

import hu.taliann.icesmp.client.protocol.ClientProtocol;
import hu.taliann.icesmp.client.protocol.QuestStatePayload;
import hu.taliann.icesmp.managers.QuestManager;
import hu.taliann.icesmp.quest.QuestSourcePolicy;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A vanilla questlog öt füljével azonos QUEST_STATE leképezés — ugyanazokból a
 * QuestManager-hívásokból, azonos fül-besorolással. A láthatóság egyetlen forrása az
 * isVisible-szűrt getVisibleQuestIds (HIDDEN sosem szivárog); a riddle-questek
 * progressze a szerveroldali describeProgress „???” placeholderével utazik. A
 * fülönkénti listák a protokoll-limiten csonkolódnak, a total mezők a valós
 * darabszámot viszik. A játékos régió-szálán hívandó.
 */
public final class ClientQuestProjector {

    private ClientQuestProjector() {
    }

    public static QuestStatePayload project(final Player player, final QuestManager questManager) {
        final List<String> activeIds = questManager.getActiveQuests(player);
        final List<String> readyIds = questManager.getReadyQuests(player);
        final Set<String> activeSet = new HashSet<>(activeIds);
        final Set<String> completedSet = new HashSet<>(questManager.getCompletedQuests(player));
        final Set<String> readySet = new HashSet<>(readyIds);

        final List<QuestStatePayload.ActiveEntry> active = new ArrayList<>();
        for (final String questId : activeIds) {
            if (active.size() >= ClientProtocol.MAX_LIST_ELEMENTS) {
                break;
            }
            active.add(new QuestStatePayload.ActiveEntry(questId,
                    questManager.getDisplayName(questId),
                    categoryOf(questManager, questId),
                    descriptionOf(questManager, questId),
                    questManager.describeProgress(player, questId),
                    readySet.contains(questId)));
        }

        final List<QuestStatePayload.HintEntry> ready = new ArrayList<>();
        for (final String questId : readyIds) {
            if (ready.size() >= ClientProtocol.MAX_LIST_ELEMENTS) {
                break;
            }
            ready.add(hintEntry(questManager, questId, questManager.describeTurnInHint(questId)));
        }

        // A BOARD/AVAILABLE besorolás a QuestLogGUI-val azonos: látható, nem aktív és
        // nem teljesített questek, a forrás-policy startType-ja szerint kettéosztva.
        final List<QuestStatePayload.HintEntry> board = new ArrayList<>();
        final List<QuestStatePayload.HintEntry> available = new ArrayList<>();
        final List<QuestStatePayload.HintEntry> completed = new ArrayList<>();
        int boardTotal = 0;
        int availableTotal = 0;
        int completedTotal = 0;
        for (final String questId : questManager.getVisibleQuestIds(player)) {
            if (activeSet.contains(questId)) {
                continue;
            }
            if (completedSet.contains(questId)) {
                completedTotal++;
                if (completed.size() < ClientProtocol.MAX_LIST_ELEMENTS) {
                    completed.add(hintEntry(questManager, questId, ""));
                }
                continue;
            }
            final QuestSourcePolicy policy = questManager.getSourcePolicy(questId);
            if (policy != null && policy.startType() == QuestSourcePolicy.StartType.QUEST_BOARD) {
                boardTotal++;
                if (board.size() < ClientProtocol.MAX_LIST_ELEMENTS) {
                    board.add(hintEntry(questManager, questId, ""));
                }
            } else {
                availableTotal++;
                if (available.size() < ClientProtocol.MAX_LIST_ELEMENTS) {
                    available.add(hintEntry(questManager, questId,
                            questManager.describeStartHint(questId)));
                }
            }
        }

        final String tracked = questManager.getTracked(player);
        return new QuestStatePayload(tracked == null ? "" : tracked,
                active, ready, readyIds.size(), board, boardTotal,
                available, availableTotal, completed, completedTotal);
    }

    private static QuestStatePayload.HintEntry hintEntry(final QuestManager questManager,
                                                         final String questId, final String hint) {
        return new QuestStatePayload.HintEntry(questId,
                questManager.getDisplayName(questId),
                categoryOf(questManager, questId),
                descriptionOf(questManager, questId),
                hint == null ? "" : hint);
    }

    private static String categoryOf(final QuestManager questManager, final String questId) {
        return questManager.getCategory(questId) == null ? ""
                : questManager.getCategory(questId).name();
    }

    private static String descriptionOf(final QuestManager questManager, final String questId) {
        final ConfigurationSection section = questManager.getQuestSection(questId);
        return section == null ? "" : section.getString("description", "");
    }
}
