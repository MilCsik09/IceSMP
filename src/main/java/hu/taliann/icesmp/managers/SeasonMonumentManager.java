package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.data.FactionType;
import hu.taliann.icesmp.storage.PersistentStore;
import hu.taliann.icesmp.storage.YamlStore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TextDisplay;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Durable physical history projection for closed seasons and the one-shot Prologue. */
public final class SeasonMonumentManager implements PersistentStore {
    private static volatile SeasonMonumentManager active;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy.MM.dd.")
            .withZone(ZoneId.of("Europe/Budapest"));

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final StatsManager statsManager;
    private final File storageFile;
    private static final String PROLOGUE_LINE_PREFIX = "Prologue — Az Első Expedíció — Kárhozat Éjszakája — ";
    private final List<String> lines = new ArrayList<>();
    private final Map<String, Long> appliedGrants = new LinkedHashMap<>();
    private volatile int seasonIndex;
    private volatile UUID displayId;
    private volatile FactionType lastChampion;
    private volatile boolean lastProjectionPrologue;

    public SeasonMonumentManager(final JavaPlugin plugin, final ConfigManager configManager,
                                 final StatsManager statsManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.statsManager = statsManager;
        this.storageFile = new File(plugin.getDataFolder(), "monument.yml");
        YamlStore.registerCriticalWrite(storageFile);
        plugin.getDataFolder().mkdirs();
        active = this;
    }

    public static SeasonMonumentManager current() { return active; }

    @Override
    public synchronized void load() {
        lines.clear();
        appliedGrants.clear();
        seasonIndex = 0;
        displayId = null;
        lastChampion = null;
        lastProjectionPrologue = false;
        if (!storageFile.exists()) return;
        final YamlConfiguration yaml = YamlStore.loadTracked(storageFile, plugin.getLogger());
        seasonIndex = yaml.getInt("season-index", -1);
        if (seasonIndex < 0) {
            YamlStore.failCorrupt(storageFile, plugin.getLogger(), "Érvénytelen monument season-index");
            return;
        }
        lines.addAll(yaml.getStringList("lines"));
        lastProjectionPrologue = yaml.getBoolean("last-projection-prologue", false);
        final String rawChampion = yaml.getString("last-champion", "");
        if (!rawChampion.isBlank()) {
            lastChampion = FactionType.fromInput(rawChampion);
            if (lastChampion == null) {
                YamlStore.failCorrupt(storageFile, plugin.getLogger(), "Érvénytelen monument champion");
                return;
            }
        }
        final org.bukkit.configuration.ConfigurationSection grants = yaml.getConfigurationSection("applied-grants");
        if (grants != null) {
            for (final String key : grants.getKeys(false)) {
                final long timestamp = grants.getLong(key, -1L);
                if (key.isBlank() || timestamp <= 0L) {
                    YamlStore.failCorrupt(storageFile, plugin.getLogger(),
                            "Érvénytelen monument applied-grant: " + key);
                    return;
                }
                appliedGrants.put(key, timestamp);
            }
        }
        final String rawId = yaml.getString("display-uuid", "");
        if (!rawId.isBlank()) {
            try { displayId = UUID.fromString(rawId); }
            catch (final IllegalArgumentException invalid) {
                YamlStore.failCorrupt(storageFile, plugin.getLogger(), "Érvénytelen monument display UUID");
                return;
            }
        }
        if (lastProjectionPrologue || lastChampion != null) {
            plugin.getServer().getGlobalRegionScheduler().runDelayed(plugin,
                    task -> refreshMonument(lastProjectionPrologue
                            ? Material.PURPLE_BANNER : bannerOf(lastChampion)), 1L);
        }
    }

    @Override
    public synchronized void save() {
        if (!writeStateLocked()) {
            throw new IllegalStateException("monument.yml mentése sikertelen — részletek a logban");
        }
    }

    private boolean writeStateLocked() {
        try {
            final YamlConfiguration yaml = new YamlConfiguration();
            yaml.set("season-index", seasonIndex);
            yaml.set("lines", List.copyOf(lines));
            yaml.set("display-uuid", displayId == null ? "" : displayId.toString());
            yaml.set("last-champion", lastChampion == null ? "" : lastChampion.name());
            yaml.set("last-projection-prologue", lastProjectionPrologue);
            for (final Map.Entry<String, Long> entry : appliedGrants.entrySet()) {
                yaml.set("applied-grants." + entry.getKey(), entry.getValue());
            }
            YamlStore.saveAtomic(storageFile, yaml);
            return true;
        } catch (final IOException exception) {
            plugin.getLogger().severe("Failed to save monument.yml: " + exception.getMessage());
            return false;
        } catch (final hu.taliann.icesmp.storage.CriticalPersistenceWriteError fatal) {
            plugin.getLogger().severe(fatal.getMessage() == null ? fatal.toString() : fatal.getMessage());
            return false;
        }
    }

    public synchronized boolean recordSeasonOnce(final String grantId, final int closedSeason,
                                                  final FactionType champion) {
        if (grantId == null || grantId.isBlank() || closedSeason < 1 || champion == null) return false;
        if (appliedGrants.containsKey(grantId)) return true;
        if (!configManager.getBoolean("season-monument.enabled", true)) return true;
        final Snapshot before = snapshot();
        seasonIndex = Math.max(seasonIndex + 1, closedSeason);
        lastChampion = champion;
        lastProjectionPrologue = false;
        final StringBuilder heroes = new StringBuilder();
        final List<StatsManager.Entry> top = statsManager.top(StatsManager.Category.LEVEL, 3);
        for (int i = 0; i < top.size(); i++) {
            heroes.append(top.get(i).name());
            if (i < top.size() - 1) heroes.append(", ");
        }
        appendLine(closedSeason + ". korszak — " + champion.getDisplayName()
                + (heroes.isEmpty() ? "" : " — Hősök: " + heroes));
        appliedGrants.put(grantId, System.currentTimeMillis());
        if (!writeStateLocked()) {
            restore(before);
            return false;
        }
        refreshMonument(bannerOf(champion));
        return true;
    }

