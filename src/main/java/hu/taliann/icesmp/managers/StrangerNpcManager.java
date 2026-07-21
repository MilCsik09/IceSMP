package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.utils.MessageManager;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.WanderingTrader;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * D19 — a Rejtélyes Idegen (lore: a Suttogók toborzója vagy az Első Csend hírnöke —
 * kódex VII.). Ritkán, véletlen helyen és időben feltűnik egy név nélküli, csuklyás
 * alak: mond egy talányos sort a közelben állóknak, majd lélek-köddel eltűnik.
 * SZÁNDÉKOSAN nincs mechanikai jutalma — se bolt, se quest: tisztán atmoszféra,
 * a "láttátok az Idegent?" beszélgetésekért.
 *
 * <p>Megvalósítás: néma, mozdulatlan, sérthetetlen WANDERING_TRADER (kék köpenyes
 * vándor-sziluett), PDC-taggel; az interakciót a {@code StrangerListener} nyeli el
 * (nincs kereskedés). Folia: a tick a globális schedulerről a horgony-játékos, majd a
 * cél-hely régió-schedulerére hopol (világboss-minta); a despawn az entitás saját
 * schedulerén fut. Minden kulcs élőben olvasódik (world-events.stranger-npc.*).
 */
public final class StrangerNpcManager {

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final MessageManager messageManager;
    private final NamespacedKey strangerKey;
    /** Setter-injected (a guard később épül a DI-sorrendben); null = nincs hely-korlát. */
    private volatile EventSpawnGuard spawnGuard;

    private volatile long nextAttemptAt;
    private volatile UUID activeStrangerId;

