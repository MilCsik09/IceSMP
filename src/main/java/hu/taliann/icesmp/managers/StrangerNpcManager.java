package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.utils.MessageManager;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.WanderingTrader;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * A Rejtélyes Idegen: helyi, jutalom nélküli lore-esemény. A konkrét helyet a
 * központi spawnőr választja több jelöltből, ezért az NPC nem materializálódhat
 * vízen, tiltott biomon vagy a horgony játékos aktuális látóirányában.
 */
public final class StrangerNpcManager {
    private static final long SPAWN_GRACE_MILLIS = 30_000L;

    private static final String[] LINES = {
            "<dark_gray>„A Fa gyökerei mélyebbre nyúlnak, mint a Királynő álma… és mindkettő alattad van.”</dark_gray>",
            "<dark_gray>„Hallod? A Csend nem üres. A Csend vár.”</dark_gray>",
            "<dark_gray>„Két tűz közt jársz, vándor. A harmadik benned ég.”</dark_gray>",
            "<dark_gray>„A lapok fordulnak. Volt, aki a margóra írt — és a Könyv visszaírt neki.”</dark_gray>",
            "<dark_gray>„A Kapu nem nyílik. A Kapu emlékszik.”</dark_gray>",
            "<dark_gray>„Nem engem kerestél. De lehet, hogy én kerestelek téged.”</dark_gray>",
            "<dark_gray>„A Mélység Népe nem halt ki. Számol. És mindjárt végez.”</dark_gray>",
            "<dark_gray>„Minden tűz kialszik egyszer. A tiéd is. Az övé nem.”</dark_gray>",
            "<dark_gray>„Láttam a nevedet a Könyvben. Két helyen. Az egyik át van húzva.”</dark_gray>",
            "<dark_gray>„Amit a jég megőriz, azt a láng irigyli. Te melyiké vagy?”</dark_gray>",
            "<dark_gray>„A Fa gyógyul. Kérdezd meg magadtól: minek örül ennyire a Kapu?”</dark_gray>",
            "<dark_gray>„Nem minden szárny visz haza. Némelyik csak messzebb visz.”</dark_gray>",
            "<dark_gray>„Számoltad már, hány lépés a fővárostól a Kapuig? Ő számolta. Kétszer.”</dark_gray>",
            "<dark_gray>„A pénzed csörög. A bűneid nem. Mégis az utóbbit hallani messzebbre.”</dark_gray>",
            "<dark_gray>„Egy korszak múlva senki sem emlékszik rád. Kivéve, ha Ő emlékszik.”</dark_gray>",
            "<dark_gray>„A tábortüzeknél rólam mesélnek. Kíváncsi vagyok, melyik igaz. Én már elfelejtettem.”</dark_gray>",
            "<dark_gray>„A jég alatt is folyik a víz. A csend alatt is szól a hang. Csak füle kell.”</dark_gray>",
            "<dark_gray>„Nem árnyék vagyok. Az árnyékhoz fény kell. Én régebbi vagyok annál.”</dark_gray>",
            "<dark_gray>„A Bankárszövetség egyszer nekem is számlát nyitott. Még mindig nyitva van. Üresen.”</dark_gray>",
            "<dark_gray>„Kérdezd meg a bárdot, ki tanította az első dalt. Nézd meg, elsápad-e.”</dark_gray>",
            "<dark_gray>„A Kárhozat Kapuja nem bejárat, vándor. Kijárat. Ezt kevesen értik.”</dark_gray>",
            "<dark_gray>„Ma jó napod lesz. Ezt nem jóslatként mondom. Bocsánatkérésként.”</dark_gray>",
            "<dark_gray>„A gyökerek városában születtél újra. A kérdés csak az: melyik ágon halsz meg.”</dark_gray>",
            "<dark_gray>„Két Caldestera van. Az egyik elfelejtette, miért ment el. A másik, hogy miért maradt.”</dark_gray>",
            "<dark_gray>„A vérfák nem a nemességet táplálják, vándor. A nemesség táplálja őket.”</dark_gray>",
            "<dark_gray>„Olethropyla. Régen így hívtuk. Én még emlékszem, KI adta ezt a nevet.”</dark_gray>",
            "<dark_gray>„A holtak városának két neve van. Az élők adták mindkettőt. A holtak nem adnak nevet semminek.”</dark_gray>",
            "<dark_gray>„A komp a szoroson jár. Én a szoros alatt jártam. Nem ajánlom.”</dark_gray>",
            "<dark_gray>„Ha legközelebb találkozunk, ne köszönj. Úgy tovább beszélgethetünk.”</dark_gray>",
            "<dark_gray>„A Számvevők a pénzt írják. A Könyv a döntéseket. Csak az egyiket lehet visszafizetni.”</dark_gray>",
            "<dark_gray>„Négy uralkodó neve maradt fenn: Zhoris, Miinus, Benedictus, Lineata. Kérdezd meg, hol vannak.”</dark_gray>",
            "<dark_gray>„Majd’ három évszázada nem halt meg koronás fő öregségben. Ezt nevezik ők rendnek.”</dark_gray>",
            "<dark_gray>„A Fának négy gyermeke volt. Hármat imádnak. A negyediket kifelejtették — de ő emlékszik.”</dark_gray>",
            "<dark_gray>„Soleil lángot vitt, Kallan pikkelyt, Arkynn erdőt. Eleftheria csendet kapott. Melyik ajándék tartott ki?”</dark_gray>",
            "<dark_gray>„A csillag, ami a Fát ültette, még mindig ott van a földben. Néha kőként hullik vissza az égből.”</dark_gray>",
            "<dark_gray>„Az első évben megrepedt a világ. Azóta minden térkép egy seb két partja.”</dark_gray>",
            "<dark_gray>„Kétfelé szórtak minket, hogy ne öljük egymást. Ügyes terv volt. Hét háborúig működött.”</dark_gray>",
            "<dark_gray>„A Kárhozat Kapuja nem bejárat. Kifelé nyílik — és nem a te oldaladról.”</dark_gray>",
            "<dark_gray>„A rontás alulról nő. Ezért nem segít, ha felfelé nézel, amikor rossz szagot érzel.”</dark_gray>",
            "<dark_gray>„A Fa adott neked egy tárgyat, amit nem tudsz eldobni. Gondolkodj el rajta, miért nem.”</dark_gray>",
            "<dark_gray>„Ti visszatértek a halálból. Mi nem. Ez nem igazságtalanság — ez üzlet, és valaki fizeti.”</dark_gray>",
            "<dark_gray>„Vannak, akik már hallgatnak rá, és nappal a te asztalodnál esznek.”</dark_gray>",
            "<dark_gray>„A Suttogók nem gonoszak. Csak korábban feleltek egy kérdésre, amit neked még nem tettek fel.”</dark_gray>",
            "<dark_gray>„A vérhold nem jel. Mozdulás. Ő fordul meg álmában, és a világ megbillen.”</dark_gray>",
            "<dark_gray>„Két mondat elhangzott. A harmadikra készül. Tudod, mit tesz addig? Vár. Mint én.”</dark_gray>"
    };

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final MessageManager messageManager;
    private final NamespacedKey strangerKey;
    private final hu.taliann.icesmp.utils.PeriodicChanceEvent schedule =
            new hu.taliann.icesmp.utils.PeriodicChanceEvent();

