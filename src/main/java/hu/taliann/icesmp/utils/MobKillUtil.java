package hu.taliann.icesmp.utils;

import hu.taliann.icesmp.managers.AfkManager;
import hu.taliann.icesmp.managers.ConfigManager;
import hu.taliann.icesmp.managers.MinionManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Random;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Közös kill-jutalom előszűrő. Az egyes szűrők (AFK-fék, spawner-kizárás, minion-kizárás,
 * survival-kapu) korábban listenerenként külön-külön voltak megírva, ezért rendszertől
 * függően hol éltek, hol nem — ugyanaz az ölés az egyik jutalmat kifizette, a másikat nem.
 * A {@link RewardKind} tier dönti el, egy jutalom-fajta melyik szűrőket kapja.
 *
 * <p>Folia: minden hívás az áldozat régió-szálán fut (EntityDeathEvent). Az áldozat PDC-je itt
 * biztonságos, a GYILKOSRÓL viszont semmit nem olvasunk közvetlenül — ő másik régió-szálhoz
 * tartozhat. A játékmód a {@link GameModeCache} konkurens tükréből, az AFK-állapot az
 * {@code AfkManager} UUID-kulcsos map-jéből jön, tehát nincs kereszt-száli entitás-érintés.
 * Ezért ad az {@link #eligibleKill} <b>{@link KillContext}</b> pillanatképet és nem élő
 * {@link Player}-t: a gyilkos entitás minden olvasása/mutációja csak a
 * {@link KillContext#runOnKiller} hopon belül szabad.
 *
 * <p><b>Hatókör.</b> Ez a garancia AZ ITT ÁTVEZETETT jutalom-utakra áll. A pozíció-alapú
 * megosztás (párt-XP {@code PartyManager.getNearbyMembers}, Vad Hajsza személyes loot) még
 * élő {@link Player}-t kap és az áldozat szálán olvas pozíciót — ott ma fail-open try/catch
 * fedi el a kereszt-régiós esetet (a megosztás némán elmarad). Az {@link #eligibleTrackingKiller}
 * ezért maradt meg: azt CSAK ilyen, még nem átvezetett hívó használhatja.
 */
public final class MobKillUtil {

    /**
     * A spawner-spawnokat a {@code MobMoneyDropListener.onSpawn} jelöli meg (a jelölés
     * entitás-PDC-ben él, ezért újraindítás-biztos). Itt csak OLVASSUK.
     */
    private static final NamespacedKey SPAWNER_MOB_KEY = NamespacedKey.fromString("icesmp:spawner_mob");

    /**
     * Kifizetés-retesz KILL-szinten: {@code áldozat-UUID + csatorna} → a nyertes hívás ideje.
     * Statikus, mert egy halálra több {@link KillContext} is készül, és a reteszt köztük kell
     * megosztani. Öntakarító: minden reteszelésnél kiesnek a {@value #CLAIM_TTL_MILLIS}-nál
     * régebbi bejegyzések, plusz kemény felső korlát véd a korlátlan növekedés ellen.
     */
    private static final java.util.concurrent.ConcurrentHashMap<String, Long> CLAIMED =
            new java.util.concurrent.ConcurrentHashMap<>();
    private static final long CLAIM_TTL_MILLIS = 60_000L;
    private static final int CLAIM_CAP = 4096;

    private MobKillUtil() {
    }

    /**
     * A retesz-tábla takarítása. Minden {@code claimOnce} elején fut: a jutalom-ágak egy-két
     * tick alatt lezajlanak, ezért egy percnél régebbi bejegyzésre már senki nem hivatkozik.
     */
    private static void pruneClaims() {
        if (CLAIMED.isEmpty()) {
            return;
        }
        final long cutoff = System.currentTimeMillis() - CLAIM_TTL_MILLIS;
        CLAIMED.values().removeIf(stamp -> stamp < cutoff);
        if (CLAIMED.size() > CLAIM_CAP) {
            // Vészfék: ha valami mégis felhalmozná, inkább ürítünk (a retesz elvesztése
            // legfeljebb egy dupla kifizetést engedhet, a memória-szivárgás viszont fatális).
            CLAIMED.clear();
        }
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
     * Egy jutalmazható ölés szál-független pillanatképe. CSAK immutable adatot hordoz (gyilkos-UUID,
     * áldozat-típus, hely-koordináták, drop-mag) — élő {@link Player} SZÁNDÉKOSAN nincs benne, mert a
     * példány az ÁLDOZAT régió-szálán készül, a gyilkos pedig másik szálhoz tartozhat.
     *
     * <p>A {@link #dropRandom(String)} a kill-maghoz kötött, ezért ugyanaz a csatorna ugyanazt a
     * sorozatot adja az áldozat szálán és a gyilkos hopjában is: így egyetlen sorsolás alapján
     * dönthető el az a jutalom-sáv, amelyhez a gyilkos állapotát is olvasni kell — kereszt-száli
     * dupla-fizetés nélkül.
     */
    public static final class KillContext {

        private final UUID killerId;
        private final UUID victimId;
        private final EntityType victimType;
        private final World victimWorld;
        private final double victimX;
        private final double victimY;
        private final double victimZ;
        private final long dropSeed;


        private KillContext(final UUID killerId, final LivingEntity victim) {
            this.killerId = killerId;
            this.victimId = victim.getUniqueId();
            this.victimType = victim.getType();
            final Location location = victim.getLocation();
            this.victimWorld = location.getWorld();
            this.victimX = location.getX();
            this.victimY = location.getY();
            this.victimZ = location.getZ();
            // KILL-szintű mag: kizárólag az áldozat azonosítójából. A nanoTime bekeverése
            // példány-szintűvé tette volna, holott a szerződés kill-szintű determinizmust ígér
            // (egy halálra több KillContext is készül — lásd MobLootListener).
            this.dropSeed = victimId.getMostSignificantBits() ^ victimId.getLeastSignificantBits();
        }

        /** @return a jutalomra jogosult gyilkos UUID-je (map-kulcsként bármely szálról használható) */
        public UUID killerId() {
            return killerId;
        }

        /** @return az áldozat UUID-je */
        public UUID victimId() {
            return victimId;
        }

        /** @return az áldozat entitás-típusa */
        public EntityType victimType() {
            return victimType;
        }

        /** @return az áldozat világa, vagy {@code null}, ha a hely-pillanatkép világa eltűnt */
        public World victimWorld() {
            return victimWorld;
        }

        /**
         * @return friss {@link Location} példány az áldozat halál-helyéről (a pillanatkép
         *         koordinátákat tárol, hogy egyetlen mutálható Location se osztódjon meg szálak közt)
         */
        public Location victimLocation() {
            return new Location(victimWorld, victimX, victimY, victimZ);
        }

        /** @return a kill drop-magja (csatornánkénti sorsoláshoz) */
        public long dropSeed() {
            return dropSeed;
        }

        /**
         * @param channel jutalom-csatorna azonosítója (pl. {@code "mob-money"})
         * @return a kill-maghoz és a csatornához kötött sorsoló — ugyanarra a killre és csatornára
         *         ugyanazt a sorozatot adja, bármelyik régió-szálról kérjük
         */
        public Random dropRandom(final String channel) {
            return new Random(dropSeed * 31L + (channel == null ? 0 : channel.hashCode()));
        }

        /**
         * Egy ölés = egy kifizetés csatornánként. A retesz KILL-szintű (áldozat-UUID + csatorna),
         * nem példány-szintű: egy halálra több {@code KillContext} is készülhet (a
         * {@code MobLootListener} ma is kettőt gyárt), és példány-szintű reteszen a második
         * context ugyanazért a killért újra fizetett volna. Akkor is helyes, ha a jutalomág két
         * szálon (áldozat-szál és gyilkos-hop) ágazik el.
         *
         * @return true, ha ez a hívás nyerte el a kifizetés jogát
         */
        public boolean claimOnce(final String channel) {
            pruneClaims();
            return CLAIMED.putIfAbsent(victimId + "\u0000" + channel, System.currentTimeMillis()) == null;
        }

        /**
         * A gyilkos entitásának minden olvasása/mutációja (PDC, inventory, kéz, üzenet) ezen belül
         * fut, a gyilkos SAJÁT régió-szálán — az áldozat szálán ez idegen entitás érintése lenne.
         * Kilépett vagy már nem elérhető gyilkosnál nem hív semmit.
         */
        public void runOnKiller(final JavaPlugin plugin, final Consumer<Player> action) {
            if (plugin == null || action == null) {
                return;
            }
            final Player killer = Bukkit.getPlayer(killerId);
            if (killer == null) {
                return;
            }
            killer.getScheduler().run(plugin, task -> action.accept(killer), null);
        }
    }

    /**
     * @return a jutalmazható ölés pillanatképe, vagy {@code null}, ha az ölés nem jutalmazható
     *         (nincs játékos-gyilkos, vagy valamelyik tier-szűrő kizárja)
     */
    public static KillContext eligibleKill(final LivingEntity victim, final RewardKind kind,
                                           final ConfigManager configManager, final AfkManager afkManager) {
        if (victim == null) {
            return null;
        }
        // A getKiller() az ÁLDOZAT állapotát olvassa (itt vagyunk a szálán); a visszakapott
        // referencia nem szivárog ki, csak az UUID-jét vesszük át a pillanatképbe.
        final Player killer = victim.getKiller();
        if (killer == null) {
            return null;
        }
        final UUID killerId = killer.getUniqueId();
        // Saját idézett minion/horda leölése egyetlen jutalmat sem fizet — minden tierre érvényes.
        if (excludeMinions(configManager) && MinionManager.isMinionTagged(victim)) {
            return null;
        }
        if (kind != RewardKind.TRACKING) {
            // A játékmód a konkurens tükörből jön: a gyilkos MÁSIK régió-szálhoz tartozhat, a
            // getGameMode() közvetlen olvasása idegen entitás érintése lenne. A döntés nem
            // odázható el (a listenerek a halál-eventben, helyben döntenek a jutalomról).
            if (requireSurvival(configManager) && !GameModeCache.isSurvival(killerId)) {
                return null;
            }
            // Az afkManager a DI-sorrendben később épül, mint néhány listener — a null itt
            // fail-open (nincs AFK-adat → nem szűrünk), nem néma jutalom-elvonás.
            if (afkManager != null && blockAfkRewards(configManager) && afkManager.isAfk(killerId)) {
                return null;
            }
        }
        if (kind == RewardKind.FAUCET && excludeSpawnerMobs(configManager) && isSpawnerSpawned(victim)) {
            return null;
        }
        return new KillContext(killerId, victim);
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