    /** Talányos sor-variánsok (messages-kulcs: stranger-line-1..6). */
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
            "<dark_gray>„Ha legközelebb találkozunk, ne köszönj. Úgy tovább beszélgethetünk.”</dark_gray>"
    };

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

    /** Az entitás a (még élő) Idegen-e (az interakció-elnyelő listener szűrője). */
    public boolean isStranger(final Entity entity) {
        return entity != null && entity.getPersistentDataContainer()
                .getOrDefault(strangerKey, PersistentDataType.BYTE, (byte) 0) == (byte) 1;
    }

    /** Periodikus esély-próba a globális world-events tickről (AmbientEventManager-minta). */
    public void tick() {
        if (!configManager.getBoolean("world-events.stranger-npc.enabled", true)) {
            return;
        }
        final long now = System.currentTimeMillis();
        if (now < nextAttemptAt) {
            return;
        }
        nextAttemptAt = now + Math.max(1L,
                configManager.getLong("world-events.stranger-npc.check-interval-minutes", 90L)) * 60_000L;
        final double chance = Math.max(0.0D, Math.min(100.0D,
                configManager.getDouble("world-events.stranger-npc.chance-percent", 6.0D)));
        if (ThreadLocalRandom.current().nextDouble(100.0D) >= chance) {
            return;
        }
        final List<? extends Player> online = List.copyOf(Bukkit.getOnlinePlayers());
        if (online.isEmpty()) {
            return;
        }
        spawnNear(online.get(ThreadLocalRandom.current().nextInt(online.size())));
    }

    /** Admin-próba (/events stranger): azonnali felbukkanás a horgony közelében. */
    public boolean forceSpawn(final Player anchor) {
        if (anchor == null) {
            final List<? extends Player> online = List.copyOf(Bukkit.getOnlinePlayers());
            if (online.isEmpty()) {
                return false;
            }
            spawnNear(online.get(ThreadLocalRandom.current().nextInt(online.size())));
            return true;
        }
        spawnNear(anchor);
        return true;
    }

    private void spawnNear(final Player anchor) {
        // Folia: a horgony helyét a SAJÁT szálán olvassuk, majd a cél-hely régiójára hopolunk.
        anchor.getScheduler().run(plugin, task -> {
            final Location base = anchor.getLocation().clone();
            final double angle = ThreadLocalRandom.current().nextDouble(Math.PI * 2.0D);
            final int minDist = Math.max(8, configManager.getInt("world-events.stranger-npc.min-distance", 16));
            final int maxDist = Math.max(minDist + 1, configManager.getInt("world-events.stranger-npc.max-distance", 40));
            final int dist = ThreadLocalRandom.current().nextInt(minDist, maxDist + 1);
            final int x = base.getBlockX() + (int) Math.round(Math.cos(angle) * dist);
            final int z = base.getBlockZ() + (int) Math.round(Math.sin(angle) * dist);
            final World world = base.getWorld();
            if (world == null) {
                return;
            }
            plugin.getServer().getRegionScheduler().run(plugin, new Location(world, x, 0, z),
                    place -> placeStranger(world, x, z));
        }, null);
    }

    /** A cél-oszlop régió-szálán: guard-ellenőrzés, spawn, suttogás a közelieknek, időzített köddé válás. */
    private void placeStranger(final World world, final int x, final int z) {
        final int y = world.getHighestBlockYAt(x, z) + 1;
        final Location spot = new Location(world, x + 0.5D, y, z + 0.5D);
        final EventSpawnGuard guard = spawnGuard;
        if (guard != null && (guard.isBlocked("stranger", spot)
                || guard.isUnsafeSurface("stranger", world, x, z))) {
            return; // Ma nem mutatkozik — a következő próba máshol keres.
        }
        final WanderingTrader stranger = world.spawn(spot, WanderingTrader.class);
        stranger.getPersistentDataContainer().set(strangerKey, PersistentDataType.BYTE, (byte) 1);
        stranger.setAI(false);
        stranger.setSilent(true);
        stranger.setInvulnerable(true);
        stranger.setPersistent(false);
        stranger.setDespawnDelay(0);
        stranger.customName(messageManager.getMessage("stranger-name", "<dark_gray>Az Idegen</dark_gray>"));
        stranger.setCustomNameVisible(true);
        activeStrangerId = stranger.getUniqueId();

        world.playSound(spot, Sound.AMBIENT_SOUL_SAND_VALLEY_MOOD, 0.8F, 0.6F);
        hu.taliann.icesmp.utils.ParticleUtil.spawn(world, Particle.ASH, spot.clone().add(0.0D, 1.0D, 0.0D),
                20, 0.4D, 0.9D, 0.4D, 0.01D);

        // A talányos sor a közelben állóknak (a saját szálukon — más régióban állókhoz hop).
        final int index = ThreadLocalRandom.current().nextInt(LINES.length);
        final Component line = messageManager.getMessage("stranger-line-" + (index + 1), LINES[index]);
        final double radius = Math.max(4.0D, configManager.getDouble("world-events.stranger-npc.whisper-radius", 24.0D));
        for (final Entity nearby : stranger.getNearbyEntities(radius, radius, radius)) {
            if (nearby instanceof Player player) {
                if (Bukkit.isOwnedByCurrentRegion(player)) {
                    player.sendMessage(line);
                    player.playSound(player.getLocation(), Sound.AMBIENT_SOUL_SAND_VALLEY_MOOD, 0.5F, 1.5F);
                } else {
                    player.getScheduler().run(plugin, t -> {
                        player.sendMessage(line);
                        player.playSound(player.getLocation(), Sound.AMBIENT_SOUL_SAND_VALLEY_MOOD, 0.5F, 1.5F);
                    }, null);
                }
            }
        }

        // Köddé válás: az entitás saját schedulerén (retire-safe), lélek-örvénnyel.
        final long despawnTicks = Math.max(5L,
                configManager.getLong("world-events.stranger-npc.despawn-seconds", 45L)) * 20L;
        stranger.getScheduler().runDelayed(plugin, task -> {
            if (stranger.isValid()) {
                hu.taliann.icesmp.utils.ParticleUtil.spawn(stranger.getWorld(), Particle.SOUL,
                        stranger.getLocation().add(0.0D, 1.0D, 0.0D), 30, 0.4D, 0.9D, 0.4D, 0.02D);
                stranger.getWorld().playSound(stranger.getLocation(), Sound.PARTICLE_SOUL_ESCAPE, 1.0F, 0.7F);
                stranger.remove();
            }
            activeStrangerId = null;
        }, null, despawnTicks);
    }

    /** Leállás-takarítás: az élő Idegen nem maradhat a világban. */
    public void shutdown() {
        final UUID id = activeStrangerId;
        if (id != null) {
            try {
                final Entity entity = Bukkit.getEntity(id);
                if (entity != null && entity.isValid()) {
                    entity.remove();
                }
            } catch (final Exception ignored) {
                // Shutdown közben a régió már nem elérhető — a nem-persistent entitás magától eltűnik.
            }
            activeStrangerId = null;
        }
    }

}
