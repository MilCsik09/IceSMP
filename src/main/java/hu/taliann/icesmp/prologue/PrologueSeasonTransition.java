package hu.taliann.icesmp.prologue;

import hu.taliann.icesmp.listeners.QuestProgressListener;
import hu.taliann.icesmp.managers.CommunityGoalManager;
import hu.taliann.icesmp.managers.ConfigManager;
import hu.taliann.icesmp.managers.SeasonManager;
import hu.taliann.icesmp.storage.YamlStore;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.RegisteredListener;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;

/** Prepares a clean Season 1 generation; admission stays gated until Prologue COMPLETED. */
public final class PrologueSeasonTransition {
    private final JavaPlugin plugin;
    private final ConfigManager config;
    private final File receiptFile;

    public PrologueSeasonTransition(final JavaPlugin plugin, final ConfigManager config) {
        this.plugin = plugin;
        this.config = config;
        this.receiptFile = new File(plugin.getDataFolder(), "prologue-season-transition.yml");
        YamlStore.registerCriticalWrite(receiptFile);
    }

    /**
     * The actual Season 1 start timestamp is reserved exactly once immediately before the clean
     * generation write. Replays reuse that receipt instead of drifting to plugin/prologue startup.
     */
    public synchronized void prepareSeasonOne(final long ignoredLegacyTimestamp) {
        final long startTimestamp = seasonOneStartTimestamp();
        final SeasonManager season = resolveSeasonManager();
        final CommunityGoalManager community = resolveCommunityGoalManager();
        final File communityFile = new File(plugin.getDataFolder(), "community-goals.yml");
        final File seasonFile = new File(plugin.getDataFolder(), "season.yml");
        final YamlConfiguration cleanCommunity = new YamlConfiguration();
        cleanCommunity.set("season.number", 1);
        final YamlConfiguration cleanSeason = new YamlConfiguration();
        cleanSeason.set("season.start", startTimestamp);
        cleanSeason.set("season.number", 1);
        try {
            YamlStore.saveAtomic(communityFile, cleanCommunity);
            YamlStore.saveAtomic(seasonFile, cleanSeason);
        } catch (final IOException failure) {
            throw new UncheckedIOException("Season 1 transition state write failed", failure);
        }
        season.load();
        community.load();
    }

    /**
     * Teszt-visszaállítás Season 1-ből: a nyugta törlésével a következő valódi átmenet ismét
     * friss indulási timestampet ír, a tiszta season/community állapotot pedig a Season 0
     * content overlay másodperces reconcile-ja kapcsolja vissza inaktívra.
     */
    public synchronized void rollbackSeasonOne() {
        final YamlConfiguration cleanCommunity = new YamlConfiguration();
        cleanCommunity.set("season.number", 1);
        final YamlConfiguration cleanSeason = new YamlConfiguration();
        cleanSeason.set("season.start", System.currentTimeMillis());
        cleanSeason.set("season.number", 1);
        try {
            java.nio.file.Files.deleteIfExists(receiptFile.toPath());
            YamlStore.saveAtomic(new File(plugin.getDataFolder(), "community-goals.yml"), cleanCommunity);
            YamlStore.saveAtomic(new File(plugin.getDataFolder(), "season.yml"), cleanSeason);
        } catch (final IOException failure) {
            throw new UncheckedIOException("Season 1 rollback state write failed", failure);
        }
        resolveSeasonManager().load();
        resolveCommunityGoalManager().load();
    }

    public void activateSeasonOne() {
        config.clearRuntimeOverride("world-events.season.enabled");
        config.clearRuntimeOverride("world-events.season-finale.enabled");
        config.clearRuntimeOverride("community-goals.enabled");
    }

    public synchronized long seasonOneStartTimestamp() {
        if (receiptFile.exists()) {
            final YamlConfiguration yaml = YamlStore.loadTracked(receiptFile, plugin.getLogger());
            final long existing = yaml.getLong("season-one-start", -1L);
            if (existing <= 0L) {
                YamlStore.failCorrupt(receiptFile, plugin.getLogger(),
                        "Érvénytelen Season 1 start receipt");
                throw new IllegalStateException("Invalid Season 1 start receipt");
            }
            return existing;
        }
        final long reserved = System.currentTimeMillis();
        final YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("season-one-start", reserved);
        try {
            YamlStore.saveAtomic(receiptFile, yaml);
        } catch (final IOException failure) {
            throw new UncheckedIOException("Season 1 start receipt write failed", failure);
        }
        return reserved;
    }

    private SeasonManager resolveSeasonManager() {
        for (final RegisteredListener registration : HandlerList.getRegisteredListeners(plugin)) {
            if (registration.getListener() instanceof SeasonManager seasonManager) return seasonManager;
        }
        throw new IllegalStateException("SeasonManager listener not registered");
    }

    private CommunityGoalManager resolveCommunityGoalManager() {
        for (final RegisteredListener registration : HandlerList.getRegisteredListeners(plugin)) {
            if (!(registration.getListener() instanceof QuestProgressListener listener)) continue;
            try {
                final java.lang.reflect.Field field = QuestProgressListener.class
                        .getDeclaredField("communityGoalManager");
                field.setAccessible(true);
                final Object value = field.get(listener);
                if (value instanceof CommunityGoalManager manager) return manager;
            } catch (final ReflectiveOperationException failure) {
                throw new IllegalStateException("CommunityGoalManager runtime bridge failed", failure);
            }
        }
        throw new IllegalStateException("QuestProgressListener not registered");
    }
}
