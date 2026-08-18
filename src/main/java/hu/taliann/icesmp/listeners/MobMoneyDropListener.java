package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.items.MoneyPouchItemFactory;
import hu.taliann.icesmp.managers.ConfigManager;
import hu.taliann.icesmp.managers.MobScalingManager;
import hu.taliann.icesmp.utils.MobKillUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Random;

/**
 * WoW-stílusú pénz-drop: az ellenséges mob halálakor (játékos-öléssel) eséllyel Kopott
 * erszény esik — fizikai tárgy, véletlen frakció-valutával, az összeg a mob szintjével
 * skálázódik. Spawner-mob nem dob (farm-fék): a spawner-spawnokat entitás-PDC jelöli,
 * ami az újraindítást is túléli. Minden kulcs élőben olvasódik (mob-money-drop.*).
 *
 * <p>Folia: mindkét event a mob régió-szálán fut. Az alap-esély ott is eldönthető, ezért a
 * drop az {@code event.getDrops()}-ba kerül. A Mohóság Rúnája viszont a GYILKOS kezében van —
 * idegen entitás inventory-olvasása csak annak saját régió-szálán szabad —, ezért a
 * rúna-bónusz sávja hopol, és a drop onnan a régió-ütemezőn, az áldozat helyén esik le.
 * A kettőt egyetlen, a kill drop-magjából származó sorsolás kapcsolja össze, így a két sáv
 * kizárja egymást: dupla erszény nem születhet.
 */
public final class MobMoneyDropListener implements Listener {

    /** A rúna-sáv kifizetése a gyilkos ütemezőjén fut — ehhez kell a plugin-példány. */
    private final org.bukkit.plugin.java.JavaPlugin plugin;
    private final ConfigManager configManager;
    private final MobScalingManager mobScalingManager;
    private final MoneyPouchItemFactory pouchFactory;
    private final hu.taliann.icesmp.managers.AfkManager afkManager;
    private final hu.taliann.icesmp.itemization.ItemIdentityService itemIdentityService;
    private final org.bukkit.NamespacedKey spawnerMobKey;

    public MobMoneyDropListener(final org.bukkit.plugin.java.JavaPlugin plugin,
                                final ConfigManager configManager,
                                final MobScalingManager mobScalingManager,
                                final MoneyPouchItemFactory pouchFactory,
                                final hu.taliann.icesmp.managers.AfkManager afkManager,
                                final hu.taliann.icesmp.itemization.ItemIdentityService itemIdentityService) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.mobScalingManager = mobScalingManager;
        this.pouchFactory = pouchFactory;
        this.afkManager = afkManager;
        this.itemIdentityService = itemIdentityService;
        this.spawnerMobKey = new org.bukkit.NamespacedKey(plugin, "spawner_mob");
    }

    @EventHandler(ignoreCancelled = true)
    public void onSpawn(final CreatureSpawnEvent event) {
        // A jelölést a MobKillUtil olvassa vissza minden FAUCET-jutalomnál (nem csak itt).
        if (event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.SPAWNER) {
            event.getEntity().getPersistentDataContainer().set(spawnerMobKey,
                    org.bukkit.persistence.PersistentDataType.BYTE, (byte) 1);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDeath(final EntityDeathEvent event) {
        final LivingEntity entity = event.getEntity();
        if (!(entity instanceof Monster) || !configManager.getBoolean("mob-money-drop.enabled", true)) {
            return;
        }
        final MobKillUtil.KillContext kill = MobKillUtil.eligibleKill(entity,
                MobKillUtil.RewardKind.FAUCET, configManager, afkManager);
        if (kill == null) {
            return;
        }
        final double baseChance = Math.max(0.0D, Math.min(100.0D,
                configManager.getDouble("mob-money-drop.chance-percent", 20.0D)));
        final double runeBonus = Math.max(0.0D,
                configManager.getDouble("runes.runa_moho.money-drop-bonus-percent", 5.0D));

        // EGYETLEN sorsolás a kill drop-magjából: ugyanez az érték jön ki a gyilkos szálán is,
        // ezért a rúna-sáv utólag, kereszt-száli dupla-fizetés nélkül dönthető el.
        final Random rng = kill.dropRandom("mob-money");
        final double roll = rng.nextDouble() * 100.0D;
        if (roll >= Math.min(100.0D, baseChance + runeBonus)) {
            return;
        }

        // Az áldozat PDC-je (mob-szint) csak itt, a saját régió-szálán olvasható.
        final int mobLevel = mobScalingManager == null ? 1 : Math.max(1, mobScalingManager.getLevel(entity));
        final long amount = rollAmount(rng, mobLevel);

        if (roll < baseChance) {
            final ItemStack pouch = payout(kill, amount);
            if (pouch != null) {
                event.getDrops().add(pouch);
            }
            return;
        }

        final World world = kill.victimWorld();
        if (world == null) {
            return;
        }
        // Csak a rúna-sáv maradt: a Mohóság Rúnája a gyilkos fő kezében van, ezért az olvasás
        // az ő ütemezőjén fut, a drop pedig a hely régió-ütemezőjén (a drop-listát az event
        // lefutása után már nem lehet módosítani).
        kill.runOnKiller(plugin, killer -> {
            if (!itemIdentityService.hasRune(killer.getInventory().getItemInMainHand(), "runa_moho")) {
                return;
            }
            final ItemStack pouch = payout(kill, amount);
            if (pouch == null) {
                return;
            }
            final Location at = kill.victimLocation();
            Bukkit.getRegionScheduler().run(plugin, at, task -> world.dropItemNaturally(at, pouch));
        });
    }

    private long rollAmount(final Random rng, final int mobLevel) {
        final double min = Math.max(0.01D, configManager.getDouble("mob-money-drop.min-amount", 1.0D));
        final double max = Math.max(min, configManager.getDouble("mob-money-drop.max-amount", 4.0D));
        final double perLevel = Math.max(0.0D, configManager.getDouble("mob-money-drop.per-level-bonus", 0.5D));
        return Math.max(1L, Math.round(min + rng.nextDouble() * (max - min) + (mobLevel - 1) * perLevel));
    }

    /**
     * Egy ölés = egy erszény: a retesz azért kell, mert a kifizetés két szálon ágazhat el
     * (alap-sáv az áldozat szálán, rúna-sáv a gyilkos hopjában). A napi keret ezután fogy,
     * hogy az elutasított kísérlet ne könyveljen.
     *
     * @return a kifizetendő erszény, vagy {@code null}, ha ez az ölés már fizetett / kimerült a keret
     */
    private ItemStack payout(final MobKillUtil.KillContext kill, final long amount) {
        if (!kill.claimOnce("mob-money") || !tryConsumeDailyBudget(kill.killerId(), amount)) {
            return null;
        }
        return pouchFactory.createRandom(amount);
    }

    /**
     * Napi keret (mob-money-drop.daily-cap, 0 = korlátlan): a spawner-fék a darkroom-
     * farmokat nem fogja (NATURAL spawn), ezért ez a plafon zárja a végtelen csapot.
     * Memóriás tároló, mert a halál-event a MOB régió-szálán fut — a gyilkos PDC-jébe
     * innen nem írhatunk.
     */
    private final hu.taliann.icesmp.utils.DailyBudget.InMemory<java.util.UUID> moneyBudget =
            new hu.taliann.icesmp.utils.DailyBudget.InMemory<>(512);

    private boolean tryConsumeDailyBudget(final java.util.UUID playerId, final long amount) {
        return moneyBudget.tryConsume(playerId, amount,
                configManager.getDouble("mob-money-drop.daily-cap", 300.0D));
    }
}
