package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.managers.ConfigManager;
import hu.taliann.icesmp.managers.JobManager;
import hu.taliann.icesmp.managers.MobScalingManager;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;

/**
 * Awards class XP for mob kills. Scaled mobs (mob_level PDC) grant bonus XP
 * per level, tying class progression to the distance-based difficulty system.
 */
public final class ClassXpListener implements Listener {

    private final JobManager jobManager;
    private final MobScalingManager mobScalingManager;
    private final ConfigManager configManager;

    public ClassXpListener(final JobManager jobManager, final MobScalingManager mobScalingManager,
                           final ConfigManager configManager) {
        this.jobManager = jobManager;
        this.mobScalingManager = mobScalingManager;
        this.configManager = configManager;
    }

    @EventHandler
    public void onEntityDeath(final EntityDeathEvent event) {
        final LivingEntity entity = event.getEntity();
        final Player killer = entity.getKiller();
        if (killer == null || !jobManager.hasPrimaryJob(killer)) {
            return;
        }

        if (configManager.getBoolean("classes.xp.hostile-only", true) && !(entity instanceof Monster)) {
            return;
        }

        final int baseXp = Math.max(0, configManager.getInt("classes.xp.per-kill", 5));
        final int perLevelXp = Math.max(0, configManager.getInt("classes.xp.per-mob-level", 2));
        final int mobLevel = mobScalingManager.getLevel(entity);
        final int totalXp = baseXp + (mobLevel * perLevelXp);
        if (totalXp <= 0) {
            return;
        }

        jobManager.addXpToJob(killer, true, totalXp);

        if (jobManager.hasSecondaryJob(killer)) {
            final int sharePercent = Math.max(0, Math.min(100, configManager.getInt("classes.xp.secondary-share-percent", 50)));
            final int secondaryXp = totalXp * sharePercent / 100;
            if (secondaryXp > 0) {
                jobManager.addXpToJob(killer, false, secondaryXp);
            }
        }
    }
}
