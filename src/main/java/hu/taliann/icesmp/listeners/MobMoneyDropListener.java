package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.items.MoneyPouchItemFactory;
import hu.taliann.icesmp.managers.ConfigManager;
import hu.taliann.icesmp.managers.MobScalingManager;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDeathEvent;

import java.util.concurrent.ThreadLocalRandom;

/**
 * WoW-stílusú pénz-drop: az ellenséges mob halálakor (játékos-öléssel) eséllyel Kopott
 * erszény esik — fizikai tárgy, véletlen frakció-valutával, az összeg a mob szintjével
 * skálázódik. Spawner-mob nem dob (farm-fék): a spawner-spawnokat entitás-PDC jelöli,
 * ami az újraindítást is túléli. Minden kulcs élőben olvasódik (mob-money-drop.*).
 * Folia: mindkét event a mob régió-szálán fut, a drop régió-lokális.
 */
public final class MobMoneyDropListener implements Listener {

    private final ConfigManager configManager;
    private final MobScalingManager mobScalingManager;
    private final MoneyPouchItemFactory pouchFactory;
    private final hu.taliann.icesmp.managers.AfkManager afkManager;
    private final org.bukkit.NamespacedKey spawnerMobKey;

    public MobMoneyDropListener(final org.bukkit.plugin.java.JavaPlugin plugin,
                                final ConfigManager configManager,
                                final MobScalingManager mobScalingManager,
                                final MoneyPouchItemFactory pouchFactory,
                                final hu.taliann.icesmp.managers.AfkManager afkManager) {
        this.configManager = configManager;
        this.mobScalingManager = mobScalingManager;
        this.pouchFactory = pouchFactory;
        this.afkManager = afkManager;
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
        final Player killer = hu.taliann.icesmp.utils.MobKillUtil.eligibleKiller(entity,
                hu.taliann.icesmp.utils.MobKillUtil.RewardKind.FAUCET, configManager, afkManager);
        if (killer == null) {
            return;
        }
        double chance = Math.max(0.0D, Math.min(100.0D,
                configManager.getDouble("mob-money-drop.chance-percent", 20.0D)));
        // Mohóság Rúnája a gyilkos fegyverén: drop-esély bónusz (százalékPONT).
        if ("runa_moho".equals(RuneEffectListener.runeOf(killer.getInventory().getItemInMainHand()))) {
            chance = Math.min(100.0D, chance
                    + Math.max(0.0D, configManager.getDouble("runes.runa_moho.money-drop-bonus-percent", 5.0D)));
        }
        if (ThreadLocalRandom.current().nextDouble(100.0D) >= chance) {
            return;
        }
        final double min = Math.max(0.01D, configManager.getDouble("mob-money-drop.min-amount", 1.0D));
        final double max = Math.max(min, configManager.getDouble("mob-money-drop.max-amount", 4.0D));
        final double perLevel = Math.max(0.0D, configManager.getDouble("mob-money-drop.per-level-bonus", 0.5D));
        final int mobLevel = mobScalingManager == null ? 1 : Math.max(1, mobScalingManager.getLevel(entity));
        final long amount = Math.max(1L, Math.round(min + ThreadLocalRandom.current().nextDouble() * (max - min)
                + (mobLevel - 1) * perLevel));
        if (!tryConsumeDailyBudget(killer.getUniqueId(), amount)) {
            return; // A napi mob-pénz keret elfogyott — a természetes farmok fékje.
        }
        event.getDrops().add(pouchFactory.createRandom(amount));
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
