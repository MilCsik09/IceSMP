package hu.taliann.icesmp.prologue;

import hu.taliann.icesmp.playerprofile.application.PlayerProfileAchievementStore;
import hu.taliann.icesmp.playerprofile.application.PlayerProfileAuthority;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** Profile v2-only prestige state; no power reward or legacy player persistence. */
public final class PrologueRewardService {
    public static final String FOUNDER_ACHIEVEMENT = "prologue_founder";
    public static final String FINALE_ACHIEVEMENT = "prologue_finale_participant";

    private final JavaPlugin plugin;
    private final PrologueManager prologue;
    private final PlayerProfileAchievementStore achievements = new PlayerProfileAchievementStore();

    public PrologueRewardService(final JavaPlugin plugin, final PrologueManager prologue) {
        this.plugin = plugin;
        this.prologue = prologue;
    }

    /** Joining while the Prologue is materially active counts as real Founder-era participation. */
    public void grantFounderWhenProfileReady(final Player player, final int attempt) {
        if (player == null || !player.isOnline() || !founderEraActive()) return;
        player.getScheduler().runDelayed(plugin, task -> {
            if (!player.isOnline() || !founderEraActive()) return;
            final boolean ready = PlayerProfileAuthority.installed().flatMap(authority -> authority.repository()
                    .cached(player.getUniqueId())).isPresent();
            if (!ready) {
                if (attempt + 1 < 40) grantFounderWhenProfileReady(player, attempt + 1);
                return;
            }
            achievements.unlock(player.getUniqueId(), FOUNDER_ACHIEVEMENT)
                    .exceptionally(failure -> {
                        plugin.getLogger().warning("Founder státusz mentése sikertelen: " + failure);
                        return false;
                    });
        }, null, attempt == 0 ? 20L : 10L);
    }

    public CompletionStage<Void> commitFinaleParticipants(final Set<UUID> participants) {
        final ArrayList<CompletableFuture<?>> writes = new ArrayList<>();
        for (final UUID playerId : participants == null ? Set.<UUID>of() : participants) {
            writes.add(achievements.unlock(playerId, FOUNDER_ACHIEVEMENT).toCompletableFuture());
            writes.add(achievements.unlock(playerId, FINALE_ACHIEVEMENT).toCompletableFuture());
        }
        return CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new));
    }

    public boolean isFounder(final UUID playerId) {
        return achievements.isUnlocked(playerId, FOUNDER_ACHIEVEMENT);
    }

    public boolean isFinaleParticipant(final UUID playerId) {
        return achievements.isUnlocked(playerId, FINALE_ACHIEVEMENT);
    }

    private boolean founderEraActive() {
        return PrologueContentPolicy.enabled(hu.taliann.icesmp.managers.ConfigManager.current())
                && prologue.state() != PrologueState.DORMANT
                && !prologue.state().completed();
    }
}
