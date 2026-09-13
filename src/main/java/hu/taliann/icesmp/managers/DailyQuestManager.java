package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.playerprofile.application.PlayerProfileDailyQuestStore;
import org.bukkit.entity.Player;

/**
 * Read-only compatibility view for retired procedural-daily history.
 *
 * <p>Authored quests are the sole live daily gameplay authority. Existing streak state is retained
 * for achievement/history display; no progress, cooldown or reward mutation is performed here.</p>
 */
public final class DailyQuestManager {

    private final PlayerProfileDailyQuestStore store = new PlayerProfileDailyQuestStore();

    public int getStreak(final Player player) {
        return player == null ? 0 : store.streak(player.getUniqueId());
    }
}
