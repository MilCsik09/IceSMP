package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.data.FactionType;
import hu.taliann.icesmp.data.Territory;
import hu.taliann.icesmp.data.TerritoryType;
import hu.taliann.icesmp.storage.PersistentStore;
import hu.taliann.icesmp.storage.YamlStore;
import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.Map;

/**
 * B33 — Szezonzáró világesemény, a "végítélet-hét" (lore: a Korszakok Könyve
 * lapfordulása + a fél-álom Királynő nyugtalansága — kódex VII./VIII.). A szezon
 * utolsó {@code days} napjában a világ érezhetően "felfut a záráshoz":
 * <ul>
 *   <li><b>eszkalálódó esemény-esélyek</b> — a vérhold/világboss/invázió esély-kulcsa
 *       naponta növekvő szorzót kap ({@link #eventChanceMultiplier});</li>
 *   <li><b>erősebb invázió</b> — a horda mob-szintje napi bónuszt kap
 *       ({@link #bonusMobLevels});</li>
 *   <li><b>emelt liga-pontok</b> — a SeasonManager minden pont-jóváírása lineárisan
 *       a maximumig (alapból dupláig) skálázódik ({@link #leaguePointMultiplier});</li>
 *   <li><b>az utolsó napon</b> egyszeri, globálisan kihirdetett SZEZONBOSS spawnol a
 *       NEUTRAL főváros falainál (a világboss-infra finálé-módja: guard-bypass,
 *       emelt élet, egyedi loot-tábla a halálakor).</li>
 * </ul>
 * A napváltás lore-hangú broadcastot kap. Állapot: {@code season-finale.yml} — melyik
 * szezonhoz (seasonStart-bélyeg) hirdettünk már napot / spawnolt már boss (restart-álló,
 * nem duplázódik). Folia: a tick a globális world-events schedulerről fut; a boss-spawn
 * a WorldBossManager régió-hopos útját használja. Minden kulcs élőben olvasódik
 * (world-events.season-finale.*).
 */
public final class SeasonFinaleManager implements PersistentStore {

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final SeasonManager seasonManager;
    private final WorldBossManager worldBossManager;
    private final TerritoryManager territoryManager;
    private final MessageManager messageManager;
    private final File storageFile;

    /** Melyik szezonban (seasonStart-bélyeg) melyik finálé-napot hirdettük már ki. */
    private volatile long announcedSeasonStart;
    private volatile int lastAnnouncedDay;
    /** Melyik szezonban spawnolt már a szezonboss (egyszeri). */
    private volatile long bossSpawnedSeasonStart;

    public SeasonFinaleManager(final JavaPlugin plugin, final ConfigManager configManager,
                               final SeasonManager seasonManager, final WorldBossManager worldBossManager,
                               final TerritoryManager territoryManager, final MessageManager messageManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.seasonManager = seasonManager;
        this.worldBossManager = worldBossManager;
        this.territoryManager = territoryManager;
        this.messageManager = messageManager;
        this.storageFile = new File(plugin.getDataFolder(), "season-finale.yml");
        plugin.getDataFolder().mkdirs();
    }

    @Override
    public void load() {
        announcedSeasonStart = 0L;
        lastAnnouncedDay = 0;
        bossSpawnedSeasonStart = 0L;
        if (!storageFile.exists()) {
            return;
        }
        final YamlConfiguration yaml = hu.taliann.icesmp.storage.YamlStore.loadTracked(storageFile, plugin.getLogger());
        announcedSeasonStart = yaml.getLong("announced-season", 0L);
        lastAnnouncedDay = yaml.getInt("last-announced-day", 0);
        bossSpawnedSeasonStart = yaml.getLong("boss-spawned-season", 0L);
    }

    @Override
    public void save() {
        try {
            final YamlConfiguration yaml = new YamlConfiguration();
            yaml.set("announced-season", announcedSeasonStart);
            yaml.set("last-announced-day", lastAnnouncedDay);
            yaml.set("boss-spawned-season", bossSpawnedSeasonStart);
            YamlStore.saveAtomic(storageFile, yaml);
        } catch (final IOException exception) {
            plugin.getLogger().severe("Failed to save season-finale.yml: " + exception.getMessage());
        }
    }

    public boolean isEnabled() {
        return configManager.getBoolean("world-events.season-finale.enabled", true)
                && configManager.getBoolean("world-events.season.enabled", true);
    }

    private int finaleDays() {
        return Math.max(1, configManager.getInt("world-events.season-finale.days", 7));
    }

    /**
     * A finálé aktuális napja: 1 = a végítélet-hét első napja, {@code days} = az utolsó
     * (boss-)nap; 0 = még nem tart a finálé. A szezon-vége időpontot a SeasonManager
     * élő configból számolja, így a hossz-átállítás azonnal érvényesül.
     */
    public int finaleDay() {
        if (!isEnabled()) {
            return 0;
        }
        final long now = System.currentTimeMillis();
        final long end = seasonManager.getSeasonEndMillis();
        if (now >= end) {
            return 0; // A szezonzárást a SeasonManager tick kezeli.
        }
        final int days = finaleDays();
        final long daysLeft = (end - now + 86_399_999L) / 86_400_000L; // felfelé kerekítve, 1..n
        if (daysLeft > days) {
            return 0;
        }
        return (int) (days - daysLeft + 1);
    }

