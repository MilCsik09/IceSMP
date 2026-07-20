package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.managers.ConfigManager;
import hu.taliann.icesmp.managers.JobManager;
import hu.taliann.icesmp.managers.MobScalingManager;
import hu.taliann.icesmp.managers.TalentManager;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Awards class XP for mob kills. Scaled mobs (mob_level PDC) grant bonus XP
 * per level, tying class progression to the distance-based difficulty system.
 */
public final class ClassXpListener implements Listener {

    private final JavaPlugin plugin;
    private final JobManager jobManager;
    private final MobScalingManager mobScalingManager;
    private final ConfigManager configManager;
    private final TalentManager talentManager;

    private final hu.taliann.icesmp.managers.AfkManager afkManager;

    public ClassXpListener(final JavaPlugin plugin, final JobManager jobManager, final MobScalingManager mobScalingManager,
                           final ConfigManager configManager, final TalentManager talentManager,
                           final hu.taliann.icesmp.managers.AfkManager afkManager) {
        this.plugin = plugin;
        this.jobManager = jobManager;
        this.mobScalingManager = mobScalingManager;
        this.configManager = configManager;
        this.talentManager = talentManager;
        this.afkManager = afkManager;
    }

    @EventHandler
    public void onEntityDeath(final EntityDeathEvent event) {
        final LivingEntity entity = event.getEntity();
        final Player killer = entity.getKiller();
        if (killer == null) {
            return;
        }
        // AFK-jelölt játékos nem termel kaszt-XP-t (auto-farm exploit-fék). (Map-olvasás — szál-biztos.)
        if (afkManager != null && configManager.getBoolean("afk.block-rewards", true)
                && afkManager.isAfk(killer.getUniqueId())) {
            return;
        }

        if (configManager.getBoolean("classes.xp.hostile-only", true) && !(entity instanceof Monster)) {
            return;
        }

        final int mobLevel = mobScalingManager.getLevel(entity);

        // Folia: a death event fires on the dying mob's region thread, but the killer is a DIFFERENT
        // entity — even the hasPrimaryJob eligibility check reads its PDC. Hop first, so EVERY
        // killer touch (check + award) happens on the killer's own region thread.
        killer.getScheduler().run(plugin, task -> {
            if (!jobManager.hasPrimaryJob(killer)) {
                return;
            }
            final int baseXp = Math.max(0, configManager.getInt("classes.xp.per-kill", 5));
            final int perLevelXp = Math.max(0, configManager.getInt("classes.xp.per-mob-level", 2));
            final double xpBonusPercent = Math.max(0.0D, talentManager.getEffectTotal(killer, "class-xp-bonus"));
            int totalXp = (int) Math.round((baseXp + (mobLevel * perLevelXp)) * (1.0D + (xpBonusPercent / 100.0D)));
            // H14 — ritka variáns: dupla kaszt-XP (rare-variant.xp-multiplier).
            if (hu.taliann.icesmp.managers.MobScalingManager.rareVariantOf(entity) != null) {
                totalXp = (int) Math.round(totalXp * Math.max(1.0D,
                        configManager.getDouble("rare-variant.xp-multiplier", 2.0D)));
            }
            if (totalXp <= 0) {
                return;
            }

            jobManager.addXpToJob(killer, totalXp);
        }, null);
    }
}
