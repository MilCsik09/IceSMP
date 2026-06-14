package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.data.CurrencyType;
import hu.taliann.icesmp.managers.BloodMoonManager;
import hu.taliann.icesmp.managers.ConfigManager;
import hu.taliann.icesmp.managers.CurrencyManager;
import hu.taliann.icesmp.managers.MobScalingManager;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Soulstone drops (ideas.md "Sötét valuta = lélekkő"): high-level scaled mobs
 * have a chance to drop DARK currency tokens on player kills, making the
 * dangerous outer rings economically worthwhile.
 */
public final class SoulstoneListener implements Listener {

    private final CurrencyManager currencyManager;
    private final MobScalingManager mobScalingManager;
    private final BloodMoonManager bloodMoonManager;
    private final ConfigManager configManager;

    public SoulstoneListener(final CurrencyManager currencyManager, final MobScalingManager mobScalingManager,
                             final BloodMoonManager bloodMoonManager, final ConfigManager configManager) {
        this.currencyManager = currencyManager;
        this.mobScalingManager = mobScalingManager;
        this.bloodMoonManager = bloodMoonManager;
        this.configManager = configManager;
    }

    @EventHandler
    public void onEntityDeath(final EntityDeathEvent event) {
        if (!configManager.getBoolean("currency.soul-drop.enabled", true)) {
            return;
        }

        final LivingEntity entity = event.getEntity();
        final Player killer = entity.getKiller();
        if (killer == null) {
            return;
        }

        final int minLevel = Math.max(1, configManager.getInt("currency.soul-drop.min-mob-level", 3));
        final int mobLevel = mobScalingManager.getLevel(entity);
        if (mobLevel < minLevel) {
            return;
        }

        // Blood moon nights multiply the soulstone drop chance.
        final double chancePercent = Math.max(0.0D, Math.min(100.0D,
                configManager.getDouble("currency.soul-drop.chance-percent", 25.0D)
                        * bloodMoonManager.getSoulDropMultiplier()));
        if (ThreadLocalRandom.current().nextDouble(100.0D) >= chancePercent) {
            return;
        }

        final int maxAmount = Math.max(1, configManager.getInt("currency.soul-drop.max-amount", 5));
        final int amount = Math.min(maxAmount, mobLevel - minLevel + 1);
        event.getDrops().add(currencyManager.createCurrencyItem(CurrencyType.DARK, amount));
    }
}
