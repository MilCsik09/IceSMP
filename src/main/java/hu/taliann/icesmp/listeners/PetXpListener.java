package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.managers.ConfigManager;
import hu.taliann.icesmp.managers.PetManager;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Awards Beast Master companion XP when the owner kills a hostile mob.
 */
public final class PetXpListener implements Listener {

    private final JavaPlugin plugin;
    private final PetManager petManager;
    private final ConfigManager configManager;

    public PetXpListener(final JavaPlugin plugin, final PetManager petManager, final ConfigManager configManager) {
        this.plugin = plugin;
        this.petManager = petManager;
        this.configManager = configManager;
    }

    @EventHandler
    public void onEntityDeath(final EntityDeathEvent event) {
        if (!(event.getEntity() instanceof Monster)) {
            return;
        }
        final Player killer = event.getEntity().getKiller();
        if (killer == null) {
            return;
        }
        // Folia: the death event runs on the mob's region thread; the killer is a DIFFERENT entity —
        // even the canOwnPet spec check reads its PDC. Hop first, then check + award on the killer's
        // thread. (Both pet-owning specs — Beast Master AND Necromancer — earn companion XP.)
        killer.getScheduler().run(plugin, task -> {
            if (!petManager.canOwnPet(killer)) {
                return;
            }
            petManager.addXp(killer, Math.max(0, configManager.getInt("pets.companion.xp-per-kill", 2)));
        }, null);
    }
}
