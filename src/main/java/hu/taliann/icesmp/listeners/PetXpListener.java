package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.managers.ConfigManager;
import hu.taliann.icesmp.managers.PetManager;
import org.bukkit.entity.Monster;
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
        final hu.taliann.icesmp.utils.MobKillUtil.KillContext kill =
                hu.taliann.icesmp.utils.MobKillUtil.eligibleKill(event.getEntity(),
                        hu.taliann.icesmp.utils.MobKillUtil.RewardKind.PROGRESSION, configManager, afkManager);
        if (kill == null) {
            return;
        }
        // Folia: the death event runs on the mob's region thread; the killer is a DIFFERENT entity —
        // even the canOwnPet spec check reads its PDC. Hop first, then check + award on the killer's
        // thread. (Both pet-owning specs — Beast Master AND Necromancer — earn companion XP.)
        final boolean undeadVictim = event.getEntity() instanceof org.bukkit.entity.Zombie
                || event.getEntity() instanceof org.bukkit.entity.AbstractSkeleton
                || event.getEntity() instanceof org.bukkit.entity.Phantom;
        final boolean occultVictim = event.getEntity() instanceof org.bukkit.entity.Witch
                || event.getEntity() instanceof org.bukkit.entity.Raider;
        kill.runOnKiller(plugin, killer -> {
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
                dropAtVictim(kill, factory.createPetArmorItem(1));
            }
            if (undeadVictim && petManager.isUnholy(killer)
                    && Math.random() < configManager.getDouble("pets.summon.heart-drop-chance", 0.03D)) {
                dropAtVictim(kill, factory.createHeartItem(1));
            } else if (occultVictim && petManager.isWarlock(killer)
                    && Math.random() < configManager.getDouble("pets.summon.seal-drop-chance", 0.06D)) {
                dropAtVictim(kill, factory.createSealItem(1));
            }
        });
    }

    /**
     * A drop a gyilkos szálán dől el, de az áldozat helyére esik — a világ-mutáció csak az adott
     * régió ütemezőjén szabad, ezért minden esetben friss Location-példánnyal hopolunk.
     */
    private void dropAtVictim(final hu.taliann.icesmp.utils.MobKillUtil.KillContext kill,
                              final org.bukkit.inventory.ItemStack item) {
        final org.bukkit.World world = kill.victimWorld();
        if (item == null || world == null) {
            return;
        }
        final org.bukkit.Location at = kill.victimLocation();
        org.bukkit.Bukkit.getRegionScheduler().run(plugin, at, t -> world.dropItemNaturally(at, item));
    }
}
