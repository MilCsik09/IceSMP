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

    private volatile hu.taliann.icesmp.items.CaptureItemFactory captureItemFactory;

    public void setCaptureItemFactory(final hu.taliann.icesmp.items.CaptureItemFactory factory) {
        this.captureItemFactory = factory;
    }

    public PetXpListener(final JavaPlugin plugin, final PetManager petManager, final ConfigManager configManager) {
        this.plugin = plugin;
        this.petManager = petManager;
        this.configManager = configManager;
    }

    /** A kill-előszűrő AFK-fékéhez (setter: az AfkManager később épül a DI-sorrendben). */
    private volatile hu.taliann.icesmp.managers.AfkManager afkManager;

    public void setAfkManager(final hu.taliann.icesmp.managers.AfkManager afkManager) {
        this.afkManager = afkManager;
    }

    @EventHandler
    public void onEntityDeath(final EntityDeathEvent event) {
        if (!(event.getEntity() instanceof Monster)) {
            return;
        }
        final Player killer = hu.taliann.icesmp.utils.MobKillUtil.eligibleKiller(event.getEntity(),
                hu.taliann.icesmp.utils.MobKillUtil.RewardKind.PROGRESSION, configManager, afkManager);
        if (killer == null) {
            return;
        }
        // Folia: the death event runs on the mob's region thread; the killer is a DIFFERENT entity —
        // even the canOwnPet spec check reads its PDC. Hop first, then check + award on the killer's
        // thread. (Both pet-owning specs — Beast Master AND Necromancer — earn companion XP.)
        final org.bukkit.Location dropAt = event.getEntity().getLocation();
        final boolean undeadVictim = event.getEntity() instanceof org.bukkit.entity.Zombie
                || event.getEntity() instanceof org.bukkit.entity.AbstractSkeleton
                || event.getEntity() instanceof org.bukkit.entity.Phantom;
        final boolean occultVictim = event.getEntity() instanceof org.bukkit.entity.Witch
                || event.getEntity() instanceof org.bukkit.entity.Raider;
        final org.bukkit.World dropWorld = dropAt.getWorld();
        killer.getScheduler().run(plugin, task -> {
            if (!petManager.canOwnPet(killer)) {
                return;
            }
            petManager.addXp(killer, Math.max(0, configManager.getInt("pets.companion.xp-per-kill", 2)));
            // Rituálé-kellék dropok: a beszerzés-kihívás forrása (a drop a mob helyén esik).
            final hu.taliann.icesmp.items.CaptureItemFactory factory = this.captureItemFactory;
            if (factory == null) {
                return;
            }
            if (!petManager.hasPetArmor(killer)
                    && Math.random() < configManager.getDouble("pets.equipment.drop-chance", 0.01D)) {
                org.bukkit.Bukkit.getRegionScheduler().run(plugin, dropAt,
                        t -> dropWorld.dropItemNaturally(dropAt, factory.createPetArmorItem(1)));
            }
            if (undeadVictim && petManager.isUnholy(killer)
                    && Math.random() < configManager.getDouble("pets.summon.heart-drop-chance", 0.03D)) {
                org.bukkit.Bukkit.getRegionScheduler().run(plugin, dropAt,
                        t -> dropWorld.dropItemNaturally(dropAt, factory.createHeartItem(1)));
            } else if (occultVictim && petManager.isWarlock(killer)
                    && Math.random() < configManager.getDouble("pets.summon.seal-drop-chance", 0.06D)) {
                org.bukkit.Bukkit.getRegionScheduler().run(plugin, dropAt,
                        t -> dropWorld.dropItemNaturally(dropAt, factory.createSealItem(1)));
            }
        }, null);
    }
}
