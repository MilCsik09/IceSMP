package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.storage.PersistentStore;
import hu.taliann.icesmp.storage.YamlStore;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * "A világ visszagyógyul" — rombolás/robbanás után a blokkok pontosan az eredeti
 * állapotukba épülnek vissza, drop nélkül (nincs dupe-út). A védett zónák robbanásai
 * és az ostrom-rombolás így látványosan megtörténhetnek anélkül, hogy maradandó kárt
 * vagy zsákmányt adnának.
 *
 * Tile-entity blokkot (láda, kemence, spawner…) SOSEM veszünk fel: azok tartalmát nem
 * pillanatképezzük, ezért azokat a hívó köteles érintetlenül hagyni.
 *
 * A várólista perzisztens (block-regen.yml): restart közben esedékessé váló
 * visszaépítés sem vész el — nem marad örök lyuk a városfalban.
 */
public final class BlockRegenService implements PersistentStore {



    private record Entry(String world, int x, int y, int z, String blockData, long restoreAt) {
    }

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final File storageFile;
    private final Queue<Entry> queue = new ConcurrentLinkedQueue<>();

    public BlockRegenService(final JavaPlugin plugin, final ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.storageFile = new File(plugin.getDataFolder(), "block-regen.yml");
    }

    public boolean isEnabled() {
        return configManager.getBoolean("territory.protection.regen.enabled", true);
    }

    public long explosionDelayMillis() {
        return Math.max(5L, configManager.getLong("territory.protection.regen.delay-seconds", 180L)) * 1000L;
    }

    /** Hány tickenként fut a visszaépítő menet (indításkor olvasott kulcs). */
    public long restoreIntervalTicks() {
        return Math.max(1L, configManager.getLong("territory.protection.regen.restore-interval-ticks", 10L));
    }

    /** Menetenként ennyi blokk kerül vissza — ez adja a visszaépülés "tempóját". */
    public int blocksPerPass() {
        return Math.max(1, configManager.getInt("territory.protection.regen.blocks-per-pass", 3));
    }

    public boolean isSiegeBreakEnabled() {
        return configManager.getBoolean("territory.protection.regen.player-break.siege-enabled", true);
    }

    public long siegeBreakDelayMillis() {
        return Math.max(5L, configManager.getLong(
                "territory.protection.regen.player-break.siege-delay-seconds", 300L)) * 1000L;
    }

    public boolean isAlwaysBreakEnabled() {
        return configManager.getBoolean("territory.protection.regen.player-break.always-enabled", false);
    }

    public long alwaysBreakDelayMillis() {
        return Math.max(5L, configManager.getLong(
                "territory.protection.regen.player-break.always-delay-seconds", 120L)) * 1000L;
    }

    /**
     * Felveszi a blokkot a visszaépülési sorba a JELENLEGI állapotával. Tile-entity
     * blokkra false — azt a hívó ne engedje elpusztulni.
     */
    public boolean capture(final Block block, final long delayMillis) {
        if (block.getState() instanceof TileState) {
            return false;
        }
        queue.add(new Entry(block.getWorld().getName(), block.getX(), block.getY(), block.getZ(),
                block.getBlockData().getAsString(), System.currentTimeMillis() + delayMillis));
        return true;
    }

    /** Igaz, ha a blokk tile-entity — robbanás-listából eleve ki kell venni. */
    public static boolean isTileEntity(final Block block) {
        return block.getState() instanceof TileState;
    }

    /** A törmelék-entitások jelölése — landoláskor porladnak, sosem raknak le blokkot. */
    public static final String DEBRIS_TAG = "icesmp_debris";

    public int debrisMaxPerExplosion() {
        return configManager.getInt("territory.protection.regen.debris-max-per-explosion", 30);
    }

