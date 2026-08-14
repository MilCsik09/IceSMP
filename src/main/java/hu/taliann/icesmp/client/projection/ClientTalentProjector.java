package hu.taliann.icesmp.client.projection;

import hu.taliann.icesmp.client.protocol.ClientProtocol;
import hu.taliann.icesmp.client.protocol.TalentStatePayload;
import hu.taliann.icesmp.managers.TalentManager;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * A vanilla talent-GUI-val azonos szűrésű TALENT_STATE leképezés: poolonként csak az
 * isAvailable-teljesítő (a játékos aktuális kasztjához/szakmájához tartozó) talentek,
 * tier szerint rendezve, a protokoll-limiten csonkolva. A játékos régió-szálán hívandó
 * (a requirement-ellenőrzés élő gameplay-gettereken fut).
 */
public final class ClientTalentProjector {

    private ClientTalentProjector() {
    }

    public static TalentStatePayload project(final Player player, final TalentManager talentManager) {
        return new TalentStatePayload(
                talentManager.getAvailablePoints(player, true),
                talentManager.getSpentPoints(player, true),
                talentManager.getAvailablePoints(player, false),
                talentManager.getSpentPoints(player, false),
                pool(player, talentManager, true),
                pool(player, talentManager, false));
    }

    private static List<TalentStatePayload.Entry> pool(final Player player,
                                                       final TalentManager talentManager,
                                                       final boolean classPool) {
        final ConfigurationSection definitions = talentManager.getDefinitions(classPool);
        final List<TalentStatePayload.Entry> entries = new ArrayList<>();
        if (definitions == null) {
            return entries;
        }
        for (final String talentId : definitions.getKeys(false)) {
            if (!talentManager.isAvailable(player, classPool, talentId)) {
                continue;
            }
            final ConfigurationSection talent = definitions.getConfigurationSection(talentId);
            if (talent == null) {
                continue;
            }
            entries.add(new TalentStatePayload.Entry(
                    talentId,
                    talent.getString("display-name", talentId),
                    talent.getInt("tier", 1),
                    talentManager.getRank(player, classPool, talentId),
                    talent.getInt("max-rank", 1),
                    talent.getString("effect", ""),
                    talent.getDouble("per-rank", 0.0D),
                    talentManager.isUnlocked(player, classPool, talentId),
                    talent.getString("requires-talent", ""),
                    talent.getStringList("excludes"),
                    talent.getInt("requires-spent", 0),
                    !talent.getString("grants-spell", "").isEmpty()));
        }
        entries.sort(Comparator.comparingInt(TalentStatePayload.Entry::tier)
                .thenComparing(TalentStatePayload.Entry::talentId));
        return entries.size() <= ClientProtocol.MAX_LIST_ELEMENTS
                ? entries : entries.subList(0, ClientProtocol.MAX_LIST_ELEMENTS);
    }
}
