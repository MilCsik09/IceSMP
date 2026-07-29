package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.core.Permissions;
import hu.taliann.icesmp.moderation.PaperEntityTaskSubmission;
import hu.taliann.icesmp.session.PlayerStateCleanup;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Applies the persisted vanish state through the supported Bukkit visibility API. It tracks only
 * visibility changes made by IceSMP, so refresh/disable never calls showPlayer for another
 * system's hidden subjects.
 */
public final class VanishManager implements PlayerStateCleanup {

    private final JavaPlugin plugin;
    private final ModerationManager moderationManager;
    private final ConfigManager configManager;
    private final ConcurrentHashMap<UUID, Set<UUID>> hiddenByViewer = new ConcurrentHashMap<>();
    private final Set<UUID> onlinePlayers = ConcurrentHashMap.newKeySet();

    public VanishManager(final JavaPlugin plugin, final ModerationManager moderationManager,
                         final ConfigManager configManager) {
        this.plugin = plugin;
        this.moderationManager = moderationManager;
        this.configManager = configManager;
    }

    public boolean isVanished(final UUID playerId) {
        return moderationManager.isVanished(playerId);
    }

    public boolean excludedFromOnlineCount() {
        return configManager.getBoolean("moderation.vanish.exclude-from-online-count", true);
    }

    public int visibleOnlineCount() {
        if (!excludedFromOnlineCount()) {
            return onlinePlayers.size();
        }
        return (int) onlinePlayers.stream()
                .filter(playerId -> !isVanished(playerId))
                .count();
    }

    public void markOnline(final UUID playerId) {
        onlinePlayers.add(playerId);
    }

    public void markOffline(final UUID playerId) {
        onlinePlayers.remove(playerId);
    }

    /** Reconciles one viewer on that viewer's entity scheduler. */
    public void refreshViewer(final Player viewer) {
        PaperEntityTaskSubmission.run(plugin, viewer.getScheduler(), () -> applyViewer(viewer), () -> { });
    }

    /** Reconciles every viewer; global discovery is followed by one entity-owned task per viewer. */
    public void refreshAll() {
        Bukkit.getGlobalRegionScheduler().run(plugin, task -> {
            final Set<UUID> current = ConcurrentHashMap.newKeySet();
            for (final Player viewer : Bukkit.getOnlinePlayers()) {
                current.add(viewer.getUniqueId());
                refreshViewer(viewer);
            }
            onlinePlayers.retainAll(current);
            onlinePlayers.addAll(current);
        });
    }

    /** Reconciles a changed subject across all viewers without discovering them off-global. */
    public void refreshSubject(final UUID subjectId) {
        Bukkit.getGlobalRegionScheduler().run(plugin, task -> {
            for (final Player viewer : Bukkit.getOnlinePlayers()) {
                PaperEntityTaskSubmission.run(plugin, viewer.getScheduler(),
                        () -> applySubject(viewer, subjectId), () -> { });
            }
        });
    }

    private void applyViewer(final Player viewer) {
        if (!viewer.isOnline()) {
            return;
        }
        final Set<UUID> hidden = hiddenByViewer.computeIfAbsent(viewer.getUniqueId(), ignored ->
                ConcurrentHashMap.newKeySet());
        for (final UUID subjectId : Set.copyOf(onlinePlayers)) {
            if (!subjectId.equals(viewer.getUniqueId())) {
                applySubject(viewer, subjectId);
            }
        }
        hidden.removeIf(subjectId -> {
            if (Bukkit.getPlayer(subjectId) != null) {
                return false;
            }
            return true;
        });
    }

    private void applySubject(final Player viewer, final UUID subjectId) {
        if (!viewer.isOnline() || viewer.getUniqueId().equals(subjectId)) {
            return;
        }
        final Player subject = Bukkit.getPlayer(subjectId);
        final Set<UUID> hidden = hiddenByViewer.computeIfAbsent(viewer.getUniqueId(), ignored ->
                ConcurrentHashMap.newKeySet());
        final boolean shouldHide = subject != null && isVanished(subjectId)
                && !viewer.hasPermission(Permissions.MODERATION_VANISH_SEE);
        if (shouldHide) {
            if (hidden.add(subjectId)) {
                viewer.hidePlayer(plugin, subject);
            }
        } else if (hidden.remove(subjectId) && subject != null) {
            viewer.showPlayer(plugin, subject);
        }
    }

    /** Best-effort visibility restoration during plugin shutdown/reload teardown. */
    public void shutdown() {
        // Capture before scheduling: clearing the shared map immediately after scheduling would make
        // every task observe null and leave IceSMP-owned visibility pairs hidden until disconnect.
        for (final Player viewer : Bukkit.getOnlinePlayers()) {
            final Set<UUID> hidden = hiddenByViewer.remove(viewer.getUniqueId());
            if (hidden == null || hidden.isEmpty()) {
                continue;
            }
            final Set<UUID> snapshot = Set.copyOf(hidden);
            PaperEntityTaskSubmission.run(plugin, viewer.getScheduler(), () -> {
                for (final UUID subjectId : snapshot) {
                    final Player subject = Bukkit.getPlayer(subjectId);
                    if (subject != null) {
                        viewer.showPlayer(plugin, subject);
                    }
                }
            }, () -> { });
        }
        hiddenByViewer.clear();
    }

    @Override
    public void clearPlayerState(final UUID playerId) {
        onlinePlayers.remove(playerId);
        hiddenByViewer.remove(playerId);
        for (final Set<UUID> hidden : hiddenByViewer.values()) {
            hidden.remove(playerId);
        }
    }
}