    private volatile EventSpawnGuard spawnGuard;
    private volatile UUID activeStrangerId;

    public StrangerNpcManager(final JavaPlugin plugin, final ConfigManager configManager,
                              final MessageManager messageManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.messageManager = messageManager;
        this.strangerKey = new NamespacedKey(plugin, "stranger_npc");
    }

    public void setSpawnGuard(final EventSpawnGuard spawnGuard) {
        this.spawnGuard = spawnGuard;
    }

    public boolean isStranger(final Entity entity) {
        return entity != null && entity.getPersistentDataContainer()
                .getOrDefault(strangerKey, PersistentDataType.BYTE, (byte) 0) == (byte) 1;
    }

    public void tick() {
        if (!configManager.getBoolean("world-events.stranger-npc.enabled", true)
                || strangerStanding()) {
            return;
        }
        final long intervalMillis = Math.max(1L, configManager.getLong(
                "world-events.stranger-npc.check-interval-minutes", 90L)) * 60_000L;
        if (!schedule.tryAttempt(intervalMillis, configManager.getDouble(
                "world-events.stranger-npc.chance-percent", 6.0D), SPAWN_GRACE_MILLIS)) {
            return;
        }
        final List<? extends Player> online = List.copyOf(Bukkit.getOnlinePlayers());
        if (online.isEmpty()) {
            schedule.release();
            return;
        }
        spawnNear(online.get(ThreadLocalRandom.current().nextInt(online.size())));
    }

