package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.items.MoneyPouchItemFactory;
import hu.taliann.icesmp.managers.ConfigManager;
import hu.taliann.icesmp.managers.MobScalingManager;
import org.bukkit.GameMode;
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
    private final org.bukkit.NamespacedKey spawnerMobKey;

    public MobMoneyDropListener(final org.bukkit.plugin.java.JavaPlugin plugin,
                                final ConfigManager configManager,
                                final MobScalingManager mobScalingManager,
                                final MoneyPouchItemFactory pouchFactory) {
        this.configManager = configManager;
        this.mobScalingManager = mobScalingManager;
        this.pouchFactory = pouchFactory;
        this.spawnerMobKey = new org.bukkit.NamespacedKey(plugin, "spawner_mob");
    }

    @EventHandler(ignoreCancelled = true)
    public void onSpawn(final CreatureSpawnEvent event) {
        // Spawner-mobot megjelölünk, hogy a farm ne legyen pénznyomda.
        if (event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.SPAWNER) {
            event.getEntity().getPersistentDataContainer().set(spawnerMobKey,
                    org.bukkit.persistence.PersistentDataType.BYTE, (byte) 1);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDeath(final EntityDeathEvent event) {
        final LivingEntity entity = event.getEntity();
        if (!(entity instanceof Monster)
                || !configManager.getBoolean("mob-money-drop.enabled", true)
                || entity.getPersistentDataContainer().has(spawnerMobKey,
                        org.bukkit.persistence.PersistentDataType.BYTE)) {
            return;
        }
        final Player killer = entity.getKiller();
        if (killer == null || killer.getGameMode() != GameMode.SURVIVAL) {
            return;
        }
        // Saját idézett minion (nekromanta-horda stb.) leölése nem pénzcsap.
        if (hu.taliann.icesmp.managers.MinionManager.isMinionTagged(entity)) {
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

    /** játékos -> (nap, mai összeg) — memóriában él (restartkor nullázódik, a capnek elég). */
    private final java.util.Map<java.util.UUID, long[]> dailyEarned = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Napi keret (mob-money-drop.daily-cap, 0 = korlátlan): a spawner-fék a darkroom-
     * farmokat nem fogja (NATURAL spawn), ezért ez a plafon zárja a végtelen csapot.
     * Konkurrens map (a kill bármely régió-szálán futhat); a más-napi bejegyzések
     * túlcsordulásnál söprődnek.
     */
    private boolean tryConsumeDailyBudget(final java.util.UUID playerId, final long amount) {
        final double cap = configManager.getDouble("mob-money-drop.daily-cap", 300.0D);
        if (cap <= 0.0D) {
            return true;
        }
        final long today = System.currentTimeMillis() / 86_400_000L;
        if (dailyEarned.size() > 512) {
            dailyEarned.values().removeIf(entry -> entry[0] != today);
        }
        final long[] entry = dailyEarned.compute(playerId, (key, old) ->
                old == null || old[0] != today ? new long[]{today, amount} : new long[]{today, old[1] + amount});
        return entry[1] <= (long) cap;
    }
}
