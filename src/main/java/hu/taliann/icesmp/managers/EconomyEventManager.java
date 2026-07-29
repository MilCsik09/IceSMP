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
 * Periodic economy events: a random
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
    /** F14 — konjunktúra: amíg él, az adott valutában kötött piaci eladások díja csökkentett. */
    private volatile CurrencyType boomCurrency;
    private volatile long boomEndsAt;
    /** F15 — a végítélet-hét alatt sűrűbb/nagyobb sokkok (setterrel kötve). */
    private volatile SeasonFinaleManager seasonFinale;

    public void setSeasonFinale(final SeasonFinaleManager seasonFinale) {
        this.seasonFinale = seasonFinale;
    }

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

        final YamlConfiguration yaml = hu.taliann.icesmp.storage.YamlStore.loadTracked(storageFile, plugin.getLogger());
        final CurrencyType currency = CurrencyType.fromInput(yaml.getString("event.currency", ""));
        final long endsAt = yaml.getLong("event.ends-at", 0L);
        if (currency != null && endsAt > System.currentTimeMillis()) {
            eventCurrency = currency;
            eventMultiplier = Math.max(0.1D, yaml.getDouble("event.multiplier", 1.0D));
            eventEndsAt = endsAt;
            plugin.getLogger().info("Resumed economy event: " + currency.name() + " x" + eventMultiplier);
        }
        final CurrencyType boom = CurrencyType.fromInput(yaml.getString("boom.currency", ""));
        final long boomEnd = yaml.getLong("boom.ends-at", 0L);
        if (boom != null && boomEnd > System.currentTimeMillis()) {
            boomCurrency = boom;
            boomEndsAt = boomEnd;
            plugin.getLogger().info("Resumed market boom: " + boom.name());
        }
    }

    public synchronized void save() {
        try {
            final YamlConfiguration yaml = new YamlConfiguration();
            if (isActive()) {
                yaml.set("event.currency", eventCurrency.name());
                yaml.set("event.multiplier", eventMultiplier);
                yaml.set("event.ends-at", eventEndsAt);
            }
            if (isBoomActive()) {
                yaml.set("boom.currency", boomCurrency.name());
                yaml.set("boom.ends-at", boomEndsAt);
            }

            YamlStore.saveAtomic(storageFile, yaml);
        } catch (final IOException exception) {
            plugin.getLogger().severe("Failed to save economy-event.yml: " + exception.getMessage());
            throw new java.io.UncheckedIOException("Failed to save economy-event.yml", exception);
        }
    }

    public boolean isActive() {
        return eventCurrency != null && System.currentTimeMillis() < eventEndsAt;
    }


    /**
     * @param currencyType the currency
     * @return the active demand-shock multiplier, or 1.0 when no event affects it
     */
    public double getMultiplier(final CurrencyType currencyType) {
        return isActive() && currencyType == eventCurrency ? eventMultiplier : 1.0D;
    }

    /**
     * A Vének Tanácsának Vásár-hete (tulaj-jóváhagyás): a tanács kézzel nyithat egy
     * Creutzér-konjunktúrát (piaci díj-kedvezmény ablak). false, ha már fut konjunktúra.
     */
    public boolean startCouncilBoom() {
        if (isBoomActive()) {
            return false;
        }
        boomCurrency = hu.taliann.icesmp.data.CurrencyType.NEUTRAL;
        boomEndsAt = System.currentTimeMillis() + Math.max(5L,
                configManager.getLong("factions.council.market-week-minutes", 60L)) * 60_000L;
        save();
        Bukkit.getServer().broadcast(messageManager.getMessage(
                "council-market-week",
                "<gold>🏛 A Vének Tanácsa VÁSÁR-HETET hirdet — a Creutzér piaci díja átmenetileg kedvezményes! Caldestera kapui tárva!</gold>"));
        return true;
    }

    /** F14 — él-e konjunktúra-ablak. */
    public boolean isBoomActive() {
        return boomCurrency != null && System.currentTimeMillis() < boomEndsAt;
    }

    public CurrencyType getBoomCurrency() {
        return isBoomActive() ? boomCurrency : null;
    }

    /**
     * F14 — a piaci eladási díj felülbírálása konjunktúra alatt: az érintett valutában
     * a csökkentett díj-százalék, egyébként null (a MarketManager a normál díjat használja).
     */
    public Double marketFeeOverride(final CurrencyType currencyType) {
        if (!isBoomActive() || currencyType != boomCurrency) {
            return null;
        }
        return Math.max(0.0D, Math.min(100.0D,
                configManager.getDouble("currency.market-boom.fee-percent", 5.0D)));
    }

    /**
     * Periodic tick (global region scheduler): ends expired events and may
     * roll a new demand shock when none is active.
     */
    public void tick() {
        if (!configManager.getBoolean("currency.economy-event.enabled", true)) {
            // A konjunktúra a sokktól FÜGGETLEN saját állapot — a sokk kikapcsolása
            // ne némítsa el (a komment mindig is ezt ígérte).
            tickBoom();
            return;
        }

        if (eventCurrency != null && System.currentTimeMillis() >= eventEndsAt) {
            final CurrencyType ended = eventCurrency;
            final double endedMultiplier = eventMultiplier;
            eventCurrency = null;
            eventMultiplier = 1.0D;
            eventEndsAt = 0L;
            save();
            Bukkit.getServer().broadcast(messageManager.getMessage(
                    endedMultiplier < 1.0D ? "economy-panic-ended" : "economy-event-ended",
                    endedMultiplier < 1.0D
                            ? "<gold>📈 A pánik elült: a(z) <white>{currency}</white> valuta értéke normalizálódott.</gold>"
                            : "<gold>📉 A kereslet-sokk lecsengett: a(z) <white>{currency}</white> valuta értéke normalizálódott.</gold>",
                    Map.of("currency", ended.getDisplayName())
            ));
            return;
        }

        // Konjunktúra-ablak lejárata/sorsolása (a sokktól független, saját állapot).
        tickBoom();

        if (isActive()) {
            return;
        }

        // A végítélet-hét alatt sűrűbb sokkok (chance-szorzó) és rövidebb, hevesebb
        // ablakok — a szezonzárás gazdasági dráma-rétege (finálé nélkül 1.0).
        final SeasonFinaleManager finaleRef = seasonFinale;
        final boolean finaleActive = finaleRef != null && finaleRef.isActive();
        final double finaleChanceMult = finaleActive
                ? Math.max(1.0D, configManager.getDouble("currency.economy-event.finale-chance-mult", 3.0D)) : 1.0D;

        final double chancePercent = Math.max(0.0D, Math.min(100.0D,
                configManager.getDouble("currency.economy-event.chance-percent", 25.0D) * finaleChanceMult));
        if (ThreadLocalRandom.current().nextDouble(100.0D) >= chancePercent) {
            return;
        }

        final CurrencyType[] currencies = CurrencyType.values();
        eventCurrency = currencies[ThreadLocalRandom.current().nextInt(currencies.length)];

        // A kereslet-sokk tükörpárja: panic-chance eséllyel PÁNIK jön (x0.6-0.8 leértékelés)
        // a felfelé sokk helyett — a lefelé mozgás nélkül a rendszer csak inflálna.
        final boolean panic = ThreadLocalRandom.current().nextDouble()
                < Math.max(0.0D, Math.min(1.0D, configManager.getDouble("currency.economy-event.panic-chance", 0.35D)));
        final double min;
        final double max;
        if (panic) {
            min = Math.max(0.1D, configManager.getDouble("currency.economy-event.panic-min-multiplier", 0.6D));
            max = Math.max(min, configManager.getDouble("currency.economy-event.panic-max-multiplier", 0.8D));
        } else {
            min = Math.max(1.0D, configManager.getDouble("currency.economy-event.min-multiplier", 1.2D));
            max = Math.max(min, configManager.getDouble("currency.economy-event.max-multiplier", 1.6D));
        }
        // Finálé alatt az amplitúdó is nő: a szorzó 1.0-tól mért kilengése felszorzódik.
        double rolled = min + (ThreadLocalRandom.current().nextDouble() * (max - min));
        if (finaleActive) {
            final double amp = Math.max(1.0D, configManager.getDouble("currency.economy-event.finale-amplitude-mult", 1.5D));
            rolled = Math.max(0.1D, 1.0D + (rolled - 1.0D) * amp);
        }
        eventMultiplier = Math.round(rolled * 100.0D) / 100.0D;
        final long baseDurationHours = Math.max(1L, configManager.getLong("currency.economy-event.duration-hours", 48L));
        final long durationHours = finaleActive
                ? Math.max(1L, baseDurationHours / Math.max(1L,
                        configManager.getLong("currency.economy-event.finale-duration-div", 4L)))
                : baseDurationHours;
        eventEndsAt = System.currentTimeMillis() + (durationHours * 3_600_000L);
        save();

        Bukkit.getServer().broadcast(messageManager.getMessage(
                panic ? "economy-panic-started" : "economy-event-started",
                panic
                        ? "<red>📉 PÁNIK tört ki a(z) <white>{currency}</white> piacon! A valuta értéke <white>x{multiplier}</white>-re zuhant <white>{hours}</white> órára — ki mer most vásárolni?</red>"
                        : "<gold>📈 Kereslet-sokk! A(z) <white>{currency}</white> valuta értéke <white>x{multiplier}</white>-re ugrott <white>{hours}</white> órára — itt az alkalom a kereskedésre!</gold>",
                Map.of(
                        "currency", eventCurrency.getDisplayName(),
                        "multiplier", String.valueOf(eventMultiplier),
                        "hours", String.valueOf(durationHours)
                )
        ));
    }

    /** F14 — a konjunktúra-ablak lejárat-kezelése és sorsolása (piaci díj-kedvezmény). */
    private void tickBoom() {
        if (!configManager.getBoolean("currency.market-boom.enabled", true)) {
            return;
        }
        final long now = System.currentTimeMillis();
        if (boomCurrency != null && now >= boomEndsAt) {
            final CurrencyType ended = boomCurrency;
            boomCurrency = null;
            boomEndsAt = 0L;
            save();
            Bukkit.getServer().broadcast(messageManager.getMessage(
                    "market-boom-ended",
                    "<gold>🏦 A fellendülés lecsengett — a(z) <white>{currency}</white> piaci díja visszaállt.</gold>",
                    Map.of("currency", ended.getDisplayName())));
            return;
        }
        if (isBoomActive()) {
            return;
        }
        final double chance = Math.max(0.0D, Math.min(100.0D,
                configManager.getDouble("currency.market-boom.chance-percent", 12.0D)));
        if (ThreadLocalRandom.current().nextDouble(100.0D) >= chance) {
            return;
        }
        final CurrencyType[] currencies = CurrencyType.values();
        boomCurrency = currencies[ThreadLocalRandom.current().nextInt(currencies.length)];
        final long minutes = Math.max(5L, configManager.getLong("currency.market-boom.duration-minutes", 30L));
        boomEndsAt = now + minutes * 60_000L;
        save();
        Bukkit.getServer().broadcast(messageManager.getMessage(
                "market-boom-started",
                "<gold>🏦 KONJUNKTÚRA! A(z) <white>{currency}</white> piacon <white>{minutes}</white> percig csak <white>{fee}%</white> az eladási díj — adj-vegyél, amíg tart!</gold>",
                Map.of("currency", boomCurrency.getDisplayName(),
                        "minutes", String.valueOf(minutes),
                        "fee", String.valueOf(configManager.getDouble("currency.market-boom.fee-percent", 5.0D)))));
    }
}
