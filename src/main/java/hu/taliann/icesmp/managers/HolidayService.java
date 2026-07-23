package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;

import java.time.LocalDate;
import java.time.MonthDay;
import java.util.Map;

/**
 * D1 — szezonális ünnepek: valós naptárhoz kötött ablakok (kódex VIII. — Hasadás
 * Napja, Ultimátum Napja, Vérhold-virrasztás, Érkezés Napja + október/december
 * skin-ablakok). A service az aktív ünnep-azonosítót adja (a világesemény-managerek
 * futásidőben olvashatják paraméter-felülíráshoz — a config sosem íródik át), és
 * az ablak nyíltakor/zártakor broadcastol. Dátum-logika: a szerver alapértelmezett
 * időzónája, MM-DD ablakok (év-átfordulást is kezelve). Élő kulcsok: seasonal-events.*.
 */
public final class HolidayService {

    private final ConfigManager configManager;
    private final MessageManager messageManager;
    private volatile String lastActive = "";

    public HolidayService(final ConfigManager configManager, final MessageManager messageManager) {
        this.configManager = configManager;
        this.messageManager = messageManager;
    }

    /** Az aktív ünnep azonosítója (null, ha nincs). */
    public String activeHolidayId() {
        final ConfigurationSection root = configManager.getConfiguration() == null ? null
                : configManager.getConfiguration().getConfigurationSection("seasonal-events");
        if (root == null || !configManager.getBoolean("seasonal-events-enabled", true)) {
            return null;
        }
        final MonthDay today = MonthDay.from(LocalDate.now());
        for (final String id : root.getKeys(false)) {
            final ConfigurationSection holiday = root.getConfigurationSection(id);
            if (holiday == null) {
                continue;
            }
            try {
                final MonthDay start = MonthDay.parse("--" + holiday.getString("start", "01-01"));
                final MonthDay end = MonthDay.parse("--" + holiday.getString("end", "01-01"));
                final boolean wraps = start.compareTo(end) > 0; // pl. 12-28 -> 01-03
                final boolean active = wraps
                        ? (today.compareTo(start) >= 0 || today.compareTo(end) <= 0)
                        : (today.compareTo(start) >= 0 && today.compareTo(end) <= 0);
                if (active) {
                    return id;
                }
            } catch (final Exception ignored) {
                // hibás dátum-formátum — kihagyjuk
            }
        }
        return null;
    }

    public boolean isActive(final String holidayId) {
        return holidayId != null && holidayId.equalsIgnoreCase(activeHolidayId());
    }

    /**
     * Ünnep-specifikus felülíró kulcs olvasása (pl. invasion-broadcast, bloodmoon-title):
     * az aktív ünnep seasonal-events.<id>.<key> értéke, vagy null. A managerek a saját
     * szövegük/paraméterük helyett ezt használhatják, ha nem null — futásidőben.
     */
    public String override(final String key) {
        final String active = activeHolidayId();
        return active == null ? null
                : configManager.getString("seasonal-events." + active + "." + key, null);
    }

    /** A world-events tick hívja: ablak-váltáskor broadcast (nyitás/zárás). */
    public void tick() {
        final String active = activeHolidayId();
        final String current = active == null ? "" : active;
        if (current.equals(lastActive)) {
            return;
        }
        final String previous = lastActive;
        lastActive = current;
        if (!current.isEmpty()) {
            final String greeting = configManager.getString(
                    "seasonal-events." + current + ".greeting",
                    "Ünnep kezdődött: " + current);
            Bukkit.getServer().broadcast(messageManager.getMessage("holiday-started",
                    "<gold>🎉 {greeting}</gold>", Map.of("greeting", greeting)));
        } else if (!previous.isEmpty()) {
            Bukkit.getServer().broadcast(messageManager.getMessage("holiday-ended",
                    "<gray>🕯 Az ünnep véget ért — a hétköznapok visszatérnek.</gray>"));
        }
    }
}
