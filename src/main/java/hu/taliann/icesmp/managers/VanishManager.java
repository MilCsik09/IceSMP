package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.core.Permissions;
import hu.taliann.icesmp.moderation.PaperEntityTaskSubmission;
import hu.taliann.icesmp.session.PlayerStateCleanup;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Applies persisted vanish to both entity tracking and the per-viewer player list.
 *
 * <p>Both ledgers are ownership ledgers: IceSMP restores only pairs it changed. Entity
 * visibility remains plugin-scoped through hide/showPlayer; tab-list visibility is
 * tracked separately because Paper's list/unlist API is viewer-scoped rather than
 * plugin-scoped.</p>
 */
public final class VanishManager implements PlayerStateCleanup {

    private static final long TRACKING_REASSERT_TICKS = 20L;

    private final JavaPlugin plugin;
    private final ModerationManager moderationManager;
    private final ConfigManager configManager;
    private final ConcurrentHashMap<UUID, Set<UUID>> hiddenByViewer = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Set<UUID>> unlistedByViewer = new ConcurrentHashMap<>();
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
        return excludedFromOnlineCount() ? onlineCountExcludingVanished() : onlineCount();
    }

    public int onlineCount() {
        return onlinePlayers.size();
    }

    public int onlineCountExcludingVanished() {
        return (int) onlinePlayers.stream().filter(playerId -> !isVanished(playerId)).count();
    }

    public void markOnline(final UUID playerId) {
        onlinePlayers.add(playerId);
    }

    public void markOffline(final UUID playerId) {
        onlinePlayers.remove(playerId);
    }

    public void refreshViewer(final Player viewer) {
        PaperEntityTaskSubmission.run(plugin, viewer.getScheduler(), () -> applyViewer(viewer), () -> { });
    }

    public void refreshAll() {
        reconcileAllOnce();
        Bukkit.getGlobalRegionScheduler().runDelayed(plugin, task -> reconcileAllOnce(), TRACKING_REASSERT_TICKS);
    }

    private void reconcileAllOnce() {
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

    public void refreshSubject(final UUID subjectId) {
        refreshSubjectOnce(subjectId);
        Bukkit.getGlobalRegionScheduler().runDelayed(plugin,
                task -> refreshSubjectOnce(subjectId), TRACKING_REASSERT_TICKS);
    }

    private void refreshSubjectOnce(final UUID subjectId) {
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
        for (final UUID subjectId : Set.copyOf(onlinePlayers)) {
            if (!subjectId.equals(viewer.getUniqueId())) {
                applySubject(viewer, subjectId);
            }
        }
        pruneOffline(hiddenByViewer.computeIfAbsent(viewer.getUniqueId(), ignored ->
                ConcurrentHashMap.newKeySet()));
        pruneOffline(unlistedByViewer.computeIfAbsent(viewer.getUniqueId(), ignored ->
                ConcurrentHashMap.newKeySet()));
    }

    private static void pruneOffline(final Set<UUID> subjects) {
        subjects.removeIf(subjectId -> Bukkit.getPlayer(subjectId) == null);
    }

    private void applySubject(final Player viewer, final UUID subjectId) {
        if (!viewer.isOnline() || viewer.getUniqueId().equals(subjectId)) {
            return;
        }
        final Player subject = Bukkit.getPlayer(subjectId);
        final Set<UUID> hidden = hiddenByViewer.computeIfAbsent(viewer.getUniqueId(), ignored ->
                ConcurrentHashMap.newKeySet());
        final Set<UUID> unlisted = unlistedByViewer.computeIfAbsent(viewer.getUniqueId(), ignored ->
                ConcurrentHashMap.newKeySet());
        final boolean shouldHide = subject != null && isVanished(subjectId)
                && !viewer.hasPermission(Permissions.MODERATION_VANISH_SEE);

        if (shouldHide) {
            hidden.add(subjectId);
            viewer.hidePlayer(plugin, subject);
            if (viewer.isListed(subject)) {
                viewer.unlistPlayer(subject);
                unlisted.add(subjectId);
            } else if (unlisted.contains(subjectId)) {
                viewer.unlistPlayer(subject);
            }
            return;
        }

        if (subject != null && hidden.remove(subjectId)) {
            viewer.showPlayer(plugin, subject);
        }
        if (subject != null && unlisted.remove(subjectId)
                && viewer.canSee(subject) && !viewer.isListed(subject)) {
            viewer.listPlayer(subject);
        }
    }

    public void shutdown() {
        for (final Player viewer : Bukkit.getOnlinePlayers()) {
            final Set<UUID> hidden = hiddenByViewer.remove(viewer.getUniqueId());
            final Set<UUID> unlisted = unlistedByViewer.remove(viewer.getUniqueId());
            if ((hidden == null || hidden.isEmpty()) && (unlisted == null || unlisted.isEmpty())) {
                continue;
            }
            final Set<UUID> subjects = new LinkedHashSet<>();
            if (hidden != null) subjects.addAll(hidden);
            if (unlisted != null) subjects.addAll(unlisted);
            final Set<UUID> hiddenSnapshot = hidden == null ? Set.of() : Set.copyOf(hidden);
            final Set<UUID> unlistedSnapshot = unlisted == null ? Set.of() : Set.copyOf(unlisted);
            PaperEntityTaskSubmission.run(plugin, viewer.getScheduler(), () -> {
                for (final UUID subjectId : subjects) {
                    final Player subject = Bukkit.getPlayer(subjectId);
                    if (subject == null) continue;
                    if (hiddenSnapshot.contains(subjectId)) {
                        viewer.showPlayer(plugin, subject);
                    }
                    if (unlistedSnapshot.contains(subjectId)
                            && viewer.canSee(subject) && !viewer.isListed(subject)) {
                        viewer.listPlayer(subject);
                    }
                }
            }, () -> { });
        }
        hiddenByViewer.clear();
        unlistedByViewer.clear();
    }

    @Override
    public void clearPlayerState(final UUID playerId) {
        onlinePlayers.remove(playerId);
        hiddenByViewer.remove(playerId);
        unlistedByViewer.remove(playerId);
        for (final Set<UUID> hidden : hiddenByViewer.values()) {
            hidden.remove(playerId);
        }
        for (final Set<UUID> unlisted : unlistedByViewer.values()) {
            unlisted.remove(playerId);
        }
    }
}
