package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.storage.PersistentStore;

import hu.taliann.icesmp.storage.YamlStore;

import hu.taliann.icesmp.data.CurrencyType;
import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Periodic economy events (ideas.md "Heti gazdasági esemény"): a random
 * "demand shock" temporarily multiplies one currency's base value in the
 * dynamic exchange model — a trading opportunity for attentive players.
 * State survives restarts via economy-event.yml; the tick runs on the global
 * region scheduler.
 */
public final class EconomyEventManager implements PersistentStore {

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final MessageManager messageManager;
    private final File storageFile;

    private volatile CurrencyType eventCurrency;
    private volatile double eventMultiplier = 1.0D;
    private volatile long eventEndsAt;

    public EconomyEventManager(final JavaPlugin plugin, final ConfigManager configManager,
                               final MessageManager messageManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.messageManager = messageManager;
        this.storageFile = new File(plugin.getDataFolder(), "economy-event.yml");
        plugin.getDataFolder().mkdirs();
    }

    public void load() {
        eventCurrency = null;
        eventMultiplier = 1.0D;
        eventEndsAt = 0L;

        if (!storageFile.exists()) {
            return;
        }

        final YamlConfiguration yaml = YamlConfiguration.loadConfiguration(storageFile);
        final CurrencyType currency = CurrencyType.fromInput(yaml.getString("event.currency", ""));
        final long endsAt = yaml.getLong("event.ends-at", 0L);
        if (currency != null && endsAt > System.currentTimeMillis()) {
            eventCurrency = currency;
            eventMultiplier = Math.max(0.1D, yaml.getDouble("event.multiplier", 1.0D));
            eventEndsAt = endsAt;
            plugin.getLogger().info("Resumed economy event: " + currency.name() + " x" + eventMultiplier);
        }
    }

    public void save() {
        try {
            final YamlConfiguration yaml = new YamlConfiguration();
            if (isActive()) {
                yaml.set("event.currency", eventCurrency.name());
                yaml.set("event.multiplier", eventMultiplier);
                yaml.set("event.ends-at", eventEndsAt);
            }

            YamlStore.saveAtomic(storageFile, yaml);
        } catch (final IOException exception) {
            plugin.getLogger().severe("Failed to save economy-event.yml: " + exception.getMessage());
        }
    }

    public boolean isActive() {
        return eventCurrency != null && System.currentTimeMillis() < eventEndsAt;
    }

    public CurrencyType getEventCurrency() {
        return isActive() ? eventCurrency : null;
    }

    /**
     * Gets the demand-shock multiplier applied to a currency's base value.
     *
     * @param currencyType the currency
     * @return the active multiplier, or 1.0 when no event affects it
     */
    public double getMultiplier(final CurrencyType currencyType) {
        return isActive() && currencyType == eventCurrency ? eventMultiplier : 1.0D;
    }

    /**
     * Periodic tick (global region scheduler): ends expired events and may
     * roll a new demand shock when none is active.
     */
    public void tick() {
        if (!configManager.getBoolean("currency.economy-event.enabled", true)) {
            return;
        }

        if (eventCurrency != null && System.currentTimeMillis() >= eventEndsAt) {
            final CurrencyType ended = eventCurrency;
            eventCurrency = null;
            eventMultiplier = 1.0D;
            eventEndsAt = 0L;
            save();
            Bukkit.getServer().broadcast(messageManager.getMessage(
                    "economy-event-ended",
                    "<gold>📉 A kereslet-sokk lecsengett: a(z) <white>{currency}</white> valuta értéke normalizálódott.</gold>",
                    Map.of("currency", ended.getDisplayName())
            ));
            return;
        }

        if (isActive()) {
            return;
        }

        final double chancePercent = Math.max(0.0D, Math.min(100.0D,
                configManager.getDouble("currency.economy-event.chance-percent", 25.0D)));
        if (ThreadLocalRandom.current().nextDouble(100.0D) >= chancePercent) {
            return;
        }

        final CurrencyType[] currencies = CurrencyType.values();
        eventCurrency = currencies[ThreadLocalRandom.current().nextInt(currencies.length)];
        final double min = Math.max(1.0D, configManager.getDouble("currency.economy-event.min-multiplier", 1.2D));
        final double max = Math.max(min, configManager.getDouble("currency.economy-event.max-multiplier", 1.6D));
        eventMultiplier = Math.round((min + (ThreadLocalRandom.current().nextDouble() * (max - min))) * 100.0D) / 100.0D;
        final long durationHours = Math.max(1L, configManager.getLong("currency.economy-event.duration-hours", 48L));
        eventEndsAt = System.currentTimeMillis() + (durationHours * 3_600_000L);
        save();

        Bukkit.getServer().broadcast(messageManager.getMessage(
                "economy-event-started",
                "<gold>📈 Kereslet-sokk! A(z) <white>{currency}</white> valuta értéke <white>x{multiplier}</white>-re ugrott <white>{hours}</white> órára — itt az alkalom a kereskedésre!</gold>",
                Map.of(
                        "currency", eventCurrency.getDisplayName(),
                        "multiplier", String.valueOf(eventMultiplier),
                        "hours", String.valueOf(durationHours)
                )
        ));
    }
}
