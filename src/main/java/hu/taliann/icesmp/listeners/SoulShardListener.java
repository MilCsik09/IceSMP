package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.data.SpecializationType;
import hu.taliann.icesmp.managers.ConfigManager;
import hu.taliann.icesmp.managers.SoulShardManager;
import hu.taliann.icesmp.managers.SpecializationManager;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Awards soul shards to necromancer-spec players for hostile kills.
 */
public final class SoulShardListener implements Listener {

    private final JavaPlugin plugin;
    private final SoulShardManager soulShardManager;
    private final SpecializationManager specializationManager;
    private final ConfigManager configManager;

    public SoulShardListener(final JavaPlugin plugin, final SoulShardManager soulShardManager,
                             final SpecializationManager specializationManager,
                             final ConfigManager configManager) {
        this.plugin = plugin;
        this.soulShardManager = soulShardManager;
        this.specializationManager = specializationManager;
        this.configManager = configManager;
    }

    /** AFK-fék (setterrel kötve — az AfkManager később épül a DI-sorrendben). */
    private volatile hu.taliann.icesmp.managers.AfkManager afkManager;

    public void setAfkManager(final hu.taliann.icesmp.managers.AfkManager afkManager) {
        this.afkManager = afkManager;
    }

    @EventHandler
    public void onEntityDeath(final EntityDeathEvent event) {
        if (!(event.getEntity() instanceof Monster)) {
            return;
        }

        // Az élőhalottban nincs learatható lélek — a Néma Királynő szava mozgatja, nem lélek (lore),
        // és a nekromanta (DARK-kötött spec) a rá nem támadó élőhalottakból kockázat nélkül aratna
        // (exploit-fék). A szilánk az ÉLŐ szörnyek lelkéből jön.
        if (!configManager.getBoolean("souls.shards-from-undead", false)
                && hu.taliann.icesmp.utils.UndeadUtil.isUndead(event.getEntity())) {
            return;
        }

        final Player killer = event.getEntity().getKiller();
        if (killer == null) {
            return;
        }
        // Saját minion nem lélek-forrás; AFK-parkolt farm sem termel szilánkot
        // (a testvér-listenerek — Soulstone/ClassXp — ugyanígy fékeznek).
        if (hu.taliann.icesmp.managers.MinionManager.isMinionTagged(event.getEntity())) {
            return;
        }
        final hu.taliann.icesmp.managers.AfkManager afkRef = afkManager;
        if (afkRef != null && afkRef.isAfk(killer.getUniqueId())) {
            return;
        }

        // Folia: the death event runs on the mob's region thread; the killer is a DIFFERENT entity —
        // even the spec check reads its PDC. Hop first, then check + award on the killer's thread.
        killer.getScheduler().run(plugin, task -> {
            if (specializationManager.getClassSpecialization(killer) != SpecializationType.NECROMANCER) {
                return;
            }
            soulShardManager.addShards(killer, Math.max(0, configManager.getInt("souls.shards-per-kill", 1)));
        }, null);
    }
}
