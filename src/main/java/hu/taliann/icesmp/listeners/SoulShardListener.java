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
 * Awards soul shards to necromancer-spec players for hostile kills
 * (ideas.md "Nekromanta lélek-erőforrás").
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

    @EventHandler
    public void onEntityDeath(final EntityDeathEvent event) {
        if (!(event.getEntity() instanceof Monster)) {
            return;
        }

        final Player killer = event.getEntity().getKiller();
        if (killer == null || specializationManager.getClassSpecialization(killer) != SpecializationType.NECROMANCER) {
            return;
        }

        // Folia: the death event runs on the mob's region thread; addShards mutates the killer.
        // Hop onto the killer's own scheduler first.
        killer.getScheduler().run(plugin,
                task -> soulShardManager.addShards(killer, Math.max(0, configManager.getInt("souls.shards-per-kill", 1))), null);
    }
}