    /**
     * Teszt-visszaállítás: a Prologue-sor és a hozzá tartozó grant törlése, hogy egy ismételt
     * próba friss résztvevőszámmal rögzülhessen újra.
     */
    public synchronized boolean forgetPrologue(final String grantId) {
        if (grantId == null || grantId.isBlank()) return false;
        final Snapshot before = snapshot();
        final boolean hadGrant = appliedGrants.remove(grantId) != null;
        final boolean hadLine = lines.removeIf(line -> line.startsWith(PROLOGUE_LINE_PREFIX));
        if (!hadGrant && !hadLine) return true;
        lastProjectionPrologue = false;
        if (!writeStateLocked()) {
            restore(before);
            return false;
        }
        refreshMonument(lastChampion == null ? Material.PURPLE_BANNER : bannerOf(lastChampion));
        return true;
    }

    /** Prologue is a historical pre-season entry, not a fake Season 0 league winner. */
    public synchronized boolean recordPrologueOnce(final String grantId, final int participantCount,
                                                    final long completedAt) {
        if (grantId == null || grantId.isBlank() || participantCount < 0 || completedAt <= 0L) return false;
        if (appliedGrants.containsKey(grantId)) return true;
        if (!configManager.getBoolean("season-monument.enabled", true)) return true;
        final Snapshot before = snapshot();
        lastProjectionPrologue = true;
        lastChampion = null;
        appendLine(PROLOGUE_LINE_PREFIX
                + participantCount + " résztvevő — " + DATE.format(Instant.ofEpochMilli(completedAt)));
        appliedGrants.put(grantId, System.currentTimeMillis());
        if (!writeStateLocked()) {
            restore(before);
            return false;
        }
        refreshMonument(Material.PURPLE_BANNER);
        return true;
    }

    private void appendLine(final String line) {
        lines.add(line);
        final int maxLines = Math.max(1, configManager.getInt("season-monument.max-lines", 12));
        while (lines.size() > maxLines) lines.remove(0);
    }

    private Snapshot snapshot() {
        return new Snapshot(seasonIndex, displayId, lastChampion, lastProjectionPrologue,
                List.copyOf(lines), new LinkedHashMap<>(appliedGrants));
    }

    private void restore(final Snapshot snapshot) {
        seasonIndex = snapshot.seasonIndex();
        displayId = snapshot.displayId();
        lastChampion = snapshot.champion();
        lastProjectionPrologue = snapshot.prologue();
        lines.clear();
        lines.addAll(snapshot.lines());
        appliedGrants.clear();
        appliedGrants.putAll(snapshot.grants());
    }

    private void refreshMonument(final Material banner) {
        final Location base = parseLocation();
        if (base == null) return;
        final List<String> snapshot = List.copyOf(lines);
        final UUID oldDisplay = displayId;
        plugin.getServer().getRegionScheduler().run(plugin, base, task -> {
            final World world = base.getWorld();
            if (world == null) return;
            world.getBlockAt(base).setType(banner, false);
            if (oldDisplay != null) {
                final Entity old = Bukkit.getEntity(oldDisplay);
                if (old != null && old.isValid()) old.remove();
            }
            Component text = MiniMessage.miniMessage().deserialize(configManager.getString(
                    "season-monument.header", "<gold>📖 A Korszakok Könyve</gold>"));
            for (final String line : snapshot) {
                text = text.append(Component.newline()).append(Component.text(line,
                        net.kyori.adventure.text.format.NamedTextColor.YELLOW));
            }
            final Component displayText = text;
            final TextDisplay display = world.spawn(base.clone().add(0.5D, 2.2D, 0.5D), TextDisplay.class,
                    spawned -> {
                        spawned.text(displayText);
                        spawned.setBillboard(Display.Billboard.CENTER);
                        spawned.setPersistent(true);
                        spawned.setSeeThrough(false);
                        spawned.setDefaultBackground(false);
                    });
            synchronized (SeasonMonumentManager.this) {
                displayId = display.getUniqueId();
                writeStateLocked();
            }
            world.playSound(base, org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0F, 0.8F);
        });
    }

    private Location parseLocation() {
        final String raw = configManager.getString("season-monument.location", "");
        if (raw.isBlank()) return null;
        final String[] parts = raw.split(",");
        if (parts.length < 4) return null;
        final World world = Bukkit.getWorld(parts[0].trim());
        if (world == null) return null;
        try {
            return new Location(world, Integer.parseInt(parts[1].trim()),
                    Integer.parseInt(parts[2].trim()), Integer.parseInt(parts[3].trim()));
        } catch (final NumberFormatException exception) {
            plugin.getLogger().warning("Hibás season-monument.location formátum: " + raw);
            return null;
        }
    }

    private static Material bannerOf(final FactionType faction) {
        if (faction == null) return Material.WHITE_BANNER;
        return switch (faction) {
            case RED -> Material.RED_BANNER;
            case BLUE -> Material.LIGHT_BLUE_BANNER;
            case DARK -> Material.BLACK_BANNER;
            default -> Material.WHITE_BANNER;
        };
    }

    private record Snapshot(int seasonIndex, UUID displayId, FactionType champion, boolean prologue,
                            List<String> lines, Map<String, Long> grants) { }
}
