package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.managers.ConfigManager;
import hu.taliann.icesmp.managers.JobManager;
import hu.taliann.icesmp.managers.MobScalingManager;
import hu.taliann.icesmp.managers.TalentManager;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
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
        final java.util.UUID victimId = entity.getUniqueId();
        final hu.taliann.icesmp.utils.MobKillUtil.KillContext kill =
                hu.taliann.icesmp.utils.MobKillUtil.eligibleKill(entity,
                        hu.taliann.icesmp.utils.MobKillUtil.RewardKind.PROGRESSION, configManager, afkManager);
        if (kill == null) {
            return;
        }

        if (configManager.getBoolean("classes.xp.hostile-only", true) && !(entity instanceof Monster)) {
            return;
        }

        // Az áldozat PDC-je csak itt, a saját régió-szálán olvasható — a hopba már csak a szám megy.
        final int mobLevel = mobScalingManager.getLevel(entity);
        final boolean rareVariant = hu.taliann.icesmp.managers.MobScalingManager.rareVariantOf(entity) != null;

        // Folia: a death event fires on the dying mob's region thread, but the killer is a DIFFERENT
        // entity — even the hasPrimaryJob eligibility check reads its PDC. Hop first, so EVERY
        // killer touch (check + award) happens on the killer's own region thread.
        kill.runOnKiller(plugin, killer -> {
            if (!jobManager.hasPrimaryJob(killer)) {
                return;
            }
            final int baseXp = Math.max(0, configManager.getInt("classes.xp.per-kill", 5));
            final int perLevelXp = Math.max(0, configManager.getInt("classes.xp.per-mob-level", 2));
            final double xpBonusPercent = Math.max(0.0D, talentManager.getEffectTotal(killer, "class-xp-bonus"));
            int totalXp = (int) Math.round((baseXp + (mobLevel * perLevelXp)) * (1.0D + (xpBonusPercent / 100.0D)));
            // Ritka variáns: dupla kaszt-XP (rare-variant.xp-multiplier).
            if (rareVariant) {
                totalXp = (int) Math.round(totalXp * Math.max(1.0D,
                        configManager.getDouble("rare-variant.xp-multiplier", 2.0D)));
            }
            if (totalXp <= 0) {
                return;
            }

            jobManager.addXpToJobV2(killer, totalXp,
                    "mob-kill:" + killer.getUniqueId() + ":" + victimId)
                    .exceptionally(failure -> { plugin.getLogger().warning("Class XP commit failed: " + failure.getMessage()); return false; });
        });
    }
}
