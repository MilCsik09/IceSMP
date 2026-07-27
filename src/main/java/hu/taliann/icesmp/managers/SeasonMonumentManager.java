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
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Locale;
import java.util.UUID;

/**
 * D3 — Szezon-emlékművek (lore: a Korszakok Könyve fizikai emlékművei — "neve örökre
 * bekerül", kódex VIII.). Szezonzáráskor a győztes frakció MARADANDÓ nyomot hagy a
 * világban: egy admin-előkészített ponton ({@code season-monument.location}) a talapzat
 * banner-blokkja a bajnok színére vált, fölötte TextDisplay-hologram sorolja a lezárt
 * korszakokat (szezon-sorszám + bajnok + a korszak hősei a StatsManager top-3-ából).
 * A lista nem törlődik — korszakról korszakra bővül (a legutóbbi {@code max-lines} sor
 * látszik). Hely nélkül a sorok akkor is gyűlnek (monument.yml), csak hologram nincs.
 *
 * <p>Folia: a blokk-csere és a display-entitás minden művelete a hely régió-schedulerén
 * fut; a hívás a SeasonManager tickjéről (globális scheduler) érkezik. A display-entitás
 * perzisztens — a UUID-ját tároljuk, frissítéskor a régit eltávolítjuk.
 */
public final class SeasonMonumentManager implements PersistentStore {

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final StatsManager statsManager;
    private final File storageFile;

    private final List<String> lines = new ArrayList<>();
    private final Map<String, Long> appliedGrants = new LinkedHashMap<>();
    private volatile int seasonIndex;
    private volatile UUID displayId;
    private volatile FactionType lastChampion;

    public SeasonMonumentManager(final JavaPlugin plugin, final ConfigManager configManager,
                                 final StatsManager statsManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.statsManager = statsManager;
        this.storageFile = new File(plugin.getDataFolder(), "monument.yml");
        YamlStore.registerCriticalWrite(storageFile);
        plugin.getDataFolder().mkdirs();
    }