    public boolean forceSpawn(final Player anchor) {
        if (strangerStanding() || !schedule.tryForce(SPAWN_GRACE_MILLIS)) {
            return false;
        }
        Player target = anchor;
        if (target == null) {
            final List<? extends Player> online = List.copyOf(Bukkit.getOnlinePlayers());
            if (online.isEmpty()) {
                schedule.release();
                return false;
            }
            target = online.get(ThreadLocalRandom.current().nextInt(online.size()));
        }
        spawnNear(target);
        return true;
    }

    private void spawnNear(final Player anchor) {
        anchor.getScheduler().run(plugin, task -> {
            final EventSpawnGuard guard = spawnGuard;
            if (guard == null) {
                schedule.release();
                return;
            }
            final Location origin = anchor.getLocation().clone();
            guard.findSafeNear("stranger", origin, System.nanoTime(),
                    this::placeStranger, schedule::release);
        }, schedule::release);
    }

    /** Runs on the selected location's region thread. */
    private void placeStranger(final Location spot) {
        if (spot.getWorld() == null || strangerStanding()) {
            schedule.release();
            return;
        }
        final WanderingTrader stranger = spot.getWorld().spawn(spot, WanderingTrader.class);
        stranger.getPersistentDataContainer().set(strangerKey, PersistentDataType.BYTE, (byte) 1);
        stranger.setAI(false);
        stranger.setSilent(true);
        stranger.setInvulnerable(true);
        stranger.setPersistent(false);
        stranger.setDespawnDelay(0);
        stranger.customName(messageManager.getMessage(
                "stranger-name", "<dark_gray>Az Idegen</dark_gray>"));
        stranger.setCustomNameVisible(true);
        hu.taliann.icesmp.utils.TransientEntities.register(plugin, stranger);
        activeStrangerId = stranger.getUniqueId();
        schedule.release();

        spot.getWorld().playSound(spot, Sound.AMBIENT_SOUL_SAND_VALLEY_MOOD, 0.8F, 0.6F);
        hu.taliann.icesmp.utils.ParticleUtil.spawn(spot.getWorld(), Particle.ASH,
                spot.clone().add(0.0D, 1.0D, 0.0D),
                20, 0.4D, 0.9D, 0.4D, 0.01D);

        final int index = ThreadLocalRandom.current().nextInt(LINES.length);
        final Component line = messageManager.getMessage(
                "stranger-line-" + (index + 1), LINES[index]);
        final double radius = Math.max(4.0D, configManager.getDouble(
                "world-events.profiles.stranger.whisper-radius",
                configManager.getDouble("world-events.stranger-npc.whisper-radius", 24.0D)));
        for (final Entity nearby : stranger.getNearbyEntities(radius, radius, radius)) {
            if (nearby instanceof Player player) {
                if (Bukkit.isOwnedByCurrentRegion(player)) {
                    player.sendMessage(line);
                    player.playSound(player.getLocation(),
                            Sound.AMBIENT_SOUL_SAND_VALLEY_MOOD, 0.5F, 1.5F);
                } else {
                    player.getScheduler().run(plugin, task -> {
                        player.sendMessage(line);
                        player.playSound(player.getLocation(),
                                Sound.AMBIENT_SOUL_SAND_VALLEY_MOOD, 0.5F, 1.5F);
                    }, null);
                }
            }
        }

        final long despawnTicks = Math.max(5L, configManager.getLong(
                "world-events.profiles.stranger.despawn-seconds",
                configManager.getLong("world-events.stranger-npc.despawn-seconds", 45L))) * 20L;
        stranger.getScheduler().runDelayed(plugin, task -> {
            if (stranger.isValid()) {
                hu.taliann.icesmp.utils.ParticleUtil.spawn(stranger.getWorld(), Particle.SOUL,
                        stranger.getLocation().add(0.0D, 1.0D, 0.0D),
                        30, 0.4D, 0.9D, 0.4D, 0.02D);
                stranger.getWorld().playSound(stranger.getLocation(),
                        Sound.PARTICLE_SOUL_ESCAPE, 1.0F, 0.7F);
                stranger.remove();
            }
            activeStrangerId = null;
        }, () -> activeStrangerId = null, despawnTicks);
    }

    private boolean strangerStanding() {
        final UUID id = activeStrangerId;
        if (id == null) {
            return false;
        }
        if (hu.taliann.icesmp.utils.TransientEntities.isAlive(id)) {
            return true;
        }
        activeStrangerId = null;
        return false;
    }

    public void shutdown() {
        final UUID id = activeStrangerId;
        activeStrangerId = null;
        schedule.release();
        hu.taliann.icesmp.utils.TransientEntities.removeById(plugin, id);
    }
}
