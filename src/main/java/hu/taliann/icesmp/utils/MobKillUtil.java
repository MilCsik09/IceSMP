package hu.taliann.icesmp.utils;

import hu.taliann.icesmp.managers.AfkManager;
import hu.taliann.icesmp.managers.ConfigManager;
import hu.taliann.icesmp.managers.MinionManager;
import org.bukkit.GameMode;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

/**
 * Közös kill-jutalom előszűrő. Az egyes szűrők (AFK-fék, spawner-kizárás, minion-kizárás,
 * survival-kapu) korábban listenerenként külön-külön voltak megírva, ezért rendszertől
 * függően hol éltek, hol nem — ugyanaz az ölés az egyik jutalmat kifizette, a másikat nem.
 * A {@link RewardKind} tier dönti el, egy jutalom-fajta melyik szűrőket kapja.
 *
 * <p>Folia: minden hívás az áldozat régió-szálán fut (EntityDeathEvent), és csak az áldozat
 * PDC-jét + a gyilkos memóriabeli állapotát olvassa — nincs kereszt-száli entitás-érintés.
 */
public final class MobKillUtil {

    /**
     * A spawner-spawnokat a {@code MobMoneyDropListener.onSpawn} jelöli meg (a jelölés
     * entitás-PDC-ben él, ezért újraindítás-biztos). Itt csak OLVASSUK.
     */
    private static final NamespacedKey SPAWNER_MOB_KEY = NamespacedKey.fromString("icesmp:spawner_mob");

    private MobKillUtil() {
    }

    /** Jutalom-fajta: ez dönti el, mennyire szigorú az előszűrő. */
    public enum RewardKind {

        /**
         * Valuta- vagy tárgy-csap (lélekkő, pénz-erszény, mob-loot, kazamata-lelet): a legszigorúbb
         * szűrés, mert ez termel új értéket a gazdaságba — AFK-fék, spawner- és minion-kizárás,
         * survival-kapu.
         */
        FAUCET,

        /**
         * Progresszió (kaszt-XP, lélekszilánk, pet-XP): AFK-fék, minion-kizárás, survival-kapu.
         * A spawner-mob NEM kizárt — a mob-farmos szintezés szándékos MMO-minta.
         */
        PROGRESSION,

        /**
         * Számláló/haladás (statisztika, quest-haladás, bestiárium, közösségi cél): csak a
         * minion-kizárás él, hogy a saját idézett hordája ne pörgesse a számlálókat. AFK és
         * kreatív mód nem blokkol — a quest-haladás adminként is tesztelhető maradjon.
         */
        TRACKING
    }

    /**
     * @return a jutalomra jogosult gyilkos, vagy {@code null}, ha az ölés nem jutalmazható
     *         (nincs játékos-gyilkos, vagy valamelyik tier-szűrő kizárja)
     */
    public static Player eligibleKiller(final LivingEntity victim, final RewardKind kind,
                                        final ConfigManager configManager, final AfkManager afkManager) {
        if (victim == null) {
            return null;
        }
        final Player killer = victim.getKiller();
        if (killer == null) {
            return null;
        }
        // Saját idézett minion/horda leölése egyetlen jutalmat sem fizet — minden tierre érvényes.
        if (excludeMinions(configManager) && MinionManager.isMinionTagged(victim)) {
            return null;
        }
        if (kind != RewardKind.TRACKING) {
            if (requireSurvival(configManager) && killer.getGameMode() != GameMode.SURVIVAL) {
                return null;
            }
            // Az afkManager a DI-sorrendben később épül, mint néhány listener — a null itt
            // fail-open (nincs AFK-adat → nem szűrünk), nem néma jutalom-elvonás.
            if (afkManager != null && blockAfkRewards(configManager)
                    && afkManager.isAfk(killer.getUniqueId())) {
                return null;
            }
        }
        if (kind == RewardKind.FAUCET && excludeSpawnerMobs(configManager) && isSpawnerSpawned(victim)) {
            return null;
        }
        return killer;
    }

    /**
     * TRACKING-tier előszűrő config/AFK-manager nélkül — a számláló-listenerekhez, amelyek
     * nem tartanak ConfigManager-referenciát. A minion-kizárás itt FELTÉTEL NÉLKÜL él: a
     * ranglista, a közösségi cél és a quest-számláló integritása nem konfigurációs kérdés
     * (a saját idézett hordával pumpált számláló minden esetben exploit).
     *
     * @return a számlálóhoz jogosult gyilkos, vagy {@code null}
     */
    public static Player eligibleTrackingKiller(final LivingEntity victim) {
        if (victim == null) {
            return null;
        }
        final Player killer = victim.getKiller();
        if (killer == null || MinionManager.isMinionTagged(victim)) {
            return null;
        }
        return killer;
    }

    /** @return true, ha az entitást spawner hozta létre (PDC-jelölés a spawn-eventből) */
    public static boolean isSpawnerSpawned(final Entity entity) {
        return entity != null && SPAWNER_MOB_KEY != null
                && entity.getPersistentDataContainer().has(SPAWNER_MOB_KEY, PersistentDataType.BYTE);
    }

    private static boolean blockAfkRewards(final ConfigManager configManager) {
        if (configManager == null) {
            return true;
        }
        // A legacy afk.block-rewards az alapérték, hogy a meglévő szerver-configok ne váltsanak
        // viselkedést az új kulcs bevezetésével.
        return configManager.getBoolean("kill-rewards.afk-block",
                configManager.getBoolean("afk.block-rewards", true));
    }

    private static boolean excludeSpawnerMobs(final ConfigManager configManager) {
        return configManager == null || configManager.getBoolean("kill-rewards.exclude-spawner-mobs", true);
    }

    private static boolean excludeMinions(final ConfigManager configManager) {
        return configManager == null || configManager.getBoolean("kill-rewards.exclude-minions", true);
    }

    private static boolean requireSurvival(final ConfigManager configManager) {
        return configManager == null || configManager.getBoolean("kill-rewards.require-survival", true);
    }
}