    @Override
    public synchronized void load() {
        lines.clear();
        appliedGrants.clear();
        seasonIndex = 0;
        displayId = null;
        lastChampion = null;
        if (!storageFile.exists()) {
            return;
        }
        final YamlConfiguration yaml = YamlStore.loadTracked(storageFile, plugin.getLogger());
        seasonIndex = yaml.getInt("season-index", -1);
        if (seasonIndex < 0) {
            YamlStore.failCorrupt(storageFile, plugin.getLogger(), "Érvénytelen monument season-index");
            return;
        }
        lines.addAll(yaml.getStringList("lines"));
        final String rawChampion = yaml.getString("last-champion", "");
        if (!rawChampion.isBlank()) {
            lastChampion = FactionType.fromInput(rawChampion);
            if (lastChampion == null) {
                YamlStore.failCorrupt(storageFile, plugin.getLogger(), "Érvénytelen monument champion");
                return;
            }
        }
        final org.bukkit.configuration.ConfigurationSection grants =
                yaml.getConfigurationSection("applied-grants");
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
            try {
                displayId = UUID.fromString(rawId);
            } catch (final IllegalArgumentException invalid) {
                YamlStore.failCorrupt(storageFile, plugin.getLogger(), "Érvénytelen monument display UUID");
                return;
            }
        }
        if (lastChampion != null) {
            plugin.getServer().getGlobalRegionScheduler().runDelayed(
                    plugin, task -> refreshMonument(lastChampion), 1L);
        }
    }

    @Override
    public synchronized void save() {
        if (!writeStateLocked()) {
            plugin.getLogger().severe("Failed to save monument.yml");
        }
    }

    private boolean writeStateLocked() {
        try {
            final YamlConfiguration yaml = new YamlConfiguration();
            yaml.set("season-index", seasonIndex);
            yaml.set("lines", List.copyOf(lines));
            yaml.set("display-uuid", displayId == null ? "" : displayId.toString());
            yaml.set("last-champion", lastChampion == null ? "" : lastChampion.name());
            for (final Map.Entry<String, Long> entry : appliedGrants.entrySet()) {
                yaml.set("applied-grants." + entry.getKey(), entry.getValue());
            }
            YamlStore.saveAtomic(storageFile, yaml);
            return true;
        } catch (final IOException exception) {
            plugin.getLogger().severe("Failed to save monument.yml: " + exception.getMessage());
            return false;
        }
    }

    /** Idempotently records a closed season before refreshing its physical projection. */
    public synchronized boolean recordSeasonOnce(final String grantId, final int closedSeason,
                                                 final FactionType champion) {
        if (grantId == null || grantId.isBlank() || closedSeason < 1 || champion == null) {
            return false;
        }
        if (appliedGrants.containsKey(grantId)) {
            return true;
        }
        if (!configManager.getBoolean("season-monument.enabled", true)) {
            return true;
        }

        final int previousIndex = seasonIndex;
        final UUID previousDisplay = displayId;
        final FactionType previousChampion = lastChampion;
        final List<String> previousLines = List.copyOf(lines);
        seasonIndex = Math.max(seasonIndex + 1, closedSeason);
        lastChampion = champion;
        final StringBuilder heroes = new StringBuilder();
        final List<StatsManager.Entry> top = statsManager.top(StatsManager.Category.LEVEL, 3);
        for (int i = 0; i < top.size(); i++) {
            heroes.append(top.get(i).name());
            if (i < top.size() - 1) {
                heroes.append(", ");
            }
        }
        lines.add(closedSeason + ". korszak — " + champion.getDisplayName()
                + (heroes.isEmpty() ? "" : " — Hősök: " + heroes));
        final int maxLines = Math.max(1, configManager.getInt("season-monument.max-lines", 12));
        while (lines.size() > maxLines) {
            lines.remove(0);
        }
        appliedGrants.put(grantId, System.currentTimeMillis());
        if (!writeStateLocked()) {
            appliedGrants.remove(grantId);
            seasonIndex = previousIndex;
            displayId = previousDisplay;
            lastChampion = previousChampion;
            lines.clear();
            lines.addAll(previousLines);
            return false;
        }
        refreshMonument(champion);
        return true;
    }

    /** A fizikai emlékmű frissítése (talapzat-banner + hologram) a hely régió-szálán. */
    private void refreshMonument(final FactionType champion) {
        final Location base = parseLocation();
        if (base == null) {
            return; // Nincs kijelölt hely — a sorok gyűlnek, hologram nélkül.
        }
        final List<String> snapshot = List.copyOf(lines);
        final UUID oldDisplay = displayId;
        plugin.getServer().getRegionScheduler().run(plugin, base, task -> {
            final World world = base.getWorld();
            // Talapzat: a bajnok színére váltó banner-blokk.
            world.getBlockAt(base).setType(bannerOf(champion), false);
            // Hologram: a régi display cserélődik (a szöveg nő), CENTER-billboard, perzisztens.
            if (oldDisplay != null) {
                final Entity old = Bukkit.getEntity(oldDisplay);
                if (old != null && old.isValid()) {
                    old.remove();
                }
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

    /** "world,x,y,z" formátumú emlékmű-pont (üres = nincs fizikai emlékmű). */
    private Location parseLocation() {
        final String raw = configManager.getString("season-monument.location", "");
        if (raw.isBlank()) {
            return null;
        }
        final String[] parts = raw.split(",");
        if (parts.length < 4) {
            return null;
        }
        final World world = Bukkit.getWorld(parts[0].trim());
        if (world == null) {
            return null;
        }
        try {
            return new Location(world, Integer.parseInt(parts[1].trim()),
                    Integer.parseInt(parts[2].trim()), Integer.parseInt(parts[3].trim()));
        } catch (final NumberFormatException exception) {
            plugin.getLogger().warning("Hibás season-monument.location formátum: " + raw);
            return null;
        }
    }

    private static Material bannerOf(final FactionType faction) {
        return switch (faction) {
            case RED -> Material.RED_BANNER;
            case BLUE -> Material.LIGHT_BLUE_BANNER;
            case DARK -> Material.BLACK_BANNER;
            default -> Material.WHITE_BANNER;
        };
    }

}