    public boolean isActive() {
        return finaleDay() > 0;
    }

    /** Esemény-esély szorzó a vérhold/világboss/invázió chance-kulcsaira (1.0 = nincs finálé). */
    public double eventChanceMultiplier() {
        final int day = finaleDay();
        if (day <= 0) {
            return 1.0D;
        }
        final double perDay = Math.max(0.0D,
                configManager.getDouble("world-events.season-finale.chance-mult-per-day", 0.15D));
        return 1.0D + day * perDay;
    }

    /** Invázió mob-szint bónusz a finálé napjai alatt (0 = nincs finálé). */
    public int bonusMobLevels() {
        final int day = finaleDay();
        if (day <= 0) {
            return 0;
        }
        final double perDay = Math.max(0.0D,
                configManager.getDouble("world-events.season-finale.mob-level-bonus-per-day", 0.5D));
        return (int) Math.floor(day * perDay);
    }

    /** Liga-pont szorzó: lineárisan fut fel a maximumig (alapból duplázás az utolsó napon). */
    public double leaguePointMultiplier() {
        final int day = finaleDay();
        if (day <= 0) {
            return 1.0D;
        }
        final double max = Math.max(1.0D,
                configManager.getDouble("world-events.season-finale.league-point-mult-max", 2.0D));
        return 1.0D + ((double) day / (double) finaleDays()) * (max - 1.0D);
    }

    /**
     * Periodikus driver a globális world-events tickről: napváltás-broadcast + az utolsó
     * napon az egyszeri szezonboss-spawn a fővárosnál.
     */
    public void tick() {
        final int day = finaleDay();
        if (day <= 0) {
            return;
        }
        // Szezon-azonosító a KEZDŐ bélyeg: a length-days élő átírása a vég-bélyeget
        // elmozdítaná, és az egyszeri boss-spawn/nap-broadcast újra elsülne.
        final long seasonStart = seasonManager.getSeasonStart();
        if (announcedSeasonStart != seasonStart || lastAnnouncedDay != day) {
            announcedSeasonStart = seasonStart;
            lastAnnouncedDay = day;
            announceDay(day);
            save();
        }
        if (day >= finaleDays()
                && configManager.getBoolean("world-events.season-finale.boss.enabled", true)
                && bossSpawnedSeasonStart != seasonStart) {
            if (spawnFinaleBoss()) {
                bossSpawnedSeasonStart = seasonStart;
                save();
            }
        }
    }

    /** Lore-hangú napi eszkaláció-broadcast (a Korszakok Könyve lapfordulása). */
    private void announceDay(final int day) {
        final int days = finaleDays();
        if (day >= days) {
            Bukkit.getServer().broadcast(messageManager.getMessage(
                    "season-finale-final-day",
                    "<dark_red>📖 A Korszakok Könyvének lapja fordulóban — ez a szezon UTOLSÓ napja! A Királynő nyugtalansága a főváros falaiig ér… (liga-pontok ×{mult})</dark_red>",
                    Map.of("mult", trimmed(leaguePointMultiplier()))));
            return;
        }
        Bukkit.getServer().broadcast(messageManager.getMessage(
                "season-finale-day",
                "<gold>📖 Végítélet-hét, {day}. nap — a Korszakok Könyvének lapja zizeg: sűrűbb vérhold, vadabb hordák, emelt liga-pontok (×{mult}). A szezon zárul!</gold>",
                Map.of("day", String.valueOf(day), "mult", trimmed(leaguePointMultiplier()))));
    }

    private static String trimmed(final double value) {
        return String.format(java.util.Locale.ROOT, "%.2f", value).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    /**
     * A szezonboss spawnja a NEUTRAL főváros zónájánál (a világboss-infra finálé-módja:
     * spawn-guard bypass — a boss szándékosan a városfalaknál jelenik meg). Ha nincs
     * főváros-zóna, egy random online játékos mellé esik vissza (guard-os út).
     *
     * @return true, ha a spawn ütemezve lett (a jelzőt csak ekkor állítjuk)
     */
    private boolean spawnFinaleBoss() {
        final Territory capital = territoryManager.all().stream()
                .filter(zone -> zone.type() == TerritoryType.CAPITAL && zone.faction() == FactionType.NEUTRAL)
                .findFirst().orElse(null);
        if (capital != null) {
            final World world = Bukkit.getWorld(capital.world());
            if (world != null) {
                // A falakon KÍVÜLRE, a zóna pereme mellé: a boss ostromló, nem városromboló —
                // a bounding-sugár + offset pont a kapuk elé teszi.
                final int offset = Math.max(8, configManager.getInt("world-events.season-finale.boss.wall-offset", 16));
                final double angle = java.util.concurrent.ThreadLocalRandom.current().nextDouble(Math.PI * 2.0D);
                final int x = capital.x() + (int) Math.round(Math.cos(angle) * (capital.radius() + offset));
                final int z = capital.z() + (int) Math.round(Math.sin(angle) * (capital.radius() + offset));
                final long lifetime = Math.max(5L, configManager.getLong("world-events.season-finale.boss.lifetime-minutes", 30L));
                return worldBossManager.forceFinaleSpawn(new Location(world, x, 0, z), lifetime);
            }
        }
        // Fallback: nincs kijelölt főváros — a szokásos (guard-os) világboss-út egy játékos mellé.
        return worldBossManager.forceSpawn(null);
    }
}
