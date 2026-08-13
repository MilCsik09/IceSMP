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

/** Prepares a clean Season 1 generation; admission stays runtime-gated until Prologue COMPLETED. */
public final class PrologueSeasonTransition {
    private final JavaPlugin plugin;
    private final ConfigManager config;

    public PrologueSeasonTransition(final JavaPlugin plugin, final ConfigManager config) {
        this.plugin = plugin;
        this.config = config;
    }

    public void prepareSeasonOne(final long startTimestamp) {
        if (startTimestamp <= 0L) throw new IllegalArgumentException("Season 1 start timestamp is required");
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
            throw new java.io.UncheckedIOException("Season 1 transition state write failed", failure);
        }
        season.load();
        community.load();
    }

    public void activateSeasonOne() {
        config.clearRuntimeOverride("world-events.season.enabled");
        config.clearRuntimeOverride("world-events.season-finale.enabled");
        config.clearRuntimeOverride("community-goals.enabled");
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
