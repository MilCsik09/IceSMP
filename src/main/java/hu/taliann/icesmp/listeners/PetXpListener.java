package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.managers.ConfigManager;
import hu.taliann.icesmp.managers.PetManager;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;

/**
 * Awards Beast Master companion XP when the owner kills a hostile mob.
 */
public final class PetXpListener implements Listener {

    private final PetManager petManager;
    private final ConfigManager configManager;

    public PetXpListener(final PetManager petManager, final ConfigManager configManager) {
        this.petManager = petManager;
        this.configManager = configManager;
    }

    @EventHandler
    public void onEntityDeath(final EntityDeathEvent event) {
        if (!(event.getEntity() instanceof Monster)) {
            return;
        }
        final Player killer = event.getEntity().getKiller();
        // Both pet-owning specs (Beast Master AND Necromancer) earn companion XP from kills.
        if (killer == null || !petManager.canOwnPet(killer)) {
            return;
        }
        petManager.addXp(killer, Math.max(0, configManager.getInt("pets.companion.xp-per-kill", 2)));
    }
}
