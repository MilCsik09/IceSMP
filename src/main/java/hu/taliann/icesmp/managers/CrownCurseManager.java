package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.data.FactionType;
import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * A Néma Királynő átka a koronán (kódex IV. + „Az Elveszett Uralkodók" függelék): Eleftheria átka
 * „a világ minden koronás főjét sírba vitte", és a négy Elveszett Uralkodó nevét ma már csak a
 * legendás tárgyak őrzik. A király-rendszer ezt eddig nem tudta — ez a manager köti a lore-hoz.
 *
 * <p><b>Hogyan működik:</b> az átok szintje a TRÓNON TÖLTÖTT IDŐBŐL számolódik, nem külön
 * állapotból — a {@link KingManager} amúgy is tartja a trónra lépés idejét, és trónfosztáskor/
 * újraválasztáskor azt nullázza, tehát az átok magától tisztul. Nincs új perzisztencia.
 *
 * <p>Az alsó szintek CSAK hangulat (suttogás + lich-türkiz derengés); a felsőkön van tét
 * (élőhalott-vonzás, gyengülő gyógyulás). Így a hosszú uralkodás érezhetően kockázatosabb —
 * beépített fék az örökös királyra, a ciklus-lejárat mellé.
 *
 * <p><b>Folia:</b> a tick a globál szálon fut, a király viszont MÁS entitás — minden érintés
 * (üzenet, partikel, effekt, közeli mobok) a király SAJÁT régió-szálán történik.
 */
public final class CrownCurseManager {

    /**
     * A Királynő suttogásai SZINTENKÉNTI készletekben. Nem díszítés: ez az egyik hely, ahol a
     * játékos a kódex elolvasása NÉLKÜL is megismeri a világ történetét — ezért a szintek
     * fokozatosan tárnak fel többet (1: puszta nyugtalanság → 5: nyílt ítélet), és minden sor
     * egy konkrét kánon-elemet tanít (Hasadás, a Káoszkor 698-as éve, az Elveszett Uralkodók
     * nevei, Eleftheria mibenléte, a Fa kínja).
     */
    private static final String[][] WHISPERS_BY_LEVEL = {
            // 1. szint — még csak nyugtalanság; a korona hidegebb, mint kellene.
            {
                    "„Egy korona csak egy kör. Minden kör bezárul.”",
                    "„Hallom a fémet a fejeden. Ismerem a hangját.”",
                    "„Ne aludj benne. Ami koronában alszik, azt könnyebb megtalálni.”",
                    "„Milyen érdekes. Megint valaki, aki azt hiszi, ő lesz az első.”",
                    "„Nézd meg a kezed. Már hidegebb, ugye?”",
                    "„A holtak nem irigyek. Csak türelmesek.”",
                    "„Nem szólok hangosan. Nem kell. Te már hallgatózol.”",
                    "„Fáradt vagy? Az még nem én vagyok. Az még csak a súly.”"
            },
            // 2. szint — a Káoszkor és a bukott koronák. Konkrét évszám: Hu. 698.
            {
                    "„Hatszázkilencvennyolc. Azóta egyetlen korona sem öregedett meg.”",
                    "„Az ég akkor hasadt ketté, amikor felébredtem. Azt hitték, vihar.”",
                    "„A nemesség nem elbukott. Eltöröltem. Az más munka.”",
                    "„A Hetedik Vérháborúban két sereg vonult ki. Egy sem jött vissza — csak megtanult járni.”",
                    "„Előtted is voltak koronások. Kérdezd meg őket. Ja, igen.”",
                    "„A trónok nem üresek, tudod. Csak nem látod, ki üldögél bennük.”",
                    "„A Káoszkor nem katasztrófa volt. Rendezés volt.”",
                    "„Minden királyság vezet egy lajstromot a győzelmeiről. Én egy másikat vezetek.”"
            },
            // 3. szint — az Elveszett Uralkodók NEVEI (a lajstrom lapjai).
            {
                    "„I. Zhoris a lángmadarai tollából köpenyt szőtt. Melegen tartotta. Nem elég melegen.”",
                    "„V. Miinus haragját acélba kovácsolták. A harag megmaradt. A kéz nem.”",
                    "„I. Benedictus és I. Lineata büszke seregei ma is rójják az utakat. Csak lassabban.”",
                    "„Zhoris is ezt hitte. Miinus is. Mindketten a lajstromban vannak.”",
                    "„Tudod, mi a közös a legendás fegyverekben? Mind egy halott nevét viseli.”",
                    "„A sárkánykirály örököse nem sárkánytól halt meg. Csak megvárta a reggelt.”",
                    "„Ha egy pengét egy uralkodóról neveznek el, az azt jelenti: az uralkodó már nem kell hozzá.”",
                    "„Nem kell mind a négy nevet megtanulnod. Az ötödiket jegyezd meg — az a tiéd.”"
            },
            // 4. szint — Eleftheria mibenléte: a Fa gyermeke, az első suttogás, a Könny.
            {
                    "„Én is a Fa gyermeke voltam. Négyen voltunk. Csak engem hagytak a mélyben.”",
                    "„Soleil lángot kapott. Kallan pikkelyt. Arkynn erdőt. Én csendet.”",
                    "„Az első szavam megkövült. Éjfekete csepp lett belőle — a Könnyem. Ha megtalálod, ne hallgasd meg.”",
                    "„A Fa nem gyógyult be a Hasadás után. Én vagyok a seb, ami beszél.”",
                    "„Nem gonoszságból ébredtem fel. Egyszerűen már senki nem tartott lent.”",
                    "„A gyökerek mélyebbre nyúlnak, mint az álmom. És mindkettő alattad van.”",
                    "„A Csend nem üres. A Csend vár. Ennyit tudnod elég.”",
                    "„Fél-álomban vagyok, koronás. Képzeld el, mi lesz, ha kinyitom a szemem.”"
            },
            // 5. szint — nyílt ítélet: a lajstrom, a harmadik mondat, a Fa kínja.
            {
                    "„Két mondatot kimondtam. Az első felébresztett. A második eltörölte a nemességet.”",
                    "„A harmadik mondat még megvan. Tartogatom. Nem neked — de te is benne leszel.”",
                    "„A lajstromban van egy üres sor. Már a nevedet formálja.”",
                    "„Ne siess. Én sem sietek. Nekem hatszáz évem volt gyakorolni a várakozást.”",
                    "„Érzed? Ez nem a tél. Ez én vagyok, ahogy közelebb hajolok.”",
                    "„A koronát leteheted. A koronát MINDIG le lehet tenni. Ezt szokták a legkésőbb megérteni.”",
                    "„Amit a Fa kínjából magamba zártam, azt most rajtad keresztül számolom vissza.”",
                    "„Emlékszel a nevekre? Zhoris. Miinus. Benedictus. Lineata. …és?”"
            }
    };

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final KingManager kingManager;
    private final MessageManager messageManager;

    /** király -> a legutóbb NEKI kihirdetett szint (hogy a szint-lépés egyszer szóljon). */
    private final Map<UUID, Integer> announcedLevel = new java.util.concurrent.ConcurrentHashMap<>();

    public CrownCurseManager(final JavaPlugin plugin, final ConfigManager configManager,
                             final KingManager kingManager, final MessageManager messageManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.kingManager = kingManager;
        this.messageManager = messageManager;
    }

    public boolean isEnabled() {
        return configManager.getBoolean("factions.kings.crown-curse.enabled", true);
    }

    /**
     * Az átok szintje a trónon töltött idő alapján (0 = nincs átok).
     * Offline királyra is számol — az idő akkor is telik.
     */
    public int curseLevel(final FactionType faction) {
        if (!isEnabled() || faction == null || kingManager.getKing(faction) == null) {
            return 0;
        }
        final long hoursPerLevel = Math.max(1L,
                configManager.getLong("factions.kings.crown-curse.hours-per-level", 24L));
        final int maxLevel = Math.max(1,
                configManager.getInt("factions.kings.crown-curse.max-level", 5));
        final long enthronedMillis = kingManager.millisOnThrone(faction);
        if (enthronedMillis <= 0L) {
            return 0;
        }
        final long level = enthronedMillis / (hoursPerLevel * 3_600_000L);
        return (int) Math.max(0L, Math.min(maxLevel, level));
    }

    /** Periodikus lépés a világesemény-kötegből. */
    public void tick() {
        if (!isEnabled()) {
            return;
        }
        // A leváltott királyok bejegyzése nem ragadhat bent (UUID-kulcsú map nem szivároghat):
        // minden körben csak az AKTUÁLIS koronás fők maradnak.
        if (!announcedLevel.isEmpty()) {
            final java.util.Set<UUID> current = new java.util.HashSet<>();
            for (final FactionType faction : FactionType.values()) {
                final UUID king = kingManager.getKing(faction);
                if (king != null) {
                    current.add(king);
                }
            }
            announcedLevel.keySet().retainAll(current);
        }
        for (final FactionType faction : FactionType.values()) {
            final UUID kingId = kingManager.getKing(faction);
            if (kingId == null) {
                continue; // Nincs király ebben a frakcióban.
            }
            final int level = curseLevel(faction);
            if (level <= 0) {
                announcedLevel.remove(kingId);
                continue;
            }
            final Player king = Bukkit.getPlayer(kingId);
            if (king == null) {
                continue; // Offline király: az idő telik, az átok majd a belépésnél folytatódik.
            }
            // Folia: a király MÁS entitás, mint a tick szála — minden érintés a saját szálán.
            king.getScheduler().run(plugin, task -> applyCurse(king, faction, level), null);
        }
    }

    private void applyCurse(final Player king, final FactionType faction, final int level) {
        final Integer announced = announcedLevel.get(king.getUniqueId());
        if (announced == null || announced < level) {
            announcedLevel.put(king.getUniqueId(), level);
            king.sendMessage(messageManager.getMessage("crown-curse-level",
                    "<dark_aqua>👑 A korona hidegebb lett. <gray>(A Néma Királynő figyelme: {level}/{max})</gray></dark_aqua>",
                    Map.of("level", String.valueOf(level),
                            "max", String.valueOf(Math.max(1, configManager
                                    .getInt("factions.kings.crown-curse.max-level", 5))))));
        }
        // Hangulat MINDEN szinten: lich-türkiz derengés a fej körül (a DARK vizuális akcent).
        hu.taliann.icesmp.utils.ParticleUtil.spawn(king.getWorld(), Particle.SOUL_FIRE_FLAME,
                king.getLocation().add(0.0D, 2.1D, 0.0D), level, 0.25D, 0.1D, 0.25D, 0.0D);
        final int whisperChance = Math.max(0,
                configManager.getInt("factions.kings.crown-curse.whisper-percent", 12));
        if (ThreadLocalRandom.current().nextInt(100) < whisperChance) {
            final int tier = Math.min(WHISPERS_BY_LEVEL.length - 1, Math.max(0, level - 1));
            final String[] pool = WHISPERS_BY_LEVEL[tier];
            final int index = ThreadLocalRandom.current().nextInt(pool.length);
            // SORONKÉNTI messages-kulcs (a campfire-story minta): így minden suttogás külön
            // átírható a szerveren, és egy felülírás nem fagyasztja be az egész készletet.
            king.sendMessage(messageManager.getMessage(
                    "crown-curse-whisper-" + (tier + 1) + "-" + (index + 1),
                    "<dark_aqua><italic>" + pool[index] + "</italic></dark_aqua>"));
        }

        // TÉT csak a felső szinteken — az alsók szándékosan csak hangulat.
        final int stakesFrom = Math.max(1,
                configManager.getInt("factions.kings.crown-curse.stakes-from-level", 3));
        if (level < stakesFrom) {
            return;
        }
        // 1) A korona elszívja az életerőt: HUNGER — a jóllakottság fogyása leállítja a
        // TERMÉSZETES regenerációt (nem sebez, nem halálos), tehát a hosszú uralkodás
        // sebezhetőbbé tesz anélkül, hogy büntetés-spirált indítana.
        if (configManager.getBoolean("factions.kings.crown-curse.weaken-healing", true)) {
            final int seconds = Math.max(5,
                    configManager.getInt("factions.kings.crown-curse.effect-seconds", 30));
            king.addPotionEffect(new PotionEffect(PotionEffectType.HUNGER, seconds * 20,
                    level - stakesFrom, true, false, false));
        }
        // 2) Élőhalott-vonzás: a közeli, céltalan élőhalottak a koronát keresik.
        if (!configManager.getBoolean("factions.kings.crown-curse.undead-attraction", true)) {
            return;
        }
        final double radius = Math.max(4.0D,
                configManager.getDouble("factions.kings.crown-curse.attraction-radius", 24.0D));
        for (final org.bukkit.entity.Entity nearby : king.getNearbyEntities(radius, radius, radius)) {
            if (!(nearby instanceof Mob mob) || !hu.taliann.icesmp.utils.UndeadUtil.isUndead(mob)) {
                continue;
            }
            // A közeli entitások a király régiójában vannak (getNearbyEntities régió-lokális),
            // de a célzás mutáció: az adott mob saját schedulerén végezzük.
            mob.getScheduler().run(plugin, task -> {
                if (mob.getTarget() == null) {
                    mob.setTarget(king);
                }
            }, null);
        }
    }
}
