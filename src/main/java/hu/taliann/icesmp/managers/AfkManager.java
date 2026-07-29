package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.session.PlayerStateCleanup;

import java.util.UUID;

/**
 * Retained global AFK service: automatic inactivity detection plus the manual {@code /afk} toggle.
 *
 * <p>Activity writes are safe from async chat and region threads. Reward-producing systems consume
 * {@link #isAfk(UUID)} as their shared exploit gate. Reward zones, payouts and boss bars are
 * intentionally outside this product scope.
 */
public final class AfkManager implements PlayerStateCleanup {

    private final ConfigManager configManager;
    private final GlobalAfkTracker tracker = new GlobalAfkTracker();

    public AfkManager(final ConfigManager configManager) {
        this.configManager = configManager;
    }

    public void recordActivity(final UUID playerId) {
        tracker.recordActivity(playerId);
    }

    /**
     * Toggles the player's overall AFK state. Toggling either manual or automatic AFK off also
     * refreshes the inactivity baseline, so automatic AFK cannot immediately reappear.
     */
    public boolean toggleAfk(final UUID playerId) {
        return tracker.toggleAfk(playerId, System.currentTimeMillis(), timeoutSeconds());
    }

    public boolean isAfk(final UUID playerId) {
        return tracker.isAfk(playerId, System.currentTimeMillis(), timeoutSeconds());
    }

    private long timeoutSeconds() {
        return configManager.getLong("afk.afk-after-seconds", 180L);
    }

    @Override
    public void clearPlayerState(final UUID playerId) {
        tracker.clear(playerId);
    }
}