    /**
     * Kozmetikai törmelék: a kirobbant blokk másolata FallingBlockként repül ki a
     * robbanás középpontjából — pattog/csúszik a vanília fizikával, majd pár másodperc
     * után porfelhővel eltűnik. Tisztán látvány, se blokk-lerakás, se drop.
     */
    public void spawnDebris(final Block block, final Location center) {
        if (!configManager.getBoolean("territory.protection.regen.debris-enabled", true)) {
            return;
        }
        // Csak a blokkok debris-percent %-a válik repülő törmelékké (látvány-sűrűség fék).
        final double percent = configManager.getDouble("territory.protection.regen.debris-percent", 100.0D);
        if (Math.random() * 100.0D >= percent) {
            return;
        }
        final Location from = block.getLocation().add(0.5D, 0.5D, 0.5D);
        final org.bukkit.util.Vector dir = from.toVector().subtract(center.toVector());
        if (dir.lengthSquared() < 0.01D) {
            dir.setY(1.0D);
        }
        final double power = configManager.getDouble("territory.protection.regen.debris-launch-power", 0.6D);
        final org.bukkit.entity.FallingBlock debris = block.getWorld().spawnFallingBlock(from, block.getBlockData());
        debris.setDropItem(false);
        debris.setCancelDrop(true);
        debris.addScoreboardTag(DEBRIS_TAG);
        debris.setVelocity(dir.normalize().multiply(power)
                .add(new org.bukkit.util.Vector(0.0D, 0.35D + Math.random() * 0.2D, 0.0D)));
        final long lifetimeTicks = Math.max(20L,
                configManager.getLong("territory.protection.regen.debris-lifetime-seconds", 4L) * 20L);
        debris.getScheduler().runDelayed(plugin, task -> {
            if (debris.isValid()) {
                debris.getWorld().spawnParticle(org.bukkit.Particle.BLOCK_CRUMBLE,
                        debris.getLocation(), 12, 0.2D, 0.2D, 0.2D, block.getBlockData());
                debris.remove();
            }
        }, null, lifetimeTicks);
    }

    /** A globál-tickről hívva: az esedékes blokkok visszaépítése (alulról felfelé). */
    public void tick() {
        final long now = System.currentTimeMillis();
        final List<Entry> due = new ArrayList<>();
        for (final Entry e : queue) {
            if (e.restoreAt() <= now) {
                due.add(e);
                if (due.size() >= blocksPerPass()) {
                    break;
                }
            }
        }
        if (due.isEmpty()) {
            return;
        }
        queue.removeAll(due);
        // Alulról felfelé: a gravitációs blokk (homok, kavics) nem hullik ki a fal aljából.
        due.sort(Comparator.comparingInt(Entry::y));
        for (final Entry e : due) {
            final World world = Bukkit.getWorld(e.world());
            if (world == null) {
                continue;
            }
            final Location loc = new Location(world, e.x(), e.y(), e.z());
            Bukkit.getRegionScheduler().run(plugin, loc, task -> {
                try {
                    // Mindig felülírunk: a világ PONTOSAN a rombolás előtti állapotba tér
                    // vissza (a közben odarakott blokk drop nélkül tűnik el — hadszíntér).
                    final org.bukkit.block.data.BlockData data = Bukkit.createBlockData(e.blockData());
                    world.getBlockAt(e.x(), e.y(), e.z()).setBlockData(data, false);
                    if (configManager.getBoolean("territory.protection.regen.restore-effects-enabled", true)) {
                        // Anyag-hű "gyógyulás": a blokk saját lerakás-hangja + kis porfelhő.
                        final Location fx = loc.clone().add(0.5D, 0.5D, 0.5D);
                        world.playSound(fx, data.getSoundGroup().getPlaceSound(), 0.6F,
                                0.8F + (float) (Math.random() * 0.4D));
                        world.spawnParticle(org.bukkit.Particle.CLOUD, fx, 4, 0.25D, 0.25D, 0.25D, 0.01D);
                    }
                } catch (final IllegalArgumentException ignored) {
                    // Érvénytelenné vált blockdata (pl. verzióváltás) — kihagyjuk.
                }
            });
        }
    }

    @Override
    public void load() {
        queue.clear();
        if (!storageFile.exists()) {
            return;
        }
        final YamlConfiguration yaml = YamlConfiguration.loadConfiguration(storageFile);
        for (final java.util.Map<?, ?> raw : yaml.getMapList("pending")) {
            try {
                queue.add(new Entry(String.valueOf(raw.get("world")),
                        ((Number) raw.get("x")).intValue(), ((Number) raw.get("y")).intValue(),
                        ((Number) raw.get("z")).intValue(), String.valueOf(raw.get("data")),
                        ((Number) raw.get("at")).longValue()));
            } catch (final RuntimeException ignored) {
                // Sérült sor — a többi bejegyzés attól még betölt.
            }
        }
    }

    @Override
    public void save() {
        final YamlConfiguration yaml = new YamlConfiguration();
        final List<java.util.Map<String, Object>> out = new ArrayList<>();
        for (final Entry e : queue) {
            final java.util.Map<String, Object> row = new java.util.LinkedHashMap<>();
            row.put("world", e.world());
            row.put("x", e.x());
            row.put("y", e.y());
            row.put("z", e.z());
            row.put("data", e.blockData());
            row.put("at", e.restoreAt());
            out.add(row);
        }
        yaml.set("pending", out);
        try {
            YamlStore.saveAtomic(storageFile, yaml);
        } catch (final java.io.IOException e) {
            plugin.getLogger().severe("block-regen.yml mentési hiba: " + e);
        }
    }
}
