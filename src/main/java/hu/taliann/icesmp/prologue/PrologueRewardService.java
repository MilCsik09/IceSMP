package hu.taliann.icesmp.prologue;

import hu.taliann.icesmp.playerprofile.application.PlayerProfileAchievementStore;
import hu.taliann.icesmp.playerprofile.application.PlayerProfileAuthority;
import org.bukkit.Bukkit;
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

    public void grantEligibleWhenProfileReady(final Player player, final int attempt) {
        if (player == null || !player.isOnline()) return;
        player.getScheduler().runDelayed(plugin, task -> {
            if (!player.isOnline()) return;
            final var authority = PlayerProfileAuthority.installed().orElse(null);
            final boolean ready = authority != null
                    && authority.repository().cached(player.getUniqueId()).isPresent();
            if (!ready) {
                if (attempt + 1 < 40) grantEligibleWhenProfileReady(player, attempt + 1);
                return;
            }
            final UUID playerId = player.getUniqueId();
            final ArrayList<CompletableFuture<?>> writes = new ArrayList<>();
            if (founderEraActive() || prologue.finaleParticipants().contains(playerId)) {
                writes.add(achievements.unlock(playerId, FOUNDER_ACHIEVEMENT).toCompletableFuture());
            }
            if (prologue.rewardPlanCreated() && prologue.finaleParticipants().contains(playerId)) {
                writes.add(achievements.unlock(playerId, FINALE_ACHIEVEMENT).toCompletableFuture());
            }
            CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new))
                    .exceptionally(failure -> {
                        plugin.getLogger().warning("Prologue prestige státusz mentése sikertelen: " + failure);
                        return null;
                    });
        }, null, attempt == 0 ? 20L : 10L);
    }

    /**
     * Delivers every currently loaded Profile v2 immediately. Offline participants deliberately
     * remain in the durable world reward plan and are replayed by grantEligibleWhenProfileReady.
     */
    public CompletionStage<Void> commitFinaleParticipants(final Set<UUID> participants) {
        final ArrayList<CompletableFuture<?>> writes = new ArrayList<>();
        final var authority = PlayerProfileAuthority.installed().orElse(null);
        if (authority == null) return CompletableFuture.completedFuture(null);
        for (final UUID playerId : participants == null ? Set.<UUID>of() : participants) {
            if (authority.repository().cached(playerId).isEmpty()) continue;
            writes.add(achievements.unlock(playerId, FOUNDER_ACHIEVEMENT).toCompletableFuture());
            writes.add(achievements.unlock(playerId, FINALE_ACHIEVEMENT).toCompletableFuture());
        }
        return CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new));
    }

    public void reconcileLoadedParticipants() {
        if (!prologue.rewardPlanCreated()) return;
        for (final Player player : Bukkit.getOnlinePlayers()) {
            if (prologue.finaleParticipants().contains(player.getUniqueId())) {
                grantEligibleWhenProfileReady(player, 0);
            }
        }
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
