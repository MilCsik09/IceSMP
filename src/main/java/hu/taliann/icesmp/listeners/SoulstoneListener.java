package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.data.CurrencyType;
import hu.taliann.icesmp.data.FactionType;
import hu.taliann.icesmp.managers.BloodMoonManager;
import hu.taliann.icesmp.managers.ConfigManager;
import hu.taliann.icesmp.managers.CurrencyManager;
import hu.taliann.icesmp.managers.FactionManager;
import hu.taliann.icesmp.managers.MobScalingManager;
import hu.taliann.icesmp.utils.UndeadUtil;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Soulstone drops: high-level scaled mobs have a chance to drop DARK currency
 * tokens on player kills, making the dangerous outer rings economically
 * worthwhile.
 */
public final class SoulstoneListener implements Listener {

    private final CurrencyManager currencyManager;
    private final MobScalingManager mobScalingManager;
    private final BloodMoonManager bloodMoonManager;
    private final ConfigManager configManager;
    private final FactionManager factionManager;

    private final hu.taliann.icesmp.managers.AfkManager afkManager;

    public SoulstoneListener(final CurrencyManager currencyManager, final MobScalingManager mobScalingManager,
                             final BloodMoonManager bloodMoonManager, final ConfigManager configManager,
                             final FactionManager factionManager,
                             final hu.taliann.icesmp.managers.AfkManager afkManager) {
        this.currencyManager = currencyManager;
        this.mobScalingManager = mobScalingManager;
        this.bloodMoonManager = bloodMoonManager;
        this.configManager = configManager;
        this.factionManager = factionManager;
        this.afkManager = afkManager;
    }

    @EventHandler
    public void onEntityDeath(final EntityDeathEvent event) {
        if (!configManager.getBoolean("currency.soul-drop.enabled", true)) {
            return;
        }

        final LivingEntity entity = event.getEntity();
        if (!hu.taliann.icesmp.pve.CreatureProfileService.authoredRewardEligible(entity)) {
            return;
        }
        final hu.taliann.icesmp.utils.MobKillUtil.KillContext kill =
                hu.taliann.icesmp.utils.MobKillUtil.eligibleKill(entity,
                        hu.taliann.icesmp.utils.MobKillUtil.RewardKind.FAUCET, configManager, afkManager);
        if (kill == null) {
            return;
        }

        // „A Királynő nem fizet a testvérgyilkosságért": a DARK-jelölt kezén az élőhalottból nem
        // kristályosodik lélekkő — az élőhalottak úgysem védekeznek ellene (frakció-passzív), így ez
        // egyszerre lore-szabály és auto-farm exploit-fék. (A frakció-lekérdezés ConcurrentHashMap —
        // Folia-biztos a mob régió-szálán is.)
        if (!configManager.getBoolean("currency.soul-drop.dark-undead-drops", false)
                && UndeadUtil.isUndead(entity)
                && factionManager.isMember(kill.killerId(), FactionType.DARK)) {
            return;
        }

        final int minLevel = Math.max(1, configManager.getInt("currency.soul-drop.min-mob-level", 3));
        final int mobLevel = mobScalingManager.getLevel(entity);
        if (mobLevel < minLevel) {
            return;
        }

        // Blood moon nights multiply the soulstone drop chance.
        double chancePercent = Math.max(0.0D, Math.min(100.0D,
                configManager.getDouble("currency.soul-drop.chance-percent", 25.0D)
                        * bloodMoonManager.getSoulDropMultiplier()));
        // Ritka variáns: emelt lélekkő-esély (rare-variant.soul-chance-multiplier).
        if (hu.taliann.icesmp.managers.MobScalingManager.rareVariantOf(entity) != null) {
            chancePercent = Math.min(100.0D, chancePercent * Math.max(1.0D,
                    configManager.getDouble("rare-variant.soul-chance-multiplier", 2.0D)));
        }
        if (ThreadLocalRandom.current().nextDouble(100.0D) >= chancePercent) {
            return;
        }

        final int maxAmount = Math.max(1, configManager.getInt("currency.soul-drop.max-amount", 5));
        final int amount = Math.min(maxAmount, mobLevel - minLevel + 1);
        if (!tryConsumeDailyBudget(kill.killerId(), amount)) {
            return;
        }
        event.getDrops().add(currencyManager.createCurrencyItem(CurrencyType.DARK, amount));
    }

    /**
     * Napi keret (currency.soul-drop.daily-cap, 0 = korlátlan): a min-mob-level a
     * spawner-mobokat kizárja (azok skálázatlanok, szintjük 0), de a szint-zónás
     * natural-farm ellen csak a plafon véd. Memóriás tároló, mert a halál-event a MOB
     * régió-szálán fut — a gyilkos PDC-jébe innen nem írhatunk.
     */
    private final hu.taliann.icesmp.utils.DailyBudget.InMemory<java.util.UUID> soulBudget =
            new hu.taliann.icesmp.utils.DailyBudget.InMemory<>(512);

    private boolean tryConsumeDailyBudget(final java.util.UUID playerId, final long amount) {
        return soulBudget.tryConsume(playerId, amount,
                configManager.getDouble("currency.soul-drop.daily-cap", 50.0D));
    }
}
